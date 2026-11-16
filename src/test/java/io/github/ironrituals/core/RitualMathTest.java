package io.github.ironrituals.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RitualMathTest {

    @Test
    public void ticksUntilAlwaysMovesForward() {
        assertEquals(0, RitualMath.ticksUntil(0, 0));
        assertEquals(13000, RitualMath.ticksUntil(0, 13000));
        assertEquals(24000 - 1000, RitualMath.ticksUntil(1000, 0));
        assertEquals(1000, RitualMath.ticksUntil(24000L * 5 + 12000, 13000));
        assertEquals(23000, RitualMath.ticksUntil(24000L * 3 + 14000, 13000));
    }

    @Test
    public void forgeLevelsHaveNoGaps() {
        int[] t = {500, 1600, Integer.MAX_VALUE};
        assertEquals(0, RitualMath.forgeLevel(0, t));
        assertEquals(1, RitualMath.forgeLevel(1, t));
        assertEquals(1, RitualMath.forgeLevel(500, t));
        assertEquals(2, RitualMath.forgeLevel(501, t));
        assertEquals(2, RitualMath.forgeLevel(1600, t));
        assertEquals(3, RitualMath.forgeLevel(1601, t));
        assertEquals(3, RitualMath.forgeLevel(2031, t));
        assertEquals(-1, RitualMath.forgeLevel(2000, 500, 1600));
    }

    @Test
    public void durationMultiplierIsCapped() {
        assertEquals(1.0, RitualMath.durationMultiplier(1.0, 1.5), 1e-9);
        assertEquals(1.5, RitualMath.durationMultiplier(3.0, 1.5), 1e-9);
        assertEquals(0.5, RitualMath.durationMultiplier(-5.0, 1.5), 1e-9);
    }
}
