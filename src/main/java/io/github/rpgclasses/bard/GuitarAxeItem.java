package io.github.rpgclasses.bard;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Guitarra-hacha (Épica, objeto único): un hacha de netherita con un 15 % menos de daño (8,5) que el Bardo también
 * toca (instrumento de 5 ranuras). Es un {@link AxeItem}, así que cuenta como arma pesada; el Bardo está exento de
 * ese muro con ella (DamagePipeline). Las demás clases la usan como hacha normal, sin tocarla.
 */
public class GuitarAxeItem extends AxeItem {
    public GuitarAxeItem(Properties props) {
        super(Tiers.NETHERITE, 3.5f, -3.0f, props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !Bard.isBard(player)) return super.use(level, player, hand);
        return BardPlay.use(level, player, hand, player.getItemInHand(hand));
    }

    /** Un Bardo no descorteza troncos con ella: el clic derecho siempre toca. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() != null && Bard.isBard(context.getPlayer())) return InteractionResult.PASS;
        return super.useOn(context);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        BardPlay.inventoryTick(stack, level, entity, slot);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        BardPlay.tooltip(stack, Instrument.GUITAR_AXE, tooltip);
    }
}
