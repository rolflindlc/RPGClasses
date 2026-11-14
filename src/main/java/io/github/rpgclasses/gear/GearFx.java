package io.github.rpgclasses.gear;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.content.CombatAbilities;
import io.github.rpgclasses.content.MonkCharges;
import io.github.rpgclasses.content.race.Races;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Daño, elementos y partículas compartidos por los efectos del equipo de clase. Todo su daño es no_proc. */
public final class GearFx {
    public static final ResourceKey<DamageType> MARTIAL = key("martial");
    public static final ResourceKey<DamageType> FLAME = key("dragon_flame");
    public static final ResourceKey<DamageType> FROST = key("dragon_frost");
    public static final ResourceKey<DamageType> SPARK = key("dragon_spark");

    public static final DustParticleOptions EMBER = dust(1.0f, 0.45f, 0.1f, 1.1f);
    public static final DustParticleOptions FROST_DUST = dust(0.6f, 0.9f, 1.0f, 1.1f);
    public static final DustParticleOptions SPARK_DUST = dust(0.75f, 0.55f, 1.0f, 1.1f);
    public static final DustParticleOptions STONE_DUST = dust(0.7f, 0.55f, 0.35f, 1.2f);
    public static final DustParticleOptions WATER_DUST = dust(0.25f, 0.55f, 1.0f, 1.1f);
    public static final DustParticleOptions OBSIDIAN = dust(0.35f, 0.1f, 0.55f, 1.0f);
    public static final DustParticleOptions BONE = dust(0.93f, 0.9f, 0.8f, 1.0f);

    private static ResourceKey<DamageType> key(String id) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, RpgClasses.id(id));
    }

    private static DustParticleOptions dust(float r, float g, float b, float size) {
        return new DustParticleOptions(new Vector3f(r, g, b), size);
    }

    public static DamageSource source(Entity attacker, ResourceKey<DamageType> type) {
        return new DamageSource(attacker.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), attacker);
    }

    /** Tipo de daño de un elemento (fire, ice, lightning); físico si es null. */
    public static ResourceKey<DamageType> elementType(String element) {
        if (element == null) return MARTIAL;
        return switch (element) {
            case "fire" -> FLAME;
            case "ice" -> FROST;
            default -> SPARK;
        };
    }

    public static ParticleOptions elementParticle(String element) {
        if (element == null) return ParticleTypes.CRIT;
        return switch (element) {
            case "fire" -> ParticleTypes.FLAME;
            case "ice" -> ParticleTypes.SNOWFLAKE;
            default -> ParticleTypes.ELECTRIC_SPARK;
        };
    }

    public static DustParticleOptions elementDust(String element) {
        if (element == null) return BONE;
        return switch (element) {
            case "fire" -> EMBER;
            case "ice" -> FROST_DUST;
            default -> SPARK_DUST;
        };
    }

    /** Efecto de un elemento sobre un objetivo: quemadura (acumulable), lentitud o chispa a un enemigo cercano. */
    public static void applyElement(ServerPlayer attacker, LivingEntity target, String element, double strength) {
        if (element == null) return;
        switch (element) {
            case "fire" -> MonkCharges.addBurn(target, 3 * strength);
            case "ice" -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) Math.round(40 * strength), 1), attacker);
            default -> {
                LivingEntity next = nearestEnemy(attacker, target.position(), 4, List.of(target));
                if (next != null) {
                    next.hurt(source(attacker, SPARK), (float) (2 * strength));
                    line(target, next, ParticleTypes.ELECTRIC_SPARK, 8);
                }
            }
        }
        burst(target, elementParticle(element), 6, 0.05);
    }

    /** Congelación del equipo (misma idea que la del Elementalista): aturdimiento corto, la mitad en jefes. */
    public static void freeze(LivingEntity target, int ticks) {
        CombatAbilities.stun(target, Races.isBoss(target) ? Math.max(1, ticks / 3) : ticks);
        burst(target, ParticleTypes.SNOWFLAKE, 20, 0.05);
    }

    public static List<LivingEntity> enemiesAround(ServerPlayer player, Vec3 center, double radius) {
        return player.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(center, center).inflate(radius),
                e -> e != player && e.isAlive() && CombatAbilities.canHit(player, e) && e.position().distanceToSqr(center) <= radius * radius);
    }

    public static LivingEntity nearestEnemy(ServerPlayer player, Vec3 center, double radius, List<? extends Entity> exclude) {
        return enemiesAround(player, center, radius).stream().filter(e -> !exclude.contains(e))
                .min(Comparator.comparingDouble(e -> e.position().distanceToSqr(center))).orElse(null);
    }

    /** Enemigos en un cono delante del jugador (ángulo total en grados). */
    public static List<LivingEntity> cone(ServerPlayer player, double range, double degrees, Entity exclude) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double cos = Math.cos(Math.toRadians(degrees / 2));
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : enemiesAround(player, player.position(), range + 1)) {
            if (e == exclude) continue;
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double d = to.length();
            if (d > range + e.getBbWidth() / 2 || d < 1.0E-3) continue;
            if (to.normalize().dot(look) >= cos) out.add(e);
        }
        return out;
    }

    /** Enemigos en una línea delante del jugador (ancho ±0,9 bloques). */
    public static List<LivingEntity> lineTargets(ServerPlayer player, double length) {
        Vec3 start = player.getEyePosition();
        Vec3 dir = player.getLookAngle();
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : enemiesAround(player, player.position(), length + 1)) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(start);
            double along = to.dot(dir);
            if (along < 0 || along > length) continue;
            if (to.subtract(dir.scale(along)).length() <= 0.9 + e.getBbWidth() / 2) out.add(e);
        }
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        return out;
    }

    // ------------------------------------------------------------------ partículas

    public static void burst(Entity at, ParticleOptions particle, int count, double speed) {
        if (!(at.level() instanceof ServerLevel level)) return;
        level.sendParticles(particle, at.getX(), at.getY() + at.getBbHeight() * 0.6, at.getZ(), count,
                at.getBbWidth() * 0.5, at.getBbHeight() * 0.35, at.getBbWidth() * 0.5, speed);
    }

    public static void ring(ServerLevel level, Vec3 c, ParticleOptions particle, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count;
            level.sendParticles(particle, c.x + Math.cos(a) * radius, c.y + 0.2, c.z + Math.sin(a) * radius, 1, 0, 0.02, 0, 0);
        }
    }

    public static void line(Entity from, Entity to, ParticleOptions particle, int steps) {
        if (!(from.level() instanceof ServerLevel level)) return;
        Vec3 a = from.getBoundingBox().getCenter(), b = to.getBoundingBox().getCenter();
        for (int i = 0; i <= steps; i++) {
            Vec3 p = a.lerp(b, i / (double) steps);
            level.sendParticles(particle, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    /** Rastro en línea recta desde los ojos del jugador. */
    public static void ray(ServerPlayer player, ParticleOptions particle, double length, int steps) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 start = player.getEyePosition().add(0, -0.3, 0);
        Vec3 dir = player.getLookAngle();
        for (int i = 1; i <= steps; i++) {
            Vec3 p = start.add(dir.scale(length * i / steps));
            level.sendParticles(particle, p.x, p.y, p.z, 2, 0.15, 0.15, 0.15, 0.01);
        }
    }

    /** Cono de partículas delante del jugador. */
    public static void coneFx(ServerPlayer player, ParticleOptions particle, double range, double degrees) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition().add(0, -0.3, 0);
        double yaw = Math.toRadians(player.getYRot() + 90);
        for (int i = 0; i < 30; i++) {
            double a = yaw + Math.toRadians((player.getRandom().nextDouble() - 0.5) * degrees);
            double d = 0.8 + player.getRandom().nextDouble() * range;
            level.sendParticles(particle, eye.x + Math.cos(a) * d, eye.y + (player.getRandom().nextDouble() - 0.5) * 0.8,
                    eye.z + Math.sin(a) * d, 1, 0, 0, 0, 0.01);
        }
    }

    /** Alas espectrales a la espalda (Ira del Wyrm): dos arcos de partículas que «baten» con el tiempo. */
    public static void wings(ServerPlayer player, ParticleOptions particle, long tick) {
        ServerLevel level = (ServerLevel) player.level();
        double yaw = Math.toRadians(player.yBodyRot);
        Vec3 back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw)).scale(0.35); // detrás del cuerpo
        Vec3 side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        double flap = Math.sin(tick * 0.25) * 0.35;
        Vec3 base = player.position().add(0, 1.35, 0).add(back);
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 1; i <= 6; i++) {
                double t = i / 6.0;
                Vec3 p = base.add(side.scale(s * t * 1.6)).add(back.scale(t * 0.8)).add(0, Math.sin(t * Math.PI) * 0.5 + flap * t, 0);
                level.sendParticles(particle, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
    }

    private GearFx() {}
}
