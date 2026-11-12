package io.github.rpgclasses.core;

import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.RaceDef;
import io.github.rpgclasses.core.definition.RaceStore;
import io.github.rpgclasses.core.progression.ProgressState;
import io.github.rpgclasses.core.progression.ProgressionRules;
import io.github.rpgclasses.core.progression.ProgressionRules.Result;
import io.github.rpgclasses.core.stat.StatCalculator;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Razas (anexo de razas): archivos reales del mod y reglas puras. */
public class RaceTest {
    private static final Path DATA = Path.of(System.getProperty("rpgclasses.data", "src/main/resources/data"));
    private static final DefinitionStore STORE = new DefinitionStore();
    private static final RaceStore RACES = new RaceStore();
    private static final StatCalculator.Context CTX = new StatCalculator.Context(false);

    private static Map<String, String> read(String folder) throws IOException {
        Map<String, String> out = new HashMap<>();
        Path dir = DATA.resolve("rpgclasses").resolve("rpgclasses").resolve(folder);
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                String name = f.getFileName().toString();
                if (name.endsWith(".json")) out.put("rpgclasses:" + name.substring(0, name.length() - 5), Files.readString(f, StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    @BeforeClass
    public static void load() throws IOException {
        STORE.load(read("classes"), read("subclasses"), read("abilities"));
        RACES.load(read("races"));
    }

    private static ProgressState player(String race, String subrace, int rank, String classId, int basePoints) {
        ProgressState s = new ProgressState();
        RaceDef def = RACES.race("rpgclasses:" + race);
        assertEquals(Result.OK, ProgressionRules.checkChooseRace(s, def, subrace));
        ProgressionRules.chooseRace(s, def, subrace);
        s.raceRank = rank;
        if (classId != null) {
            ClassDef c = STORE.classDef("rpgclasses:" + classId);
            ProgressionRules.chooseClass(s, c);
            // 30 puntos repartidos en 3 ramas: el muro llega a su máximo.
            int left = basePoints;
            for (var b : c.branches()) {
                int n = Math.min(10, left);
                s.branchPoints.put(b.id(), n);
                left -= n;
            }
        }
        return s;
    }

    private static double wall(ProgressState s, String discipline) {
        return StatCalculator.wall(s, STORE.classDef(s.classId), null, RACES.race(s.raceId), discipline);
    }

    private static double health(ProgressState s) {
        return StatCalculator.attributeTotals(s, STORE.classDef(s.classId), null, RACES.race(s.raceId), CTX)
                .getOrDefault(new StatCalculator.Key(StatCalculator.MAX_HEALTH, "multiply_base"), 0.0);
    }

    @Test
    public void eightRacesWithThreeSubraces() {
        assertTrue("Errores: " + RACES.errors(), RACES.errors().isEmpty());
        assertEquals(8, RACES.ids().size());
        for (String id : RACES.ids()) assertEquals(id, 3, RACES.race(id).subraces().size());
    }

    @Test
    public void raceIsPermanentAndSurvivesClassRespec() {
        ProgressState s = player("orc", "stoneskin", 2, "mage", 30);
        assertEquals(Result.ALREADY_HAS_RACE, ProgressionRules.checkChooseRace(s, RACES.race("rpgclasses:elf"), "dark"));
        ProgressionRules.respecClass(s, 0.33);
        assertNull(s.classId);
        assertEquals("rpgclasses:orc", s.raceId);
        assertEquals("stoneskin", s.subraceId);
        assertEquals(2, s.raceRank);
    }

    @Test
    public void allSubracesAvailableAndUnknownRejected() {
        // Fase R2: las 24 subrazas están programadas y se pueden elegir.
        for (String[] rs : new String[][]{{"dwarf", "smith"}, {"goblin", "wolf_rider"}, {"infernal", "pyromaniac"}}) {
            assertEquals(Result.OK, ProgressionRules.checkChooseRace(new ProgressState(), RACES.race("rpgclasses:" + rs[0]), rs[1]));
        }
        assertEquals(Result.UNKNOWN_SUBRACE,
                ProgressionRules.checkChooseRace(new ProgressState(), RACES.race("rpgclasses:goblin"), "nope"));
    }

    @Test
    public void humanWallReductionMatchesDesignTable() {
        // Mago, Armas ligeras (−50 %): Adaptable R3 → −25 %; Especialista → −12,5 %.
        ProgressState s = player("human", "autodidact", 3, "mage", 30);
        ClassDef mage = STORE.classDef(s.classId);
        assertEquals(0.50, wall(s, Ids.LIGHT), 1e-9);
        assertEquals(Result.OK, ProgressionRules.checkChooseWall(s, mage, RACES.race(s.raceId), Ids.LIGHT));
        s.humanWalls.add(Ids.LIGHT);
        assertEquals(0.25, wall(s, Ids.LIGHT), 1e-9);
        assertEquals(Result.WALL_SLOTS_FULL, ProgressionRules.checkChooseWall(s, mage, RACES.race(s.raceId), Ids.SHIELD));

        ProgressState spec = player("human", "specialist", 3, "mage", 30);
        spec.humanWalls.add(Ids.LIGHT);
        assertEquals(0.125, wall(spec, Ids.LIGHT), 1e-9);
        spec.raceRank = 0;
        assertEquals(0.50 * (1 - 0.15), wall(spec, Ids.LIGHT), 1e-9);

        // Polivalente: segundo muro con la mitad (Escudos −50 % → −37,5 %).
        ProgressState poly = player("human", "polyvalent", 3, "mage", 30);
        poly.humanWalls.add(Ids.LIGHT);
        assertEquals(Result.OK, ProgressionRules.checkChooseWall(poly, mage, RACES.race(poly.raceId), Ids.SHIELD));
        assertEquals(Result.WALL_INVALID, ProgressionRules.checkChooseWall(poly, mage, RACES.race(poly.raceId), Ids.MAGIC));
        poly.humanWalls.add(Ids.SHIELD);
        assertEquals(0.25, wall(poly, Ids.LIGHT), 1e-9);
        assertEquals(0.375, wall(poly, Ids.SHIELD), 1e-9);

        // Quien no es humano no elige muro.
        ProgressState orc = player("orc", "stoneskin", 3, "mage", 30);
        assertEquals(Result.NOT_HUMAN, ProgressionRules.checkChooseWall(orc, mage, RACES.race(orc.raceId), Ids.LIGHT));
    }

    @Test
    public void humanReducesNegativeClassTraitsOnly() {
        assertEquals(-0.18, health(player("human", "specialist", 0, "mage", 0)), 1e-9);
        assertEquals(-0.10, health(player("human", "specialist", 3, "mage", 0)), 1e-9);
        // El +20 % de vida del Paladín no cambia.
        assertEquals(0.20, health(player("human", "specialist", 3, "paladin", 0)), 1e-9);
    }

    @Test
    public void autodidactHalvesRespecLossAtRankThree() {
        ProgressState s = player("human", "autodidact", 3, "mage", 0);
        assertEquals(0.5, StatCalculator.respecLossFactor(s, RACES.race(s.raceId)), 1e-9);
        ProgressState other = player("human", "specialist", 3, "mage", 0);
        assertEquals(1.0, StatCalculator.respecLossFactor(other, RACES.race(other.raceId)), 1e-9);
    }

    @Test
    public void healthSumsWithClassAndHasFloor() {
        // Goblin × Mago: −20 % −25 % = −45 %. Orco sin clase: +10 %.
        assertEquals(-0.45, health(player("goblin", "thief", 0, "mage", 0)), 1e-9);
        assertEquals(0.10, health(player("orc", "stoneskin", 0, null, 0)), 1e-9);
        // Goblin × Pícaro con Evasión (−10 %): −50 %, que es el suelo de 10 HP.
        ProgressState rogue = player("goblin", "thief", 0, "rogue", 30);
        for (var b : STORE.classDef(rogue.classId).branches()) rogue.branchPoints.put(b.id(), 0);
        rogue.branchPoints.put("wind", 10);
        rogue.perks.put("wind", "evasion");
        assertEquals(-0.50, health(rogue), 1e-9);
    }

    @Test
    public void mechanicsUnlockByRank() {
        ProgressState s = player("orc", "devourer", 0, null, 0);
        RaceDef orc = RACES.race(s.raceId);
        assertNotNull(StatCalculator.raceMechanic(s, orc, "rpgclasses:race_last_stand"));
        assertNotNull(StatCalculator.raceMechanic(s, orc, "rpgclasses:race_iron_stomach"));
        assertNull(StatCalculator.raceMechanic(s, orc, "rpgclasses:race_feast"));
        s.raceRank = 3;
        assertNotNull(StatCalculator.raceMechanic(s, orc, "rpgclasses:race_feast"));
        // Los valores por rango salen de la tabla.
        ProgressState stone = player("orc", "stoneskin", 1, null, 0);
        assertEquals(1.25, StatCalculator.raceMechanic(stone, orc, "rpgclasses:race_thick_skin").number("flat_reduction", 0), 1e-9);
        assertFalse(StatCalculator.raceMechanics(stone, orc).isEmpty());
    }
}
