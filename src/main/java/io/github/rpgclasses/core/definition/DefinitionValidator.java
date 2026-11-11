package io.github.rpgclasses.core.definition;

import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.definition.Definitions.BranchDef;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.EffectDef;
import io.github.rpgclasses.core.definition.Definitions.MilestoneDef;
import io.github.rpgclasses.core.definition.Definitions.SubclassDef;
import io.github.rpgclasses.core.progression.ProgressionRules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Comprueba las reglas estructurales del diseño. Una clase con errores se desactiva; nunca tumba el servidor. */
public final class DefinitionValidator {
    private static final Set<String> KIT_SLOTS = Set.of("mainhand", "offhand", "head", "chest", "legs", "feet", "inventory");

    /** @return lista de errores de la clase y de sus subclases (vacía = válida). */
    public static List<String> validateClass(ClassDef c, Map<String, SubclassDef> subclasses) {
        return validateClass(c, subclasses, null);
    }

    /** @param abilityIds habilidades existentes; null = no comprobar que los hitos apunten a habilidades reales. */
    public static List<String> validateClass(ClassDef c, Map<String, SubclassDef> subclasses, Set<String> abilityIds) {
        List<String> errors = new ArrayList<>();

        if (c.branches().size() != ProgressionRules.BRANCHES_PER_CLASS) {
            errors.add("debe tener " + ProgressionRules.BRANCHES_PER_CLASS + " ramas y tiene " + c.branches().size());
        }
        for (EffectDef e : c.traits()) {
            if (!Ids.EFFECT_ATTRIBUTE.equals(e.type())) errors.add("rasgo de clase: solo se admiten efectos " + Ids.EFFECT_ATTRIBUTE);
            else validateEffect(e, 1, "rasgo de clase: ", errors);
        }
        Set<String> branchIds = new HashSet<>();
        for (BranchDef b : c.branches()) {
            String where = "rama '" + b.id() + "': ";
            if (!branchIds.add(b.id())) errors.add(where + "id repetido");
            if (b.maxPoints() != ProgressionRules.BRANCH_MAX) {
                errors.add(where + "max_points debe ser " + ProgressionRules.BRANCH_MAX);
            }
            if (b.effects().isEmpty()) errors.add(where + "no tiene efectos");
            for (EffectDef e : b.effects()) validateEffect(e, Math.max(1, b.maxPoints()), where, errors);
            if (b.perks().size() != ProgressionRules.PERKS_PER_BRANCH) {
                errors.add(where + "debe tener " + ProgressionRules.PERKS_PER_BRANCH + " perks y tiene " + b.perks().size());
            }
            Set<String> perkIds = new HashSet<>();
            for (Definitions.PerkDef p : b.perks()) {
                String pw = where + "perk '" + p.id() + "': ";
                if (!perkIds.add(p.id())) errors.add(pw + "id repetido");
                if (p.id().isEmpty() || p.id().contains("/")) errors.add(pw + "id vacío o con '/'");
                if (p.effects().isEmpty()) errors.add(pw + "no tiene efectos");
                for (EffectDef e : p.effects()) validateEffect(e, 1, pw, errors);
            }
        }

        for (Map.Entry<String, Double> w : c.walls().entrySet()) {
            if (!Ids.DISCIPLINES.contains(w.getKey())) errors.add("muro sobre disciplina desconocida: " + w.getKey());
            double v = w.getValue();
            if (!(v > 0 && v <= 1)) errors.add("muro de " + w.getKey() + " fuera de (0, 1]: " + v);
        }

        for (Definitions.KitItem k : c.startingKit()) {
            if (!KIT_SLOTS.contains(k.slot())) errors.add("equipo inicial: ranura desconocida '" + k.slot() + "'");
            if (k.item().indexOf(':') <= 0 || k.count() < 1) errors.add("equipo inicial: objeto o cantidad inválidos: " + k.item());
        }

        if (!(c.critChanceBonus() >= -0.3 && c.critChanceBonus() <= 0.7)) {
            errors.add("crit_chance_bonus fuera de [-0.3, 0.7]");
        }

        Set<String> granted = new HashSet<>();
        for (MilestoneDef m : c.milestones()) {
            if (m.subclassLevel()) errors.add("una clase base no puede tener hitos de nivel de subclase");
            validateMilestone(m, "", errors);
            if (abilityIds != null && !abilityIds.contains(m.grants())) errors.add("hito que otorga una habilidad inexistente: " + m.grants());
            granted.add(m.grants());
        }

        if (c.subclasses().size() != 3) errors.add("debe tener 3 subclases y tiene " + c.subclasses().size());
        if (new HashSet<>(c.subclasses()).size() != c.subclasses().size()) errors.add("subclases repetidas");
        for (String subId : c.subclasses()) {
            SubclassDef s = subclasses.get(subId);
            String where = "subclase '" + subId + "': ";
            if (s == null) {
                errors.add(where + "no existe o no se pudo leer");
                continue;
            }
            if (!c.id().equals(s.parentClass())) errors.add(where + "su campo \"class\" es " + s.parentClass());
            if (s.effects().isEmpty() && s.milestones().isEmpty()) errors.add(where + "no tiene efectos ni hitos");
            for (EffectDef e : s.effects()) validateEffect(e, ProgressionRules.SUBCLASS_MAX, where, errors);
            for (MilestoneDef m : s.milestones()) {
                validateMilestone(m, where, errors);
                if (abilityIds != null && !abilityIds.contains(m.grants())) errors.add(where + "hito que otorga una habilidad inexistente: " + m.grants());
                if (m.upgrades() != null && !granted.contains(m.upgrades())) {
                    errors.add(where + "mejora '" + m.upgrades() + "', que ningún hito de la clase otorga");
                }
            }
        }
        return errors;
    }

    private static void validateEffect(EffectDef e, int maxLevel, String where, List<String> errors) {
        if (!Ids.EFFECT_TYPES.contains(e.type())) {
            errors.add(where + "tipo de efecto desconocido: " + e.type());
            return;
        }
        try {
            if (Ids.EFFECT_ATTRIBUTE.equals(e.type())) {
                AttributeEffect.parse(e.params(), maxLevel);
            } else if (Ids.EFFECT_MECHANIC.equals(e.type())) {
                if (!e.params().has("id") || e.params().get("id").getAsString().indexOf(':') <= 0) {
                    errors.add(where + "mecánica sin \"id\" con namespace");
                }
            }
        } catch (RuntimeException ex) {
            errors.add(where + "efecto " + e.type() + ": " + ex.getMessage());
        }
    }

    private static void validateMilestone(MilestoneDef m, String where, List<String> errors) {
        int max = m.subclassLevel() ? ProgressionRules.SUBCLASS_MAX
                : ProgressionRules.BASE_MAX + ProgressionRules.SUBCLASS_MAX;
        if (m.level() < 1 || m.level() > max) errors.add(where + "hito con nivel fuera de 1.." + max + ": " + m.level());
        if (m.grants().indexOf(':') <= 0) errors.add(where + "hito con \"grants\" sin namespace: " + m.grants());
    }

    private DefinitionValidator() {}
}
