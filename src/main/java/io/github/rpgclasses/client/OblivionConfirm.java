package io.github.rpgclasses.client;

import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets.ClassAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

/** Confirmación del Libro de Olvido (cliente). */
public final class OblivionConfirm {
    private OblivionConfirm() {}

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ConfirmScreen(yes -> {
            if (yes) Net.CHANNEL.sendToServer(new ClassAction(ClassAction.Action.USE_OBLIVION, ""));
            mc.setScreen(null);
        }, Component.translatable("ui.rites_and_paths.oblivion.title"), Component.translatable("ui.rites_and_paths.oblivion.text")));
    }
}
