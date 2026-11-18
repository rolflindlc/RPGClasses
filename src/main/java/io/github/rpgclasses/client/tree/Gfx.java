package io.github.rpgclasses.client.tree;

/** Primitivas vectoriales sobre un {@link Canvas}: discos, anillos, arcos, halos, líneas, rombos y colores. */
public final class Gfx {
    private static final float TAU = (float) (Math.PI * 2);

    private Gfx() {}

    // --- Colores ARGB ---

    public static int alpha(int argb, float a) {
        int base = (argb >>> 24) & 0xFF;
        int na = Math.max(0, Math.min(255, Math.round(base * a)));
        return (na << 24) | (argb & 0xFFFFFF);
    }

    public static int withAlpha(int argb, float a) {
        int na = Math.max(0, Math.min(255, Math.round(255 * a)));
        return (na << 24) | (argb & 0xFFFFFF);
    }

    public static int mix(int c1, int c2, float t) {
        t = Math.max(0, Math.min(1, t));
        int a = Math.round(((c1 >>> 24) & 0xFF) * (1 - t) + ((c2 >>> 24) & 0xFF) * t);
        int r = Math.round(((c1 >> 16) & 0xFF) * (1 - t) + ((c2 >> 16) & 0xFF) * t);
        int g = Math.round(((c1 >> 8) & 0xFF) * (1 - t) + ((c2 >> 8) & 0xFF) * t);
        int b = Math.round((c1 & 0xFF) * (1 - t) + (c2 & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int lighten(int c, float t) {
        return mix(c, 0xFFFFFFFF & (c | 0x00FFFFFF), t);
    }

    public static int darken(int c, float t) {
        return mix(c, c & 0xFF000000, t);
    }

    // --- Formas ---

    public static void rect(Canvas c, float x0, float y0, float x1, float y1, int color) {
        c.quad(x0, y0, color, x0, y1, color, x1, y1, color, x1, y0, color);
    }

    public static void rectV(Canvas c, float x0, float y0, float x1, float y1, int top, int bottom) {
        c.quad(x0, y0, top, x0, y1, bottom, x1, y1, bottom, x1, y0, top);
    }

    /** Marco de {@code t} píxeles por dentro del rectángulo. */
    public static void frame(Canvas c, float x0, float y0, float x1, float y1, float t, int color) {
        rect(c, x0, y0, x1, y0 + t, color);
        rect(c, x0, y1 - t, x1, y1, color);
        rect(c, x0, y0 + t, x0 + t, y1 - t, color);
        rect(c, x1 - t, y0 + t, x1, y1 - t, color);
    }

    static int segments(float r) {
        return Math.max(10, Math.min(64, Math.round(r * 0.9f) + 8));
    }

    public static void disc(Canvas c, float cx, float cy, float r, int color) {
        ringGradient(c, cx, cy, 0, r, color, color);
    }

    /** Disco con degradado radial: {@code inner} en el centro, {@code outer} en el borde. */
    public static void discGradient(Canvas c, float cx, float cy, float r, int inner, int outer) {
        ringGradient(c, cx, cy, 0, r, inner, outer);
    }

    public static void ring(Canvas c, float cx, float cy, float r0, float r1, int color) {
        ringGradient(c, cx, cy, r0, r1, color, color);
    }

    public static void ringGradient(Canvas c, float cx, float cy, float r0, float r1, int c0, int c1) {
        arc(c, cx, cy, r0, r1, 0, TAU, c0, c1);
    }

    /** Sector de anillo entre los ángulos {@code a0} y {@code a1} (radianes, sentido horario en pantalla). */
    public static void arc(Canvas c, float cx, float cy, float r0, float r1, float a0, float a1, int color) {
        arc(c, cx, cy, r0, r1, a0, a1, color, color);
    }

    public static void arc(Canvas c, float cx, float cy, float r0, float r1, float a0, float a1, int c0, int c1) {
        if (r1 <= 0 || a1 <= a0) return;
        int n = Math.max(1, Math.round(segments(r1) * (a1 - a0) / TAU));
        float prevCos = (float) Math.cos(a0), prevSin = (float) Math.sin(a0);
        for (int i = 1; i <= n; i++) {
            float a = a0 + (a1 - a0) * i / n;
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            c.quad(cx + prevCos * r0, cy + prevSin * r0, c0,
                    cx + prevCos * r1, cy + prevSin * r1, c1,
                    cx + cos * r1, cy + sin * r1, c1,
                    cx + cos * r0, cy + sin * r0, c0);
            prevCos = cos;
            prevSin = sin;
        }
    }

    /** Anillo a trazos ({@code dashes} trazos), girado {@code phase} radianes. */
    public static void dashedRing(Canvas c, float cx, float cy, float r0, float r1, int dashes, float fill, float phase, int color) {
        float step = TAU / dashes;
        for (int i = 0; i < dashes; i++) {
            float a = phase + i * step;
            arc(c, cx, cy, r0, r1, a, a + step * fill, color);
        }
    }

    /** Halo: degradado del color con opacidad {@code a} en {@code r0} a transparente en {@code r1}. */
    public static void glow(Canvas c, float cx, float cy, float r0, float r1, int color, float a) {
        if (a <= 0.01f) return;
        ringGradient(c, cx, cy, r0, r1, withAlpha(color, a), withAlpha(color, 0));
    }

    public static void line(Canvas c, float x0, float y0, float x1, float y1, float w, int color) {
        line(c, x0, y0, x1, y1, w, color, color);
    }

    /** Línea con un color en cada extremo. */
    public static void line(Canvas c, float x0, float y0, float x1, float y1, float w, int c0, int c1) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) return;
        float nx = -dy / len * w / 2, ny = dx / len * w / 2;
        c.quad(x0 + nx, y0 + ny, c0, x0 - nx, y0 - ny, c0, x1 - nx, y1 - ny, c1, x1 + nx, y1 + ny, c1);
    }

    /** Halo alrededor de una línea: opaco ({@code a}) en el centro y transparente a {@code w / 2}. */
    public static void lineGlow(Canvas c, float x0, float y0, float x1, float y1, float w, int color, float a) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f || a <= 0.01f) return;
        float nx = -dy / len * w / 2, ny = dx / len * w / 2;
        int in = withAlpha(color, a), out = withAlpha(color, 0);
        c.quad(x0, y0, in, x0 + nx, y0 + ny, out, x1 + nx, y1 + ny, out, x1, y1, in);
        c.quad(x0, y0, in, x1, y1, in, x1 - nx, y1 - ny, out, x0 - nx, y0 - ny, out);
    }

    /** Línea a trazos que avanzan con {@code offset} (píxeles). */
    public static void dashedLine(Canvas c, float x0, float y0, float x1, float y1, float w, float dash, float gap, float offset, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) return;
        float ux = dx / len, uy = dy / len;
        float period = dash + gap;
        float start = (offset % period + period) % period - period;
        for (float s = start; s < len; s += period) {
            float a = Math.max(0, s), b = Math.min(len, s + dash);
            if (b <= a) continue;
            line(c, x0 + ux * a, y0 + uy * a, x0 + ux * b, y0 + uy * b, w, color);
        }
    }

    /** Rombo con sombreado: arriba más claro, abajo más oscuro. */
    public static void diamond(Canvas c, float cx, float cy, float r, int top, int bottom) {
        int mid = mix(top, bottom, 0.5f);
        c.quad(cx, cy - r, top, cx - r, cy, mid, cx, cy + r, bottom, cx + r, cy, mid);
    }

    public static void diamond(Canvas c, float cx, float cy, float r, int color) {
        c.quad(cx, cy - r, color, cx - r, cy, color, cx, cy + r, color, cx + r, cy, color);
    }

    public static void diamondOutline(Canvas c, float cx, float cy, float r, float t, int color) {
        float ri = Math.max(0, r - t * 1.41f);
        c.quad(cx, cy - r, color, cx, cy - ri, color, cx + ri, cy, color, cx + r, cy, color);
        c.quad(cx + r, cy, color, cx + ri, cy, color, cx, cy + ri, color, cx, cy + r, color);
        c.quad(cx, cy + r, color, cx, cy + ri, color, cx - ri, cy, color, cx - r, cy, color);
        c.quad(cx - r, cy, color, cx - ri, cy, color, cx, cy - ri, color, cx, cy - r, color);
    }

    /** Destello de cuatro puntas. */
    public static void sparkle(Canvas c, float cx, float cy, float r, int color) {
        float w = r * 0.22f;
        c.quad(cx, cy - r, color, cx - w, cy, color, cx, cy + r, color, cx + w, cy, color);
        c.quad(cx - r, cy, color, cx, cy + w, color, cx + r, cy, color, cx, cy - w, color);
    }

    /** Cruz "+" de brazo {@code r} y grosor {@code t}. */
    public static void plus(Canvas c, float cx, float cy, float r, float t, int color) {
        rect(c, cx - r, cy - t / 2, cx + r, cy + t / 2, color);
        rect(c, cx - t / 2, cy - r, cx + t / 2, cy - t / 2, color);
        rect(c, cx - t / 2, cy + t / 2, cx + t / 2, cy + r, color);
    }

    /** Aspa "x". */
    public static void cross(Canvas c, float cx, float cy, float r, float t, int color) {
        line(c, cx - r, cy - r, cx + r, cy + r, t, color);
        line(c, cx - r, cy + r, cx + r, cy - r, t, color);
    }

    /** Candado de {@code s} píxeles de alto centrado en (cx, cy). */
    public static void lock(Canvas c, float cx, float cy, float s, int color) {
        float bw = s * 0.42f, top = cy - s * 0.05f;
        rect(c, cx - bw, top, cx + bw, cy + s * 0.5f, color);
        float sr = s * 0.27f, st = Math.max(0.8f, s * 0.11f);
        arc(c, cx, top, sr - st, sr, (float) Math.PI, TAU, color);
        rect(c, cx - sr, top - s * 0.12f, cx - sr + st, top, color);
        rect(c, cx + sr - st, top - s * 0.12f, cx + sr, top, color);
        rect(c, cx - s * 0.05f, top + s * 0.15f, cx + s * 0.05f, top + s * 0.35f, darken(color, 0.7f));
    }

    /** Pastilla (rectángulo con los extremos redondeados). */
    public static void pill(Canvas c, float x0, float y0, float x1, float y1, int color) {
        float r = (y1 - y0) / 2;
        if (x1 - x0 <= 2 * r) {
            disc(c, (x0 + x1) / 2, (y0 + y1) / 2, r, color);
            return;
        }
        rect(c, x0 + r, y0, x1 - r, y1, color);
        arc(c, x0 + r, y0 + r, 0, r, (float) (Math.PI / 2), (float) (Math.PI * 1.5), color);
        arc(c, x1 - r, y0 + r, 0, r, (float) (-Math.PI / 2), (float) (Math.PI / 2), color);
    }

    /** Rectángulo con esquinas redondeadas de radio {@code r}. */
    public static void roundRect(Canvas c, float x0, float y0, float x1, float y1, float r, int color) {
        r = Math.min(r, Math.min(x1 - x0, y1 - y0) / 2);
        rect(c, x0 + r, y0, x1 - r, y1, color);
        rect(c, x0, y0 + r, x0 + r, y1 - r, color);
        rect(c, x1 - r, y0 + r, x1, y1 - r, color);
        float h = (float) (Math.PI / 2);
        arc(c, x0 + r, y0 + r, 0, r, 2 * h, 3 * h, color);
        arc(c, x1 - r, y0 + r, 0, r, 3 * h, 4 * h, color);
        arc(c, x1 - r, y1 - r, 0, r, 0, h, color);
        arc(c, x0 + r, y1 - r, 0, r, h, 2 * h, color);
    }
}
