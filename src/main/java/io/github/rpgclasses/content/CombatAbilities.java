package io.github.rpgclasses.content;

import com.google.gson.JsonObject;
import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.registry.ModEffects;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.MechanicService;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Habilidades de las clases base de Bárbaro y Paladín. Los efectos sobre el daño
 * (Baluarte, Furia Berserker, Guardia Firme, reducción de Provocación) se aplican en DamagePipeline.
 */
public final class CombatAbilities {
    public static final String TAUNT = Ids.NS + ":taunt";
    public static final String STEADY_GUARD = Ids.NS + ":steady_guard";
    public static final String BULWARK = Ids.NS + ":bulwark";
    public static final String BLOODTHIRST = Ids.NS + ":bloodthirst";
    public static final String CHARGE = Ids.NS + ":charge";
    public static final String BERSERKER_RAGE = Ids.NS + ":berserker_rage";

    public static final TagKey<EntityType<?>> TAUNT_IMMUNE = TagKey.create(Registries.ENTITY_TYPE, RpgClasses.id("taunt_immune"));
    public static final TagKey<EntityType<?>> STUN_IMMUNE = TagKey.create(Registries.ENTITY_TYPE, RpgClasses.id("stun_immune"));

    /** Daño propio de Embestida: está en el tag no_proc, así que no recibe críticos ni otros bonus. */
    public static final ResourceKey<DamageType> CHARGE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("charge"));

    /** Estado de una Embestida en curso. */
    private record Charging(int untilTick, Vec3 direction, double speed, Set<Integer> alreadyHit) {}

    private static final Map<UUID, Charging> CHARGING = new HashMap<>();
    private static final int CHARGE_WINDOW_TICKS = 12;
    private static final int STUN_TICKS = 40;

    public static void register() {
        AbilityService.register(TAUNT, CombatAbilities::taunt);
        AbilityService.register(BULWARK, CombatAbilities::bulwark);
        AbilityService.register(CHARGE, CombatAbilities::charge);
        AbilityService.register(BERSERKER_RAGE, CombatAbilities::berserkerRage);
        // Estampida (−25 %) y Carga Demoledora (+30 %) se multiplican: 20 s -> 15 s -> 19,5 s.
        AbilityService.registerCooldownModifier(CHARGE, player ->
                (1 + MechanicService.number(player, BarbarianPerks.CHARGE_UPGRADE, "cooldown_change", 0))
                        * (1 + MechanicService.number(player, BarbarianPerks.CRUSHING_CHARGE, "cooldown_change", 0)));
    }

    // --- Paladín ---

    private static boolean taunt(ServerPlayer player, JsonObject p) {
        double radius = AbilityService.number(p, "radius", 20);
        int slowTicks = AbilityService.ticks(p, "slow_seconds", 3);
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius),
                m -> m.isAlive() && !m.getType().is(TAUNT_IMMUNE) && !m.isAlliedTo(player))) {
            mob.setTarget(player);
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slowTicks, 0));
        }
        player.addEffect(new MobEffectInstance(ModEffects.TAUNT_GUARD.get(), AbilityService.ticks(p, "reduction_seconds", 4), 0));
        sound(player, SoundEvents.RAVAGER_ROAR);
        return true;
    }

    private static boolean bulwark(ServerPlayer player, JsonObject p) {
        double t = subclassProgress(player);
        double seconds = lerp(AbilityService.number(p, "duration_min_seconds", 6), AbilityService.number(p, "duration_max_seconds", 8), t);
        player.addEffect(new MobEffectInstance(ModEffects.BULWARK.get(), (int) Math.round(seconds * 20), 0));
        sound(player, SoundEvents.SHIELD_BLOCK);
        return true;
    }

    /** Reducción de Baluarte: del mínimo al máximo según el nivel de subclase. */
    public static double bulwarkReduction(ServerPlayer player) {
        JsonObject p = AbilityService.params(BULWARK);
        return lerp(AbilityService.number(p, "damage_reduction_min", 0.4), AbilityService.number(p, "damage_reduction_max", 0.5),
                subclassProgress(player));
    }

    // --- Bárbaro ---

    private static boolean charge(ServerPlayer player, JsonObject p) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) return false; // mirando recto arriba o abajo
        // Velocidad inicial aproximada para recorrer la distancia configurada [MEDIR].
        double distance = AbilityService.number(p, "distance", 6)
                * (1 + MechanicService.number(player, BarbarianPerks.CHARGE_UPGRADE, "distance_bonus", 0));
        double speed = distance * 0.25;
        player.setDeltaMovement(flat.normalize().scale(speed).add(0, 0.15, 0));
        player.hurtMarked = true; // envía la velocidad al cliente
        CHARGING.put(player.getUUID(), new Charging(player.server.getTickCount() + CHARGE_WINDOW_TICKS, flat.normalize(), speed, new HashSet<>()));
        sound(player, SoundEvents.PLAYER_ATTACK_SWEEP);
        return true;
    }

    private static boolean berserkerRage(ServerPlayer player, JsonObject p) {
        player.addEffect(new MobEffectInstance(ModEffects.BERSERKER_RAGE.get(), AbilityService.ticks(p, "duration_seconds", 10), 0));
        // Rev. 2.7b: Debilidad I a los enemigos cercanos (hostiles, o mobs y jugadores que no sean aliados).
        double radius = AbilityService.number(p, "weakness_radius", 6);
        int weaknessTicks = AbilityService.ticks(p, "weakness_seconds", 6);
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                t -> t != player && t.isAlive() && !t.isAlliedTo(player) && t.distanceToSqr(player) <= radius * radius
                        && (t instanceof net.minecraft.world.entity.monster.Enemy || t instanceof net.minecraft.world.entity.player.Player
                            || (t instanceof Mob m && m.getTarget() == player)))) {
            if (e instanceof ServerPlayer other && !player.canHarmPlayer(other)) continue;
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, weaknessTicks, 0), player);
        }
        sound(player, SoundEvents.WOLF_GROWL);
        return true;
    }

    /** Embestida: cada enemigo alcanzado durante la carga recibe daño, empuje y aturdimiento (una vez). */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Charging charging = CHARGING.get(player.getUUID());
        if (charging == null) return;
        JsonObject p = AbilityService.params(CHARGE);
        float damage = (float) AbilityService.number(p, "hit_damage", 2);
        double knockback = AbilityService.number(p, "knockback_strength", 1.0);
        ActiveMechanic unstoppable = MechanicService.find(player, BarbarianPerks.UNSTOPPABLE_CHARGE);
        ActiveMechanic crushing = MechanicService.find(player, BarbarianPerks.CRUSHING_CHARGE);
        if (player.server.getTickCount() > charging.untilTick() || !player.isAlive()) {
            CHARGING.remove(player.getUUID());
            // Carga Demoledora: la carga termina con una onda de daño alrededor del Bárbaro.
            if (crushing != null && player.isAlive()) {
                double radius = crushing.number("wave_radius", 3);
                for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                        e -> canHit(player, e) && e.distanceToSqr(player) <= radius * radius)) {
                    if (damage > 0) target.hurt(chargeDamage(player), damage);
                    Vec3 away = target.position().subtract(player.position());
                    if (knockback > 0 && away.horizontalDistanceSqr() > 1.0E-4) target.knockback(knockback * 0.5, -away.x, -away.z);
                }
                sound(player, SoundEvents.GENERIC_EXPLODE);
            }
            return;
        }
        Vec3 dir = charging.direction();
        // Carga Imparable: atraviesa (mantiene el impulso), 60 % del daño y sin aturdir.
        if (unstoppable != null) {
            Vec3 v = player.getDeltaMovement();
            double horizontal = Math.sqrt(v.x * v.x + v.z * v.z);
            if (horizontal < charging.speed()) {
                player.setDeltaMovement(dir.x * charging.speed(), v.y, dir.z * charging.speed());
                player.hurtMarked = true;
            }
        }
        float hitDamage = unstoppable == null ? damage : (float) (damage * unstoppable.number("damage_fraction", 0.6));
        int stunTicks = unstoppable != null ? 0 : (int) Math.round(STUN_TICKS * (crushing == null ? 1 : crushing.number("stun_factor", 2)));
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.8),
                e -> canHit(player, e))) {
            if (!charging.alreadyHit().add(target.getId())) continue;
            if (hitDamage > 0) target.hurt(chargeDamage(player), hitDamage);
            // knockback() empuja en sentido contrario al vector dado: se pasa la dirección de la carga invertida.
            if (knockback > 0 && unstoppable == null) target.knockback(knockback, -dir.x, -dir.z);
            if (stunTicks > 0) stun(target, stunTicks);
        }
    }

    private static boolean canHit(ServerPlayer player, LivingEntity e) {
        if (e == player || !e.isAlive() || e.isAlliedTo(player)) return false;
        return !(e instanceof ServerPlayer other) || player.canHarmPlayer(other);
    }

    private static DamageSource chargeDamage(ServerPlayer player) {
        return new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(CHARGE_DAMAGE), player);
    }

    /** Sed de Sangre: curación al matar. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        if (event.getEntity() == player || !AbilityService.has(player, BLOODTHIRST)) return;
        if (AbilityService.remainingTicks(player, BLOODTHIRST) > 0) return;
        JsonObject p = AbilityService.params(BLOODTHIRST);
        player.heal((float) (player.getMaxHealth() * AbilityService.number(p, "heal_fraction", 0.1)));
        AbilityService.setCooldown(player, BLOODTHIRST, AbilityService.ticks(p, "icd_seconds", 1));
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CHARGING.remove(event.getEntity().getUUID());
    }

    // --- Utilidades ---

    /**
     * Aturdimiento sin Epic Fight: casi inmóvil y sin fuerza durante unos instantes.
     * El puente de Epic Fight lo sustituirá por su aturdimiento real.
     */
    public static void stun(LivingEntity target, int ticks) {
        if (target.getType().is(STUN_IMMUNE)) return;
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 2));
        if (target instanceof Mob mob) mob.getNavigation().stop();
    }

    /** 0 sin subclase, 1 con la subclase al nivel 10. */
    private static double subclassProgress(ServerPlayer player) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        return data == null ? 0 : Math.min(1.0, data.state.subclassLevel / 10.0);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static void sound(ServerPlayer player, net.minecraft.sounds.SoundEvent sound) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
    }
}
