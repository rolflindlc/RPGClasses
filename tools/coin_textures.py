"""Genera las dos caras de la moneda de la Apuesta con la Muerte (64x64):
anj (sobrevive) y calavera (muere). Uso: python3 -I tools/coin_textures.py src/main/resources"""
import sys, math
from PIL import Image, ImageDraw, ImageFilter

S = 512          # se dibuja grande y se reduce (bordes suaves)
OUT = 64
GOLD_HI, GOLD, GOLD_LO = (255, 226, 120), (232, 178, 52), (150, 98, 18)
ENGRAVE = (92, 52, 8)

def coin():
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = S / 2
    # borde oscuro y canto
    d.ellipse([8, 8, S - 8, S - 8], fill=GOLD_LO + (255,))
    # cara con degradado radial (luz arriba a la izquierda)
    face = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = face.load()
    r = c - 40
    for y in range(S):
        for x in range(S):
            dx, dy = x - c, y - c
            dist = math.hypot(dx, dy)
            if dist <= r:
                k = max(0.0, min(1.0, 0.55 - (dx + dy) / (2.6 * r)))
                col = tuple(int(GOLD[i] + (GOLD_HI[i] - GOLD[i]) * k) if k > 0.5 else
                            int(GOLD_LO[i] + (GOLD[i] - GOLD_LO[i]) * (k / 0.5 * 0.6 + 0.4)) for i in range(3))
                px[x, y] = col + (255,)
    img.alpha_composite(face)
    # anillo grabado y puntos del canto
    d.ellipse([c - r + 26, c - r + 26, c + r - 26, c + r - 26], outline=ENGRAVE + (255,), width=10)
    for i in range(24):
        a = i * math.tau / 24
        x, y = c + math.cos(a) * (r + 18), c + math.sin(a) * (r + 18)
        d.ellipse([x - 7, y - 7, x + 7, y + 7], fill=GOLD_HI + (255,))
    return img

def ankh(img):
    d = ImageDraw.Draw(img)
    c = S / 2
    col = ENGRAVE + (255,)
    # lazo
    d.ellipse([c - 62, 112, c + 62, 268], outline=col, width=34)
    # brazos
    d.rounded_rectangle([c - 118, 262, c + 118, 302], radius=14, fill=col)
    # tronco que se ensancha hacia abajo
    d.polygon([(c - 20, 262), (c + 20, 262), (c + 36, 416), (c - 36, 416)], fill=col)
    # brillo verde de vida
    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([c - 30, 150, c + 30, 230], fill=(90, 230, 140, 150))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(18)))

def skull(img):
    d = ImageDraw.Draw(img)
    c = S / 2
    col = ENGRAVE + (255,)
    # cráneo y mandíbula
    d.ellipse([c - 120, 104, c + 120, 330], fill=col)
    d.rounded_rectangle([c - 74, 290, c + 74, 392], radius=26, fill=col)
    # ojos (rojo apagado) y nariz recortados en oro
    hole = GOLD_LO + (255,)
    for sx in (-1, 1):
        d.ellipse([c + sx * 58 - 40, 196, c + sx * 58 + 40, 268], fill=(120, 16, 10, 255))
    d.polygon([(c, 270), (c - 18, 304), (c + 18, 304)], fill=hole)
    # dientes
    for i in range(-2, 3):
        x = c + i * 26
        d.rectangle([x - 4, 330, x + 4, 386], fill=hole)
    # brillo rojo en las cuencas
    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for sx in (-1, 1):
        gd.ellipse([c + sx * 58 - 22, 214, c + sx * 58 + 22, 252], fill=(255, 60, 30, 170))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(10)))

def save(img, path):
    img.resize((OUT, OUT), Image.LANCZOS).save(path)

if __name__ == "__main__":
    base = sys.argv[1] + "/assets/rpgclasses/textures/gui/"
    a = coin(); ankh(a); save(a, base + "coin_ankh.png")
    s = coin(); skull(s); save(s, base + "coin_skull.png")
    print("ok")
