package io.github.ironrituals.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Puntos de entrada solo de cliente llamados desde código común vía {@code DistExecutor}. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openTome(ItemStack stack) {
        Minecraft.getInstance().setScreen(new RitualTomeScreen(stack.copy()));
    }
}
