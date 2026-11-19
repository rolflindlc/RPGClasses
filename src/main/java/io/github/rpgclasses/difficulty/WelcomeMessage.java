package io.github.rpgclasses.difficulty;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import io.github.rpgclasses.config.ServerConfig;

/**
 * Mensaje de bienvenida: se envía una vez a cada jugador la primera vez que entra en un mundo con el mod. Explica por
 * encima cómo funciona y, a quien puede cambiarlos, le recuerda los ajustes de dificultad con un enlace al menú.
 */
public final class WelcomeMessage {
    private static final String KEY_SEEN = "rites_and_paths_welcomed";
    private static final String P = "rites_and_paths.welcome.";

    private WelcomeMessage() {}

    /** Envía el mensaje si el jugador aún no lo ha visto en este mundo. */
    public static void sendOnce(ServerPlayer player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(KEY_SEEN)) return;
        persisted.putBoolean(KEY_SEEN, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        send(player);
    }

    public static void send(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable(P + "title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.translatable(P + "classes", Component.keybind("key.rites_and_paths.open").withStyle(ChatFormatting.YELLOW)));
        if (!ServerConfig.DIFFICULTY_ENABLED.get()) return;
        player.sendSystemMessage(Component.translatable(P + "difficulty"));
        if (DifficultySettings.canConfigure(player.createCommandSourceStack())) {
            Component link = Component.translatable(P + "link").withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE).withStyle(s -> s
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, DifficultyMenu.COMMAND))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(P + "link.hover"))));
            player.sendSystemMessage(Component.translatable(P + "remind").append(" ").append(link));
        }
    }
}
