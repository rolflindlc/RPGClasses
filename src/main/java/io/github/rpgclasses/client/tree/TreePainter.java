package io.github.rpgclasses.client.tree;

import io.github.rpgclasses.client.tree.TreeLayout.Kind;
import io.github.rpgclasses.client.tree.TreeLayout.Node;
import io.github.rpgclasses.client.tree.TreeStatus.Status;
import io.github.rpgclasses.core.definition.Definitions.BranchDef;
import io.github.rpgclasses.core.progression.ProgressState;
import io.github.rpgclasses.core.progression.ProgressionRules;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dibuja el árbol entero sobre un {@link Canvas}: fondo estrellado, puente, enlaces con energía, nodos, iconos,
 * etiquetas, efectos y el HUD (nivel, ramas usadas, XP, minimapa). No conoce Minecraft: recibe los textos ya
 * traducidos y devuelve dónde quedaron el minimapa y el botón de centrar para que la vista atienda los clics.
 */
public final class TreePainter {
    private static final float TAU = (float) (Math.PI * 2);
    private static final int BG = 0xFF100D1A;
    private static final int TEAL = 0xFF4DE1C1;
    private static final int RED = 0xFFC04A5C;
    private static final int TEXT = 0xFFE8E4F0;
    private static final int TEXT_DIM = 0xFF8C869C;
    private static final int PLAN_TEXT = 0xFFD9A0F0;

    /** Textos del HUD, ya traducidos. Los que son null no se dibujan. */
    public record Hud(String title, String level, String levelPlanned, String xp, String plan, String branches,
                      String hint, String preview, String bridge, String subHub) {}

    private record Seg(String text, int color) {}

    private record Label(float cx, float cy, List<Seg> segs, int bg, boolean lock, boolean leftAligned, boolean rightAligned) {}

    private final Map<Node, Float> hoverT = new IdentityHashMap<>();
    private final float[] stars;

    /** Rectángulo del minimapa en pantalla (x0, y0, x1, y1) y su transformación (escala, origen x, origen y). */
    public final float[] minimap = new float[4];
    public final float[] minimapMap = new float[3];
    /** Botón de centrar la vista. */
    public final float[] home = new float[4];

    public TreePainter() {
        // Estrellas deterministas: x, y (0..1 de la losa), tamaño, fase.
        stars = new float[150 * 4];
        long seed = 0x5DEECE66DL;
        for (int i = 0; i < stars.length; i++) {
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            stars[i] = (float) ((seed >>> 17) & 0xFFFFFF) / 0xFFFFFF;
        }
    }

    /** Anima el realce de los nodos bajo el ratón. */
    public void update(float dt, Node hover) {
        float k = 1 - (float) Math.exp(-dt * 18);
        if (hover != null) hoverT.putIfAbsent(hover, 0f);
        hoverT.replaceAll((n, v) -> v + ((n == hover ? 1f : 0f) - v) * k);
        hoverT.values().removeIf(v -> v < 0.01f);
        if (hover != null && !hoverT.containsKey(hover)) hoverT.put(hover, 0.01f);
    }

    private float hover(Node n) {
        return hoverT.getOrDefault(n, 0f);
    }

    /**
     * @param real estado real del jugador (null en vista previa)
     * @param hoverLock ramas que se bloquearían al comprar el nodo bajo el ratón (se tiñen de rojo)
     */
    public void paint(Canvas c, TreeLayout layout, TreeStatus status, ProgressState real, TreeCamera cam, TreeFx fx,
                      float time, Node hoverNode, Set<Integer> hoverLock, Hud hud, float hintAlpha) {
        float z = cam.zoom;
        background(c, layout, cam, time);
        if (layout.subHub != null) bridge(c, layout, status, real, cam, time, hud);

        // Enlaces y la energía que corre por los comprados.
        for (Node n : layout.nodes) {
            if (n.parent == null || !visible(cam, n.parent, n)) continue;
            link(c, cam, n, vis(n, status, hoverLock), time);
        }
        for (Node n : layout.nodes) {
            if (n.parent == null || !visible(cam, n.parent, n)) continue;
            if (vis(n, status, hoverLock) == Status.OWNED) pulse(c, cam, n, time);
        }

        // Nodos: cuerpo, iconos y lo que va encima de los iconos.
        for (Node n : layout.nodes) {
            if (!visible(cam, n, n)) continue;
            body(c, layout, status, real, cam, n, vis(n, status, hoverLock), time);
        }
        for (Node n : layout.nodes) {
            if (n.icon == null || !visible(cam, n, n)) continue;
            float R = radius(n, z);
            float size = n.kind == Kind.KEYSTONE ? R * 1.36f : R * 1.32f;
            c.icon(n.icon, cam.sx(n.x), cam.sy(n.y), size);
        }
        for (Node n : layout.nodes) {
            if (!visible(cam, n, n)) continue;
            overlay(c, layout, status, real, cam, n, vis(n, status, hoverLock), time);
        }

        // Efectos.
        for (TreeFx.Burst b : fx.live(time)) burst(c, cam, b, time);

        // Etiquetas (primero todas las pastillas y luego todos los textos).
        List<Label> labels = new ArrayList<>();
        collectLabels(c, layout, status, real, cam, hoverNode, hoverLock, hud, labels);
        drawLabels(c, labels);

        hud(c, layout, status, real, cam, hud, hintAlpha, time);
    }

    // --- Fondo ---

    private void background(Canvas c, TreeLayout layout, TreeCamera cam, float time) {
        float x0 = cam.viewX0, y0 = cam.viewY0, x1 = cam.viewX1, y1 = cam.viewY1;
        Gfx.rectV(c, x0, y0, x1, y1, 0xFF15112A, 0xFF07060D);
        float z = cam.zoom;
        // Nebulosas en coordenadas del árbol (se mueven con él).
        nebula(c, cam, 0, 0, 430 * z, 0xFF7A4FD0, 0.17f);
        nebula(c, cam, -260, 250, 270 * z, 0xFF3050C0, 0.10f);
        nebula(c, cam, 240, -280, 250 * z, 0xFFA03A90, 0.08f);
        if (layout.subHub != null) nebula(c, cam, layout.subHub.x, 0, 320 * z, 0xFF1FA59A, 0.12f);
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

    private static void nebula(Canvas c, TreeCamera cam, float tx, float ty, float r, int color, float a) {
        Gfx.ringGradient(c, cam.sx(tx), cam.sy(ty), 0, r, Gfx.withAlpha(color, a), Gfx.withAlpha(color, 0));
    }

    // --- Puente entre las islas ---

    private void bridge(Canvas c, TreeLayout layout, TreeStatus status, ProgressState real, TreeCamera cam, float time, Hud hud) {
        float z = cam.zoom;
        float x0 = cam.sx(layout.core.x + layout.core.r + 20), x1 = cam.sx(layout.subHub.x - layout.subHub.r - 9);
        float y = cam.sy(0);
        if (y < cam.viewY0 - 20 || y > cam.viewY1 + 20) return;
        float w = Math.max(1.2f, 2.6f * z);
        boolean editable = status.editable;
        int base = editable && real != null ? real.basePoints() : 0;
        int simBase = editable && status.sim != null ? status.sim.state.basePoints() : base;
        float need = ProgressionRules.SUBCLASS_UNLOCK;
        float fReal = editable ? Math.min(1, base / need) : 0, fSim = editable ? Math.min(1, simBase / need) : 0;
        Gfx.dashedLine(c, x0, y, x1, y, w * 0.8f, 6 * z, 6 * z, 0, editable ? 0xFF2B2A3C : Gfx.withAlpha(TEAL, 0.25f));
        float xr = x0 + (x1 - x0) * fReal, xs = x0 + (x1 - x0) * fSim;
        if (fSim > fReal) {
            Gfx.line(c, xr, y, xs, y, w, Gfx.withAlpha(TEAL, 0.4f));
            Gfx.dashedLine(c, xr, y, xs, y, w, 5 * z, 7 * z, time * 24 * z, Gfx.withAlpha(Gfx.lighten(TEAL, 0.4f), 0.9f));
        }
        if (fReal > 0) {
            Gfx.lineGlow(c, x0, y, xr, y, w * 5, TEAL, fReal >= 1 ? 0.35f : 0.2f);
            Gfx.line(c, x0, y, xr, y, w, Gfx.lighten(TEAL, 0.2f));
        }
        if (fReal >= 1) {
            // Motas de energía que viajan hacia la subclase.
            for (int i = 0; i < 4; i++) {
                float p = frac(time * 0.35f + i * 0.25f);
                float px = x0 + (x1 - x0) * p;
                float a = (float) Math.sin(p * Math.PI);
                Gfx.glow(c, px, y, 0, 5 * z, TEAL, 0.6f * a);
                Gfx.disc(c, px, y, 1.3f * z, Gfx.withAlpha(0xFFFFFFFF, 0.85f * a));
            }
        }
    }

    // --- Enlaces ---

    private static float startRadius(Node p) {
        return switch (p.kind) {
            case CORE -> p.r + 15;
            case SUB_HUB -> p.r + 9;
            case KEYSTONE -> p.r + 4.5f;
            case SUB_KEY -> p.r + 3;
            default -> p.r + 1.5f;
        };
    }

    private float[] linkEnds(TreeCamera cam, Node n) {
        Node p = n.parent;
        float z = cam.zoom;
        float px = cam.sx(p.x), py = cam.sy(p.y), nx = cam.sx(n.x), ny = cam.sy(n.y);
        float dx = nx - px, dy = ny - py;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float a = startRadius(p) * z, b = (n.r + (n.kind == Kind.KEYSTONE ? 4.5f : 1.5f)) * z;
        if (len <= a + b + 0.5f) return null;
        float ux = dx / len, uy = dy / len;
        return new float[]{px + ux * a, py + uy * a, nx - ux * b, ny - uy * b};
    }

    private void link(Canvas c, TreeCamera cam, Node n, Status st, float time) {
        float[] e = linkEnds(cam, n);
        if (e == null) return;
        float z = cam.zoom;
        float w = Math.max(1.2f, 2.4f * z);
        int col = n.kind == Kind.PERK ? n.parent.color : n.color;
        switch (st) {
            case OWNED -> {
                Gfx.lineGlow(c, e[0], e[1], e[2], e[3], w * 4.5f, col, 0.28f);
                Gfx.line(c, e[0], e[1], e[2], e[3], w, Gfx.lighten(col, 0.25f));
            }
            case PLANNED -> {
                Gfx.line(c, e[0], e[1], e[2], e[3], w, Gfx.withAlpha(col, 0.4f));
                Gfx.dashedLine(c, e[0], e[1], e[2], e[3], w, 5 * z, 7 * z, time * 24 * z, Gfx.withAlpha(Gfx.lighten(col, 0.45f), 0.95f));
            }
            case AVAILABLE -> Gfx.line(c, e[0], e[1], e[2], e[3], w * 0.9f, Gfx.withAlpha(col, 0.75f), Gfx.withAlpha(col, 0.15f));
            case POOR -> Gfx.line(c, e[0], e[1], e[2], e[3], w * 0.8f, Gfx.withAlpha(col, 0.35f), Gfx.withAlpha(col, 0.08f));
            case LOCKED, DISCARDED -> Gfx.line(c, e[0], e[1], e[2], e[3], w * 0.7f, 0xFF1D1C25);
            case WILL_LOCK -> Gfx.dashedLine(c, e[0], e[1], e[2], e[3], w * 0.8f, 3 * z, 4 * z, 0, 0xFF6A2A36);
            case PREVIEW -> Gfx.line(c, e[0], e[1], e[2], e[3], w * 0.8f, Gfx.withAlpha(col, 0.32f));
            default -> Gfx.line(c, e[0], e[1], e[2], e[3], w * 0.8f, 0xFF2B2A3C);
        }
    }

    /** Mota de energía que recorre un enlace comprado, desde el centro hacia fuera. */
    private void pulse(Canvas c, TreeCamera cam, Node n, float time) {
        float[] e = linkEnds(cam, n);
        if (e == null) return;
        float p = frac(time * 0.55f - n.level * 0.11f + Math.max(0, n.index) * 0.29f);
        float a = (float) Math.sin(p * Math.PI);
        float x = e[0] + (e[2] - e[0]) * p, y = e[1] + (e[3] - e[1]) * p;
        float z = cam.zoom;
        int col = n.kind == Kind.PERK ? n.parent.color : n.color;
        Gfx.glow(c, x, y, 0, 4.5f * z, Gfx.lighten(col, 0.3f), 0.55f * a);
        Gfx.disc(c, x, y, 1.1f * z, Gfx.withAlpha(0xFFFFFFFF, 0.8f * a));
    }

    // --- Nodos ---

    private float radius(Node n, float z) {
        return n.r * z * (1 + 0.18f * hover(n));
    }

    private static boolean visible(TreeCamera cam, Node a, Node b) {
        float m = 60 * cam.zoom;
        float minX = Math.min(cam.sx(a.x), cam.sx(b.x)) - m, maxX = Math.max(cam.sx(a.x), cam.sx(b.x)) + m;
        float minY = Math.min(cam.sy(a.y), cam.sy(b.y)) - m, maxY = Math.max(cam.sy(a.y), cam.sy(b.y)) + m;
        return maxX >= cam.viewX0 && minX <= cam.viewX1 && maxY >= cam.viewY0 && minY <= cam.viewY1;
    }

    /** Estado con el que se dibuja: las ramas que bloquearía el nodo bajo el ratón se ven en rojo. */
    private static Status vis(Node n, TreeStatus status, Set<Integer> hoverLock) {
        Status st = status.of(n);
        if (hoverLock != null && !hoverLock.isEmpty() && (n.kind == Kind.KEYSTONE || n.kind == Kind.POINT || n.kind == Kind.PERK)
                && hoverLock.contains(n.index) && st != Status.OWNED && st != Status.PLANNED && st != Status.LOCKED) {
            return Status.WILL_LOCK;
        }
        return st;
    }

    private void body(Canvas c, TreeLayout layout, TreeStatus status, ProgressState real, TreeCamera cam, Node n, Status st, float time) {
        float z = cam.zoom, sx = cam.sx(n.x), sy = cam.sy(n.y), R = radius(n, z);
        float p = 0.5f + 0.5f * (float) Math.sin(time * 4);
        float t = Math.max(1f, 1.3f * z);
        switch (n.kind) {
            case CORE -> core(c, layout, status, real, cam, n, sx, sy, R, time);
            case KEYSTONE -> {
                int col = n.color;
                if (st == Status.OWNED) Gfx.glow(c, sx, sy, R, R + 11 * z, col, 0.35f);
                if (st == Status.AVAILABLE) Gfx.glow(c, sx, sy, R, R + (6 + 4 * p) * z, col, 0.15f + 0.2f * p);
                Gfx.disc(c, sx, sy, R + 1.8f * z, 0xC0000000);
                Gfx.discGradient(c, sx, sy, R, 0xFF26223A, 0xFF13111B);
            }
            case POINT -> circle(c, n.color, st, sx, sy, R, t, z, p, time, n);
            case SUB_POINT -> {
                if (n.milestones.isEmpty()) circle(c, n.color, st, sx, sy, R, t, z, p, time, n);
                else gem(c, n.color, st, sx, sy, R, t, z, p, true);
            }
            case PERK -> perk(c, st, sx, sy, R, t, z, p);
            case GEM -> gem(c, TreeLayout.GEM_COLOR, st, sx, sy, R, t, z, p, false);
            case SUB_HUB -> hub(c, status, real, n, st, sx, sy, R, t, z, p, time);
            case SUB_KEY -> {
                int col = n.color;
                if (st == Status.OWNED) Gfx.glow(c, sx, sy, R, R + 12 * z, col, 0.38f);
                if (st == Status.AVAILABLE) Gfx.glow(c, sx, sy, R, R + (7 + 5 * p) * z, col, 0.18f + 0.25f * p);
                Gfx.disc(c, sx, sy, R + 1.8f * z, 0xC0000000);
                Gfx.disc(c, sx, sy, R, 0xFF15131F);
            }
        }
        if (hover(n) > 0.05f && n.kind != Kind.CORE) {
            float h = hover(n);
            if (n.kind == Kind.PERK || n.kind == Kind.GEM || (n.kind == Kind.SUB_POINT && !n.milestones.isEmpty())) {
                Gfx.diamondOutline(c, sx, sy, R + 3 * z, Math.max(1, z), Gfx.withAlpha(0xFFFFFFFF, 0.6f * h));
            } else {
                Gfx.ring(c, sx, sy, R + 1.2f * z, R + 1.2f * z + Math.max(1, z), Gfx.withAlpha(0xFFFFFFFF, 0.6f * h));
            }
        }
    }

    private static void circle(Canvas c, int col, Status st, float sx, float sy, float R, float t, float z, float p, float time, Node n) {
        Gfx.disc(c, sx, sy, R + 1.6f * z, 0xB0000000);
        switch (st) {
            case OWNED -> {
                Gfx.glow(c, sx, sy, R, R + 8 * z, col, 0.34f + 0.08f * (float) Math.sin(time * 2 + n.level));
                Gfx.discGradient(c, sx, sy, R, Gfx.lighten(col, 0.45f), Gfx.darken(col, 0.3f));
                Gfx.ring(c, sx, sy, R - t, R, Gfx.lighten(col, 0.6f));
                Gfx.disc(c, sx - R * 0.3f, sy - R * 0.32f, R * 0.3f, 0x55FFFFFF);
            }
            case PLANNED -> {
                Gfx.disc(c, sx, sy, R, 0xFF1A1726);
                Gfx.disc(c, sx, sy, R * 0.62f, Gfx.withAlpha(col, 0.6f));
                Gfx.dashedRing(c, sx, sy, R - t, R, 8, 0.55f, time * 1.5f, Gfx.lighten(col, 0.3f));
            }
            case AVAILABLE -> {
                Gfx.glow(c, sx, sy, R, R + (5 + 4 * p) * z, col, 0.18f + 0.2f * p);
                Gfx.disc(c, sx, sy, R, 0xFF181624);
                Gfx.ring(c, sx, sy, R - t, R, Gfx.withAlpha(col, 0.55f + 0.45f * p));
                Gfx.plus(c, sx, sy, R * 0.45f, Math.max(0.8f, 1.2f * z), Gfx.withAlpha(col, 0.9f));
            }
            case POOR -> {
                Gfx.disc(c, sx, sy, R, 0xFF141320);
                Gfx.ring(c, sx, sy, R - t, R, Gfx.withAlpha(col, 0.35f));
                Gfx.plus(c, sx, sy, R * 0.4f, Math.max(0.8f, 1.1f * z), Gfx.withAlpha(col, 0.3f));
            }
            case LOCKED, DISCARDED -> {
                Gfx.disc(c, sx, sy, R, 0xFF0D0C11);
                Gfx.ring(c, sx, sy, R - t, R, 0xFF23212B);
            }
            case WILL_LOCK -> {
                Gfx.disc(c, sx, sy, R, 0xFF140C10);
                Gfx.ring(c, sx, sy, R - t, R, Gfx.withAlpha(RED, 0.45f + 0.35f * p));
            }
            case CAPPED -> {
                Gfx.disc(c, sx, sy, R, 0xFF0F0F15);
                Gfx.dashedRing(c, sx, sy, R - t, R, 10, 0.45f, 0, 0xFF3A3A4C);
            }
            case PREVIEW -> {
                Gfx.disc(c, sx, sy, R, 0xFF13121B);
                Gfx.ring(c, sx, sy, R - t, R, Gfx.withAlpha(col, 0.55f));
            }
            default -> {
                Gfx.disc(c, sx, sy, R, 0xFF121119);
                Gfx.ring(c, sx, sy, R - t, R, 0xFF34324A);
            }
        }
    }

    private static void perk(Canvas c, Status st, float sx, float sy, float R, float t, float z, float p) {
        int col = TreeLayout.PERK_COLOR;
        switch (st) {
            case OWNED -> {
                Gfx.glow(c, sx, sy, R * 0.8f, R + 11 * z, col, 0.45f);
                Gfx.diamond(c, sx, sy, R + 1.8f * z, 0xC0000000);
                Gfx.diamond(c, sx, sy, R, Gfx.lighten(col, 0.5f), Gfx.darken(col, 0.35f));
                Gfx.diamondOutline(c, sx, sy, R, t, Gfx.lighten(col, 0.65f));
                Gfx.sparkle(c, sx, sy, R * 0.62f, 0xE0FFFFFF);
            }
            case PLANNED -> {
                Gfx.diamond(c, sx, sy, R + 1.8f * z, 0xC0000000);
                Gfx.diamond(c, sx, sy, R, 0xFF1E1528);
                Gfx.diamond(c, sx, sy, R * 0.6f, Gfx.withAlpha(col, 0.6f));
                Gfx.diamondOutline(c, sx, sy, R, t, Gfx.withAlpha(col, 0.85f));
            }
            case AVAILABLE -> {
                Gfx.glow(c, sx, sy, R * 0.8f, R + (7 + 4 * p) * z, col, 0.2f + 0.25f * p);
                Gfx.diamond(c, sx, sy, R + 1.8f * z, 0xC0000000);
                Gfx.diamond(c, sx, sy, R, 0xFF1E1528);
                Gfx.diamondOutline(c, sx, sy, R, t * 1.3f, Gfx.withAlpha(col, 0.55f + 0.45f * p));
                Gfx.sparkle(c, sx, sy, R * 0.5f * (0.8f + 0.2f * p), Gfx.withAlpha(col, 0.9f));
            }
            case DISCARDED -> {
                Gfx.diamond(c, sx, sy, R + 1.6f * z, 0xC0000000);
                Gfx.diamond(c, sx, sy, R, 0xFF0F0E13);
                Gfx.diamondOutline(c, sx, sy, R, t, 0xFF2A2630);
                Gfx.cross(c, sx, sy, R * 0.3f, Math.max(0.8f, z), 0xFF3A3640);
            }
            case LOCKED -> {
                Gfx.diamond(c, sx, sy, R, 0xFF0D0C11);
                Gfx.diamondOutline(c, sx, sy, R, t, 0xFF23212B);
            }
            case WILL_LOCK -> {
                Gfx.diamond(c, sx, sy, R, 0xFF140C10);
                Gfx.diamondOutline(c, sx, sy, R, t, Gfx.withAlpha(RED, 0.45f + 0.35f * p));
            }
            case PREVIEW -> {
                Gfx.diamond(c, sx, sy, R + 1.6f * z, 0xC0000000);
                Gfx.diamond(c, sx, sy, R, 0xFF16131F);
                Gfx.diamondOutline(c, sx, sy, R, t, Gfx.withAlpha(col, 0.5f));
            }
            default -> {
                Gfx.diamond(c, sx, sy, R + 1.6f * z, 0xC0000000);
                Gfx.diamond(c, sx, sy, R, 0xFF16131F);
                Gfx.diamondOutline(c, sx, sy, R, t, 0xFF4A3F5E);
            }
        }
    }

    /** Hito: rombo cian (en el anillo del núcleo) o del color de la subclase con filo cian (en su brazo). */
    private static void gem(Canvas c, int col, Status st, float sx, float sy, float R, float t, float z, float p, boolean accent) {
        int cyan = TreeLayout.GEM_COLOR;
        switch (st) {
            case OWNED -> {
                Gfx.glow(c, sx, sy, R * 0.7f, R + 9 * z, accent ? col : cyan, 0.5f);
                Gfx.diamond(c, sx, sy, R + 1.5f * z, 0xC0000000);
                Gfx.diamond(c, sx, sy, R, Gfx.lighten(col, 0.55f), Gfx.darken(col, 0.3f));
                Gfx.diamondOutline(c, sx, sy, R, t, Gfx.lighten(cyan, 0.5f));
            }
            case PLANNED -> {
                Gfx.diamond(c, sx, sy, R + 1.5f * z, 0xC0000000);
                Gfx.diamond(c, sx, sy, R, 0xFF15202A);
                Gfx.diamond(c, sx, sy, R * 0.6f, Gfx.withAlpha(col, 0.6f));
                Gfx.diamondOutline(c, sx, sy, R, t, Gfx.withAlpha(cyan, 0.8f));
            }
            case AVAILABLE -> {
                Gfx.glow(c, sx, sy, R * 0.7f, R + (6 + 4 * p) * z, col, 0.2f + 0.2f * p);
                Gfx.diamond(c, sx, sy, R + 1.5f * z, 0xC0000000);
                Gfx.diamond(c, sx, sy, R, 0xFF15202A);
                Gfx.diamondOutline(c, sx, sy, R, t * 1.2f, Gfx.withAlpha(col, 0.55f + 0.45f * p));
                Gfx.plus(c, sx, sy, R * 0.4f, Math.max(0.8f, 1.1f * z), Gfx.withAlpha(col, 0.9f));
            }
            case LOCKED, DISCARDED -> {
                Gfx.diamond(c, sx, sy, R, 0xFF0D0C11);
                Gfx.diamondOutline(c, sx, sy, R, t, 0xFF23212B);
            }
            case WILL_LOCK -> {
                Gfx.diamond(c, sx, sy, R, 0xFF140C10);
                Gfx.diamondOutline(c, sx, sy, R, t, Gfx.withAlpha(RED, 0.45f + 0.35f * p));
            }
            case CAPPED -> {
                Gfx.diamond(c, sx, sy, R, 0xFF0F0F15);
                Gfx.diamondOutline(c, sx, sy, R, t, 0xFF3A3A4C);
            }
            default -> {
                Gfx.diamond(c, sx, sy, R + 1.5f * z, 0xC0000000);
                Gfx.diamond(c, sx, sy, R, 0xFF121A22);
                Gfx.diamondOutline(c, sx, sy, R, t, Gfx.withAlpha(accent ? col : cyan, st == Status.PREVIEW ? 0.55f : 0.4f));
            }
        }
        if (accent && st != Status.LOCKED && st != Status.WILL_LOCK) {
            Gfx.diamondOutline(c, sx, sy, R + 2.6f * z, Math.max(0.7f, 0.8f * z), Gfx.withAlpha(cyan, st == Status.OWNED ? 0.8f : 0.4f));
        }
    }

    private void core(Canvas c, TreeLayout layout, TreeStatus status, ProgressState real, TreeCamera cam, Node n,
                      float sx, float sy, float R, float time) {
        float z = cam.zoom;
        int gold = TreeLayout.GOLD;
        Gfx.glow(c, sx, sy, R, R * 2.7f, gold, 0.15f + 0.04f * (float) Math.sin(time * 1.3f));
        Gfx.disc(c, sx, sy, R + 2 * z, 0xD0000000);
        Gfx.discGradient(c, sx, sy, R, 0xFF2A2240, 0xFF0F0C17);
        // Anillo de 40 marcas: el Nivel Total (base + subclase). Los hitos de la clase van engastados en él.
        int total = ProgressionRules.BASE_MAX + ProgressionRules.SUBCLASS_MAX;
        int have = status.editable && real != null ? real.totalLevel() : 0;
        int planned = status.editable && status.sim != null ? status.sim.state.totalLevel() : have;
        float r0 = (n.r + 4) * z, r1 = (n.r + 8.5f) * z;
        float step = TAU / total, gap = step * 0.12f;
        for (int i = 0; i < total; i++) {
            float a0 = -TAU / 4 + i * step + gap, a1 = -TAU / 4 + (i + 1) * step - gap;
            int col = i < have ? gold : i < planned ? Gfx.withAlpha(Gfx.lighten(gold, 0.3f), 0.5f)
                    : status.editable ? 0xFF2C2840 : Gfx.withAlpha(gold, 0.22f);
            Gfx.arc(c, sx, sy, r0, r1, a0, a1, col);
        }
    }

    private void hub(Canvas c, TreeStatus status, ProgressState real, Node n, Status st, float sx, float sy, float R, float t, float z,
                     float p, float time) {
        boolean open = st == Status.OWNED || st == Status.PLANNED || st == Status.PREVIEW;
        if (st == Status.OWNED) Gfx.glow(c, sx, sy, R, R + 14 * z, TEAL, 0.3f);
        Gfx.disc(c, sx, sy, R + 2 * z, 0xC0000000);
        Gfx.discGradient(c, sx, sy, R, open ? 0xFF1A2C33 : 0xFF141419, open ? 0xFF0B1317 : 0xFF0A0A0E);
        // Marcas de nivel de subclase.
        int have = status.editable && real != null && real.subclassId != null ? real.subclassLevel : 0;
        int planned = status.editable && status.sim != null && status.sim.state.subclassId != null ? status.sim.state.subclassLevel : have;
        int max = ProgressionRules.SUBCLASS_MAX;
        float r0 = (n.r + 3) * z, r1 = (n.r + 6.5f) * z, step = TAU / max, gap = step * 0.1f;
        for (int i = 0; i < max; i++) {
            float a0 = -TAU / 4 + i * step + gap, a1 = -TAU / 4 + (i + 1) * step - gap;
            int col = i < have ? TEAL : i < planned ? Gfx.withAlpha(TEAL, 0.45f) : open ? 0xFF1E3238 : 0xFF1C1B24;
            Gfx.arc(c, sx, sy, r0, r1, a0, a1, col);
        }
        Gfx.ring(c, sx, sy, R - t, R, st == Status.OWNED ? TEAL : open ? Gfx.withAlpha(TEAL, 0.6f) : 0xFF2A2838);
        if (!open) {
            Gfx.lock(c, sx, sy + R * 0.05f, R * 0.75f, 0xFF6A6878);
        } else if (status.editable && (status.sim == null || status.sim.state.subclassId == null)) {
            Gfx.sparkle(c, sx, sy, R * 0.55f * (0.85f + 0.15f * p), Gfx.withAlpha(TEAL, 0.85f));
        }
    }

    /** Lo que va encima de los iconos: viñeta y marco de los retratos, candados, progreso de cada rama. */
    private void overlay(Canvas c, TreeLayout layout, TreeStatus status, ProgressState real, TreeCamera cam, Node n, Status st, float time) {
        float z = cam.zoom, sx = cam.sx(n.x), sy = cam.sy(n.y), R = radius(n, z);
        float p = 0.5f + 0.5f * (float) Math.sin(time * 4);
        float t2 = Math.max(1.2f, 2 * z);
        switch (n.kind) {
            case CORE -> {
                Gfx.ringGradient(c, sx, sy, R * 0.6f, R, Gfx.withAlpha(0xFF0F0C17, 0), 0xFF0F0C17);
                Gfx.ringGradient(c, sx, sy, R - t2 * 1.4f, R, Gfx.lighten(TreeLayout.GOLD, 0.35f), Gfx.darken(TreeLayout.GOLD, 0.2f));
            }
            case KEYSTONE -> {
                int col = n.color;
                int ring = switch (st) {
                    case OWNED -> Gfx.lighten(col, 0.25f);
                    case PLANNED -> Gfx.withAlpha(col, 0.8f);
                    case AVAILABLE -> Gfx.withAlpha(col, 0.55f + 0.45f * p);
                    case POOR -> Gfx.withAlpha(col, 0.45f);
                    case LOCKED, DISCARDED -> 0xFF2A2830;
                    case WILL_LOCK -> Gfx.withAlpha(RED, 0.5f + 0.3f * p);
                    case PREVIEW -> Gfx.withAlpha(col, 0.7f);
                    default -> Gfx.mix(col, 0xFF3A384A, 0.65f);
                };
                if (st == Status.FUTURE || st == Status.POOR) Gfx.disc(c, sx, sy, R - t2, 0x70000000);
                Gfx.ring(c, sx, sy, R - t2, R, ring);
                if (st == Status.LOCKED || st == Status.WILL_LOCK) {
                    Gfx.disc(c, sx, sy, R - t2, 0xA0000000);
                    float ls = 8 * z;
                    Gfx.disc(c, sx + R * 0.68f, sy + R * 0.68f, ls * 0.62f, 0xE0000000);
                    Gfx.lock(c, sx + R * 0.68f, sy + R * 0.68f, ls, st == Status.LOCKED ? 0xFF9A96A8 : Gfx.lighten(RED, 0.3f));
                }
                if (status.editable && real != null) {
                    BranchDef b = layout.def.branches().get(n.index);
                    int max = Math.max(1, b.maxPoints());
                    int have = real.pointsIn(b.id());
                    int planned = status.sim != null ? status.sim.state.pointsIn(b.id()) : have;
                    float a0 = -TAU / 4;
                    float r0 = R + 2.2f * z, r1 = R + 3.8f * z;
                    if (planned > 0 || have > 0) Gfx.ring(c, sx, sy, r0, r1, 0x50000000);
                    if (have > 0) Gfx.arc(c, sx, sy, r0, r1, a0, a0 + TAU * have / max, Gfx.lighten(col, 0.3f));
                    if (planned > have) Gfx.arc(c, sx, sy, r0, r1, a0 + TAU * have / max, a0 + TAU * planned / max, Gfx.withAlpha(col, 0.45f));
                }
            }
            case SUB_KEY -> {
                int col = n.color;
                Gfx.ringGradient(c, sx, sy, R * 0.6f, R, Gfx.withAlpha(0xFF15131F, 0), 0xFF15131F);
                if (st == Status.FUTURE || st == Status.LOCKED || st == Status.WILL_LOCK) Gfx.disc(c, sx, sy, R, 0x90000000);
                int ring = switch (st) {
                    case OWNED -> Gfx.lighten(col, 0.25f);
                    case PLANNED -> Gfx.withAlpha(col, 0.85f);
                    case AVAILABLE -> Gfx.withAlpha(col, 0.55f + 0.45f * p);
                    case LOCKED -> 0xFF2A2830;
                    case WILL_LOCK -> Gfx.withAlpha(RED, 0.5f + 0.3f * p);
                    case PREVIEW -> Gfx.withAlpha(col, 0.75f);
                    default -> Gfx.mix(col, 0xFF3A384A, 0.65f);
                };
                Gfx.ring(c, sx, sy, R - t2, R, ring);
                if (st == Status.PLANNED) Gfx.dashedRing(c, sx, sy, R + 1.5f * z, R + 3 * z, 14, 0.5f, time, Gfx.withAlpha(col, 0.9f));
                if (st == Status.LOCKED || st == Status.WILL_LOCK) {
                    float ls = 9 * z;
                    Gfx.disc(c, sx + R * 0.68f, sy + R * 0.68f, ls * 0.62f, 0xE0000000);
                    Gfx.lock(c, sx + R * 0.68f, sy + R * 0.68f, ls, st == Status.LOCKED ? 0xFF9A96A8 : Gfx.lighten(RED, 0.3f));
                }
            }
            default -> { }
        }
    }

    private void burst(Canvas c, TreeCamera cam, TreeFx.Burst b, float time) {
        float z = cam.zoom;
        float age = (time - b.start()) / TreeFx.duration(b.type());
        float sx = cam.sx(b.x()), sy = cam.sy(b.y()), R = b.r() * z;
        float ease = 1 - (1 - age) * (1 - age);
        switch (b.type()) {
            case GAIN -> {
                float rr = R + 18 * z * ease;
                Gfx.ring(c, sx, sy, rr - Math.max(1, 2 * z * (1 - age)), rr, Gfx.withAlpha(Gfx.lighten(b.color(), 0.4f), 1 - age));
                Gfx.glow(c, sx, sy, 0, R + 10 * z, b.color(), 0.5f * (1 - age));
                for (int i = 0; i < 6; i++) {
                    float a = TAU * i / 6 + b.x() * 0.1f;
                    float d = R + 14 * z * ease;
                    Gfx.sparkle(c, sx + (float) Math.cos(a) * d, sy + (float) Math.sin(a) * d, 3 * z * (1 - age),
                            Gfx.withAlpha(0xFFFFFFFF, 0.9f * (1 - age)));
                }
            }
            case PLAN -> {
                float rr = R + 8 * z * ease;
                Gfx.ring(c, sx, sy, rr - Math.max(1, z), rr, Gfx.withAlpha(PLAN_TEXT, 0.8f * (1 - age)));
            }
            case LOCK -> {
                float rr = R + 34 * z * ease;
                Gfx.ring(c, sx, sy, rr - Math.max(1.5f, 3 * z * (1 - age)), rr, Gfx.withAlpha(RED, 0.9f * (1 - age)));
                Gfx.glow(c, sx, sy, 0, R + 16 * z, 0xFF000000, 0.6f * (1 - age));
            }
        }
    }

    // --- Etiquetas ---

    private void collectLabels(Canvas c, TreeLayout layout, TreeStatus status, ProgressState real, TreeCamera cam, Node hoverNode,
                               Set<Integer> hoverLock, Hud hud, List<Label> out) {
        float z = cam.zoom;
        boolean show = z >= 0.3f;
        boolean editable = status.editable && real != null;
        // Ramas: nombre y puntos, a un lado del brazo.
        for (int k = 0; k < layout.keystones.size(); k++) {
            Node key = layout.keystones.get(k);
            boolean hovered = hoverNode != null && hoverNode.index == k
                    && (hoverNode.kind == Kind.KEYSTONE || hoverNode.kind == Kind.POINT || hoverNode.kind == Kind.PERK);
            if (!show && !hovered) continue;
            Status st = vis(key, status, hoverLock);
            boolean locked = st == Status.LOCKED || st == Status.WILL_LOCK;
            List<Seg> segs = new ArrayList<>();
            segs.add(new Seg(key.label, locked ? (st == Status.LOCKED ? 0xFF77737F : Gfx.lighten(RED, 0.35f)) : Gfx.lighten(key.color, 0.15f)));
            if (editable) {
                BranchDef b = layout.def.branches().get(k);
                int have = real.pointsIn(b.id());
                int planned = status.sim != null ? status.sim.state.pointsIn(b.id()) : have;
                segs.add(new Seg(" " + have + "/" + b.maxPoints(), locked ? 0xFF5E5A66 : TEXT));
                if (planned > have) segs.add(new Seg(" +" + (planned - have), PLAN_TEXT));
            }
            float[] a = layout.branchLabelAnchor[k];
            out.add(armLabel(c, cam, a, segs, locked));
        }
        for (int j = 0; j < layout.subKeys.size(); j++) {
            Node key = layout.subKeys.get(j);
            boolean hovered = hoverNode != null && hoverNode.index == j && (hoverNode.kind == Kind.SUB_KEY || hoverNode.kind == Kind.SUB_POINT);
            if (!show && !hovered) continue;
            Status st = status.of(key);
            boolean locked = st == Status.LOCKED || st == Status.WILL_LOCK;
            List<Seg> segs = new ArrayList<>();
            segs.add(new Seg(key.label, locked ? 0xFF77737F : Gfx.lighten(key.color, 0.15f)));
            if (editable && (st == Status.OWNED || st == Status.PLANNED)) {
                int have = st == Status.OWNED ? real.subclassLevel : 0;
                int planned = status.sim != null ? status.sim.state.subclassLevel : have;
                segs.add(new Seg(" " + have + "/" + ProgressionRules.SUBCLASS_MAX, TEXT));
                if (planned > have) segs.add(new Seg(" +" + (planned - have), PLAN_TEXT));
            }
            out.add(armLabel(c, cam, layout.subLabelAnchor[j], segs, locked));
        }
        // Perks: su nombre junto al rombo.
        for (List<Node> perks : layout.perks) {
            for (Node n : perks) {
                boolean hovered = n == hoverNode;
                if (z < 0.7f && !hovered) continue;
                if (!visible(cam, n, n)) continue;
                Status st = vis(n, status, hoverLock);
                int color = switch (st) {
                    case OWNED -> 0xFFF4C2FF;
                    case PLANNED -> PLAN_TEXT;
                    case AVAILABLE -> TreeLayout.PERK_COLOR;
                    case DISCARDED, LOCKED -> 0xFF5E5A66;
                    case WILL_LOCK -> Gfx.lighten(RED, 0.3f);
                    default -> 0xFFA69CB8;
                };
                float sx = cam.sx(n.x), sy = cam.sy(n.y), R = radius(n, z);
                float d = R + 5;
                float cx, cy;
                boolean left = false, right = false;
                if (n.dx > 0.35f) {
                    cx = sx + d;
                    cy = sy;
                    left = true;
                } else if (n.dx < -0.35f) {
                    cx = sx - d;
                    cy = sy;
                    right = true;
                } else {
                    cx = sx;
                    cy = sy + Math.signum(n.dy == 0 ? 1 : n.dy) * (d + 6);
                }
                // Los dos perks de una rama vertical van a izquierda y derecha para no pisarse.
                if (!left && !right && Math.abs(n.dx) <= 0.35f) {
                    Node other = null;
                    for (Node o : perks) if (o != n) other = o;
                    if (other != null) {
                        boolean isLeft = cam.sx(n.x) < cam.sx(other.x);
                        cx = isLeft ? sx - d : sx + d;
                        cy = sy;
                        right = isLeft;
                        left = !isLeft;
                    }
                }
                out.add(new Label(cx, cy, List.of(new Seg(n.label, color)), 0xB0000000, false, left, right));
            }
        }
        // Núcleo de subclase.
        if (layout.subHub != null && hud != null && hud.subHub() != null && show) {
            Node h = layout.subHub;
            float sx = cam.sx(h.x), sy = cam.sy(h.y) - (h.r + 15) * z;
            out.add(new Label(sx, sy, List.of(new Seg(hud.subHub(), 0xFF9EEADB)), 0x90000000, false, false, false));
        }
        if (layout.subHub != null && editable && status.sim != null && status.sim.state.subclassId != null) {
            Node h = layout.subHub;
            int have = real.subclassId != null ? real.subclassLevel : 0;
            int planned = status.sim.state.subclassLevel;
            out.add(new Label(cam.sx(h.x), cam.sy(h.y), List.of(new Seg(String.valueOf(planned), planned > have ? PLAN_TEXT : TEXT)),
                    0, false, false, false));
        }
        // Puente cerrado: cuántos puntos faltan.
        if (layout.subHub != null && hud != null && hud.bridge() != null && show) {
            float x0 = cam.sx(layout.core.x + layout.core.r + 20), x1 = cam.sx(layout.subHub.x - layout.subHub.r - 9);
            out.add(new Label((x0 + x1) / 2, cam.sy(0) - 10, List.of(new Seg(hud.bridge(), 0xFF8FCFC4)), 0xA0000000, false, false, false));
        }
    }

    /** Etiqueta junto al centro de un brazo, desplazada en perpendicular hasta no pisar los nodos. */
    private static Label armLabel(Canvas c, TreeCamera cam, float[] a, List<Seg> segs, boolean lock) {
        float w = 8 + (lock ? 9 : 0);
        for (Seg s : segs) w += c.textWidth(s.text());
        float h = 11;
        float px = a[2], py = a[3];
        float dist = (TreeLayout.POINT_R + 4) * cam.zoom + Math.abs(px) * w / 2 + Math.abs(py) * h / 2;
        return new Label(cam.sx(a[0]) + px * dist, cam.sy(a[1]) + py * dist, segs, 0xB0000000, lock, false, false);
    }

    private static void drawLabels(Canvas c, List<Label> labels) {
        float[][] boxes = new float[labels.size()][];
        for (int i = 0; i < labels.size(); i++) {
            Label l = labels.get(i);
            float w = (l.lock() ? 9 : 0);
            for (Seg s : l.segs()) w += c.textWidth(s.text());
            float x0 = l.leftAligned() ? l.cx() : l.rightAligned() ? l.cx() - w - 8 : l.cx() - w / 2 - 4;
            float y0 = l.cy() - 5.5f;
            boxes[i] = new float[]{x0, y0, x0 + w + 8, y0 + 11};
            if ((l.bg() >>> 24) != 0) Gfx.pill(c, x0, y0, x0 + w + 8, y0 + 11, l.bg());
            if (l.lock()) Gfx.lock(c, x0 + 7, y0 + 5.5f, 7, 0xFF8A8696);
        }
        for (int i = 0; i < labels.size(); i++) {
            Label l = labels.get(i);
            float x = boxes[i][0] + 4 + (l.lock() ? 9 : 0);
            for (Seg s : l.segs()) {
                c.text(s.text(), x, boxes[i][1] + 2, s.color(), true);
                x += c.textWidth(s.text());
            }
        }
    }

    // --- HUD ---

    private void hud(Canvas c, TreeLayout layout, TreeStatus status, ProgressState real, TreeCamera cam, Hud hud, float hintAlpha, float time) {
        if (hud == null) return;
        float x0 = cam.viewX0, y0 = cam.viewY0, x1 = cam.viewX1, y1 = cam.viewY1;
        boolean editable = status.editable && real != null;

        // Arriba a la izquierda: clase, nivel y ramas usadas (3 huecos).
        String line1 = hud.title() + "  " + hud.level();
        String plannedLevel = hud.levelPlanned() == null ? "" : "  " + hud.levelPlanned();
        float w1 = c.textWidth(line1) + c.textWidth(plannedLevel);
        float w2 = editable ? c.textWidth(hud.branches()) + 6 + ProgressionRules.BRANCHES_USABLE * 11 : 0;
        float pw = Math.max(w1, w2) + 12, ph = editable ? 26 : 15;
        panel(c, x0 + 5, y0 + 5, x0 + 5 + pw, y0 + 5 + ph);
        // Huecos de rama.
        if (editable) {
            List<Integer> used = new ArrayList<>();
            List<Boolean> plannedOnly = new ArrayList<>();
            for (int k = 0; k < layout.def.branches().size(); k++) {
                String id = layout.def.branches().get(k).id();
                if (real.pointsIn(id) > 0) {
                    used.add(k);
                    plannedOnly.add(false);
                }
            }
            if (status.sim != null) {
                for (int k = 0; k < layout.def.branches().size(); k++) {
                    String id = layout.def.branches().get(k).id();
                    if (real.pointsIn(id) == 0 && status.sim.state.pointsIn(id) > 0) {
                        used.add(k);
                        plannedOnly.add(true);
                    }
                }
            }
            float sx = x0 + 11 + c.textWidth(hud.branches()) + 6, sy = y0 + 23.5f;
            for (int i = 0; i < ProgressionRules.BRANCHES_USABLE; i++) {
                float cx = sx + i * 11;
                if (i < used.size()) {
                    int col = TreeLayout.BRANCH_COLORS[used.get(i) % TreeLayout.BRANCH_COLORS.length];
                    if (plannedOnly.get(i)) {
                        Gfx.disc(c, cx, sy, 3.2f, Gfx.withAlpha(col, 0.35f));
                        Gfx.dashedRing(c, cx, sy, 3.2f, 4.4f, 6, 0.6f, time, col);
                    } else {
                        Gfx.glow(c, cx, sy, 3, 7, col, 0.4f);
                        Gfx.discGradient(c, cx, sy, 4.2f, Gfx.lighten(col, 0.5f), col);
                    }
                } else {
                    Gfx.disc(c, cx, sy, 4.2f, 0xFF15131F);
                    Gfx.ring(c, cx, sy, 3.3f, 4.2f, 0xFF3A3650);
                }
            }
        }

        // Arriba a la derecha: XP y plan, o el aviso de vista previa.
        List<Seg> right = new ArrayList<>();
        if (hud.preview() != null) right.add(new Seg(hud.preview(), 0xFFB9B2CC));
        if (hud.xp() != null) right.add(new Seg(hud.xp(), 0xFFB8F28C));
        String plan = hud.plan();
        float rw = 0;
        for (Seg s : right) rw = Math.max(rw, c.textWidth(s.text()));
        if (plan != null) rw = Math.max(rw, c.textWidth(plan));
        if (rw > 0) {
            float rh = plan != null ? 26 : 15;
            panel(c, x1 - 5 - rw - 12, y0 + 5, x1 - 5, y0 + 5 + rh);
        }

        // Abajo a la derecha: minimapa y botón de centrar.
        float mw = Math.min(104, (x1 - x0) * 0.24f), mh = mw * 0.56f;
        float mx1 = x1 - 5, my1 = y1 - 5, mx0 = mx1 - mw, my0 = my1 - mh;
        minimap[0] = mx0;
        minimap[1] = my0;
        minimap[2] = mx1;
        minimap[3] = my1;
        panel(c, mx0, my0, mx1, my1);
        float lw = layout.maxX - layout.minX, lh = layout.maxY - layout.minY;
        float scale = Math.min((mw - 8) / lw, (mh - 8) / lh);
        float ox = mx0 + (mw - lw * scale) / 2 - layout.minX * scale, oy = my0 + (mh - lh * scale) / 2 - layout.minY * scale;
        minimapMap[0] = scale;
        minimapMap[1] = ox;
        minimapMap[2] = oy;
        for (Node n : layout.nodes) {
            Status st = status.of(n);
            int col = st == Status.OWNED ? (n.kind == Kind.PERK ? TreeLayout.PERK_COLOR : n.color)
                    : st == Status.PLANNED ? Gfx.withAlpha(n.color, 0.6f)
                    : st == Status.LOCKED || st == Status.WILL_LOCK || st == Status.DISCARDED ? 0xFF2E2C38
                    : st == Status.PREVIEW ? Gfx.withAlpha(n.color, 0.45f) : 0xFF4E4B62;
            float s = n.kind == Kind.CORE || n.kind == Kind.SUB_HUB ? 2.2f : n.kind == Kind.KEYSTONE || n.kind == Kind.SUB_KEY ? 1.5f : 0.8f;
            float px = ox + n.x * scale, py = oy + n.y * scale;
            Gfx.rect(c, px - s, py - s, px + s, py + s, col);
        }
        // Rectángulo de lo que se ve.
        float vx0 = ox + cam.treeX(x0) * scale, vy0 = oy + cam.treeY(y0) * scale;
        float vx1 = ox + cam.treeX(x1) * scale, vy1 = oy + cam.treeY(y1) * scale;
        vx0 = Math.max(mx0 + 1, vx0);
        vy0 = Math.max(my0 + 1, vy0);
        vx1 = Math.min(mx1 - 1, vx1);
        vy1 = Math.min(my1 - 1, vy1);
        if (vx1 > vx0 && vy1 > vy0) {
            Gfx.rect(c, vx0, vy0, vx1, vy1, 0x18FFFFFF);
            Gfx.frame(c, vx0, vy0, vx1, vy1, 1, 0xB0FFFFFF);
        }
        float hb = 13;
        home[0] = mx0 - hb - 3;
        home[1] = my1 - hb;
        home[2] = mx0 - 3;
        home[3] = my1;
        panel(c, home[0], home[1], home[2], home[3]);
        float hx = (home[0] + home[2]) / 2, hy = (home[1] + home[3]) / 2;
        Gfx.ring(c, hx, hy, 3, 4, 0xFFD8D2E8);
        Gfx.disc(c, hx, hy, 1.2f, 0xFFD8D2E8);
        Gfx.rect(c, hx - 0.5f, hy - 6, hx + 0.5f, hy - 4, 0xFFD8D2E8);
        Gfx.rect(c, hx - 0.5f, hy + 4, hx + 0.5f, hy + 6, 0xFFD8D2E8);
        Gfx.rect(c, hx - 6, hy - 0.5f, hx - 4, hy + 0.5f, 0xFFD8D2E8);
        Gfx.rect(c, hx + 4, hy - 0.5f, hx + 6, hy + 0.5f, 0xFFD8D2E8);

        // Textos del HUD.
        c.text(hud.title(), x0 + 11, y0 + 9, 0xFFFFE08A, true);
        float tx = x0 + 11 + c.textWidth(hud.title() + "  ");
        c.text(hud.level(), tx, y0 + 9, TEXT, true);
        if (hud.levelPlanned() != null) c.text(plannedLevel, tx + c.textWidth(hud.level()), y0 + 9, PLAN_TEXT, true);
        if (editable) c.text(hud.branches(), x0 + 11, y0 + 20, TEXT_DIM, true);
        float ry = y0 + 9;
        for (Seg s : right) {
            c.text(s.text(), x1 - 11 - c.textWidth(s.text()), ry, s.color(), true);
            ry += 11;
        }
        if (plan != null) c.text(plan, x1 - 11 - c.textWidth(plan), y0 + 20, PLAN_TEXT, true);
        if (hud.hint() != null && hintAlpha > 0.02f) {
            int a = Math.round(255 * hintAlpha);
            if (a > 8) c.text(hud.hint(), x0 + 7, y1 - 13, (a << 24) | 0xA8A2B8, true);
        }
    }

    private static void panel(Canvas c, float x0, float y0, float x1, float y1) {
        Gfx.roundRect(c, x0, y0, x1, y1, 4, 0xE03A3550);
        Gfx.roundRect(c, x0 + 1, y0 + 1, x1 - 1, y1 - 1, 3, 0xE00C0B14);
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }
}
