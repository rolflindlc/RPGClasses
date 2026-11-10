package io.github.rpgclasses.core;

import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.SubclassDef;
import io.github.rpgclasses.core.progression.ProgressState;
import io.github.rpgclasses.core.progression.ProgressionRules;
import io.github.rpgclasses.core.stat.StatCalculator;
import io.github.rpgclasses.core.stat.StatCalculator.Context;
import io.github.rpgclasses.core.stat.StatCalculator.Key;
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

/** Comprueba, con los JSON reales, que los números que se aplicarán en el juego son los del diseño. */
public class StatCalculatorTest {
    private static final double EPS = 1e-9;
    private static final Path DATA = Path.of(System.getProperty("rpgclasses.data", "src/main/resources/data"));
    private static final DefinitionStore STORE = new DefinitionStore();
    private static final Context NO_ARMOR = new Context(false);
    private static final Context ARMORED = new Context(true);

    private static Map<String, String> read(String folder) throws IOException {
        Map<String, String> out = new HashMap<>();
        try (Stream<Path> files = Files.list(DATA.resolve("rpgclasses/rpgclasses").resolve(folder))) {
            for (Path f : (Iterable<Path>) files::iterator) {
                String name = f.getFileName().toString();
                out.put("rpgclasses:" + name.substring(0, name.length() - 5), Files.readString(f, StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    @BeforeClass
    public static void load() throws IOException {
        STORE.load(read("classes"), read("subclasses"), read("abilities"));
    }

    /** Jugador con todas las ramas a {@code branchPoints} y, opcionalmente, una subclase a {@code subLevel}. */
    private static ProgressState player(String classId, int branchPoints, String subclassId, int subLevel) {
        ClassDef c = STORE.classDef("rpgclasses:" + classId);
        ProgressState s = new ProgressState();
        ProgressionRules.chooseClass(s, c);
        s.branchPoints.replaceAll((k, v) -> branchPoints);
        if (subclassId != null) {
            s.subclassId = "rpgclasses:" + subclassId;
            s.subclassLevel = subLevel;
        }
        return s;
    }

    private static Map<Key, Double> totals(ProgressState s, Context ctx) {
        return StatCalculator.attributeTotals(s, STORE.classDef(s.classId), STORE.subclassDef(s.subclassId), ctx);
    }

    private static double wall(ProgressState s, String discipline) {
        return StatCalculator.wall(s, STORE.classDef(s.classId), STORE.subclassDef(s.subclassId), "rpgclasses:" + discipline);
    }

    @Test
    public void barbarianUnarmoredPassives() {
        ProgressState s = player("barbarian", 10, null, 0);
        Map<Key, Double> t = totals(s, NO_ARMOR);
        assertEquals(16.0, t.get(new Key("minecraft:generic.armor", "add")), EPS);
        assertEquals(6.0, t.get(new Key("minecraft:generic.armor_toughness", "add")), EPS);
        assertEquals(0.25, t.get(new Key("minecraft:generic.movement_speed", "multiply_base")), EPS);
        assertEquals(1.0, t.get(new Key("rpgclasses:crit_damage", "add")), EPS);
        // Cualquier pieza de armadura desactiva la defensa y la velocidad desarmadas.
        assertFalse(totals(s, ARMORED).containsKey(new Key("minecraft:generic.armor", "add")));
        // A media rama, la mitad: ambas pasivas suben con cada punto.
        assertEquals(8.0, totals(player("barbarian", 5, null, 0), NO_ARMOR).get(new Key("minecraft:generic.armor", "add")), EPS);
    }

    @Test
    public void woundFuryMatchesDesign() {
        ClassDef c = STORE.classDef("rpgclasses:barbarian");
        ProgressState s = player("barbarian", 10, null, 0);
        assertEquals(0.0, StatCalculator.woundFuryBonus(s, c, 1.0), EPS);
        assertEquals(0.40, StatCalculator.woundFuryBonus(s, c, 0.60), EPS); // supuesto de la estimación de balance
        assertEquals(0.75, StatCalculator.woundFuryBonus(s, c, 0.05), EPS); // tope
        assertEquals(0.04, StatCalculator.woundFuryBonus(player("barbarian", 1, null, 0), c, 0.60), EPS);
    }

    @Test
    public void monkBreezeMasterReachesDesignTotals() {
        Map<Key, Double> t = totals(player("monk", 10, "breeze_master", 10), NO_ARMOR);
        assertEquals(0.80, t.get(new Key("minecraft:generic.movement_speed", "multiply_base")), EPS);
        assertEquals(1.20, t.get(new Key("rpgclasses:jump_bonus", "add")), EPS);
    }

    @Test
    public void wallsAndMitigation() {
        assertEquals(0.45, wall(player("rogue", 10, null, 0), "magic"), EPS);
        assertEquals(0.225, wall(player("rogue", 5, null, 0), "magic"), EPS);
        assertEquals(0.0, wall(player("rogue", 10, null, 0), "light"), EPS); // afín
        // Armonía Arcana: -10% del muro de Magia por nivel de subclase.
        assertEquals(0.225, wall(player("rogue", 10, "arcane_blade", 5), "magic"), EPS);
        assertEquals(0.0, wall(player("rogue", 10, "arcane_blade", 10), "magic"), EPS);
        assertEquals(0.55, wall(player("rogue", 10, "arcane_blade", 10), "heavy"), EPS);
        // Armonía Marcial: anula Pesadas y Ligeras, no Arco.
        assertEquals(0.0, wall(player("mage", 10, "battle_mage", 10), "heavy"), EPS);
        assertEquals(0.25, wall(player("mage", 10, "battle_mage", 10), "bow"), EPS);
    }

    @Test
    public void paladinCuirassAndJuggernaut() {
        ProgressState s = player("paladin", 10, "charging_juggernaut", 10);
        double[] cuirass = StatCalculator.equippedArmorBonus(s, STORE.classDef(s.classId));
        assertEquals(0.25, cuirass[0], EPS);
        assertEquals(0.40, cuirass[1], EPS);
        assertEquals(0.60, totals(s, ARMORED).get(new Key("minecraft:generic.max_health", "multiply_base")), EPS);
    }

    @Test
    public void sniperTotalsIncludeBase() {
        Map<Key, Double> t = totals(player("ranger", 10, "sniper", 10), ARMORED);
        assertEquals(0.75, t.get(new Key("rpgclasses:bow_damage", "add")), EPS);
        assertEquals(3.0, t.get(new Key("rpgclasses:projectile_velocity", "add")), EPS);
    }

    @Test
    public void suspendedOrEmptyPlayerGetsNothing() {
        ProgressState s = new ProgressState();
        s.classId = "rpgclasses:removed";
        assertEquals(0, StatCalculator.attributeTotals(s, null, null, NO_ARMOR).size());
        assertEquals(0.0, StatCalculator.wall(s, null, null, "rpgclasses:magic"), EPS);
    }
}
