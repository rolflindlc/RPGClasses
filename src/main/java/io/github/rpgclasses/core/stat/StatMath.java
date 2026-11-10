package io.github.rpgclasses.core.stat;

/** Fórmulas de muros, críticos, topes y daño. Lógica pura; la usan StatEngine y DamagePipeline. */
public final class StatMath {
    public static final double SPEED_BONUS_CAP = 1.0;
    public static final double JUMP_BONUS_CAP = 1.5;
    public static final double BASE_CRIT_MULTIPLIER = 1.5;
    private static final int WALL_FULL_AT = 30;

    /** Penalización de muro = máximo x min(puntos base, 30) / 30. */
    public static double wallPenalty(double wallMax, int basePoints) {
        double max = clamp01(wallMax);
        int pts = Math.max(0, Math.min(basePoints, WALL_FULL_AT));
        return max * pts / WALL_FULL_AT;
    }

    /** Muro tras una pasiva de mitigación (Armonía Marcial / Arcana): mitigation 1.0 lo anula. */
    public static double mitigatedWall(double penalty, double mitigation) {
        return clamp01(penalty) * (1 - clamp01(mitigation));
    }

    /** Multiplicador de daño (o de poder de hechizo) que deja un muro. */
    public static double wallDamageFactor(double penalty) {
        return 1 - clamp01(penalty);
    }

    /** Muro de Escudos: el cooldown de recuperación crece el doble que el muro. */
    public static double shieldCooldownFactor(double penalty) {
        return 1 + 2 * clamp01(penalty);
    }

    public static double shieldDurabilityFactor(double penalty) {
        return 1 + clamp01(penalty);
    }

    /** Tope global: solo se suman aquí los bonus del propio mod. */
    public static double capSpeedBonus(double modBonusSum) {
        return Math.min(modBonusSum, SPEED_BONUS_CAP);
    }

    public static double capJumpBonus(double modBonusSum) {
        return Math.min(modBonusSum, JUMP_BONUS_CAP);
    }

    public static double critChance(double base, double classBonus, boolean guaranteed) {
        return guaranteed ? 1.0 : clamp01(base + classBonus);
    }

    /** Multiplicador de un golpe crítico: x1,5 más los bonus de daño crítico de las ramas. */
    public static double critMultiplier(double critDamageBonusSum) {
        return BASE_CRIT_MULTIPLIER + Math.max(0, critDamageBonusSum);
    }

    /** Bonus aditivos: se suman entre sí antes de multiplicar. (0,30 + 1,00) -> x2,3. */
    public static double additiveFactor(double... bonuses) {
        double sum = 0;
        for (double b : bonuses) sum += b;
        return Math.max(0, 1 + sum);
    }

    /** Última línea de defensa del pipeline: nunca NaN, infinito ni negativo. */
    public static float sanitizeDamage(double damage) {
        if (!Double.isFinite(damage) || damage < 0) return 0f;
        return (float) Math.min(damage, Float.MAX_VALUE);
    }

    private static double clamp01(double v) {
        if (!Double.isFinite(v)) return 0;
        return Math.max(0, Math.min(1, v));
    }

    private StatMath() {}
}
