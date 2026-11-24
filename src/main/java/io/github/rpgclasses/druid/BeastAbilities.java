package io.github.rpgclasses.druid;

import com.google.gson.JsonObject;
import io.github.rpgclasses.content.CombatAbilities;
import io.github.rpgclasses.content.race.Races;
import io.github.rpgclasses.network.DruidPackets;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.Allies;
import io.github.rpgclasses.server.DamagePipeline;
import io.github.rpgclasses.server.DebugLog;
import io.github.rpgclasses.server.SessionState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El cuerpo de la bestia en combate.
 * <ul>
 *   <li><b>Ataque:</b> transformado, el golpe vanilla (con el arma que lleve en la mano) no ocurre: se golpea con el
 *       daño de la bestia × las mejoras del Druida × la carga del golpe, sin barrido ni desgaste del objeto. Pasa por el
 *       pipeline del mod (críticos) marcado como golpe de bestia, así que no le afectan los muros de armas.</li>
 *   <li><b>Habilidad especial</b> (tecla de habilidad de bestia): la de la tabla de la especie (o del instinto de la
 *       quimera), con su recarga.</li>
 * </ul>
 */
public final class BeastAbilities {
    /** Embestidas en curso: se golpea al primer enemigo que se toque. */
    private record Charge(int ticks, double damage, double knockback, String kind) {}

    private record Web(ServerLevel level, BlockPos pos, long until) {}

    private static final Map<UUID, Charge> CHARGES = SessionState.track(new HashMap<>());
    /** Salto de caza: tic de juego hasta el que el siguiente golpe hace más daño, y cuánto. */
    private static final Map<UUID, double[]> POUNCE = SessionState.track(new HashMap<>());
    /** Inflarse: hasta qué tic. */
    private static final Map<UUID, Long> INFLATED = SessionState.track(new HashMap<>());
    /** Abalanzarse: objetivo al que aturdir al caer (id de entidad) y hasta cuándo. */
    private static final Map<UUID, long[]> LEAPS = SessionState.track(new HashMap<>());
    private static final List<Web> WEBS = SessionState.track(new ArrayList<>());

    public static String cooldownKey(String ability) {
        return "druid_beast:" + ability;
    }

    // ------------------------------------------------------------------ ataque

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !WildShape.inForm(p)) return;
        event.setCanceled(true);
        BeastMoves.breakCamouflage(p);
        Entity target = event.getTarget();
        if (!target.isAttackable() || target.skipAttackInteraction(p) || !(target instanceof LivingEntity victim)) return;
        Beasts.Info info = WildShape.info(p);
        if (info == null) return;
        float strength = p.getAttackStrengthScale(.5f);
        p.resetAttackStrengthTicker();
        double damage = info.damage() * Beasts.damageFactor(p) * (.2 + strength * strength * .8);
        double[] pounce = POUNCE.remove(p.getUUID());
        if (pounce != null && p.level().getGameTime() <= pounce[0]) damage *= 1 + pounce[1];
        boolean hit;
        WildShape.beastHit(true);
        try {
            hit = victim.hurt(p.damageSources().playerAttack(p), (float) damage);
        } finally {
            WildShape.beastHit(false);
        }
        if (!hit) return;
        double kb = .4 * (strength > .9f ? 1 : .5) + (p.isSprinting() && strength > .9f ? .4 : 0);
        victim.knockback(kb, Math.sin(Math.toRadians(p.getYRot())), -Math.cos(Math.toRadians(p.getYRot())));
        if (p.isSprinting()) p.setSprinting(false);
        p.setLastHurtMob(victim);
        p.causeFoodExhaustion(.1f);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, .7f, .8f);
        BeastEffects.onBeastHit(p, victim);
        DebugLog.event(p, "Druida", "golpe de bestia a {}: {} (carga {})", DebugLog.describe(victim), DebugLog.num(damage), DebugLog.num(strength));
    }

    // ------------------------------------------------------------------ habilidad especial

    static void use(ServerPlayer p) {
        WildShape.Form form = WildShape.form(p);
        if (form == null) return;
        String ability = form.info.ability();
        if (ability == null) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.beast_ability_none"), true);
            return;
        }
        String key = cooldownKey(ability);
        long remaining = AbilityService.remainingTicks(p, key);
        Component name = Component.translatable("druid.rites_and_paths.ability." + ability);
        if (remaining > 0) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.beast_ability_cooldown", name,
                    String.format(java.util.Locale.ROOT, "%.1f", remaining / 20.0)), true);
            return;
        }
        JsonObject params = Beasts.abilityParams(ability, false);
        String type = params.has("type") ? params.get("type").getAsString() : ability;
        if (!"camouflage".equals(type)) BeastMoves.breakCamouflage(p);
        boolean used;
        try {
            used = run(p, type, params);
        } catch (RuntimeException e) {
            DebugLog.error(p, "Druida", e, "habilidad de bestia {} falló", ability);
            return;
        }
        if (!used) return;
        AbilityService.setCooldown(p, key, (int) Math.round(num(params, "cooldown_seconds", 5) * 20));
        DebugLog.event(p, "Druida", "habilidad de bestia {} ({})", ability, type);
        DruidNet.sendState(p);
    }

    /** Ejecuta la habilidad de ese tipo con esos parámetros (los de la variante). */
    private static boolean run(ServerPlayer p, String ability, JsonObject a) {
        ServerLevel level = p.serverLevel();
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        double dmgFactor = Beasts.damageFactor(p);
        switch (ability) {
            case "pounce" -> {
                impulse(p, flat.scale(num(a, "distance", 5) * .22).add(0, num(a, "lift", .4), 0));
                POUNCE.put(p.getUUID(), new double[] {level.getGameTime() + num(a, "window_seconds", 3) * 20, num(a, "damage_bonus", .5)});
                level.playSound(null, p.blockPosition(), SoundEvents.WOLF_GROWL, SoundSource.PLAYERS, 1f, 1.1f);
                return true;
            }
            case "leap" -> {
                LivingEntity target = lookEnemy(p, num(a, "range", 12));
                if (target == null) return noTarget(p);
                Vec3 to = target.position().subtract(p.position());
                Vec3 h = new Vec3(to.x, 0, to.z);
                impulse(p, h.scale(.16).add(0, num(a, "lift", .9), 0));
                LEAPS.put(p.getUUID(), new long[] {target.getId(), level.getGameTime() + 40, Math.round(num(a, "stun_seconds", .5) * 20)});
                level.playSound(null, p.blockPosition(), SoundEvents.FOX_AGGRO, SoundSource.PLAYERS, 1f, 1f);
                return true;
            }
            case "super_jump" -> {
                impulse(p, flat.scale(num(a, "forward", .6)).add(0, num(a, "lift", 1.1), 0));
                level.playSound(null, p.blockPosition(), SoundEvents.RABBIT_JUMP, SoundSource.PLAYERS, 1f, 1f);
                return true;
            }
            case "web" -> {
                double range = num(a, "range", 16);
                Vec3 eye = p.getEyePosition();
                Vec3 end = eye.add(look.scale(range));
                BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                Vec3 stop = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
                EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, p, eye, stop, p.getBoundingBox().expandTowards(look.scale(range)).inflate(1),
                        e -> e instanceof LivingEntity le && le.isAlive() && e != p);
                BlockPos pos = eh != null ? eh.getEntity().blockPosition()
                        : bh.getType() == HitResult.Type.BLOCK ? bh.getBlockPos().relative(bh.getDirection()) : BlockPos.containing(stop);
                if (!level.getBlockState(pos).canBeReplaced() || !io.github.rpgclasses.content.race.BodyTraits.mayBreak(p, pos)) return noTarget(p);
                level.setBlockAndUpdate(pos, Blocks.COBWEB.defaultBlockState());
                WEBS.add(new Web(level, pos.immutable(), level.getGameTime() + Math.round(num(a, "duration_seconds", 5) * 20)));
                level.playSound(null, pos, SoundEvents.SPIDER_AMBIENT, SoundSource.PLAYERS, 1f, 1.2f);
                return true;
            }
            case "roar" -> {
                double r = num(a, "radius", 5);
                // effect: otro estado en vez de debilidad (el cascabel ralentiza, el chillido oscurece...).
                MobEffect effect = BeastMoves.effect(a, "effect", MobEffects.WEAKNESS);
                int ticks = (int) Math.round(num(a, "effect_seconds", num(a, "weakness_seconds", 5)) * 20);
                int amp = Math.max(0, (int) num(a, "effect_level", 1) - 1);
                for (LivingEntity e : enemiesAround(p, r)) {
                    e.addEffect(new MobEffectInstance(effect, ticks, amp), p);
                    Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1).normalize();
                    e.knockback(num(a, "knockback", 1), -away.x, -away.z);
                }
                level.sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
                level.playSound(null, p.blockPosition(), BeastMoves.sound(a, SoundEvents.POLAR_BEAR_WARNING), SoundSource.PLAYERS, 1.5f, .8f);
                return true;
            }
            case "ram" -> {
                // follow_look: la carga sigue la mirada (también hacia arriba o abajo), para las que nadan.
                Vec3 dir = a.has("follow_look") && a.get("follow_look").getAsBoolean() ? look : flat;
                impulse(p, dir.scale(num(a, "distance", 6) * .25).add(0, dir == flat ? .15 : 0, 0));
                CHARGES.put(p.getUUID(), new Charge(14, num(a, "damage", 4) * dmgFactor, num(a, "knockback", 2), "ram"));
                level.playSound(null, p.blockPosition(), BeastMoves.sound(a, SoundEvents.GOAT_RAM_IMPACT), SoundSource.PLAYERS, 1f, 1f);
                return true;
            }
            case "toss" -> {
                LivingEntity target = lookEnemy(p, num(a, "range", 3));
                if (target == null) return noTarget(p);
                beastDamage(p, target, num(a, "damage", 4) * dmgFactor);
                if (!Races.isBoss(target)) {
                    target.setDeltaMovement(target.getDeltaMovement().add(0, num(a, "lift", 1), 0));
                    target.hurtMarked = true;
                }
                level.playSound(null, p.blockPosition(), SoundEvents.HOGLIN_ATTACK, SoundSource.PLAYERS, 1f, 1f);
                return true;
            }
            case "stun_roar" -> {
                double r = num(a, "radius", 4);
                int ticks = (int) Math.round(num(a, "stun_seconds", 1) * 20);
                for (LivingEntity e : enemiesAround(p, r)) {
                    CombatAbilities.stun(e, Races.isBoss(e) ? (int) Math.round(ticks * num(a, "boss_factor", .5)) : ticks);
                }
                level.sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1.2, p.getZ(), 1, 0, 0, 0, 0);
                level.playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.5f, 1f);
                return true;
            }
            case "dash" -> {
                impulse(p, flat.scale(num(a, "distance", 8) * .22).add(0, .1, 0));
                level.playSound(null, p.blockPosition(), SoundEvents.CAMEL_DASH, SoundSource.PLAYERS, 1f, 1f);
                return true;
            }
            case "spit" -> {
                LlamaSpit spit = EntityType.LLAMA_SPIT.create(level);
                if (spit == null) return false;
                spit.setOwner(p);
                spit.moveTo(p.getX(), p.getEyeY() - .1, p.getZ(), p.getYRot(), p.getXRot());
                spit.shoot(look.x, look.y, look.z, (float) num(a, "speed", 1.5), 1f);
                // El escupitajo de llama hace 1 de daño: el factor lleva al daño de la tabla, sin muro de Arco.
                DamagePipeline.markProjectile(spit, num(a, "damage", 3) * dmgFactor, false);
                level.addFreshEntity(spit);
                level.playSound(null, p.blockPosition(), SoundEvents.LLAMA_SPIT, SoundSource.PLAYERS, 1f, 1f);
                return true;
            }
            case "sting" -> {
                LivingEntity target = lookEnemy(p, num(a, "range", 3));
                if (target == null) return noTarget(p);
                beastDamage(p, target, num(a, "damage", 2) * dmgFactor);
                target.addEffect(new MobEffectInstance(BeastMoves.effect(a, "effect", MobEffects.POISON),
                        (int) Math.round(num(a, "effect_seconds", num(a, "poison_seconds", 5)) * 20),
                        Math.max(0, (int) num(a, "effect_level", num(a, "poison_level", 2)) - 1)), p);
                level.playSound(null, p.blockPosition(), SoundEvents.BEE_STING, SoundSource.PLAYERS, 1f, 1f);
                return true;
            }
            case "echolocation" -> {
                double r = num(a, "radius", 16);
                int[] ids = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), e -> e != p && e.isAlive())
                        .stream().mapToInt(Entity::getId).limit(256).toArray();
                Net.toPlayer(p, new DruidPackets.Glow(ids, (int) Math.round(num(a, "duration_seconds", 5) * 20)));
                level.playSound(null, p.blockPosition(), SoundEvents.BAT_AMBIENT, SoundSource.PLAYERS, 1f, .6f);
                return true;
            }
            case "water_dash" -> {
                if (!p.isInWater()) {
                    p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.water_only"), true);
                    return false;
                }
                impulse(p, look.scale(num(a, "distance", 8) * .25));
                level.sendParticles(ParticleTypes.BUBBLE, p.getX(), p.getY() + .5, p.getZ(), 20, .3, .3, .3, .1);
                level.playSound(null, p.blockPosition(), SoundEvents.DOLPHIN_JUMP, SoundSource.PLAYERS, 1f, 1f);
                return true;
            }
            case "shell" -> {
                int ticks = (int) Math.round(num(a, "duration_seconds", 4) * 20);
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, Math.max(0, (int) num(a, "resistance_level", 2) - 1)));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, Math.max(0, (int) num(a, "slow_level", 2) - 1)));
                level.playSound(null, p.blockPosition(), SoundEvents.TURTLE_SHAMBLE, SoundSource.PLAYERS, 1f, .8f);
                return true;
            }
            case "play_dead" -> {
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, (int) Math.round(num(a, "duration_seconds", 5) * 20),
                        Math.max(0, (int) num(a, "regen_level", 2) - 1)));
                for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24), m -> m.getTarget() == p)) m.setTarget(null);
                level.playSound(null, p.blockPosition(), SoundEvents.AXOLOTL_HURT, SoundSource.PLAYERS, 1f, .7f);
                return true;
            }
            case "tongue" -> {
                double range = num(a, "range", 6);
                LivingEntity target = lookEnemy(p, range);
                if (target == null || target.getBbWidth() > num(a, "max_width", 1) || Races.isBoss(target)) return noTarget(p);
                Vec3 pull = p.position().subtract(target.position()).scale(.25).add(0, .2, 0);
                target.setDeltaMovement(pull);
                target.hurtMarked = true;
                beastDamage(p, target, num(a, "damage", 2) * dmgFactor);
                level.playSound(null, p.blockPosition(), SoundEvents.FROG_TONGUE, SoundSource.PLAYERS, 1f, 1f);
                return true;
            }
            case "ink" -> {
                double r = num(a, "radius", 4);
                for (LivingEntity e : enemiesAround(p, r)) {
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, (int) Math.round(num(a, "blind_seconds", 3) * 20), 0), p);
                }
                level.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + .5, p.getZ(), 40, r / 2, .5, r / 2, .05);
                level.playSound(null, p.blockPosition(), SoundEvents.SQUID_SQUIRT, SoundSource.PLAYERS, 1f, 1f);
                return true;
            }
            case "inflate" -> {
                INFLATED.put(p.getUUID(), level.getGameTime() + Math.round(num(a, "duration_seconds", 4) * 20));
                level.playSound(null, p.blockPosition(), SoundEvents.PUFFER_FISH_BLOW_UP, SoundSource.PLAYERS, 1f, 1f);
                return true;
            }
            default -> {
                Boolean used = BeastMoves.run(p, ability, a, dmgFactor);
                if (used != null) return used;
                p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.beast_ability_none"), true);
                return false;
            }
        }
    }

    /** Cada tic de la forma (desde WildShape): embestidas, salto de caza, inflarse. */
    static void tick(ServerPlayer p, WildShape.Form form) {
        ServerLevel level = p.serverLevel();
        BeastMoves.tick(p);
        Charge c = CHARGES.get(p.getUUID());
        if (c != null) {
            LivingEntity hit = null;
            for (LivingEntity e : enemiesAround(p, 1.6)) {
                if (e.getBoundingBox().intersects(p.getBoundingBox().inflate(.4))) {
                    hit = e;
                    break;
                }
            }
            if (hit != null) {
                beastDamage(p, hit, c.damage());
                if (!Races.isBoss(hit)) {
                    Vec3 dir = hit.position().subtract(p.position()).multiply(1, 0, 1).normalize();
                    hit.knockback(c.knockback(), -dir.x, -dir.z);
                    hit.setDeltaMovement(hit.getDeltaMovement().add(0, .35, 0));
                }
                CHARGES.remove(p.getUUID());
            } else if (c.ticks() <= 1) {
                CHARGES.remove(p.getUUID());
            } else {
                CHARGES.put(p.getUUID(), new Charge(c.ticks() - 1, c.damage(), c.knockback(), c.kind()));
            }
        }
        long[] leap = LEAPS.get(p.getUUID());
        if (leap != null) {
            long now = level.getGameTime();
            Entity target = level.getEntity((int) leap[0]);
            if (now > leap[1] || !(target instanceof LivingEntity le) || !le.isAlive()) {
                LEAPS.remove(p.getUUID());
            } else if (p.onGround() && now > leap[1] - 36 && p.distanceToSqr(le) < 4) {
                CombatAbilities.stun(le, Races.isBoss(le) ? (int) (leap[2] / 2) : (int) leap[2]);
                LEAPS.remove(p.getUUID());
            }
        }
        Long inflated = INFLATED.get(p.getUUID());
        if (inflated != null) {
            if (level.getGameTime() > inflated) {
                INFLATED.remove(p.getUUID());
            } else if (p.tickCount % 10 == 0) {
                JsonObject a = Beasts.abilityParams("inflate", false);
                double r = num(a, "radius", 1.5);
                for (LivingEntity e : enemiesAround(p, r)) {
                    e.addEffect(new MobEffectInstance(MobEffects.POISON, (int) Math.round(num(a, "poison_seconds", 4) * 20), 0), p);
                }
                level.sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + .4, p.getZ(), 3, .3, .2, .3, 0);
            }
        }
    }

    /** Las telarañas de la araña desaparecen solas. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || WEBS.isEmpty()) return;
        Iterator<Web> it = WEBS.iterator();
        while (it.hasNext()) {
            Web w = it.next();
            if (w.level().getGameTime() < w.until()) continue;
            if (w.level().isLoaded(w.pos()) && w.level().getBlockState(w.pos()).is(Blocks.COBWEB)) w.level().removeBlock(w.pos(), false);
            it.remove();
        }
    }

    // ------------------------------------------------------------------ utilidades

    static void impulse(ServerPlayer p, Vec3 v) {
        p.setDeltaMovement(p.getDeltaMovement().add(v));
        p.hurtMarked = true;
        p.resetFallDistance();
    }

    static boolean noTarget(ServerPlayer p) {
        p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.no_target"), true);
        return false;
    }

    /** Daño de una habilidad de bestia: golpe del jugador marcado como de bestia (sin muros de armas). */
    static void beastDamage(ServerPlayer p, LivingEntity target, double damage) {
        WildShape.beastHit(true);
        try {
            target.hurt(p.damageSources().playerAttack(p), (float) damage);
        } finally {
            WildShape.beastHit(false);
        }
    }

    static List<LivingEntity> enemiesAround(ServerPlayer p, double r) {
        return p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
                e -> e != p && e.isAlive() && e.distanceToSqr(p) <= r * r && Allies.isEnemy(p, e));
    }

    /** Enemigo al que mira (con ayuda de puntería), o null. */
    static LivingEntity lookEnemy(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        AABB box = p.getBoundingBox().expandTowards(look.scale(range)).inflate(1);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end, box,
                e -> e instanceof LivingEntity le && le.isAlive() && e != p && Allies.isEnemy(p, le));
        if (hit != null && hit.getEntity() instanceof LivingEntity le) return le;
        LivingEntity best = null;
        double bestDot = Math.cos(Math.toRadians(10));
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range))) {
            if (e == p || !e.isAlive() || !Allies.isEnemy(p, e) || !p.hasLineOfSight(e)) continue;
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            if (to.length() > range) continue;
            double dot = to.normalize().dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = e;
            }
        }
        return best;
    }

    static double num(JsonObject o, String key, double fallback) {
        return AbilityService.number(o, key, fallback);
    }
}
