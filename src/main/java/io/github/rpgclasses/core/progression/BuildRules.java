package io.github.rpgclasses.core.progression;

import io.github.rpgclasses.core.definition.Definitions.BranchDef;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.SubclassDef;

/** Aplicar una build guardada sobre un estado con la clase ya elegida. Lógica pura. */
public final class BuildRules {
    private BuildRules() {}

    /**
     * Gasta el saldo siguiendo la build: un punto por rama cada vuelta (en el orden de las ramas de la clase), los perks
     * en cuanto la rama se llena, la subclase en cuanto se puede y sus niveles cuando el tope lo permite. Se para al
     * acabarse el saldo o al completar la build. Nunca se salta las reglas (usa los mismos check*).
     *
     * @param sub definición de la subclase de la build (null si no tiene o ya no existe)
     * @return puntos gastados
     */
    public static int apply(ProgressState s, ClassDef def, SubclassDef sub, SavedBuild b) {
        if (def == null || b == null || !def.id().equals(s.classId) || !def.id().equals(b.classId())) return 0;
        int spent = 0;
        boolean progressed = true;
        while (progressed) {
            progressed = false;
            choosePerks(s, def, b);
            if (sub != null && b.subclassId() != null && s.subclassId == null
                    && ProgressionRules.checkChooseSubclass(s, def, sub).ok()) {
                ProgressionRules.chooseSubclass(s, sub);
            }
            for (BranchDef br : def.branches()) {
                int target = Math.min(b.branchPoints().getOrDefault(br.id(), 0), br.maxPoints());
                if (s.pointsIn(br.id()) < target && ProgressionRules.checkSpendBranch(s, def, br.id()).ok()) {
                    spent += ProgressionRules.spendBranch(s, br.id());
                    progressed = true;
                }
            }
            if (b.subclassId() != null && b.subclassId().equals(s.subclassId) && s.subclassLevel < b.subclassLevel()
                    && ProgressionRules.checkSpendSubclass(s).ok()) {
                spent += ProgressionRules.spendSubclass(s);
                progressed = true;
            }
        }
        choosePerks(s, def, b);
        return spent;
    }

    private static void choosePerks(ProgressState s, ClassDef def, SavedBuild b) {
        for (BranchDef br : def.branches()) {
            String perk = b.perks().get(br.id());
            if (perk != null && ProgressionRules.checkChoosePerk(s, def, br.id(), perk).ok()) {
                ProgressionRules.choosePerk(s, br.id(), perk);
            }
        }
    }
}
