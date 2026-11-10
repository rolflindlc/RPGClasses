package io.github.rpgclasses.client;

import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.network.Packets.SyncDefinitions;
import io.github.rpgclasses.network.Packets.SyncPlayerData;
import io.github.rpgclasses.player.PlayerClassData;
import net.minecraft.client.Minecraft;

/** Solo cliente. Se invoca únicamente a través de DistExecutor desde los paquetes. */
public final class ClientHandlers {
    /** Copia local del progreso del jugador, para la interfaz. La verdad está siempre en el servidor. */
    public static final PlayerClassData LOCAL_DATA = new PlayerClassData();

    private static boolean needsSelection;

    /** true mientras el servidor diga que el jugador no tiene clase. */
    public static boolean needsSelection() {
        return needsSelection;
    }

    public static void onDefinitions(SyncDefinitions msg) {
        DefinitionStore.CLIENT.load(msg.classes(), msg.subclasses(), msg.abilities());
        // Tras un /reload, una pantalla abierta se reconstruye con los datos nuevos.
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ClassScreen screen) mc.setScreen(new ClassScreen(screen.isSelecting()));
    }

    public static void onPlayerData(SyncPlayerData msg) {
        LOCAL_DATA.load(msg.data());
        if (LOCAL_DATA.state.classId != null) {
            needsSelection = false;
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ClassScreen screen && screen.isSelecting()) mc.setScreen(null);
        }
    }

    /** El servidor avisa de que el jugador no tiene clase; ClientSetup abre la pantalla en el siguiente tick. */
    public static void onOpenSelection() {
        needsSelection = true;
    }

    /** Al desconectar: nada de un servidor debe quedar para el siguiente. */
    public static void reset() {
        needsSelection = false;
        LOCAL_DATA.load(new net.minecraft.nbt.CompoundTag());
    }

    private ClientHandlers() {}
}
