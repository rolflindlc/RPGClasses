package io.github.ironrituals.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Geometría de los círculos arcanos (plan, sección 2.1). Lógica pura, sin Minecraft, para poder probarla.
 * <p>
 * Un anillo de radio {@code r} es el perímetro del cuadrado de semilado {@code r} centrado en el núcleo, con una
 * vela cada 2 bloques. Las velas ocupan las casillas del perímetro con ambas coordenadas pares, lo que da
 * {@code 4·r} velas por anillo: 8 (r=2), 16 (r=4) y 24 (r=6).
 */
public final class CircleGeometry {
    /** Nivel máximo de círculo. */
    public static final int MAX_LEVEL = 3;

    private CircleGeometry() {}

    /** Radio del anillo exterior de un nivel: 2, 4 o 6. */
    public static int ringRadius(int level) {
        checkLevel(level);
        return level * 2;
    }

    /** Desplazamientos (dx, dz) de las velas de un único anillo de radio {@code r}. */
    public static List<int[]> ringOffsets(int radius) {
        if (radius <= 0 || radius % 2 != 0) throw new IllegalArgumentException("radio inválido: " + radius);
        List<int[]> out = new ArrayList<>(4 * radius);
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == radius) out.add(new int[]{dx, dz});
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** Velas que exige un nivel de círculo (todos sus anillos): 8, 24 o 48. */
    public static int candlesRequired(int level) {
        checkLevel(level);
        int total = 0;
        for (int l = 1; l <= level; l++) total += 4 * ringRadius(l);
        return total;
    }

    /**
     * Calcula el nivel válido de un círculo a partir del estado de cada anillo.
     *
     * @param ringValid {@code ringValid[i]} indica si el anillo del nivel {@code i+1} tiene todas sus velas
     *                  colocadas y encendidas
     * @return el mayor nivel cuyos anillos 1..N son todos válidos, o 0 si el primero no lo es
     */
    public static int validLevel(boolean[] ringValid) {
        int level = 0;
        for (int i = 0; i < Math.min(ringValid.length, MAX_LEVEL); i++) {
            if (!ringValid[i]) break;
            level = i + 1;
        }
        return level;
    }

    private static void checkLevel(int level) {
        if (level < 1 || level > MAX_LEVEL) throw new IllegalArgumentException("nivel inválido: " + level);
    }
}
