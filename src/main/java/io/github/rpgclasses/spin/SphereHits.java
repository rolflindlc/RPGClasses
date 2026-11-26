package io.github.rpgclasses.spin;

import io.github.rpgclasses.compat.improvedarmor.ImprovedArmorCompat;
import io.github.rpgclasses.core.ArmorSolve;
import io.github.rpgclasses.config.ServerConfig;
import io.github.rpgclasses.content.CombatAbilities;
import io.github.rpgclasses.content.MonkCharges;
import io.github.rpgclasses.content.race.BodyTraits;
import io.github.rpgclasses.content.race.Races;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.core.stat.StatMath;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.registry.ModAttributes;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.Allies;
import io.github.rpgclasses.server.DebugLog;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lo que pasa cuando una bola impacta (adenda del Maestro del Giro, secciones 3, 4, 5 y 8): daño en pulsos mientras gira
 * clavada (unos 2 s) con el crítico decidido una vez, efecto del material (potenciado con Giro II), Jinete, Escáner,
 * Giro, durabilidad, los impactos de rebote de la Ruta de Rebotes y lo que hace en los bloques (redstone, estalactita,
 * Fricción). También las minas del Trampero y el salto de la perla de Ender.
 */
public final class SphereHits {
    public static final TagKey<net.minecraft.world.level.block.Block> STALACTITE_BREAKABLE =
            TagKey.create(Registries.BLOCK, io.github.rpgclasses.RpgClasses.id("stalactite_breakable"));
    private static final String TAG_WEAK = "rites_and_paths.weak_point.";
    private static final String TAG_COLD = "rites_and_paths.cold_eye.";

    private SphereHits() {}

    // ------------------------------------------------------------------ impacto en un enemigo

    /** La bola se clava en {@code target}: se calcula el daño del impacto, sus efectos y el primer pulso. */
    static void startSpin(SphereEntity s, ServerPlayer owner, LivingEntity target, Vec3 at) {
        Vec3 incoming = s.getDeltaMovement();
        SpinState.State st = SpinState.of(owner);
        s.setState(SphereEntity.SPINNING);
        s.stuck = target;
        s.stuckOffset = at.subtract(target.position());
        s.spinTick = 0;
        // Tiempo clavada: 1 s (el doble con la carga del clic derecho; más con la Barrena), con daño cada pocos tics.
        s.spinTicks = Math.max(1, (int) Math.round(SpinMaster.t("spin_seconds", 1.0) * 20 * s.spinTimeFactor * s.upgradeTime));
        s.pulseTicks = Math.max(1, (int) Math.round(SpinMaster.t("pulse_seconds", 0.25) * 20));
        s.hitStopTicks = (int) Math.round(SpinMaster.t("hit_stop_ticks", 3));
        s.lingerTicks = s.virtual ? 0 : s.clawLevel * SpinEnchantments.CLAW_TICKS;
        s.hitIds.add(target.getId());
        s.hitEnemy = true;
        s.lastImpact = at;
        s.setDeltaMovement(Vec3.ZERO);
        s.setSpin(st.spin);
        boolean spinII = st.spin >= SpinMaster.g("threshold_2", 0.66);
        s.materialFactor = spinII ? SpinMaster.g("threshold_2_material", 1.5) : 1.0;

        // Pulsos: uno cada pulse_seconds del tiempo clavada; Rotación añade ticks de daño (más pulsos en el mismo tiempo).
        double basePulses = s.spinTicks / (double) s.pulseTicks;
        int pulses = Math.max(1, (int) Math.ceil(basePulses * (1 + s.extraTicks) - 1.0E-6));
        // Daño total del impacto: el de los pulsos base y los ticks extra; la carga (×1,5 con la carga completa), la
        // mejora (Barrena ×1,5) y los rebotes previos de la Ruta lo suben. El Mazazo lo da todo de un golpe.
        double total = impactDamage(s, owner, target, st, spinII) * s.chargeDamage * s.upgradeTotal * (1 + s.extraTicks)
                * (1 + s.trickBonus * s.trickHits);
        if (s.oneHit) {
            pulses = 1;
            s.spinTicks = 1;
            s.lingerTicks = 0;
        }
        s.pulseHits = pulses;
        s.pulsesDone = 1;
        s.damagePerTick = (float) (total / pulses);
        s.critDecided = decideCrit(s, owner, target);
        // Empuje: solo el primer pulso (el impacto) empuja, y poco; los ticks siguientes no, para no dejar al objetivo
        // inmovilizado a base de empujones.
        s.impactKnockback = SpinMaster.t("impact_knockback", 0.15) + switch (s.material()) {
            case IRON -> SpinMaster.t("iron_knockback", 0.10);
            case NETHERITE -> SpinMaster.t("netherite_knockback", 0.25);
            default -> 0;
        } + (s.oneHit ? SpinMaster.t("crush_knockback", 0.5) : 0);

        materialEffects(s, owner, target, at, incoming, total);
        subclassEffects(s, owner, target);
        if (s.guardBreak) breakGuard(s, owner, target, at);
        if (!s.virtual) {
            SpinState.onImpact(owner, target, s.clawLevel);
            if (Bandolier.damage(owner, s.throwId, 1)) broke(s, owner);
        }
        ServerLevel level = (ServerLevel) s.level();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.45f, 1.7f + level.random.nextFloat() * 0.2f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.7f, 1.4f);
        // Temblor (lanzador y, si es un jugador, el golpeado) y onomatopeya en los golpes fuertes.
        float shake = Boolean.TRUE.equals(s.critDecided) || spinII || s.golden ? 0.35f : 0.18f;
        SpinFxSender.shake(owner, shake);
        if (target instanceof ServerPlayer hit) SpinFxSender.shake(hit, shake + 0.15f);
        if (Boolean.TRUE.equals(s.critDecided)) SpinFxSender.onomatopoeia(level, at, SpinFxSender.WORD_BAKYUN);
        else if (spinII || s.charge >= 0.999) SpinFxSender.onomatopoeia(level, at, SpinFxSender.WORD_GYARU);
        if (DebugLog.enabled()) {
            DebugLog.event(owner, "giro", "impacto {} de {} en {}: {} de daño ({} tics, {} pulsos), carga {}, crítico {}, rebotes {}+{}, ruta {}, Giro {}",
                    s.throwId, s.kind().id(), DebugLog.describe(target), String.format("%.2f", total), s.spinTicks, pulses,
                    String.format("%.2f", s.charge), s.critDecided, s.blockBounces, s.hops, s.trickHits, String.format("%.2f", st.spin));
        }
        dealTick(s, owner, target);
        if (s.oneHit || s.hitStopTicks <= 0) impactPush(s, owner, target);
        // Bola sobrecargada: giro muscular (el objetivo usa lo que lleva en la mano).
        if (s.muscle && !s.virtual && target.isAlive()) MuscleSpin.trigger(s, owner, target);
        if (s.oneHit) {
            // Mazazo: un golpe seco y la bola sigue (rebote hacia la mira o vuelta).
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 0.7f);
            SpinFxSender.onomatopoeia(level, at, SpinFxSender.WORD_DOGOON);
            s.finishSpin(owner, !target.isAlive());
        }
    }

    /**
     * Empuje del golpe que se está dando ahora (-1 = el normal de Minecraft, 0 = ninguno). Lo usa {@link #onKnockback}:
     * así el impacto empuja poco y los ticks del giro no empujan nada.
     */
    private static double knockbackOverride = -1;

    /** Hace daño con el empuje indicado en vez del de Minecraft (0 = sin empuje). */
    static void hurtWithKnockback(LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount, double knockback) {
        double before = knockbackOverride;
        knockbackOverride = Math.max(0, knockback);
        try {
            target.hurt(source, amount);
        } finally {
            knockbackOverride = before;
        }
    }

    public static void onKnockback(net.minecraftforge.event.entity.living.LivingKnockBackEvent event) {
        if (knockbackOverride < 0) return;
        if (knockbackOverride <= 0 || Races.isBoss(event.getEntity())) event.setCanceled(true);
        else event.setStrength((float) knockbackOverride);
    }

    /**
     * El único empuje del impacto, pequeño y lejos del lanzador. Se da al acabar la micro-pausa (antes la pausa lo
     * anularía); los pulsos del giro no empujan. Los jefes no se mueven.
     */
    static void impactPush(SphereEntity s, ServerPlayer owner, LivingEntity t) {
        if (s.impactKnockback <= 0 || Races.isBoss(t) || !t.isAlive()) return;
        Vec3 away = t.position().subtract(owner.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0E-4) return;
        t.knockback(s.impactKnockback, -away.x, -away.z);
        t.hurtMarked = true;
    }

    /** Un pulso del giro: una parte del daño, saltándose la invulnerabilidad del objetivo. No empuja. */
    static void dealTick(SphereEntity s, ServerPlayer owner, LivingEntity target) {
        if (s.damagePerTick <= 0) return;
        target.invulnerableTime = 0;
        hurtWithKnockback(target, SpinMaster.sphereSource(s.level(), s, owner), s.damagePerTick, 0);
        if (s.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CRIT, s.getX(), s.getY(), s.getZ(), 3, 0.12, 0.12, 0.12, 0.25);
            if (s.spinTick > 0) {
                sl.playSound(null, s.getX(), s.getY(), s.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.18f,
                        1.5f + 0.4f * s.spinTick / Math.max(1f, s.spinTicks));
            }
        }
    }

    /**
     * Ruta de Rebotes: la bola pasa por un enemigo intermedio sin clavarse. Le da un impacto de rebote (una parte del de un
     * impacto normal, más por cada rebote anterior de la ruta) que cuenta para el Giro y gasta durabilidad.
     */
    static void ricochet(SphereEntity s, ServerPlayer owner, LivingEntity target, Vec3 at) {
        SpinState.State st = SpinState.of(owner);
        boolean spinII = st.spin >= SpinMaster.g("threshold_2", 0.66);
        s.materialFactor = spinII ? SpinMaster.g("threshold_2_material", 1.5) : 1.0;
        double total = impactDamage(s, owner, target, st, spinII) * s.trickHitFactor * (1 + s.extraTicks) * (1 + s.trickBonus * s.trickHits);
        s.critDecided = decideCrit(s, owner, target);
        boolean crit = Boolean.TRUE.equals(s.critDecided);
        target.invulnerableTime = 0;
        hurtWithKnockback(target, SpinMaster.sphereSource(s.level(), s, owner), (float) total, SpinMaster.t("impact_knockback", 0.15));
        s.critDecided = null;
        s.trickHits++;
        s.hitIds.add(target.getId());
        s.hitEnemy = true;
        s.lastImpact = at;
        SpinState.onImpact(owner, target, s.clawLevel);
        if (!s.virtual && Bandolier.damage(owner, s.throwId, 1)) broke(s, owner);
        ServerLevel level = (ServerLevel) s.level();
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.35);
        level.sendParticles(ParticleTypes.WAX_OFF, at.x, at.y, at.z, 4, 0.1, 0.1, 0.1, 0.1);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.6f, 1.6f + 0.1f * s.trickHits);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.5f, 1.4f);
        SpinFxSender.shake(owner, crit ? 0.25f : 0.12f);
        if (target instanceof ServerPlayer hit) SpinFxSender.shake(hit, 0.3f);
        SpinFxSender.onomatopoeia(level, at, crit ? SpinFxSender.WORD_BAKYUN : SpinFxSender.WORD_PASHI);
        if (DebugLog.enabled()) {
            DebugLog.event(owner, "giro", "Ruta de Rebotes: rebote {} en {}: {} de daño, crítico {}", s.trickHits,
                    DebugLog.describe(target), String.format("%.2f", total), crit);
        }
    }

    /**
     * Bola cargada al máximo (rompeguardias): desactiva el escudo de un jugador como un hacha (y no lo deja volver a
     * subirlo durante {@code charge_guard_seconds}) y le pone Guardia Rota, menos armadura y dureza ese tiempo.
     */
    static void breakGuard(SphereEntity s, ServerPlayer owner, LivingEntity target, Vec3 at) {
        int ticks = (int) Math.round(SpinMaster.t("charge_guard_seconds", 4) * 20);
        if (ticks <= 0) return;
        if (target instanceof net.minecraft.world.entity.player.Player pl) {
            boolean blocking = pl.isBlocking();
            pl.disableShield(true);
            // disableShield pone 5 s de enfriamiento; se ajusta al tiempo de la Guardia Rota.
            for (net.minecraft.world.item.ItemStack h : new net.minecraft.world.item.ItemStack[] {pl.getMainHandItem(), pl.getOffhandItem()}) {
                if (h.canPerformAction(net.minecraftforge.common.ToolActions.SHIELD_BLOCK)) pl.getCooldowns().addCooldown(h.getItem(), ticks);
            }
            if (blocking) s.level().playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.0f, 0.9f);
        } else if (target.isBlocking()) {
            target.stopUsingItem();
        }
        int level = Math.max(0, (int) Math.round(Math.abs(SpinMaster.t("guard_break_armor", -0.25)) / 0.05) - 1);
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(SpinRegistry.GUARD_BREAK.get(), ticks, level, false, true, true), owner);
        ServerLevel level2 = (ServerLevel) s.level();
        level2.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.5f, 1.8f);
        level2.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 14, 0.25, 0.25, 0.25, 0.4);
        level2.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.6f, 0.3f, 0.9f), 1.3f),
                at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0);
        SpinFxSender.onomatopoeia(level2, at, SpinFxSender.WORD_DOGOON);
    }

    /** Daño total de un impacto (antes del muro, el crítico y la armadura, que pone DamagePipeline). */
    static double impactDamage(SphereEntity s, ServerPlayer owner, LivingEntity target, SpinState.State st, boolean spinII) {
        double add = s.damageBonus;
        if (spinII) add += SpinMaster.g("threshold_2_damage", 0.10);
        add += Math.min(SpinMaster.t("kinetic_max", 0.20), s.blockBounces * SpinMaster.t("kinetic_per_bounce", 0.04));
        // Jinete: daño extra según la velocidad.
        ActiveMechanic rider = SpinMaster.mech(owner, SpinMaster.SPIN_CHARGE);
        if (rider != null) {
            double thr = rider.number("speed_threshold", 0.70);
            double frac = SpinState.speedFraction(owner);
            if (frac >= thr) add += rider.number("speed_damage", 0) * Mth.clamp((frac - thr) / Math.max(0.01, 1 - thr), 0, 1);
        }
        // Ojo Constante: impactos seguidos al mismo objetivo.
        ActiveMechanic steady = SpinMaster.mech(owner, SpinMaster.STEADY_EYE);
        if (steady != null) {
            if (st.steadyTarget == target.getId()) st.steadyCount++;
            else {
                st.steadyTarget = target.getId();
                st.steadyCount = 0;
            }
            add += Math.min(steady.number("max_bonus", 0.32), steady.number("per_hit", 0.08) * st.steadyCount);
        }
        double dmg = s.baseDamage * Math.max(0.1, 1 + add);
        if (s.hops > 0) dmg *= Math.pow(s.hopKeep, s.hops);
        int chained = s.blockBounces + s.hops;
        if (s.chainFalloff > 0) dmg *= Math.pow(1 - s.chainFalloff, chained);
        if (s.elasticity > 0) dmg *= Math.pow(1 - s.elasticity, chained);
        return dmg;
    }

    /** Crítico del impacto: Punto Débil (Escáner) y Ojo Frío son seguros; si no, la probabilidad normal. */
    static boolean decideCrit(SphereEntity s, ServerPlayer owner, LivingEntity target) {
        long now = owner.level().getGameTime();
        boolean forced = false;
        // Escáner: la primera bola marca el punto débil; las siguientes son críticos seguros.
        ActiveMechanic weak = SpinMaster.mech(owner, SpinMaster.WEAK_POINT);
        if (weak != null) {
            String key = TAG_WEAK + owner.getUUID();
            var tag = target.getPersistentData();
            long until = tag.getLong(key);
            int dur = (int) Math.round(weak.number("duration_seconds", 6) * 20);
            if (until > now) {
                forced = true;
                if (AbilityService.has(owner, SpinMaster.SHARED_WEAK_POINT)) tag.putLong(key, now + dur);
            } else {
                tag.putLong(key, now + dur);
            }
            SpinState.markWeak(owner, target);
        }
        // Ojo Frío: el primer impacto a cada objetivo (con su tiempo mínimo por objetivo).
        ActiveMechanic cold = SpinMaster.mech(owner, SpinMaster.COLD_EYE);
        if (cold != null) {
            String key = TAG_COLD + owner.getUUID();
            var tag = target.getPersistentData();
            if (tag.getLong(key) <= now) {
                forced = true;
                tag.putLong(key, now + (long) Math.round(cold.number("icd_seconds", 6) * 20));
            }
        }
        if (forced) return true;
        PlayerClassData data = PlayerClassDataProvider.get(owner);
        ClassDef c = data == null ? null : DefinitionStore.SERVER.classDef(data.state.classId);
        double chance = StatMath.critChance(ServerConfig.BASE_CRIT_CHANCE.get(),
                (c == null ? 0 : c.critChanceBonus()) + ModAttributes.value(owner, ModAttributes.CRIT_CHANCE), false);
        return owner.getRandom().nextDouble() < chance;
    }

    private static void materialEffects(SphereEntity s, ServerPlayer owner, LivingEntity target, Vec3 at, Vec3 incoming, double total) {
        ServerLevel level = (ServerLevel) s.level();
        double f = s.materialFactor;
        // Fuego: Fricción y netherita no se acumulan (cuenta el mayor).
        double burn = s.frictionLevel > 0 ? SpinEnchantments.FRICTION_SECONDS[Math.min(2, s.frictionLevel)] : 0;
        if (s.material() == SphereMaterial.NETHERITE) burn = Math.max(burn, SpinMaster.t("netherite_burn_seconds", 3) * f);
        if (burn > 0 && !target.fireImmune()) target.setSecondsOnFire((int) Math.ceil(burn));
        SphereUpgrade up = s.upgrade();
        if (up != null) switch (up) {
            case COPPER -> {
                int n = (int) Math.round(SpinMaster.t("copper_targets", 2));
                double frac = SpinMaster.t("copper_fraction", 0.40) * f;
                double r = SpinMaster.t("copper_range", 6);
                List<LivingEntity> near = enemiesAround(owner, at, r, target);
                near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(at)));
                for (int i = 0; i < Math.min(n, near.size()); i++) {
                    LivingEntity e = near.get(i);
                    if (!visible(level, at, e.getBoundingBox().getCenter(), s)) continue;
                    e.invulnerableTime = 0;
                    e.hurt(SpinMaster.effectSource(level, s, owner), (float) (total * frac));
                    line(level, at, e.getBoundingBox().getCenter(), ParticleTypes.ELECTRIC_SPARK);
                }
                if (!near.isEmpty()) level.playSound(null, at.x, at.y, at.z, SoundEvents.COPPER_HIT, SoundSource.PLAYERS, 1.0f, 1.6f);
                if (level.isThundering() && level.canSeeSky(target.blockPosition())
                        && owner.getRandom().nextDouble() < SpinMaster.t("copper_lightning_chance", 0.25)) {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                    if (bolt != null) {
                        bolt.moveTo(target.getX(), target.getY(), target.getZ());
                        bolt.setCause(owner);
                        level.addFreshEntity(bolt);
                    }
                }
            }
            case AMETHYST -> {
                Vec3 dir = incoming.lengthSqr() < 1.0E-6 ? at.subtract(owner.getEyePosition()) : incoming;
                if (dir.lengthSqr() < 1.0E-6) break;
                dir = dir.normalize();
                double len = SpinMaster.t("amethyst_length", 6) * f;
                double frac = SpinMaster.t("amethyst_fraction", 0.50) * f;
                int blind = (int) Math.round(SpinMaster.t("amethyst_blind_seconds", 1) * f * 20);
                Vec3 end = at.add(dir.scale(len));
                // La onda atraviesa bloques: solo se miran las entidades a lo largo de la línea.
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, end).inflate(1.0),
                        e -> e != target && autoTarget(owner, e))) {
                    if (e.getBoundingBox().inflate(0.6).clip(at, end).isEmpty() && !e.getBoundingBox().inflate(0.6).contains(at)) continue;
                    e.invulnerableTime = 0;
                    e.hurt(SpinMaster.effectSource(level, s, owner), (float) (total * frac));
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, Math.max(1, blind), 0), owner);
                }
                for (double d = 0.5; d <= len; d += 1.0) {
                    Vec3 p = at.add(dir.scale(d));
                    level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
                level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 1.2f);
            }
            case SLIME -> level.sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.05);
            case REDSTONE -> level.sendParticles(net.minecraft.core.particles.DustParticleOptions.REDSTONE, at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0);
            default -> {
            }
        }
    }

    /** Jinete: aturde si impacta a toda velocidad (con el registro de aturdimientos del Monje, regla 6). */
    private static void subclassEffects(SphereEntity s, ServerPlayer owner, LivingEntity target) {
        ActiveMechanic rider = SpinMaster.mech(owner, SpinMaster.SPIN_CHARGE);
        if (rider != null && SpinState.speedFraction(owner) >= rider.number("speed_threshold", 0.70)) {
            int icd = (int) Math.round(rider.number("icd_seconds", 8) * 20);
            if (MonkCharges.tryStun(owner, target, icd)) {
                double seconds = rider.number("stun_seconds", 0.3) * (Races.isBoss(target) ? rider.number("boss_factor", 0.5) : 1);
                CombatAbilities.stun(target, (int) Math.max(1, Math.round(seconds * 20)));
                if (s.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.FLASH, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 1, 0, 0, 0, 0);
                }
            }
        }
    }

    private static void broke(SphereEntity s, ServerPlayer owner) {
        s.broken = true;
        owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8f, 1.0f);
        owner.displayClientMessage(Component.translatable("message.rites_and_paths.spin.broken",
                s.kind().name()).withStyle(ChatFormatting.RED), true);
    }

    // ------------------------------------------------------------------ bloques

    /**
     * Efectos al golpear un bloque: Fricción (prende el bloque), redstone (señal en el bloque golpeado) y estalactita
     * (rompe el bloque y se lleva lo que suelta). Devuelve true si la estalactita lo ha roto (la bola sigue).
     */
    static boolean blockEffects(SphereEntity s, ServerPlayer owner, BlockHitResult hit) {
        ServerLevel level = (ServerLevel) s.level();
        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getDirection();
        BlockState state = level.getBlockState(pos);
        s.lastImpact = hit.getLocation();
        if (s.frictionLevel > 0 && ServerConfig.SPHERE_FRICTION_IGNITE.get() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                && state.isFlammable(level, pos, face)) {
            BlockPos fire = pos.relative(face);
            if (level.isEmptyBlock(fire) && BaseFireBlock.canBePlacedAt(level, fire, face) && allowed(owner, level, fire)) {
                level.setBlock(fire, BaseFireBlock.getState(level, fire), 11);
            }
        }
        if (s.upgrade() == SphereUpgrade.REDSTONE && ServerConfig.SPHERE_REDSTONE.get()) {
            BlockPos sig = pos.relative(face);
            if (level.getBlockState(sig).isAir() && allowed(owner, level, sig)) {
                int ticks = (int) Math.round(SpinState.of(owner).spin >= SpinMaster.g("threshold_2", 0.66)
                        ? SpinMaster.t("redstone_signal_ticks_spin", 20) : SpinMaster.t("redstone_signal_ticks", 3));
                level.setBlock(sig, SpinRegistry.SIGNAL.get().defaultBlockState().setValue(SphereSignalBlock.FACING, face), 3);
                level.scheduleTick(sig, SpinRegistry.SIGNAL.get(), Math.max(1, ticks));
                level.sendParticles(net.minecraft.core.particles.DustParticleOptions.REDSTONE, hit.getLocation().x, hit.getLocation().y,
                        hit.getLocation().z, 8, 0.2, 0.2, 0.2, 0);
            }
        }
        if (s.upgrade() == SphereUpgrade.DRIPSTONE && ServerConfig.SPHERE_STALACTITE_BREAK.get()
                && s.blocksBroken < (int) Math.round(SpinMaster.t("dripstone_blocks", 3))) {
            float hardness = state.getDestroySpeed(level, pos);
            if (!state.isAir() && state.is(STALACTITE_BREAKABLE) && !state.hasBlockEntity() && hardness >= 0
                    && hardness <= SpinMaster.t("dripstone_hardness", 3) && BodyTraits.mayBreak(owner, pos)) {
                // "Los trae": lo que suelta (como con un pico de piedra: sin diamantes ni oro) vuelve con la bola.
                ItemStack tool = new ItemStack(Items.STONE_PICKAXE);
                List<ItemStack> drops = state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)
                        ? List.of() : Block.getDrops(state, level, pos, null, owner, tool);
                level.destroyBlock(pos, false, owner);
                s.carried.addAll(drops);
                s.blocksBroken++;
                if (!s.virtual && Bandolier.damage(owner, s.throwId, 1)) broke(s, owner);
                return true;
            }
        }
        return false;
    }

    /** Bola de redstone: pulsa botones y alterna palancas que cruza (no tienen colisión). */
    static void pressComponents(SphereEntity s, ServerPlayer owner, Vec3 a, Vec3 b) {
        if (!ServerConfig.SPHERE_REDSTONE.get()) return;
        Level level = s.level();
        BlockHitResult hit = level.clip(new ClipContext(a, b, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, s));
        if (hit.getType() == HitResult.Type.MISS) return;
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ButtonBlock) && !(state.getBlock() instanceof LeverBlock)) return;
        if (!s.toggled.add(pos.immutable()) || !allowed(owner, level, pos)) return;
        if (state.getBlock() instanceof ButtonBlock button) {
            if (!state.getValue(ButtonBlock.POWERED)) {
                button.press(state, level, pos);
                level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6f, 0.8f);
            }
        } else if (state.getBlock() instanceof LeverBlock lever) {
            lever.pull(state, level, pos);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 0.6f);
        }
    }

    /** Protecciones de terreno: zona de aparición y mods de reclamos (se pregunta como si se colocara un bloque). */
    static boolean allowed(ServerPlayer owner, Level level, BlockPos pos) {
        if (!level.mayInteract(owner, pos)) return false;
        return !ForgeEventFactory.onBlockPlace(owner, BlockSnapshot.create(level.dimension(), level, pos), Direction.UP);
    }

    // ------------------------------------------------------------------ minas (Trampero de Giro)

    static void becomeMine(SphereEntity s, ServerPlayer owner, Vec3 at) {
        ActiveMechanic m = SpinMaster.mech(owner, SpinMaster.SPIN_MINE);
        s.setState(SphereEntity.MINE);
        s.setDeltaMovement(Vec3.ZERO);
        s.setPos(at.x, at.y, at.z);
        s.mineUntil = s.level().getGameTime() + Math.round((m == null ? 30 : m.number("mine_seconds", 30)) * 20);
        s.level().playSound(null, at.x, at.y, at.z, SoundEvents.TRIPWIRE_ATTACH, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    static void checkMine(SphereEntity s, ServerPlayer owner) {
        ActiveMechanic m = SpinMaster.mech(owner, SpinMaster.SPIN_MINE);
        double r = m == null ? 2 : m.number("trigger_radius", 2);
        if (!enemiesAround(owner, s.position(), r, null).isEmpty()) explodeMine(s, owner);
    }

    /** La mina explota: daño de la mina (150 %) y Lentitud a los enemigos cercanos; luego la bola vuelve. */
    static void explodeMine(SphereEntity s, ServerPlayer owner) {
        if (s.state() != SphereEntity.MINE) return;
        ServerLevel level = (ServerLevel) s.level();
        ActiveMechanic m = SpinMaster.mech(owner, SpinMaster.SPIN_MINE);
        double r = m == null ? 2 : m.number("trigger_radius", 2);
        double factor = m == null ? 1.5 : m.number("mine_damage", 1.5);
        int slowTicks = (int) Math.round((m == null ? 2 : m.number("slow_seconds", 2)) * 20);
        int slowLevel = (int) Math.round(m == null ? 2 : m.number("slow_level", 2));
        SpinState.State st = SpinState.of(owner);
        boolean spinII = st.spin >= SpinMaster.g("threshold_2", 0.66);
        Vec3 at = s.position();
        List<LivingEntity> hit = enemiesAround(owner, at, r, null);
        for (LivingEntity e : hit) {
            double dmg = impactDamage(s, owner, e, st, spinII) * factor * (1 + s.extraTicks);
            s.critDecided = decideCrit(s, owner, e);
            e.invulnerableTime = 0;
            e.hurt(SpinMaster.sphereSource(level, s, owner), (float) dmg);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slowTicks, Math.max(0, slowLevel - 1)), owner);
        }
        s.critDecided = null;
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 20, 0.6, 0.4, 0.6, 0.4);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6f, 1.5f);
        SpinFxSender.onomatopoeia(level, at, SpinFxSender.WORD_DOGOON);
        if (!hit.isEmpty()) {
            s.hitEnemy = true;
            SpinState.onImpact(owner, hit.get(0), s.clawLevel);
            if (!s.virtual && Bandolier.damage(owner, s.throwId, 1)) broke(s, owner);
        }
        // Sigue marcada como mina: volver sin haber dado a nadie (Giro Reverso) no cuenta como fallo.
        s.setState(SphereEntity.FLYING);
        s.startReturn(owner);
    }

    // ------------------------------------------------------------------ perla de Ender

    /** El lanzador aparece en el punto de impacto (un poco hacia atrás, en un hueco donde quepa). */
    static void enderTeleport(SphereEntity s, ServerPlayer owner, Vec3 at) {
        ServerLevel level = (ServerLevel) s.level();
        Vec3 back = owner.position().subtract(at);
        back = back.lengthSqr() < 1.0E-6 ? Vec3.ZERO : back.normalize();
        for (double off : new double[] {0.7, 1.3, 2.0, 0.2}) {
            Vec3 p = at.add(back.scale(off));
            for (double dy : new double[] {0, 1, -1, 2}) {
                Vec3 c = new Vec3(p.x, Math.floor(p.y) + dy, p.z);
                AABB box = owner.getBoundingBox().move(c.subtract(owner.position()));
                if (!level.noCollision(owner, box)) continue;
                Vec3 from = owner.position();
                if (owner.isPassenger()) owner.stopRiding();
                owner.teleportTo(c.x, c.y, c.z);
                owner.resetFallDistance();
                level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f);
                level.playSound(null, c.x, c.y, c.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f);
                level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1, from.z, 24, 0.3, 0.6, 0.3, 0.2);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 1, c.z, 24, 0.3, 0.6, 0.3, 0.1);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ enganches de DamagePipeline

    /** Crítico ya decidido para el impacto de esta bola (null = no es una bola: tirada normal). */
    public static Boolean critOverride(Entity direct) {
        return direct instanceof SphereEntity s ? s.critDecided() : null;
    }

    /**
     * Armadura frente a las bolas. Devuelve el factor por el que se multiplica el daño antes de la armadura vanilla para
     * que, después de ella, quede lo que tiene que quedar:
     * <ul>
     *   <li><b>Pulsos del giro:</b> la armadura de Minecraft quita más cuanto más pequeño es el golpe, así que muchos ticks
     *       pequeños saldrían mucho peor que un impacto entero. Cada pulso recibe la misma reducción que recibiría el
     *       impacto completo de un solo golpe (la parte de armadura que le quitaría de más se ignora).</li>
     *   <li>La bola de diamante (más con Giro II) y los críticos con la rama Escaneo ignoran además parte de la armadura.</li>
     * </ul>
     */
    public static double armorFactor(ServerPlayer attacker, LivingEntity target, DamageSource source, Entity direct, double damage, boolean crit) {
        if (!(direct instanceof SphereEntity s) || damage <= 0 || source.is(DamageTypeTags.BYPASSES_ARMOR)) return 1;
        double ignored = 0;
        if (s.material() == SphereMaterial.DIAMOND) ignored += SpinMaster.t("diamond_armor", 0.20) * s.materialFactor;
        if (crit) ignored += SpinMaster.mech(attacker, SpinMaster.SCAN, "crit_pierce", 0);
        ignored += s.chargeArmor; // bola cargada (rompeguardias)
        ignored = Mth.clamp(ignored, 0, 0.9);
        int pulses = s.state() == SphereEntity.SPINNING ? Math.max(1, s.pulseHits) : 1;
        if (ignored <= 0 && pulses <= 1) return 1;
        if (target.getArmorValue() <= 0) return 1;
        // Lo que debe quedar tras la armadura: la parte de este pulso del impacto entero contra la armadura efectiva.
        double wanted = ImprovedArmorCompat.damageAfterArmor(target, source, damage * pulses, 1 - ignored) / pulses;
        // Daño antes de la armadura que deja exactamente eso (la reducción no es lineal: búsqueda binaria).
        double hi = ArmorSolve.damageBeforeArmor(d -> ImprovedArmorCompat.damageAfterArmor(target, source, d, 1), wanted,
                damage * pulses * 2 + 1);
        return Math.max(1, hi / damage);
    }

    // ------------------------------------------------------------------ utilidades

    /**
     * Objetivo para lo que apunta solo (cobre, amatista, minas, Ruta de Rebotes, Rectángulo Dorado): un enemigo que
     * además sea hostil (monstruo, jefe o mob que te persigue) o un jugador enemigo. El golpe directo vale contra
     * cualquier enemigo (lo apunta el jugador): así lo automático no se lleva aldeanos ni animales.
     */
    static boolean autoTarget(ServerPlayer owner, LivingEntity e) {
        if (e == owner || !e.isAlive() || e instanceof ArmorStand || !Allies.isEnemy(owner, e)) return false;
        if (e instanceof net.minecraft.world.entity.player.Player || e instanceof net.minecraft.world.entity.monster.Enemy || Races.isBoss(e)) return true;
        return e instanceof net.minecraft.world.entity.Mob m && m.getTarget() != null
                && (m.getTarget() == owner || io.github.rpgclasses.server.Allies.isAlly(owner, m.getTarget()));
    }

    static List<LivingEntity> enemiesAround(ServerPlayer owner, Vec3 at, double r, LivingEntity except) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : owner.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r),
                e -> e != except && autoTarget(owner, e))) {
            if (e.getBoundingBox().getCenter().distanceToSqr(at) <= (r + e.getBbWidth() * 0.5) * (r + e.getBbWidth() * 0.5)) out.add(e);
        }
        return out;
    }

    static boolean visible(Level level, Vec3 a, Vec3 b, Entity self) {
        return level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self)).getType() == HitResult.Type.MISS;
    }

    static void line(ServerLevel level, Vec3 a, Vec3 b, net.minecraft.core.particles.ParticleOptions p) {
        Vec3 d = b.subtract(a);
        int n = Math.max(2, (int) (d.length() * 3));
        for (int i = 0; i <= n; i++) {
            Vec3 q = a.add(d.scale(i / (double) n));
            level.sendParticles(p, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
    }
}
