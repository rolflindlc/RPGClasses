"""Mini librería de pixel art: capas de formas planas con sombreado automático y contorno.

Cada capa es una máscara (dibujada con ImageDraw sin suavizado) y un color. Al componer, los píxeles con borde
arriba/izquierda se aclaran y los de abajo/derecha se oscurecen; al final se añade un contorno oscuro alrededor
de todo lo opaco. Las coordenadas se escriben en un espacio de 36 unidades y se escalan con ``k``.
"""
from PIL import Image, ImageDraw


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(c, t, f):
    return tuple(int(round(a + (b - a) * f)) for a, b in zip(c, t))


WARM = (255, 244, 205)   # las luces tiran a crema
COOL = (38, 22, 66)      # las sombras tiran a púrpura


def light(c, f=0.3):
    return mix(c, WARM, f)


def dark(c, f=0.3):
    return mix(c, COOL, f)


def saturate(c, f=1.18):
    import colorsys
    h, l, s = colorsys.rgb_to_hls(*(v / 255 for v in c))
    r, g, b = colorsys.hls_to_rgb(h, l, min(1.0, s * f))
    return (round(r * 255), round(g * 255), round(b * 255))


def outline_of(c):
    return mix(c, (22, 12, 30), 0.74)


class Canvas:
    def __init__(self, size, k=None, off=0.0):
        self.size = size
        self.k = size / 36 if k is None else k
        self.off = off
        self._post = []
        self.img = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    # --- capas -------------------------------------------------------------
    def layer(self, color, draw, shade=True, hi=0.34, lo=0.36, noise=0.05, spec=True):
        """draw(Pen) dibuja la forma; se pinta con ``color`` y se sombrea en 5 tonos con luz arriba-izquierda.

        Tonos: brillo (filo iluminado), luz, base, sombra y sombra profunda (filo opuesto); un brillo especular
        cerca de la esquina iluminada y algo de textura (píxeles sueltos de un tono vecino).
        """
        import random
        color = saturate(hexrgb(color) if isinstance(color, str) else color)
        mask = Image.new("L", (self.size, self.size), 0)
        draw(Pen(ImageDraw.Draw(mask), self.k, self.off))
        m = mask.load()
        px = self.img.load()
        n = self.size
        inside = lambda x, y: 0 <= x < n and 0 <= y < n and m[x, y] > 0
        pts = [(x, y) for y in range(n) for x in range(n) if m[x, y]]
        if not pts:
            return self
        if not shade:
            for x, y in pts:
                px[x, y] = color + (255,)
            return self

        def run(x, y, dx, dy, cap=6):
            k = 0
            while k < cap and inside(x + dx * k, y + dy * k):
                k += 1
            return k

        band = max(1, round(n / 24))
        tones = [light(color, hi * 1.9), light(color, hi), color, dark(color, lo), dark(color, lo * 1.9)]
        rnd = random.Random(hash((color, len(pts), n)) & 0xffff)
        level = {}
        for x, y in pts:
            lit = min(run(x, y, 0, -1), run(x, y, -1, 0))
            sh = min(run(x, y, 0, 1), run(x, y, 1, 0))
            if lit == 1 and sh == 1:
                t = 1                      # trazo fino: tono claro
            elif lit == 1:
                t = 0
            elif sh == 1:
                t = 4
            elif lit <= band + 1 and lit < sh:
                t = 1
            elif sh <= band + 1 and sh < lit:
                t = 3
            else:
                t = 2
            if noise and n > 18 and t in (2, 3) and rnd.random() < noise:
                t += rnd.choice((-1, 1))
            level[(x, y)] = t
            px[x, y] = tones[t] + (255,)
        # brillo especular: cerca de la esquina iluminada, en la zona clara, si la forma es grande
        if spec and len(pts) >= (14 if n <= 18 else 40):
            cands = [(x + y, x, y) for (x, y), t in level.items() if t == 1 and inside(x + 1, y) and level.get((x + 1, y), 4) <= 2]
            if cands:
                _, x, y = min(cands)
                white = light(color, 0.85)
                px[x, y] = white + (255,)
                if n > 18:
                    px[x + 1, y] = white + (255,)
                    if inside(x, y + 1):
                        px[x, y + 1] = light(color, 0.6) + (255,)
        return self

    def sparkle(self, x, y, color="#fff6c8", r=2):
        """Destello sin contorno (se pinta al final, después del contorno). Coordenadas en el espacio de 36."""
        self._post.append((x, y, hexrgb(color) if isinstance(color, str) else color, r))
        return self

    def finish(self):
        """Contorno y destellos."""
        self.outline()
        px = self.img.load()
        n = self.size
        for x, y, c, r in self._post:
            cx, cy = round(x * self.k + self.off), round(y * self.k + self.off)
            rr = max(1, round(r * self.k))
            pts = [(cx, cy)] + [(cx + d, cy) for d in range(-rr, rr + 1)] + [(cx, cy + d) for d in range(-rr, rr + 1)]
            for i, (a, b) in enumerate(pts):
                if 0 <= a < n and 0 <= b < n:
                    px[a, b] = ((255, 255, 255) if (a, b) == (cx, cy) else c) + (255,)
        return self

    def pixels(self, color, pts):
        color = hexrgb(color) if isinstance(color, str) else color
        px = self.img.load()
        for x, y in pts:
            if 0 <= x < self.size and 0 <= y < self.size:
                px[x, y] = color + (255,)
        return self

    def outline(self, color=None, f=0.62):
        """Contorno de 1 px alrededor de lo opaco (oscureciendo el color vecino, o un color fijo)."""
        src = self.img.copy().load()
        px = self.img.load()
        n = self.size
        for y in range(n):
            for x in range(n):
                if src[x, y][3]:
                    continue
                for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < n and 0 <= yy < n and src[xx, yy][3]:
                        c = hexrgb(color) if color else outline_of(src[xx, yy][:3])
                        px[x, y] = c + (255,)
                        break
        return self


class Pen:
    """Envuelve ImageDraw escalando coordenadas (espacio de 36)."""

    def __init__(self, d, k, off=0.0):
        self.d, self.k, self.o = d, k, off

    def _p(self, pts):
        return [(x * self.k + self.o, y * self.k + self.o) for x, y in pts]

    def _b(self, x0, y0, x1, y1):
        k, o = self.k, self.o
        return [x0 * k + o, y0 * k + o, x1 * k + o - 1, y1 * k + o - 1]

    def poly(self, *pts):
        self.d.polygon(self._p(pts), fill=255)

    def _rb(self, x0, y0, x1, y1):
        a, b, c, e = (round(v) for v in self._b(x0, y0, x1, y1))
        return [a, b, max(a, c), max(b, e)]

    def rect(self, x0, y0, x1, y1):
        self.d.rectangle(self._rb(x0, y0, x1, y1), fill=255)

    def ellipse(self, x0, y0, x1, y1):
        self.d.ellipse(self._b(x0, y0, x1, y1), fill=255)

    def circle(self, cx, cy, r):
        self.ellipse(cx - r, cy - r, cx + r, cy + r)

    def line(self, x0, y0, x1, y1, w=2):
        self.d.line(self._p([(x0, y0), (x1, y1)]), fill=255, width=max(1, round(w * self.k)))

    def erase_circle(self, cx, cy, r):
        self.d.ellipse(self._b(cx - r, cy - r, cx + r, cy + r), fill=0)

    def erase_poly(self, *pts):
        self.d.polygon(self._p(pts), fill=0)

    def erase_rect(self, x0, y0, x1, y1):
        self.d.rectangle(self._rb(x0, y0, x1, y1), fill=0)

    def arc(self, x0, y0, x1, y1, start, end, w=2):
        self.d.arc(self._b(x0, y0, x1, y1), start, end, fill=255, width=max(1, round(w * self.k)))
