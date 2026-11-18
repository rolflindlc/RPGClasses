# Texturas provisionales del Clérigo: retratos de la clase y sus subclases (32x32 duplicados a 64x64) e iconos de
# efecto (18x18): Tumbado (Milagro) y Forma Angelical. El arte final será de Laira.
# Uso: python3 -I tools/art/make_cleric.py src/main/resources
import math, os, sys
from PIL import Image

OUT = sys.argv[1]
TEX = os.path.join(OUT, "assets", "rites_and_paths", "textures")
for d in ("mob_effect", "gui/portrait/class", "gui/portrait/subclass"):
    os.makedirs(os.path.join(TEX, d), exist_ok=True)

GOLD = (236, 196, 92, 255); GOLD_D = (170, 128, 46, 255); WHITE = (250, 246, 228, 255); WHITE_D = (205, 198, 176, 255)
RED = (176, 22, 26, 255); RED_D = (110, 10, 14, 255); BLUE = (120, 170, 235, 255); SKIN = (236, 200, 160, 255)


def canvas(n, bg):
    im = Image.new("RGBA", (n, n), bg)
    return im, im.load()


def glow_bg(px, n, inner, outer):
    c = (n - 1) / 2
    for y in range(n):
        for x in range(n):
            t = min(1, math.hypot(x - c, y - c) / (n * 0.62))
            px[x, y] = tuple(int(inner[i] * (1 - t) + outer[i] * t) for i in range(3)) + (255,)


def disc(px, cx, cy, r, col, n):
    for y in range(n):
        for x in range(n):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r: px[x, y] = col


def ring(px, cx, cy, r0, r1, col, n, squash=1.0):
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - cx, (y - cy) / squash)
            if r0 <= d <= r1: px[x, y] = col


def rect(px, x0, y0, x1, y1, col):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1): px[x, y] = col


def save64(im, rel):
    im.resize((64, 64), Image.NEAREST).save(os.path.join(TEX, rel))


def cross(px, cx, top, h, arm, col, dark):
    rect(px, cx - 1, top, cx + 1, top + h, col)
    rect(px, cx - arm, top + h // 3, cx + arm, top + h // 3 + 2, col)
    for y in range(top, top + h + 1): px[cx + 1, y] = dark


# Clérigo: cruz dorada bajo un haz de luz.
im, px = canvas(32, (0, 0, 0, 255)); glow_bg(px, 32, (250, 232, 170), (60, 52, 70))
for y in range(0, 32):
    for x in range(13, 19):
        r, g, b, a = px[x, y]; px[x, y] = (min(255, r + 25), min(255, g + 25), min(255, b + 18), 255)
ring(px, 15.5, 7, 4.2, 5.4, GOLD, 32, 0.45)
cross(px, 15, 9, 18, 6, GOLD, GOLD_D)
save64(im, "gui/portrait/class/cleric.png")

# Ángel: alas blancas y halo.
im, px = canvas(32, (0, 0, 0, 255)); glow_bg(px, 32, (200, 225, 250), (40, 50, 80))
for side in (-1, 1):
    for k in range(5):
        for y in range(9 + k * 2, 24 - k):
            x0 = 15.5 + side * (3 + k * 2)
            for dx in range(2):
                x = int(x0 + side * dx)
                if 0 <= x < 32: px[x, y] = WHITE if (y + k) % 4 else WHITE_D
disc(px, 15.5, 13, 2.6, SKIN, 32)
rect(px, 14, 16, 17, 26, (240, 240, 250, 255))
ring(px, 15.5, 8.5, 3.0, 4.0, GOLD, 32, 0.4)
save64(im, "gui/portrait/subclass/angel.png")

# Mártir: gota de sangre sobre una cruz oscura.
im, px = canvas(32, (0, 0, 0, 255)); glow_bg(px, 32, (90, 30, 34), (20, 10, 14))
cross(px, 15, 4, 24, 7, (70, 58, 60, 255), (40, 32, 34, 255))
for y in range(9, 25):
    w = 0 if y < 12 else min(5, (y - 11) * 0.9) if y < 19 else 5 - (y - 19) * 0.9
    for x in range(int(15.5 - w), int(15.5 + w) + 1): px[x, y] = RED if x < 16 else RED_D
px[14, 18] = (240, 120, 120, 255); px[14, 19] = (220, 90, 90, 255)
save64(im, "gui/portrait/subclass/martyr.png")

# Intercesor: manos juntas rezando y un pequeño ángel de luz encima.
im, px = canvas(32, (0, 0, 0, 255)); glow_bg(px, 32, (120, 110, 170), (24, 22, 40))
for y in range(14, 28):
    w = max(1, (y - 13) // 2)
    for x in range(16 - w, 16 + w): px[x, y] = SKIN if x != 15 else (200, 160, 120, 255)
rect(px, 12, 27, 19, 29, (120, 80, 160, 255))
disc(px, 15.5, 6, 2.2, WHITE, 32)
for side in (-1, 1):
    for d in range(1, 5): px[int(15.5 + side * (2 + d)), 6 - d // 2] = WHITE_D
ring(px, 15.5, 3.2, 1.6, 2.4, GOLD, 32, 0.4)
save64(im, "gui/portrait/subclass/intercessor.png")

# Efecto Tumbado (18x18): figura acostada con una cruz dorada encima.
im, px = canvas(18, (0, 0, 0, 0))
rect(px, 3, 12, 14, 14, (170, 170, 190, 255)); disc(px, 3.5, 12.5, 1.8, SKIN, 18)
cross(px, 9, 2, 8, 3, GOLD, GOLD_D)
im.save(os.path.join(TEX, "mob_effect/downed.png"))

# Efecto Forma Angelical (18x18): alas y halo.
im, px = canvas(18, (0, 0, 0, 0))
for side in (-1, 1):
    for k in range(4):
        for y in range(5 + k, 14 - k // 2):
            x = int(8.5 + side * (2 + k))
            px[x, y] = WHITE if (y + k) % 3 else WHITE_D
ring(px, 8.5, 3.2, 1.8, 2.6, GOLD, 18, 0.45)
rect(px, 8, 6, 9, 14, (240, 240, 250, 255))
im.save(os.path.join(TEX, "mob_effect/angelic_form.png"))
print("texturas del Clérigo escritas")
