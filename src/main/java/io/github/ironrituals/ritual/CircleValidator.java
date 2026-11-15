package io.github.ironrituals.ritual;

import io.github.ironrituals.block.ArcaneCandleBlock;
import io.github.ironrituals.core.CircleGeometry;
import io.github.ironrituals.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Valida el círculo arcano alrededor de un núcleo (plan, 2.1): las velas están en el mismo plano Y que el núcleo
 * y un anillo es válido si todas sus velas están colocadas y encendidas.
 */
public final class CircleValidator {
    private CircleValidator() {}

    /** Resultado de inspeccionar un círculo. */
    public record Report(int level, List<BlockPos> missing, List<BlockPos> unlit) {
        /** Posiciones de todas las velas de los anillos 1..{@link #level}, para gastarlas al completar el ritual. */
        public List<BlockPos> candlesUsed(BlockPos core) {
            List<BlockPos> out = new ArrayList<>();
            for (int l = 1; l <= level; l++) out.addAll(ringPositions(core, l));
            return out;
        }

        /** Velas que faltan o están apagadas en el primer anillo incompleto. */
        public int problems() {
            return missing.size() + unlit.size();
        }
    }

    public static Report inspect(Level level, BlockPos core) {
        boolean[] valid = new boolean[CircleGeometry.MAX_LEVEL];
        List<BlockPos> missing = Collections.emptyList();
        List<BlockPos> unlit = Collections.emptyList();
        for (int l = 1; l <= CircleGeometry.MAX_LEVEL; l++) {
            List<BlockPos> ringMissing = new ArrayList<>();
            List<BlockPos> ringUnlit = new ArrayList<>();
            for (BlockPos p : ringPositions(core, l)) {
                BlockState s = level.getBlockState(p);
                if (!s.is(ModTags.ARCANE_CANDLES) || !s.hasProperty(ArcaneCandleBlock.LIT)) {
                    ringMissing.add(p);
                } else if (!s.getValue(ArcaneCandleBlock.LIT)) {
                    ringUnlit.add(p);
                }
            }
            valid[l - 1] = ringMissing.isEmpty() && ringUnlit.isEmpty();
            if (!valid[l - 1]) {
                missing = ringMissing;
                unlit = ringUnlit;
                break;
            }
        }
        return new Report(CircleGeometry.validLevel(valid), missing, unlit);
    }

    /** Posiciones de las velas del anillo propio de un nivel (no incluye los anillos interiores). */
    public static List<BlockPos> ringPositions(BlockPos core, int level) {
        List<int[]> offsets = CircleGeometry.ringOffsets(CircleGeometry.ringRadius(level));
        List<BlockPos> out = new ArrayList<>(offsets.size());
        for (int[] o : offsets) out.add(core.offset(o[0], 0, o[1]));
        return out;
    }
}
