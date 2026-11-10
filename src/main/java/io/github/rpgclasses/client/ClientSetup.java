package io.github.rpgclasses.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

/** Registro de todo lo que solo existe en el cliente. Se llama desde el mod a través de DistExecutor. */
public final class ClientSetup {
    public static final KeyMapping OPEN_BOOK = new KeyMapping(
            "key.rpgclasses.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.rpgclasses");

    public static final KeyMapping[] ABILITY_KEYS = {
            new KeyMapping("key.rpgclasses.ability_1", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.categories.rpgclasses"),
            new KeyMapping("key.rpgclasses.ability_2", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.rpgclasses"),
            new KeyMapping("key.rpgclasses.ability_3", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.rpgclasses"),
    };

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.register(OPEN_BOOK);
            for (KeyMapping key : ABILITY_KEYS) event.register(key);
        });
        modBus.addListener((RegisterGuiOverlaysEvent event) -> event.registerAboveAll("abilities", AbilityHud::render));
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
        for (int i = 0; i < ABILITY_KEYS.length; i++) {
            while (ABILITY_KEYS[i].consumeClick()) {
                if (mc.screen == null) Net.CHANNEL.sendToServer(new Packets.UseAbility(i));
            }
        }
        while (OPEN_BOOK.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new ClassScreen(false));
        }
    }

    private ClientSetup() {}
}
