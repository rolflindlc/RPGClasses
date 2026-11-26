# Texturas provisionales del Maestro del Giro: textura de cada bola (16x16, la usa el modelo voxel), icono de la
# bandolera (16x16), icono del efecto Giro Dorado (18x18) y retratos de la clase y sus subclases (32x32 duplicados a
# 64x64). El arte final será de Laira.
# Distribución de la textura de bola: núcleo (0,0)-(8,8), núcleo oscuro (0,8)-(8,16), anillo (8,0)-(16,4) y anillo
# dorado (8,4)-(16,8). Debe coincidir con client/spin/VoxelBall.java.
# Uso: python3 -I tools/art/make_spin.py src/main/resources
import math, os, sys
from PIL import Image

OUT = sys.argv[1]
NS = "rites_and_paths"
TEX = os.path.join(OUT, "assets", NS, "textures")
for d in ("item/sphere", "mob_effect", "gui/portrait/class", "gui/portrait/subclass"):
    os.makedirs(os.path.join(TEX, d), exist_ok=True)

T = (0, 0, 0, 0)
GOLD = (236, 196, 92, 255); GOLD_L = (255, 236, 150, 255); GOLD_D = (170, 128, 46, 255)

# Material: (base, claro, oscuro, color del anillo)
MATERIALS = {
    "iron": ((200, 200, 204), (236, 236, 240), (120, 120, 128), (150, 150, 158)),
    "diamond": ((92, 219, 213), (190, 255, 250), (36, 140, 140), (60, 170, 170)),
    "netherite": ((76, 64, 68), (120, 104, 108), (40, 32, 36), (150, 90, 60)),
    "slime": ((124, 203, 106), (190, 240, 170), (70, 140, 60), (90, 170, 80)),
    "copper": ((217, 130, 79), (245, 180, 130), (150, 80, 50), (90, 170, 150)),
    "amethyst": ((167, 123, 219), (220, 190, 250), (100, 70, 150), (200, 160, 240)),
    "ender_pearl": ((46, 155, 131), (120, 220, 200), (20, 80, 70), (30, 60, 90)),
    "gold": ((242, 209, 74), (255, 245, 170), (180, 140, 30), (220, 170, 40)),
    "redstone": ((208, 40, 30), (255, 110, 90), (120, 10, 10), (90, 80, 80)),
    "dripstone": ((158, 128, 102), (200, 175, 150), (100, 80, 64), (120, 100, 84)),
    # Mejoras nuevas: solo se usa su anillo (el núcleo es el del tier).
    "flint": ((85, 82, 78), (150, 146, 140), (40, 38, 36), (110, 106, 100)),
    "obsidian": ((42, 30, 61), (100, 80, 140), (16, 10, 26), (70, 50, 110)),
}


def rgba(c, a=255): return (c[0], c[1], c[2], a)


def mix(a, b, t): return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3)) + (255,)


def sphere_texture(mid):
    base, light, dark, ring = MATERIALS[mid]
    im = Image.new("RGBA", (16, 16), T)
    px = im.load()
    # Núcleo: degradado con brillo arriba a la izquierda y algo de ruido "pixelado".
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - 2, y - 2) / 9
            c = mix(light, base, min(1, d * 1.4)) if d < 0.5 else mix(base, dark, min(1, (d - 0.5) * 1.6))
            if (x * 7 + y * 3) % 11 == 0: c = mix(c, dark, 0.35)
            if mid == "diamond" and (x + y) % 4 == 0: c = rgba(light)
            if mid == "redstone" and (x * 3 + y) % 7 == 0: c = (255, 80, 60, 255)
            if mid == "ender_pearl" and 2 <= x <= 5 and 2 <= y <= 5: c = mix(c, (10, 30, 30), 0.5)
            if mid == "slime" and 2 <= x <= 5 and 2 <= y <= 5: c = mix(c, (60, 120, 50), 0.4)
            px[x, y] = c
    # Núcleo oscuro (cubo interior): más apagado.
    for y in range(8):
        for x in range(8):
            px[x, y + 8] = mix(px[x, y], dark, 0.45)
    # Anillo y anillo dorado: bandas con remaches.
    for y in range(4):
        for x in range(8):
            c = rgba(ring) if y in (1, 2) else mix(ring, dark, 0.4)
            if x % 4 == 0 and y in (1, 2): c = mix(ring, (255, 255, 255), 0.4)
            px[8 + x, y] = c
            g = GOLD if y in (1, 2) else GOLD_D
            if x % 4 == 0 and y in (1, 2): g = GOLD_L
            px[8 + x, 4 + y] = g
    im.save(os.path.join(TEX, "item", "sphere", mid + ".png"))


for m in MATERIALS: sphere_texture(m)

# Bandolera: correa de cuero en diagonal con tres bolas enganchadas.
im = Image.new("RGBA", (16, 16), T); px = im.load()
LEATHER = (139, 90, 52, 255); LEATHER_D = (96, 60, 34, 255); BUCKLE = (210, 200, 160, 255)
for i in range(16):
    for w in range(3):
        x, y = i, 15 - i - 1 + w
        if 0 <= y < 16: px[x, y] = LEATHER if w == 1 else LEATHER_D
for (cx, cy, mid) in ((4, 9, "iron"), (8, 5, "gold"), (11, 10, "diamond")):
    base, light, dark, _ = MATERIALS[mid]
    for y in range(cy - 2, cy + 2):
        for x in range(cx - 2, cx + 2):
            if (x - cx + 0.5) ** 2 + (y - cy + 0.5) ** 2 <= 4.6:
                px[x, y] = rgba(light) if (x < cx and y < cy) else rgba(base) if (x - cx + y - cy) < 1 else rgba(dark)
px[1, 13] = BUCKLE; px[2, 13] = BUCKLE; px[1, 12] = BUCKLE
im.save(os.path.join(TEX, "item", "sphere_bandolier.png"))

# Efecto Giro Dorado (18x18): espiral áurea.
im = Image.new("RGBA", (18, 18), T); px = im.load()
phi = (1 + 5 ** 0.5) / 2
for i in range(220):
    t = i / 220 * 4 * math.pi
    r = 0.55 * phi ** (t / (math.pi / 2)) / phi ** 8 * 8.5
    x, y = 9 + r * math.cos(t), 9 + r * math.sin(t)
    if 0 <= int(x) < 18 and 0 <= int(y) < 18: px[int(x), int(y)] = GOLD if i % 7 else GOLD_L
px[9, 9] = GOLD_L
im.save(os.path.join(TEX, "mob_effect", "golden_spin.png"))

# Efecto Guardia Rota (18x18): escudo gris partido por una grieta morada.
im = Image.new("RGBA", (18, 18), T); px = im.load()
SH = (150, 150, 160, 255); SH_L = (200, 200, 210, 255); SH_D = (90, 90, 100, 255); CRACK = (170, 70, 220, 255)
for y in range(2, 16):
    half = 6 if y < 10 else max(1, 6 - (y - 9))
    for x in range(9 - half, 9 + half):
        edge = x in (9 - half, 9 + half - 1) or y == 2
        px[x, y] = SH_D if edge else (SH_L if x < 8 and y < 8 else SH)
for (x, y) in ((10, 2), (9, 4), (10, 5), (8, 7), (9, 8), (7, 10), (8, 11), (9, 13), (8, 14)):
    px[x, y] = CRACK
im.save(os.path.join(TEX, "mob_effect", "guard_break.png"))


# ------------------------------------------------------------------ retratos (32x32 → 64x64)
def canvas(bg_in, bg_out):
    im = Image.new("RGBA", (32, 32), T); px = im.load()
    for y in range(32):
        for x in range(32):
            t = min(1, math.hypot(x - 15.5, y - 15.5) / (32 * 0.62))
            px[x, y] = mix(bg_in, bg_out, t)
    return im, px


def ball(px, cx, cy, r, mid):
    base, light, dark, _ = MATERIALS[mid]
    for y in range(32):
        for x in range(32):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d <= r:
                px[x, y] = rgba(light) if dx < -r * 0.2 and dy < -r * 0.2 else rgba(base) if d < r * 0.75 else rgba(dark)


def ring(px, cx, cy, r, col, squash=0.4, w=1.2):
    for y in range(32):
        for x in range(32):
            d = math.hypot(x + 0.5 - cx, (y + 0.5 - cy) / squash)
            if r - w <= d <= r: px[x, y] = col


def spiral(px, cx, cy, scale, col, turns=3.0):
    for i in range(400):
        t = i / 400 * turns * 2 * math.pi
        rr = scale * math.exp(0.306 * t) / math.exp(0.306 * turns * 2 * math.pi) * 14
        x, y = cx + rr * math.cos(t), cy + rr * math.sin(t)
        if 0 <= int(x) < 32 and 0 <= int(y) < 32: px[int(x), int(y)] = col


def save64(im, rel): im.resize((64, 64), Image.NEAREST).save(os.path.join(TEX, rel))


# Maestro del Giro: bola de hierro con anillo dorado sobre una espiral áurea.
im, px = canvas((90, 70, 30), (24, 20, 30))
spiral(px, 15.5, 15.5, 1.0, GOLD_D)
ball(px, 15.5, 15.5, 6.2, "iron")
ring(px, 15.5, 15.5, 10, GOLD)
save64(im, "gui/portrait/class/spin_master.png")

# Jinete: bola lanzada con estela de velocidad y herradura.
im, px = canvas((120, 90, 50), (30, 24, 20))
for k in range(5):
    for x in range(2, 16):
        y = 12 + k * 2
        if (x + k) % 3: px[x, y] = mix((200, 180, 130), (60, 50, 40), x / 16)
ball(px, 20, 15, 5, "gold")
ring(px, 20, 15, 8, GOLD, 0.35)
for a in range(0, 181, 12):
    t = math.radians(a)
    x, y = 9 + 4 * math.cos(t), 24 + 3 * math.sin(t)
    px[int(x), int(y)] = (180, 180, 190, 255)
save64(im, "gui/portrait/subclass/spin_rider.png")

# Escáner: bola de diamante con una retícula y un punto débil rojo.
im, px = canvas((40, 80, 90), (12, 20, 30))
ball(px, 15.5, 15.5, 7, "diamond")
for i in range(32):
    if i % 2 == 0:
        px[i, 15] = (200, 255, 250, 255)
        px[15, i] = (200, 255, 250, 255)
ring(px, 15.5, 15.5, 11, (200, 255, 250, 255), 1.0, 0.9)
for (x, y) in ((19, 12), (20, 12), (19, 11), (20, 11)): px[x, y] = (255, 40, 50, 255)
save64(im, "gui/portrait/subclass/spin_scanner.png")

# Trampero de Giro: bola clavada en el suelo con chispas.
im, px = canvas((70, 60, 50), (20, 18, 16))
for y in range(22, 32):
    for x in range(32):
        px[x, y] = (90, 80, 70, 255) if (x + y) % 5 else (70, 62, 54, 255)
ball(px, 15.5, 20, 5.5, "redstone")
ring(px, 15.5, 20, 8.5, (150, 140, 130, 255), 0.3)
for (x, y) in ((8, 12), (24, 11), (12, 8), (21, 7), (6, 17), (26, 16)): px[x, y] = GOLD_L
save64(im, "gui/portrait/subclass/spin_trapper.png")
print("ok")
