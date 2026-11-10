package io.github.rpgclasses.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.rpgclasses.core.definition.DefinitionStore;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

/** Registro de todo lo que solo existe en el cliente. Se llama desde el mod a través de DistExecutor. */
public final class ClientSetup {
    public static final KeyMapping OPEN_BOOK = new KeyMapping(
            "key.rpgclasses.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.rpgclasses");

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(OPEN_BOOK));
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientHandlers.reset());
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Sin clase: la pantalla de selección vuelve a abrirse siempre que no haya otra pantalla.
        if (ClientHandlers.needsSelection() && mc.screen == null && !DefinitionStore.CLIENT.classIds().isEmpty()) {
            mc.setScreen(new ClassScreen(true));
            return;
        }
        while (OPEN_BOOK.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new ClassScreen(false));
        }
    }

    private ClientSetup() {}
}
