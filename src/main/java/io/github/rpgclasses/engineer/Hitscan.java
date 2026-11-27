package io.github.rpgclasses.engineer;

import io.github.rpgclasses.RpgClasses;
import java.util.function.Predicate;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Disparos instantáneos propios del Ingeniero (torretas, Abanico y Ojo Muerto): un rayo con estela de partículas y el
 * tipo de daño {@code rites_and_paths:engineer_bullet}, que el pipeline trata como arma de fuego (muro de Pólvora).
 * No crean proyectiles de NTGL: así no dependen de sus clases internas.
 */
final class Hitscan {
    static final ResourceKey<DamageType> BULLET = ResourceKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("engineer_bullet"));
    static final ResourceKey<DamageType> BLAST = ResourceKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("engineer_blast"));
    /** Multiplicador de un disparo a la cabeza (el mismo que la config de NTGL acordada: ×1,5). */
    static final double HEADSHOT = 1.5;
    /** Disparo a la cabeza del impacto que se está resolviendo (lo lee EngineerCombat). */
    static final ThreadLocal<Boolean> HEAD = ThreadLocal.withInitial(() -> false);
    /** Bala especial del impacto que se está resolviendo, o -1. */
    static final ThreadLocal<Integer> SPECIAL = ThreadLocal.withInitial(() -> -1);

    private Hitscan() {
    }

    static DamageSource source(ServerLevel level, ResourceKey<DamageType> type, Entity direct, Entity causing) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), direct, causing);
    }

    /** Dirección con una dispersión aleatoria de hasta {@code degrees} grados. */
    static Vec3 spread(Vec3 dir, double degrees, RandomSource random) {
        if (degrees <= 0) return dir.normalize();
        double r = Math.toRadians(degrees);
        return dir.normalize().add(random.triangle(0, r), random.triangle(0, r), random.triangle(0, r)).normalize();
    }

    /**
     * Dispara un rayo de {@code from} en {@code dir}. {@code fades}: el daño baja con la distancia hasta 0 al final del
     * alcance (perdigones). {@code forceHead}: cuenta como disparo a la cabeza (Ojo Muerto). Devuelve lo que golpeó.
     */
    static LivingEntity shoot(ServerLevel level, Entity direct, Entity causing, Vec3 from, Vec3 dir, double range, float damage,
                              boolean fades, boolean forceHead, int special, Predicate<Entity> canHit) {
        Vec3 to = from.add(dir.normalize().scale(range));
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, direct));
        if (block.getType() != HitResult.Type.MISS) to = block.getLocation();
        AABB box = new AABB(from, to).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, direct, from, to, box,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e.isPickable() && canHit.test(e));
        Vec3 end = hit != null ? hit.getLocation() : to;
        trail(level, from, end);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.8f, 1.6f);
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) return null;
        double distance = from.distanceTo(end);
        float dealt = damage;
        if (fades) dealt *= (float) Math.max(0, 1 - distance / range);
        boolean head = forceHead || end.y >= target.getEyeY() - target.getBbHeight() * 0.12;
        if (head) dealt *= HEADSHOT;
        if (dealt <= 0) return null;
        HEAD.set(head);
        SPECIAL.set(special);
        try {
            target.invulnerableTime = 0;
            target.hurt(source(level, BULLET, direct, causing), dealt);
        } finally {
            HEAD.set(false);
            SPECIAL.set(-1);
        }
        return target;
    }

    static void trail(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        int steps = (int) Math.min(48, Math.max(2, d.length() * 2));
        for (int i = 1; i <= steps; i++) {
            Vec3 at = from.add(d.scale(i / (double) steps));
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.SMOKE, from.x, from.y, from.z, 3, 0.05, 0.05, 0.05, 0.01);
    }
}
