package io.github.rpgclasses.druid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Altar de Carne (Escultor): con un Recipiente de Carne completo encima, clic derecho crea la quimera ({@link Sculptor}). */
public class FleshAltarBlock extends Block {
    public FleshAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) Sculptor.tryCreate(sp, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
