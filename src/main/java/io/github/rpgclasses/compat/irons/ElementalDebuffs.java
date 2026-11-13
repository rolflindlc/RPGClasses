package io.github.rpgclasses.compat.irons;

import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.server.MechanicService;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
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

import io.github.rpgclasses.content.Schools;
import io.github.rpgclasses.content.Schools.School;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Elementalista: Debuffs Elementales (mecánica {@code elemental_debuffs}).
 *
 * <p>Cada golpe de conjuro de cualquier escuela salvo la sagrada (exclusiva del Paladín) tiene una probabilidad (40–75 %) de poner una carga
 * de ese elemento en el objetivo. Las cargas duran {@code duration_seconds} desde la primera (desde
 * {@code refresh_from_level} cada carga nueva reinicia el tiempo). Al llegar al máximo, detonan y se vacían:
 * <ul>
 *   <li>Fuego: explosión de área (daño = parte del golpe que detona) y prende fuego.</li>
 *   <li>Hielo: congelación (lentitud extrema, sin saltar); más corta contra jefes.</li>
 *   <li>Rayo: arcos a enemigos cercanos.</li>
 *   <li>Sangre: daño extra que cura al lanzador.</li>
 *   <li>Ender: daño y teletransporte aleatorio del objetivo (no a jefes).</li>
 *   <li>Invocación: anillo de colmillos de evocador.</li>
 *   <li>Naturaleza: raíces (lentitud extrema) y veneno fuerte.</li>
 *   <li>Eldritch: Marca del Vacío; guarda parte del daño que recibe el objetivo y al terminar implosiona.</li>
 *   <li>Agua (solo con T.O Magic 'n Extras): ola que empuja, ralentiza y aplica Mojado.</li>
 * </ul>
 * El daño secundario es mágico de vanilla, así que no vuelve a poner cargas.
 */
final class ElementalDebuffs {
    /** Rev. 2.15: las 10 escuelas (ver {@link Schools}); Agua solo aparece con T.O Magic 'n Extras. */

    private static final class Stacks {
        int count;
        long expires;
    }

    /** Objetivo → lanzador → cargas por elemento (cada Elementalista lleva sus propias cargas). */
    private static final Map<UUID, Map<UUID, EnumMap<School, Stacks>>> STACKS = new HashMap<>();

    private ElementalDebuffs() {}

    static School element(DamageSource source) {
        return source.typeHolder().unwrapKey().map(k -> Schools.ofDamageType(k.location())).orElse(null);
    }

    /** Llamado con el daño final de un golpe de conjuro del jugador. */
    static void onSpellHit(ServerPlayer player, LivingEntity target, DamageSource source, float amount, long tick) {
        if (amount <= 0 || target == player || !target.isAlive()) return;
        ActiveMechanic mech = MechanicService.find(player, "elemental_debuffs");
        if (mech == null) return;
        School element = element(source);
        // La sagrada es exclusiva del Paladín (regla 14): no carga.
        if (element == null || element == School.HOLY || player.getRandom().nextDouble() >= mech.number("chance", 0.4)) return;

        int max = Math.max(1, (int) Math.round(mech.number("max_stacks", 3)));
        long duration = Math.round(mech.number("duration_seconds", 4) * 20);
        // Rev. 2.7f: cada carga nueva reinicia el tiempo (antes solo desde el nivel 8; en las pruebas no daba tiempo a detonar).
        boolean refresh = mech.level() >= mech.number("refresh_from_level", 1);
        Stacks s = STACKS.computeIfAbsent(target.getUUID(), id -> new HashMap<>())
                .computeIfAbsent(player.getUUID(), id -> new EnumMap<>(School.class))
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
            player.displayClientMessage(Component.translatable("message.rpgclasses.element_detonate",
                    Component.translatable("element.rpgclasses." + element.key())), true);
        } else {
            player.displayClientMessage(Component.translatable("message.rpgclasses.element_stack",
                    Component.translatable("element.rpgclasses." + element.key()), s.count, max), true);
        }
    }

    private static void detonate(ServerPlayer player, LivingEntity target, School element, float hit, ActiveMechanic mech) {
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
                // Rev. 2.7f: el objetivo queda encerrado en una Tumba de Hielo de Iron's (los jefes, solo congelados).
                if (!boss && iceTomb(player, target, (int) Math.round(mech.number("freeze_seconds", 3) * 20))) {
                    level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 0.6f);
                    return;
                }
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
            case ENDER -> {
                // Desplazamiento: daño y el objetivo salta a un sitio cercano al azar (los jefes no se mueven).
                target.invulnerableTime = 0;
                target.hurt(magic, (float) (hit * mech.number("ender_fraction", 0.4)));
                level.sendParticles(ParticleTypes.PORTAL, target.getX(), target.getY() + 1, target.getZ(), 40, 0.4, 0.8, 0.4, 0.3);
                if (!boss) Schools.blink(target, mech.number("ender_range", 8));
            }
            case EVOCATION -> {
                // Colmillos: anillo de colmillos de evocador alrededor del objetivo.
                Schools.fangs(player, target, (int) Math.round(mech.number("fangs", 6)), 1.5);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 1f);
            }
            case NATURE -> {
                // Enredo: veneno fuerte y raíces (lentitud extrema); más corto contra jefes.
                int ticks = (int) Math.round(mech.number(boss ? "boss_root_seconds" : "root_seconds", boss ? 1.5 : 3) * 20);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 4, false, true), player);
                target.addEffect(new MobEffectInstance(MobEffects.POISON, (int) Math.round(mech.number("poison_seconds", 5) * 20),
                        (int) Math.round(mech.number("poison_level", 2)) - 1, false, true), player);
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, target.getX(), target.getY() + 0.5, target.getZ(), 30, 0.5, 0.5, 0.5, 0.02);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + 0.2, target.getZ(), 15, 0.6, 0.1, 0.6, 0);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.PLAYERS, 1f, 0.7f);
            }
            case ELDRITCH -> {
                // Marca del Vacío (rev. 2.16): guarda daño y al terminar implosiona (ver VoidMark).
                io.github.rpgclasses.content.VoidMark.mark(player, target, mech.number("void_seconds", 4),
                        mech.number("void_store", 0.3), mech.number("void_radius", 4), mech.number("void_pull", 0.9),
                        mech.number("void_boss_factor", 0.5), mech.number("void_area_fraction", 0.5),
                        hit * mech.number("void_initial", 0.25));
            }
            case AQUA -> {
                // Marea (T.O Magic 'n Extras): ola que empuja a los enemigos cercanos, los ralentiza y los moja.
                double radius = mech.number("wave_radius", 3);
                float damage = (float) (hit * mech.number("wave_fraction", 0.4));
                int ticks = (int) Math.round(mech.number("wet_seconds", 6) * 20);
                for (LivingEntity e : enemiesAround(player, target.position(), radius)) {
                    e.hurt(magic, damage);
                    Vec3 dir = e.position().subtract(player.position()).multiply(1, 0, 1).normalize();
                    e.push(dir.x * 1.0, 0.3, dir.z * 1.0);
                    e.hurtMarked = true;
                    e.clearFire();
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks / 2, 1, false, true), player);
                    Schools.applyWet(e, player, ticks, 1);
                }
                level.sendParticles(ParticleTypes.SPLASH, target.getX(), target.getY() + 0.5, target.getZ(), 60, radius / 2, 0.3, radius / 2, 0.2);
                level.sendParticles(ParticleTypes.BUBBLE_POP, target.getX(), target.getY() + 0.5, target.getZ(), 30, radius / 2, 0.5, radius / 2, 0.05);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1f, 0.8f);
            }
        }
    }

    /** Tumba de Hielo "maligna" de Iron's (la de los enemigos): congela al pasajero y al romperse le hace el doble de daño. */
    private static boolean iceTomb(ServerPlayer player, LivingEntity target, int ticks) {
        if (target.isPassenger() || !target.canChangeDimensions()) return false;
        try {
            Class<?> cls = Class.forName("io.redspace.ironsspellbooks.entity.spells.ice_tomb.IceTombEntity", true, ElementalDebuffs.class.getClassLoader());
            Object tomb = cls.getConstructor(net.minecraft.world.level.Level.class, net.minecraft.world.entity.Entity.class)
                    .newInstance(target.level(), player);
            cls.getMethod("setEvil").invoke(tomb);
            cls.getMethod("setLifetime", int.class).invoke(tomb, ticks);
            net.minecraft.world.entity.Entity e = (net.minecraft.world.entity.Entity) tomb;
            e.moveTo(target.position());
            if (!target.level().addFreshEntity(e)) return false;
            if (!target.startRiding(e, true)) {
                e.discard();
                return false;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return false;
        }
    }

    private static List<LivingEntity> enemiesAround(ServerPlayer player, Vec3 at, double radius) {
        return player.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(radius),
                e -> io.github.rpgclasses.server.Allies.isEnemy(player, e) && e.distanceToSqr(at) <= radius * radius);
    }

    private static void line(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 step = to.subtract(from);
        int points = Math.max(2, (int) (step.length() * 4));
        for (int i = 0; i <= points; i++) {
            Vec3 p = from.add(step.scale(i / (double) points));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    private static ParticleOptions particle(School element) {
        return switch (element) {
            case FIRE -> ParticleTypes.SMALL_FLAME;
            case ICE -> ParticleTypes.SNOWFLAKE;
            case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
            case BLOOD -> ParticleTypes.DAMAGE_INDICATOR;
            default -> Schools.particle(element);
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
