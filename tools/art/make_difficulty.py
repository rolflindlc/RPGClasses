# Texturas provisionales de la dificultad por zona: iconos de efecto (18x18) de Tranquilidad y de Desafío.
# El arte final será de Laira.
# Uso: python3 -I tools/art/make_difficulty.py src/main/resources
import os, sys
from PIL import Image

OUT = sys.argv[1]
TEX = os.path.join(OUT, "assets", "rites_and_paths", "textures", "mob_effect")
os.makedirs(TEX, exist_ok=True)


def frame(inner, border):
    im = Image.new("RGBA", (18, 18), border)
    px = im.load()
    for y in range(1, 17):
        for x in range(1, 17):
            t = y / 17
            px[x, y] = tuple(int(inner[0][i] * (1 - t) + inner[1][i] * t) for i in range(3)) + (255,)
    return im


def tranquility():
    im = frame(((150, 210, 235), (96, 160, 205)), (52, 96, 138, 255))
    px, white = im.load(), (245, 250, 255, 255)
    # Dos ondas suaves: calma.
    for row, y0 in ((0, 6), (1, 11)):
        for x in range(3, 15):
            px[x, y0 + (1 if (x + row * 2) % 4 in (1, 2) else 0)] = white
    return im


def challenge():
    im = frame(((235, 110, 70), (170, 50, 36)), (92, 20, 16, 255))
    px, white = im.load(), (255, 238, 210, 255)
    # Dos chevrones hacia arriba: sube la dificultad.
    for top in (4, 9):
        for d in range(5):
            px[9 - d, top + d] = white
            px[8 + d, top + d] = white
    return im


tranquility().save(os.path.join(TEX, "tranquility.png"))
challenge().save(os.path.join(TEX, "challenge.png"))
