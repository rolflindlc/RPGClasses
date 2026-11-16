package io.github.ironrituals.item;

import io.github.ironrituals.custody.SealData;
import io.github.ironrituals.custody.Seals;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Sello de Custodia (plan, 3.F). Agachado + clic derecho sobre un contenedor o una puerta: solo quien lo pone y sus
 * aliados pueden abrirlo o romperlo. Quien tiene acceso lo retira agachado con la mano vacía o rompiendo el bloque,
 * y recupera el sello.
 */
public class CustodySealItem extends Item {
    public CustodySealItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        BlockPos clicked = ctx.getClickedPos();
        BlockState state = level.getBlockState(clicked);
        if (player == null) return InteractionResult.PASS;
        if (!Seals.isSealable(level, clicked, state)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.ironrituals.seal.invalid")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;

        BlockPos pos = Seals.anchor(state, clicked);
        SealData data = SealData.get(server);
        if (data.get(pos) != null) {
            player.displayClientMessage(Component.translatable("message.ironrituals.seal.already").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.FAIL;
        }
        BlockPos partner = Seals.chestPartner(state, pos);
        String name = player.getGameProfile().getName();
        data.put(pos, new SealData.Seal(player.getUUID(), name, partner));
        if (partner != null) data.put(partner, new SealData.Seal(player.getUUID(), name, pos));

        if (!player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        server.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 15, 0.3, 0.3, 0.3, 0.5);
        server.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 1.2F);
        player.displayClientMessage(Component.translatable("message.ironrituals.seal.applied").withStyle(ChatFormatting.GOLD), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.ironrituals.custody_seal").withStyle(ChatFormatting.GRAY));
    }
}
