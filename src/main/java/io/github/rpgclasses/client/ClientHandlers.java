package io.github.rpgclasses.client;

import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.network.Packets.SyncDefinitions;
import io.github.rpgclasses.network.Packets.SyncPlayerData;
import io.github.rpgclasses.player.PlayerClassData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Solo cliente. Se invoca únicamente a través de DistExecutor desde los paquetes. */
public final class ClientHandlers {
    /** Copia local del progreso del jugador, para la interfaz. La verdad está siempre en el servidor. */
    public static final PlayerClassData LOCAL_DATA = new PlayerClassData();

    public static void onDefinitions(SyncDefinitions msg) {
        DefinitionStore.CLIENT.load(msg.classes(), msg.subclasses());
    }

    public static void onPlayerData(SyncPlayerData msg) {
        LOCAL_DATA.load(msg.data());
    }

    /**
     * Provisional (fase 1): lista de clases en el chat, cada una con clic para elegirla.
     * La fase 3 sustituye esto por ClassSelectionScreen con descripciones y datos exactos.
     */
    public static void onOpenSelection() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        player.displayClientMessage(Component.translatable("msg.rpgclasses.choose_prompt").withStyle(ChatFormatting.GOLD), false);
        for (String id : DefinitionStore.CLIENT.classIds()) {
            MutableComponent line = Component.literal(" > ").append(Component.translatable(Ids.langKey("class", id, "name")));
            line.withStyle(style -> style
                    .withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clase elegir " + id)));
            player.displayClientMessage(line, false);
        }
    }

    private ClientHandlers() {}
}
