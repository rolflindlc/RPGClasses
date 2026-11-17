package io.github.rpgclasses.core;

import com.google.gson.JsonParser;
import io.github.rpgclasses.core.definition.DefinitionParser;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.SubclassDef;
import io.github.rpgclasses.core.progression.ProgressState;
import io.github.rpgclasses.core.progression.ProgressionRules;
import io.github.rpgclasses.core.progression.ProgressionRules.Result;
import io.github.rpgclasses.core.progression.XpMath;
import io.github.rpgclasses.core.stat.StatMath;
import io.github.rpgclasses.core.value.LevelValue;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Pruebas de la lógica pura: no necesitan arrancar Minecraft. */
public class CoreLogicTest {
    private static final double EPS = 1e-9;
    private static final long RICH = 1_000_000;

    private static final String CLASS_JSON = """
            {"branches":[
              {"id":"a","effects":[{"type":"rites_and_paths:mechanic","id":"t:x"}]},
              {"id":"b","effects":[{"type":"rites_and_paths:mechanic","id":"t:x"}]},
              {"id":"c","effects":[{"type":"rites_and_paths:mechanic","id":"t:x"}],
               "perks":[{"id":"p1","effects":[{"type":"rites_and_paths:mechanic","id":"t:p"}]},{"id":"p2","effects":[{"type":"rites_and_paths:mechanic","id":"t:q"}]}]},
              {"id":"d","effects":[{"type":"rites_and_paths:mechanic","id":"t:x"}]},
              {"id":"e","effects":[{"type":"rites_and_paths:mechanic","id":"t:x"}]}],
             "subclasses":["t:s1","t:s2","t:s3"]}""";

    private static ClassDef testClass() {
        return DefinitionParser.parseClass("t:class", CLASS_JSON);
    }

    private static SubclassDef testSub(String id) {
        return DefinitionParser.parseSubclass(id, "{\"class\":\"t:class\",\"effects\":[{\"type\":\"rites_and_paths:mechanic\",\"id\":\"t:y\"}]}");
    }

    private static void fill(ProgressState s, ClassDef def, String branch, int points) {
        for (int i = 0; i < points; i++) {
            assertEquals(Result.OK, ProgressionRules.checkSpendBranch(s, def, branch, RICH, 1.0));
            ProgressionRules.spendBranch(s, branch, 1.0);
        }
    }

    // --- Ramas extra y perks (reglas 11 y 13) ---

    @Test
    public void schoolWallsParsedAndClamped() {
        ClassDef plain = testClass();
        assertTrue(plain.schoolWalls().isEmpty());
        assertTrue(plain.magicWallExemptSchools().isEmpty());
        ClassDef c = DefinitionParser.parseClass("t:c2", "{\"branches\":[],\"subclasses\":[],\"school_walls\":{\"irons_spellbooks:holy\":1.5},"
                + "\"magic_wall_exempt_schools\":[\"irons_spellbooks:fire\"]}");
        assertEquals(1.0, c.schoolWalls().get("irons_spellbooks:holy"), EPS);
        assertEquals("irons_spellbooks:fire", c.magicWallExemptSchools().get(0));
    }

    @Test
    public void thirdBranchLocksTheOtherTwo() {
        ClassDef def = testClass();
        ProgressState s = new ProgressState();
        ProgressionRules.chooseClass(s, def);
        fill(s, def, "a", 1);
        fill(s, def, "b", 1);
        assertFalse(ProgressionRules.isBranchLocked(s, "e"));
        fill(s, def, "c", 1);
        assertTrue(ProgressionRules.isBranchLocked(s, "d"));
        assertTrue(ProgressionRules.isBranchLocked(s, "e"));
        assertFalse(ProgressionRules.isBranchLocked(s, "a"));
        assertEquals(Result.BRANCH_LOCKED, ProgressionRules.checkSpendBranch(s, def, "d", RICH, 1.0));
        fill(s, def, "a", 9);
        // Reiniciar la clase desbloquea todo.
        ProgressionRules.respecClass(s, 0.33);
        ProgressionRules.chooseClass(s, def);
        assertEquals(Result.OK, ProgressionRules.checkSpendBranch(s, def, "e", RICH, 1.0));
    }

    @Test
    public void perkNeedsAFullBranchAndIsLostOnClassRespec() {
        ClassDef def = testClass();
        ProgressState s = new ProgressState();
        ProgressionRules.chooseClass(s, def);
        fill(s, def, "c", 9);
        assertEquals(Result.PERK_UNAVAILABLE, ProgressionRules.checkChoosePerk(s, def, "c", "p1"));
        fill(s, def, "c", 1);
        assertTrue(ProgressionRules.perkPending(s, def.branch("c")));
        assertEquals(Result.UNKNOWN_PERK, ProgressionRules.checkChoosePerk(s, def, "c", "zz"));
        assertEquals(Result.UNKNOWN_BRANCH, ProgressionRules.checkChoosePerk(s, def, "zz", "p1"));
        assertEquals(Result.OK, ProgressionRules.checkChoosePerk(s, def, "c", "p2"));
        ProgressionRules.choosePerk(s, "c", "p2");
        assertFalse(ProgressionRules.perkPending(s, def.branch("c")));
        assertEquals(Result.PERK_ALREADY_CHOSEN, ProgressionRules.checkChoosePerk(s, def, "c", "p1"));
        // El perk no cuenta para el Nivel Total.
        assertEquals(10, s.totalLevel());
        // Reiniciar la subclase no lo toca; reiniciar la clase sí.
        s.subclassId = "t:s1";
        ProgressionRules.respecSubclass(s, 1.0, 0.33);
        assertEquals("p2", s.perkIn("c"));
        ProgressionRules.respecClass(s, 0.33);
        assertTrue(s.perks.isEmpty());
    }

    // --- XP ---

    @Test
    public void totalCostMatchesDesign() {
        assertEquals(86, XpMath.costOfLevel(1, 1.0));
        assertEquals(5190, XpMath.totalCost(0, 30, 1.0));
        assertEquals(2930, XpMath.totalCost(30, 40, 1.0));
        assertEquals(8120, XpMath.totalCost(0, 40, 1.0));
        assertEquals(172, XpMath.costOfLevel(1, 2.0));
    }

    @Test
    public void vanillaXpFormulas() {
        assertEquals(0, XpMath.totalXpAtLevel(0));
        assertEquals(352, XpMath.totalXpAtLevel(16));
        assertEquals(1395, XpMath.totalXpAtLevel(30));
        assertEquals(2920, XpMath.totalXpAtLevel(40));
        // Las fórmulas cerradas coinciden con sumar nivel a nivel.
        long sum = 0;
        for (int l = 0; l < 60; l++) {
            assertEquals(sum, XpMath.totalXpAtLevel(l));
            sum += XpMath.xpNeededForNextLevel(l);
        }
        assertEquals(1395 + 56, XpMath.totalPoints(30, 0.5f));
        assertEquals(0, XpMath.levelFromTotal(6));
        assertEquals(1, XpMath.levelFromTotal(7));
        assertEquals(30, XpMath.levelFromTotal(1395 + 56));
        assertEquals(40, XpMath.levelFromTotal(2920));
    }

    // --- Progresión ---

    @Test
    public void subclassCapFollowsRule() {
        assertEquals(0, ProgressionRules.subclassCap(14));
        assertEquals(2, ProgressionRules.subclassCap(15));
        assertEquals(5, ProgressionRules.subclassCap(20));
        assertEquals(7, ProgressionRules.subclassCap(25));
        assertEquals(10, ProgressionRules.subclassCap(30));
    }

    @Test
    public void fullProgressionCosts8120() {
        ClassDef def = testClass();
        ProgressState s = new ProgressState();
        assertEquals(Result.NO_CLASS, ProgressionRules.checkSpendBranch(s, def, "a", RICH, 1.0));
        assertEquals(Result.OK, ProgressionRules.checkChooseClass(s, def));
        ProgressionRules.chooseClass(s, def);
        assertEquals(Result.ALREADY_HAS_CLASS, ProgressionRules.checkChooseClass(s, def));

        fill(s, def, "a", 10);
        assertEquals(Result.BRANCH_FULL, ProgressionRules.checkSpendBranch(s, def, "a", RICH, 1.0));
        assertEquals(Result.UNKNOWN_BRANCH, ProgressionRules.checkSpendBranch(s, def, "zzz", RICH, 1.0));
        assertEquals(Result.NOT_ENOUGH_XP, ProgressionRules.checkSpendBranch(s, def, "b", 10, 1.0));
        assertEquals(Result.SUBCLASS_LOCKED, ProgressionRules.checkChooseSubclass(s, def, testSub("t:s1")));

        fill(s, def, "b", 5); // 15 de base
        assertEquals(Result.UNKNOWN_SUBCLASS, ProgressionRules.checkChooseSubclass(s, def, testSub("t:other")));
        assertEquals(Result.NO_SUBCLASS, ProgressionRules.checkSpendSubclass(s, RICH, 1.0));
        assertEquals(Result.OK, ProgressionRules.checkChooseSubclass(s, def, testSub("t:s1")));
        ProgressionRules.chooseSubclass(s, testSub("t:s1"));
        ProgressionRules.spendSubclass(s, 1.0);
        ProgressionRules.spendSubclass(s, 1.0);
        assertEquals(Result.SUBCLASS_CAP, ProgressionRules.checkSpendSubclass(s, RICH, 1.0));
        assertEquals(17, s.totalLevel());

        fill(s, def, "b", 5);
        fill(s, def, "c", 10);
        while (ProgressionRules.checkSpendSubclass(s, RICH, 1.0).ok()) ProgressionRules.spendSubclass(s, 1.0);
        assertEquals(30, s.basePoints());
        assertEquals(10, s.subclassLevel);
        assertEquals(40, s.totalLevel());
        // El reparto entre base y subclase no cambia el total.
        assertEquals(8120, s.xpPaidBase + s.xpPaidSubclass);
    }

    @Test
    public void respecIsUnlimitedAndLosesAFraction() {
        ClassDef def = testClass();
        ProgressState s = new ProgressState();
        ProgressionRules.chooseClass(s, def);
        fill(s, def, "a", 10);
        fill(s, def, "b", 5);
        ProgressionRules.chooseSubclass(s, testSub("t:s1"));
        ProgressionRules.spendSubclass(s, 1.0); // comprado barato, como nivel 16
        ProgressionRules.spendSubclass(s, 1.0);
        fill(s, def, "b", 5);
        fill(s, def, "c", 10);
        while (ProgressionRules.checkSpendSubclass(s, RICH, 1.0).ok()) ProgressionRules.spendSubclass(s, 1.0);
        assertTrue(ProgressionRules.isMaxed(s));

        assertEquals(Result.IN_COMBAT, ProgressionRules.checkRespecSubclass(s, true));
        assertEquals(Result.OK, ProgressionRules.checkRespecSubclass(s, false));
        // Se devuelve el coste actual de los niveles 31-40 (2930) menos el 33%, sin importar el orden de compra.
        assertEquals((long) Math.floor(2930 * 0.67), ProgressionRules.respecSubclass(s, 1.0, 0.33));
        assertNull(s.subclassId);
        assertEquals(30, s.totalLevel());
        assertEquals(5190, s.xpPaidBase + s.xpPaidSubclass);
        assertFalse(ProgressionRules.isMaxed(s));

        // Se puede elegir otra subclase y reiniciar de nuevo, sin límite.
        assertEquals(Result.OK, ProgressionRules.checkChooseSubclass(s, def, testSub("t:s2")));
        ProgressionRules.chooseSubclass(s, testSub("t:s2"));
        ProgressionRules.spendSubclass(s, 1.0);
        assertEquals((long) Math.floor(XpMath.costOfLevel(31, 1.0) * 0.67), ProgressionRules.respecSubclass(s, 1.0, 0.33));

        // Reinicio de clase: 67% de todo lo pagado, y permite elegir otra clase.
        assertEquals((long) Math.floor(5190 * 0.67), ProgressionRules.respecClass(s, 0.33));
        assertNull(s.classId);
        assertEquals(0, s.totalLevel());
        assertEquals(Result.OK, ProgressionRules.checkChooseClass(s, def));
    }

    @Test
    public void respecWithoutLossAllowsExactRebuy() {
        ClassDef def = testClass();
        ProgressState s = new ProgressState();
        ProgressionRules.chooseClass(s, def);
        fill(s, def, "a", 10);
        fill(s, def, "b", 5);
        ProgressionRules.chooseSubclass(s, testSub("t:s1"));
        ProgressionRules.spendSubclass(s, 1.0);
        ProgressionRules.spendSubclass(s, 1.0);
        fill(s, def, "b", 5); // la base sube después: los 2 niveles de subclase ahora "valen" como 21 y 22
        long refund = ProgressionRules.respecSubclass(s, 1.0, 0.0);
        assertEquals(XpMath.totalCost(20, 22, 1.0), refund);
        assertEquals(XpMath.totalCost(0, 20, 1.0), s.xpPaidBase);
    }

    @Test
    public void suspendedClassCannotSpend() {
        ProgressState s = new ProgressState();
        s.classId = "t:removed";
        assertEquals(Result.UNKNOWN_CLASS, ProgressionRules.checkSpendBranch(s, null, "a", RICH, 1.0));
        assertEquals(Result.OK, ProgressionRules.checkRespecClass(s, false));
    }

    // --- Estadísticas ---

    @Test
    public void wallsScaleWithBasePointsOnly() {
        assertEquals(0.0, StatMath.wallPenalty(0.75, 0), EPS);
        assertEquals(0.375, StatMath.wallPenalty(0.75, 15), EPS);
        assertEquals(0.75, StatMath.wallPenalty(0.75, 30), EPS);
        assertEquals(0.75, StatMath.wallPenalty(0.75, 40), EPS);
        assertEquals(0.0, StatMath.mitigatedWall(0.5, 1.0), EPS);
        assertEquals(0.25, StatMath.mitigatedWall(0.5, 0.5), EPS);
        // Escudos: -30% -> +60% de cooldown y +30% de desgaste; -100% -> +200% y +100%.
        assertEquals(1.6, StatMath.shieldCooldownFactor(0.3), EPS);
        assertEquals(1.3, StatMath.shieldDurabilityFactor(0.3), EPS);
        assertEquals(3.0, StatMath.shieldCooldownFactor(1.0), EPS);
    }

    @Test
    public void damageAndCritMath() {
        assertEquals(2.3, StatMath.additiveFactor(0.30, 1.00), EPS);
        assertEquals(3.25, StatMath.additiveFactor(0.75, 1.50), EPS);
        assertEquals(0.35, StatMath.critChance(0.30, 0.05, false), EPS);
        assertEquals(0.25, StatMath.critChance(0.30, -0.05, false), EPS);
        assertEquals(1.0, StatMath.critChance(0.30, -0.05, true), EPS);
        assertEquals(2.5, StatMath.critMultiplier(1.0), EPS);
        assertEquals(1.0, StatMath.capSpeedBonus(1.4), EPS);
        assertEquals(1.5, StatMath.capJumpBonus(2.0), EPS);
        assertEquals(0f, StatMath.sanitizeDamage(Double.NaN), 0f);
        assertEquals(0f, StatMath.sanitizeDamage(-3), 0f);
    }

    @Test
    public void deathGambleExpectedSurvivals() {
        // Rev. 2.10: 75 % la primera vez y 50 % después, sin límite. E = 0,75 × (1 + E'), con E' = 0,5 × (1 + E') = 1.
        double first = 0.75, rest = 0.5;
        double expected = first * (1 + rest / (1 - rest));
        assertEquals(1.5, expected, 1e-9);
    }

    @Test
    public void levelValueShapes() {
        LevelValue up = LevelValue.upTo(0.5, 10);
        assertEquals(0.0, up.at(0), EPS);
        assertEquals(0.05, up.at(1), EPS);
        assertEquals(0.5, up.at(10), EPS);
        assertEquals(0.5, up.at(99), EPS);
        LevelValue lin = LevelValue.linear(0.001, 0.01, 10);
        assertEquals(0.001, lin.at(1), EPS);
        assertEquals(0.01, lin.at(10), EPS);
        LevelValue luck = LevelValue.fromJson(JsonParser.parseString("{\"from\":3,\"to\":5}").getAsJsonObject(), 10);
        assertEquals(3.0, luck.at(1), EPS);
        assertEquals(5.0, luck.at(10), EPS);
        LevelValue table = LevelValue.fromJson(JsonParser.parseString("{\"values\":[3,3,3,4,4,4,4,5,5,5]}").getAsJsonObject(), 10);
        assertEquals(4.0, table.at(5), EPS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void levelValueTableMustMatchLevels() {
        LevelValue.fromJson(JsonParser.parseString("{\"values\":[1,2,3]}").getAsJsonObject(), 10);
    }
}
