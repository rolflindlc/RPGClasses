# Texturas del Equipo de Clase (rev. 2.20): iconos de 16x16 y capas de armadura de 64x32, dibujadas con máscaras
# de píxeles y teñidas por material. Uso: python3 -I tools/art/make_gear.py src/main/resources
import os, sys
from PIL import Image

OUT = sys.argv[1]
NS = "rpgclasses"
ITEM = os.path.join(OUT, "assets", NS, "textures", "item")
ARMOR = os.path.join(OUT, "assets", NS, "textures", "models", "armor")
os.makedirs(ITEM, exist_ok=True)
os.makedirs(ARMOR, exist_ok=True)

def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)

def mix(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))

# Color base por material (armaduras y armas).
MAT = {"leather": (150, 96, 50), "chain": (160, 162, 168), "iron": (206, 208, 214), "gold": (240, 196, 62),
       "diamond": (84, 222, 214), "netherite": (78, 66, 72), "wood": (156, 118, 70), "stone": (130, 130, 130),
       "dragon_fire": (196, 52, 30), "dragon_ice": (118, 182, 236), "dragon_lightning": (128, 82, 200),
       "dragonbone": (232, 226, 206), "dragonsteel_fire": (226, 104, 44), "dragonsteel_ice": (178, 222, 246),
       "dragonsteel_lightning": (170, 120, 240)}
STRAP = (104, 68, 36)
CLOTH = (232, 222, 196)

def palette(base):
    return {"o": shade(base, 0.45), "d": shade(base, 0.72), "b": base, "l": shade(mix(base, (255, 255, 255), 0.35), 1.0)}

def draw(mask, pal, extra=None):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    colors = dict(pal)
    if extra: colors.update(extra)
    for y, row in enumerate(mask):
        for x, ch in enumerate(row.ljust(16, ".")):
            if ch in colors: img.putpixel((x, y), colors[ch] + (255,))
    return img

# --- máscaras de iconos (o contorno, d oscuro, b base, l brillo, s correa/tela, w madera, c cadena)
M = {
"barbarian_helmet": [
"................",
".o............o.",
".lo..........ol.",
"..bo........ob..",
"..bbo......obb..",
"...dooooooood...",
"...sbbbbbbbbs...",
"...sbllbbllbs...",
"...ssssssssss...",
"...s........s...",
"...s........s...",
"................"],
"barbarian_chestplate": [
"................",
"..oo........oo..",
".obbo......obbo.",
".obds......sdbo.",
"..os.s....s.so..",
"....s.s..s.s....",
".....s.ss.s.....",
"......sbbs......",
".....s.ss.s.....",
"....s.s..s.s....",
"...s.s....s.s...",
"...ssssssssss...",
"...sdbbsbbbds...",
"................"],
"barbarian_leggings": [
"................",
"...ssssssssss...",
"...sbbbsbbbbs...",
"....dbb..bbd....",
"....sss..sss....",
"....obo..obo....",
"....sss..sss....",
"....obo..obo....",
"....sss..sss....",
"....obo..obo....",
"................"],
"barbarian_boots": [
"................",
"................",
"................",
"................",
"...sss....sss...",
"...obo....obo...",
"...sss....sss...",
"...obo....obo...",
"..obbbo..obbbo..",
"..ddddd..ddddd..",
"................"],
"monk_helmet": [
"................",
"................",
"................",
"................",
"....ssssssss....",
"...sbbbbbbbbs...",
"...ssssssssss...",
"............sb..",
".............sb.",
"..............s.",
"................"],
"monk_chestplate": [
"................",
"...sss....sss...",
"..sssss..sssss..",
".sssssbssbsssss.",
".ssssssbbssssss.",
".sss.sssssss.ss.",
".ss..sssssss..s.",
".....sssssss....",
".....bbbbbbb....",
".....dbbbbbd....",
".....sssssss....",
".....sssssss....",
".....ssssssss...",
"................"],
"monk_leggings": [
"................",
"....bbbbbbbb....",
"....ssssssss....",
"....sss..sss....",
"....sss..sss....",
"....sss..sss....",
"...ssss..ssss...",
"...ssss..ssss...",
"...bbbb..bbbb...",
"................"],
"monk_boots": [
"................",
"................",
"................",
"................",
"................",
"....b......b....",
"...sbs....sbs...",
"...b.b....b.b...",
"..obbbo..obbbo..",
"..ddddd..ddddd..",
"................"],
"nunchaku": [
"................",
"............oo..",
"...........olbo.",
"..........olbo..",
".........olbo...",
"........obbo....",
".......c.oo.....",
"......c.........",
".....c..........",
"....c...........",
"...oo...........",
"..obbo..........",
"..olbo..........",
"..olbo..........",
"..obbo..........",
"...oo..........."],
"sai": [
"................",
".............o..",
"............olo.",
"...........olo..",
"..o.......olo...",
".olo.....olo....",
"..olo...olo.....",
"...olooolo......",
"....obbbo.......",
"....oboo........",
"...owo..........",
"..owo...........",
".owo............",
".oo.............",
"................"],
"bo": [
"..............ll",
".............lbo",
"............obo.",
"...........wwo..",
"..........wwo...",
".........wwo....",
"........wwo.....",
".......wwo......",
"......wwo.......",
".....wwo........",
"....wwo.........",
"...wwo..........",
"..obo...........",
".obl............",
"lb..............",
"................"],
"knuckles": [
"................",
"................",
"................",
"................",
"...oooooooooo...",
"..olbolbolbolo..",
"..obbobbobbobo..",
"..oddoddoddodo..",
"...oooooooooo...",
"....s......s....",
"....ss....ss....",
".....ssssss.....",
"................"],
"cured_strap": [
"................",
"................",
"..........ss....",
".........sdds...",
"........sddds...",
".......sdds.....",
"......sdds......",
".....sdds.......",
"....sdds........",
"...sdds.........",
"..ooo...........",
"..oso...........",
"..ooo...........",
"................"],
"martial_cloth": [
"................",
"................",
"...ssssssssss...",
"..sbsssssssbs...",
"..ssssssssssss..",
"..sssbsssssss...",
"..ssssssssbsss..",
"..sbsssssssss...",
"..ssssssssssss..",
"...dddddddddd...",
"................"],
"draconic_forge_template": [
"................",
"..oooooooooooo..",
"..obbbbbbbbbbo..",
"..obdddddddbbo..",
"..obdlllllldbo..",
"..obdl.ss.ldbo..",
"..obdl.ss.ldbo..",
"..obdlllllldbo..",
"..obdddddddbbo..",
"..obbbbbbbbbbo..",
"..oooooooooooo..",
"................"],
}

ARMOR_TIERS = ["leather", "chain", "iron", "gold", "diamond", "netherite", "dragon_fire", "dragon_ice", "dragon_lightning",
               "dragonsteel_fire", "dragonsteel_ice", "dragonsteel_lightning"]
WEAPON_TIERS = ["wood", "stone", "iron", "gold", "diamond", "netherite", "dragonbone", "dragonsteel_fire", "dragonsteel_ice", "dragonsteel_lightning"]
PIECES = ["helmet", "chestplate", "leggings", "boots"]

def pad(mask):
    rows = [r.ljust(16, ".")[:16] for r in mask]
    top = (16 - len(rows)) // 2
    return ["." * 16] * top + rows + ["." * 16] * (16 - len(rows) - top)

def monk_cloth(tier):
    base = MAT[tier]
    return mix(CLOTH, base, 0.25 if tier in ("leather", "chain", "iron") else 0.45)

for tier in ARMOR_TIERS:
    pal = palette(MAT[tier])
    for p in PIECES:
        draw(pad(M["barbarian_" + p]), pal, {"s": STRAP}).save(os.path.join(ITEM, f"barbarian_{tier}_{p}.png"))
        draw(pad(M["monk_" + p]), pal, {"s": monk_cloth(tier)}).save(os.path.join(ITEM, f"monk_{tier}_{p}.png"))
for tier in WEAPON_TIERS:
    pal = palette(MAT[tier])
    wood = (124, 86, 48) if tier != "dragonbone" else MAT["dragonbone"]
    for k in ("nunchaku", "sai", "bo", "knuckles"):
        extra = {"w": wood, "c": (120, 120, 126), "s": STRAP}
        draw(pad(M[k]), pal, extra).save(os.path.join(ITEM, f"{k}_{tier}.png"))
draw(pad(M["cured_strap"]), palette(STRAP), {"s": shade(STRAP, 0.6)}).save(os.path.join(ITEM, "cured_strap.png"))
draw(pad(M["martial_cloth"]), palette(CLOTH), {"s": CLOTH}).save(os.path.join(ITEM, "martial_cloth.png"))
draw(pad(M["draconic_forge_template"]), palette((60, 40, 70)), {"s": (230, 90, 40)}).save(os.path.join(ITEM, "draconic_forge_template.png"))

# --- capas de armadura (UV estándar del modelo humanoide de 64x32)
def rect(img, x0, y0, x1, y1, c):
    for x in range(x0, x1):
        for y in range(y0, y1):
            img.putpixel((x, y), c + (255,))

def faces(x, y, w, h, d):
    """Caras laterales de una caja (derecha, frente, izquierda, detrás) desde su origen UV."""
    return [(x, y + d, d, h), (x + d, y + d, w, h), (x + d + w, y + d, d, h), (x + 2 * d + w, y + d, w, h)]

HEAD, BODY, ARM, LEG = (0, 0, 8, 8, 8), (16, 16, 8, 12, 4), (40, 16, 4, 12, 4), (0, 16, 4, 12, 4)

def band(img, box, rows, c):
    x, y, w, h, d = box
    for fx, fy, fw, fh in faces(x, y, w, h, d):
        for r in rows:
            if 0 <= r < fh: rect(img, fx, fy + r, fx + fw, fy + r + 1, c)

def top(img, box, c):
    x, y, w, h, d = box
    rect(img, x + d, y, x + d + w, y + d, c)

def scales(img, box, rows, c1, c2):
    x, y, w, h, d = box
    for fx, fy, fw, fh in faces(x, y, w, h, d):
        for r in rows:
            for i in range(fw):
                img.putpixel((fx + i, fy + r), (c1 if (i + r) % 2 else c2) + (255,))

def barbarian_layers(tier):
    pal = palette(MAT[tier])
    high = ARMOR_TIERS.index(tier) >= ARMOR_TIERS.index("netherite")
    dragon = tier.startswith("dragon")
    l1 = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    # Casco: cinta y, desde la netherita, placa superior.
    band(l1, HEAD, [2, 3], STRAP)
    band(l1, HEAD, [3], pal["b"])
    if high:
        top(l1, HEAD, pal["d"])
        band(l1, HEAD, [0, 1], pal["b"])
    # Torso: correas cruzadas en el frente y la espalda, cinturón y placa central en tiers altos.
    x, y, w, h, d = BODY
    for fx, fy, fw, fh in faces(x, y, w, h, d)[1::2]:
        for i in range(fh):
            for dx in (0, 1):
                a = int(i * (fw - 2) / (fh - 1)) + dx
                l1.putpixel((fx + a, fy + i), STRAP + (255,))
                l1.putpixel((fx + fw - 1 - a, fy + i), STRAP + (255,))
    band(l1, BODY, [10, 11], STRAP)
    band(l1, BODY, [10], pal["d"])
    if high:
        fx, fy = x + d, y + d
        rect(l1, fx + 2, fy + 3, fx + 6, fy + 7, pal["b"])
        if dragon: scales(l1, BODY, [2, 3, 4], pal["l"], pal["b"])
    # Hombreras.
    top(l1, ARM, pal["b"])
    band(l1, ARM, [0, 1, 2], pal["b"] if high else pal["d"])
    band(l1, ARM, [3], STRAP)
    # Botas (capa 1, zona de piernas).
    band(l1, LEG, [10, 11], pal["d"])
    band(l1, LEG, [8], STRAP)
    l2 = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    band(l2, LEG, [1, 4, 7], STRAP)
    band(l2, LEG, [2, 5], pal["b"] if high else STRAP)
    band(l2, BODY, [10, 11], STRAP)
    fx, fy = BODY[0] + BODY[4], BODY[1] + BODY[4]
    rect(l2, fx + 2, fy + 8, fx + 6, fy + 12, pal["d"])
    return l1, l2

def monk_layers(tier):
    pal = palette(MAT[tier])
    cloth = monk_cloth(tier)
    dark = shade(cloth, 0.78)
    high = ARMOR_TIERS.index(tier) >= ARMOR_TIERS.index("netherite")
    dragon = tier.startswith("dragon")
    l1 = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    band(l1, HEAD, [1, 2], pal["b"])
    band(l1, HEAD, [2], dark)
    # Gi: torso entero, solapas en V, faja (obi).
    x, y, w, h, d = BODY
    for fx, fy, fw, fh in faces(x, y, w, h, d):
        rect(l1, fx, fy, fx + fw, fy + fh, cloth)
    top(l1, BODY, cloth)
    fx, fy = x + d, y + d
    for i in range(6):
        l1.putpixel((fx + min(3, i // 2 + 0), fy + i), dark + (255,))
        l1.putpixel((fx + 7 - min(3, i // 2 + 0), fy + i), dark + (255,))
    band(l1, BODY, [7, 8], pal["b"])
    if high:
        band(l1, BODY, [0], pal["d"])  # kesa: borde del manto
        for i in range(12):
            a = int(i * 6 / 11)
            l1.putpixel((fx + a, fy + i), pal["b"] + (255,))
        if dragon: scales(l1, BODY, [3, 4, 5], pal["l"], pal["b"])
    # Mangas.
    top(l1, ARM, cloth)
    band(l1, ARM, list(range(0, 6)), cloth)
    band(l1, ARM, [5], dark)
    # Sandalias.
    band(l1, LEG, [11], pal["d"])
    band(l1, LEG, [9], pal["b"])
    l2 = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    band(l2, LEG, list(range(0, 10)), cloth)
    band(l2, LEG, [9], dark)
    band(l2, BODY, [8, 9, 10, 11], cloth)
    band(l2, BODY, [8], pal["b"])
    return l1, l2

for tier in ARMOR_TIERS:
    for cls, fn in (("barbarian", barbarian_layers), ("monk", monk_layers)):
        a, b = fn(tier)
        a.save(os.path.join(ARMOR, f"{cls}_{tier}_layer_1.png"))
        b.save(os.path.join(ARMOR, f"{cls}_{tier}_layer_2.png"))
print("texturas del equipo generadas")

# --- curios: una silueta por ranura, teñida con el color propio de cada curio (y un detalle en "g")
SLOT_MASK = {
"necklace": [
"................",
"...oo......oo...",
"..o..o....o..o..",
"..o...o..o...o..",
"...o...oo...o...",
"....o......o....",
".....o....o.....",
"......obbo......",
".....oblbbo.....",
".....obggbo.....",
".....obbbdo.....",
"......oddo......",
".......oo......."],
"ring": [
"................",
"......oooo......",
".....ogllgo.....",
"......oggo......",
".....oo..oo.....",
"....ob....bo....",
"....ob....bo....",
"....od....do....",
".....od..do.....",
"......oooo......"],
"charm": [
"................",
".......oo.......",
"......olbo......",
".....olbbbo.....",
"....olbggbbo....",
"....obbggbdo....",
".....obbbdo.....",
"......obdo......",
".......oo......."],
"belt": [
"................",
"................",
"oooooooooooooooo",
"obbbbboooobbbbbo",
"oddddoglgoodddo.",
"obbbbboggobbbbbo",
"oooooooooooooooo"],
"back": [
"................",
"....oooooooo....",
"...obbbbbbbbo...",
"...olbbbbbbbo...",
"..olbbbggbbbdo..",
"..obbbbggbbbdo..",
"..obbbbbbbbbdo..",
".obbbbbbbbbbbdo.",
".obbbbbbbbbbbdo.",
".obdbdbdbdbdbdo.",
"..o.o.o.o.o.o..."],
"bracelet": [
"................",
"................",
"....oooooooo....",
"...olbbggbbbo...",
"...obbggggbdo...",
"...olbbggbbbo...",
"....oooooooo...."],
"head": [
"................",
"......oooo......",
".....olggbo.....",
"....olbggbdo....",
"...oooooooooo...",
"...obbbbbbbbo...",
"...oddddddddo..."],
}
CURIO_COLORS = {
 "wisp_lantern": ((70, 200, 220), (180, 255, 255)), "last_breath_compass": ((200, 160, 60), (230, 60, 60)),
 "hermit_medallion": ((190, 190, 200), (200, 40, 40)), "traveler_belt": ((120, 80, 40), (220, 190, 80)),
 "frost_ring": ((210, 180, 60), (150, 210, 255)), "pixie_bell": ((230, 200, 80), (255, 140, 220)),
 "serpent_pendant": ((40, 130, 150), (120, 230, 200)), "cyclops_horn": ((200, 180, 140), (120, 60, 30)),
 "gorgon_eye": ((90, 140, 70), (250, 220, 60)), "storm_talisman": ((60, 60, 120), (200, 170, 255)),
 "phoenix_feather": ((230, 90, 30), (255, 220, 80)), "archmage_prism": ((150, 90, 220), (240, 220, 255)),
 "arcane_hourglass": ((80, 120, 200), (240, 210, 120)), "oath_reliquary": ((230, 190, 70), (255, 255, 220)),
 "pocket_banner": ((230, 200, 80), (180, 40, 40)), "fang_collar": ((110, 70, 40), (240, 235, 215)),
 "war_drum": ((150, 60, 40), (230, 200, 160)), "trophy_skull": ((70, 70, 70), (240, 235, 210)),
 "crow_cloak": ((40, 40, 50), (90, 90, 130)), "loaded_dice": ((235, 230, 215), (40, 40, 40)),
 "hippogryph_feather": ((220, 210, 190), (150, 110, 70)), "dragonslayer_quiver": ((110, 70, 40), (220, 70, 40)),
 "stance_rosary": ((150, 110, 200), (240, 120, 60)), "temple_bell": ((210, 170, 70), (120, 80, 30)),
 "guild_insignia": ((200, 170, 70), (90, 180, 90)), "troll_earring": ((110, 140, 90), (230, 220, 200)),
 "mine_lantern": ((90, 90, 90), (255, 210, 110)), "evergreen_leaf": ((60, 150, 60), (180, 240, 120)),
 "trinket_pouch": ((130, 90, 50), (240, 200, 60)), "tide_conch": ((230, 190, 170), (80, 170, 220)),
 "magma_heart": ((120, 30, 20), (255, 140, 30)), "midnight_veil": ((40, 30, 60), (150, 130, 200)),
}
CURIO_SLOTS = {"wisp_lantern": "charm", "last_breath_compass": "necklace", "hermit_medallion": "necklace", "traveler_belt": "belt",
 "frost_ring": "ring", "pixie_bell": "charm", "serpent_pendant": "necklace", "cyclops_horn": "charm", "gorgon_eye": "charm",
 "storm_talisman": "bracelet", "phoenix_feather": "back", "archmage_prism": "charm", "arcane_hourglass": "necklace",
 "oath_reliquary": "necklace", "pocket_banner": "back", "fang_collar": "necklace", "war_drum": "belt", "trophy_skull": "belt",
 "crow_cloak": "back", "loaded_dice": "charm", "hippogryph_feather": "charm", "dragonslayer_quiver": "back",
 "stance_rosary": "necklace", "temple_bell": "belt", "guild_insignia": "charm", "troll_earring": "necklace",
 "mine_lantern": "head", "evergreen_leaf": "charm", "trinket_pouch": "belt", "tide_conch": "necklace",
 "magma_heart": "charm", "midnight_veil": "head"}
for cid, slot in CURIO_SLOTS.items():
    base, gem = CURIO_COLORS[cid]
    draw(pad(SLOT_MASK[slot]), palette(base), {"g": gem}).save(os.path.join(ITEM, cid + ".png"))
print("iconos de curios generados")
