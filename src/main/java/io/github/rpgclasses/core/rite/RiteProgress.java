package io.github.rpgclasses.core.rite;

import io.github.rpgclasses.core.definition.Definitions.ObjectiveDef;
import io.github.rpgclasses.core.definition.Definitions.RiteDef;

import java.util.Map;
import java.util.Set;

/**
 * Progreso de un rito (lógica pura). Se guarda en {@code ProgressState.riteProgress}:
 * <ul>
 *   <li>{@code "o<i>"}: contador del objetivo i (o 1 = hecho, en los objetivos de una vez).</li>
 *   <li>{@code "o<i>|<sub>"}: contador por tipo (tipo de mob, estilo de combate) de kill_variety y kill_styles.</li>
 * </ul>
 */
public final class RiteProgress {
    /** Objetivos que se cumplen de una vez (ofrenda, construcción, jefe). */
    public static final Set<String> ONE_SHOT = Set.of("offer", "structure", "boss");

    private RiteProgress() {}

    public static String key(int objective) {
        return "o" + objective;
    }

    public static String key(int objective, String sub) {
        return "o" + objective + "|" + sub;
    }

    public static int add(Map<String, Integer> p, int objective, int amount) {
        return p.merge(key(objective), amount, Integer::sum);
    }

    public static int addSub(Map<String, Integer> p, int objective, String sub, int amount) {
        return p.merge(key(objective, sub), amount, Integer::sum);
    }

    public static void markDone(Map<String, Integer> p, int objective) {
        p.put(key(objective), 1);
    }

    /** Meta del objetivo tal como se muestra (tipos distintos para kill_variety y kill_styles). */
    public static int target(ObjectiveDef o) {
        if (ONE_SHOT.contains(o.type())) return 1;
        if ("kill_variety".equals(o.type())) return intParam(o, "types", 1);
        if ("kill_styles".equals(o.type())) return intParam(o, "styles", 1);
        return o.count();
    }

    /** Valor actual: para kill_variety y kill_styles, cuántos tipos llegan a la cantidad por tipo. */
    public static int value(ObjectiveDef o, int index, Map<String, Integer> p) {
        if ("kill_variety".equals(o.type()) || "kill_styles".equals(o.type())) {
            String prefix = key(index) + "|";
            int n = 0;
            for (Map.Entry<String, Integer> e : p.entrySet()) {
                if (e.getKey().startsWith(prefix) && e.getValue() >= o.count()) n++;
            }
            return n;
        }
        return p.getOrDefault(key(index), 0);
    }

    public static boolean done(ObjectiveDef o, int index, Map<String, Integer> p) {
        return value(o, index, p) >= target(o);
    }

    public static boolean complete(RiteDef rite, Map<String, Integer> p) {
        for (int i = 0; i < rite.objectives().size(); i++) if (!done(rite.objectives().get(i), i, p)) return false;
        return true;
    }

    private static int intParam(ObjectiveDef o, String name, int fallback) {
        return o.params().has(name) ? o.params().get(name).getAsInt() : fallback;
    }
}
