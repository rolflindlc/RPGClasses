#!/usr/bin/env python3
"""Genera las texturas de interfaz del mod (panel, cabecera y botones-tarjeta) en
src/main/resources/assets/rites_and_paths/textures/gui/ui/. Se dibujan de 9 porciones (ver UiStyle.nine).

Uso: python3 -I tools/art/make_ui.py src/main/resources
Para cambiar el estilo basta con editar los colores de aquí o sustituir los PNG por los tuyos
(mismas medidas: panel 24x24 borde 6, cabecera 24x16 borde 4, botones 20x20 borde 3).
"""
import os
import sys

from PIL import Image

GOLD = (230, 195, 90)


def canvas(w, h, fill):
    return Image.new("RGBA", (w, h), fill)


def px(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def rect_frame(img, x0, y0, x1, y1, c):
    for x in range(x0, x1 + 1):
        px(img, x, y0, c)
        px(img, x, y1, c)
    for y in range(y0, y1 + 1):
        px(img, x0, y, c)
        px(img, x1, y, c)


def bevel(img, x0, y0, x1, y1, light, dark):
    for x in range(x0, x1 + 1):
        px(img, x, y0, light)
        px(img, x, y1, dark)
    for y in range(y0, y1 + 1):
        px(img, x0, y, light)
        px(img, x1, y, dark)


def panel():
    img = canvas(24, 24, (16, 16, 20, 176))
    rect_frame(img, 0, 0, 23, 23, (0, 0, 0, 255))
    bevel(img, 1, 1, 22, 22, (96, 96, 110, 255), (34, 34, 42, 255))
    rect_frame(img, 2, 2, 21, 21, (44, 44, 56, 230))
    # remaches dorados en las esquinas
    for cx, cy in ((3, 3), (19, 3), (3, 19), (19, 19)):
        for dx in (0, 1):
            for dy in (0, 1):
                px(img, cx + dx, cy + dy, GOLD + (255,))
    return img


def header():
    img = Image.new("RGBA", (24, 16))
    for y in range(16):
        t = y / 15
        c = (int(48 - 24 * t), int(48 - 24 * t), int(64 - 32 * t), 192)
        for x in range(24):
            img.putpixel((x, y), c)
    for x in range(24):
        px(img, x, 15, GOLD + (150,))
        px(img, x, 0, (110, 110, 128, 200))
    return img


def button(fill, frame, inner):
    img = canvas(20, 20, fill)
    rect_frame(img, 0, 0, 19, 19, frame)
    rect_frame(img, 1, 1, 18, 18, inner)
    # esquinas redondeadas
    for x, y in ((0, 0), (19, 0), (0, 19), (19, 19)):
        px(img, x, y, (0, 0, 0, 0))
    return img


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources"
    out = os.path.join(root, "assets", "rites_and_paths", "textures", "gui", "ui")
    os.makedirs(out, exist_ok=True)
    files = {
        "panel": panel(),
        "header": header(),
        "button": button((20, 20, 24, 160), (58, 58, 68, 255), (30, 30, 38, 200)),
        "button_hover": button((48, 48, 64, 192), (255, 255, 255, 255), (86, 86, 110, 220)),
        "button_selected": button((64, 56, 24, 192), GOLD + (255,), (150, 125, 50, 230)),
    }
    for name, img in files.items():
        img.save(os.path.join(out, name + ".png"))
        print("escrito", name)


if __name__ == "__main__":
    main()
