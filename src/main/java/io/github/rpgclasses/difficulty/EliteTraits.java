package io.github.rpgclasses.difficulty;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.config.ServerConfig;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.List;

/**
 * Rasgos de élite: efectos especiales de los mobs escalados. Un mob puede salir "de élite" (0 % por debajo de
 * {@code eliteStartThreat}, creciendo con T) y entonces lleva uno o varios rasgos distintos. Cada rasgo tiene su
 * propia T de desbloqueo (repartidas a lo largo de toda la escala), se puede apagar por separado y suelta sus
 * propias partículas ({@link TraitBehaviors}). Los rasgos se guardan en el mob (NBT).
 * <p>
 * Para añadir un rasgo: una entrada en {@link Trait}, su apartado en {@code ServerConfig}, su comportamiento y
 * su partícula en {@link TraitBehaviors}, y su nombre en el texto de {@link DifficultyInfo}.
 */
public final class EliteTraits {
    /** Presente = el mob es de élite; el valor son los ids de sus rasgos separados por comas. */
    public static final String KEY_TRAITS = RpgClasses.MODID + "_traits";

    public enum Trait {
        /** +50 % de velocidad de movimiento. */
        SWIFT("swift", 10),
        /** Sus golpes envenenan. */
        POISONOUS("poisonous", 15),
        /** Sus golpes prenden fuego al objetivo. */
        IGNEOUS("igneous", 20),
        /** Regeneración I permanente. */
        REGENERATING("regenerating", 25),
        /** Sus golpes ralentizan y debilitan al objetivo. */
        CRIPPLING("crippling", 30),
        /** Al morir explota (sin romper bloques). */
        VOLATILE("volatile", 35),
        /** Se cura una parte del daño que hace. */
        VAMPIRIC("vampiric", 40),
        /** Se teletransporta junto a su objetivo. */
        TELEPORTING("teleporting", 50),
        /** Los proyectiles rebotan en él. */
        DEFLECTING("deflecting", 60),
        /** Más grande, +50 % de vida, -25 % de velocidad; al morir explota (solo visual) y suelta 3 mobs iguales sin rasgos. */
        COLOSSUS("colossus", 70);

        public final String id;
        /** Amenaza T mínima por defecto (se cambia en la configuración). */
        public final double defaultUnlock;

        Trait(String id, double defaultUnlock) {
            this.id = id;
            this.defaultUnlock = defaultUnlock;
        }

        public boolean enabled() { return ServerConfig.TRAIT_ENABLED.get(this).get(); }

        public double unlockThreat() { return ServerConfig.TRAIT_UNLOCK.get(this).get(); }
    }

    private EliteTraits() {}

    /** Probabilidad de que un mob escalado sea de élite con esta T (0 por debajo de eliteStartThreat). */
    public static double eliteChance(double threat) {
        double start = ServerConfig.DIFFICULTY_ELITE_START.get();
        if (threat < start) return 0.0;
        return Math.min(1.0, ServerConfig.DIFFICULTY_ELITE_BASE.get() + ServerConfig.DIFFICULTY_ELITE_PER_T.get() * (threat - start));
    }

    /** Rasgos que puede salir con esta T: activados y ya desbloqueados. */
    public static List<Trait> available(double threat) {
        List<Trait> list = new ArrayList<>();
        for (Trait trait : Trait.values()) {
            if (trait.enabled() && threat >= trait.unlockThreat()) list.add(trait);
        }
        return list;
    }

    /** Rasgos que lleva un mob de élite con esta T (sin pasar de los disponibles). */
    public static int traitCount(double threat) {
        double start = ServerConfig.DIFFICULTY_ELITE_START.get();
        if (threat < start) return 0;
        int count = 1 + (int) ((threat - start) / ServerConfig.DIFFICULTY_ELITE_TRAIT_STEP.get());
        return Math.min(Math.min(ServerConfig.DIFFICULTY_ELITE_MAX_TRAITS.get(), count), available(threat).size());
    }

    public static void roll(Mob mob, double threat) {
        int count = traitCount(threat);
        RandomSource random = mob.getRandom();
        if (count <= 0 || random.nextDouble() >= eliteChance(threat)) return;
        List<Trait> pool = available(threat);
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            Trait trait = pool.remove(random.nextInt(pool.size()));
            if (ids.length() > 0) ids.append(',');
            ids.append(trait.id);
        }
        String chosen = ids.toString();
        mob.getPersistentData().putString(KEY_TRAITS, chosen);
        for (String id : chosen.split(",")) {
            for (Trait trait : Trait.values()) {
                if (trait.id.equals(id)) TraitBehaviors.onApply(mob, trait);
            }
        }
    }

    public static boolean has(LivingEntity entity, Trait trait) {
        String ids = entity.getPersistentData().getString(KEY_TRAITS);
        return !ids.isEmpty() && ("," + ids + ",").contains("," + trait.id + ",");
    }
}
