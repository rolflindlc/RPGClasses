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
}
