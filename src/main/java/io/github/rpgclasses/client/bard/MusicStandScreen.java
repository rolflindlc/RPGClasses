package io.github.rpgclasses.client.bard;

import io.github.rpgclasses.bard.Instrument;
import io.github.rpgclasses.bard.MusicStandMenu;
import io.github.rpgclasses.client.UiStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Atril del Bardo: 12 huecos de partitura (dorados los usables, grises los que superan tus ranuras) y el inventario. */
public class MusicStandScreen extends AbstractContainerScreen<MusicStandMenu> {
    public MusicStandScreen(MusicStandMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = MusicStandMenu.HEIGHT;
        inventoryLabelY = MusicStandMenu.INV_Y - 11;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        UiStyle.panel(g, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
        for (Slot s : menu.slots) {
            int x = leftPos + s.x, y = topPos + s.y;
            boolean sheet = s.index < Instrument.MAX_SLOTS;
            int border = !sheet ? 0xFF202020 : s.index < menu.capacity() ? 0xFFB08A3C : 0xFF555555;
            g.fill(x - 1, y - 1, x + 17, y + 17, border);
            g.fill(x, y, x + 16, y + 16, sheet && s.index >= menu.capacity() ? 0xFF2A2A2A : 0xFF3A3A3A);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, UiStyle.GOLD, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFDDDDDD, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (mouseX >= leftPos && mouseX < leftPos + imageWidth && mouseY >= topPos + 4 && mouseY < topPos + 16) {
            g.renderTooltip(font, font.split(Component.translatable("ui.rpgclasses.stand.help"), 200), mouseX, mouseY);
        }
    }
}
