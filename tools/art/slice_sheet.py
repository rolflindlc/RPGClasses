"""Recorta la hoja de iconos de Daniel a resolución completa, con fondo (sin reescalar).

Retratos: 236x236 (centro de cada celda). Efectos: 133x133.
Uso: python3 -I tools/art/slice_sheet.py tools/art/source/iconos.jpg <salida>
"""
import os, sys
from PIL import Image

src, outdir = sys.argv[1], sys.argv[2]
im = Image.open(src).convert('RGB'); W, H = im.size; px = im.load()
GAP = (19, 21, 34)
isgap = lambda c: sum(abs(a - b) for a, b in zip(c, GAP)) < 12

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

def crop(box, side):
    x0, y0, x1, y1 = box
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    return im.crop((cx - side // 2, cy - side // 2, cx - side // 2 + side, cy - side // 2 + side))

PORTRAITS = [
    "class/mage", "subclass/battle_mage", None, "subclass/elementalist",  # None: Evocador Caótico (retirado; el Cronomante tiene retrato propio)
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
           "endurance", "condemned", "banner", "hunters_mark", "retreat", "arcane_step", None]

portrait_cells = [c for row in cells[:7] for c in row]
assert len(portrait_cells) == 56 and len(cells[7]) == 14
for name, box in zip(PORTRAITS, portrait_cells):
    if name is None: continue
    p = os.path.join(outdir, 'gui', 'portrait', name + '.png'); os.makedirs(os.path.dirname(p), exist_ok=True)
    crop(box, 236).save(p)
    with open(p + '.mcmeta', 'w') as f:
        f.write('{"texture": {"blur": true}}\n')
for name, box in zip(EFFECTS, cells[7]):
    if name is None: continue
    p = os.path.join(outdir, 'mob_effect', name + '.png'); os.makedirs(os.path.dirname(p), exist_ok=True)
    crop(box, 133).save(p)
print('ok')
