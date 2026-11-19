package io.github.rpgclasses.client.tree;

/**
 * Fondo de los árboles: degradado, nebulosas que se mueven con el árbol y estrellas con paralaje.
 * Lo comparten el árbol de habilidades de clase y el de raza. Lógica pura (dibuja sobre un {@link Canvas}).
 */
final class TreeBackdrop {
    /** Nebulosa en coordenadas del árbol: centro, radio en unidades del árbol, color y opacidad. */
    record Nebula(float x, float y, float r, int color, float alpha) {}

    private final float[] stars;

    TreeBackdrop() {
        // Estrellas deterministas: x, y (0..1 de la losa), tamaño, fase.
        stars = new float[150 * 4];
        long seed = 0x5DEECE66DL;
        for (int i = 0; i < stars.length; i++) {
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            stars[i] = (float) ((seed >>> 17) & 0xFFFFFF) / 0xFFFFFF;
        }
    }

    void paint(Canvas c, TreeCamera cam, float time, Nebula... nebulas) {
        float x0 = cam.viewX0, y0 = cam.viewY0, x1 = cam.viewX1, y1 = cam.viewY1;
        Gfx.rectV(c, x0, y0, x1, y1, 0xFF15112A, 0xFF07060D);
        float z = cam.zoom;
        for (Nebula n : nebulas) {
            Gfx.ringGradient(c, cam.sx(n.x()), cam.sy(n.y()), 0, n.r() * z, Gfx.withAlpha(n.color(), n.alpha()), Gfx.withAlpha(n.color(), 0));
        }
        // Estrellas con paralaje.
        float tile = 360, par = 0.3f;
        float ox = -cam.x * z * par, oy = -cam.y * z * par;
        for (int i = 0; i < stars.length; i += 4) {
            float u = stars[i] * tile + ox, v = stars[i + 1] * tile + oy;
            float bx = x0 + ((u % tile) + tile) % tile - tile, by = y0 + ((v % tile) + tile) % tile - tile;
            float s = 0.5f + stars[i + 2] * 1.1f;
            float a = 0.25f + 0.45f * (0.5f + 0.5f * (float) Math.sin(time * (0.8f + stars[i + 3] * 2.2f) + stars[i + 3] * 40));
            int col = Gfx.withAlpha(stars[i + 3] > 0.8f ? 0xFFBFD8FF : stars[i + 3] > 0.6f ? 0xFFFFE6C4 : 0xFFFFFFFF, a);
            for (float yy = by; yy < y1; yy += tile) {
                if (yy < y0 - 2) continue;
                for (float xx = bx; xx < x1; xx += tile) {
                    if (xx < x0 - 2) continue;
                    Gfx.rect(c, xx, yy, xx + s, yy + s, col);
                    if (s > 1.3f) Gfx.glow(c, xx + s / 2, yy + s / 2, 0, s * 2.5f, col, a * 0.35f);
                }
            }
        }
    }
}
