"""Recorta la hoja de iconos de Daniel (tools/art/source/iconos.jpg) en retratos de 36x36 y efectos de 18x18.

Uso (desde la raíz del repo): python3 -I tools/art/slice_sheet.py tools/art/source/iconos.jpg /tmp/iconos
y copiar /tmp/iconos/gui/portrait y /tmp/iconos/mob_effect a src/main/resources/assets/rpgclasses/textures.
Orden de la hoja: 7 filas de 8 retratos (clase + 3 subclases, raza + 3 subrazas) y 1 fila de 14 efectos.
"""
import sys, statistics
from collections import deque
from PIL import Image

src = sys.argv[1]; outdir = sys.argv[2]
im = Image.open(src).convert('RGB'); W, H = im.size; px = im.load()
GAP = (19, 21, 34)

def isgap(c): return sum(abs(a - b) for a, b in zip(c, GAP)) < 12
def runs(vals, th):
    out = []; start = None
    for i, v in enumerate(vals):
        if v < th and start is None: start = i
        if v >= th and start is not None: out.append((start, i)); start = None
    if start is not None: out.append((start, len(vals)))
    return [r for r in out if r[1] - r[0] > 40]

rowg = [sum(isgap(px[x, y]) for x in range(0, W, 2)) / (W / 2) for y in range(H)]
cells = []
for y0, y1 in runs(rowg, 0.9):
    colg = [sum(isgap(px[x, y]) for y in range(y0, y1, 2)) / ((y1 - y0) / 2) for x in range(W)]
    cells.append([(x0, y0, x1, y1) for x0, x1 in runs(colg, 0.9)])

def extract(box, n):
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    # color de fondo de la celda: mediana del borde interior
    border = [px[x, y0 + 4] for x in range(x0 + 10, x1 - 10, 3)] + [px[x, y1 - 5] for x in range(x0 + 10, x1 - 10, 3)]
    bg = tuple(statistics.median(c[i] for c in border) for i in range(3))
    tol = 34
    isbg = lambda c: sum(abs(a - b) for a, b in zip(c, bg)) < tol or sum(abs(a - b) for a, b in zip(c, GAP)) < tol
    # relleno desde el borde de la celda: solo el fondo conectado con el borde se vuelve transparente
    seen = bytearray(w * h)
    q = deque()
    for x in range(w):
        for y in (0, h - 1):
            q.append((x, y))
    for y in range(h):
        for x in (0, w - 1):
            q.append((x, y))
    while q:
        x, y = q.popleft()
        i = y * w + x
        if seen[i]: continue
        if not isbg(px[x0 + x, y0 + y]): continue
        seen[i] = 1
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            xx, yy = x + dx, y + dy
            if 0 <= xx < w and 0 <= yy < h and not seen[yy * w + xx]:
                q.append((xx, yy))
    side = min(w, h)
    ox, oy = (w - side) / 2, (h - side) / 2
    b = side / n
    out = Image.new('RGBA', (n, n), (0, 0, 0, 0)); op = out.load()
    for j in range(n):
        for i in range(n):
            fx0, fy0 = ox + i * b, oy + j * b
            samples = []; total = 0
            for yy in range(int(fy0 + b * 0.2), int(fy0 + b * 0.8) + 1):
                for xx in range(int(fx0 + b * 0.2), int(fx0 + b * 0.8) + 1):
                    if 0 <= xx < w and 0 <= yy < h:
                        total += 1
                        if not seen[yy * w + xx]:
                            samples.append(px[x0 + xx, y0 + yy])
            if total and len(samples) > total * 0.5:
                op[i, j] = tuple(int(statistics.median(c[k] for c in samples)) for k in range(3)) + (255,)
    return out

PORTRAITS = [
    "class/mage", "subclass/battle_mage", "subclass/chaotic_evoker", "subclass/elementalist",
    "class/paladin", "subclass/blessed_guardian", "subclass/charging_juggernaut", "subclass/unyielding_bastion",
    "class/barbarian", "subclass/indestructible", "subclass/plague_corruptor", "subclass/suicidal_berserker",
    "class/rogue", "subclass/arcane_blade", "subclass/shadow_illusionist", "subclass/shadow_walker",
    "class/ranger", "subclass/agile_scout", "subclass/beast_master", "subclass/sniper",
    "class/monk", "subclass/breeze_master", "subclass/heavy_palm", "subclass/triworld_guardian",
    "race/human", "subrace/human/polyvalent", "subrace/human/specialist", "subrace/human/autodidact",
    "race/orc", "subrace/orc/stoneskin", "subrace/orc/devourer", "subrace/orc/warlord",
    "race/dwarf", "subrace/dwarf/deep", "subrace/dwarf/smith", "subrace/dwarf/demolisher",
    "race/elf", "subrace/elf/dark", "subrace/elf/arcane", "subrace/elf/sylvan",
    "race/goblin", "subrace/goblin/wolf_rider", "subrace/goblin/thief", "subrace/goblin/trapper",
    "race/triton", "subrace/triton/fisher", "subrace/triton/shaper", "subrace/triton/abyssal",
    "race/infernal", "subrace/infernal/native", "subrace/infernal/smelter", "subrace/infernal/pyromaniac",
    "race/undead", "subrace/undead/vampire", "subrace/undead/skeleton", "subrace/undead/specter",
]
EFFECTS = ["bulwark", "charging_bulwark", "immortal_fury", "plague", "taunt_guard", "berserker_rage", "toxin",
           "endurance", "condemned", "banner", "hunters_mark", "retreat", "arcane_step", "_extra_flag"]

import os
portrait_cells = [c for row in cells[:7] for c in row]
assert len(portrait_cells) == 56 and len(cells[7]) == 14, (len(portrait_cells), len(cells[7]))
for name, box in zip(PORTRAITS, portrait_cells):
    p = os.path.join(outdir, 'gui', 'portrait', name + '.png'); os.makedirs(os.path.dirname(p), exist_ok=True)
    extract(box, 36).save(p)
for name, box in zip(EFFECTS, cells[7]):
    p = os.path.join(outdir, 'mob_effect', name + '.png'); os.makedirs(os.path.dirname(p), exist_ok=True)
    extract(box, 18).save(p)
print('ok')
