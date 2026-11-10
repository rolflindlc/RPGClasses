package io.github.rpgclasses.core.progression;

/** Costes de nivel de clase y conversión de XP vanilla. Lógica pura. */
public final class XpMath {

    /** Coste del nivel de clase n (n = puntos totales comprados + 1): (80 + 6n) x multiplicador. */
    public static int costOfLevel(int n, double multiplier) {
        if (n < 1) throw new IllegalArgumentException("n debe ser >= 1");
        double raw = (80 + 6.0 * n) * Math.max(0, multiplier);
        return (int) Math.min(Integer.MAX_VALUE, Math.round(raw));
    }

    /** Suma de costes de los niveles (fromExclusive, toInclusive]. */
    public static long totalCost(int fromExclusive, int toInclusive, double multiplier) {
        long sum = 0;
        for (int n = fromExclusive + 1; n <= toInclusive; n++) sum += costOfLevel(n, multiplier);
        return sum;
    }

    /** XP necesaria para pasar del nivel vanilla {@code level} al siguiente. */
    public static int xpNeededForNextLevel(int level) {
        if (level >= 30) return 112 + (level - 30) * 9;
        if (level >= 15) return 37 + (level - 15) * 5;
        return 7 + level * 2;
    }

    /** Puntos de XP acumulados al llegar exactamente al nivel vanilla {@code level}. */
    public static long totalXpAtLevel(int level) {
        long l = Math.max(0, level);
        if (l <= 16) return l * l + 6 * l;
        if (l <= 31) return Math.round(2.5 * l * l - 40.5 * l + 360);
        return Math.round(4.5 * l * l - 162.5 * l + 2220);
    }

    /**
     * Puntos de XP reales de un jugador a partir de su nivel y progreso.
     * No se usa el campo vanilla totalExperience porque no baja al encantar.
     */
    public static long totalPoints(int level, float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        return totalXpAtLevel(level) + (long) Math.floor(p * xpNeededForNextLevel(Math.max(0, level)));
    }

    /** Nivel vanilla que corresponde a un total de puntos de XP. */
    public static int levelFromTotal(long total) {
        int level = 0;
        while (level < 100_000 && totalXpAtLevel(level + 1) <= total) level++;
        return level;
    }

    private XpMath() {}
}
