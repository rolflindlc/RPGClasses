package io.github.rpgclasses.spin;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.BundleTooltip;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Bandolera de Esferas: guarda hasta {@link Bandolier#CAPACITY} bolas y es lo que se lleva en la mano para lanzarlas
 * (clic izquierdo directo, clic derecho curvo, tecla para cambiar de bola). Se llena y se vacía como un saco: clic derecho
 * con una bola encima la guarda; clic derecho sobre un hueco saca una. En la mano se dibuja la bola elegida girando.
 */
public class BandolierItem extends Item {
    public BandolierItem(Properties properties) {
        super(properties);
    }

    /** La bandolera, cogida con el cursor, se pulsa sobre una ranura. */
    @Override
    public boolean overrideStackedOnOther(ItemStack bandolier, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) return false;
        ItemStack other = slot.getItem();
        if (other.isEmpty()) {
            ItemStack out = Bandolier.removeOne(bandolier);
            if (!out.isEmpty()) {
                player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8f, 0.8f + player.level().getRandom().nextFloat() * 0.4f);
                ItemStack rest = slot.safeInsert(out);
                if (!rest.isEmpty()) Bandolier.add(bandolier, rest);
            }
        } else if (Bandolier.canAdd(bandolier, other)) {
            ItemStack taken = slot.safeTake(1, 1, player);
            if (!taken.isEmpty() && Bandolier.add(bandolier, taken)) {
                player.playSound(SoundEvents.BUNDLE_INSERT, 0.8f, 0.8f + player.level().getRandom().nextFloat() * 0.4f);
            }
        }
        return true;
    }

    /** Con algo en el cursor se pulsa sobre la bandolera (o con el cursor vacío, para sacar una bola). */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack bandolier, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) return false;
        if (other.isEmpty()) {
            ItemStack out = Bandolier.removeOne(bandolier);
            if (!out.isEmpty()) {
                player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8f, 0.8f + player.level().getRandom().nextFloat() * 0.4f);
                access.set(out);
            }
            return true;
        }
        if (Bandolier.canAdd(bandolier, other)) {
            if (Bandolier.add(bandolier, other)) {
                other.shrink(1);
                player.playSound(SoundEvents.BUNDLE_INSERT, 0.8f, 0.8f + player.level().getRandom().nextFloat() * 0.4f);
            }
            return true;
        }
        return false;
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        NonNullList<ItemStack> items = NonNullList.create();
        for (Bandolier.Entry e : Bandolier.entries(stack)) items.add(e.stack);
        if (items.isEmpty()) return Optional.empty();
        return Optional.of(new BundleTooltip(items, Math.min(64, 64 * items.size() / Bandolier.CAPACITY)));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        int n = Bandolier.count(stack);
        if (n == 0) {
            tooltip.add(Component.translatable("tooltip.rites_and_paths.bandolier.empty").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.rites_and_paths.bandolier.count", n, Bandolier.CAPACITY).withStyle(ChatFormatting.GRAY));
            SphereMaterial m = Bandolier.current(stack);
            if (m != null) {
                tooltip.add(Component.translatable("tooltip.rites_and_paths.bandolier.selected",
                        Component.translatable("item.rites_and_paths." + m.itemName())).withStyle(ChatFormatting.GOLD));
            }
        }
        tooltip.add(Component.translatable("tooltip.rites_and_paths.bandolier.controls",
                Component.keybind("key.rites_and_paths.cycle_sphere")).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.rites_and_paths.bandolier.help").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return Bandolier.count(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.min(13, 1 + 12 * Bandolier.count(stack) / Bandolier.CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xC9A24A;
    }

    /** Si la bandolera se destruye en el suelo, suelta sus bolas. */
    @Override
    public void onDestroyed(ItemEntity entity) {
        ItemUtils.onContainerDestroyed(entity, Bandolier.entries(entity.getItem()).stream().map(e -> e.stack));
    }

    /** Lanzar y volver cambian el NBT de la bandolera: no debe repetirse la animación de sacarla. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return io.github.rpgclasses.client.spin.SpinItemRenderer.get();
            }
        });
    }
}
