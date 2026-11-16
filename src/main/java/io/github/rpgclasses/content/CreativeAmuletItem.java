package io.github.rpgclasses.content;

import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * Amuleto del Creativo (solo para pruebas): mientras esté en cualquier hueco del inventario, todos los enfriamientos
 * de clase y de raza son 0 (habilidades con tecla, ICD de pasivas y perks, recargas de cargas). Solo se consigue en la
 * pestaña de creativo o con /give: no tiene receta. Funciona en cualquier modo de juego para poder probar en supervivencia.
 * El enganche está en {@link io.github.rpgclasses.server.AbilityService} (setCooldown y remainingTicks), por donde
 * pasan todos esos enfriamientos.
 */
public final class CreativeAmuletItem extends Item {
    public CreativeAmuletItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.rpgclasses.creative_amulet").withStyle(ChatFormatting.GRAY));
    }

    /** ¿Lleva el amuleto en el inventario (incluidas armadura y mano secundaria)? */
    public static boolean carried(Player player) {
        if (player == null || player instanceof FakePlayer) return false;
        Item amulet = ModBlocks.CREATIVE_AMULET.get();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(amulet)) return true;
        }
        return false;
    }

    /** Al coger el amuleto se borran los enfriamientos que ya estuvieran corriendo (y el HUD deja de mostrarlos). */
    public static final class Events {
        @SubscribeEvent
        public void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                    || player.tickCount % 10 != 0) return;
            PlayerClassData data = PlayerClassDataProvider.get(player);
            if (data == null || data.cooldowns.isEmpty() || !carried(player)) return;
            data.cooldowns.clear();
            Net.syncPlayer(player);
        }
    }
}
