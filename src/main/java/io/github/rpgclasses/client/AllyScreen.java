package io.github.rpgclasses.client;

import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pantalla de aliados: jugadores conectados y aliados desconectados, con un botón para marcar o quitar.
 * Quien no es aliado cuenta como enemigo. La verdad está en el servidor; esta pantalla se refresca al sincronizar.
 */
public final class AllyScreen extends Screen {
    private static final int ROW = 22;
    private static final int TOP = 48;

    private record Row(UUID id, String name, boolean online) {}

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private int page;
    private int perPage = 1;

    public AllyScreen(Screen parent) {
        super(Component.translatable("ui.rites_and_paths.ally.title"));
        this.parent = parent;
    }

    public void onDataChanged() {
        rebuildWidgets();
    }

    private void collect() {
        rows.clear();
        Minecraft mc = Minecraft.getInstance();
        UUID self = mc.player == null ? null : mc.player.getUUID();
        Map<UUID, Row> byId = new LinkedHashMap<>();
        if (mc.getConnection() != null) {
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                UUID id = info.getProfile().getId();
                if (id == null || id.equals(self)) continue;
                byId.put(id, new Row(id, info.getProfile().getName(), true));
            }
        }
        for (Packets.SyncAllies.Entry e : ClientHandlers.ALLIES) byId.putIfAbsent(e.id(), new Row(e.id(), e.name(), false));
        rows.addAll(byId.values());
        rows.sort(Comparator.comparing((Row r) -> !ClientHandlers.isAlly(r.id()))
                .thenComparing(r -> !r.online())
                .thenComparing(r -> r.name().toLowerCase(java.util.Locale.ROOT)));
    }

    @Override
    protected void init() {
        collect();
        perPage = Math.max(1, (height - TOP - 40) / ROW);
        int pages = Math.max(1, (rows.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        int right = width / 2 + 150;
        int y = TOP;
        for (int i = page * perPage; i < Math.min(rows.size(), (page + 1) * perPage); i++) {
            Row r = rows.get(i);
            boolean ally = ClientHandlers.isAlly(r.id());
            addRenderableWidget(Button.builder(Component.translatable(ally ? "ui.rites_and_paths.ally.remove" : "ui.rites_and_paths.ally.add"),
                    b -> Net.CHANNEL.sendToServer(new Packets.SetAlly(r.id(), !ally))).bounds(right - 90, y, 90, 20).build());
            y += ROW;
        }
        int bottom = height - 28;
        Button prev = Button.builder(Component.literal("<"), b -> {
            page--;
            rebuildWidgets();
        }).bounds(width / 2 - 150, bottom, 20, 20).build();
        prev.active = page > 0;
        addRenderableWidget(prev);
        Button next = Button.builder(Component.literal(">"), b -> {
            page++;
            rebuildWidgets();
        }).bounds(width / 2 - 126, bottom, 20, 20).build();
        next.active = page < pages - 1;
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(Component.translatable("ui.rites_and_paths.close"), b -> onClose())
                .bounds(width / 2 + 50, bottom, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("ui.rites_and_paths.ally.help"), width / 2, 24, 0xAAAAAA);
        int left = width / 2 - 150;
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("ui.rites_and_paths.ally.empty"), width / 2, TOP + 6, 0x888888);
        }
        int y = TOP;
        for (int i = page * perPage; i < Math.min(rows.size(), (page + 1) * perPage); i++) {
            Row r = rows.get(i);
            boolean ally = ClientHandlers.isAlly(r.id());
            Component name = Component.literal(r.name()).withStyle(ally ? ChatFormatting.GREEN : ChatFormatting.WHITE);
            g.drawString(font, name, left, y + 2, 0xFFFFFF);
            Component status = Component.translatable(ally ? "ui.rites_and_paths.ally.status_ally" : "ui.rites_and_paths.ally.status_enemy")
                    .withStyle(ally ? ChatFormatting.DARK_GREEN : ChatFormatting.RED);
            if (!r.online()) status = Component.empty().append(status).append(Component.translatable("ui.rites_and_paths.ally.offline")
                    .withStyle(ChatFormatting.GRAY));
            if (ClientHandlers.isAlliedBy(r.id())) status = Component.empty().append(status)
                    .append(Component.translatable("ui.rites_and_paths.ally.allied_by").withStyle(ChatFormatting.AQUA));
            g.drawString(font, status, left, y + 12, 0xFFFFFF);
            y += ROW;
        }
        int pages = Math.max(1, (rows.size() + perPage - 1) / perPage);
        g.drawString(font, Component.translatable("ui.rites_and_paths.ally.count", ClientHandlers.ALLIES.size(), page + 1, pages),
                width / 2 - 100, height - 22, 0xAAAAAA);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
