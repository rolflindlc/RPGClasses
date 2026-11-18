package io.github.rpgclasses.core;

import io.github.rpgclasses.client.tree.TreeLayout;
import io.github.rpgclasses.client.tree.TreePlan;
import io.github.rpgclasses.client.tree.TreeStatus;
import io.github.rpgclasses.core.definition.DefinitionParser;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.SubclassDef;
import io.github.rpgclasses.core.progression.ProgressState;
import io.github.rpgclasses.core.progression.ProgressionRules;
import io.github.rpgclasses.core.progression.XpMath;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Plan del árbol de habilidades: lo que se simula debe coincidir con lo que aceptará el servidor. */
public class TreePlanTest {
    private static final long RICH = 1_000_000;

    private static final String CLASS_JSON = """
            {"branches":[
              {"id":"a","display":{"icon":"minecraft:clock"},"effects":[{"type":"rites_and_paths:mechanic","id":"t:x"}],
               "perks":[{"id":"p1","effects":[]},{"id":"p2","effects":[]}]},
              {"id":"b","effects":[{"type":"rites_and_paths:mechanic","id":"t:x"}]},
              {"id":"c","effects":[{"type":"rites_and_paths:mechanic","id":"t:x"}]},
              {"id":"d","effects":[{"type":"rites_and_paths:mechanic","id":"t:x"}]},
              {"id":"e","effects":[{"type":"rites_and_paths:mechanic","id":"t:x"}]}],
             "milestones":[{"total_level":5,"grants":"t:ab"}],
             "subclasses":["t:s1","t:s2"]}""";

    private final ClassDef def = DefinitionParser.parseClass("t:class", CLASS_JSON);
    private final Map<String, SubclassDef> subs = new HashMap<>();

    public TreePlanTest() {
        for (String id : def.subclasses()) {
            subs.put(id, DefinitionParser.parseSubclass(id,
                    "{\"class\":\"t:class\",\"effects\":[],\"milestones\":[{\"subclass_level\":3,\"grants\":\"t:sa\"}]}"));
        }
    }

    private ProgressState fresh() {
        ProgressState s = new ProgressState();
        ProgressionRules.chooseClass(s, def);
        return s;
    }

    private TreePlan.Context ctx(ProgressState s, long xp) {
        return new TreePlan.Context(s, def, subs::get, xp, 1.0);
    }

    @Test
    public void branchIconIsParsed() {
        assertEquals("minecraft:clock", def.branch("a").icon());
        assertNull(def.branch("b").icon());
    }

    @Test
    public void targetAddsStepsAndCostsLikeTheServer() {
        ProgressState s = fresh();
        TreePlan plan = new TreePlan();
        plan.setBranchTarget(ctx(s, RICH), "a", 4);
        TreePlan.Simulation sim = plan.simulate(ctx(s, RICH));
        assertEquals(4, sim.state.pointsIn("a"));
        assertEquals(XpMath.totalCost(0, 4, 1.0), sim.cost);
        assertEquals(0, s.pointsIn("a")); // el estado real no cambia
        // Bajar el objetivo quita pasos del plan, nunca puntos comprados.
        plan.setBranchTarget(ctx(s, RICH), "a", 1);
        assertEquals(1, plan.simulate(ctx(s, RICH)).state.pointsIn("a"));
    }

    @Test
    public void xpLimitsThePlan() {
        ProgressState s = fresh();
        TreePlan plan = new TreePlan();
        long two = XpMath.totalCost(0, 2, 1.0);
        plan.setBranchTarget(ctx(s, two), "a", 10);
        assertEquals(2, plan.steps().size());
        // Si luego hay menos XP, los pasos que ya no se pueden pagar se saltan.
        assertEquals(1, plan.simulate(ctx(s, two - 1)).state.pointsIn("a"));
    }

    @Test
    public void thirdBranchLocksTheRest() {
        ProgressState s = fresh();
        TreePlan plan = new TreePlan();
        TreePlan.Context c = ctx(s, RICH);
        plan.setBranchTarget(c, "a", 1);
        plan.setBranchTarget(c, "b", 1);
        plan.setBranchTarget(c, "c", 1);
        int before = plan.steps().size();
        plan.setBranchTarget(c, "d", 3);
        assertEquals(before, plan.steps().size());
        TreeLayout layout = new TreeLayout(def, List.of(subs.get("t:s1"), subs.get("t:s2")));
        TreeStatus status = new TreeStatus(layout, c, plan.simulate(c), true);
        assertTrue(status.willLock.contains(3));
        assertTrue(status.willLock.contains(4));
        assertEquals(TreeStatus.Status.WILL_LOCK, status.of(layout.points.get(3).get(0)));
        assertEquals(TreeStatus.Status.PLANNED, status.of(layout.points.get(0).get(0)));
    }

    @Test
    public void perkNeedsAFullBranchAndIsPrunedWithIt() {
        ProgressState s = fresh();
        TreePlan plan = new TreePlan();
        TreePlan.Context c = ctx(s, RICH);
        assertFalse(plan.choosePerk(c, "a", "p1"));
        plan.setBranchTarget(c, "a", 10);
        assertTrue(plan.choosePerk(c, "a", "p1"));
        assertTrue(plan.choosePerk(c, "a", "p2")); // cambiar de perk sustituye el paso
        assertEquals("p2", plan.simulate(c).state.perkIn("a"));
        plan.setBranchTarget(c, "a", 9);
        assertNull(plan.simulate(c).state.perkIn("a"));
    }

    @Test
    public void subclassNeedsBasePointsAndRespectsTheCap() {
        ProgressState s = fresh();
        TreePlan plan = new TreePlan();
        TreePlan.Context c = ctx(s, RICH);
        assertFalse(plan.chooseSubclass(c, "t:s1"));
        plan.setBranchTarget(c, "a", 10);
        plan.setBranchTarget(c, "b", 6);
        assertTrue(plan.chooseSubclass(c, "t:s1"));
        plan.setSubclassTarget(c, 10);
        TreePlan.Simulation sim = plan.simulate(c);
        assertEquals("t:s1", sim.state.subclassId);
        assertEquals(ProgressionRules.subclassCap(16), sim.state.subclassLevel);
        // Cambiar de subclase conserva los niveles planeados.
        assertTrue(plan.chooseSubclass(c, "t:s2"));
        assertEquals("t:s2", plan.simulate(c).state.subclassId);
        assertEquals(ProgressionRules.subclassCap(16), plan.simulate(c).state.subclassLevel);
        // Quitar puntos de base por debajo de 15 se lleva la subclase y sus niveles.
        plan.setBranchTarget(c, "b", 2);
        assertNull(plan.simulate(c).state.subclassId);
    }

    @Test
    public void layoutHasOneArmPerBranchAndSubclass() {
        List<SubclassDef> list = new ArrayList<>();
        for (String id : def.subclasses()) list.add(subs.get(id));
        TreeLayout layout = new TreeLayout(def, list);
        assertEquals(5, layout.keystones.size());
        assertEquals(10, layout.points.get(0).size());
        assertEquals(2, layout.perks.get(0).size());
        assertEquals(0, layout.perks.get(1).size());
        assertEquals(1, layout.gems.size());
        assertNotNull(layout.subHub);
        assertEquals(2, layout.subKeys.size());
        assertEquals(1, layout.subPoints.get(0).get(2).milestones.size());
        assertTrue(layout.baseMaxX < layout.subHub.x);
    }
}
