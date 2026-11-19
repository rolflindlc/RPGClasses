package io.github.rpgclasses.client.tree;

import io.github.rpgclasses.client.tree.RaceTreeLayout.Kind;
import io.github.rpgclasses.client.tree.RaceTreeLayout.Node;
import io.github.rpgclasses.client.tree.TreeStatus.Status;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dibuja el árbol de raza sobre un {@link Canvas} con el mismo estilo que el árbol de habilidades: fondo
 * estrellado, enlaces con energía, nodos, iconos, etiquetas y HUD. No conoce Minecraft: recibe los textos ya
 * traducidos y el estado de cada nodo.
 */
public final class RaceTreePainter {
    private static final float TAU = (float) (Math.PI * 2);
    private static final int TEXT = 0xFFE8E4F0;
    private static final int TEXT_DIM = 0xFF8C869C;

    /** Estado y progreso de cada nodo, que decide la vista. */
    public interface Info {
        /** OWNED (conseguido), AVAILABLE (el rito en curso) o FUTURE (aún no se llega). */
        Status status(Node n);

        /** 0..1: lo cumplido del rito en curso. */
        float progress(Node n);

        /** Rango actual (0..3). */
        int rank();
    }

    public record Line(String text, int color) {}

    /** Textos del HUD ya traducidos: título, subtítulo, rango, y el rito en curso con sus objetivos. */
    public record Hud(String title, String subtitle, String rank, String riteTitle, List<Line> riteLines, String hint) {}

    private record Seg(String text, int color) {}

    private record Label(float cx, float cy, List<Seg> segs, int bg, boolean leftAligned) {}

    private final TreeBackdrop backdrop = new TreeBackdrop();
    private final Map<Node, Float> hoverT = new IdentityHashMap<>();

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

    public void paint(Canvas c, RaceTreeLayout layout, Info info, TreeCamera cam, float time, Node hoverNode, Hud hud, float hintAlpha) {
        float z = cam.zoom;
        backdrop.paint(c, cam, time, new TreeBackdrop.Nebula(0, 0, 360, 0xFFB08A2E, 0.14f),
                new TreeBackdrop.Nebula(layout.subKey.x + 80, 0, 420, layout.subKey.color, 0.10f),
                new TreeBackdrop.Nebula(layout.maxX - 120, 0, 380, 0xFF7A4FD0, 0.12f));

        for (Node n : layout.nodes) {
            if (n.parent == null || !visible(cam, n.parent, n)) continue;
            link(c, cam, n, info.status(n), time);
        }
        for (Node n : layout.nodes) {
            if (n.parent == null || !visible(cam, n.parent, n)) continue;
            if (info.status(n) == Status.OWNED) pulse(c, cam, n, time);
        }
        for (Node n : layout.nodes) {
            if (!visible(cam, n, n)) continue;
            body(c, info, cam, n, info.status(n), time);
        }
        for (Node n : layout.nodes) {
            if (n.icon == null || !visible(cam, n, n)) continue;
            float size = n.kind == Kind.SUB_KEY ? radius(n, z) * 1.36f : radius(n, z) * 1.32f;
            c.icon(n.icon, cam.sx(n.x), cam.sy(n.y), size);
        }
        for (Node n : layout.nodes) {
            if (!visible(cam, n, n)) continue;
            overlay(c, info, cam, n, info.status(n), time);
        }

        List<Label> labels = new ArrayList<>();
        collectLabels(c, layout, info, cam, hoverNode, labels);
        drawLabels(c, labels);
        hud(c, cam, hud, hintAlpha);
    }

    // --- Enlaces ---

    private static float startRadius(Node p) {
        return switch (p.kind) {
            case CORE -> p.r + 10;
            case SUB_KEY -> p.r + 3;
            default -> p.r + 1.5f;
        };
    }

    private static float[] linkEnds(TreeCamera cam, Node n) {
        Node p = n.parent;
        float z = cam.zoom;
        float px = cam.sx(p.x), py = cam.sy(p.y), nx = cam.sx(n.x), ny = cam.sy(n.y);
        float dx = nx - px, dy = ny - py;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float a = startRadius(p) * z, b = (n.r + 1.5f) * z;
        if (len <= a + b + 0.5f) return null;
        float ux = dx / len, uy = dy / len;
        return new float[]{px + ux * a, py + uy * a, nx - ux * b, ny - uy * b};
    }

    private static int armColor(Node n) {
        return n.kind == Kind.GEM && n.trunk ? TreeLayout.GEM_COLOR : n.color;
    }

    private static void link(Canvas c, TreeCamera cam, Node n, Status st, float time) {
        float[] e = linkEnds(cam, n);
        if (e == null) return;
        float z = cam.zoom;
        float w = Math.max(1.2f, (n.kind == Kind.GEM ? 1.6f : 2.4f) * z);
        int col = armColor(n);
        switch (st) {
            case OWNED -> {
                Gfx.lineGlow(c, e[0], e[1], e[2], e[3], w * 4.5f, col, 0.28f);
                Gfx.line(c, e[0], e[1], e[2], e[3], w, Gfx.lighten(col, 0.25f));
            }
            case AVAILABLE -> {
                Gfx.line(c, e[0], e[1], e[2], e[3], w, Gfx.withAlpha(col, 0.4f));
                Gfx.dashedLine(c, e[0], e[1], e[2], e[3], w, 5 * z, 7 * z, time * 24 * z, Gfx.withAlpha(Gfx.lighten(col, 0.45f), 0.95f));
            }
            default -> Gfx.line(c, e[0], e[1], e[2], e[3], w * 0.8f, 0xFF2B2A3C);
        }
    }

    /** Mota de energía que recorre un enlace conseguido, desde el centro hacia fuera. */
    private static void pulse(Canvas c, TreeCamera cam, Node n, float time) {
        float[] e = linkEnds(cam, n);
        if (e == null) return;
        float p = frac(time * 0.55f - n.rank * 0.17f + (n.kind == Kind.GEM ? 0.31f : 0));
        float a = (float) Math.sin(p * Math.PI);
        float x = e[0] + (e[2] - e[0]) * p, y = e[1] + (e[3] - e[1]) * p;
        float z = cam.zoom;
        Gfx.glow(c, x, y, 0, 4.5f * z, Gfx.lighten(armColor(n), 0.3f), 0.55f * a);
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

    private void body(Canvas c, Info info, TreeCamera cam, Node n, Status st, float time) {
        float z = cam.zoom, sx = cam.sx(n.x), sy = cam.sy(n.y), R = radius(n, z);
        float p = 0.5f + 0.5f * (float) Math.sin(time * 4);
        float t = Math.max(1f, 1.3f * z);
        switch (n.kind) {
            case CORE -> core(c, info, n, sx, sy, R, z, time);
            case SUB_KEY -> {
                Gfx.glow(c, sx, sy, R, R + 12 * z, n.color, 0.38f);
                Gfx.disc(c, sx, sy, R + 1.8f * z, 0xC0000000);
                Gfx.disc(c, sx, sy, R, 0xFF15131F);
            }
            case RITE -> rite(c, info, n, st, sx, sy, R, t, z, p, time);
            case GEM -> gem(c, st, armColor(n), sx, sy, R, t, z, p);
        }
        if (hover(n) > 0.05f && n.kind != Kind.CORE) {
            float h = hover(n);
            if (n.kind == Kind.GEM) Gfx.diamondOutline(c, sx, sy, R + 3 * z, Math.max(1, z), Gfx.withAlpha(0xFFFFFFFF, 0.6f * h));
            else Gfx.ring(c, sx, sy, R + 1.2f * z, R + 1.2f * z + Math.max(1, z), Gfx.withAlpha(0xFFFFFFFF, 0.6f * h));
        }
    }

    private static void rite(Canvas c, Info info, Node n, Status st, float sx, float sy, float R, float t, float z, float p, float time) {
        int col = n.color;
        Gfx.disc(c, sx, sy, R + 1.6f * z, 0xB0000000);
        switch (st) {
            case OWNED -> {
                Gfx.glow(c, sx, sy, R, R + 9 * z, col, 0.36f + 0.08f * (float) Math.sin(time * 2 + n.rank));
                Gfx.discGradient(c, sx, sy, R, Gfx.lighten(col, 0.45f), Gfx.darken(col, 0.3f));
                Gfx.ring(c, sx, sy, R - t, R, Gfx.lighten(col, 0.6f));
            }
            case AVAILABLE -> {
                Gfx.glow(c, sx, sy, R, R + (6 + 4 * p) * z, col, 0.2f + 0.22f * p);
                Gfx.disc(c, sx, sy, R, 0xFF181624);
                Gfx.ring(c, sx, sy, R - t, R, Gfx.withAlpha(col, 0.55f + 0.45f * p));
            }
            default -> {
                Gfx.disc(c, sx, sy, R, 0xFF121119);
                Gfx.ring(c, sx, sy, R - t, R, 0xFF34324A);
            }
        }
    }

    private static void gem(Canvas c, Status st, int col, float sx, float sy, float R, float t, float z, float p) {
        int cyan = TreeLayout.GEM_COLOR;
        if (st == Status.OWNED) {
            Gfx.glow(c, sx, sy, R * 0.7f, R + 9 * z, col, 0.5f);
            Gfx.diamond(c, sx, sy, R + 1.5f * z, 0xC0000000);
            Gfx.diamond(c, sx, sy, R, Gfx.lighten(col, 0.55f), Gfx.darken(col, 0.3f));
            Gfx.diamondOutline(c, sx, sy, R, t, Gfx.lighten(cyan, 0.5f));
        } else {
            Gfx.diamond(c, sx, sy, R + 1.5f * z, 0xC0000000);
            Gfx.diamond(c, sx, sy, R, 0xFF121A22);
            Gfx.diamondOutline(c, sx, sy, R, t, Gfx.withAlpha(col, 0.4f));
        }
    }

    private static void core(Canvas c, Info info, Node n, float sx, float sy, float R, float z, float time) {
        int gold = TreeLayout.GOLD;
        Gfx.glow(c, sx, sy, R, R * 2.7f, gold, 0.15f + 0.04f * (float) Math.sin(time * 1.3f));
        Gfx.disc(c, sx, sy, R + 2 * z, 0xD0000000);
        Gfx.discGradient(c, sx, sy, R, 0xFF2A2240, 0xFF0F0C17);
        // Anillo de 3 marcas: el Rango de raza (0..3).
        int total = 3, have = Math.max(0, Math.min(total, info.rank()));
        float r0 = (n.r + 4) * z, r1 = (n.r + 8.5f) * z;
        float step = TAU / total, gap = step * 0.06f;
        for (int i = 0; i < total; i++) {
            float a0 = -TAU / 4 + i * step + gap, a1 = -TAU / 4 + (i + 1) * step - gap;
            Gfx.arc(c, sx, sy, r0, r1, a0, a1, i < have ? gold : 0xFF2C2840);
        }
    }

    /** Lo que va encima de los iconos: viñeta y marco de los retratos, candado y progreso del rito en curso. */
    private void overlay(Canvas c, Info info, TreeCamera cam, Node n, Status st, float time) {
        float z = cam.zoom, sx = cam.sx(n.x), sy = cam.sy(n.y), R = radius(n, z);
        float p = 0.5f + 0.5f * (float) Math.sin(time * 4);
        float t2 = Math.max(1.2f, 2 * z);
        switch (n.kind) {
            case CORE -> {
                Gfx.ringGradient(c, sx, sy, R * 0.6f, R, Gfx.withAlpha(0xFF0F0C17, 0), 0xFF0F0C17);
                Gfx.ringGradient(c, sx, sy, R - t2 * 1.4f, R, Gfx.lighten(TreeLayout.GOLD, 0.35f), Gfx.darken(TreeLayout.GOLD, 0.2f));
            }
            case SUB_KEY -> {
                Gfx.ringGradient(c, sx, sy, R * 0.6f, R, Gfx.withAlpha(0xFF15131F, 0), 0xFF15131F);
                Gfx.ring(c, sx, sy, R - t2, R, Gfx.lighten(n.color, 0.25f));
            }
            case RITE -> {
                if (st == Status.FUTURE) {
                    Gfx.disc(c, sx, sy, R - t2, 0x90000000);
                    float ls = 7 * z;
                    Gfx.disc(c, sx + R * 0.7f, sy + R * 0.7f, ls * 0.62f, 0xE0000000);
                    Gfx.lock(c, sx + R * 0.7f, sy + R * 0.7f, ls, 0xFF9A96A8);
                } else if (st == Status.AVAILABLE) {
                    float r0 = R + 2.2f * z, r1 = R + 3.8f * z, f = Math.max(0, Math.min(1, info.progress(n)));
                    Gfx.ring(c, sx, sy, r0, r1, 0x50000000);
                    if (f > 0) Gfx.arc(c, sx, sy, r0, r1, -TAU / 4, -TAU / 4 + TAU * f, Gfx.lighten(n.color, 0.3f));
                    if (f < 1) Gfx.dashedRing(c, sx, sy, R + 4.6f * z, R + 5.6f * z, 12, 0.5f, time, Gfx.withAlpha(n.color, 0.35f + 0.4f * p));
                } else {
                    Gfx.ring(c, sx, sy, R - t2, R, Gfx.lighten(n.color, 0.25f));
                }
            }
            default -> { }
        }
    }

    // --- Etiquetas ---

    private void collectLabels(Canvas c, RaceTreeLayout layout, Info info, TreeCamera cam, Node hoverNode, List<Label> out) {
        float z = cam.zoom;
        for (Node n : layout.nodes) {
            if (n.label == null || n.label.isEmpty() || !visible(cam, n, n)) continue;
            Status st = info.status(n);
            float sx = cam.sx(n.x), sy = cam.sy(n.y), R = radius(n, z);
            switch (n.kind) {
                case CORE -> out.add(new Label(sx - R - 8, sy, List.of(new Seg(n.label, 0xFFFFE08A)), 0xB0000000, false));
                case SUB_KEY -> out.add(new Label(sx + R + 6, sy - 14 * z - 3, List.of(new Seg(n.label, Gfx.lighten(n.color, 0.2f))), 0xB0000000, true));
                case RITE -> {
                    int color = st == Status.OWNED ? Gfx.lighten(n.color, 0.3f) : st == Status.AVAILABLE ? 0xFFFFFFFF : 0xFF77737F;
                    List<Seg> segs = new ArrayList<>();
                    segs.add(new Seg(n.label, color));
                    out.add(new Label(sx + R + 6, sy - 14 * z - 3, segs, 0xB0000000, true));
                }
                case GEM -> {
                    boolean hovered = n == hoverNode;
                    if (z < 0.75f && !hovered) continue;
                    int color = st == Status.OWNED ? 0xFFD6F6FF : 0xFF77737F;
                    String text = hovered ? n.label : fit(c, n.label, Math.max(40, RaceTreeLayout.COLUMN * z - 30));
                    out.add(new Label(sx + R + 6, sy, List.of(new Seg(text, color)), 0xB0000000, true));
                }
            }
        }
    }

    /** Recorta el texto con puntos suspensivos hasta que quepa. */
    private static String fit(Canvas c, String s, float max) {
        if (c.textWidth(s) <= max) return s;
        String t = s;
        while (t.length() > 1 && c.textWidth(t + "…") > max) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    private static void drawLabels(Canvas c, List<Label> labels) {
        float[][] boxes = new float[labels.size()][];
        for (int i = 0; i < labels.size(); i++) {
            Label l = labels.get(i);
            float w = 0;
            for (Seg s : l.segs()) w += c.textWidth(s.text());
            float x0 = l.leftAligned() ? l.cx() : l.cx() - w - 8;
            float y0 = l.cy() - 5.5f;
            boxes[i] = new float[]{x0, y0, x0 + w + 8, y0 + 11};
            if ((l.bg() >>> 24) != 0) Gfx.pill(c, x0, y0, x0 + w + 8, y0 + 11, l.bg());
        }
        for (int i = 0; i < labels.size(); i++) {
            Label l = labels.get(i);
            float x = boxes[i][0] + 4;
            for (Seg s : l.segs()) {
                c.text(s.text(), x, boxes[i][1] + 2, s.color(), true);
                x += c.textWidth(s.text());
            }
        }
    }

    // --- HUD ---

    private void hud(Canvas c, TreeCamera cam, Hud hud, float hintAlpha) {
        if (hud == null) return;
        float x0 = cam.viewX0, y0 = cam.viewY0, x1 = cam.viewX1, y1 = cam.viewY1;

        // Arriba a la izquierda: raza, subraza y rango.
        String line1 = hud.title() + "  " + hud.rank();
        float w1 = Math.max(c.textWidth(line1), c.textWidth(hud.subtitle()));
        panel(c, x0 + 5, y0 + 5, x0 + 5 + w1 + 12, y0 + 5 + 26);
        c.text(hud.title(), x0 + 11, y0 + 9, 0xFFFFE08A, true);
        c.text(hud.rank(), x0 + 11 + c.textWidth(hud.title() + "  "), y0 + 9, TEXT, true);
        c.text(hud.subtitle(), x0 + 11, y0 + 20, TEXT_DIM, true);

        // Arriba a la derecha: el rito en curso con el progreso de cada objetivo.
        if (hud.riteTitle() != null) {
            float rw = c.textWidth(hud.riteTitle());
            for (Line l : hud.riteLines()) rw = Math.max(rw, c.textWidth(l.text()));
            float rh = 15 + 11 * hud.riteLines().size();
            panel(c, x1 - 5 - rw - 12, y0 + 5, x1 - 5, y0 + 5 + rh);
            c.text(hud.riteTitle(), x1 - 11 - c.textWidth(hud.riteTitle()), y0 + 9, 0xFFD9A0F0, true);
            float ry = y0 + 20;
            for (Line l : hud.riteLines()) {
                c.text(l.text(), x1 - 11 - c.textWidth(l.text()), ry, l.color(), true);
                ry += 11;
            }
        }
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
