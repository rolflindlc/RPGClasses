package io.github.rpgclasses.content.race;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Altar de Raza: clic derecho muestra el rito en curso y entrega ofrendas y construcciones; agachado + clic derecho
 * invoca al jefe de rito. Cada jugador avanza solo sus propios ritos (ver {@link Rites}).
 */
public class RaceAltarBlock extends Block {
    public RaceAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level.isClientSide() && player instanceof ServerPlayer sp) Rites.onAltar(sp, pos, player.isShiftKeyDown());
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
