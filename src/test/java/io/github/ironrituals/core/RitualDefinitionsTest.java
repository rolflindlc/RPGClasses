package io.github.ironrituals.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/** Comprueba que las definiciones de rituales del datapack cumplen el formato y cubren el catálogo. */
public class RitualDefinitionsTest {
    private static final Path DIR = Path.of("src/main/resources/data/ironrituals/recipes/rituals");
    private static final Path LANG = Path.of("src/main/resources/assets/ironrituals/lang/es_es.json");

    private static JsonObject read(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    public void everyCatalogRitualHasAValidDefinition() throws IOException {
        JsonObject lang = read(LANG);
        Set<String> seen = new HashSet<>();
        for (String id : RitualIds.ALL) {
            Path file = DIR.resolve(id + ".json");
            assertTrue("falta " + file, Files.exists(file));
            JsonObject r = read(file);
            assertEquals("ironrituals:ritual", r.get("type").getAsString());
            assertEquals("ironrituals:" + id, r.get("ritual").getAsString());
            assertTrue(r.get("school").getAsString().startsWith("irons_spellbooks:"));
            int min = r.get("min_circle").getAsInt();
            assertTrue(min >= 1 && min <= 3);

            JsonArray tiers = r.getAsJsonArray("tiers");
            assertFalse(tiers.isEmpty());
            Set<Integer> levels = new HashSet<>();
            for (JsonElement te : tiers) {
                JsonObject t = te.getAsJsonObject();
                int level = t.get("level").getAsInt();
                assertTrue(id + ": nivel " + level, level >= min && level <= 3);
                assertTrue(id + ": nivel repetido", levels.add(level));
                assertTrue(t.get("mana").getAsFloat() > 0);
                checkComponents(id, t.getAsJsonArray("components"));
            }
            if (r.has("catalysts")) {
                for (JsonElement ce : r.getAsJsonArray("catalysts")) {
                    JsonObject c = ce.getAsJsonObject();
                    checkComponents(id, c.getAsJsonArray("components"));
                    assertTrue(id + ": pista del catalizador", lang.has("ritual.ironrituals." + id + ".catalyst." + c.get("id").getAsString()));
                }
            }
            for (String suffix : new String[]{"", ".hint", ".lore"}) {
                assertTrue(id + suffix + " sin traducir", lang.has("ritual.ironrituals." + id + suffix));
            }
            seen.add(id);
        }
        assertEquals(29, seen.size());
    }

    private static void checkComponents(String id, JsonArray comps) {
        assertFalse(id + ": sin componentes", comps.isEmpty());
        for (JsonElement e : comps) {
            JsonObject c = e.getAsJsonObject();
            int kinds = (c.has("item") ? 1 : 0) + (c.has("tag") ? 1 : 0) + (c.has("ingredient") ? 1 : 0) + (c.has("soul") ? 1 : 0);
            assertEquals(id + ": componente ambiguo " + c, 1, kinds);
            if (c.has("count")) assertTrue(c.get("count").getAsInt() >= 1);
        }
    }
}
