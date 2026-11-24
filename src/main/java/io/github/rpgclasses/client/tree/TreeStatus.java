package io.github.rpgclasses.client.tree;

import io.github.rpgclasses.client.tree.TreeLayout.Node;
import io.github.rpgclasses.client.tree.TreePlan.Step;
import io.github.rpgclasses.client.tree.TreePlan.Type;
import io.github.rpgclasses.core.definition.Definitions.BranchDef;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.PerkDef;
import io.github.rpgclasses.core.progression.ProgressState;
import io.github.rpgclasses.core.progression.ProgressionRules;
import io.github.rpgclasses.core.progression.ProgressionRules.Result;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Estado visual de cada nodo a partir del estado real y del simulado con el plan. Lógica pura. */
public final class TreeStatus {
    public enum Status {
        /** Comprado o elegido (estado real). */
        OWNED,
        /** En el plan, aún sin confirmar. */
        PLANNED,
        /** Es el siguiente y se puede pagar. */
        AVAILABLE,
        /** Es el siguiente, pero falta XP. */
        POOR,
        /** Aún no se llega. */
        FUTURE,
        /** Rama o subclase bloqueada (regla 13 o subclase ya elegida). */
        LOCKED,
        /** Quedará bloqueada al confirmar el plan. */
        WILL_LOCK,
        /** Nivel de subclase por encima del tope que permiten los puntos de base. */
        CAPPED,
        /** Perk no elegido (se eligió el otro). */
        DISCARDED,
        /** Vista previa de otra clase: solo se consulta. */
        PREVIEW
    }

    private final Map<Node, Status> statuses = new IdentityHashMap<>();
    public final TreePlan.Simulation sim;
    public final boolean editable;
    /** Ramas que quedarán bloqueadas al confirmar el plan (índices). */
    public final Set<Integer> willLock = new HashSet<>();

    public TreeStatus(TreeLayout layout, TreePlan.Context c, TreePlan.Simulation sim, boolean editable) {
        this.sim = sim;
        this.editable = editable;
        ClassDef def = layout.def;
        ProgressState real = c == null ? new ProgressState() : c.real();
        ProgressState s = sim == null ? real : sim.state;
        long pointsLeft = sim == null ? 0 : sim.pointsLeft;

        for (Node n : layout.nodes) {
            Status st;
            if (!editable) {
                st = Status.PREVIEW;
            } else {
                st = switch (n.kind) {
                    case CORE -> Status.OWNED;
                    case KEYSTONE -> keystone(def.branches().get(n.index), real, s, c);
                    case POINT -> point(def.branches().get(n.index), n.level, real, s, c);
                    case PERK -> perk(def.branches().get(n.index), n.level, real, s);
                    case GEM -> real.totalLevel() >= n.level ? Status.OWNED : s.totalLevel() >= n.level ? Status.PLANNED : Status.FUTURE;
                    case SUB_HUB -> real.basePoints() >= ProgressionRules.SUBCLASS_UNLOCK ? Status.OWNED
                            : s.basePoints() >= ProgressionRules.SUBCLASS_UNLOCK ? Status.PLANNED : Status.LOCKED;
                    case SUB_KEY -> subKey(def.subclasses().get(n.index), real, s, c);
                    case SUB_POINT -> subPoint(def.subclasses().get(n.index), n.level, real, s, c);
                };
            }
            statuses.put(n, st);
        }
        if (editable) {
            for (int k = 0; k < def.branches().size(); k++) {
                String id = def.branches().get(k).id();
                if (!ProgressionRules.isBranchLocked(real, id) && ProgressionRules.isBranchLocked(s, id)) willLock.add(k);
            }
        }
    }

    public Status of(Node n) {
        return statuses.getOrDefault(n, Status.FUTURE);
    }

    private static Status keystone(BranchDef b, ProgressState real, ProgressState s, TreePlan.Context c) {
        if (ProgressionRules.isBranchLocked(real, b.id())) return Status.LOCKED;
        if (ProgressionRules.isBranchLocked(s, b.id())) return Status.WILL_LOCK;
        if (real.pointsIn(b.id()) > 0) return Status.OWNED;
        if (s.pointsIn(b.id()) > 0) return Status.PLANNED;
        return nextStatus(TreePlan.check(s, c, new Step(Type.BRANCH, b.id(), null)));
    }

    private static Status point(BranchDef b, int level, ProgressState real, ProgressState s, TreePlan.Context c) {
        if (level <= real.pointsIn(b.id())) return Status.OWNED;
        if (level <= s.pointsIn(b.id())) return Status.PLANNED;
        if (ProgressionRules.isBranchLocked(real, b.id())) return Status.LOCKED;
        if (ProgressionRules.isBranchLocked(s, b.id())) return Status.WILL_LOCK;
        if (level == s.pointsIn(b.id()) + 1) return nextStatus(TreePlan.check(s, c, new Step(Type.BRANCH, b.id(), null)));
        return Status.FUTURE;
    }

    private static Status perk(BranchDef b, int index, ProgressState real, ProgressState s) {
        if (index >= b.perks().size()) return Status.FUTURE;
        PerkDef p = b.perks().get(index);
        String realPerk = real.perkIn(b.id()), simPerk = s.perkIn(b.id());
        if (realPerk != null) return realPerk.equals(p.id()) ? Status.OWNED : Status.DISCARDED;
        if (simPerk != null) return simPerk.equals(p.id()) ? Status.PLANNED : Status.DISCARDED;
        if (ProgressionRules.isBranchLocked(real, b.id())) return Status.LOCKED;
        if (ProgressionRules.isBranchLocked(s, b.id())) return Status.WILL_LOCK;
        return ProgressionRules.perkPending(s, b) ? Status.AVAILABLE : Status.FUTURE;
    }

    private static Status subKey(String subId, ProgressState real, ProgressState s, TreePlan.Context c) {
        if (subId.equals(real.subclassId)) return Status.OWNED;
        if (real.subclassId != null) return Status.LOCKED;
        if (subId.equals(s.subclassId)) return Status.PLANNED;
        if (s.subclassId != null) return Status.WILL_LOCK;
        Result r = TreePlan.check(s, c, new Step(Type.CHOOSE_SUBCLASS, null, subId), 0);
        return r.ok() ? Status.AVAILABLE : Status.FUTURE;
    }

    private static Status subPoint(String subId, int level, ProgressState real, ProgressState s, TreePlan.Context c) {
        if (subId.equals(real.subclassId) && level <= real.subclassLevel) return Status.OWNED;
        if (real.subclassId != null && !subId.equals(real.subclassId)) return Status.LOCKED;
        if (s.subclassId != null && !subId.equals(s.subclassId)) return Status.WILL_LOCK;
        if (subId.equals(s.subclassId) && level <= s.subclassLevel) return Status.PLANNED;
        if (level > ProgressionRules.subclassCap(s.basePoints())) return Status.CAPPED;
        if (subId.equals(s.subclassId) && level == s.subclassLevel + 1) {
            return nextStatus(TreePlan.check(s, c, new Step(Type.SUBCLASS, null, null)));
        }
        return Status.FUTURE;
    }

    private static Status nextStatus(Result r) {
        if (r.ok()) return Status.AVAILABLE;
        if (r == Result.NOT_ENOUGH_XP || r == Result.NO_POINTS) return Status.POOR;
        if (r == Result.BRANCH_LOCKED || r == Result.SUBCLASS_LOCKED) return Status.LOCKED;
        if (r == Result.SUBCLASS_CAP) return Status.CAPPED;
        return Status.FUTURE;
    }

    /** Puntos de base necesarios para poder tener {@code level} niveles de subclase. */
    public static int baseNeededForSubclassLevel(int level) {
        return Math.max(ProgressionRules.SUBCLASS_UNLOCK, 2 * level + 10);
    }
}
