package io.github.rpgclasses.compat.irons;

import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.server.MechanicService;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Elementalista: Debuffs Elementales (mecánica {@code elemental_debuffs}).
 *
 * <p>Cada golpe de conjuro de Fuego, Hielo, Rayo o Sangre tiene una probabilidad (40–75 %) de poner una carga
 * de ese elemento en el objetivo. Las cargas duran {@code duration_seconds} desde la primera (desde
 * {@code refresh_from_level} cada carga nueva reinicia el tiempo). Al llegar al máximo, detonan y se vacían:
 * <ul>
 *   <li>Fuego: explosión de área (daño = parte del golpe que detona) y prende fuego.</li>
 *   <li>Hielo: congelación (lentitud extrema, sin saltar); más corta contra jefes.</li>
 *   <li>Rayo: arcos a enemigos cercanos.</li>
 *   <li>Sangre: daño extra que cura al lanzador.</li>
 * </ul>
 * El daño secundario es mágico de vanilla, así que no vuelve a poner cargas.
 */
final class ElementalDebuffs {
    enum Element { FIRE, ICE, LIGHTNING, BLOOD }

    private static final class Stacks {
        int count;
        long expires;
    }

    /** Objetivo → lanzador → cargas por elemento (cada Elementalista lleva sus propias cargas). */
    private static final Map<UUID, Map<UUID, EnumMap<Element, Stacks>>> STACKS = new HashMap<>();

    private ElementalDebuffs() {}

    static Element element(DamageSource source) {
        return source.typeHolder().unwrapKey().map(k -> switch (k.location().toString()) {
            case "irons_spellbooks:fire_magic" -> Element.FIRE;
            case "irons_spellbooks:ice_magic" -> Element.ICE;
            case "irons_spellbooks:lightning_magic" -> Element.LIGHTNING;
            case "irons_spellbooks:blood_magic" -> Element.BLOOD;
            default -> null;
        }).orElse(null);
    }

    /** Llamado con el daño final de un golpe de conjuro del jugador. */
    static void onSpellHit(ServerPlayer player, LivingEntity target, DamageSource source, float amount, long tick) {
        if (amount <= 0 || target == player || !target.isAlive()) return;
        ActiveMechanic mech = MechanicService.find(player, "elemental_debuffs");
        if (mech == null) return;
        Element element = element(source);
        if (element == null || player.getRandom().nextDouble() >= mech.number("chance", 0.4)) return;

        int max = Math.max(1, (int) Math.round(mech.number("max_stacks", 3)));
        long duration = Math.round(mech.number("duration_seconds", 4) * 20);
        boolean refresh = mech.level() >= mech.number("refresh_from_level", 8);
        Stacks s = STACKS.computeIfAbsent(target.getUUID(), id -> new HashMap<>())
                .computeIfAbsent(player.getUUID(), id -> new EnumMap<>(Element.class))
                .computeIfAbsent(element, e -> new Stacks());
        if (s.count > 0 && tick > s.expires) s.count = 0;
        if (s.count == 0 || refresh) s.expires = tick + duration;
        s.count++;
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(particle(element), target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    3 + s.count * 2, 0.3, 0.3, 0.3, 0.02);
        }
        if (s.count >= max) {
            s.count = 0;
            detonate(player, target, element, amount, mech);
        }
    }

    private static void detonate(ServerPlayer player, LivingEntity target, Element element, float hit, ActiveMechanic mech) {
        if (!(target.level() instanceof ServerLevel level)) return;
        DamageSource magic = player.damageSources().indirectMagic(player, player);
        boolean boss = target.getType().is(Tags.EntityTypes.BOSSES);
        switch (element) {
            case FIRE -> {
                double radius = mech.number("fire_radius", 3);
                float damage = (float) (hit * mech.number("fire_fraction", 0.6));
                int seconds = (int) Math.round(mech.number("fire_seconds", 4));
                for (LivingEntity e : enemiesAround(player, target.position(), radius)) {
                    e.hurt(magic, damage);
                    e.setSecondsOnFire(seconds);
                }
                level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 0.5, target.getZ(), 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 0.5, target.getZ(), 40, radius / 2, 0.4, radius / 2, 0.05);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7f, 1.3f);
            }
            case ICE -> {
                int ticks = (int) Math.round(mech.number(boss ? "boss_freeze_seconds" : "freeze_seconds", boss ? 1.5 : 3) * 20);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, true), player);
                target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 2, false, true), player);
                target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + ticks));
                level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + 1, target.getZ(), 40, 0.5, 0.8, 0.5, 0.03);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 0.6f);
            }
            case LIGHTNING -> {
                int targets = (int) Math.round(mech.number("arc_targets", 3));
                float damage = (float) (hit * mech.number("arc_fraction", 0.5));
                List<LivingEntity> near = enemiesAround(player, target.position(), mech.number("arc_range", 6));
                near.remove(target);
                near.sort((a, b) -> Double.compare(a.distanceToSqr(target), b.distanceToSqr(target)));
                for (int i = 0; i < Math.min(targets, near.size()); i++) {
                    LivingEntity e = near.get(i);
                    e.hurt(magic, damage);
                    line(level, target.position().add(0, target.getBbHeight() / 2, 0), e.position().add(0, e.getBbHeight() / 2, 0));
                }
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6f, 1.6f);
            }
            case BLOOD -> {
                float damage = (float) (hit * mech.number("leech_fraction", 0.4));
                float before = target.getHealth();
                target.invulnerableTime = 0;
                target.hurt(magic, damage);
                float dealt = Math.max(0, before - target.getHealth());
                if (dealt > 0) player.heal(dealt);
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY() + 1, target.getZ(), 10, 0.3, 0.4, 0.3, 0.1);
                level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 2, player.getZ(), 3, 0.3, 0.2, 0.3, 0.02);
            }
        }
    }

    private static List<LivingEntity> enemiesAround(ServerPlayer player, Vec3 at, double radius) {
        return player.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(radius),
                e -> e != player && e.isAlive() && !e.isAlliedTo(player) && e.distanceToSqr(at) <= radius * radius
                        && !(e instanceof ServerPlayer other && !player.canHarmPlayer(other)));
    }

    private static void line(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 step = to.subtract(from);
        int points = Math.max(2, (int) (step.length() * 4));
        for (int i = 0; i <= points; i++) {
            Vec3 p = from.add(step.scale(i / (double) points));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    private static ParticleOptions particle(Element element) {
        return switch (element) {
            case FIRE -> ParticleTypes.SMALL_FLAME;
            case ICE -> ParticleTypes.SNOWFLAKE;
            case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
            case BLOOD -> ParticleTypes.DAMAGE_INDICATOR;
        };
    }

    /** Limpieza periódica de cargas caducadas. */
    static void cleanup(long tick) {
        STACKS.values().removeIf(byOwner -> {
            byOwner.values().removeIf(map -> {
                map.values().removeIf(s -> s.count == 0 || tick > s.expires);
                return map.isEmpty();
            });
            return byOwner.isEmpty();
        });
    }
}
