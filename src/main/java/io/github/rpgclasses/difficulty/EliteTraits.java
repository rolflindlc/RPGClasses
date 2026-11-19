package io.github.rpgclasses.difficulty;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.config.ServerConfig;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Rasgos de élite: efectos especiales de los mobs a partir de la amenaza T = 40. Un mob escalado puede salir "de élite"
 * (la probabilidad sube con T) y entonces lleva 1 rasgo (T = 40), 2 (T = 60) o 3 (T = 80), distintos entre sí.
 * Los rasgos se guardan en el mob (NBT) y los aplican {@link DifficultyEvents} al golpear y al morir.
 */
public final class EliteTraits {
    /** Presente = el mob es de élite; el valor son los ids de sus rasgos separados por comas. */
    public static final String KEY_TRAITS = RpgClasses.MODID + "_traits";

    public enum Trait {
        /** Regeneración I permanente. */
        REGENERATING("regenerating"),
        /** Sus golpes prenden fuego al objetivo. */
        IGNEOUS("igneous"),
        /** Se cura una parte del daño que hace. */
        VAMPIRIC("vampiric"),
        /** Sus golpes ralentizan y debilitan al objetivo. */
        CRIPPLING("crippling"),
        /** Al morir explota (sin romper bloques). */
        VOLATILE("volatile");

        public final String id;

        Trait(String id) { this.id = id; }
    }

    private EliteTraits() {}

    /** Probabilidad de que un mob escalado sea de élite con esta T. */
    public static double eliteChance(double threat) {
        double start = ServerConfig.DIFFICULTY_ELITE_START.get();
        if (threat < start) return 0.0;
        return Math.min(1.0, ServerConfig.DIFFICULTY_ELITE_BASE.get() + ServerConfig.DIFFICULTY_ELITE_PER_T.get() * (threat - start));
    }

    /** Rasgos que lleva un mob de élite con esta T. */
    public static int traitCount(double threat) {
        double start = ServerConfig.DIFFICULTY_ELITE_START.get();
        if (threat < start) return 0;
        int count = 1 + (int) ((threat - start) / ServerConfig.DIFFICULTY_ELITE_TRAIT_STEP.get());
        return Math.min(ServerConfig.DIFFICULTY_ELITE_MAX_TRAITS.get(), Math.min(count, Trait.values().length));
    }

    public static void roll(Mob mob, double threat) {
        int count = traitCount(threat);
        RandomSource random = mob.getRandom();
        if (count <= 0 || random.nextDouble() >= eliteChance(threat)) return;
        List<Trait> pool = new ArrayList<>(List.of(Trait.values()));
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            Trait trait = pool.remove(random.nextInt(pool.size()));
            if (ids.length() > 0) ids.append(',');
            ids.append(trait.id);
            if (trait == Trait.REGENERATING) {
                // Efecto infinito: se guarda con el mob y no hace falta ningún gancho de tick.
                mob.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MobEffectInstance.INFINITE_DURATION, 0, false, true));
            }
        }
        mob.getPersistentData().putString(KEY_TRAITS, ids.toString());
    }

    public static boolean has(LivingEntity entity, Trait trait) {
        String ids = entity.getPersistentData().getString(KEY_TRAITS);
        return !ids.isEmpty() && ("," + ids + ",").contains("," + trait.id + ",");
    }
}
