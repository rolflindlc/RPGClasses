package io.github.rpgclasses.core.progression;

import java.util.Collection;
import java.util.Map;

/**
 * Equilibrio de puntos entre jugadores: el precio en XP del siguiente punto de clase baja para quien va por debajo de la
 * media de los <b>demás</b> jugadores activos y sube para quien va muy por delante.
 * <ul>
 *   <li>Con menos de {@link Rules#minOthers} jugadores activos aparte de ti, no hay ajuste.</li>
 *   <li>Dentro de ±{@link Rules#margin} puntos de la media, no hay ajuste; fuera, solo cuenta lo que pase del margen.</li>
 *   <li>Por debajo: {@link Rules#discountPerPoint} por punto, hasta {@link Rules#maxDiscount}.</li>
 *   <li>Por encima: {@link Rules#penaltyPerPoint} por punto, hasta {@link Rules#maxPenalty}.</li>
 * </ul>
 * El resultado es un descuento con signo: positivo abarata, negativo encarece (−0,15 = +15 % de precio).
 */
public final class CatchUp {
    public static final long HOUR_MS = 3_600_000L;

    private CatchUp() {}

    public record Rules(double margin, double discountPerPoint, double maxDiscount,
                        double penaltyPerPoint, double maxPenalty, int minOthers) {}

    /** Descuento con signo para quien tiene {@code myPoints} frente a los puntos de los demás jugadores activos. */
    public static double discount(int myPoints, Collection<Integer> othersPoints, Rules r) {
        if (othersPoints.isEmpty() || othersPoints.size() < r.minOthers()) return 0;
        long sum = 0;
        for (int p : othersPoints) sum += p;
        double average = (double) sum / othersPoints.size();
        return discount(myPoints, average, r);
    }

    /** Lo mismo con la media ya calculada (y suficientes jugadores). */
    public static double discount(int myPoints, double othersAverage, Rules r) {
        double diff = othersAverage - myPoints; // > 0: vas por detrás
        double excess = Math.abs(diff) - Math.max(0, r.margin());
        if (!(excess > 1e-9)) return 0;
        if (diff > 0) return clamp(excess * r.discountPerPoint(), r.maxDiscount());
        return -clamp(excess * r.penaltyPerPoint(), r.maxPenalty());
    }

    private static double clamp(double value, double max) {
        return Math.max(0, Math.min(Math.max(0, max), value));
    }

    /**
     * Tiempo jugado dentro de la ventana: {@code hours} es hora desde la época (ms / 3 600 000) → milisegundos jugados en
     * esa hora; solo cuentan las horas desde {@code firstHour}.
     */
    public static long playedMs(Map<Long, Long> hours, long firstHour) {
        long total = 0;
        for (Map.Entry<Long, Long> e : hours.entrySet()) {
            if (e.getKey() >= firstHour) total += e.getValue();
        }
        return total;
    }

    /**
     * Primera hora de una ventana de {@code windowHours} horas que acaba ahora. Se cuenta por horas enteras, así que la
     * ventana cubre las {@code windowHours} horas anteriores completas más la hora en curso (nunca menos de lo pedido).
     */
    public static long firstHour(long nowMs, int windowHours) {
        return nowMs / HOUR_MS - Math.max(1, windowHours);
    }
}
