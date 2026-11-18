package io.github.rpgclasses.client.tree;

/**
 * Superficie de dibujo del árbol, en coordenadas de pantalla (píxeles de interfaz). En el juego la implementa
 * {@code McCanvas}; separarla permite dibujar el árbol (y comprobar su aspecto) sin Minecraft.
 */
public interface Canvas {
    /** Cuadrilátero con un color ARGB por vértice; con dos vértices iguales es un triángulo. */
    void quad(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3);

    void text(String s, float x, float y, int color, boolean shadow);

    int textWidth(String s);

    /** Icono (objeto o retrato) centrado en (cx, cy) con {@code size} píxeles de lado. */
    void icon(IconRef icon, float cx, float cy, float size);
}
