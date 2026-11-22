package io.github.rpgclasses.core;

import static org.junit.Assert.assertEquals;

import java.util.function.DoubleUnaryOperator;
import org.junit.Test;

public class ArmorSolveTest {
    private static final double EPS = 1e-6;

    /** Vanilla's curve for a given armor and toughness. */
    private static DoubleUnaryOperator vanilla(double armor, double toughness) {
        return d -> {
            double f = 2.0 + toughness / 4.0;
            double eff = Math.min(20.0, Math.max(armor / 5.0, armor - d / f));
            return d * (1.0 - eff / 25.0);
        };
    }

    /** The flat curve of Improved Armor: A / (A + K) with the same big-hit penalty. */
    private static DoubleUnaryOperator flat(double armor, double toughness, double k) {
        return d -> {
            double f = 2.0 + toughness / 4.0;
            double eff = Math.max(armor / 5.0, armor - d / f);
            return d * (1.0 - eff / (eff + k));
        };
    }

    @Test
    public void solvesTheDamageBeforeArmorOnTheVanillaCurve() {
        DoubleUnaryOperator curve = vanilla(15, 0);
        double wanted = curve.applyAsDouble(10);
        assertEquals(10.0, ArmorSolve.damageBeforeArmor(curve, wanted, 100), 1e-5);
    }

    @Test
    public void solvesTheDamageBeforeArmorOnTheFlatCurve() {
        DoubleUnaryOperator curve = flat(20, 8, 22);
        double wanted = curve.applyAsDouble(7.5);
        assertEquals(7.5, ArmorSolve.damageBeforeArmor(curve, wanted, 100), 1e-5);
    }

    @Test
    public void piercingArmorRaisesTheDamageOnBothCurves() {
        for (DoubleUnaryOperator[] pair : new DoubleUnaryOperator[][]{
                {vanilla(15, 4), vanilla(15 * 0.7, 4)},
                {flat(15, 4, 22), flat(15 * 0.7, 4, 22)}}) {
            double actual = pair[0].applyAsDouble(10);
            double wanted = pair[1].applyAsDouble(10);
            double factor = ArmorSolve.ratio(wanted, actual);
            assertEquals(true, factor > 1.0);
            // Applying the factor to the incoming damage makes the full-armor curve give the wanted result.
            assertEquals(wanted, pair[0].applyAsDouble(ArmorSolve.damageBeforeArmor(pair[0], wanted, 100)), 1e-5);
        }
    }

    @Test
    public void ratioIsOneWhenNothingWouldBeTaken() {
        assertEquals(1.0, ArmorSolve.ratio(0, 0), EPS);
        assertEquals(1.0, ArmorSolve.ratio(3, -1), EPS);
        assertEquals(1.5, ArmorSolve.ratio(3, 2), EPS);
    }

    @Test
    public void unreachableTargetSitsAtTheNearestEnd() {
        DoubleUnaryOperator identity = d -> d;
        assertEquals(50.0, ArmorSolve.damageBeforeArmor(identity, 1000, 50), EPS);
        assertEquals(0.0, ArmorSolve.damageBeforeArmor(identity, -5, 50), 1e-6);
    }
}
