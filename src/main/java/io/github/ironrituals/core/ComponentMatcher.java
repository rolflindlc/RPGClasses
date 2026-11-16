package io.github.ironrituals.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Reparto de componentes de un ritual entre las pilas disponibles en el área central. Lógica pura.
 * <p>
 * Cada requisito pide {@code count} unidades de pilas que acepte. El reparto es voraz pero empieza por los
 * requisitos más exigentes (los que aceptan menos pilas), lo que evita la mayoría de conflictos entre tags.
 */
public final class ComponentMatcher {
    private ComponentMatcher() {}

    /** Requisito: cuántas unidades y qué pilas (por índice) valen. */
    public record Requirement(int count, IntPredicate accepts) {}

    /**
     * @param available unidades disponibles en cada pila
     * @param reqs      requisitos
     * @return unidades tomadas de cada pila (mismo tamaño que {@code available}), o {@code null} si no alcanza
     */
    public static int[] allocate(int[] available, List<Requirement> reqs) {
        int[] left = available.clone();
        int[] taken = new int[available.length];

        List<Requirement> ordered = new ArrayList<>(reqs);
        ordered.sort(Comparator.comparingInt(r -> acceptedStacks(r, available.length)));

        for (Requirement req : ordered) {
            int need = req.count();
            for (int i = 0; i < left.length && need > 0; i++) {
                if (left[i] <= 0 || !req.accepts().test(i)) continue;
                int t = Math.min(need, left[i]);
                left[i] -= t;
                taken[i] += t;
                need -= t;
            }
            if (need > 0) return null;
        }
        return taken;
    }

    /** Resta un reparto de las unidades disponibles (para encadenar nivel base y catalizadores). */
    public static int[] subtract(int[] available, int[] taken) {
        int[] out = available.clone();
        for (int i = 0; i < out.length; i++) out[i] -= taken[i];
        return out;
    }

    private static int acceptedStacks(Requirement r, int n) {
        int c = 0;
        for (int i = 0; i < n; i++) if (r.accepts().test(i)) c++;
        return c;
    }
}
