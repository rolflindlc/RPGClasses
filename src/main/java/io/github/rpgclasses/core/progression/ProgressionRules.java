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
    /** Ramas que tiene cada clase (regla 13). */
    public static final int BRANCHES_PER_CLASS = 5;
    /** Ramas que puede usar un jugador: al poner puntos en tantas, las demás se bloquean (regla 13). */
    public static final int BRANCHES_USABLE = 3;
    public static final int BASE_MAX = BRANCH_MAX * BRANCHES_USABLE;
    /** Perks por rama (regla 11). */
    public static final int PERKS_PER_BRANCH = 2;
    public static final int SUBCLASS_MAX = 10;
    public static final int SUBCLASS_UNLOCK = 15;

    public enum Result {
        OK, NO_CLASS, ALREADY_HAS_CLASS, UNKNOWN_CLASS, UNKNOWN_BRANCH, BRANCH_FULL,
        SUBCLASS_LOCKED, UNKNOWN_SUBCLASS, ALREADY_HAS_SUBCLASS, NO_SUBCLASS, SUBCLASS_CAP,
        NOT_ENOUGH_XP, IN_COMBAT, CLASS_DISABLED, BRANCH_LOCKED, UNKNOWN_PERK, PERK_UNAVAILABLE, PERK_ALREADY_CHOSEN,
        NO_RACE, ALREADY_HAS_RACE, UNKNOWN_RACE, UNKNOWN_SUBRACE, SUBRACE_UNAVAILABLE, NOT_HUMAN, WALL_INVALID, WALL_SLOTS_FULL;

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

    // --- Raza (anexo de razas): permanente ---

    public static final int RACE_MAX_RANK = 3;

    public static Result checkChooseRace(ProgressState s, io.github.rpgclasses.core.definition.Definitions.RaceDef race, String subraceId) {
        if (s.raceId != null) return Result.ALREADY_HAS_RACE;
        if (race == null) return Result.UNKNOWN_RACE;
        io.github.rpgclasses.core.definition.Definitions.SubraceDef sub = race.subrace(subraceId);
        if (sub == null) return Result.UNKNOWN_SUBRACE;
        if (!sub.available()) return Result.SUBRACE_UNAVAILABLE;
        return Result.OK;
    }

    public static void chooseRace(ProgressState s, io.github.rpgclasses.core.definition.Definitions.RaceDef race, String subraceId) {
        s.raceId = race.id();
        s.subraceId = subraceId;
        s.raceRank = 0;
        s.humanWalls.clear();
        s.riteProgress.clear();
    }

    /** Humano: elegir el muro (o el segundo, el Polivalente) al que se aplica Adaptable. */
    public static Result checkChooseWall(ProgressState s, ClassDef def, io.github.rpgclasses.core.definition.Definitions.RaceDef race, String discipline) {
        if (s.classId == null) return Result.NO_CLASS;
        if (def == null || !def.id().equals(s.classId)) return Result.UNKNOWN_CLASS;
        int slots = io.github.rpgclasses.core.stat.StatCalculator.humanWallSlots(s, race);
        if (slots <= 0) return Result.NOT_HUMAN;
        if (s.humanWalls.size() >= slots) return Result.WALL_SLOTS_FULL;
        if (def.walls().getOrDefault(discipline, 0.0) <= 0 || s.humanWalls.contains(discipline)) return Result.WALL_INVALID;
        return Result.OK;
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
        s.perks.clear();
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
        if (isBranchLocked(s, branchId)) return Result.BRANCH_LOCKED;
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

    /** Regla 13: una rama sin puntos queda bloqueada en cuanto hay puntos en 3 ramas distintas. */
    public static boolean isBranchLocked(ProgressState s, String branchId) {
        return s.pointsIn(branchId) == 0 && s.usedBranches() >= BRANCHES_USABLE;
    }

    // --- Perks (regla 11) ---

    /** true si la rama está llena y aún no se ha elegido su perk. */
    public static boolean perkPending(ProgressState s, BranchDef b) {
        return !b.perks().isEmpty() && s.pointsIn(b.id()) >= b.maxPoints() && s.perkIn(b.id()) == null;
    }

    public static Result checkChoosePerk(ProgressState s, ClassDef def, String branchId, String perkId) {
        if (s.classId == null) return Result.NO_CLASS;
        if (def == null || !def.id().equals(s.classId)) return Result.UNKNOWN_CLASS;
        BranchDef branch = def.branch(branchId);
        if (branch == null) return Result.UNKNOWN_BRANCH;
        if (branch.perk(perkId) == null) return Result.UNKNOWN_PERK;
        if (s.pointsIn(branchId) < branch.maxPoints()) return Result.PERK_UNAVAILABLE;
        if (s.perkIn(branchId) != null) return Result.PERK_ALREADY_CHOSEN;
        return Result.OK;
    }

    /** El perk es gratis y fijo hasta reiniciar la clase. */
    public static void choosePerk(ProgressState s, String branchId, String perkId) {
        s.perks.put(branchId, perkId);
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

    /**
     * Quita la subclase (se podrá elegir otra). Sin límite de usos.
     * Devuelve el coste actual de los niveles que se pierden (los más altos del Nivel Total)
     * menos la fracción {@code lossFraction}. Así, con pérdida 0, lo devuelto alcanza exactamente
     * para volver a comprarlos, se hayan comprado en el orden que sea.
     * @return XP a devolver.
     */
    public static long respecSubclass(ProgressState s, double multiplier, double lossFraction) {
        int total = s.totalLevel();
        long removedCost = Math.min(XpMath.totalCost(total - s.subclassLevel, total, multiplier), s.xpPaidBase + s.xpPaidSubclass);
        s.xpPaidBase = s.xpPaidBase + s.xpPaidSubclass - removedCost;
        s.xpPaidSubclass = 0;
        s.subclassId = null;
        s.subclassLevel = 0;
        return refund(removedCost, lossFraction);
    }

    public static Result checkRespecClass(ProgressState s, boolean inCombat) {
        if (s.classId == null) return Result.NO_CLASS;
        if (inCombat) return Result.IN_COMBAT;
        return Result.OK;
    }

    /** Quita clase y subclase (se podrá elegir otra clase). Sin límite de usos. @return XP a devolver. */
    public static long respecClass(ProgressState s, double lossFraction) {
        long paid = s.xpPaidBase + s.xpPaidSubclass;
        s.classId = null;
        s.branchPoints.clear();
        s.perks.clear();
        s.subclassId = null;
        s.subclassLevel = 0;
        s.xpPaidBase = 0;
        s.xpPaidSubclass = 0;
        s.kitGranted = false;
        s.humanWalls.clear();
        return refund(paid, lossFraction);
    }

    private static long refund(long paid, double lossFraction) {
        double loss = Double.isFinite(lossFraction) ? Math.max(0, Math.min(1, lossFraction)) : 0;
        return (long) Math.floor(paid * (1 - loss));
    }

    /** true si ya no queda ningún punto por comprar (30 de base y 10 de subclase). */
    public static boolean isMaxed(ProgressState s) {
        return s.basePoints() >= BASE_MAX && s.subclassLevel >= SUBCLASS_MAX;
    }

    private ProgressionRules() {}
}
