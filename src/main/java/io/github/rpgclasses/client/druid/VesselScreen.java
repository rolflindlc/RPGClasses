package io.github.rpgclasses.client.druid;

import io.github.rpgclasses.client.UiStyle;
import io.github.rpgclasses.druid.FormSpec;
import io.github.rpgclasses.druid.VesselMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Recipiente de Carne: cuatro huecos (cabeza, torso, patas, instinto) con su nombre debajo, y el inventario. */
public class VesselScreen extends AbstractContainerScreen<VesselMenu> {
    public VesselScreen(VesselMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = VesselMenu.HEIGHT;
        inventoryLabelY = VesselMenu.INV_Y - 11;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        UiStyle.panel(g, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
        for (Slot s : menu.slots) {
            int x = leftPos + s.x, y = topPos + s.y;
            boolean part = s.index < 4;
            g.fill(x - 1, y - 1, x + 17, y + 17, part ? 0xFF7A2020 : 0xFF202020);
            g.fill(x, y, x + 16, y + 16, part ? 0xFF2E1A1A : 0xFF3A3A3A);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, UiStyle.GOLD, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFDDDDDD, false);
        for (int i = 0; i < 4; i++) {
            Component label = Component.translatable("druid.rites_and_paths.part.slot." + FormSpec.Chimera.PARTS[i]);
            String text = label.getString();
            int space = text.indexOf(' ');
            String shortText = space > 0 ? text.substring(0, space) : text;
            g.pose().pushPose();
            g.pose().translate(VesselMenu.SLOT_X[i] + 8, VesselMenu.SLOT_Y + 19, 0);
            g.pose().scale(.6f, .6f, 1);
            g.drawCenteredString(font, shortText, 0, 0, 0xFFD9A0A0);
            g.pose().popPose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
