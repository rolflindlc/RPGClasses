package io.github.rpgclasses.core.value;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Número que escala con los puntos de una rama o el nivel de subclase.
 * Tres formas en JSON, sobre el objeto que lo contiene:
 * <ul>
 *   <li>{@code "to": 0.5}: lineal desde 0; nivel n = to * n / max.</li>
 *   <li>{@code "from": 0.001, "to": 0.01}: {@code from} en el nivel 1, {@code to} en el máximo.</li>
 *   <li>{@code "values": [..]}: tabla explícita, un valor por nivel.</li>
 * </ul>
 * El nivel 0 siempre vale 0.
 */
public final class LevelValue {
    private final double[] byLevel; // índice 0 = nivel 1

    private LevelValue(double[] byLevel) {
        this.byLevel = byLevel;
    }

    public static LevelValue upTo(double to, int maxLevel) {
        requireMax(maxLevel);
        double[] v = new double[maxLevel];
        for (int i = 0; i < maxLevel; i++) v[i] = to * (i + 1) / maxLevel;
        return new LevelValue(v);
    }

    public static LevelValue linear(double from, double to, int maxLevel) {
        requireMax(maxLevel);
        double[] v = new double[maxLevel];
        for (int i = 0; i < maxLevel; i++) {
            v[i] = maxLevel == 1 ? to : from + (to - from) * i / (maxLevel - 1);
        }
        return new LevelValue(v);
    }

    public static LevelValue table(double[] values) {
        requireMax(values.length);
        return new LevelValue(values.clone());
    }

    /** @throws IllegalArgumentException si el JSON no define un valor válido para {@code maxLevel} niveles. */
    public static LevelValue fromJson(JsonObject holder, int maxLevel) {
        if (holder.has("values")) {
            JsonArray arr = holder.getAsJsonArray("values");
            if (arr.size() != maxLevel) {
                throw new IllegalArgumentException("\"values\" tiene " + arr.size() + " entradas y hacen falta " + maxLevel);
            }
            double[] v = new double[maxLevel];
            for (int i = 0; i < maxLevel; i++) v[i] = finite(arr.get(i).getAsDouble());
            return new LevelValue(v);
        }
        if (!holder.has("to")) {
            throw new IllegalArgumentException("falta \"to\" o \"values\"");
        }
        double to = finite(holder.get("to").getAsDouble());
        if (holder.has("from")) {
            return linear(finite(holder.get("from").getAsDouble()), to, maxLevel);
        }
        return upTo(to, maxLevel);
    }

    public double at(int level) {
        if (level <= 0) return 0;
        return byLevel[Math.min(level, byLevel.length) - 1];
    }

    public double max() {
        return byLevel[byLevel.length - 1];
    }

    public int maxLevel() {
        return byLevel.length;
    }

    private static void requireMax(int maxLevel) {
        if (maxLevel < 1) throw new IllegalArgumentException("maxLevel debe ser >= 1");
    }

    private static double finite(double d) {
        if (!Double.isFinite(d)) throw new IllegalArgumentException("valor no finito");
        return d;
    }
}
