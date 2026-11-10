package io.github.rpgclasses.content;

import com.google.gson.JsonObject;
import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.registry.ModEffects;
import io.github.rpgclasses.server.AbilityService;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
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
import java.util.Map;
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

    /** Jugadores en plena Embestida -> tick de servidor en que termina. */
    private static final Map<UUID, Integer> CHARGING_UNTIL = new HashMap<>();
    private static final int CHARGE_WINDOW_TICKS = 12;
    private static final int STUN_TICKS = 40;

    public static void register() {
        AbilityService.register(TAUNT, CombatAbilities::taunt);
        AbilityService.register(BULWARK, CombatAbilities::bulwark);
        AbilityService.register(CHARGE, CombatAbilities::charge);
        AbilityService.register(BERSERKER_RAGE, CombatAbilities::berserkerRage);
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
        double speed = AbilityService.number(p, "distance", 6) * 0.25;
        player.setDeltaMovement(flat.normalize().scale(speed).add(0, 0.15, 0));
        player.hurtMarked = true; // envía la velocidad al cliente
        CHARGING_UNTIL.put(player.getUUID(), player.server.getTickCount() + CHARGE_WINDOW_TICKS);
        sound(player, SoundEvents.PLAYER_ATTACK_SWEEP);
        return true;
    }

    private static boolean berserkerRage(ServerPlayer player, JsonObject p) {
        player.addEffect(new MobEffectInstance(ModEffects.BERSERKER_RAGE.get(), AbilityService.ticks(p, "duration_seconds", 10), 0));
        sound(player, SoundEvents.WOLF_GROWL);
        return true;
    }

    /** Embestida: el primer enemigo alcanzado durante la carga queda aturdido. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Integer until = CHARGING_UNTIL.get(player.getUUID());
        if (until == null) return;
        if (player.server.getTickCount() > until || !player.isAlive()) {
            CHARGING_UNTIL.remove(player.getUUID());
            return;
        }
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.6),
                e -> e != player && e.isAlive() && !e.isAlliedTo(player))) {
            stun(target, STUN_TICKS);
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            CHARGING_UNTIL.remove(player.getUUID());
            break;
        }
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
        CHARGING_UNTIL.remove(event.getEntity().getUUID());
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
