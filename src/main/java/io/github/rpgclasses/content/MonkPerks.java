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
 * Perks y ramas extra del Monje que no van en las cargas de postura (MonkCharges) ni en Pies Ligeros
 * (MovementPerks): Impulso de Carrera, Piel Espiritual y Meditación (que al terminar da cargas, rev. 2.9).
 */
public final class MonkPerks {
    public static final String DOMINANT = MonkCharges.DOMINANT;
    public static final String MOMENTUM = "running_momentum";
    public static final String SPIRIT_SKIN = "spirit_skin";
    public static final String MEDITATION = "meditation";
    public static final String MEDITATION_VARIANT = "meditation_variant";

    /** Por jugador: ticks seguidos corriendo, impulso cargado, quietud y si está meditando. */
    private static final class State {
        int sprintTicks;
        long momentumUntil;
        long stillSince = -1;
        boolean meditating;
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

    /** Cargas de postura (buff de Fuego y efecto propio) e Impulso de Carrera (+30 % tras correr 2 s, se gasta con el golpe). */
    public static double unarmedFactor(ServerPlayer attacker, LivingEntity target) {
        double factor = MonkCharges.onUnarmedHit(attacker, target);
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

    // --- Tics ---

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        boolean momentum = MechanicService.has(player, MOMENTUM);
        ActiveMechanic meditation = MechanicService.find(player, MEDITATION);
        if (!momentum && meditation == null) {
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

        if (meditation != null) meditate(player, st, meditation, now);
        st.lastPos = player.position();
    }

    /** Meditación: agachado, quieto y en el suelo; tras el retraso cura cada segundo hasta el límite de vida. */
    private static void meditate(ServerPlayer player, State st, ActiveMechanic m, long now) {
        boolean still = player.isShiftKeyDown() && player.onGround() && st.lastPos != null
                && st.lastPos.distanceToSqr(player.position()) < 1.0E-4;
        if (!still) {
            st.stillSince = -1;
            endMeditation(player, st, m);
            return;
        }
        if (st.stillSince < 0) st.stillSince = now;
        ActiveMechanic variant = MechanicService.find(player, MEDITATION_VARIANT);
        double delay = variant != null && variant.params().has("delay_seconds") ? variant.number("delay_seconds", 4) : m.number("delay_seconds", 2);
        if (now - st.stillSince < delay * 20) return;
        st.meditating = true;

        double perSecond = m.number("heal_per_second", 0.5);
        if (variant != null && variant.params().has("heal_per_second_fixed")) perSecond = variant.number("heal_per_second_fixed", 1);
        if (variant != null) perSecond *= variant.number("heal_factor", 1);
        perSecond *= io.github.rpgclasses.gear.SetEffects.meditationFactor(player); // Peregrinaje (rev. 2.20)
        if (now - st.lastHealTick >= 20) {
            st.lastHealTick = now;
            double limitFraction = variant != null && variant.params().has("health_limit") ? variant.number("health_limit", 0.8) : m.number("health_limit", 0.6);
            float limit = (float) (player.getMaxHealth() * limitFraction);
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
                if (io.github.rpgclasses.server.EffectRules.isNegative(e.getEffect())) {
                    player.removeEffect(e.getEffect());
                    st.lastPurgeTick = now;
                    break;
                }
            }
        }
    }

    /** Rev. 2.9: al dejar de meditar se ganan cargas de la postura actual que no decaen un tiempo. */
    private static void endMeditation(ServerPlayer player, State st, ActiveMechanic m) {
        if (!st.meditating) return;
        st.meditating = false;
        if (m != null) MonkCharges.grantHeld(player, (int) Math.round(m.number("end_charges", 4)), m.number("hold_seconds", 10));
    }

    /** Recibir daño interrumpe la Meditación. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || event.getAmount() <= 0) return;
        State st = STATES.get(player.getUUID());
        if (st == null) return;
        st.stillSince = -1;
        st.lastDamageTick = player.server.getTickCount();
        endMeditation(player, st, MechanicService.find(player, MEDITATION));
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }
}
