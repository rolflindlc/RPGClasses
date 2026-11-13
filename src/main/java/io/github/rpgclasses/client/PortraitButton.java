package io.github.rpgclasses.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Botón-tarjeta con retrato. VERTICAL: retrato arriba y nombre debajo (listas de clases y razas).
 * HORIZONTAL: retrato pequeño a la izquierda y nombre a la derecha (subrazas, subclases).
 * Un botón inactivo se dibuja como "seleccionado" (marco dorado), igual que en el resto de pantallas.
 */
public final class PortraitButton extends Button {
    public enum Layout { VERTICAL, HORIZONTAL }

    private final ResourceLocation tex;
    private final String fallbackItem;
    private final Layout layout;
    private final boolean showName;
    /** true: inactivo significa "seleccionado"; false: inactivo significa "no disponible" (gris). */
    private boolean selectedWhenInactive = true;

    public PortraitButton(int x, int y, int w, int h, Component name, ResourceLocation tex, String fallbackItem, Layout layout,
                          boolean showName, OnPress onPress) {
        super(x, y, w, h, name, onPress, DEFAULT_NARRATION);
        this.tex = tex;
        this.fallbackItem = fallbackItem;
        this.layout = layout;
        this.showName = showName;
        if (!showName || layout == Layout.VERTICAL) setTooltip(Tooltip.create(name));
    }

    public PortraitButton unavailable() {
        selectedWhenInactive = false;
        return this;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean selected = !active && selectedWhenInactive;
        boolean hovered = active && isHoveredOrFocused();
        UiStyle.nine(g, selected ? UiStyle.BUTTON_SELECTED : hovered ? UiStyle.BUTTON_HOVER : UiStyle.BUTTON, x, y, w, h, 20, 20, 3);
        int text = selected ? 0xFFFFE08A : !active ? 0xFF777777 : 0xFFFFFFFF;
        if (layout == Layout.VERTICAL) {
            int size = Math.min(Portraits.TEX, Math.min(w - 4, h - (showName ? 14 : 4)));
            int px = x + (w - size) / 2, py = y + 3;
            Portraits.draw(g, tex, fallbackItem, px, py, size);
            if (!active && !selectedWhenInactive) g.fill(px, py, px + size, py + size, 0x90000000);
            if (showName) {
                String name = font.plainSubstrByWidth(getMessage().getString(), w - 4);
                g.drawString(font, name, x + (w - font.width(name)) / 2, y + h - 11, text, false);
            }
        } else {
            int size = h - 4;
            Portraits.draw(g, tex, fallbackItem, x + 2, y + 2, size);
            if (!active && !selectedWhenInactive) g.fill(x + 2, y + 2, x + 2 + size, y + 2 + size, 0x90000000);
            if (showName) {
                String name = font.plainSubstrByWidth(getMessage().getString(), w - size - 8);
                g.drawString(font, name, x + size + 6, y + (h - 8) / 2, text, false);
            }
        }
    }
}
