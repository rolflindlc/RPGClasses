package io.github.rpgclasses.content;

import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.registry.ModAttributes;
import io.github.rpgclasses.server.MechanicService;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ramas extra y perks del Monje (rev. 2.7c), salvo lo que va en las posturas (MonkAbilities) y Pies Ligeros
 * (MovementPerks): Flujo (daño), Onda Elemental, Impulso de Carrera, Piel Espiritual, Calma Interior y Meditación.
 */
public final class MonkPerks {
    public static final String FLOW = "flow";
    public static final String ELEMENTAL_WAVE = "elemental_wave";
    public static final String MASTERY = "stance_mastery";
    public static final String DOMINANT = "dominant_stance";
    public static final String HYBRID = "hybrid_stance";
    public static final String MOMENTUM = "running_momentum";
    public static final String SPIRIT_SKIN = "spirit_skin";
    public static final String INNER_CALM = "inner_calm";
    public static final String MEDITATION = "meditation";
    public static final String MEDITATION_VARIANT = "meditation_variant";

    /** Por jugador: ticks seguidos corriendo, impulso cargado, reserva de Calma Interior, quietud y último daño. */
    private static final class State {
        int sprintTicks;
        long momentumUntil;
        double calm;
        long stillSince = -1;
        long lastHealTick;
        long lastPurgeTick;
        long lastDamageTick = -1000;
        Vec3 lastPos;
    }
    private static final Map<UUID, State> STATES = new HashMap<>();

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), k -> new State());
    }

    // --- Daño (llamado desde DamagePipeline en golpes desarmados) ---

    /** Flujo (−5 %) e Impulso de Carrera (+30 % tras correr 2 s, se gasta con el golpe). */
    public static double unarmedFactor(ServerPlayer attacker, LivingEntity target) {
        double factor = 1 + MechanicService.number(attacker, FLOW, "unarmed_damage_change", 0);
        ActiveMechanic momentum = MechanicService.find(attacker, MOMENTUM);
        State st = STATES.get(attacker.getUUID());
        if (momentum != null && st != null && st.momentumUntil >= attacker.server.getTickCount()) {
            st.momentumUntil = 0;
            factor *= 1 + momentum.number("damage_bonus", 0.3);
            // Empuje extra [MEDIR]: knockback() empuja en sentido contrario al vector dado.
            double strength = 0.4 * momentum.number("knockback_bonus", 0.5);
            target.knockback(strength, attacker.getX() - target.getX(), attacker.getZ() - target.getZ());
        }
        return factor;
    }

    /** Piel Espiritual: menos daño mágico y elemental, con un tope de reducción combinada. */
    public static double spiritSkinFactor(ServerPlayer victim, DamageSource source, double currentFactor) {
        ActiveMechanic m = MechanicService.find(victim, SPIRIT_SKIN);
        if (m == null || !isMagicOrElemental(source)) return currentFactor;
        double f = currentFactor * (1 - m.number("magic_reduction", 0.3));
        return Math.max(f, 1 - m.number("combined_cap", 0.85));
    }

    private static boolean isMagicOrElemental(DamageSource source) {
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_FREEZING) || source.is(DamageTypeTags.IS_LIGHTNING)
                || source.is(DamageTypes.WITHER) || source.is(DamageTypes.DRAGON_BREATH) || source.is(DamageTypes.SONIC_BOOM)) return true;
        return source.typeHolder().unwrapKey().map(k -> "irons_spellbooks".equals(k.location().getNamespace())).orElse(false);
    }

    // --- Onda Elemental (al cambiar de postura) ---

    static void elementalWave(ServerPlayer player, String stance, ActiveMechanic wave) {
        double radius = wave.number("radius", 3);
        double unarmedHit = player.getAttributeValue(Attributes.ATTACK_DAMAGE) + ModAttributes.value(player, ModAttributes.UNARMED_DAMAGE);
        float damage = (float) (unarmedHit * wave.number("damage_fraction", 0.75));
        DamageSource source = new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(PaladinPerks.SHOCKWAVE_DAMAGE), player);
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> e != player && e.isAlive() && !e.isAlliedTo(player) && e.distanceToSqr(player) <= radius * radius)) {
            if (e instanceof ServerPlayer other && !player.canHarmPlayer(other)) continue;
            if (damage > 0) e.hurt(source, damage);
            switch (stance) {
                case "fire" -> e.setSecondsOnFire(3);
                case "water" -> {
                    e.knockback(1.0, player.getX() - e.getX(), player.getZ() - e.getZ()); // ~3 bloques [MEDIR]
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), player);
                }
                case "stone" -> e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0), player);
                default -> { }
            }
        }
        if (player.level() instanceof ServerLevel level) {
            var particle = switch (stance) {
                case "fire" -> ParticleTypes.FLAME;
                case "water" -> ParticleTypes.SPLASH;
                case "stone" -> ParticleTypes.CRIT;
                default -> ParticleTypes.CLOUD;
            };
            level.sendParticles(particle, player.getX(), player.getY() + 0.5, player.getZ(), 30, radius / 2, 0.2, radius / 2, 0.05);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    // --- Tics ---

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        MonkAbilities.tickLinger(player);
        boolean momentum = MechanicService.has(player, MOMENTUM);
        ActiveMechanic calm = MechanicService.find(player, INNER_CALM);
        ActiveMechanic meditation = MechanicService.find(player, MEDITATION);
        if (!momentum && calm == null && meditation == null) {
            STATES.remove(player.getUUID());
            return;
        }
        State st = state(player);
        long now = player.server.getTickCount();

        if (momentum) {
            ActiveMechanic m = MechanicService.find(player, MOMENTUM);
            st.sprintTicks = player.isSprinting() ? st.sprintTicks + 1 : 0;
            if (st.sprintTicks >= m.number("sprint_seconds", 2) * 20) st.momentumUntil = now + 40; // dura 2 s tras dejar de correr
        }

        // Calma Interior: la reserva crece mientras no recibe daño (1 s de margen tras el último golpe).
        if (calm != null) {
            if (now - st.lastDamageTick >= 20) {
                st.calm = Math.min(calm.number("absorb_max", 5), st.calm + calm.number("absorb_per_second", 0.5) / 20.0);
            }
        } else {
            st.calm = 0;
        }

        if (meditation != null) meditate(player, st, meditation, now);
        st.lastPos = player.position();
    }

    /** Meditación: agachado, quieto y en el suelo; tras el retraso cura cada segundo hasta el límite de vida. */
    private static void meditate(ServerPlayer player, State st, ActiveMechanic m, long now) {
        boolean still = player.isShiftKeyDown() && player.onGround() && st.lastPos != null
                && st.lastPos.distanceToSqr(player.position()) < 1.0E-4;
        if (!still) {
            st.stillSince = -1;
            return;
        }
        if (st.stillSince < 0) st.stillSince = now;
        ActiveMechanic variant = MechanicService.find(player, MEDITATION_VARIANT);
        double delay = variant != null && variant.params().has("delay_seconds") ? variant.number("delay_seconds", 4) : m.number("delay_seconds", 2);
        if (now - st.stillSince < delay * 20) return;

        double perSecond = m.number("heal_per_second", 0.5);
        if (variant != null && variant.params().has("heal_per_second_fixed")) perSecond = variant.number("heal_per_second_fixed", 1);
        if (variant != null) perSecond *= variant.number("heal_factor", 1);
        if (now - st.lastHealTick >= 20) {
            st.lastHealTick = now;
            float limit = (float) (player.getMaxHealth() * m.number("health_limit", 0.6));
            if (player.getHealth() < limit && perSecond > 0) {
                player.heal((float) Math.min(perSecond, limit - player.getHealth()));
            }
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.4, 0.4, 0.4, 0.2);
            }
        }
        // Mente Clara: purga un efecto negativo cada N segundos (también Veneno y Wither).
        if (variant != null && variant.params().has("purge_interval_seconds")
                && now - st.lastPurgeTick >= variant.number("purge_interval_seconds", 5) * 20) {
            for (MobEffectInstance e : player.getActiveEffects()) {
                if (e.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                    player.removeEffect(e.getEffect());
                    st.lastPurgeTick = now;
                    break;
                }
            }
        }
    }

    /** Recibir daño interrumpe la Meditación; Calma Interior absorbe con su reserva. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || event.getAmount() <= 0) return;
        State st = STATES.get(player.getUUID());
        if (st == null) return;
        st.stillSince = -1;
        st.lastDamageTick = player.server.getTickCount();
        if (st.calm > 0 && MechanicService.has(player, INNER_CALM)) {
            float absorbed = (float) Math.min(st.calm, event.getAmount());
            st.calm -= absorbed;
            event.setAmount(event.getAmount() - absorbed);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.8f, 1.5f);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
        MonkAbilities.forget(event.getEntity().getUUID());
    }
}
