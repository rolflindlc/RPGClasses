package io.github.rpgclasses.content;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.registry.ModEffects;
import io.github.rpgclasses.server.MechanicService;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Rama Toxinas del Pícaro (rev. 2.7c): probabilidad de aplicar Toxina al golpear cuerpo a cuerpo.
 * Daño periódico sin armadura, no se apila (si ya la tiene no se renueva). Los perks Dosis Concentrada /
 * Prolongada cambian daño, intervalo y duración (mismo total) y la Prolongada añade Lentitud.
 * La dosis se guarda en el propio objetivo (persistentData), así sobrevive a guardar el mundo.
 */
public final class Toxins {
    public static final String TOXIN = "toxin";
    public static final String VARIANT = "toxin_variant";
    public static final ResourceKey<DamageType> TOXIN_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("toxin"));
    private static final String TAG = "rpgclasses.toxin";

    /** Llamado por DamagePipeline tras un golpe cuerpo a cuerpo del jugador. */
    public static void onMeleeHit(ServerPlayer attacker, LivingEntity target) {
        ActiveMechanic toxin = MechanicService.find(attacker, TOXIN);
        if (toxin == null || !target.isAlive() || target.hasEffect(ModEffects.TOXIN.get())) return;
        if (attacker.getRandom().nextDouble() >= toxin.number("chance", 0.3)) return;
        ActiveMechanic variant = MechanicService.find(attacker, VARIANT);
        double damage = pick(variant, toxin, "toxin_damage", 1);
        double interval = Math.max(0.05, pick(variant, toxin, "interval_seconds", 3));
        double duration = pick(variant, toxin, "duration_seconds", 6);
        int slowness = (int) Math.round(variant == null ? 0 : variant.number("slowness_level", 0));

        int durationTicks = (int) Math.round(duration * 20);
        int intervalTicks = (int) Math.round(interval * 20);
        if (!target.addEffect(new MobEffectInstance(ModEffects.TOXIN.get(), durationTicks + 1, 0), attacker)) return;
        if (slowness > 0) target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, durationTicks, slowness - 1), attacker);
        CompoundTag dose = new CompoundTag();
        dose.putFloat("damage", (float) damage);
        dose.putInt("interval", intervalTicks);
        dose.putLong("next", target.level().getGameTime() + intervalTicks);
        dose.putUUID("owner", attacker.getUUID());
        target.getPersistentData().put(TAG, dose);
    }

    private static double pick(ActiveMechanic variant, ActiveMechanic base, String key, double fallback) {
        return variant != null && variant.params().has(key) ? variant.number(key, fallback) : base.number(key, fallback);
    }

    @SubscribeEvent
    public void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        CompoundTag data = entity.getPersistentData();
        if (!entity.hasEffect(ModEffects.TOXIN.get())) {
            if (data.contains(TAG)) data.remove(TAG); // curada con leche o caducada
            return;
        }
        if (!data.contains(TAG)) return;
        CompoundTag dose = data.getCompound(TAG);
        long now = entity.level().getGameTime();
        if (now < dose.getLong("next")) return;
        dose.putLong("next", now + Math.max(1, dose.getInt("interval")));
        // Sin entidad causante: no hay empuje en cada tic. El crédito de la muerte se da a mano.
        if (dose.hasUUID("owner") && entity.level() instanceof ServerLevel level
                && level.getServer().getPlayerList().getPlayer(dose.getUUID("owner")) instanceof ServerPlayer owner) {
            entity.setLastHurtByPlayer(owner);
        }
        // Ni la bloquea un golpe reciente ni sus tics dan invulnerabilidad frente a los golpes del Pícaro.
        int invulnerable = entity.invulnerableTime;
        entity.invulnerableTime = 0;
        entity.hurt(new DamageSource(entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(TOXIN_DAMAGE)), dose.getFloat("damage"));
        entity.invulnerableTime = invulnerable;
    }
}
