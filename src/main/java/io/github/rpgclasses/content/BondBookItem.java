package io.github.rpgclasses.content;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Libro de Vínculos: solo lo abre el Maestro de Bestias. El servidor le envía el contenido y el cliente abre la pantalla. */
public final class BondBookItem extends Item {
    public BondBookItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer sp) || !RangerSubclasses.isBeastMaster(sp)) {
            player.displayClientMessage(Component.translatable("msg.rpgclasses.bond.only_beast_master"), true);
            return InteractionResultHolder.fail(stack);
        }
        BondBook.open(sp);
        return InteractionResultHolder.success(stack);
    }
}
