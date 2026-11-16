package io.github.ironrituals.block;

import io.github.ironrituals.config.ServerConfig;
import io.github.ironrituals.item.RitualTomeItem;
import io.github.ironrituals.ritual.Allies;
import io.github.ironrituals.registry.ModBlockEntities;
import io.github.ironrituals.ritual.RitualRunner;
import io.github.ironrituals.ritual.active.RitualTicker;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;


/**
 * Núcleo del Ritual (plan, 3.B).
 * <ul>
 *   <li>Clic derecho con un Libro de Ritual: lo coloca en la ranura.</li>
 *   <li>Shift + clic derecho con la mano vacía: retira el libro.</li>
 *   <li>Clic derecho con el libro colocado: activa el núcleo y empieza la canalización ({@link RitualRunner}).
 *       Si falta algo, hace un diagnóstico: nivel del círculo, velas que faltan y componentes en el área.</li>
 *   <li>Shift + clic derecho del lanzador durante la canalización: la cancela.</li>
 * </ul>
 */
public class RitualCoreBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 4, 15),
            Block.box(3, 4, 3, 13, 10, 13),
            Block.box(2, 10, 2, 14, 13, 14));

    public RitualCoreBlock(Properties props) {
        super(props);
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RitualCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.RITUAL_CORE.get(), RitualCoreBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player p && level.getBlockEntity(pos) instanceof RitualCoreBlockEntity core) {
            core.setOwner(p.getUUID(), p.getGameProfile().getName());
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof RitualCoreBlockEntity core)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

        if (ServerConfig.CORE_OWNER_ONLY.get() && !Allies.canAccess(player, core.getOwner(), core.getOwnerName())) {
            player.displayClientMessage(Component.translatable("message.ironrituals.core.not_yours", core.getOwnerName())
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }

        ItemStack held = player.getItemInHand(hand);

        if (held.getItem() instanceof RitualTomeItem) {
            if (core.hasBook()) {
                player.displayClientMessage(Component.translatable("message.ironrituals.core.occupied"), true);
                return InteractionResult.CONSUME;
            }
            if (core.insertBook(held)) {
                if (!player.getAbilities().instabuild) held.shrink(1);
                level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.CONSUME;
        }

        if (player.isShiftKeyDown() && held.isEmpty() && !core.isChanneling()) {
            if (core.hasBook()) {
                ItemStack book = core.removeBook();
                if (!player.addItem(book)) player.drop(book, false);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.CONSUME;
        }

        if (!core.hasBook()) {
            player.displayClientMessage(Component.translatable("message.ironrituals.core.no_book"), true);
            return InteractionResult.CONSUME;
        }

        if (player instanceof ServerPlayer sp) RitualRunner.activate((ServerLevel) level, pos, core, sp);
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof RitualCoreBlockEntity core) {
            if (level instanceof ServerLevel sl) {
                RitualRunner.interrupt(sl, pos, core, "core_broken");
                RitualTicker.endAtCore(sl.getServer(), sl.dimension(), pos);
            }
            if (core.hasBook()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, core.removeBook());
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
