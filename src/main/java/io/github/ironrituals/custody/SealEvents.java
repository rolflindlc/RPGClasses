package io.github.ironrituals.custody;

import io.github.ironrituals.IronRituals;
import io.github.ironrituals.registry.ModItems;
import io.github.ironrituals.ritual.Allies;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/** Protección de los bloques sellados (plan, 4.F.29): apertura, rotura y explosiones. */
@Mod.EventBusSubscriber(modid = IronRituals.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SealEvents {
    private SealEvents() {}

    /** Sello vigente en la posición; borra los que apuntan a un bloque que ya no se puede sellar. */
    @Nullable
    private static SealData.Seal sealAt(ServerLevel level, BlockPos clicked, BlockState state) {
        BlockPos pos = Seals.anchor(state, clicked);
        SealData data = SealData.get(level);
        SealData.Seal seal = data.get(pos);
        if (seal != null && !Seals.isSealable(level, pos, level.getBlockState(pos))) {
            data.remove(pos);
            return null;
        }
        return seal;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos clicked = event.getPos();
        BlockState state = level.getBlockState(clicked);
        SealData.Seal seal = sealAt(level, clicked, state);
        if (seal == null) return;
        Player player = event.getEntity();

        if (!Allies.canAccess(player, seal.owner(), seal.ownerName())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            player.displayClientMessage(Component.translatable("message.ironrituals.seal.locked", seal.ownerName())
                    .withStyle(ChatFormatting.RED), true);
            level.playSound(null, clicked, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0F, 1.0F);
            return;
        }
        // Retirar el sello: agachado y con las dos manos vacías.
        if (player.isShiftKeyDown() && event.getHand() == InteractionHand.MAIN_HAND
                && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
            SealData.get(level).remove(Seals.anchor(state, clicked));
            if (!player.getInventory().add(new ItemStack(ModItems.CUSTODY_SEAL.get()))) {
                player.drop(new ItemStack(ModItems.CUSTODY_SEAL.get()), false);
            }
            level.playSound(null, clicked, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0F, 1.2F);
            player.displayClientMessage(Component.translatable("message.ironrituals.seal.removed").withStyle(ChatFormatting.GOLD), true);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        SealData.Seal seal = sealAt(level, event.getPos(), event.getState());
        if (seal == null) return;
        Player player = event.getPlayer();
        if (!Allies.canAccess(player, seal.owner(), seal.ownerName())) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("message.ironrituals.seal.locked", seal.ownerName())
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        BlockPos pos = Seals.anchor(event.getState(), event.getPos());
        SealData.get(level).remove(pos);
        ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                new ItemStack(ModItems.CUSTODY_SEAL.get()));
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        SealData data = SealData.get(level);
        event.getAffectedBlocks().removeIf(p -> data.get(Seals.anchor(level.getBlockState(p), p)) != null);
    }
}
