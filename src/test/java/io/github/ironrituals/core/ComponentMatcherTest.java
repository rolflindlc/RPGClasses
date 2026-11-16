package io.github.ironrituals.core;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ComponentMatcherTest {
    // Pilas: 0 = 5 oro, 1 = 3 girasoles, 2 = 1 perla divina, 3 = 4 oro más
    private final String[] items = {"gold", "sunflower", "pearl", "gold"};
    private final int[] counts = {5, 3, 1, 4};

    private ComponentMatcher.Requirement req(String item, int n) {
        return new ComponentMatcher.Requirement(n, i -> items[i].equals(item));
    }

    @Test
    public void allocatesAcrossStacks() {
        int[] taken = ComponentMatcher.allocate(counts, List.of(req("gold", 7), req("sunflower", 3), req("pearl", 1)));
        assertNotNull(taken);
        assertEquals(7, taken[0] + taken[3]);
        assertEquals(3, taken[1]);
        assertEquals(1, taken[2]);
    }

    @Test
    public void failsWhenShort() {
        assertNull(ComponentMatcher.allocate(counts, List.of(req("gold", 10))));
        assertNull(ComponentMatcher.allocate(counts, List.of(req("pearl", 2))));
    }

    @Test
    public void specificRequirementsGoFirst() {
        // "cualquier cosa" (acepta todo) no debe comerse la única perla antes del requisito de perla.
        ComponentMatcher.Requirement any = new ComponentMatcher.Requirement(12, i -> true);
        int[] taken = ComponentMatcher.allocate(counts, List.of(any, req("pearl", 1)));
        assertNotNull(taken);
        assertEquals(1, taken[2]);
    }

    @Test
    public void subtractLeavesTheRestForCatalysts() {
        int[] taken = ComponentMatcher.allocate(counts, List.of(req("gold", 5)));
        int[] rest = ComponentMatcher.subtract(counts, taken);
        assertNotNull(ComponentMatcher.allocate(rest, List.of(req("gold", 4))));
        assertNull(ComponentMatcher.allocate(rest, List.of(req("gold", 5))));
    }

    @Test
    public void ritualMath() {
        assertEquals(300, RitualMath.channelTicks(1, 10, 5));
        assertEquals(500, RitualMath.channelTicks(3, 10, 5));
        assertEquals(1.0, RitualMath.durationMultiplier(1.0, 1.5), 1e-9);
        assertEquals(1.25, RitualMath.durationMultiplier(1.5, 1.5), 1e-9);
        assertEquals(1.5, RitualMath.durationMultiplier(4.0, 1.5), 1e-9);
        assertEquals(0.5, RitualMath.durationMultiplier(-3.0, 1.5), 1e-9);
    }
}
