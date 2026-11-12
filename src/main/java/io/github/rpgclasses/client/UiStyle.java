package io.github.rpgclasses.client;

import net.minecraft.client.gui.GuiGraphics;

/** Estilo común de los paneles del mod: fondo oscuro translúcido con biselado y franja de cabecera. */
public final class UiStyle {
    public static final int GOLD = 0xFFE6C35A;

    private UiStyle() {}

    /** Panel con biselado (claro arriba-izquierda, oscuro abajo-derecha). */
    public static void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, 0xB0101014);
        g.fill(x0, y0, x1, y0 + 1, 0xFF5A5A66);
        g.fill(x0, y0, x0 + 1, y1, 0xFF5A5A66);
        g.fill(x0, y1 - 1, x1, y1, 0xFF000000);
        g.fill(x1 - 1, y0, x1, y1, 0xFF000000);
    }

    /** Franja de cabecera dentro de un panel, con un filo dorado debajo. */
    public static void header(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fillGradient(x0 + 1, y0 + 1, x1 - 1, y1, 0xC0303040, 0xC0181820);
        g.fill(x0 + 1, y1 - 1, x1 - 1, y1, 0x80E6C35A);
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
