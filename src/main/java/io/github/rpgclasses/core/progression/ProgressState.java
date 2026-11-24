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
    /** Puntos de clase sin gastar (se compran con XP, con libros de conocimiento o al derrotar jefes). */
    public int pointBalance;
    /** Especies de jefe que ya le han dado su punto (puntos por jefes). */
    public final java.util.Set<String> bossesCredited = new java.util.LinkedHashSet<>();
    /** Builds guardadas (huecos fijos; null = vacío). */
    public final SavedBuild[] builds = new SavedBuild[SavedBuild.SLOTS];
    public boolean kitGranted;
    /** Raza y subraza (anexo de razas): permanentes, no se pierden al reiniciar la clase. null = sin elegir. */
    public String raceId;
    public String subraceId;
    /** Rango de raza, 0..3 (sube con los ritos). */
    public int raceRank;
    /** Humano: muros elegidos para Adaptable (1, o 2 el Polivalente). Se eligen de nuevo al reiniciar la clase. */
    public final java.util.List<String> humanWalls = new java.util.ArrayList<>();
    /** Progreso del rito en curso (el que lleva al rango siguiente). Claves de {@code RiteProgress}. */
    public final java.util.Map<String, Integer> riteProgress = new java.util.HashMap<>();

    public int basePoints() {
        int sum = 0;
        for (int v : branchPoints.values()) sum += v;
        return sum;
    }

    /** Nivel Total de Clase: puntos de base + niveles de subclase. */
    public int totalLevel() {
        return basePoints() + subclassLevel;
    }

    /** Puntos que tiene en total: gastados en el árbol más el saldo. */
    public int ownedPoints() {
        return totalLevel() + pointBalance;
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
        pointBalance = o.pointBalance;
        bossesCredited.clear();
        bossesCredited.addAll(o.bossesCredited);
        System.arraycopy(o.builds, 0, builds, 0, builds.length);
        kitGranted = o.kitGranted;
        raceId = o.raceId;
        subraceId = o.subraceId;
        raceRank = o.raceRank;
        humanWalls.clear();
        humanWalls.addAll(o.humanWalls);
        riteProgress.clear();
        riteProgress.putAll(o.riteProgress);
    }
}
