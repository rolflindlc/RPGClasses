#!/usr/bin/env python3
"""Iconos 9x9 de las cargas del Monje (fuego, agua, piedra), llenos y vacíos, en
src/main/resources/assets/rpgclasses/textures/gui/charges/. Uso: python3 -I tools/art/make_charges.py src/main/resources
Se pueden sustituir por otros PNG de 9x9 con el mismo nombre."""
import os, sys
from PIL import Image

# '.' transparente, 'o' contorno, 'a' color principal, 'b' brillo, 'c' sombra
SHAPES = {
 "fire": [
  "....o....",
  "...oao...",
  "..oabao..",
  ".oabbaao.",
  ".oabbbao.",
  "oaabbbaao",
  "oacabacao",
  ".occacco.",
  "..ooooo..",
 ],
 "water": [
  "....o....",
  "...oao...",
  "..oaaao..",
  ".oabaaao.",
  "oabaaaaao",
  "oabaaaaco",
  "oaaaaacco",
  ".oaacccо.".replace("о","o"),
  "..ooooo..",
 ],
 "stone": [
  "..ooooo..",
  ".oabbaao.",
  "oabbaaaco",
  "oabaacaco",
  "oaaaaacco",
  "oacaaacco",
  "oaccaccco",
  ".occcccо.".replace("о","o"),
  "..ooooo..",
 ],
}
COLORS = {
 "fire": {"o": (60, 16, 0), "a": (240, 110, 20), "b": (255, 225, 90), "c": (170, 40, 10)},
 "water": {"o": (10, 25, 70), "a": (50, 120, 230), "b": (170, 220, 255), "c": (25, 65, 160)},
 "stone": {"o": (30, 30, 34), "a": (135, 135, 140), "b": (200, 200, 205), "c": (85, 85, 92)},
}

def draw(name, full):
    img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    for y, row in enumerate(SHAPES[name]):
        for x, ch in enumerate(row):
            if ch == ".": continue
            if full:
                c = COLORS[name][ch] + (255,)
            elif ch == "o":
                c = (20, 20, 20, 255)
            else:
                g = 60 if ch == "b" else 45 if ch == "a" else 35
                c = (g, g, g, 200)
            img.putpixel((x, y), c)
    return img

def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources"
    out = os.path.join(root, "assets", "rpgclasses", "textures", "gui", "charges")
    os.makedirs(out, exist_ok=True)
    for name in SHAPES:
        for row in SHAPES[name]: assert len(row) == 9, (name, row)
        draw(name, True).save(os.path.join(out, name + ".png"))
        draw(name, False).save(os.path.join(out, name + "_empty.png"))
        print("escrito", name)

if __name__ == "__main__":
    main()
