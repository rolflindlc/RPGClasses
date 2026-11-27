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
    private static final Path DATA = Path.of(System.getProperty("rites_and_paths.data", "src/main/resources/data"));
    private static final DefinitionStore STORE = new DefinitionStore();

    private static Map<String, String> read(String folder) throws IOException {
        Map<String, String> out = new HashMap<>();
        try (Stream<Path> namespaces = Files.list(DATA)) {
            for (Path ns : (Iterable<Path>) namespaces::iterator) {
                Path dir = ns.resolve("rites_and_paths").resolve(folder);
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
        assertEquals(List.of("rites_and_paths:barbarian", "rites_and_paths:bard", "rites_and_paths:cleric", "rites_and_paths:druid", "rites_and_paths:mage",
                "rites_and_paths:monk",
                "rites_and_paths:paladin", "rites_and_paths:ranger", "rites_and_paths:rogue", "rites_and_paths:spin_master"), List.copyOf(STORE.classIds()));
        for (String id : STORE.classIds()) {
            ClassDef c = STORE.classDef(id);
            for (String sub : c.subclasses()) assertNotNull(sub, STORE.subclassDef(sub));
        }
    }

    @Test
    public void everyClassHasFiveBranchesWithTwoPerks() {
        int perks = 0;
        for (String id : STORE.classIds()) {
            ClassDef c = STORE.classDef(id);
            assertEquals(id, 5, c.branches().size());
            for (var b : c.branches()) perks += b.perks().size();
        }
        assertEquals(110, perks);
    }

    @Test
    public void wallTotalsMatchDesignTable() {
        Map<String, Integer> expected = Map.of("mage", 200, "paladin", 150, "barbarian", 175,
                "rogue", 245, "ranger", 150, "monk", 275, "bard", 245, "cleric", 235, "spin_master", 250,
                "druid", 285);
        for (Map.Entry<String, Integer> e : expected.entrySet()) {
            ClassDef c = STORE.classDef("rites_and_paths:" + e.getKey());
            double total = 0;
            for (Map.Entry<String, Double> w : c.walls().entrySet()) {
                // La Pólvora (adenda del Ingeniero) llegó después de la tabla de diseño: no cuenta en su total.
                if (w.getKey().equals(io.github.rpgclasses.core.Ids.FIREARM)) continue;
                total += StatMath.wallPenalty(w.getValue(), 30) * 100;
            }
            assertEquals(e.getKey(), e.getValue(), Math.round(total), 0.0);
        }
    }

    /** Adenda del Ingeniero, 2: muro de Pólvora de cada clase (el Ingeniero, cuando exista, es afín). */
    @Test
    public void firearmWallsMatchEngineerAddendum() {
        Map<String, Double> expected = Map.of("ranger", .40, "rogue", .50, "barbarian", .60, "paladin", .60,
                "monk", .60, "bard", .60, "mage", .75, "cleric", .75, "druid", .75, "spin_master", .60);
        for (Map.Entry<String, Double> e : expected.entrySet()) {
            ClassDef c = STORE.classDef("rites_and_paths:" + e.getKey());
            assertEquals(e.getKey(), e.getValue(), c.walls().get(io.github.rpgclasses.core.Ids.FIREARM), 1e-9);
        }
    }

    /** Adenda del Druida: la escuela de naturaleza es suya (muro de escuela del 100 % para el resto). */
    @Test
    public void natureSchoolBelongsToTheDruid() {
        for (String id : STORE.classIds()) {
            Double wall = STORE.classDef(id).schoolWalls().get("irons_spellbooks:nature");
            assertEquals(id, id.equals("rites_and_paths:druid") ? null : 1.0, wall);
        }
    }

    /** Adenda del Maestro del Giro: Esferas solo para él (muro fijo del 100 % para el resto), y es experimental. */
    @Test
    public void spheresBelongToTheSpinMasterOnly() {
        for (String id : STORE.classIds()) {
            ClassDef c = STORE.classDef(id);
            boolean spin = id.equals("rites_and_paths:spin_master");
            assertEquals(id, spin, c.experimental());
            assertEquals(id, spin ? null : 1.0, c.fixedWalls().get(io.github.rpgclasses.core.Ids.SPHERES));
            io.github.rpgclasses.core.progression.ProgressState s = new io.github.rpgclasses.core.progression.ProgressState();
            s.classId = id;
            assertEquals(id, spin ? 0.0 : 1.0,
                    io.github.rpgclasses.core.stat.StatCalculator.wall(s, c, null, io.github.rpgclasses.core.Ids.SPHERES), 1e-9);
        }
    }

    @Test
    public void critAdjustmentsMatchDesign() {
        assertEquals(0.05, STORE.classDef("rites_and_paths:barbarian").critChanceBonus(), 1e-9);
        assertEquals(-0.05, STORE.classDef("rites_and_paths:rogue").critChanceBonus(), 1e-9);
        assertTrue(STORE.classDef("rites_and_paths:monk").armorDisablesPassives());
        assertFalse(STORE.classDef("rites_and_paths:mage").armorDisablesPassives());
    }

    @Test
    public void brokenClassIsDisabledNotFatal() {
        DefinitionStore store = new DefinitionStore();
        store.load(Map.of("t:bad", "{\"branches\":[],\"subclasses\":[]}", "t:worse", "{not json"), Map.of());
        assertTrue(store.classIds().isEmpty());
        assertEquals(2, store.errors().size());
    }
}
