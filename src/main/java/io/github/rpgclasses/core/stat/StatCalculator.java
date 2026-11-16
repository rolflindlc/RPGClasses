package io.github.rpgclasses.core.stat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.definition.AttributeEffect;
import io.github.rpgclasses.core.definition.Definitions.BranchDef;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.EffectDef;
import io.github.rpgclasses.core.definition.Definitions.PerkDef;
import io.github.rpgclasses.core.definition.Definitions.RaceDef;
import io.github.rpgclasses.core.definition.Definitions.SubraceDef;
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
    /**
     * @param traitFactors multiplicadores sobre los rasgos base de un atributo (coste fijo de las posturas del Monje).
     * @param extra        modificadores añadidos antes de los topes (bonus de las cargas de postura del Monje).
     */
    public record Context(boolean wearingArmor, Map<String, Double> factors, Map<String, Double> traitFactors, Map<Key, Double> extra) {
        public Context(boolean wearingArmor) {
            this(wearingArmor, Map.of());
        }

        public Context(boolean wearingArmor, Map<String, Double> factors) {
            this(wearingArmor, factors, Map.of(), Map.of());
        }
    }

    /** Rasgos con {@code "scale": "base_points"} crecen de 0 al valor completo con los puntos de clase base (tope 30). */
    public static final String SCALE_BASE_POINTS = "base_points";

    /**
     * Suma de todos los modificadores de atributo de la clase, la subclase, los rasgos base y los perks,
     * con los topes globales aplicados. Los factores del contexto (posturas) solo escalan los bonus que
     * crecen con puntos (ramas y subclase), no los rasgos ni los perks.
     */
    public static Map<Key, Double> attributeTotals(ProgressState s, ClassDef c, SubclassDef sub, Context ctx) {
        return attributeTotals(s, c, sub, null, ctx);
    }

    /** Igual, sumando los rasgos de raza y subraza (que cuentan para los topes y tienen suelos). */
    public static Map<Key, Double> attributeTotals(ProgressState s, ClassDef c, SubclassDef sub, RaceDef race, Context ctx) {
        Map<Key, Double> totals = new LinkedHashMap<>();
        if (c != null && c.id().equals(s.classId)) addClassTotals(totals, s, c, sub, adaptable(s, race), ctx);
        for (EffectDef e : raceEffects(s, race)) addAttributeEffect(totals, e, s.raceRank + 1, RACE_RANKS, ctx);
        ctx.extra().forEach((k, v) -> totals.merge(k, v, Double::sum));
        // Topes globales (regla 7): solo cuentan los bonus del mod.
        totals.computeIfPresent(new Key(MOVEMENT_SPEED, "multiply_base"), (k, v) -> StatMath.capSpeedBonus(v));
        totals.computeIfPresent(new Key(JUMP_BONUS, "add"), (k, v) -> StatMath.capJumpBonus(v));
        totals.computeIfPresent(new Key(MAX_HEALTH, "multiply_base"), (k, v) -> StatMath.capHealthPctBonus(v));
        return totals;
    }

    private static void addClassTotals(Map<Key, Double> totals, ProgressState s, ClassDef c, SubclassDef sub, double adaptable, Context ctx) {
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
        // Humano (Adaptable): las penalizaciones de los rasgos base se reducen en el porcentaje del rango.
        Map<Key, Double> traits = new LinkedHashMap<>();
        int base = Math.min(ProgressionRules.BASE_MAX, s.basePoints());
        for (EffectDef e : c.traits()) {
            boolean scaled = e.params().has("scale") && SCALE_BASE_POINTS.equals(e.params().get("scale").getAsString());
            if (scaled) {
                if (base > 0) addAttributeEffect(traits, e, base, ProgressionRules.BASE_MAX, ctx);
            } else {
                addAttributeEffect(traits, e, 1, 1, ctx);
            }
        }
        if (!ctx.traitFactors().isEmpty()) {
            traits.replaceAll((key, value) -> value * ctx.traitFactors().getOrDefault(key.attribute(), 1.0));
        }
        traits.forEach((k, v) -> totals.merge(k, v < 0 ? v * (1 - Math.max(0, Math.min(1, adaptable))) : v, Double::sum));
        for (PerkDef p : chosenPerks(s, c)) {
            for (EffectDef e : p.effects()) addAttributeEffect(totals, e, 1, 1, ctx);
        }
    }

    // --- Razas ---

    public static final int RACE_RANKS = 4;
    public static final String RACE_ADAPTABLE = Ids.NS + ":race_adaptable";
    public static final String RACE_SECOND_WALL = Ids.NS + ":race_second_wall";
    public static final String RACE_WALL_MASTERY = Ids.NS + ":race_wall_mastery";
    public static final String RACE_FLEXIBLE_RESPEC = Ids.NS + ":race_flexible_respec";

    /** Efectos de la raza del jugador y de su subraza (vacío si la raza no es la suya o no existe). */
    public static List<EffectDef> raceEffects(ProgressState s, RaceDef race) {
        List<EffectDef> out = new ArrayList<>();
        if (race == null || !race.id().equals(s.raceId)) return out;
        out.addAll(race.effects());
        SubraceDef sub = race.subrace(s.subraceId);
        if (sub != null) out.addAll(sub.effects());
        return out;
    }

    /** Mecánicas raciales activas en el rango actual (nivel = rango + 1 de 4). */
    public static List<ActiveMechanic> raceMechanics(ProgressState s, RaceDef race) {
        List<ActiveMechanic> out = new ArrayList<>();
        for (EffectDef e : raceEffects(s, race)) {
            if (!Ids.EFFECT_MECHANIC.equals(e.type())) continue;
            JsonObject p = e.params();
            int minRank = p.has("min_rank") ? p.get("min_rank").getAsInt() : 0;
            if (s.raceRank >= minRank) out.add(new ActiveMechanic(p.get("id").getAsString(), p, s.raceRank + 1, RACE_RANKS));
        }
        return out;
    }

    public static ActiveMechanic raceMechanic(ProgressState s, RaceDef race, String id) {
        for (ActiveMechanic m : raceMechanics(s, race)) if (m.id().equals(id)) return m;
        return null;
    }

    /** Humano: fracción en que se reducen el muro elegido y los rasgos base (0 si no es humano). */
    public static double adaptable(ProgressState s, RaceDef race) {
        ActiveMechanic m = raceMechanic(s, race, RACE_ADAPTABLE);
        return m == null ? 0 : m.number("wall_reduction", 0);
    }

    /** Humano: muros que puede elegir (0 si no es humano, 2 el Polivalente). */
    public static int humanWallSlots(ProgressState s, RaceDef race) {
        if (raceMechanic(s, race, RACE_ADAPTABLE) == null) return 0;
        return raceMechanic(s, race, RACE_SECOND_WALL) != null ? 2 : 1;
    }

    /** Humano: reducción (relativa al muro original) que Adaptable da en esa disciplina. */
    public static double humanWallReduction(ProgressState s, RaceDef race, String discipline) {
        double a = adaptable(s, race);
        int index = s.humanWalls.indexOf(discipline);
        if (a <= 0 || index < 0) return 0;
        if (index == 0) {
            ActiveMechanic mastery = raceMechanic(s, race, RACE_WALL_MASTERY);
            return a * (mastery == null ? 1 : mastery.number("factor", 1.5));
        }
        ActiveMechanic second = raceMechanic(s, race, RACE_SECOND_WALL);
        return second == null ? 0 : a * second.number("factor", 0.5);
    }

    /** Humano Autodidacta: factor sobre la XP que se pierde al reiniciar (1 = sin cambio). */
    public static double respecLossFactor(ProgressState s, RaceDef race) {
        return raceMechanic(s, race, RACE_FLEXIBLE_RESPEC) == null ? 1 : 1 - adaptable(s, race);
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

        /** Valor de texto fijo (p. ej. el elemento del Dracónido). */
        public String text(String key, String fallback) {
            JsonElement v = params.get(key);
            return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString() ? v.getAsString() : fallback;
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
        return wall(s, c, sub, null, discipline);
    }

    /** Igual, con la reducción de Adaptable del Humano sumada a las mitigaciones (tope 100 %). */
    public static double wall(ProgressState s, ClassDef c, SubclassDef sub, RaceDef race, String discipline) {
        if (c == null || !c.id().equals(s.classId)) return 0;
        double penalty = StatMath.wallPenalty(c.walls().getOrDefault(discipline, 0.0), s.basePoints());
        if (penalty <= 0) return 0;
        double mitigation = humanWallReduction(s, race, discipline);
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
