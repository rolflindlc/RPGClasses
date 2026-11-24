package io.github.rpgclasses.client.tree;

import io.github.rpgclasses.core.definition.Definitions.BranchDef;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.SubclassDef;
import io.github.rpgclasses.core.progression.ProgressState;
import io.github.rpgclasses.core.progression.ProgressionRules;
import io.github.rpgclasses.core.progression.ProgressionRules.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Plan de compra del árbol: los clics no gastan nada hasta confirmar. Cada paso se valida reproduciendo el
 * plan desde el estado real con las mismas reglas que usa el servidor ({@link ProgressionRules}), así que lo
 * que muestra el árbol es exactamente lo que pasará al confirmar (salvo restricciones que solo conoce el
 * servidor, como el Elfo con Carnívoro). Lógica pura.
 */
public final class TreePlan {
    public enum Type { BRANCH, PERK, CHOOSE_SUBCLASS, SUBCLASS }

    /** BRANCH: {@code branch}. PERK: {@code branch} y {@code value} (perk). CHOOSE_SUBCLASS: {@code value}. SUBCLASS: nada. */
    public record Step(Type type, String branch, String value) {}

    /** Lo que hace falta para simular: estado real (con su saldo de puntos) y definiciones. */
    public record Context(ProgressState real, ClassDef def, Function<String, SubclassDef> subclasses) {}

    /** Resultado de reproducir el plan. */
    public static final class Simulation {
        public final ProgressState state;
        public final List<Step> valid;
        /** Puntos que costará el plan entero. */
        public final long cost;
        /** Puntos de saldo que quedan tras el plan. */
        public final long pointsLeft;
        public final int points;

        Simulation(ProgressState state, List<Step> valid, long cost, long pointsLeft, int points) {
            this.state = state;
            this.valid = valid;
            this.cost = cost;
            this.pointsLeft = pointsLeft;
            this.points = points;
        }
    }

    private final List<Step> steps = new ArrayList<>();

    public List<Step> steps() {
        return List.copyOf(steps);
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    public void clear() {
        steps.clear();
    }

    /** Reproduce el plan; los pasos que ya no son válidos (otro estado real, menos puntos) se saltan. */
    public Simulation simulate(Context c) {
        ProgressState s = new ProgressState();
        s.copyFrom(c.real());
        List<Step> ok = new ArrayList<>();
        long spent = 0;
        int points = 0;
        for (Step st : steps) {
            long cost = tryApply(s, c, st);
            if (cost < 0) continue;
            spent += cost;
            if (st.type() == Type.BRANCH || st.type() == Type.SUBCLASS) points++;
            ok.add(st);
        }
        return new Simulation(s, List.copyOf(ok), spent, s.pointBalance, points);
    }

    /** Quita los pasos que han dejado de ser válidos. */
    public void prune(Context c) {
        List<Step> valid = simulate(c).valid;
        steps.clear();
        steps.addAll(valid);
    }

    /** Rama: deja el plan con {@code target} puntos en ella (nunca por debajo de los ya comprados). */
    public void setBranchTarget(Context c, String branchId, int target) {
        BranchDef b = c.def().branch(branchId);
        if (b == null) return;
        target = Math.max(c.real().pointsIn(branchId), Math.min(target, b.maxPoints()));
        int current = simulate(c).state.pointsIn(branchId);
        if (target > current) {
            for (int i = current; i < target; i++) {
                if (!tryAdd(c, new Step(Type.BRANCH, branchId, null))) break;
            }
        } else {
            for (int i = steps.size() - 1; i >= 0 && current > target; i--) {
                Step st = steps.get(i);
                if (st.type() == Type.BRANCH && branchId.equals(st.branch())) {
                    steps.remove(i);
                    current--;
                }
            }
            prune(c);
        }
    }

    /** Subclase: deja el plan con {@code target} niveles. */
    public void setSubclassTarget(Context c, int target) {
        target = Math.max(c.real().subclassLevel, Math.min(target, ProgressionRules.SUBCLASS_MAX));
        int current = simulate(c).state.subclassLevel;
        if (target > current) {
            for (int i = current; i < target; i++) {
                if (!tryAdd(c, new Step(Type.SUBCLASS, null, null))) break;
            }
        } else {
            for (int i = steps.size() - 1; i >= 0 && current > target; i--) {
                if (steps.get(i).type() == Type.SUBCLASS) {
                    steps.remove(i);
                    current--;
                }
            }
            prune(c);
        }
    }

    /** Elige (o cambia por otro) el perk de una rama. */
    public boolean choosePerk(Context c, String branchId, String perkId) {
        int at = indexOf(Type.PERK, branchId);
        Step step = new Step(Type.PERK, branchId, perkId);
        if (at >= 0) {
            Step old = steps.set(at, step);
            if (simulate(c).valid.contains(step)) return true;
            steps.set(at, old);
            return false;
        }
        return tryAdd(c, step);
    }

    public void removePerk(Context c, String branchId) {
        int at = indexOf(Type.PERK, branchId);
        if (at >= 0) steps.remove(at);
        prune(c);
    }

    /** Elige (o cambia por otra) la subclase; los niveles ya planeados se conservan. */
    public boolean chooseSubclass(Context c, String subclassId) {
        int at = indexOf(Type.CHOOSE_SUBCLASS, null);
        Step step = new Step(Type.CHOOSE_SUBCLASS, null, subclassId);
        if (at >= 0) {
            Step old = steps.set(at, step);
            if (simulate(c).valid.contains(step)) return true;
            steps.set(at, old);
            return false;
        }
        return tryAdd(c, step);
    }

    public void removeSubclassChoice(Context c) {
        int at = indexOf(Type.CHOOSE_SUBCLASS, null);
        if (at >= 0) steps.remove(at);
        prune(c);
    }

    /** Añade un paso si, al final del plan, es válido. */
    private boolean tryAdd(Context c, Step step) {
        steps.add(step);
        Simulation sim = simulate(c);
        if (sim.valid.size() == steps.size()) return true;
        steps.remove(steps.size() - 1);
        return false;
    }

    private int indexOf(Type type, String branchId) {
        for (int i = 0; i < steps.size(); i++) {
            Step st = steps.get(i);
            if (st.type() == type && (branchId == null || branchId.equals(st.branch()))) return i;
        }
        return -1;
    }

    /** Lo que diría el servidor de un paso sobre el estado {@code s}, sin aplicarlo. */
    public static Result check(ProgressState s, Context c, Step st) {
        return switch (st.type()) {
            case BRANCH -> ProgressionRules.checkSpendBranch(s, c.def(), st.branch());
            case PERK -> ProgressionRules.checkChoosePerk(s, c.def(), st.branch(), st.value());
            case CHOOSE_SUBCLASS -> ProgressionRules.checkChooseSubclass(s, c.def(), c.subclasses().apply(st.value()));
            case SUBCLASS -> s.subclassId != null && c.subclasses().apply(s.subclassId) == null
                    ? Result.UNKNOWN_SUBCLASS : ProgressionRules.checkSpendSubclass(s);
        };
    }

    /** Aplica el paso si es válido. @return puntos que cuesta, o -1 si no es válido. */
    static long tryApply(ProgressState s, Context c, Step st) {
        if (!check(s, c, st).ok()) return -1;
        return switch (st.type()) {
            case BRANCH -> ProgressionRules.spendBranch(s, st.branch());
            case PERK -> {
                ProgressionRules.choosePerk(s, st.branch(), st.value());
                yield 0;
            }
            case CHOOSE_SUBCLASS -> {
                ProgressionRules.chooseSubclass(s, c.subclasses().apply(st.value()));
                yield 0;
            }
            case SUBCLASS -> ProgressionRules.spendSubclass(s);
        };
    }
}
