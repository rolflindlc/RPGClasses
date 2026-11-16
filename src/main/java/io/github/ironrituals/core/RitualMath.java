package io.github.ironrituals.core;

/** Fórmulas del plan (sección 2). Lógica pura. */
public final class RitualMath {
    private RitualMath() {}

    /** Ticks de canalización: {@code base + perLevel × nivel} (por defecto 10 s + 5 s por nivel). */
    public static int channelTicks(int level, int baseSeconds, int perLevelSeconds) {
        return 20 * (baseSeconds + perLevelSeconds * level);
    }

    /**
     * Multiplicador de duración por poder de hechizo (sección 2.3): {@code 1 + 0.5 × (poder − 1)}, acotado entre
     * 0.5 y {@code cap}.
     */
    public static double durationMultiplier(double schoolPower, double cap) {
        double m = 1.0 + 0.5 * (schoolPower - 1.0);
        return Math.max(0.5, Math.min(cap, m));
    }

    /** Maná que se drena en un tick para repartir {@code total} entre {@code ticks}. */
    public static float manaPerTick(float total, int ticks) {
        return ticks <= 0 ? total : total / ticks;
    }

    /** Ticks de un día de Minecraft. */
    public static final long DAY_TICKS = 24000L;

    /**
     * Ticks que hay que avanzar desde {@code dayTime} hasta la próxima vez que el reloj marque {@code target}
     * (0–23999). Siempre hacia delante; 0 si ya está en el objetivo.
     */
    public static long ticksUntil(long dayTime, long target) {
        return Math.floorMod(target - dayTime, DAY_TICKS);
    }

    /**
     * Nivel de Forja Eterna que exige un objeto según su durabilidad máxima (plan, 4.F.26): el menor nivel cuyo
     * umbral {@code thresholds[i]} (nivel i + 1) cubre la durabilidad. Devuelve 0 si no tiene durabilidad y -1 si
     * supera todos los umbrales.
     */
    public static int forgeLevel(int maxDamage, int... thresholds) {
        if (maxDamage <= 0) return 0;
        for (int i = 0; i < thresholds.length; i++) if (maxDamage <= thresholds[i]) return i + 1;
        return -1;
    }
}
