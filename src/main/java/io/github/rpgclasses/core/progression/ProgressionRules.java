package io.github.rpgclasses.core.progression;

import io.github.rpgclasses.core.definition.Definitions.BranchDef;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.SubclassDef;

/**
 * Todas las reglas de progresión del documento de diseño (sección 1), como funciones puras.
 * Patrón: un método check* que no modifica nada y devuelve un {@link Result}, y un método
 * de aplicación que solo debe llamarse tras un check con resultado OK.
 */
public final class ProgressionRules {
    public static final int BRANCH_MAX = 10;
    public static final int BRANCHES = 3;
    public static final int BASE_MAX = BRANCH_MAX * BRANCHES;
    public static final int SUBCLASS_MAX = 10;
    public static final int SUBCLASS_UNLOCK = 15;

    public enum Result {
        OK, NO_CLASS, ALREADY_HAS_CLASS, UNKNOWN_CLASS, UNKNOWN_BRANCH, BRANCH_FULL,
        SUBCLASS_LOCKED, UNKNOWN_SUBCLASS, ALREADY_HAS_SUBCLASS, NO_SUBCLASS, SUBCLASS_CAP,
        NOT_ENOUGH_XP, IN_COMBAT, CLASS_DISABLED;

        public boolean ok() {
            return this == OK;
        }
    }

    /** Niveles de subclase permitidos: 0 antes de 15 puntos de base; luego floor((base - 10) / 2), máximo 10. */
    public static int subclassCap(int basePoints) {
        if (basePoints < SUBCLASS_UNLOCK) return 0;
        return Math.min(SUBCLASS_MAX, (Math.min(basePoints, BASE_MAX) - 10) / 2);
    }

    /** Coste del siguiente punto, sea de base o de subclase. */
    public static int nextCost(ProgressState s, double multiplier) {
        return XpMath.costOfLevel(s.totalLevel() + 1, multiplier);
    }

    // --- Elegir clase ---

    public static Result checkChooseClass(ProgressState s, ClassDef def) {
        if (s.classId != null) return Result.ALREADY_HAS_CLASS;
        if (def == null) return Result.UNKNOWN_CLASS;
        return Result.OK;
    }

    public static void chooseClass(ProgressState s, ClassDef def) {
        s.classId = def.id();
        s.branchPoints.clear();
        for (BranchDef b : def.branches()) s.branchPoints.put(b.id(), 0);
        s.subclassId = null;
        s.subclassLevel = 0;
        s.xpPaidBase = 0;
        s.xpPaidSubclass = 0;
        s.kitGranted = false;
    }

    // --- Puntos de rama ---

    /** @param def definición de la clase del jugador; null si la clase ya no existe ("en suspenso"). */
    public static Result checkSpendBranch(ProgressState s, ClassDef def, String branchId, long availableXp, double multiplier) {
        if (s.classId == null) return Result.NO_CLASS;
        if (def == null || !def.id().equals(s.classId)) return Result.UNKNOWN_CLASS;
        BranchDef branch = def.branch(branchId);
        if (branch == null) return Result.UNKNOWN_BRANCH;
        if (s.pointsIn(branchId) >= branch.maxPoints()) return Result.BRANCH_FULL;
        if (availableXp < nextCost(s, multiplier)) return Result.NOT_ENOUGH_XP;
        return Result.OK;
    }

    /** @return XP a cobrar. */
    public static int spendBranch(ProgressState s, String branchId, double multiplier) {
        int cost = nextCost(s, multiplier);
        s.branchPoints.merge(branchId, 1, Integer::sum);
        s.xpPaidBase += cost;
        return cost;
    }

    // --- Subclase ---

    public static Result checkChooseSubclass(ProgressState s, ClassDef classDef, SubclassDef subDef) {
        if (s.classId == null) return Result.NO_CLASS;
        if (classDef == null || !classDef.id().equals(s.classId)) return Result.UNKNOWN_CLASS;
        if (s.subclassId != null) return Result.ALREADY_HAS_SUBCLASS;
        if (subDef == null || !classDef.subclasses().contains(subDef.id())) return Result.UNKNOWN_SUBCLASS;
        if (s.basePoints() < SUBCLASS_UNLOCK) return Result.SUBCLASS_LOCKED;
        return Result.OK;
    }

    public static void chooseSubclass(ProgressState s, SubclassDef subDef) {
        s.subclassId = subDef.id();
        s.subclassLevel = 0;
        s.xpPaidSubclass = 0;
    }

    public static Result checkSpendSubclass(ProgressState s, long availableXp, double multiplier) {
        if (s.classId == null) return Result.NO_CLASS;
        if (s.subclassId == null) return Result.NO_SUBCLASS;
        if (s.subclassLevel >= subclassCap(s.basePoints())) return Result.SUBCLASS_CAP;
        if (availableXp < nextCost(s, multiplier)) return Result.NOT_ENOUGH_XP;
        return Result.OK;
    }

    /** @return XP a cobrar. */
    public static int spendSubclass(ProgressState s, double multiplier) {
        int cost = nextCost(s, multiplier);
        s.subclassLevel++;
        s.xpPaidSubclass += cost;
        return cost;
    }

    // --- Respec ---

    public static Result checkRespecSubclass(ProgressState s, boolean inCombat) {
        if (s.classId == null) return Result.NO_CLASS;
        if (s.subclassId == null) return Result.NO_SUBCLASS;
        if (inCombat) return Result.IN_COMBAT;
        return Result.OK;
    }

    /** Quita la subclase (se podrá elegir otra). @return XP a devolver (solo el primer respec devuelve). */
    public static long respecSubclass(ProgressState s) {
        long refund = s.firstRespecUsed ? 0 : s.xpPaidSubclass;
        s.firstRespecUsed = true;
        s.subclassId = null;
        s.subclassLevel = 0;
        s.xpPaidSubclass = 0;
        return refund;
    }

    public static Result checkRespecClass(ProgressState s, boolean inCombat) {
        if (s.classId == null) return Result.NO_CLASS;
        if (inCombat) return Result.IN_COMBAT;
        return Result.OK;
    }

    /** Quita clase y subclase (se podrá elegir otra clase). @return XP a devolver. */
    public static long respecClass(ProgressState s) {
        long refund = s.firstRespecUsed ? 0 : s.xpPaidBase + s.xpPaidSubclass;
        s.firstRespecUsed = true;
        s.classId = null;
        s.branchPoints.clear();
        s.subclassId = null;
        s.subclassLevel = 0;
        s.xpPaidBase = 0;
        s.xpPaidSubclass = 0;
        s.kitGranted = false;
        return refund;
    }

    private ProgressionRules() {}
}
