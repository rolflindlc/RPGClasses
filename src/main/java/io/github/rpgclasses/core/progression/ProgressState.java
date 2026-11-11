package io.github.rpgclasses.core.progression;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Decisiones y contadores de progreso de un jugador. Todo lo demás (modificadores,
 * habilidades desbloqueadas, muros) se deriva de aquí. Lógica pura.
 */
public final class ProgressState {
    /** null = sin clase elegida. */
    public String classId;
    /** Rama -> puntos (0..10). */
    public final Map<String, Integer> branchPoints = new LinkedHashMap<>();
    /** Rama -> perk elegido al llenarla (regla 11). Se pierde al reiniciar la clase. */
    public final Map<String, String> perks = new LinkedHashMap<>();
    /** null = sin subclase elegida. */
    public String subclassId;
    public int subclassLevel;
    /** XP realmente pagada; base de lo que devuelve un reinicio de clase. */
    public long xpPaidBase;
    public long xpPaidSubclass;
    /** Sin uso desde que los reinicios son ilimitados; se conserva por compatibilidad del guardado. */
    public boolean firstRespecUsed;
    public boolean kitGranted;

    public int basePoints() {
        int sum = 0;
        for (int v : branchPoints.values()) sum += v;
        return sum;
    }

    /** Nivel Total de Clase: puntos de base + niveles de subclase. */
    public int totalLevel() {
        return basePoints() + subclassLevel;
    }

    public int pointsIn(String branchId) {
        return branchPoints.getOrDefault(branchId, 0);
    }

    /** Ramas con al menos 1 punto (regla 13: como máximo 3). */
    public int usedBranches() {
        int n = 0;
        for (int v : branchPoints.values()) if (v > 0) n++;
        return n;
    }

    public String perkIn(String branchId) {
        return perks.get(branchId);
    }

    public void copyFrom(ProgressState o) {
        classId = o.classId;
        branchPoints.clear();
        branchPoints.putAll(o.branchPoints);
        perks.clear();
        perks.putAll(o.perks);
        subclassId = o.subclassId;
        subclassLevel = o.subclassLevel;
        xpPaidBase = o.xpPaidBase;
        xpPaidSubclass = o.xpPaidSubclass;
        firstRespecUsed = o.firstRespecUsed;
        kitGranted = o.kitGranted;
    }
}
