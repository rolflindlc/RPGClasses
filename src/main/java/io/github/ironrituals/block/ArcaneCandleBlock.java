package io.github.ironrituals.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Vela Arcana (plan, 3.A). Una vela por bloque; cada nivel de desgaste es un bloque distinto ({@code uses} = usos
 * que le quedan). Está en el tag {@code minecraft:candles} para que el mechero y la carga ígnea vanilla la
 * enciendan; los proyectiles en llamas (bolas de fuego, conjuros de fuego) la encienden vía
 * {@link AbstractCandleBlock#onProjectileHit}. Clic derecho con la mano vacía la apaga.
 */
public class ArcaneCandleBlock extends AbstractCandleBlock implements SimpleWaterloggedBlock {
    public static final BooleanProperty LIT = AbstractCandleBlock.LIT;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private final int uses;
    private final int waxHeight;
    @Nullable private final Supplier<? extends Block> nextStage;
    private final VoxelShape shape;
    private final List<Vec3> flameOffset;

    /**
     * @param uses      usos que le quedan a esta vela (3, 2 o 1)
     * @param waxHeight altura de la cera en píxeles
     * @param nextStage bloque al que pasa tras un ritual; {@code null} si desaparece
     */
    public ArcaneCandleBlock(Properties props, int uses, int waxHeight, @Nullable Supplier<? extends Block> nextStage) {
        super(props);
        this.uses = uses;
        this.waxHeight = waxHeight;
        this.nextStage = nextStage;
        this.shape = Block.box(6, 0, 6, 10, waxHeight + 1, 10);
        this.flameOffset = List.of(new Vec3(0.5, (waxHeight + 2) / 16.0, 0.5));
        registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(WATERLOGGED, false));
    }

    public static int lightLevel(BlockState state) {
        return state.getValue(LIT) ? 7 : 0;
    }

    public int uses() {
        return uses;
    }

    public static boolean isLitCandle(BlockState state) {
        return state.getBlock() instanceof ArcaneCandleBlock && state.getValue(LIT);
    }

    /**
     * Gasta un uso de la vela tras un ritual completado: se apaga y pasa al siguiente estado de desgaste,
     * o desaparece si era la de 1 uso.
     */
    public void consumeUse(Level level, BlockPos pos, BlockState state) {
        if (nextStage == null) {
            level.removeBlock(pos, false);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 6, 0.1, 0.1, 0.1, 0.01);
            }
        } else {
            BlockState next = nextStage.get().defaultBlockState()
                    .setValue(LIT, false)
                    .setValue(WATERLOGGED, state.getValue(WATERLOGGED));
            level.setBlock(pos, next, Block.UPDATE_ALL);
        }
        level.playSound(null, pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.8F);
    }

    @Override
    protected Iterable<Vec3> getParticleOffsets(BlockState state) {
        return flameOffset;
    }

    @Override
    protected boolean canBeLit(BlockState state) {
        return !state.getValue(WATERLOGGED) && super.canBeLit(state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getAbilities().mayBuild && player.getItemInHand(hand).isEmpty() && state.getValue(LIT)) {
            extinguish(player, state, level, pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, WATERLOGGED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        FluidState fluid = ctx.getLevel().getFluidState(ctx.getClickedPos());
        return defaultBlockState().setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        if (!state.getValue(WATERLOGGED) && fluid.getType() == Fluids.WATER) {
            BlockState next = state.setValue(WATERLOGGED, true);
            if (state.getValue(LIT)) {
                extinguish(null, next, level, pos);
            } else {
                level.setBlock(pos, next, Block.UPDATE_ALL);
            }
            level.scheduleTick(pos, fluid.getType(), fluid.getType().getTickDelay(level));
            return true;
        }
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return dir == Direction.DOWN && !state.canSurvive(level, pos)
                ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    public int waxHeight() {
        return waxHeight;
    }
}
