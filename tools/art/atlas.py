"""Hojas de sprites de todas las texturas del mod, para repintarlas y devolverlas.

  python3 -I tools/art/atlas.py pack   src/main/resources <salida>     # crea hojas + manifiestos + índices
  python3 -I tools/art/atlas.py unpack src/main/resources <hojas> <manifiestos>   # escribe las texturas repintadas

Cada grupo (objetos, retratos, efectos...) tiene una hoja <grupo>.png, un manifiesto <grupo>.json con la
posición de cada textura y un <grupo>_indice.png con el número de cada celda. Las hojas se pueden devolver a
otra resolución (x2, x3...): 'unpack' deduce la escala por el ancho y recorta cada celda a esa escala, y si
vuelve a otra escala las texturas salen a esa resolución. Los .mcmeta no entran en las hojas y no se tocan.
Rejilla: celdas alineadas arriba a la izquierda, con 2 px de margen entre ellas y alrededor (se multiplica por la escala).
"""
import json, math, os, sys
from PIL import Image, ImageDraw

GUT = 2
GROUPS = [  # (nombre, prefijos de carpeta dentro de textures/)
    ("objetos", ["item"]),
    ("armaduras", ["models/armor"]),
    ("efectos", ["mob_effect"]),
    ("interfaz", ["gui/charges", "gui/spell_icons", "gui/ui"]),  # + los PNG sueltos de gui/
    ("retratos_clase", ["gui/portrait/class", "gui/portrait/subclass"]),
    ("retratos_raza", ["gui/portrait/race", "gui/portrait/subrace"]),
]

def collect(tex):
    files = []
    for r, _, fs in os.walk(tex):
        for f in fs:
            if f.endswith(".png"): files.append(os.path.relpath(os.path.join(r, f), tex).replace(os.sep, "/"))
    return sorted(files)

def group_of(rel):
    for g, prefixes in GROUPS:
        if any(rel.startswith(p + "/") for p in prefixes): return g
    if rel.startswith("gui/") and rel.count("/") == 1: return "interfaz"
    raise SystemExit("textura sin grupo: " + rel)

def layout(items):
    cw = max(w for _, w, h, _ in items); ch = max(h for _, w, h, _ in items)
    cols = max(1, min(len(items), round(math.sqrt(len(items) * ch / cw * 1.6)))) if len(items) > 1 else 1
    rows = math.ceil(len(items) / cols)
    return cw, ch, cols, rows

def pack(res, out):
    tex = os.path.join(res, "assets", "rites_and_paths", "textures")
    os.makedirs(out, exist_ok=True)
    by = {g: [] for g, _ in GROUPS}
    for rel in collect(tex):
        im = Image.open(os.path.join(tex, rel))
        by[group_of(rel)].append((rel, im.size[0], im.size[1], im.mode))
    total = 0
    for g, items in by.items():
        if not items: continue
        cw, ch, cols, rows = layout(items)
        W, H = GUT + cols * (cw + GUT), GUT + rows * (ch + GUT)
        sheet = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        cells = []
        for i, (rel, w, h, mode) in enumerate(items):
            x, y = GUT + (i % cols) * (cw + GUT), GUT + (i // cols) * (ch + GUT)
            sheet.paste(Image.open(os.path.join(tex, rel)).convert("RGBA"), (x, y))
            cells.append({"n": i, "path": rel, "x": x, "y": y, "w": w, "h": h, "mode": mode})
        sheet.save(os.path.join(out, g + ".png"))
        json.dump({"group": g, "width": W, "height": H, "cell_w": cw, "cell_h": ch, "cols": cols, "rows": rows,
                   "gutter": GUT, "cells": cells}, open(os.path.join(out, g + ".json"), "w"), indent=1)
        # índice numerado, ampliado y con fondo de ajedrez para ver el alfa
        s = max(1, min(8, 1400 // W)) if W < 1400 else 1
        idx = Image.new("RGB", (W * s, H * s))
        d = ImageDraw.Draw(idx)
        for yy in range(0, H * s, 8):
            for xx in range(0, W * s, 8):
                d.rectangle([xx, yy, xx + 7, yy + 7], fill=(58, 58, 58) if (xx // 8 + yy // 8) % 2 else (74, 74, 74))
        idx.paste(sheet.resize((W * s, H * s), Image.NEAREST), (0, 0), sheet.resize((W * s, H * s), Image.NEAREST))
        for c in cells:
            x0, y0 = c["x"] * s, c["y"] * s
            d.rectangle([x0 - 1, y0 - 1, x0 + c["w"] * s, y0 + c["h"] * s], outline=(255, 70, 70))
            d.text((x0 + 2, y0 + 1), str(c["n"]), fill=(255, 255, 0))
        idx.save(os.path.join(out, g + "_indice.png"))
        print(g, len(items), "texturas,", f"{W}x{H}")
        total += len(items)
    print("total", total)

def unpack(res, sheets, mans):
    tex = os.path.join(res, "assets", "rites_and_paths", "textures")
    n = 0
    for g, _ in GROUPS:
        mp = os.path.join(mans, g + ".json")
        if not os.path.exists(mp): continue
        m = json.load(open(mp)); sp = os.path.join(sheets, g + ".png")
        if not os.path.exists(sp): print("falta la hoja", g); continue
        sheet = Image.open(sp).convert("RGBA")
        sx, sy = sheet.size[0] / m["width"], sheet.size[1] / m["height"]
        if abs(sx - sy) > 1e-6 or sx != int(sx) or sx < 1: raise SystemExit(f"{g}: la hoja debe ser un múltiplo entero ({sheet.size} frente a {m['width']}x{m['height']})")
        s = int(sx)
        for c in m["cells"]:
            im = sheet.crop((c["x"] * s, c["y"] * s, (c["x"] + c["w"]) * s, (c["y"] + c["h"]) * s))
            if c["mode"] == "RGB": im = im.convert("RGB")
            dst = os.path.join(tex, c["path"]); os.makedirs(os.path.dirname(dst), exist_ok=True)
            im.save(dst); n += 1
        print(g, "x%d" % s)
    print("texturas escritas:", n)

if __name__ == "__main__":
    a = sys.argv[1:]
    if a[:1] == ["pack"] and len(a) == 3: pack(a[1], a[2])
    elif a[:1] == ["unpack"] and len(a) == 4: unpack(a[1], a[2], a[3])
    else: print(__doc__)
