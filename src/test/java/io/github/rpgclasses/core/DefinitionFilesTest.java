package io.github.rpgclasses.core;

import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.stat.StatMath;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Valida los JSON reales del mod. Cualquier clase nueva que se añada al datapack queda cubierta sola. */
public class DefinitionFilesTest {
    private static final Path DATA = Path.of(System.getProperty("rpgclasses.data", "src/main/resources/data"));
    private static final DefinitionStore STORE = new DefinitionStore();

    private static Map<String, String> read(String folder) throws IOException {
        Map<String, String> out = new HashMap<>();
        try (Stream<Path> namespaces = Files.list(DATA)) {
            for (Path ns : (Iterable<Path>) namespaces::iterator) {
                Path dir = ns.resolve("rpgclasses").resolve(folder);
                if (!Files.isDirectory(dir)) continue;
                try (Stream<Path> files = Files.list(dir)) {
                    for (Path f : (Iterable<Path>) files::iterator) {
                        String name = f.getFileName().toString();
                        if (!name.endsWith(".json")) continue;
                        out.put(ns.getFileName() + ":" + name.substring(0, name.length() - 5),
                                Files.readString(f, StandardCharsets.UTF_8));
                    }
                }
            }
        }
        return out;
    }

    @BeforeClass
    public static void load() throws IOException {
        STORE.load(read("classes"), read("subclasses"), read("abilities"));
    }

    @Test
    public void allShippedDefinitionsAreValid() {
        assertTrue("Errores de validación: " + STORE.errors(), STORE.errors().isEmpty());
        assertEquals(List.of("rpgclasses:barbarian", "rpgclasses:mage", "rpgclasses:monk",
                "rpgclasses:paladin", "rpgclasses:ranger", "rpgclasses:rogue"), List.copyOf(STORE.classIds()));
        for (String id : STORE.classIds()) {
            ClassDef c = STORE.classDef(id);
            for (String sub : c.subclasses()) assertNotNull(sub, STORE.subclassDef(sub));
        }
    }

    @Test
    public void wallTotalsMatchDesignTable() {
        Map<String, Integer> expected = Map.of("mage", 200, "paladin", 150, "barbarian", 150,
                "rogue", 245, "ranger", 150, "monk", 275);
        for (Map.Entry<String, Integer> e : expected.entrySet()) {
            ClassDef c = STORE.classDef("rpgclasses:" + e.getKey());
            double total = 0;
            for (double v : c.walls().values()) total += StatMath.wallPenalty(v, 30) * 100;
            assertEquals(e.getKey(), e.getValue(), Math.round(total), 0.0);
        }
    }

    @Test
    public void critAdjustmentsMatchDesign() {
        assertEquals(0.05, STORE.classDef("rpgclasses:barbarian").critChanceBonus(), 1e-9);
        assertEquals(-0.05, STORE.classDef("rpgclasses:rogue").critChanceBonus(), 1e-9);
        assertTrue(STORE.classDef("rpgclasses:monk").armorDisablesPassives());
        assertFalse(STORE.classDef("rpgclasses:mage").armorDisablesPassives());
    }

    @Test
    public void brokenClassIsDisabledNotFatal() {
        DefinitionStore store = new DefinitionStore();
        store.load(Map.of("t:bad", "{\"branches\":[],\"subclasses\":[]}", "t:worse", "{not json"), Map.of());
        assertTrue(store.classIds().isEmpty());
        assertEquals(2, store.errors().size());
    }
}
