package io.github.rpgclasses.client;

import io.github.rpgclasses.content.BondBook;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * Libro de Vínculos (Maestro de Bestias). Izquierda: mascotas vinculadas con su imagen (huevo de aparición de su especie),
 * nombre y estadísticas. Derecha: destino de la Salvaguarda al morir (aparición o uno de tres puntos guardados) y
 * mascotas cercanas sin vincular. El servidor es la verdad: cada acción vuelve con el contenido actualizado.
 */
public final class BondBookScreen extends Screen {
    private static final int TOP = 30;
    private static final int ROW = 24;
    private static final int POINT_ROW = 34;
    private static final int POINTS_Y = TOP + 38;
    private static final int NEARBY_Y = 176;
    private static final UUID NONE = new UUID(0, 0);

    public BondBookScreen() {
        super(Component.translatable("ui.rites_and_paths.bond.title"));
    }

    public void onDataChanged() {
        rebuildWidgets();
    }

    private static Packets.BondBookSync data() {
        return ClientHandlers.BOND_BOOK;
    }

    private static void send(int kind, UUID pet, int slot) {
        Net.CHANNEL.sendToServer(new Packets.BondBookAction(kind, pet, slot));
    }

    private int leftX() {
        return width / 2 - 185;
    }

    private int rightX() {
        return width / 2 + 5;
    }

    @Override
    protected void init() {
        Packets.BondBookSync d = data();
        if (d == null) return;
        int x0 = leftX(), x1 = rightX();

        // Izquierda: vinculadas. Romper solo si la mascota está cargada (para verla y soltarla).
        for (int i = 0; i < d.bonded().size(); i++) {
            Packets.BookEntry e = d.bonded().get(i);
            Button hold = Button.builder(Component.translatable("ui.rites_and_paths.bond.break"),
                    b -> send(BondBook.ACT_UNBIND, e.id(), 0)).bounds(x0 + 128, TOP + 14 + i * ROW + 3, 52, 18).build();
            hold.active = e.loaded();
            addRenderableWidget(hold);
        }

        // Derecha: destino de la Salvaguarda.
        addRenderableWidget(Button.builder(Component.translatable("ui.rites_and_paths.bond.dest_spawn"),
                b -> send(BondBook.ACT_SELECT, NONE, BondBook.SPAWN)).bounds(x1, TOP + 14, 170, 18).build())
                .active = d.selected() != BondBook.SPAWN;
        for (int i = 0; i < d.points().size(); i++) {
            Packets.BookPoint p = d.points().get(i);
            int y = POINTS_Y + i * POINT_ROW;
            int slot = i;
            Button use = Button.builder(Component.translatable("ui.rites_and_paths.bond.use"),
                    b -> send(BondBook.ACT_SELECT, NONE, slot)).bounds(x1, y + 11, 84, 18).build();
            use.active = p.set() && d.selected() != i;
            addRenderableWidget(use);
            addRenderableWidget(Button.builder(Component.translatable("ui.rites_and_paths.bond.save_here"),
                    b -> send(BondBook.ACT_SAVE, NONE, slot)).bounds(x1 + 86, y + 11, 84, 18).build());
        }

        // Derecha, abajo: mascotas cercanas sin vincular.
        for (int j = 0; j < d.nearby().size(); j++) {
            Packets.BookEntry e = d.nearby().get(j);
            Button link = Button.builder(Component.translatable("ui.rites_and_paths.bond.link"),
                    b -> send(BondBook.ACT_BIND, e.id(), 0)).bounds(x1 + 104, NEARBY_Y + 14 + j * ROW + 3, 66, 18).build();
            link.active = d.bonded().size() < BondBook.MAX_BONDS;
            addRenderableWidget(link);
        }

        addRenderableWidget(Button.builder(Component.translatable("ui.rites_and_paths.close"), b -> onClose())
                .bounds(width / 2 - 50, height - 26, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Packets.BondBookSync d = data();
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        if (d == null) {
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        int x0 = leftX(), x1 = rightX();

        // Izquierda: vinculadas.
        g.drawString(font, Component.translatable("ui.rites_and_paths.bond.bonded", d.bonded().size(), BondBook.MAX_BONDS), x0, TOP, 0xFFFFFF, false);
        if (d.bonded().isEmpty()) {
            g.drawString(font, Component.translatable("ui.rites_and_paths.bond.none"), x0, TOP + 14, 0x888888, false);
        }
        for (int i = 0; i < d.bonded().size(); i++) {
            Packets.BookEntry e = d.bonded().get(i);
            int y = TOP + 14 + i * ROW;
            g.renderItem(iconOf(e.type()), x0 + 2, y + 3);
            g.drawString(font, font.plainSubstrByWidth(e.name(), 100), x0 + 22, y + 1, e.boosted() ? 0x55FF55 : 0xFFFFFF, false);
            Component line = e.loaded()
                    ? Component.translatable("ui.rites_and_paths.bond.stats", fmt(e.health(), "%.0f"), fmt(e.maxHealth(), "%.0f"),
                            fmt(e.attack(), "%.1f"), fmt(e.speed(), "%.2f"))
                    : Component.translatable("ui.rites_and_paths.bond.unloaded");
            g.drawString(font, line, x0 + 22, y + 11, 0xAAAAAA, false);
        }

        // Derecha: destino de la Salvaguarda.
        g.drawString(font, Component.translatable("ui.rites_and_paths.bond.dest"), x1, TOP, 0xFFFFFF, false);
        for (int i = 0; i < d.points().size(); i++) {
            Packets.BookPoint p = d.points().get(i);
            int y = POINTS_Y + i * POINT_ROW;
            Component text = p.set()
                    ? Component.translatable("ui.rites_and_paths.bond.point_line", i + 1, p.x(), p.y(), p.z(), dimName(p.dim()))
                    : Component.translatable("ui.rites_and_paths.bond.point_empty", i + 1);
            g.drawString(font, text, x1, y + 2, d.selected() == i ? 0x55FF55 : 0xAAAAAA, false);
        }

        // Derecha, abajo: cercanas sin vincular.
        g.drawString(font, Component.translatable("ui.rites_and_paths.bond.nearby"), x1, NEARBY_Y, 0xFFFFFF, false);
        if (d.nearby().isEmpty()) {
            g.drawString(font, Component.translatable("ui.rites_and_paths.bond.no_nearby", BondBook.RANGE), x1, NEARBY_Y + 14, 0x888888, false);
        }
        for (int j = 0; j < d.nearby().size(); j++) {
            Packets.BookEntry e = d.nearby().get(j);
            int y = NEARBY_Y + 14 + j * ROW;
            g.renderItem(iconOf(e.type()), x1 + 2, y + 3);
            g.drawString(font, font.plainSubstrByWidth(e.name(), 80), x1 + 22, y + 8, 0xFFFFFF, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Imagen de la mascota: el huevo de aparición de su especie (sirve aunque no esté cargada cerca). */
    private static ItemStack iconOf(String type) {
        ResourceLocation id = ResourceLocation.tryParse(type);
        EntityType<?> entity = id == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(id);
        SpawnEggItem egg = entity == null ? null : SpawnEggItem.byId(entity);
        return egg == null ? Items.BONE.getDefaultInstance() : egg.getDefaultInstance();
    }

    private static String fmt(double value, String pattern) {
        return String.format(java.util.Locale.ROOT, pattern, value);
    }

    private static String dimName(String dim) {
        int colon = dim.indexOf(':');
        return colon < 0 ? dim : dim.substring(colon + 1);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
