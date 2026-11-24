package io.github.rpgclasses.core.progression;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Una build guardada: clase, puntos por rama, perks y subclase con su nivel. Inmutable. Se aplica con
 * {@link BuildRules#apply}, que gasta los puntos en un orden válido hasta donde llegue el saldo.
 */
public record SavedBuild(String classId, Map<String, Integer> branchPoints, Map<String, String> perks, String subclassId,
                         int subclassLevel) {
    /** Huecos de build por jugador. */
    public static final int SLOTS = 3;

    public SavedBuild {
        branchPoints = Map.copyOf(new LinkedHashMap<>(branchPoints));
        perks = Map.copyOf(new LinkedHashMap<>(perks));
        subclassLevel = Math.max(0, subclassLevel);
    }

    /** Lo que tiene ahora el jugador. */
    public static SavedBuild of(ProgressState s) {
        Map<String, Integer> pts = new LinkedHashMap<>();
        s.branchPoints.forEach((k, v) -> {
            if (v > 0) pts.put(k, v);
        });
        return new SavedBuild(s.classId, pts, new LinkedHashMap<>(s.perks), s.subclassId, s.subclassId == null ? 0 : s.subclassLevel);
    }

    /** Puntos que hacen falta para completarla. */
    public int totalPoints() {
        int sum = subclassLevel;
        for (int v : branchPoints.values()) sum += v;
        return sum;
    }
}
