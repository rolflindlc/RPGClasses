package io.github.ironrituals.custody;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.Container;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.jetbrains.annotations.Nullable;

/** Reglas de qué se puede sellar y en qué posición se guarda el sello. */
public final class Seals {
    private Seals() {}

    /** Contenedores (inventario propio) y puertas, trampillas y vallas. */
    public static boolean isSealable(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock) return true;
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && (be instanceof Container || be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent());
    }

    /** Posición donde se guarda el sello: la mitad inferior en las puertas. */
    public static BlockPos anchor(BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof DoorBlock && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return pos.below();
        return pos;
    }

    /** La otra mitad de un cofre doble, si la hay. */
    @Nullable
    public static BlockPos chestPartner(BlockState state, BlockPos pos) {
        if (!(state.getBlock() instanceof ChestBlock) || !state.hasProperty(ChestBlock.TYPE)) return null;
        if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return null;
        Direction d = ChestBlock.getConnectedDirection(state);
        return pos.relative(d);
    }
}
