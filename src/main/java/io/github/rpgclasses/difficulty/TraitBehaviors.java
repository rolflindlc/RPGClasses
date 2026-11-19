package io.github.rpgclasses.difficulty;

import io.github.rpgclasses.config.ServerConfig;
import io.github.rpgclasses.difficulty.EliteTraits.Trait;
import io.github.rpgclasses.registry.ModEffects;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Lo que hace cada rasgo de élite (ver {@link EliteTraits.Trait}): al ponerlo, al golpear, al morir, cada pocos ticks
 * y ante proyectiles. {@link DifficultyEvents} solo reenvía los eventos de Forge aquí.
 */
public final class TraitBehaviors {
    private static final UUID SWIFT_ID = UUID.fromString("5b1c3a52-7f0e-4d11-9a6e-0c4f2d7e9a11");
    private static final UUID COLOSSUS_HEALTH_ID = UUID.fromString("5b1c3a52-7f0e-4d11-9a6e-0c4f2d7e9a12");
    private static final UUID COLOSSUS_SPEED_ID = UUID.fromString("5b1c3a52-7f0e-4d11-9a6e-0c4f2d7e9a13");

    /** Cada cuántos ticks se sueltan las partículas de los rasgos. */
    public static final int PARTICLE_INTERVAL = 10;

    private TraitBehaviors() {}

    // --- Al recibir el rasgo --------------------------------------------------------------------------------------

    static void onApply(Mob mob, Trait trait) {
        switch (trait) {
            case REGENERATING ->
                // Efecto infinito: se guarda con el mob y no hace falta ningún gancho de tick.
                mob.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MobEffectInstance.INFINITE_DURATION, 0, false, true));
            case SWIFT -> modify(mob, Attributes.MOVEMENT_SPEED, SWIFT_ID, "rites_and_paths swift", ServerConfig.TRAIT_SWIFT_SPEED.get());
            case COLOSSUS -> {
                double scale = ServerConfig.TRAIT_COLOSSUS_SCALE.get();
                // La amplificación del efecto lleva el tamaño en décimas: el cliente lo lee de ahí (no tiene la config del servidor).
                mob.addEffect(new MobEffectInstance(ModEffects.GIGANTISM.get(), MobEffectInstance.INFINITE_DURATION,
                        Math.max(0, Math.min(255, (int) Math.round(scale * 10))), false, false, false));
                modify(mob, Attributes.MAX_HEALTH, COLOSSUS_HEALTH_ID, "rites_and_paths colossus health", ServerConfig.TRAIT_COLOSSUS_HEALTH.get());
                modify(mob, Attributes.MOVEMENT_SPEED, COLOSSUS_SPEED_ID, "rites_and_paths colossus speed", -ServerConfig.TRAIT_COLOSSUS_SLOW.get());
                mob.setHealth(mob.getMaxHealth());
                mob.refreshDimensions();
            }
            default -> { }
        }
    }

    /** MULTIPLY_BASE permanente: base x (1 + amount), sumado a otros bonus porcentuales. */
    private static void modify(Mob mob, Attribute attribute, UUID id, String name, double amount) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance == null || amount == 0 || instance.getModifier(id) != null) return;
        instance.addPermanentModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_BASE));
    }

    // --- Tamaño del Coloso ----------------------------------------------------------------------------------------

    /** Tamaño del mob según su efecto de gigantismo (1 si no lo tiene). Sirve en servidor y en cliente. */
    public static float sizeScale(LivingEntity entity) {
        if (entity.getActiveEffectsMap() == null) return 1.0f; // durante la construcción de la entidad
        MobEffectInstance effect = entity.getEffect(ModEffects.GIGANTISM.get());
        return effect == null || effect.getAmplifier() <= 0 ? 1.0f : effect.getAmplifier() / 10.0f;
    }

    /** Recalcula la caja de colisión si no coincide con el tamaño del efecto (mob cargado del disco o efecto recién llegado al cliente). */
    public static void syncSize(LivingEntity entity) {
        float scale = sizeScale(entity);
        if (scale == 1.0f) return;
        if (Math.abs(entity.getBbWidth() - entity.getDimensions(entity.getPose()).width * scale) > 0.01f) entity.refreshDimensions();
    }

    // --- Al golpear -----------------------------------------------------------------------------------------------

    static void onHit(Mob attacker, LivingEntity target, float amount) {
        if (EliteTraits.has(attacker, Trait.POISONOUS)) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, ServerConfig.TRAIT_POISON_SECONDS.get() * 20, 0), attacker);
        }
        if (EliteTraits.has(attacker, Trait.IGNEOUS)) target.setSecondsOnFire(ServerConfig.TRAIT_IGNEOUS_SECONDS.get());
        if (EliteTraits.has(attacker, Trait.CRIPPLING)) {
            int ticks = ServerConfig.TRAIT_CRIPPLING_SECONDS.get() * 20;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1), attacker);
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0), attacker);
        }
        if (EliteTraits.has(attacker, Trait.VAMPIRIC)) attacker.heal((float) (amount * ServerConfig.TRAIT_VAMPIRIC_HEAL.get()));
    }

    // --- Al morir -------------------------------------------------------------------------------------------------

    static void onDeath(LivingEntity dead, ServerLevel level) {
        if (EliteTraits.has(dead, Trait.VOLATILE)) {
            level.explode(dead, dead.getX(), dead.getY() + dead.getBbHeight() / 2, dead.getZ(),
                    ServerConfig.TRAIT_VOLATILE_POWER.get().floatValue(), net.minecraft.world.level.Level.ExplosionInteraction.NONE);
        }
        if (EliteTraits.has(dead, Trait.COLOSSUS)) colossusBurst(dead, level);
    }

    /** Explosión solo visual y los mobs iguales (sin rasgos) que suelta el Coloso. */
    private static void colossusBurst(LivingEntity dead, ServerLevel level) {
        double x = dead.getX(), y = dead.getY() + dead.getBbHeight() / 2, z = dead.getZ();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.POOF, x, y, z, 30, dead.getBbWidth() / 2, dead.getBbHeight() / 3, dead.getBbWidth() / 2, 0.15);
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2f, 0.8f);
        double threat = dead.getPersistentData().getFloat(DifficultyEvents.KEY_THREAT);
        ThreatService.Result zone = new ThreatService.Result(threat, 1, threat);
        RandomSource random = dead.getRandom();
        for (int i = 0; i < ServerConfig.TRAIT_COLOSSUS_SPAWNS.get(); i++) {
            Entity entity = dead.getType().create(level);
            if (!(entity instanceof Mob child)) continue;
            child.moveTo(x + (random.nextDouble() - 0.5) * 2.0, dead.getY(), z + (random.nextDouble() - 0.5) * 2.0, random.nextFloat() * 360.0f, 0.0f);
            child.finalizeSpawn(level, level.getCurrentDifficultyAt(child.blockPosition()), MobSpawnType.REINFORCEMENT, null, null);
            DifficultyEvents.markAndApply(child, zone, false);
            level.addFreshEntityWithPassengers(child);
        }
    }

    // --- Cada pocos ticks -----------------------------------------------------------------------------------------

    /** Partículas propias de cada rasgo (cada {@link #PARTICLE_INTERVAL} ticks) y teletransporte. */
    static void tick(Mob mob, ServerLevel level) {
        if (ServerConfig.DIFFICULTY_TRAIT_PARTICLES.get()) {
            for (Trait trait : Trait.values()) {
                if (EliteTraits.has(mob, trait)) emit(level, mob, trait);
            }
        }
        if (mob.tickCount % 20 == 0) {
            if (EliteTraits.has(mob, Trait.TELEPORTING)) tryTeleport(mob, level);
            if (EliteTraits.has(mob, Trait.COLOSSUS)) syncSize(mob);
        }
    }

    private static void emit(ServerLevel level, Mob mob, Trait trait) {
        double x = mob.getX(), y = mob.getY() + mob.getBbHeight() / 2, z = mob.getZ();
        double dx = mob.getBbWidth() / 2, dy = mob.getBbHeight() / 3;
        switch (trait) {
            case SWIFT -> level.sendParticles(ParticleTypes.CLOUD, x, mob.getY() + 0.1, z, 2, dx, 0.05, dx, 0.02);
            case POISONOUS -> level.sendParticles(ParticleTypes.ITEM_SLIME, x, y, z, 3, dx, dy, dx, 0.0);
            case IGNEOUS -> level.sendParticles(ParticleTypes.FLAME, x, y, z, 3, dx, dy, dx, 0.01);
            case REGENERATING -> level.sendParticles(ParticleTypes.HEART, x, mob.getY() + mob.getBbHeight() + 0.2, z, 1, dx, 0.1, dx, 0.0);
            case CRIPPLING -> level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 3, dx, dy, dx, 0.02);
            case VOLATILE -> level.sendParticles(ParticleTypes.SMOKE, x, y, z, 3, dx, dy, dx, 0.01);
            case VAMPIRIC -> level.sendParticles(new DustParticleOptions(new Vector3f(0.65f, 0.02f, 0.1f), 1.2f), x, y, z, 4, dx, dy, dx, 0.0);
            case TELEPORTING -> level.sendParticles(ParticleTypes.PORTAL, x, y, z, 5, dx, dy, dx, 0.5);
            case DEFLECTING -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 3, dx, dy, dx, 0.2);
            case COLOSSUS -> level.sendParticles(ParticleTypes.ASH, x, y, z, 4, dx, dy, dx, 0.0);
        }
    }

    /** Si su objetivo está lejos (entre minDistance y maxDistance), aparece a su lado con cierta probabilidad por segundo. */
    private static void tryTeleport(Mob mob, ServerLevel level) {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return;
        double distance = mob.distanceTo(target);
        if (distance < ServerConfig.TRAIT_TELEPORT_MIN_DISTANCE.get() || distance > ServerConfig.TRAIT_TELEPORT_MAX_DISTANCE.get()) return;
        RandomSource random = mob.getRandom();
        if (random.nextDouble() >= ServerConfig.TRAIT_TELEPORT_CHANCE.get()) return;
        double angle = random.nextDouble() * Math.PI * 2;
        double radius = 2.5 + random.nextDouble() * 1.5;
        double x = target.getX() + Math.cos(angle) * radius, z = target.getZ() + Math.sin(angle) * radius;
        if (ExemptionRules.isInSafeZone(level, new Vec3(x, target.getY(), z))) return;
        Vec3 from = mob.position().add(0, mob.getBbHeight() / 2, 0);
        if (!mob.randomTeleport(x, target.getY(), z, true)) return;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y, from.z, 30, 0.3, 0.5, 0.3, 0.2);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 30, 0.3, 0.5, 0.3, 0.2);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8f, 1.0f);
    }

    // --- Proyectiles ----------------------------------------------------------------------------------------------

    /** Rebota el proyectil (invierte su velocidad con algo de desvío) y devuelve true si lo ha desviado. */
    static boolean deflect(Projectile projectile, Mob mob) {
        if (!(projectile.level() instanceof ServerLevel level) || projectile.getOwner() == mob) return false;
        RandomSource random = mob.getRandom();
        double speed = ServerConfig.TRAIT_DEFLECT_SPEED.get();
        Vec3 v = projectile.getDeltaMovement();
        projectile.setDeltaMovement(-v.x * speed + (random.nextDouble() - 0.5) * 0.2,
                Math.abs(v.y) * speed + 0.1, -v.z * speed + (random.nextDouble() - 0.5) * 0.2);
        projectile.setYRot(projectile.getYRot() + 180.0f);
        projectile.hurtMarked = true;
        Vec3 at = projectile.position();
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.3);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 0.8f, 1.2f);
        return true;
    }
}
