package io.github.ironrituals.block;

import io.github.ironrituals.config.ServerConfig;
import io.github.ironrituals.item.RitualTomeItem;
import io.github.ironrituals.ritual.Allies;
import io.github.ironrituals.ritual.CircleValidator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
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
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Núcleo del Ritual (plan, 3.B).
 * <ul>
 *   <li>Clic derecho con un Libro de Ritual: lo coloca en la ranura.</li>
 *   <li>Shift + clic derecho con la mano vacía: retira el libro.</li>
 *   <li>Clic derecho con el libro colocado: activa el núcleo. Mientras no exista el motor de rituales (fase 2),
 *       la activación hace un diagnóstico: nivel del círculo, velas que faltan y componentes en el área.</li>
 * </ul>
 */
public class RitualCoreBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 4, 15),
            Block.box(3, 4, 3, 13, 10, 13),
            Block.box(2, 10, 2, 14, 13, 14));
    private static final DustParticleOptions MISSING_DUST = new DustParticleOptions(new Vector3f(0.9F, 0.1F, 0.15F), 1.2F);
    private static final DustParticleOptions UNLIT_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.75F, 0.1F), 1.2F);

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

        if (player.isShiftKeyDown() && held.isEmpty()) {
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

        if (player instanceof ServerPlayer sp) diagnose((ServerLevel) level, pos, core, sp);
        return InteractionResult.CONSUME;
    }

    /** Diagnóstico de la fase 1: informa del círculo y del área central y marca las velas problemáticas. */
    private static void diagnose(ServerLevel level, BlockPos pos, RitualCoreBlockEntity core, ServerPlayer player) {
        CircleValidator.Report report = CircleValidator.inspect(level, pos);

        Component ritual = RitualTomeItem.ritualName(core.getBook());
        player.sendSystemMessage(Component.translatable("message.ironrituals.core.header", ritual).withStyle(ChatFormatting.LIGHT_PURPLE));

        if (report.level() > 0) {
            player.sendSystemMessage(Component.translatable("message.ironrituals.core.circle_level", report.level()).withStyle(ChatFormatting.GREEN));
        } else {
            player.sendSystemMessage(Component.translatable("message.ironrituals.core.circle_none").withStyle(ChatFormatting.RED));
        }
        if (report.problems() > 0 && report.level() < 3) {
            player.sendSystemMessage(Component.translatable("message.ironrituals.core.next_ring",
                    report.level() + 1, report.missing().size(), report.unlit().size()).withStyle(ChatFormatting.GRAY));
        }
        for (BlockPos p : report.missing()) {
            level.sendParticles(player, MISSING_DUST, true, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 8, 0.15, 0.3, 0.15, 0);
        }
        for (BlockPos p : report.unlit()) {
            level.sendParticles(player, UNLIT_DUST, true, p.getX() + 0.5, p.getY() + 0.7, p.getZ() + 0.5, 8, 0.1, 0.2, 0.1, 0);
        }
        for (BlockPos p : report.candlesUsed(pos)) {
            level.sendParticles(player, ParticleTypes.ENCHANT, true, p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, 4, 0.1, 0.2, 0.1, 0.5);
        }

        Map<String, Integer> components = new LinkedHashMap<>();
        Map<String, Component> names = new LinkedHashMap<>();
        for (ItemEntity e : core.itemsInCentralArea()) {
            ItemStack s = e.getItem();
            String key = s.getDescriptionId();
            components.merge(key, s.getCount(), Integer::sum);
            names.putIfAbsent(key, s.getHoverName());
        }
        if (components.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.ironrituals.core.no_components").withStyle(ChatFormatting.GRAY));
        } else {
            MutableComponent line = Component.translatable("message.ironrituals.core.components").withStyle(ChatFormatting.GRAY);
            boolean first = true;
            for (Map.Entry<String, Integer> entry : components.entrySet()) {
                line.append(first ? " " : ", ");
                line.append(Component.literal(entry.getValue() + "× ").append(names.get(entry.getKey())).withStyle(ChatFormatting.WHITE));
                first = false;
            }
            player.sendSystemMessage(line);
        }
        player.sendSystemMessage(Component.translatable("message.ironrituals.core.engine_pending").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, report.level() > 0 ? 1.2F : 0.6F);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof RitualCoreBlockEntity core && core.hasBook()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, core.removeBook());
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
