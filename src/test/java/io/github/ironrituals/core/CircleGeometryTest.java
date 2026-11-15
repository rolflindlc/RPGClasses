package io.github.ironrituals.core;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class CircleGeometryTest {

    @Test
    public void candlesPerRingMatchThePlan() {
        assertEquals(8, CircleGeometry.ringOffsets(2).size());
        assertEquals(16, CircleGeometry.ringOffsets(4).size());
        assertEquals(24, CircleGeometry.ringOffsets(6).size());
    }

    @Test
    public void totalCandlesMatchThePlan() {
        assertEquals(8, CircleGeometry.candlesRequired(1));
        assertEquals(24, CircleGeometry.candlesRequired(2));
        assertEquals(48, CircleGeometry.candlesRequired(3));
    }

    @Test
    public void offsetsLieOnThePerimeterEveryTwoBlocks() {
        for (int r = 2; r <= 6; r += 2) {
            List<int[]> offsets = CircleGeometry.ringOffsets(r);
            Set<Long> unique = new HashSet<>();
            for (int[] o : offsets) {
                assertEquals(r, Math.max(Math.abs(o[0]), Math.abs(o[1])));
                assertEquals(0, Math.floorMod(o[0], 2));
                assertEquals(0, Math.floorMod(o[1], 2));
                unique.add(((long) o[0] << 32) ^ (o[1] & 0xffffffffL));
            }
            assertEquals(offsets.size(), unique.size());
        }
    }

    @Test
    public void ringsDoNotOverlap() {
        Set<Long> all = new HashSet<>();
        int count = 0;
        for (int l = 1; l <= 3; l++) {
            for (int[] o : CircleGeometry.ringOffsets(CircleGeometry.ringRadius(l))) {
                all.add(((long) o[0] << 32) ^ (o[1] & 0xffffffffL));
                count++;
            }
        }
        assertEquals(count, all.size());
    }

    @Test
    public void validLevelStopsAtFirstBrokenRing() {
        assertEquals(0, CircleGeometry.validLevel(new boolean[]{false, true, true}));
        assertEquals(1, CircleGeometry.validLevel(new boolean[]{true, false, true}));
        assertEquals(2, CircleGeometry.validLevel(new boolean[]{true, true, false}));
        assertEquals(3, CircleGeometry.validLevel(new boolean[]{true, true, true}));
    }

    @Test(expected = IllegalArgumentException.class)
    public void oddRadiusIsRejected() {
        CircleGeometry.ringOffsets(3);
    }
}
