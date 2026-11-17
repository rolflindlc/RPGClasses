package io.github.rpgclasses.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Estilo común de los paneles del mod: fondo oscuro translúcido con biselado y franja de cabecera. */
public final class UiStyle {
    public static final int GOLD = 0xFFE6C35A;

    private UiStyle() {}

    private static final ResourceLocation PANEL = tex("panel");
    private static final ResourceLocation HEADER = tex("header");
    public static final ResourceLocation BUTTON = tex("button");
    public static final ResourceLocation BUTTON_HOVER = tex("button_hover");
    public static final ResourceLocation BUTTON_SELECTED = tex("button_selected");

    private static ResourceLocation tex(String name) {
        return new ResourceLocation("rites_and_paths", "textures/gui/ui/" + name + ".png");
    }

    /** Panel del mod (textura de 24x24, borde 6). */
    public static void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        nine(g, PANEL, x0, y0, x1 - x0, y1 - y0, 24, 24, 6);
    }

    /** Franja de cabecera dentro de un panel, con un filo dorado debajo (textura de 24x16, borde 4). */
    public static void header(GuiGraphics g, int x0, int y0, int x1, int y1) {
        nine(g, HEADER, x0 + 1, y0 + 1, x1 - x0 - 2, y1 - y0 - 1, 24, 16, 4);
    }

    /**
     * Dibuja una textura de 9 porciones (esquinas fijas, bordes y centro estirados) de {@code border} píxeles de borde.
     * En 1.20.1 no existe blitNineSliced, así que se corta a mano.
     */
    public static void nine(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int texW, int texH, int b) {
        if (w <= 0 || h <= 0) return;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        int bx = Math.min(b, w / 2), by = Math.min(b, h / 2);
        int cw = texW - 2 * b, ch = texH - 2 * b;
        // esquinas
        g.blit(tex, x, y, bx, by, 0, 0, bx, by, texW, texH);
        g.blit(tex, x + w - bx, y, bx, by, texW - bx, 0, bx, by, texW, texH);
        g.blit(tex, x, y + h - by, bx, by, 0, texH - by, bx, by, texW, texH);
        g.blit(tex, x + w - bx, y + h - by, bx, by, texW - bx, texH - by, bx, by, texW, texH);
        // bordes
        g.blit(tex, x + bx, y, w - 2 * bx, by, b, 0, cw, by, texW, texH);
        g.blit(tex, x + bx, y + h - by, w - 2 * bx, by, b, texH - by, cw, by, texW, texH);
        g.blit(tex, x, y + by, bx, h - 2 * by, 0, b, bx, ch, texW, texH);
        g.blit(tex, x + w - bx, y + by, bx, h - 2 * by, texW - bx, b, bx, ch, texW, texH);
        // centro
        g.blit(tex, x + bx, y + by, w - 2 * bx, h - 2 * by, b, b, cw, ch, texW, texH);
    }

    /** Marco de 1 px alrededor de un rectángulo. */
    public static void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }

    /** Texto con escala (para títulos). */
    public static void scaledText(GuiGraphics g, net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component text,
                                  int x, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, color);
        g.pose().popPose();
    }
}
