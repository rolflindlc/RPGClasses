package io.github.rpgclasses.core.definition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.value.LevelValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Parámetros del efecto {@code rites_and_paths:attribute}:
 * <pre>
 * { "type": "rites_and_paths:attribute", "condition": "rites_and_paths:no_armor",
 *   "modifiers": [ { "attribute": "minecraft:generic.armor", "to": 16, "operation": "add" } ] }
 * </pre>
 */
public record AttributeEffect(String condition, List<Modifier> modifiers) {
    public static final Set<String> OPERATIONS = Set.of("add", "multiply_base", "multiply_total");

    public record Modifier(String attribute, String operation, LevelValue value) {}

    /** @throws IllegalArgumentException con un mensaje legible si los parámetros no son válidos. */
    public static AttributeEffect parse(JsonObject params, int maxLevel) {
        String condition = params.has("condition") ? params.get("condition").getAsString() : null;
        if (condition != null && !Ids.CONDITIONS.contains(condition)) {
            throw new IllegalArgumentException("condición desconocida: " + condition);
        }
        if (!params.has("modifiers") || !params.get("modifiers").isJsonArray() || params.getAsJsonArray("modifiers").isEmpty()) {
            throw new IllegalArgumentException("falta la lista \"modifiers\"");
        }
        List<Modifier> out = new ArrayList<>();
        for (JsonElement me : params.getAsJsonArray("modifiers")) {
            JsonObject m = me.getAsJsonObject();
            if (!m.has("attribute")) throw new IllegalArgumentException("modificador sin \"attribute\"");
            String attribute = m.get("attribute").getAsString();
            if (attribute.indexOf(':') <= 0) throw new IllegalArgumentException("atributo sin namespace: " + attribute);
            String op = m.has("operation") ? m.get("operation").getAsString() : "add";
            if (!OPERATIONS.contains(op)) throw new IllegalArgumentException("operación desconocida: " + op);
            out.add(new Modifier(attribute, op, LevelValue.fromJson(m, maxLevel)));
        }
        return new AttributeEffect(condition, List.copyOf(out));
    }
}
