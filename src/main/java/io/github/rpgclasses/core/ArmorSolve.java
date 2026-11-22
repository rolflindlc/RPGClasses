package io.github.rpgclasses.core;

import java.util.function.DoubleUnaryOperator;

/**
 * Pure maths for "ignore part of the target's armor" effects. It takes the armor curve as a function, so the
 * same code works with vanilla's curve and with the flat curve of Improved Armor. No Minecraft classes.
 */
public final class ArmorSolve {
    private ArmorSolve() {
    }

    /**
     * Damage to deal before armor so that the target ends up taking {@code wanted} after it.
     * {@code afterArmor} must be increasing. The result is found by bisection in {@code [0, upperBound]};
     * if {@code wanted} is out of reach, the result sits at the nearest end.
     */
    public static double damageBeforeArmor(DoubleUnaryOperator afterArmor, double wanted, double upperBound) {
        double lo = 0;
        double hi = upperBound;
        for (int i = 0; i < 30; i++) {
            double mid = (lo + hi) / 2;
            if (afterArmor.applyAsDouble(mid) < wanted) lo = mid;
            else hi = mid;
        }
        return hi;
    }

    /** Factor that turns the damage actually taken into the damage wanted; 1 when nothing would be taken. */
    public static double ratio(double wanted, double actual) {
        return actual <= 0 ? 1 : wanted / actual;
    }
}
