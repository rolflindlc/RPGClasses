package io.github.rpgclasses.content;

import com.google.gson.JsonObject;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.registry.ModEffects;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.MechanicService;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Subclases del Paladín (rev. 2.7d):
 * <ul>
 *   <li>Guardián Bendito — Baluarte Sanador: parte del daño que mitiga Baluarte cura al Paladín y a sus aliados.</li>
 *   <li>Bastión Inflexible — Tenacidad (mecánica {@code tenacity}) y Baluarte Incorruptible (purga + inmunidad).</li>
 *   <li>Juggernaut de Carga — Baluarte de Embestida: Velocidad III, carrera forzada (cliente) y daño de contacto en área.</li>
 * </ul>
 * La reducción de Baluarte y de Tenacidad se aplica en DamagePipeline.onDefend; la inmunidad, en EffectRules.
 */
public final class PaladinSubclasses {
    public static final String HEALING_BULWARK = Ids.NS + ":healing_bulwark";
    public static final String INCORRUPTIBLE_BULWARK = Ids.NS + ":incorruptible_bulwark";
    public static final String CHARGING_BULWARK = Ids.NS + ":charging_bulwark";
    public static final String TENACITY = "tenacity";

    /** Tenacidad: tick en que se abrió la ventana de reducción. */
    private static final Map<UUID, Long> TENACITY_OPENED = new HashMap<>();
    /** Baluarte de Embestida: último tick en que cada entidad fue arrollada, por Paladín. */
    private static final Map<UUID, Map<Integer, Long>> TRAMPLED = new HashMap<>();

    /** Llamado al activar Baluarte (ya aplicado su efecto). */
    public static void onBulwark(ServerPlayer player, int ticks) {
        if (AbilityService.has(player, INCORRUPTIBLE_BULWARK)) {
            List<MobEffect> harmful = new ArrayList<>();
            for (MobEffectInstance i : player.getActiveEffects()) {
                MobEffect e = i.getEffect();
                if (e.getCategory() == MobEffectCategory.HARMFUL && !(e instanceof ModEffects.StateEffect)) harmful.add(e);
            }
            harmful.forEach(player::removeEffect);
            player.clearFire();
        }
        if (AbilityService.has(player, CHARGING_BULWARK)) {
            int level = (int) AbilityService.number(AbilityService.params(CHARGING_BULWARK), "speed_level", 3);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, Math.max(0, level - 1)));
            player.addEffect(new MobEffectInstance(ModEffects.CHARGING_BULWARK.get(), ticks, 0));
            TRAMPLED.remove(player.getUUID());
        }
    }

    /** Baluarte Incorruptible activo: inmune a efectos negativos nuevos. */
    public static boolean incorruptible(ServerPlayer player) {
        return player.hasEffect(ModEffects.BULWARK.get()) && AbilityService.has(player, INCORRUPTIBLE_BULWARK);
    }

    /**
     * Baluarte Sanador: {@code mitigated} es el daño que ha quitado Baluarte en este golpe.
     * Cura una fracción (30–50 % según el nivel de subclase) al Paladín y a cada aliado en el radio (cantidad entera, máx. 6).
     */
    public static void healingBulwark(ServerPlayer paladin, double mitigated) {
        if (mitigated <= 0 || !AbilityService.has(paladin, HEALING_BULWARK)) return;
        JsonObject p = AbilityService.params(HEALING_BULWARK);
        double fraction = lerp(AbilityService.number(p, "heal_fraction_min", 0.3), AbilityService.number(p, "heal_fraction_max", 0.5),
                subclassProgress(paladin));
        float heal = (float) (mitigated * fraction);
        if (heal <= 0) return;
        double radius = AbilityService.number(p, "radius", 12);
        int maxAllies = (int) AbilityService.number(p, "max_allies", 6);
        paladin.heal(heal);
        List<ServerPlayer> allies = new ArrayList<>();
        for (ServerPlayer other : paladin.serverLevel().players()) {
            if (other == paladin || other instanceof FakePlayer || !other.isAlive() || other.isSpectator()) continue;
            if (!BannerAura.isAlly(paladin, other) || other.distanceToSqr(paladin) > radius * radius) continue;
            if (other.getHealth() >= other.getMaxHealth()) continue;
            allies.add(other);
        }
        allies.sort(Comparator.comparingDouble(o -> o.distanceToSqr(paladin)));
        for (int i = 0; i < Math.min(maxAllies, allies.size()); i++) {
            ServerPlayer ally = allies.get(i);
            ally.heal(heal);
            ally.serverLevel().sendParticles(ParticleTypes.HEART, ally.getX(), ally.getY() + 1.8, ally.getZ(), 1, 0.2, 0.1, 0.2, 0);
        }
    }

    /**
     * Tenacidad: tras un golpe se abre una ventana (1 s) en la que los golpes siguientes hacen −50 %.
     * Los golpes reducidos no alargan la ventana: un daño continuo no queda reducido para siempre.
     */
    public static double tenacityFactor(ServerPlayer victim) {
        ActiveMechanic m = MechanicService.find(victim, TENACITY);
        if (m == null) return 1;
        long now = victim.level().getGameTime();
        Long opened = TENACITY_OPENED.get(victim.getUUID());
        long window = (long) m.number("window_ticks", 20);
        if (opened != null && now > opened && now - opened <= window) return 1 - m.number("reduction", 0.5);
        TENACITY_OPENED.put(victim.getUUID(), now);
        return 1;
    }

    /** Baluarte de Embestida: daño y empuje en área a lo que el Paladín arrolla mientras corre. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        if (!player.hasEffect(ModEffects.CHARGING_BULWARK.get())) {
            TRAMPLED.remove(player.getUUID());
            return;
        }
        if (!player.isAlive()) return;
        player.setSprinting(true);
        JsonObject p = AbilityService.params(CHARGING_BULWARK);
        float damage = (float) AbilityService.number(p, "contact_damage", 3);
        double radius = AbilityService.number(p, "knockback_radius", 2.5);
        double strength = AbilityService.number(p, "knockback_strength", 1.0);
        long icd = (long) AbilityService.number(p, "hit_icd_ticks", 10);
        long now = player.level().getGameTime();

        List<LivingEntity> touched = player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.6),
                e -> CombatAbilities.canHit(player, e));
        if (touched.isEmpty()) return;
        Map<Integer, Long> hits = TRAMPLED.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        boolean any = false;
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> CombatAbilities.canHit(player, e) && e.distanceToSqr(player) <= radius * radius)) {
            Long last = hits.get(target.getId());
            if (last != null && now - last < icd) continue;
            hits.put(target.getId(), now);
            any = true;
            if (damage > 0) target.hurt(CombatAbilities.chargeDamage(player), damage);
            Vec3 away = target.position().subtract(player.position());
            if (strength > 0 && away.horizontalDistanceSqr() > 1.0E-4) target.knockback(strength, -away.x, -away.z);
        }
        if (any) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                    SoundSource.PLAYERS, 1.0f, 0.8f);
            hits.values().removeIf(t -> now - t > 200);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TENACITY_OPENED.remove(event.getEntity().getUUID());
        TRAMPLED.remove(event.getEntity().getUUID());
    }

    /** 0 sin subclase, 1 con la subclase al nivel 10. */
    private static double subclassProgress(ServerPlayer player) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        return data == null ? 0 : Math.min(1.0, data.state.subclassLevel / 10.0);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
