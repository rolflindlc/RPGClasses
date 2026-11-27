package io.github.rpgclasses.engineer;

import io.github.rpgclasses.compat.ntgl.NtglCompat;
import io.github.rpgclasses.content.CombatAbilities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Munición Improvisada (adenda del Ingeniero, 3.1): al terminar una recarga hay una probabilidad de que la primera bala
 * (las cinco primeras en armas automáticas) sea especial. El tipo de la siguiente especial se decide por adelantado y el
 * cliente lo enseña junto a la mira. Tipos: 0 incendiaria, 1 aturdidora, 2 congelante; Munición Oxidada añade 3 veneno y
 * 4 marchitamiento; Munición Arcana, 5 levitación y 6 atracción.
 */
final class ImprovisedAmmo {
    static final int FIRE = 0, STUN = 1, FROST = 2, POISON = 3, WITHER = 4, LEVITATION = 5, PULL = 6;
    static final String[] NAMES = {"fire", "stun", "frost", "poison", "wither", "levitation", "pull"};
    /** Etiqueta en los datos persistentes de un proyectil de NTGL con su bala especial. */
    static final String TAG = "rites_and_paths_special";

    private ImprovisedAmmo() {
    }

    private static List<Integer> pool(ServerPlayer p) {
        List<Integer> pool = new ArrayList<>(List.of(FIRE, STUN, FROST));
        if (Engineer.has(p, Engineer.RUSTED)) pool.addAll(List.of(POISON, WITHER));
        if (Engineer.has(p, Engineer.ARCANE)) pool.addAll(List.of(LEVITATION, PULL));
        return pool;
    }

    /** Tipo de la siguiente bala especial (se elige si aún no hay o si ya no está en la lista). */
    static int next(ServerPlayer p, Engineer.State s) {
        if (!Engineer.has(p, Engineer.IMPROVISED)) return -1;
        List<Integer> pool = pool(p);
        if (s.nextSpecial < 0 || !pool.contains(s.nextSpecial)) s.nextSpecial = pool.get(p.getRandom().nextInt(pool.size()));
        return s.nextSpecial;
    }

    /** Recarga terminada: tirada de bala especial. */
    static void onReloaded(ServerPlayer p, Engineer.State s) {
        double chance = Engineer.mech(p, Engineer.IMPROVISED, "chance", 0);
        if (chance <= 0) return;
        int type = next(p, s);
        if (type < 0 || p.getRandom().nextDouble() >= chance) return;
        ItemStack gun = p.getMainHandItem();
        boolean automatic = NtglCompat.rate(gun, p) <= 5;
        s.loadedSpecial = type;
        s.specialLeft = automatic ? (int) Engineer.mech(p, Engineer.IMPROVISED, "auto_shots", 5) : 1;
        List<Integer> pool = pool(p);
        s.nextSpecial = pool.get(p.getRandom().nextInt(pool.size()));
    }

    /** Efecto de una bala especial al impactar. */
    static void apply(ServerPlayer shooter, LivingEntity target, int type) {
        if (type < 0 || !target.isAlive()) return;
        boolean boss = Engineer.boss(target);
        int bossSlow = (int) Math.round(Engineer.mech(shooter, Engineer.ARCANE, "boss_slow_seconds", 3) * 20);
        int bossLevel = (int) Engineer.mech(shooter, Engineer.ARCANE, "boss_slow_level", 1);
        switch (type) {
            case FIRE -> target.setSecondsOnFire((int) Engineer.mech(shooter, Engineer.IMPROVISED, "fire_seconds", 4));
            case STUN -> {
                int ticks = (int) Engineer.mech(shooter, Engineer.IMPROVISED, "stun_ticks", 10);
                if (boss) target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks * 2, 1));
                else CombatAbilities.stun(target, ticks);
            }
            case FROST -> {
                int ticks = (int) Math.round(Engineer.mech(shooter, Engineer.IMPROVISED, "freeze_seconds", 3) * 20);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks,
                        (int) Engineer.mech(shooter, Engineer.IMPROVISED, "freeze_level", 1)));
                // Escarcha visual sin daño por congelación (el daño empieza al llegar al máximo).
                target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() - 1));
            }
            case POISON -> target.addEffect(new MobEffectInstance(MobEffects.POISON,
                    (int) Math.round(Engineer.mech(shooter, Engineer.RUSTED, "poison_seconds", 6) * 20),
                    (int) Engineer.mech(shooter, Engineer.RUSTED, "poison_level", 0)));
            case WITHER -> target.addEffect(new MobEffectInstance(MobEffects.WITHER,
                    (int) Math.round(Engineer.mech(shooter, Engineer.RUSTED, "wither_seconds", 3) * 20),
                    (int) Engineer.mech(shooter, Engineer.RUSTED, "wither_level", 1)));
            case LEVITATION -> {
                if (boss) target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, bossSlow, bossLevel));
                else target.addEffect(new MobEffectInstance(MobEffects.LEVITATION,
                        (int) Math.round(Engineer.mech(shooter, Engineer.ARCANE, "levitation_seconds", 3) * 20), 0));
            }
            case PULL -> {
                if (boss) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, bossSlow, bossLevel));
                } else {
                    Vec3 to = shooter.position().subtract(target.position()).scale(Engineer.mech(shooter, Engineer.ARCANE, "pull_fraction", .25));
                    Vec3 dest = target.position().add(to);
                    target.teleportTo(dest.x, dest.y, dest.z);
                }
            }
            default -> {
            }
        }
    }
}
