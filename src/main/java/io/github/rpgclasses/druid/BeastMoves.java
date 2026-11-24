package io.github.rpgclasses.druid;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.rpgclasses.content.CombatAbilities;
import io.github.rpgclasses.content.race.BodyTraits;
import io.github.rpgclasses.content.race.Races;
import io.github.rpgclasses.server.Allies;
import io.github.rpgclasses.server.SessionState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.rpgclasses.druid.BeastAbilities.beastDamage;
import static io.github.rpgclasses.druid.BeastAbilities.enemiesAround;
import static io.github.rpgclasses.druid.BeastAbilities.impulse;
import static io.github.rpgclasses.druid.BeastAbilities.lookEnemy;
import static io.github.rpgclasses.druid.BeastAbilities.noTarget;
import static io.github.rpgclasses.druid.BeastAbilities.num;

/**
 * Tipos de habilidad de bestia añadidos para las criaturas de otros mods (adenda de bestias de mods). Cada criatura usa
 * una <i>variante</i> definida en {@code druid_beasts.json} ({@code "abilities"}: id → {@code {"type": ..., números}}),
 * así cada una tiene su nombre y sus números con el mismo código:
 * <ul>
 *   <li><b>breath</b> (aliento): cono delante durante unos segundos, daño por segundo y estados.</li>
 *   <li><b>slam</b> (pisotón): golpe en área alrededor, levanta y ralentiza.</li>
 *   <li><b>strike</b> (golpe): golpe fuerte al que miras, con retroceso, perforación de armadura o aturdimiento.</li>
 *   <li><b>grapple</b> (presa): sujetas al enemigo (no se mueve) y recibe daño cada segundo.</li>
 *   <li><b>camouflage</b>: invisible y los mobs te pierden; se rompe al atacar o usar otra habilidad.</li>
 *   <li><b>blink</b> (salto del End): teletransporte al punto que miras.</li>
 *   <li><b>tunnel</b> (excavar): impulso que rompe tierra y piedra blanda (respeta las zonas protegidas).</li>
 *   <li><b>rally</b> (llamada): estados para ti y tus aliados cercanos.</li>
 *   <li><b>drain</b> (drenar): mordisco que te cura una parte del daño.</li>
 *   <li><b>throw</b> (lanzar): proyectil con daño y estados.</li>
 *   <li><b>sonic</b> (onda sónica): onda en línea que daña y aturde.</li>
 * </ul>
 * Los parámetros opcionales {@code "sound"} (id de sonido) y {@code "particle"} cambian el aspecto de cada variante.
 */
public final class BeastMoves {
    private record Breath(int ticksLeft, JsonObject a, double dmgFactor) {}

    private record Grapple(int targetId, int ticksLeft, JsonObject a, double dmgFactor) {}

    private record Tunnel(int ticksLeft, Vec3 dir, float maxHardness) {}

    private record Thrown(UUID owner, double damage, JsonObject a) {}

    private static final Map<UUID, Breath> BREATHS = SessionState.track(new HashMap<>());
    private static final Map<UUID, Grapple> GRAPPLES = SessionState.track(new HashMap<>());
    private static final Map<UUID, Tunnel> TUNNELS = SessionState.track(new HashMap<>());
    /** Camuflaje: hasta qué tic de juego. */
    private static final Map<UUID, Long> CAMO = SessionState.track(new HashMap<>());
    /** Proyectiles lanzados por formas (id de entidad → quién y cuánto). */
    private static final Map<Integer, Thrown> THROWN = SessionState.track(new HashMap<>());

    // ------------------------------------------------------------------ utilidades de parámetros

    static MobEffect effect(JsonObject a, String key, MobEffect fallback) {
        if (!a.has(key)) return fallback;
        ResourceLocation id = ResourceLocation.tryParse(a.get(key).getAsString());
        MobEffect e = id == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(id);
        return e == null ? fallback : e;
    }

    static SoundEvent sound(JsonObject a, SoundEvent fallback) {
        if (!a.has("sound")) return fallback;
        ResourceLocation id = ResourceLocation.tryParse(a.get("sound").getAsString());
        SoundEvent s = id == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(id);
        return s == null ? fallback : s;
    }

    private static ParticleOptions particle(JsonObject a, ParticleOptions fallback) {
        String name = a.has("particle") ? a.get("particle").getAsString() : "";
        return switch (name) {
            case "flame" -> ParticleTypes.FLAME;
            case "soul_flame" -> ParticleTypes.SOUL_FIRE_FLAME;
            case "snowflake" -> ParticleTypes.SNOWFLAKE;
            case "void" -> ParticleTypes.REVERSE_PORTAL;
            case "gas" -> ParticleTypes.SNEEZE;
            case "spore" -> ParticleTypes.SPORE_BLOSSOM_AIR;
            case "electric" -> ParticleTypes.ELECTRIC_SPARK;
            case "bubble" -> ParticleTypes.BUBBLE;
            case "heart" -> ParticleTypes.HEART;
            case "happy" -> ParticleTypes.HAPPY_VILLAGER;
            default -> fallback;
        };
    }

    /** Lista {@code [{"effect": id, "seconds": s, "level": n}]} aplicada a un objetivo. */
    private static void applyEffects(JsonObject a, String key, LivingEntity target, Entity source, double durationFactor) {
        if (!a.has(key) || !a.get(key).isJsonArray()) return;
        for (JsonElement el : a.getAsJsonArray(key)) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            MobEffect e = effect(o, "effect", null);
            if (e == null) continue;
            int ticks = (int) Math.round(num(o, "seconds", 5) * 20 * durationFactor);
            target.addEffect(new MobEffectInstance(e, Math.max(1, ticks), Math.max(0, (int) num(o, "level", 1) - 1)), source);
        }
    }

    private static boolean isBoss(LivingEntity e) {
        return Races.isBoss(e);
    }

    // ------------------------------------------------------------------ ejecución

    /** Ejecuta un tipo nuevo; null si el tipo no es de esta clase. */
    static Boolean run(ServerPlayer p, String type, JsonObject a, double dmgFactor) {
        ServerLevel level = p.serverLevel();
        Vec3 look = p.getLookAngle();
        switch (type) {
            case "breath" -> {
                BREATHS.put(p.getUUID(), new Breath((int) Math.round(num(a, "duration_seconds", 3) * 20), a, dmgFactor));
                level.playSound(null, p.blockPosition(), sound(a, SoundEvents.BLAZE_SHOOT), SoundSource.PLAYERS, 1f, 1f);
                return true;
            }
            case "slam" -> {
                double r = num(a, "radius", 4);
                int slow = (int) Math.round(num(a, "slow_seconds", 3) * 20);
                for (LivingEntity e : enemiesAround(p, r)) {
                    beastDamage(p, e, num(a, "damage", 6) * dmgFactor);
                    if (!isBoss(e)) {
                        e.setDeltaMovement(e.getDeltaMovement().add(0, num(a, "lift", .6), 0));
                        e.hurtMarked = true;
                    }
                    if (slow > 0) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slow, Math.max(0, (int) num(a, "slow_level", 2) - 1)), p);
                }
                BlockState below = level.getBlockState(p.blockPosition().below());
                if (!below.isAir()) {
                    level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, below),
                            p.getX(), p.getY() + .1, p.getZ(), (int) (r * 12), r / 2, .1, r / 2, .2);
                }
                level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + .2, p.getZ(), 2, r / 3, 0, r / 3, 0);
                level.playSound(null, p.blockPosition(), sound(a, SoundEvents.GENERIC_EXPLODE), SoundSource.PLAYERS, 1f, .7f);
                return true;
            }
            case "strike" -> {
                LivingEntity target = lookEnemy(p, num(a, "range", 3));
                if (target == null) return noTarget(p);
                double damage = num(a, "damage", 8) * dmgFactor;
                double pierce = Math.max(0, Math.min(1, num(a, "armor_pierce", 0)));
                if (pierce < 1) beastDamage(p, target, damage * (1 - pierce));
                if (pierce > 0) {
                    target.invulnerableTime = 0;
                    target.hurt(p.damageSources().indirectMagic(p, p), (float) (damage * pierce));
                }
                if (!isBoss(target)) {
                    Vec3 dir = target.position().subtract(p.position()).multiply(1, 0, 1).normalize();
                    target.knockback(num(a, "knockback", 1.5), -dir.x, -dir.z);
                }
                int stun = (int) Math.round(num(a, "stun_seconds", 0) * 20);
                if (stun > 0) CombatAbilities.stun(target, isBoss(target) ? stun / 2 : stun);
                applyEffects(a, "effects", target, p, 1);
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 10, .3, .3, .3, .2);
                level.playSound(null, p.blockPosition(), sound(a, SoundEvents.PLAYER_ATTACK_KNOCKBACK), SoundSource.PLAYERS, 1f, .8f);
                return true;
            }
            case "grapple" -> {
                LivingEntity target = lookEnemy(p, num(a, "range", 3));
                if (target == null || target.getBbWidth() > num(a, "max_width", 2.5)) return noTarget(p);
                int ticks = (int) Math.round(num(a, "seconds", 3) * 20);
                if (isBoss(target)) ticks /= 2;
                GRAPPLES.put(p.getUUID(), new Grapple(target.getId(), ticks, a, dmgFactor));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1, false, false));
                level.playSound(null, p.blockPosition(), sound(a, SoundEvents.EVOKER_FANGS_ATTACK), SoundSource.PLAYERS, 1f, .8f);
                return true;
            }
            case "camouflage" -> {
                int ticks = (int) Math.round(num(a, "seconds", 8) * 20);
                CAMO.put(p.getUUID(), level.getGameTime() + ticks);
                p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false));
                for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32), m -> m.getTarget() == p)) m.setTarget(null);
                level.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + .5, p.getZ(), 15, .3, .3, .3, .02);
                level.playSound(null, p.blockPosition(), sound(a, SoundEvents.ILLUSIONER_MIRROR_MOVE), SoundSource.PLAYERS, .8f, 1.2f);
                return true;
            }
            case "blink" -> {
                Vec3 to = blinkTarget(p, num(a, "range", 12));
                if (to == null) return noTarget(p);
                level.sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 30, .3, .6, .3, .5);
                p.teleportTo(to.x, to.y, to.z);
                p.resetFallDistance();
                level.sendParticles(ParticleTypes.PORTAL, to.x, to.y + 1, to.z, 30, .3, .6, .3, .5);
                level.playSound(null, p.blockPosition(), sound(a, SoundEvents.ENDERMAN_TELEPORT), SoundSource.PLAYERS, 1f, 1f);
                return true;
            }
            case "tunnel" -> {
                int ticks = (int) Math.round(num(a, "distance", 6) / .45);
                TUNNELS.put(p.getUUID(), new Tunnel(ticks, look.normalize(), (float) num(a, "max_hardness", 3)));
                level.playSound(null, p.blockPosition(), sound(a, SoundEvents.ROOTED_DIRT_BREAK), SoundSource.PLAYERS, 1f, .8f);
                return true;
            }
            case "rally" -> {
                double r = num(a, "radius", 8);
                List<LivingEntity> targets = new ArrayList<>(Allies.alliesAround(p, r, false));
                targets.add(p);
                for (LivingEntity t : targets) applyEffects(a, "effects", t, p, 1);
                level.sendParticles(particle(a, ParticleTypes.NOTE), p.getX(), p.getY() + 1.5, p.getZ(), 12, r / 3, .5, r / 3, 0);
                level.playSound(null, p.blockPosition(), sound(a, SoundEvents.WOLF_HOWL), SoundSource.PLAYERS, 1.2f, 1f);
                return true;
            }
            case "drain" -> {
                LivingEntity target = lookEnemy(p, num(a, "range", 3));
                if (target == null) return noTarget(p);
                float before = target.getHealth();
                beastDamage(p, target, num(a, "damage", 5) * dmgFactor);
                float dealt = Math.max(0, before - target.getHealth());
                if (dealt > 0) p.heal((float) (dealt * num(a, "heal_ratio", .5)));
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 6, .2, .2, .2, .1);
                level.playSound(null, p.blockPosition(), sound(a, SoundEvents.GENERIC_DRINK), SoundSource.PLAYERS, 1f, .7f);
                return true;
            }
            case "throw" -> {
                Snowball proj = new Snowball(level, p);
                proj.setItem(new ItemStack(item(a)));
                proj.shootFromRotation(p, p.getXRot(), p.getYRot(), 0f, (float) num(a, "speed", 1.5), 1f);
                level.addFreshEntity(proj);
                THROWN.put(proj.getId(), new Thrown(p.getUUID(), num(a, "damage", 3) * dmgFactor, a));
                level.playSound(null, p.blockPosition(), sound(a, SoundEvents.SNOWBALL_THROW), SoundSource.PLAYERS, 1f, .8f);
                return true;
            }
            case "sonic" -> {
                double range = num(a, "range", 16), width = num(a, "width", 1);
                Vec3 eye = p.getEyePosition();
                Vec3 end = eye.add(look.scale(range));
                BlockHitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                if (wall.getType() != HitResult.Type.MISS) end = wall.getLocation();
                int stun = (int) Math.round(num(a, "stun_seconds", 1) * 20);
                AABB area = new AABB(eye, end).inflate(width);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != p && e.isAlive() && Allies.isEnemy(p, e))) {
                    if (e.getBoundingBox().inflate(width).clip(eye, end).isEmpty() && !e.getBoundingBox().inflate(width).contains(eye)) continue;
                    beastDamage(p, e, num(a, "damage", 6) * dmgFactor);
                    if (stun > 0) CombatAbilities.stun(e, isBoss(e) ? stun / 2 : stun);
                    applyEffects(a, "effects", e, p, 1);
                }
                ParticleOptions part = particle(a, ParticleTypes.SONIC_BOOM);
                double len = end.distanceTo(eye);
                for (double d = 1; d < len; d += 1.5) {
                    Vec3 at = eye.add(look.scale(d));
                    level.sendParticles(part, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                }
                level.playSound(null, p.blockPosition(), sound(a, SoundEvents.WARDEN_SONIC_BOOM), SoundSource.PLAYERS, 1.2f, 1.2f);
                return true;
            }
            default -> {
                return null;
            }
        }
    }

    private static Item item(JsonObject a) {
        for (String key : new String[] {"item", "item_fallback"}) {
            if (!a.has(key)) continue;
            ResourceLocation id = ResourceLocation.tryParse(a.get(key).getAsString());
            Item it = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
            if (it != null && it != Items.AIR) return it;
        }
        return Items.SNOWBALL;
    }

    /** Punto libre más lejano en la mirada, hasta {@code range}; null si no hay sitio. */
    private static Vec3 blinkTarget(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        BlockHitResult hit = p.level().clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 aim = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(range)) : hit.getLocation().subtract(look.scale(.6));
        for (double back = 0; back < range; back += .5) {
            Vec3 feet = aim.subtract(look.scale(back)).subtract(0, p.getEyeHeight(), 0);
            AABB box = p.getBoundingBox().move(feet.subtract(p.position()));
            if (p.level().noCollision(p, box) && feet.distanceTo(p.position()) > 1.5) return feet;
            // Sobre el bloque al que mira: probar a ponerse encima.
            AABB up = box.move(0, 1, 0);
            if (p.level().noCollision(p, up) && feet.distanceTo(p.position()) > 1.5) return feet.add(0, 1, 0);
        }
        return null;
    }

    // ------------------------------------------------------------------ cada tic de la forma

    static void tick(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        UUID id = p.getUUID();
        Breath b = BREATHS.get(id);
        if (b != null) {
            if (b.ticksLeft() % 5 == 0) breathTick(p, b);
            if (b.ticksLeft() <= 1) BREATHS.remove(id);
            else BREATHS.put(id, new Breath(b.ticksLeft() - 1, b.a(), b.dmgFactor()));
        }
        Grapple g = GRAPPLES.get(id);
        if (g != null) {
            Entity e = level.getEntity(g.targetId());
            double range = num(g.a(), "range", 3) + 2;
            if (!(e instanceof LivingEntity t) || !t.isAlive() || t.distanceTo(p) > range || g.ticksLeft() <= 0) {
                GRAPPLES.remove(id);
            } else {
                if (g.ticksLeft() % 10 == 0) CombatAbilities.stun(t, 12);
                if (g.ticksLeft() % 20 == 0) beastDamage(p, t, num(g.a(), "damage_per_second", 2) * g.dmgFactor());
                if (g.ticksLeft() % 5 == 0) {
                    level.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() / 2, t.getZ(), 3, .2, .2, .2, .1);
                }
                GRAPPLES.put(id, new Grapple(g.targetId(), g.ticksLeft() - 1, g.a(), g.dmgFactor()));
            }
        }
        Tunnel tn = TUNNELS.get(id);
        if (tn != null) {
            impulse(p, tn.dir().scale(.18));
            Vec3 ahead = p.position().add(tn.dir().scale(.8));
            AABB box = p.getBoundingBox().move(ahead.subtract(p.position())).deflate(.05);
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
                BlockState st = level.getBlockState(pos);
                float hardness = st.getDestroySpeed(level, pos);
                if (st.isAir() || hardness < 0 || hardness > tn.maxHardness() || !st.getFluidState().isEmpty()) continue;
                if (BodyTraits.mayBreak(p, pos.immutable())) level.destroyBlock(pos.immutable(), true, p);
            }
            if (tn.ticksLeft() <= 1) TUNNELS.remove(id);
            else TUNNELS.put(id, new Tunnel(tn.ticksLeft() - 1, tn.dir(), tn.maxHardness()));
        }
        Long camo = CAMO.get(id);
        if (camo != null && level.getGameTime() > camo) CAMO.remove(id);
    }

    private static void breathTick(ServerPlayer p, Breath b) {
        ServerLevel level = p.serverLevel();
        JsonObject a = b.a();
        double range = num(a, "range", 4);
        double cos = Math.cos(Math.toRadians(num(a, "angle", 35)));
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        // Daño cada medio segundo (los mobs ignoran golpes más seguidos).
        boolean hurtNow = b.ticksLeft() % 10 == 0;
        for (LivingEntity e : enemiesAround(p, range + 1)) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            if (to.length() > range + e.getBbWidth() / 2 || to.normalize().dot(look) < cos || !p.hasLineOfSight(e)) continue;
            double dps = num(a, "damage_per_second", 0);
            if (hurtNow && dps > 0) beastDamage(p, e, dps * .5 * b.dmgFactor());
            int fire = (int) num(a, "fire_seconds", 0);
            if (fire > 0) e.setSecondsOnFire(fire);
            int freeze = (int) num(a, "freeze_ticks", 0);
            if (freeze > 0 && e.canFreeze()) e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 60, e.getTicksFrozen() + freeze));
            if (hurtNow) applyEffects(a, "effects", e, p, 1);
            if (hurtNow && a.has("drop_aggro") && a.get("drop_aggro").getAsBoolean() && e instanceof Mob m && m.getTarget() == p) m.setTarget(null);
        }
        ParticleOptions part = particle(a, ParticleTypes.CLOUD);
        for (int i = 0; i < 6; i++) {
            double d = .8 + level.random.nextDouble() * range;
            Vec3 at = eye.add(look.scale(d)).add((level.random.nextDouble() - .5) * d * .5, (level.random.nextDouble() - .5) * d * .4,
                    (level.random.nextDouble() - .5) * d * .5);
            level.sendParticles(part, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ camuflaje

    static boolean camouflaged(ServerPlayer p) {
        Long until = CAMO.get(p.getUUID());
        return until != null && p.level().getGameTime() <= until;
    }

    /** Atacar o usar otra habilidad rompe el camuflaje. */
    static void breakCamouflage(ServerPlayer p) {
        if (CAMO.remove(p.getUUID()) == null) return;
        MobEffectInstance inv = p.getEffect(MobEffects.INVISIBILITY);
        if (inv != null && !inv.isVisible()) p.removeEffect(MobEffects.INVISIBILITY);
    }

    /** Al salir de la forma se cortan presas, alientos, túneles y camuflaje. */
    static void clear(ServerPlayer p) {
        UUID id = p.getUUID();
        BREATHS.remove(id);
        GRAPPLES.remove(id);
        TUNNELS.remove(id);
        breakCamouflage(p);
    }

    @SubscribeEvent
    public void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof ServerPlayer p && camouflaged(p)) event.setCanceled(true);
    }

    // ------------------------------------------------------------------ proyectiles

    @SubscribeEvent
    public void onImpact(ProjectileImpactEvent event) {
        Thrown t = THROWN.remove(event.getProjectile().getId());
        if (t == null || event.getProjectile().level().isClientSide) return;
        if (!(event.getProjectile().level().getPlayerByUUID(t.owner()) instanceof ServerPlayer p)) return;
        if (event.getRayTraceResult() instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity victim) {
            if (victim == p || !Allies.isEnemy(p, victim)) {
                event.setCanceled(true);
                THROWN.put(event.getProjectile().getId(), t); // sigue volando
                return;
            }
            beastDamage(p, victim, t.damage());
            applyEffects(t.a(), "effects", victim, p, 1);
            int fire = (int) num(t.a(), "fire_seconds", 0);
            if (fire > 0) victim.setSecondsOnFire(fire);
        }
        event.setCanceled(true);
        event.getProjectile().discard();
    }

    /** Lista de efectos de una variante (para la descripción en pantalla o pruebas). */
    static JsonArray effects(JsonObject a) {
        return a.has("effects") && a.get("effects").isJsonArray() ? a.getAsJsonArray("effects") : new JsonArray();
    }
}
