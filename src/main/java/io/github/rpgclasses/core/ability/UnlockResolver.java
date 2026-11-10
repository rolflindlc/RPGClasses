package io.github.rpgclasses.core.ability;

import io.github.rpgclasses.core.definition.Definitions.AbilityDef;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.MilestoneDef;
import io.github.rpgclasses.core.definition.Definitions.SubclassDef;
import io.github.rpgclasses.core.progression.ProgressState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Habilidades que tiene un jugador, calculadas siempre a partir de su progreso.
 * No existe una lista guardada que pueda desfasarse tras un respec. Lógica pura.
 */
public final class UnlockResolver {

    /** Habilidades desbloqueadas, en el orden de los hitos (primero la clase, luego la subclase). */
    public static Set<String> unlocked(ProgressState s, ClassDef c, SubclassDef sub) {
        Set<String> out = new LinkedHashSet<>();
        if (c == null || !c.id().equals(s.classId)) return out;
        for (MilestoneDef m : c.milestones()) {
            if (s.totalLevel() >= m.level()) out.add(m.grants());
        }
        if (sub != null && sub.id().equals(s.subclassId)) {
            for (MilestoneDef m : sub.milestones()) {
                boolean reached = m.subclassLevel() ? s.subclassLevel >= m.level() : s.totalLevel() >= m.level();
                // Una mejora queda latente hasta tener la habilidad base que modifica.
                if (reached && (m.upgrades() == null || out.contains(m.upgrades()))) out.add(m.grants());
            }
        }
        return out;
    }

    /** De las desbloqueadas, las que se activan con tecla (las no pasivas), en orden: ranura 0, 1, 2... */
    public static List<String> actives(Set<String> unlocked, Function<String, AbilityDef> lookup) {
        List<String> out = new ArrayList<>();
        for (String id : unlocked) {
            AbilityDef def = lookup.apply(id);
            if (def != null && !isPassive(def)) out.add(id);
        }
        return out;
    }

    public static boolean isPassive(AbilityDef def) {
        return def.params().has("passive") && def.params().get("passive").getAsBoolean();
    }

    private UnlockResolver() {}
}
