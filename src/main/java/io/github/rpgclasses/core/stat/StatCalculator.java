package io.github.rpgclasses.core.stat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.definition.AttributeEffect;
import io.github.rpgclasses.core.definition.Definitions.BranchDef;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.EffectDef;
import io.github.rpgclasses.core.definition.Definitions.PerkDef;
import io.github.rpgclasses.core.definition.Definitions.SubclassDef;
import io.github.rpgclasses.core.progression.ProgressState;
import io.github.rpgclasses.core.progression.ProgressionRules;
import io.github.rpgclasses.core.value.LevelValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcula, a partir del progreso y las definiciones, todo lo que el juego debe aplicar:
 * totales por atributo (con topes globales), muros y bonus especiales. Lógica pura.
 * StatEngine (Forge) solo traduce estos resultados a modificadores de atributo.
 */
public final class StatCalculator {
    public static final String MOVEMENT_SPEED = "minecraft:generic.movement_speed";
    public static final String JUMP_BONUS = Ids.NS + ":jump_bonus";
    public static final String MAX_HEALTH = "minecraft:generic.max_health";
    public static final String MECHANIC_WALL_MITIGATION = Ids.NS + ":wall_mitigation";

    /** Un modificador final: atributo + operación (add, multiply_base, multiply_total). */
    public record Key(String attribute, String operation) {}

    /** Estado del jugador que activa o desactiva condiciones. */
    /**
     * @param factors multiplicadores sobre el bonus total de un atributo (id -> factor), p. ej. las posturas del Monje.
     *                Se aplican antes de los topes globales.
     */
    public record Context(boolean wearingArmor, Map<String, Double> factors) {
        public Context(boolean wearingArmor) {
            this(wearingArmor, Map.of());
        }
    }

    /**
     * Suma de todos los modificadores de atributo de la clase, la subclase, los rasgos base y los perks,
     * con los topes globales aplicados. Los factores del contexto (posturas) solo escalan los bonus que
     * crecen con puntos (ramas y subclase), no los rasgos ni los perks.
     */
    public static Map<Key, Double> attributeTotals(ProgressState s, ClassDef c, SubclassDef sub, Context ctx) {
        Map<Key, Double> totals = new LinkedHashMap<>();
        if (c == null || !c.id().equals(s.classId)) return totals;
        for (BranchDef b : c.branches()) {
            int points = s.pointsIn(b.id());
            if (points <= 0) continue;
            for (EffectDef e : b.effects()) addAttributeEffect(totals, e, points, b.maxPoints(), ctx);
        }
        if (activeSubclass(s, sub) && s.subclassLevel > 0) {
            for (EffectDef e : sub.effects()) {
                addAttributeEffect(totals, e, s.subclassLevel, ProgressionRules.SUBCLASS_MAX, ctx);
            }
        }
        if (!ctx.factors().isEmpty()) {
            totals.replaceAll((key, value) -> value * ctx.factors().getOrDefault(key.attribute(), 1.0));
        }
        // Rasgos base (regla 12) y perks (regla 11): enteros desde el primer momento.
        for (EffectDef e : c.traits()) addAttributeEffect(totals, e, 1, 1, ctx);
        for (PerkDef p : chosenPerks(s, c)) {
            for (EffectDef e : p.effects()) addAttributeEffect(totals, e, 1, 1, ctx);
        }
        // Topes globales (regla 7): solo cuentan los bonus del mod.
        totals.computeIfPresent(new Key(MOVEMENT_SPEED, "multiply_base"), (k, v) -> StatMath.capSpeedBonus(v));
        totals.computeIfPresent(new Key(JUMP_BONUS, "add"), (k, v) -> StatMath.capJumpBonus(v));
        totals.computeIfPresent(new Key(MAX_HEALTH, "multiply_base"), (k, v) -> StatMath.capHealthPctBonus(v));
        return totals;
    }

    /** Perks elegidos que siguen existiendo en la definición (una rama llena con su perk). */
    public static List<PerkDef> chosenPerks(ProgressState s, ClassDef c) {
        List<PerkDef> out = new ArrayList<>();
        if (c == null || !c.id().equals(s.classId)) return out;
        for (BranchDef b : c.branches()) {
            String id = s.perkIn(b.id());
            if (id == null || s.pointsIn(b.id()) < b.maxPoints()) continue;
            PerkDef p = b.perk(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    /** Una mecánica activa del jugador (de rama, subclase o perk) con el nivel al que se evalúan sus valores. */
    public record ActiveMechanic(String id, JsonObject params, int level, int maxLevel) {
        /** Valor numérico: fijo, o por nivel ({"to": x}, {"from","to"} o {"values"}). */
        public double number(String key, double fallback) {
            if (!params.has(key)) return fallback;
            JsonElement v = params.get(key);
            if (v.isJsonPrimitive()) return v.getAsDouble();
            return LevelValue.fromJson(v.getAsJsonObject(), maxLevel).at(level);
        }
    }

    /** Todas las mecánicas activas (tipo {@code rpgclasses:mechanic}), para que el código de content/ las lea. */
    public static List<ActiveMechanic> mechanics(ProgressState s, ClassDef c, SubclassDef sub) {
        List<ActiveMechanic> out = new ArrayList<>();
        if (c == null || !c.id().equals(s.classId)) return out;
        for (BranchDef b : c.branches()) {
            int points = s.pointsIn(b.id());
            if (points > 0) collectMechanics(out, b.effects(), points, b.maxPoints());
        }
        for (PerkDef p : chosenPerks(s, c)) collectMechanics(out, p.effects(), 1, 1);
        if (activeSubclass(s, sub) && s.subclassLevel > 0) collectMechanics(out, sub.effects(), s.subclassLevel, ProgressionRules.SUBCLASS_MAX);
        return out;
    }

    private static void collectMechanics(List<ActiveMechanic> out, List<EffectDef> effects, int level, int maxLevel) {
        for (EffectDef e : effects) {
            if (Ids.EFFECT_MECHANIC.equals(e.type())) out.add(new ActiveMechanic(e.params().get("id").getAsString(), e.params(), level, maxLevel));
        }
    }

    private static void addAttributeEffect(Map<Key, Double> totals, EffectDef e, int level, int maxLevel, Context ctx) {
        if (!Ids.EFFECT_ATTRIBUTE.equals(e.type())) return;
        AttributeEffect effect = AttributeEffect.parse(e.params(), maxLevel);
        if ((Ids.NS + ":no_armor").equals(effect.condition()) && ctx.wearingArmor()) return;
        for (AttributeEffect.Modifier m : effect.modifiers()) {
            totals.merge(new Key(m.attribute(), m.operation()), m.value().at(level), Double::sum);
        }
    }

    /** Muro efectivo (0..1) en una disciplina: escala con los puntos de base y lo reducen las pasivas de mitigación. */
    public static double wall(ProgressState s, ClassDef c, SubclassDef sub, String discipline) {
        if (c == null || !c.id().equals(s.classId)) return 0;
        double penalty = StatMath.wallPenalty(c.walls().getOrDefault(discipline, 0.0), s.basePoints());
        if (penalty <= 0) return 0;
        double mitigation = 0;
        if (activeSubclass(s, sub) && s.subclassLevel > 0) {
            for (EffectDef e : sub.effects()) {
                JsonObject p = e.params();
                if (!Ids.EFFECT_MECHANIC.equals(e.type()) || !MECHANIC_WALL_MITIGATION.equals(p.get("id").getAsString())) continue;
                boolean applies = false;
                for (JsonElement d : p.getAsJsonArray("disciplines")) applies |= discipline.equals(d.getAsString());
                if (applies) {
                    mitigation += LevelValue.fromJson(p.getAsJsonObject("mitigation"), ProgressionRules.SUBCLASS_MAX).at(s.subclassLevel);
                }
            }
        }
        return StatMath.mitigatedWall(penalty, mitigation);
    }

    /** Furia de Heridas: bonus de daño según la vida perdida. @param healthFraction vida actual / vida máxima. */
    public static double woundFuryBonus(ProgressState s, ClassDef c, double healthFraction) {
        if (c == null || !c.id().equals(s.classId)) return 0;
        double missingPct = Math.max(0, Math.min(1, 1 - healthFraction)) * 100;
        double bonus = 0;
        for (BranchDef b : c.branches()) {
            int points = s.pointsIn(b.id());
            if (points <= 0) continue;
            for (EffectDef e : b.effects()) {
                if (!Ids.EFFECT_WOUND_FURY.equals(e.type())) continue;
                JsonObject p = e.params();
                double perPct = LevelValue.fromJson(p.getAsJsonObject("per_missing_health_pct"), b.maxPoints()).at(points);
                double cap = p.has("cap") ? p.get("cap").getAsDouble() : Double.MAX_VALUE;
                bonus += Math.min(cap, perPct * missingPct);
            }
        }
        return bonus;
    }

    /** Coraza: fracción extra de la armadura [0] y la dureza [1] que aportan las piezas equipadas. */
    public static double[] equippedArmorBonus(ProgressState s, ClassDef c) {
        double[] out = new double[2];
        if (c == null || !c.id().equals(s.classId)) return out;
        for (BranchDef b : c.branches()) {
            int points = s.pointsIn(b.id());
            if (points <= 0) continue;
            for (EffectDef e : b.effects()) {
                if (!Ids.EFFECT_EQUIPPED_ARMOR.equals(e.type())) continue;
                JsonObject p = e.params();
                if (p.has("armor")) out[0] += LevelValue.fromJson(p.getAsJsonObject("armor"), b.maxPoints()).at(points);
                if (p.has("toughness")) out[1] += LevelValue.fromJson(p.getAsJsonObject("toughness"), b.maxPoints()).at(points);
            }
        }
        for (PerkDef perk : chosenPerks(s, c)) {
            for (EffectDef e : perk.effects()) {
                if (!Ids.EFFECT_EQUIPPED_ARMOR.equals(e.type())) continue;
                JsonObject p = e.params();
                if (p.has("armor")) out[0] += LevelValue.fromJson(p.getAsJsonObject("armor"), 1).at(1);
                if (p.has("toughness")) out[1] += LevelValue.fromJson(p.getAsJsonObject("toughness"), 1).at(1);
            }
        }
        return out;
    }

    private static boolean activeSubclass(ProgressState s, SubclassDef sub) {
        return sub != null && sub.id().equals(s.subclassId);
    }

    private StatCalculator() {}
}
