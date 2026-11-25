package io.github.rpgclasses.client.rogue;

import io.github.rpgclasses.network.DisguisePackets;
import io.github.rpgclasses.network.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;

/**
 * Disfraz (Cambiarrostros): lista de rostros aprendidos, jugadores primero (en dorado) y luego mobs, en páginas de dos
 * columnas. Elegir uno pide al servidor ponérselo; allí se valida todo ({@code rogue.FaceChanger.select}).
 */
public final class FaceScreen extends Screen {
    private static final int ROW = 22, WIDTH = 150, GAP = 6, ROWS = 8, PER_PAGE = ROWS * 2;
    private final DisguisePackets.FaceList list;
    private int page;

    public FaceScreen(DisguisePackets.FaceList list) {
        super(Component.translatable("ui.rites_and_paths.disguise.title"));
        this.list = list;
    }

    private int pages() {
        return Math.max(1, (list.entries().size() + PER_PAGE - 1) / PER_PAGE);
    }

    private int top() {
        int rows = Math.min(ROWS, (Math.min(PER_PAGE, list.entries().size()) + 1) / 2);
        return Math.max(34, height / 2 - (rows + 1) * ROW / 2);
    }

    @Override
    protected void init() {

        int left = width / 2 - WIDTH - GAP / 2;
        int y0 = top();
        int from = page * PER_PAGE;
        int to = Math.min(list.entries().size(), from + PER_PAGE);
        for (int i = from; i < to; i++) {
            DisguisePackets.FaceList.Entry e = list.entries().get(i);
            int slot = i - from;
            int x = left + (slot % 2) * (WIDTH + GAP);
            int y = y0 + (slot / 2) * ROW;
            addRenderableWidget(Button.builder(label(e), b -> {
                Net.CHANNEL.sendToServer(new DisguisePackets.FaceSelect(e.key()));
                onClose();
            }).bounds(x, y, WIDTH, 20).build());
        }
        if (pages() > 1) {
            int y = y0 + ROWS * ROW + 4;
            Button prev = Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(width / 2 - 50, y, 20, 20).build();
            Button next = Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(width / 2 + 30, y, 20, 20).build();
            prev.active = page > 0;
            next.active = page < pages() - 1;
            addRenderableWidget(prev);
            addRenderableWidget(next);
        }
    }

    private static Component label(DisguisePackets.FaceList.Entry e) {
        if (e.player()) return Component.literal(e.value()).withStyle(ChatFormatting.GOLD);
        return EntityType.byString(e.value()).map(EntityType::getDescription).orElse(Component.literal(e.value()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, top() - 16, 0xFFE6C35A);
        super.render(g, mouseX, mouseY, partialTick);
        if (pages() > 1) {
            g.drawCenteredString(font, (page + 1) + " / " + pages(), width / 2, top() + ROWS * ROW + 10, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
