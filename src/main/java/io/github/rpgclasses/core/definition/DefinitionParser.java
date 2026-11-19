package io.github.rpgclasses.core.definition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.rpgclasses.core.definition.Definitions.BranchDef;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.EffectDef;
import io.github.rpgclasses.core.definition.Definitions.MilestoneDef;
import io.github.rpgclasses.core.definition.Definitions.SubclassDef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Convierte JSON en definiciones. Cualquier fallo se lanza como IllegalArgumentException con un mensaje legible. */
public final class DefinitionParser {
    public static final int SCHEMA_VERSION = 1;

    public static ClassDef parseClass(String id, String json) {
        JsonObject o = root(json);
        JsonObject display = o.has("display") ? o.getAsJsonObject("display") : new JsonObject();
        Map<String, Double> walls = new LinkedHashMap<>();
        if (o.has("walls")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("walls").entrySet()) {
                walls.put(e.getKey(), e.getValue().getAsDouble());
            }
        }
        List<BranchDef> branches = new ArrayList<>();
        for (JsonElement be : array(o, "branches")) {
            JsonObject b = be.getAsJsonObject();
            List<Definitions.PerkDef> perks = new ArrayList<>();
            if (b.has("perks")) {
                for (JsonElement pe : array(b, "perks")) {
                    JsonObject p = pe.getAsJsonObject();
                    perks.add(new Definitions.PerkDef(string(p, "id"), effects(p)));
                }
            }
            JsonObject branchDisplay = b.has("display") ? b.getAsJsonObject("display") : new JsonObject();
            branches.add(new BranchDef(
                    string(b, "id"),
                    b.has("max_points") ? b.get("max_points").getAsInt() : 10,
                    effects(b),
                    List.copyOf(perks),
                    optString(branchDisplay, "icon", null)));
        }
        List<String> subclasses = new ArrayList<>();
        for (JsonElement se : array(o, "subclasses")) subclasses.add(se.getAsString());

        return new ClassDef(
                id,
                optString(display, "icon", "minecraft:barrier"),
                strings(display, "roles"),
                kit(o),
                o.has("crit_chance_bonus") ? o.get("crit_chance_bonus").getAsDouble() : 0.0,
                Map.copyOf(walls),
                schoolWalls(o),
                o.has("magic_wall_exempt_schools") ? strings(o, "magic_wall_exempt_schools") : List.of(),
                o.has("magic_wall_exempt_spells") ? strings(o, "magic_wall_exempt_spells") : List.of(),
                effects(o, "traits"),
                "disables_unarmored_passives".equals(optString(o, "armor_rule", "")),
                List.copyOf(branches),
                milestones(o),
                List.copyOf(subclasses));
    }

    private static Map<String, Double> schoolWalls(JsonObject o) {
        if (!o.has("school_walls")) return Map.of();
        Map<String, Double> out = new java.util.HashMap<>();
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("school_walls").entrySet()) {
            out.put(e.getKey(), Math.max(0.0, Math.min(1.0, e.getValue().getAsDouble())));
        }
        return Map.copyOf(out);
    }

    public static SubclassDef parseSubclass(String id, String json) {
        JsonObject o = root(json);
        JsonObject display = o.has("display") ? o.getAsJsonObject("display") : new JsonObject();
        return new SubclassDef(
                id,
                string(o, "class"),
                optString(display, "icon", "minecraft:barrier"),
                effects(o),
                milestones(o));
    }

    public static Definitions.RaceDef parseRace(String id, String json) {
        JsonObject o = root(json);
        JsonObject display = o.has("display") ? o.getAsJsonObject("display") : new JsonObject();
        List<Definitions.SubraceDef> subraces = new ArrayList<>();
        for (JsonElement se : array(o, "subraces")) {
            JsonObject s = se.getAsJsonObject();
            JsonObject sd = s.has("display") ? s.getAsJsonObject("display") : new JsonObject();
            subraces.add(new Definitions.SubraceDef(string(s, "id"), optString(sd, "icon", "minecraft:barrier"),
                    !s.has("available") || s.get("available").getAsBoolean(), effects(s), rites(s)));
        }
        List<EffectDef> effects = effects(o);
        // Validación: cada efecto debe poder evaluarse en los 4 rangos.
        List<EffectDef> all = new ArrayList<>(effects);
        for (Definitions.SubraceDef s : subraces) all.addAll(s.effects());
        for (EffectDef e : all) {
            if ("rites_and_paths:attribute".equals(e.type())) AttributeEffect.parse(e.params(), 4);
            else if ("rites_and_paths:mechanic".equals(e.type())) string(e.params(), "id");
            else throw new IllegalArgumentException("tipo de efecto no admitido en razas: " + e.type());
        }
        return new Definitions.RaceDef(id, optString(display, "icon", "minecraft:barrier"), effects, List.copyOf(subraces));
    }

    public static final java.util.Set<String> OBJECTIVE_TYPES = java.util.Set.of(
            "kill", "kill_variety", "kill_styles", "hits_taken", "steals", "barters", "counter", "offer", "structure", "boss");

    private static List<Definitions.RiteDef> rites(JsonObject s) {
        List<Definitions.RiteDef> out = new ArrayList<>();
        if (!s.has("rites")) return out;
        for (JsonElement re : array(s, "rites")) {
            JsonObject r = re.getAsJsonObject();
            List<Definitions.ObjectiveDef> objectives = new ArrayList<>();
            for (JsonElement oe : array(r, "objectives")) {
                JsonObject o = oe.getAsJsonObject();
                String type = string(o, "type");
                if (!OBJECTIVE_TYPES.contains(type)) throw new IllegalArgumentException("tipo de objetivo de rito desconocido: " + type);
                int count = o.has("count") ? o.get("count").getAsInt() : 1;
                if (count < 1) throw new IllegalArgumentException("count debe ser >= 1 en " + string(r, "id"));
                objectives.add(new Definitions.ObjectiveDef(type, count, o));
            }
            if (objectives.isEmpty()) throw new IllegalArgumentException("rito sin objetivos: " + string(r, "id"));
            out.add(new Definitions.RiteDef(string(r, "id"), optString(r, "kind", "feat"), List.copyOf(objectives)));
        }
        return List.copyOf(out);
    }

    public static Definitions.AbilityDef parseAbility(String id, String json) {
        return new Definitions.AbilityDef(id, root(json));
    }

    private static JsonObject root(String json) {
        JsonElement el;
        try {
            el = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("JSON mal formado: " + e.getMessage());
        }
        if (!el.isJsonObject()) throw new IllegalArgumentException("la raíz debe ser un objeto");
        JsonObject o = el.getAsJsonObject();
        int v = o.has("schema_version") ? o.get("schema_version").getAsInt() : SCHEMA_VERSION;
        if (v != SCHEMA_VERSION) throw new IllegalArgumentException("schema_version " + v + " no soportada");
        return o;
    }

    private static List<Definitions.KitItem> kit(JsonObject o) {
        List<Definitions.KitItem> out = new ArrayList<>();
        if (!o.has("starting_kit")) return List.of();
        for (JsonElement ke : array(o, "starting_kit")) {
            JsonObject k = ke.getAsJsonObject();
            List<Definitions.KitSpell> spells = new ArrayList<>();
            if (k.has("spells")) {
                for (JsonElement se : array(k, "spells")) {
                    JsonObject sp = se.getAsJsonObject();
                    spells.add(new Definitions.KitSpell(string(sp, "id"), sp.has("level") ? sp.get("level").getAsInt() : 1));
                }
            }
            out.add(new Definitions.KitItem(string(k, "item"),
                    k.has("count") ? k.get("count").getAsInt() : 1, optString(k, "slot", "inventory"), List.copyOf(spells)));
        }
        return List.copyOf(out);
    }

    private static List<EffectDef> effects(JsonObject holder) {
        return effects(holder, "effects");
    }

    private static List<EffectDef> effects(JsonObject holder, String key) {
        List<EffectDef> out = new ArrayList<>();
        if (!holder.has(key)) return List.of();
        for (JsonElement ee : array(holder, key)) {
            JsonObject e = ee.getAsJsonObject();
            out.add(new EffectDef(string(e, "type"), e));
        }
        return List.copyOf(out);
    }

    private static List<MilestoneDef> milestones(JsonObject holder) {
        List<MilestoneDef> out = new ArrayList<>();
        if (!holder.has("milestones")) return List.of();
        for (JsonElement me : holder.getAsJsonArray("milestones")) {
            JsonObject m = me.getAsJsonObject();
            boolean sub = m.has("subclass_level");
            if (!sub && !m.has("total_level")) {
                throw new IllegalArgumentException("hito sin \"total_level\" ni \"subclass_level\"");
            }
            int level = m.get(sub ? "subclass_level" : "total_level").getAsInt();
            out.add(new MilestoneDef(level, sub, string(m, "grants"), optString(m, "upgrades", null)));
        }
        return List.copyOf(out);
    }

    private static JsonArray array(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonArray()) throw new IllegalArgumentException("falta la lista \"" + key + "\"");
        return o.getAsJsonArray(key);
    }

    private static String string(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive()) throw new IllegalArgumentException("falta \"" + key + "\"");
        return o.get(key).getAsString();
    }

    private static String optString(JsonObject o, String key, String def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : def;
    }

    private static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (o.has(key)) for (JsonElement e : o.getAsJsonArray(key)) out.add(e.getAsString());
        return List.copyOf(out);
    }

    private DefinitionParser() {}
}
