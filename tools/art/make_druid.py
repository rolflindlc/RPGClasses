# Texturas provisionales del Druida: objetos (Libro de Estudio, Recipiente de Carne y partes de bestia), el Altar de
# Carne, el efecto Precio de la Carne, el icono del Ritual del Druida y los retratos de la clase y sus subclases
# (32x32 duplicados a 64x64). El arte final será de Laira.
# Uso: python3 -I tools/art/make_druid.py src/main/resources
import math, os, random, sys
from PIL import Image

OUT = sys.argv[1]
TEX = os.path.join(OUT, "assets", "rites_and_paths", "textures")
for d in ("item", "block", "mob_effect", "gui/spell_icons", "gui/portrait/class", "gui/portrait/subclass"):
    os.makedirs(os.path.join(TEX, d), exist_ok=True)

T = (0, 0, 0, 0)
LEAF = (86, 160, 62, 255); LEAF_D = (48, 104, 40, 255); LEAF_L = (140, 206, 92, 255)
BARK = (112, 78, 46, 255); BARK_D = (74, 50, 30, 255)
BONE = (232, 224, 196, 255); BONE_D = (178, 168, 138, 255)
FLESH = (166, 48, 52, 255); FLESH_D = (104, 22, 30, 255); FLESH_L = (212, 96, 92, 255)
PURPLE = (170, 92, 220, 255); PURPLE_L = (220, 170, 255, 255)
GOLD = (236, 196, 92, 255); MOON = (230, 236, 250, 255)


def canvas(n, bg=T):
    im = Image.new("RGBA", (n, n), bg)
    return im, im.load()


def rect(px, x0, y0, x1, y1, col):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1): px[x, y] = col


def disc(px, cx, cy, r, col, n):
    for y in range(n):
        for x in range(n):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r: px[x, y] = col


def ring(px, cx, cy, r0, r1, col, n):
    for y in range(n):
        for x in range(n):
            if r0 <= math.hypot(x - cx, y - cy) <= r1: px[x, y] = col


def glow_bg(px, n, inner, outer):
    c = (n - 1) / 2
    for y in range(n):
        for x in range(n):
            t = min(1, math.hypot(x - c, y - c) / (n * 0.62))
            px[x, y] = tuple(int(inner[i] * (1 - t) + outer[i] * t) for i in range(3)) + (255,)


def outline(im):
    """Contorno oscuro de 1 px alrededor de lo opaco (estilo de objeto de Minecraft)."""
    px = im.load(); n = im.size[0]
    edge = []
    for y in range(n):
        for x in range(n):
            if px[x, y][3] == 0 and any(0 <= x + dx < n and 0 <= y + dy < n and px[x + dx, y + dy][3] > 0 and px[x + dx, y + dy] != (30, 20, 20, 255)
                                        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                edge.append((x, y))
    for x, y in edge: px[x, y] = (30, 20, 20, 255)


def save(im, rel):
    im.save(os.path.join(TEX, rel))


def save64(im, rel):
    im.resize((64, 64), Image.NEAREST).save(os.path.join(TEX, rel))


def leaf(px, cx, cy, size, col, dark):
    for y in range(-size, size + 1):
        w = int((size - abs(y)) * 0.6) + 1
        for x in range(-w, w + 1):
            if 0 <= cx + x < 32 and 0 <= cy + y < 32: px[cx + x, cy + y] = col
    for y in range(-size + 1, size):
        if 0 <= cy + y < 32: px[cx, cy + y] = dark


# ------------------------------------------------------------------ objetos (16x16)

# Libro de Estudio: libro de piel verde con una hoja dorada.
im, px = canvas(16)
rect(px, 3, 2, 12, 13, LEAF_D); rect(px, 4, 3, 12, 12, LEAF); rect(px, 3, 2, 4, 13, BARK_D)
for y in range(4, 12): px[12, y] = (236, 228, 200, 255)
for y in range(5, 11):
    w = 2 - abs(y - 8) // 2
    for x in range(8 - w, 8 + w + 1): px[x, y] = GOLD
outline(im); save(im, "item/study_book.png")

# Recipiente de Carne: vasija de barro manchada de sangre con asas de hueso.
im, px = canvas(16)
for y in range(3, 15):
    w = [3, 4, 5, 5, 6, 6, 6, 6, 5, 5, 4, 3][y - 3]
    for x in range(8 - w, 8 + w): px[x, y] = (150, 88, 64, 255) if (x + y) % 5 else (120, 66, 48, 255)
rect(px, 6, 1, 9, 2, (150, 88, 64, 255)); rect(px, 5, 2, 10, 2, (120, 66, 48, 255))
for x, y in ((6, 6), (7, 7), (9, 9), (8, 10), (5, 9), (10, 6)): px[x, y] = FLESH
for y in range(5, 9): px[1, y] = BONE; px[14, y] = BONE
px[2, 5] = BONE; px[13, 5] = BONE; px[2, 8] = BONE; px[13, 8] = BONE
outline(im); save(im, "item/flesh_vessel.png")

# Cabeza de bestia: cráneo con colmillos.
im, px = canvas(16)
disc(px, 7.5, 6.5, 5, BONE, 16); rect(px, 4, 9, 11, 12, BONE)
for x, y in ((5, 6), (6, 6), (9, 6), (10, 6), (5, 7), (6, 7), (9, 7), (10, 7)): px[x, y] = (40, 20, 20, 255)
px[7, 9] = BONE_D; px[8, 9] = BONE_D
for x in (5, 7, 8, 10): px[x, 13] = BONE; px[x, 14] = BONE_D
outline(im); save(im, "item/beast_head.png")

# Torso de bestia: costillar de carne.
im, px = canvas(16)
for y in range(2, 14):
    w = 5 - abs(y - 8) // 3
    for x in range(8 - w, 8 + w): px[x, y] = FLESH if (y % 3) else FLESH_D
for y in range(3, 13, 3):
    for x in range(4, 12): px[x, y] = BONE
rect(px, 7, 2, 8, 13, BONE_D)
outline(im); save(im, "item/beast_torso.png")

# Patas de bestia: pata con garras.
im, px = canvas(16)
rect(px, 6, 1, 9, 9, (140, 98, 60, 255)); rect(px, 5, 9, 11, 12, (140, 98, 60, 255))
for x in (5, 7, 9, 11): px[x, 13] = BONE; px[x, 14] = BONE_D
rect(px, 7, 2, 7, 8, (110, 74, 44, 255))
outline(im); save(im, "item/beast_legs.png")

# Instinto de bestia: un ojo violeta que brilla.
im, px = canvas(16)
disc(px, 7.5, 7.5, 6, (60, 20, 80, 255), 16); disc(px, 7.5, 7.5, 4.5, PURPLE, 16)
disc(px, 7.5, 7.5, 2.2, (30, 10, 40, 255), 16); px[6, 6] = PURPLE_L; px[5, 5] = PURPLE_L
outline(im); save(im, "item/beast_instinct.png")

# ------------------------------------------------------------------ Altar de Carne (16x16)
random.seed(7)
im, px = canvas(16)
for y in range(16):
    for x in range(16):
        px[x, y] = BONE if (x // 4 + y // 4) % 2 == 0 else BONE_D
for y in range(5, 11):
    for x in range(16): px[x, y] = FLESH if random.random() > .25 else FLESH_D
for x in range(16): px[x, 5] = FLESH_D; px[x, 10] = FLESH_D
save(im, "block/flesh_altar_side.png")
im, px = canvas(16)
for y in range(16):
    for x in range(16): px[x, y] = FLESH_D if random.random() > .3 else FLESH
ring(px, 7.5, 7.5, 5, 6.2, (20, 6, 8, 255), 16)
for i in range(5):
    a = i * 2 * math.pi / 5 - math.pi / 2
    px[int(round(7.5 + math.cos(a) * 3.5)), int(round(7.5 + math.sin(a) * 3.5))] = BONE
save(im, "block/flesh_altar_top.png")
im, px = canvas(16)
for y in range(16):
    for x in range(16): px[x, y] = BONE_D if (x + y) % 3 else (150, 140, 112, 255)
save(im, "block/flesh_altar_bottom.png")

# ------------------------------------------------------------------ efecto (18x18): Precio de la Carne
im, px = canvas(18)
for y in range(18):
    for x in range(18):
        X, Y = (x - 8.5) / 6.5, (y - 8) / 6.5
        if (X * X + Y * Y - 1) ** 3 - X * X * Y * Y * Y < 0: px[x, 17 - y] = FLESH
for y in range(4, 15): px[9 if y % 2 else 8, y] = (30, 10, 10, 255)
outline(im); save(im, "mob_effect/flesh_price.png")

# ------------------------------------------------------------------ icono del conjuro (16x16): Ritual del Druida
im, px = canvas(16, (20, 30, 18, 255))
ring(px, 7.5, 7.5, 5.5, 6.8, LEAF_L, 16)
disc(px, 7.5, 7.0, 3, BONE, 16)
px[6, 6] = (20, 10, 10, 255); px[9, 6] = (20, 10, 10, 255)
rect(px, 6, 10, 9, 11, BONE_D)
for a in range(0, 360, 60):
    r = math.radians(a)
    px[int(round(7.5 + math.cos(r) * 6.2)), int(round(7.5 + math.sin(r) * 6.2))] = PURPLE_L
save(im, "gui/spell_icons/druid_ritual.png")

# ------------------------------------------------------------------ retratos (32x32 -> 64x64)

# Druida: cabeza con cornamenta de ramas y hojas.
im, px = canvas(32, (0, 0, 0, 255)); glow_bg(px, 32, (120, 170, 90), (24, 40, 26))
for side in (-1, 1):
    for k in range(9):
        x = 15.5 + side * (3 + k); y = 12 - k
        if 0 <= int(x) < 32 and 0 <= y < 32: px[int(x), y] = BARK
    leaf(px, int(15.5 + side * 9), 5, 3, LEAF, LEAF_D)
    leaf(px, int(15.5 + side * 6), 2, 2, LEAF_L, LEAF_D)
disc(px, 15.5, 17, 6, (200, 160, 120, 255), 32)
rect(px, 9, 22, 22, 31, (70, 110, 50, 255))
for x, y in ((13, 16), (18, 16)): px[x, y] = (30, 60, 30, 255)
save64(im, "gui/portrait/class/druid.png")

# Círculo de la Luna: luna llena y la silueta de un lobo.
im, px = canvas(32, (0, 0, 0, 255)); glow_bg(px, 32, (60, 70, 120), (10, 12, 30))
disc(px, 15.5, 11, 8, MOON, 32); disc(px, 18, 9, 2, (200, 206, 224, 255), 32); disc(px, 12, 14, 1.5, (200, 206, 224, 255), 32)
WOLF = (16, 18, 30, 255)
rect(px, 8, 23, 22, 26, WOLF); rect(px, 20, 19, 24, 24, WOLF); px[21, 18] = WOLF; px[23, 18] = WOLF
rect(px, 24, 21, 26, 22, WOLF)
for x in (9, 12, 18, 21): rect(px, x, 27, x, 30, WOLF)
rect(px, 5, 22, 8, 23, WOLF)
save64(im, "gui/portrait/subclass/moon_circle.png")

# Forma Sobrenatural: ojo de bestia verde con chispas.
im, px = canvas(32, (0, 0, 0, 255)); glow_bg(px, 32, (60, 110, 60), (10, 20, 12))
for y in range(32):
    for x in range(32):
        if ((x - 15.5) / 12) ** 2 + ((y - 15.5) / 6.5) ** 2 <= 1: px[x, y] = (210, 230, 120, 255)
rect(px, 14, 10, 17, 21, (20, 30, 10, 255))
random.seed(3)
for _ in range(18):
    x, y = random.randrange(32), random.randrange(32)
    if px[x, y][1] < 140: px[x, y] = LEAF_L
save64(im, "gui/portrait/subclass/supernatural_form.png")

# Escultor: carne cosida con puntadas de hueso.
im, px = canvas(32, (0, 0, 0, 255)); glow_bg(px, 32, (110, 30, 34), (24, 6, 8))
for y in range(6, 27):
    for x in range(6, 27):
        if math.hypot(x - 15.5, y - 16) < 10: px[x, y] = FLESH if (x + y) % 4 else FLESH_D
for y in range(8, 25): px[15, y] = FLESH_D; px[16, y] = FLESH_D
for y in range(9, 25, 3):
    rect(px, 13, y, 18, y, BONE)
disc(px, 10, 13, 1.5, (240, 220, 120, 255), 32); disc(px, 21, 19, 1.5, (120, 200, 240, 255), 32)
save64(im, "gui/portrait/subclass/sculptor.png")
print("Texturas del Druida generadas")
