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


def light(c, f=0.3):
    return mix(c, (255, 255, 255), f)


def dark(c, f=0.3):
    return mix(c, (0, 0, 0), f)


class Canvas:
    def __init__(self, size, k=None, off=0.0):
        self.size = size
        self.k = size / 36 if k is None else k
        self.off = off
        self.img = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    # --- capas -------------------------------------------------------------
    def layer(self, color, draw, shade=True, hi=0.28, lo=0.3):
        """draw(Pen) dibuja la forma; se pinta con ``color`` y se sombrea."""
        color = hexrgb(color) if isinstance(color, str) else color
        mask = Image.new("L", (self.size, self.size), 0)
        draw(Pen(ImageDraw.Draw(mask), self.k, self.off))
        m = mask.load()
        px = self.img.load()
        n = self.size
        inside = lambda x, y: 0 <= x < n and 0 <= y < n and m[x, y] > 0
        for y in range(n):
            for x in range(n):
                if not m[x, y]:
                    continue
                c = color
                if shade:
                    top = not inside(x, y - 1) or not inside(x - 1, y)
                    bot = not inside(x, y + 1) or not inside(x + 1, y)
                    if top and not bot:
                        c = light(color, hi)
                    elif bot and not top:
                        c = dark(color, lo)
                px[x, y] = c + (255,)
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
                        c = hexrgb(color) if color else dark(src[xx, yy][:3], f)
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
