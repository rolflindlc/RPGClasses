package io.github.rpgclasses.gear;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.content.CombatAbilities;
import io.github.rpgclasses.content.MonkCharges;
import io.github.rpgclasses.content.race.Races;
import io.github.rpgclasses.server.DamagePipeline;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mecánicas de las armas marciales (para cualquier clase que las use) y resistencia elemental por pieza:
 * <ul>
 *   <li><b>Sai:</b> guardia con el clic derecho; parry perfecto en los primeros ~0,25 s (anula el golpe, aturde al
 *       atacante y da +2 cargas al Monje), y si no, −30 % de daño. Netherita: devuelve el 50 % del golpe parado;
 *       hueso de dragón: Debilidad II; dragonsteel: estocada elemental que perfora en línea.</li>
 *   <li><b>Bo:</b> barrido en arco al 60 %. Netherita: ceniza que ciega a los mobs; hueso de dragón: salto de pértiga
 *       (agachado + saltar); dragonsteel: tras esperar 1,5 s entre golpes, onda elemental en arco.</li>
 *   <li><b>Nunchaku:</b> cada 8 golpes encadenados, Molinete en área. Hueso de dragón: nunchaku fantasma que repite
 *       3 golpes; dragonsteel: torbellino de 3 s que atrae y aplica el elemento.</li>
 *   <li><b>Nudilleras:</b> netherita: grietas que estallan a los 5 golpes; hueso de dragón: Herida (−30 % de curación);
 *       dragonsteel: elemento en cada golpe y efectos propios del Monje un 20 % más fuertes.</li>
 *   <li><b>Elemento</b> de las armas de dragonsteel en cada golpe, y <b>resistencia elemental</b> por pieza de armadura
 *       de escamas (5 %) o dragonsteel (12,5 %).</li>
 * </ul>
 * Todo el daño extra usa tipos no_proc, así que no vuelve a pasar por el pipeline ni se encadena.
 */
public final class GearEvents {
    public static final TagKey<DamageType> ELEMENT_FIRE = TagKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("element/fire"));
    public static final TagKey<DamageType> ELEMENT_ICE = TagKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("element/ice"));
    public static final TagKey<DamageType> ELEMENT_LIGHTNING = TagKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("element/lightning"));
    private static final String WOUND_UNTIL = "rites_and_paths.wound_until";
    private static final String CRACKS = "rites_and_paths.cracks.";

    private static final class State {
        int combo;
        long lastHit;
        long lastSwing;
        long riposteUntil;
        long vaultReady;
        long vaultStart;
        boolean vaulting;
        long whirlUntil;
        String whirlElement;
        final List<long[]> ghostHits = new ArrayList<>();
        UUID ghostTarget;
        float ghostDamage;
    }

    private static final Map<UUID, State> STATES = io.github.rpgclasses.server.SessionState.track(new HashMap<>());

    private static State state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    private static boolean noProc(DamageSource source) {
        return source.is(DamagePipeline.NO_PROC);
    }

    // ------------------------------------------------------------------ sai: guardia y parry

    private static MartialWeaponItem guardingSai(ServerPlayer player) {
        if (!player.isUsingItem()) return null;
        ItemStack using = player.getUseItem();
        return using.getItem() instanceof MartialWeaponItem m && m.kind == WeaponKind.SAI ? m : null;
    }

    private static boolean meleeFromFront(ServerPlayer victim, DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker) return false;
        if (source.getDirectEntity() instanceof Projectile) return false;
        Vec3 to = attacker.position().subtract(victim.position());
        Vec3 look = victim.getLookAngle();
        return new Vec3(to.x, 0, to.z).normalize().dot(new Vec3(look.x, 0, look.z).normalize()) > 0.2;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttacked(LivingAttackEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer victim) || victim instanceof FakePlayer) return;
        MartialWeaponItem sai = guardingSai(victim);
        if (sai == null || !meleeFromFront(victim, event.getSource())) return;
        if (victim.getTicksUsingItem() > sai.parryWindowTicks()) return; // la guardia normal se aplica en onHurt
        LivingEntity attacker = (LivingEntity) event.getSource().getEntity();
        event.setCanceled(true);
        CombatAbilities.stun(attacker, 10 + (sai.tier == WeaponTier.IRON ? 2 : 0));
        int late = sai.tier.lateTier();
        if (late >= 1) attacker.hurt(GearFx.source(victim, GearFx.MARTIAL), event.getAmount() * 0.5f);
        if (late >= 2) attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1), victim);
        if (late >= 3) {
            state(victim).riposteUntil = victim.level().getGameTime() + 30;
            GearFx.burst(victim, GearFx.elementParticle(sai.tier.element), 12, 0.08);
        }
        if (GearClass.MONK.is(victim)) MonkCharges.grant(victim, 2);
        GearFx.burst(attacker, ParticleTypes.CRIT, 16, 0.3);
        victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.5f, 1.9f);
        victim.stopUsingItem();
    }

    // ------------------------------------------------------------------ golpes con armas marciales

    @SubscribeEvent
    public void onSwing(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        State st = state(p);
        long now = p.level().getGameTime();
        // Bo de dragonsteel: «golpe cargado» = esperar al menos 1,5 s desde el golpe anterior.
        if (p.getMainHandItem().getItem() instanceof MartialWeaponItem m && m.kind == WeaponKind.BO && m.tier.lateTier() >= 3
                && now - st.lastSwing >= 30 && p.getAttackStrengthScale(0.5f) >= 0.95f) {
            p.getPersistentData().putLong("rites_and_paths.bo_wave", now);
        }
        st.lastSwing = now;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        LivingEntity target = event.getEntity();

        // Resistencia elemental por pieza (cualquier clase).
        if (target instanceof ServerPlayer victim && !noProc(source)) {
            double resist = elementResistance(victim, source);
            if (resist > 0) event.setAmount(event.getAmount() * (float) (1 - resist));
            MartialWeaponItem sai = guardingSai(victim);
            if (sai != null && meleeFromFront(victim, source)) {
                event.setAmount(event.getAmount() * (float) (1 - sai.guardReduction()));
                victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.6f, 1.4f);
            }
        }

        if (noProc(source) || !(source.getEntity() instanceof ServerPlayer attacker) || source.getDirectEntity() != attacker
                || attacker instanceof FakePlayer || event.getAmount() <= 0) return;
        ItemStack main = attacker.getMainHandItem();
        float hit = event.getAmount();
        State st = state(attacker);
        long now = attacker.level().getGameTime();

        if (main.getItem() instanceof MartialWeaponItem weapon) {
            if (weapon.tier.element != null) GearFx.applyElement(attacker, target, weapon.tier.element, 1);
            switch (weapon.kind) {
                case BO -> boHit(attacker, weapon, target, hit, now);
                case NUNCHAKU -> nunchakuHit(attacker, st, weapon, target, hit, now);
                case SAI -> {
                    if (st.riposteUntil >= now) {
                        st.riposteUntil = 0;
                        for (LivingEntity e : GearFx.lineTargets(attacker, 4)) {
                            if (e != target) e.hurt(GearFx.source(attacker, GearFx.elementType(weapon.tier.element)), hit * 0.6f);
                            GearFx.applyElement(attacker, e, weapon.tier.element, 1);
                        }
                        event.setAmount(hit * 1.5f);
                        GearFx.ray(attacker, GearFx.elementParticle(weapon.tier.element), 4, 10);
                    }
                }
                default -> { }
            }
        } else if (main.isEmpty()) {
            KnucklesItem k = GearRules.knuckles(attacker);
            if (k != null) knucklesHit(attacker, k, target, hit, now);
        }
        st.lastHit = now;
    }

    private static void boHit(ServerPlayer attacker, MartialWeaponItem bo, LivingEntity target, float hit, long now) {
        double reach = attacker.getEntityReach() + 0.5;
        List<LivingEntity> swept = GearFx.cone(attacker, reach, bo.sweepDegrees(), target);
        DamageSource src = GearFx.source(attacker, GearFx.MARTIAL);
        for (LivingEntity e : swept) {
            e.hurt(src, hit * 0.6f);
            e.knockback(0.4, attacker.getX() - e.getX(), attacker.getZ() - e.getZ());
            if (bo.tier.lateTier() >= 1 && e instanceof Mob) e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0), attacker);
        }
        if (attacker.level() instanceof ServerLevel level) {
            Vec3 at = attacker.position().add(attacker.getLookAngle().scale(1.5)).add(0, 1, 0);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            if (bo.tier.lateTier() >= 1) GearFx.coneFx(attacker, ParticleTypes.ASH, reach, bo.sweepDegrees());
        }
        long charged = attacker.getPersistentData().getLong("rites_and_paths.bo_wave");
        if (bo.tier.lateTier() >= 3 && charged == now) {
            attacker.getPersistentData().remove("rites_and_paths.bo_wave");
            for (LivingEntity e : GearFx.cone(attacker, 6, 120, null)) {
                e.hurt(GearFx.source(attacker, GearFx.elementType(bo.tier.element)), hit * 0.6f);
                GearFx.applyElement(attacker, e, bo.tier.element, 1);
            }
            GearFx.coneFx(attacker, GearFx.elementParticle(bo.tier.element), 6, 120);
            attacker.level().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 0.9f, 1.2f);
        }
    }

    private static void nunchakuHit(ServerPlayer attacker, State st, MartialWeaponItem n, LivingEntity target, float hit, long now) {
        st.combo = now - st.lastHit <= 40 ? st.combo + 1 : 1;
        if (n.tier.lateTier() < 1 || st.combo < 8) return;
        st.combo = 0;
        // Molinete: golpe en área alrededor y círculo de brasas.
        for (LivingEntity e : GearFx.enemiesAround(attacker, attacker.position(), 2.5)) {
            if (e != target) e.hurt(GearFx.source(attacker, GearFx.MARTIAL), hit * 0.5f);
        }
        ServerLevel level = (ServerLevel) attacker.level();
        GearFx.ring(level, attacker.position(), GearFx.EMBER, 2.5, 30);
        level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.5f);
        if (n.tier.lateTier() >= 2) { // nunchaku fantasma: 3 golpes al 30 % en los siguientes 15 tics
            st.ghostTarget = target.getUUID();
            st.ghostDamage = hit * 0.3f;
            st.ghostHits.clear();
            for (int i = 1; i <= 3; i++) st.ghostHits.add(new long[]{now + i * 5});
        }
        if (n.tier.lateTier() >= 3) {
            st.whirlUntil = now + 60;
            st.whirlElement = n.tier.element;
        }
    }

    private static void knucklesHit(ServerPlayer attacker, KnucklesItem k, LivingEntity target, float hit, long now) {
        int late = k.tier.lateTier();
        if (late >= 1) {
            String key = CRACKS + attacker.getUUID();
            int cracks = target.getPersistentData().getInt(key) + 1;
            GearFx.burst(target, GearFx.OBSIDIAN, 4, 0.02);
            if (cracks >= 5) {
                cracks = 0;
                for (LivingEntity e : GearFx.enemiesAround(attacker, target.position(), 2)) {
                    e.hurt(GearFx.source(attacker, GearFx.MARTIAL), hit * 0.3f);
                }
                GearFx.burst(target, ParticleTypes.EXPLOSION, 1, 0);
            }
            target.getPersistentData().putInt(key, cracks);
        }
        if (late >= 2 && attacker.getRandom().nextDouble() < 0.25) {
            target.getPersistentData().putLong(WOUND_UNTIL, now + 80);
            GearFx.burst(target, GearFx.BONE, 6, 0.02);
        }
        if (late >= 3) GearFx.applyElement(attacker, target, k.tier.element, 1);
    }

    /** Herida de las nudilleras de hueso de dragón: −30 % de curación durante 4 s. */
    @SubscribeEvent
    public void onHeal(LivingHealEvent event) {
        long until = event.getEntity().getPersistentData().getLong(WOUND_UNTIL);
        if (until > 0 && event.getEntity().level().getGameTime() <= until) event.setAmount(event.getAmount() * 0.7f);
    }

    /** Fuerza extra de los efectos propios del Monje con nudilleras de dragonsteel (1 sin ellas). */
    public static double finisherStrength(ServerPlayer monk) {
        if (!monk.getMainHandItem().isEmpty()) return 1;
        KnucklesItem k = GearRules.knuckles(monk);
        return k != null && k.tier.lateTier() >= 3 ? 1.2 : 1;
    }

    // ------------------------------------------------------------------ bo: salto de pértiga

    @SubscribeEvent
    public void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.isShiftKeyDown()) return;
        if (!(p.getMainHandItem().getItem() instanceof MartialWeaponItem m) || m.kind != WeaponKind.BO || m.tier.lateTier() < 2) return;
        State st = state(p);
        long now = p.level().getGameTime();
        if (now < st.vaultReady) return;
        st.vaultReady = now + 160;
        st.vaultStart = now;
        st.vaulting = true;
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        p.setDeltaMovement(flat.scale(1.1).add(0, 0.75, 0));
        p.hurtMarked = true;
        p.fallDistance = 0;
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BAMBOO_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        State st = STATES.get(p.getUUID());
        if (st == null) return;
        long now = p.level().getGameTime();
        ServerLevel level = (ServerLevel) p.level();
        if (st.vaulting) {
            p.fallDistance = 0;
            if (p.onGround() && now - st.vaultStart > 5) {
                st.vaulting = false;
                for (LivingEntity e : GearFx.enemiesAround(p, p.position(), 3)) {
                    e.knockback(1.0, p.getX() - e.getX(), p.getZ() - e.getZ());
                    e.hurtMarked = true;
                }
                GearFx.ring(level, p.position(), ParticleTypes.CLOUD, 3, 24);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.4f, 1.6f);
            }
        }
        if (!st.ghostHits.isEmpty() && st.ghostTarget != null && level.getEntity(st.ghostTarget) instanceof LivingEntity ghost) {
            if (st.ghostHits.get(0)[0] <= now) {
                st.ghostHits.remove(0);
                if (ghost.isAlive()) {
                    ghost.invulnerableTime = 0;
                    ghost.hurt(GearFx.source(p, GearFx.MARTIAL), st.ghostDamage);
                    GearFx.burst(ghost, GearFx.BONE, 8, 0.05);
                }
            }
        } else if (!st.ghostHits.isEmpty()) {
            st.ghostHits.clear();
        }
        if (st.whirlUntil >= now && now % 10 == 0) {
            for (LivingEntity e : GearFx.enemiesAround(p, p.position(), 3)) {
                Vec3 pull = p.position().subtract(e.position()).normalize().scale(Races.isBoss(e) ? 0.05 : 0.25);
                e.push(pull.x, 0.05, pull.z);
                e.hurtMarked = true;
                GearFx.applyElement(p, e, st.whirlElement, 0.5);
            }
            for (int i = 0; i < 3; i++) {
                double a = (now * 0.6 + i * 2.1);
                double r = 1.2 + i * 0.6;
                level.sendParticles(GearFx.elementDust(st.whirlElement), p.getX() + Math.cos(a) * r, p.getY() + 0.3 + i * 0.5,
                        p.getZ() + Math.sin(a) * r, 2, 0.05, 0.05, 0.05, 0);
            }
        }
    }

    // ------------------------------------------------------------------ resistencia elemental

    /** Fracción de daño elemental que evitan las piezas de escamas (5 %) o dragonsteel (12,5 %) de ese elemento. */
    public static double elementResistance(Player player, DamageSource source) {
        String element = source.is(ELEMENT_FIRE) ? "fire" : source.is(ELEMENT_ICE) ? "ice" : source.is(ELEMENT_LIGHTNING) ? "lightning" : null;
        if (element == null) return 0;
        double total = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (player.getItemBySlot(slot).getItem() instanceof ClassArmorItem a && element.equals(a.tier.element)) {
                total += a.tier.isDragonsteel() ? 0.125 : 0.05;
            }
        }
        return Math.min(0.5, total);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }
}
