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
            branches.add(new BranchDef(
                    string(b, "id"),
                    b.has("max_points") ? b.get("max_points").getAsInt() : 10,
                    effects(b),
                    List.copyOf(perks)));
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
