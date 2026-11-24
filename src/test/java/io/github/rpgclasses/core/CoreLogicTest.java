package io.github.rpgclasses.core;

import com.google.gson.JsonParser;
import io.github.rpgclasses.core.definition.DefinitionParser;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.SubclassDef;
import io.github.rpgclasses.core.progression.CatchUp;
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

    /** Da los puntos y los gasta en la rama. */
    private static void fill(ProgressState s, ClassDef def, String branch, int points) {
        for (int i = 0; i < points; i++) {
            s.pointBalance++;
            assertEquals(Result.OK, ProgressionRules.checkSpendBranch(s, def, branch));
            ProgressionRules.spendBranch(s, branch);
        }
    }

    private static void spendSub(ProgressState s) {
        s.pointBalance++;
        assertEquals(Result.OK, ProgressionRules.checkSpendSubclass(s));
        ProgressionRules.spendSubclass(s);
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
        assertEquals(Result.BRANCH_LOCKED, ProgressionRules.checkSpendBranch(s, def, "d"));
        fill(s, def, "a", 9);
        // Reiniciar la clase desbloquea todo.
        ProgressionRules.respecClass(s, 0.33);
        ProgressionRules.chooseClass(s, def);
        assertEquals(Result.OK, ProgressionRules.checkSpendBranch(s, def, "e"));
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
        ProgressionRules.respecSubclass(s, 0.33);
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
    public void fullProgressionUsesFortyPoints() {
        ClassDef def = testClass();
        ProgressState s = new ProgressState();
        assertEquals(Result.NO_CLASS, ProgressionRules.checkSpendBranch(s, def, "a"));
        assertEquals(Result.OK, ProgressionRules.checkChooseClass(s, def));
        ProgressionRules.chooseClass(s, def);
        assertEquals(Result.ALREADY_HAS_CLASS, ProgressionRules.checkChooseClass(s, def));

        fill(s, def, "a", 10);
        assertEquals(Result.BRANCH_FULL, ProgressionRules.checkSpendBranch(s, def, "a"));
        assertEquals(Result.UNKNOWN_BRANCH, ProgressionRules.checkSpendBranch(s, def, "zzz"));
        assertEquals(Result.NO_POINTS, ProgressionRules.checkSpendBranch(s, def, "b"));
        assertEquals(Result.SUBCLASS_LOCKED, ProgressionRules.checkChooseSubclass(s, def, testSub("t:s1")));

        fill(s, def, "b", 5); // 15 de base
        assertEquals(Result.UNKNOWN_SUBCLASS, ProgressionRules.checkChooseSubclass(s, def, testSub("t:other")));
        assertEquals(Result.NO_SUBCLASS, ProgressionRules.checkSpendSubclass(s));
        ProgressionRules.chooseSubclass(s, testSub("t:s1"));
        spendSub(s);
        spendSub(s);
        s.pointBalance++;
        assertEquals(Result.SUBCLASS_CAP, ProgressionRules.checkSpendSubclass(s));
        s.pointBalance--;
        assertEquals(17, s.totalLevel());

        fill(s, def, "b", 5);
        fill(s, def, "c", 10);
        while (s.subclassLevel < ProgressionRules.subclassCap(s.basePoints())) spendSub(s);
        assertEquals(40, s.totalLevel());
        assertEquals(ProgressionRules.MAX_POINTS, s.ownedPoints());
        assertTrue(ProgressionRules.isMaxed(s));
    }

    private static final CatchUp.Rules BALANCE = new CatchUp.Rules(3, 0.03, 0.5, 0.03, 0.5, 2);

    @Test
    public void catchUpIsSymmetricOutsideTheMargin() {
        // Media de los demás: 20.
        java.util.List<Integer> others = java.util.List.of(18, 22);
        assertEquals(0, CatchUp.discount(20, others, BALANCE), EPS);
        assertEquals(0, CatchUp.discount(17, others, BALANCE), EPS);   // justo en el margen
        assertEquals(0, CatchUp.discount(23, others, BALANCE), EPS);
        assertEquals(0.06, CatchUp.discount(15, others, BALANCE), EPS); // 5 por detrás → cuentan 2
        assertEquals(-0.06, CatchUp.discount(25, others, BALANCE), EPS); // 5 por delante → cuentan 2
        // Topes.
        assertEquals(0.5, CatchUp.discount(0, others, BALANCE), EPS);
        assertEquals(-0.5, CatchUp.discount(40, java.util.List.of(0, 0), BALANCE), EPS);
        // Topes distintos a cada lado.
        CatchUp.Rules soft = new CatchUp.Rules(0, 0.03, 0.5, 0.02, 0.3, 1);
        assertEquals(-0.3, CatchUp.discount(40, java.util.List.of(0), soft), EPS);
        assertEquals(-0.2, CatchUp.discount(10, java.util.List.of(0), soft), EPS);
        // Sin recargo si se configura a 0.
        assertEquals(0, CatchUp.discount(40, others, new CatchUp.Rules(3, 0.03, 0.5, 0, 0.5, 2)), EPS);
    }

    @Test
    public void catchUpNeedsEnoughActivePlayers() {
        assertEquals(0, CatchUp.discount(0, java.util.List.of(), BALANCE), EPS);
        assertEquals(0, CatchUp.discount(0, java.util.List.of(30), BALANCE), EPS); // 1 activo y hacen falta 2
        assertEquals(0.5, CatchUp.discount(0, java.util.List.of(30, 30), BALANCE), EPS);
        assertEquals(0, CatchUp.discount(0, java.util.List.of(), new CatchUp.Rules(3, 0.03, 0.5, 0.03, 0.5, 0)), EPS);
    }

    @Test
    public void activePlayTimeOnlyCountsTheWindow() {
        long now = 1000 * CatchUp.HOUR_MS + 30 * 60_000L; // media hora dentro de la hora 1000
        long first = CatchUp.firstHour(now, 48);
        assertEquals(952, first); // 48 horas completas antes de la hora en curso
        java.util.Map<Long, Long> hours = new java.util.TreeMap<>();
        hours.put(951L, 60 * 60_000L);   // fuera de la ventana
        hours.put(952L, 20 * 60_000L);
        hours.put(1000L, 30 * 60_000L);
        assertEquals(50 * 60_000L, CatchUp.playedMs(hours, first));
        hours.put(990L, 10 * 60_000L);
        assertEquals(60 * 60_000L, CatchUp.playedMs(hours, first)); // justo 1 hora: activo
    }

    @Test
    public void pointPriceFollowsEveryPointOwned() {
        ProgressState s = new ProgressState();
        assertEquals(86, ProgressionRules.nextCost(s, 1.0));
        assertEquals(Result.NOT_ENOUGH_XP, ProgressionRules.checkBuyPoint(s, 85, 86));
        assertEquals(Result.OK, ProgressionRules.checkBuyPoint(s, 86, 86));
        ProgressionRules.buyPoint(s);
        assertEquals(1, s.pointBalance);
        assertEquals(XpMath.costOfLevel(2, 1.0), ProgressionRules.nextCost(s, 1.0));
        // Un libro también encarece el siguiente.
        assertEquals(1, ProgressionRules.addPoints(s, 1));
        assertEquals(XpMath.costOfLevel(3, 1.0), ProgressionRules.nextCost(s, 1.0));
        // Descuento de la ayuda a rezagados.
        assertEquals(Math.round(XpMath.costOfLevel(3, 1.0) * 0.5), ProgressionRules.nextCost(s, 1.0, 0.5));
        // Recargo por ventaja (descuento negativo).
        assertEquals(Math.round(XpMath.costOfLevel(3, 1.0) * 1.5), ProgressionRules.nextCost(s, 1.0, -0.5));
        // Tope de 40: ni se compra ni se suman libros.
        assertEquals(38, ProgressionRules.addPoints(s, 100));
        assertEquals(40, s.ownedPoints());
        assertEquals(Result.POINTS_MAXED, ProgressionRules.checkBuyPoint(s, RICH, 1));
        assertEquals(0, ProgressionRules.addPoints(s, 1));
    }

    @Test
    public void respecIsUnlimitedAndLosesAFraction() {
        ClassDef def = testClass();
        ProgressState s = new ProgressState();
        ProgressionRules.chooseClass(s, def);
        fill(s, def, "a", 10);
        fill(s, def, "b", 10);
        ProgressionRules.chooseSubclass(s, testSub("t:s1"));
        for (int i = 0; i < 5; i++) spendSub(s);
        fill(s, def, "c", 10);
        assertEquals(0, s.pointBalance);

        assertEquals(Result.IN_COMBAT, ProgressionRules.checkRespecSubclass(s, true));
        assertEquals(Result.OK, ProgressionRules.checkRespecSubclass(s, false));
        // 5 niveles de subclase con 33 % de pérdida: vuelven round(3.35) = 3.
        assertEquals(3, ProgressionRules.respecSubclass(s, 0.33));
        assertNull(s.subclassId);
        assertEquals(30, s.totalLevel());
        assertEquals(3, s.pointBalance);

        // Se puede elegir otra subclase y reiniciar de nuevo, sin límite.
        ProgressionRules.chooseSubclass(s, testSub("t:s2"));
        ProgressionRules.spendSubclass(s);
        assertEquals(2, s.pointBalance);

        // Reinicio de clase: 31 puntos gastados -> vuelven round(20.77) = 21, y permite elegir otra clase.
        assertEquals(21, ProgressionRules.respecClass(s, 0.33));
        assertNull(s.classId);
        assertEquals(0, s.totalLevel());
        assertEquals(23, s.pointBalance);
        assertEquals(Result.OK, ProgressionRules.checkChooseClass(s, def));
    }

    @Test
    public void respecWithoutLossGivesEveryPointBack() {
        ClassDef def = testClass();
        ProgressState s = new ProgressState();
        ProgressionRules.chooseClass(s, def);
        fill(s, def, "a", 10);
        fill(s, def, "b", 5);
        ProgressionRules.chooseSubclass(s, testSub("t:s1"));
        spendSub(s);
        spendSub(s);
        assertEquals(2, ProgressionRules.respecSubclass(s, 0.0));
        assertEquals(15, ProgressionRules.respecClass(s, 0.0));
        assertEquals(17, s.pointBalance);
        assertEquals(17, s.ownedPoints());
    }

    @Test
    public void buildIsRebuiltInAValidOrderAndStopsWithThePoints() {
        ClassDef def = testClass();
        ProgressState s = new ProgressState();
        ProgressionRules.chooseClass(s, def);
        fill(s, def, "a", 10);
        fill(s, def, "c", 10);
        ProgressionRules.choosePerk(s, "c", "p1");
        ProgressionRules.chooseSubclass(s, testSub("t:s1"));
        for (int i = 0; i < 5; i++) spendSub(s);
        io.github.rpgclasses.core.progression.SavedBuild build = io.github.rpgclasses.core.progression.SavedBuild.of(s);
        assertEquals(25, build.totalPoints());

        // Con todos los puntos, la build queda igual.
        ProgressState t = new ProgressState();
        ProgressionRules.chooseClass(t, def);
        t.pointBalance = 25;
        assertEquals(25, io.github.rpgclasses.core.progression.BuildRules.apply(t, def, testSub("t:s1"), build));
        assertEquals(10, t.pointsIn("a"));
        assertEquals(10, t.pointsIn("c"));
        assertEquals("p1", t.perkIn("c"));
        assertEquals("t:s1", t.subclassId);
        assertEquals(5, t.subclassLevel);
        assertEquals(0, t.pointBalance);

        // Con menos, se reparte por vueltas y se para sin saltarse reglas.
        ProgressState u = new ProgressState();
        ProgressionRules.chooseClass(u, def);
        u.pointBalance = 12;
        assertEquals(12, io.github.rpgclasses.core.progression.BuildRules.apply(u, def, testSub("t:s1"), build));
        assertEquals(6, u.pointsIn("a"));
        assertEquals(6, u.pointsIn("c"));
        assertNull(u.perkIn("c"));
        assertNull(u.subclassId);
    }

    @Test
    public void suspendedClassCannotSpend() {
        ProgressState s = new ProgressState();
        s.classId = "t:removed";
        assertEquals(Result.UNKNOWN_CLASS, ProgressionRules.checkSpendBranch(s, null, "a"));
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
