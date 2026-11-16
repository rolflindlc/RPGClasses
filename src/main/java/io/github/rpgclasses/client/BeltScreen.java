package io.github.rpgclasses.client;

import io.github.rpgclasses.content.race.BeltMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Pantalla del cinturón del Porteador: sus huecos con un botón "Abrir" debajo de cada bloque, y el inventario. */
public class BeltScreen extends AbstractContainerScreen<BeltMenu> {
    private final Button[] open = new Button[6];

    public BeltScreen(BeltMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = BeltMenu.HEIGHT;
        inventoryLabelY = BeltMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < menu.capacity(); i++) {
            int slot = i;
            open[i] = addRenderableWidget(Button.builder(Component.translatable("ui.rpgclasses.belt.open"), b -> click(slot))
                    .bounds(leftPos + BeltMenu.slotX(i, menu.capacity()) - 5, topPos + BeltMenu.SLOT_Y + 19, 26, 12).build());
        }
        containerTick();
    }

    private void click(int slot) {
        if (minecraft == null || minecraft.player == null || minecraft.gameMode == null) return;
        if (menu.clickMenuButton(minecraft.player, slot)) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, slot);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        for (int i = 0; i < menu.capacity(); i++) {
            if (open[i] != null) open[i].visible = !menu.getSlot(i).getItem().isEmpty();
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        UiStyle.panel(g, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
        for (Slot s : menu.slots) {
            int x = leftPos + s.x, y = topPos + s.y;
            boolean belt = s.index < menu.capacity();
            g.fill(x - 1, y - 1, x + 17, y + 17, belt ? 0xFFB08A3C : 0xFF202020);
            g.fill(x, y, x + 16, y + 16, 0xFF3A3A3A);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, UiStyle.GOLD, false);
        int used = 0;
        for (int i = 0; i < menu.capacity(); i++) used += io.github.rpgclasses.content.race.PorterBelt.cost(menu.getSlot(i).getItem());
        Component cap = Component.translatable("ui.rpgclasses.belt.capacity", used, menu.capacity());
        g.drawString(font, cap, imageWidth - 8 - font.width(cap), titleLabelY, 0xFFCCCCCC, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFDDDDDD, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
