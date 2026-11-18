package io.github.rpgclasses.client.cleric;

import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Plegaria (Intercesor): lista de aliados por los que rezar, con su distancia. Con la Plegaria activa, el aliado actual
 * va marcado y hay un botón para dejar de rezar. La validación está en el servidor ({@code cleric.Prayer.select}).
 */
public final class PrayerScreen extends Screen {
    private static final int ROW = 22, WIDTH = 200, MAX_ROWS = 8;
    private final Packets.PrayerList list;

    public PrayerScreen(Packets.PrayerList list) {
        super(Component.translatable("ui.rites_and_paths.prayer.title"));
        this.list = list;
    }

    @Override
    protected void init() {
        int x = (width - WIDTH) / 2;
        int rows = Math.min(MAX_ROWS, list.entries().size()) + (list.current() != null ? 1 : 0);
        int y = Math.max(30, height / 2 - rows * ROW / 2);
        for (int i = 0; i < Math.min(MAX_ROWS, list.entries().size()); i++) {
            Packets.PrayerList.Entry e = list.entries().get(i);
            boolean current = e.id().equals(list.current());
            Component label = current ? Component.translatable("ui.rites_and_paths.prayer.current", e.name())
                    : Component.translatable("ui.rites_and_paths.prayer.distance", e.name(), e.distance());
            Button b = Button.builder(label, btn -> {
                Net.CHANNEL.sendToServer(new Packets.PrayerSelect(e.id()));
                onClose();
            }).bounds(x, y, WIDTH, 20).build();
            b.active = !current;
            addRenderableWidget(b);
            y += ROW;
        }
        if (list.current() != null) {
            addRenderableWidget(Button.builder(Component.translatable("ui.rites_and_paths.prayer.stop"), btn -> {
                Net.CHANNEL.sendToServer(new Packets.PrayerSelect(null));
                onClose();
            }).bounds(x, y + 4, WIDTH, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int rows = Math.min(MAX_ROWS, list.entries().size()) + (list.current() != null ? 1 : 0);
        int y = Math.max(30, height / 2 - rows * ROW / 2);
        g.drawCenteredString(font, title, width / 2, y - 16, 0xFFE6C35A);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
