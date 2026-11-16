package io.github.ironrituals.client;

import io.github.ironrituals.IronRituals;
import io.github.ironrituals.ritual.effect.EternalForgeEffect;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = IronRituals.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private ClientForgeEvents() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (EternalForgeEffect.isFireproof(event.getItemStack())) {
            event.getToolTip().add(Component.translatable("tooltip.ironrituals.fireproof").withStyle(ChatFormatting.GOLD));
        }
    }
}
