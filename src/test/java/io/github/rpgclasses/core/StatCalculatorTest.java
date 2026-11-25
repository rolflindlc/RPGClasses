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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;

/** Comprueba, con los JSON reales, que los números que se aplicarán en el juego son los del diseño. */
public class StatCalculatorTest {
    private static final double EPS = 1e-9;
    private static final Path DATA = Path.of(System.getProperty("rites_and_paths.data", "src/main/resources/data"));
    private static final DefinitionStore STORE = new DefinitionStore();
    private static final Context NO_ARMOR = new Context(false);
    private static final Context ARMORED = new Context(true);

    private static Map<String, String> read(String folder) throws IOException {
        Map<String, String> out = new HashMap<>();
        try (Stream<Path> files = Files.list(DATA.resolve("rites_and_paths/rites_and_paths").resolve(folder))) {
            for (Path f : (Iterable<Path>) files::iterator) {
                String name = f.getFileName().toString();
                out.put("rites_and_paths:" + name.substring(0, name.length() - 5), Files.readString(f, StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    @BeforeClass
    public static void load() throws IOException {
        STORE.load(read("classes"), read("subclasses"), read("abilities"));
    }

    /** Jugador con sus 3 primeras ramas a {@code branchPoints} y, opcionalmente, una subclase a {@code subLevel}. */
    private static ProgressState player(String classId, int branchPoints, String subclassId, int subLevel) {
        ClassDef c = STORE.classDef("rites_and_paths:" + classId);
        ProgressState s = new ProgressState();
        ProgressionRules.chooseClass(s, c);
        for (int i = 0; i < ProgressionRules.BRANCHES_USABLE; i++) s.branchPoints.put(c.branches().get(i).id(), branchPoints);
        if (subclassId != null) {
            s.subclassId = "rites_and_paths:" + subclassId;
            s.subclassLevel = subLevel;
        }
        return s;
    }

    private static Map<Key, Double> totals(ProgressState s, Context ctx) {
        return StatCalculator.attributeTotals(s, STORE.classDef(s.classId), STORE.subclassDef(s.subclassId), ctx);
    }

    private static double wall(ProgressState s, String discipline) {
        return StatCalculator.wall(s, STORE.classDef(s.classId), STORE.subclassDef(s.subclassId), "rites_and_paths:" + discipline);
    }

    @Test
    public void barbarianUnarmoredPassives() {
        ProgressState s = player("barbarian", 10, null, 0);
        Map<Key, Double> t = totals(s, NO_ARMOR);
        assertEquals(16.0, t.get(new Key("minecraft:generic.armor", "add")), EPS);
        assertEquals(6.0, t.get(new Key("minecraft:generic.armor_toughness", "add")), EPS);
        assertEquals(0.25, t.get(new Key("minecraft:generic.movement_speed", "multiply_base")), EPS);
        assertEquals(0.5, t.get(new Key("rites_and_paths:crit_damage", "add")), EPS); // Brutalidad 2.7: +50%
        // Cualquier pieza de armadura desactiva la defensa y la velocidad desarmadas.
        assertFalse(totals(s, ARMORED).containsKey(new Key("minecraft:generic.armor", "add")));
        // A media rama, la mitad: ambas pasivas suben con cada punto.
        assertEquals(8.0, totals(player("barbarian", 5, null, 0), NO_ARMOR).get(new Key("minecraft:generic.armor", "add")), EPS);
    }

    @Test
    public void woundFuryMatchesDesign() {
        ClassDef c = STORE.classDef("rites_and_paths:barbarian");
        ProgressState s = player("barbarian", 10, null, 0);
        assertEquals(0.0, StatCalculator.woundFuryBonus(s, c, 1.0), EPS);
        assertEquals(0.40, StatCalculator.woundFuryBonus(s, c, 0.60), EPS); // supuesto de la estimación de balance
        assertEquals(0.75, StatCalculator.woundFuryBonus(s, c, 0.05), EPS); // tope
        assertEquals(0.04, StatCalculator.woundFuryBonus(player("barbarian", 1, null, 0), c, 0.60), EPS);
    }

    @Test
    public void monkBreezeMasterReachesDesignTotals() {
        Map<Key, Double> t = totals(player("monk", 10, "breeze_master", 10), NO_ARMOR);
        // Rev. 2.23: Cefiro Marcial +30% de velocidad (era +40%) y +80% de salto.
        assertEquals(0.3, t.get(new Key("minecraft:generic.movement_speed", "multiply_base")), EPS);
        assertEquals(0.8, t.get(new Key("rites_and_paths:jump_bonus", "add")), EPS);
    }

    @Test
    public void monkMartialBodyScalesWithBasePointsAndStances() {
        // Cuerpo Marcial: 13 / 4 de armadura y dureza y +4 de daño desarmado con 30 puntos; a mitad con 15.
        ProgressState full = player("monk", 10, null, 0);
        Map<Key, Double> t = totals(full, NO_ARMOR);
        assertEquals(13.0, t.get(new Key("minecraft:generic.armor", "add")), EPS);
        assertEquals(4.0, t.get(new Key("minecraft:generic.armor_toughness", "add")), EPS);
        assertEquals(4.0, t.get(new Key("rites_and_paths:unarmed_damage", "add")), EPS);
        Map<Key, Double> half = totals(player("monk", 5, null, 0), NO_ARMOR);
        assertEquals(6.5, half.get(new Key("minecraft:generic.armor", "add")), EPS);
        // Con armadura no hay suelo.
        assertNull(totals(full, ARMORED).get(new Key("minecraft:generic.armor", "add")));

        // Fuego: −25 % de la armadura del suelo. Agua: −25 % del daño y las cargas se suman antes del tope.
        ClassDef c = STORE.classDef(full.classId);
        Map<Key, Double> fire = StatCalculator.attributeTotals(full, c, null, new Context(false, Map.of(),
                Map.of("minecraft:generic.armor", 0.75, "minecraft:generic.armor_toughness", 0.75), Map.of()));
        assertEquals(9.75, fire.get(new Key("minecraft:generic.armor", "add")), EPS);
        ProgressState breeze = player("monk", 10, "breeze_master", 10);
        Map<Key, Double> water = StatCalculator.attributeTotals(breeze, c, STORE.subclassDef(breeze.subclassId),
                new Context(false, Map.of(), Map.of("rites_and_paths:unarmed_damage", 0.75),
                        Map.of(new Key("minecraft:generic.movement_speed", "multiply_base"), 0.8,
                               new Key("rites_and_paths:jump_bonus", "add"), 0.8)));
        assertEquals(3.0, water.get(new Key("rites_and_paths:unarmed_damage", "add")), EPS);
        assertEquals(1.0, water.get(new Key("minecraft:generic.movement_speed", "multiply_base")), EPS); // 0,4 + 0,8 → tope +100%
        assertEquals(1.5, water.get(new Key("rites_and_paths:jump_bonus", "add")), EPS); // 0,8 + 0,8 → tope +150%
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
        // Rasgo del Paladín (+20%) + Vigor Colosal (+60%) = +80%; la velocidad lleva el -5% del rasgo (rev. 2.22).
        assertEquals(0.80, totals(s, ARMORED).get(new Key("minecraft:generic.max_health", "multiply_base")), EPS);
        assertEquals(-0.05, totals(s, ARMORED).get(new Key("minecraft:generic.movement_speed", "multiply_base")), EPS);
        // Con Cuerpo Colosal (+20%) llega justo al tope global de +100%.
        s.perks.put("cuirass", "colossal_body");
        assertEquals(1.0, totals(s, ARMORED).get(new Key("minecraft:generic.max_health", "multiply_base")), EPS);
    }

    @Test
    public void traitsApplyInFullFromTheStart() {
        assertEquals(-0.20, totals(player("mage", 0, null, 0), NO_ARMOR).get(new Key("minecraft:generic.max_health", "multiply_base")), EPS);
        assertEquals(-0.15, totals(player("rogue", 0, null, 0), NO_ARMOR).get(new Key("minecraft:generic.max_health", "multiply_base")), EPS);
        assertFalse(totals(player("barbarian", 0, null, 0), NO_ARMOR).containsKey(new Key("minecraft:generic.max_health", "multiply_base")));
    }

    @Test
    public void perksApplyOnlyWithAFullBranch() {
        ProgressState s = player("rogue", 10, null, 0);
        s.perks.put("wind", "evasion");
        // Rasgo -15% + Esquiva -10% = -25% (15 HP).
        assertEquals(-0.25, totals(s, NO_ARMOR).get(new Key("minecraft:generic.max_health", "multiply_base")), EPS);
        s.branchPoints.put("wind", 9);
        assertEquals(-0.15, totals(s, NO_ARMOR).get(new Key("minecraft:generic.max_health", "multiply_base")), EPS);
        s.branchPoints.put("wind", 10);
        // Filo +25% + Ráfaga +25% de velocidad de ataque.
        s.perks.put("edge", "flurry");
        assertEquals(0.50, totals(s, NO_ARMOR).get(new Key("minecraft:generic.attack_speed", "multiply_base")), EPS);
        assertEquals(-0.12, totals(s, NO_ARMOR).get(new Key("minecraft:generic.attack_damage", "multiply_base")), EPS);
    }

    @Test
    public void perkMechanicsAndCoraza() {
        ProgressState s = player("paladin", 10, null, 0);
        s.perks.put("cuirass", "heavy_cuirass");
        double[] cuirass = StatCalculator.equippedArmorBonus(s, STORE.classDef(s.classId));
        assertEquals(0.45, cuirass[0], EPS);
        assertEquals(0.60, cuirass[1], EPS);
        s.perks.put("push", "shockwave");
        java.util.List<StatCalculator.ActiveMechanic> m = StatCalculator.mechanics(s, STORE.classDef(s.classId), null);
        assertEquals(1, m.size());
        assertEquals("rites_and_paths:shockwave", m.get(0).id());
        assertEquals(5, m.get(0).number("every_hits", 0), EPS);
        // Mecánica de rama que escala: Toxinas al 3% por punto.
        ProgressState r = player("rogue", 0, null, 0);
        r.branchPoints.put("toxins", 4);
        StatCalculator.ActiveMechanic toxin = StatCalculator.mechanics(r, STORE.classDef(r.classId), null).get(0);
        assertEquals(0.12, toxin.number("chance", 0), EPS);
    }

    @Test
    public void barbarianBrutalityPerksKeepExpectedDamage() {
        ProgressState s = player("barbarian", 10, null, 0);
        s.perks.put("brutality", "crushing_blow");
        Map<Key, Double> a = totals(s, NO_ARMOR);
        assertEquals(1.25, a.get(new Key("rites_and_paths:crit_damage", "add")), EPS);
        assertEquals(-0.15, a.get(new Key("rites_and_paths:crit_chance", "add")), EPS);
        s.perks.put("brutality", "steady_blow");
        Map<Key, Double> b = totals(s, NO_ARMOR);
        assertEquals(0.15, b.get(new Key("rites_and_paths:crit_damage", "add")), EPS);
        assertEquals(0.20, b.get(new Key("rites_and_paths:crit_chance", "add")), EPS);
    }

    @Test
    public void sniperTotalsIncludeBase() {
        Map<Key, Double> t = totals(player("ranger", 10, "sniper", 10), ARMORED);
        assertEquals(0.75, t.get(new Key("rites_and_paths:bow_damage", "add")), EPS);
        assertEquals(3.0, t.get(new Key("rites_and_paths:projectile_velocity", "add")), EPS);
    }

    @Test
    public void abilitiesUnlockByMilestoneAndUpgradesStayLatent() {
        ClassDef paladin = STORE.classDef("rites_and_paths:paladin");
        // 25 de base + 5 de subclase = Nivel Total 30: ya tiene Baluarte y, con subclase 5, su mejora.
        ProgressState s = player("paladin", 0, "blessed_guardian", 5);
        s.branchPoints.put("cuirass", 10);
        s.branchPoints.put("will", 10);
        s.branchPoints.put("push", 5);
        java.util.Set<String> unlocked = io.github.rpgclasses.core.ability.UnlockResolver.unlocked(s, paladin, STORE.subclassDef(s.subclassId));
        assertEquals(java.util.List.of("rites_and_paths:divine_smite", "rites_and_paths:taunt", "rites_and_paths:steady_guard", "rites_and_paths:bulwark", "rites_and_paths:healing_bulwark"),
                java.util.List.copyOf(unlocked));
        assertEquals(java.util.List.of("rites_and_paths:taunt", "rites_and_paths:bulwark"),
                io.github.rpgclasses.core.ability.UnlockResolver.actives(unlocked, STORE::abilityDef));

        // Con Nivel Total 29 aún no hay Baluarte: la mejora de subclase queda latente aunque su nivel se cumpla.
        s.branchPoints.put("push", 4);
        unlocked = io.github.rpgclasses.core.ability.UnlockResolver.unlocked(s, paladin, STORE.subclassDef(s.subclassId));
        assertFalse(unlocked.contains("rites_and_paths:bulwark"));
        assertFalse(unlocked.contains("rites_and_paths:healing_bulwark"));
    }

    @Test
    public void suspendedOrEmptyPlayerGetsNothing() {
        ProgressState s = new ProgressState();
        s.classId = "rites_and_paths:removed";
        assertEquals(0, StatCalculator.attributeTotals(s, null, null, NO_ARMOR).size());
        assertEquals(0.0, StatCalculator.wall(s, null, null, "rites_and_paths:magic"), EPS);
    }
}
