#!/usr/bin/env python3
"""Genera los recursos de Iron Rituals (modelos, estados, recetas, tags, botín, lang y texturas provisionales).

Uso: python3 tools/gen_assets.py   (desde la raíz del proyecto)
Las texturas son provisionales y procedurales; se pueden sustituir por arte definitivo sin tocar el código.
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
MOD = "ironrituals"
ISS = "irons_spellbooks"
ASSETS = os.path.join(ROOT, "assets", MOD)
DATA = os.path.join(ROOT, "data")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


# --------------------------------------------------------------------------- velas

CANDLES = {"arcane_candle": 7, "arcane_candle_worn": 5, "arcane_candle_stub": 3}


def candle_model(height, lit):
    wick = f"{MOD}:block/arcane_candle_wick" + ("_lit" if lit else "")
    side = [0, 16 - height, 4, 16]
    return {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "ambientocclusion": False,
        "textures": {"wax": f"{MOD}:block/arcane_candle", "wick": wick, "particle": f"{MOD}:block/arcane_candle"},
        "elements": [
            {
                "from": [6, 0, 6], "to": [10, height, 10],
                "faces": {
                    "north": {"uv": side, "texture": "#wax"},
                    "south": {"uv": side, "texture": "#wax"},
                    "east": {"uv": side, "texture": "#wax"},
                    "west": {"uv": side, "texture": "#wax"},
                    "up": {"uv": [4, 0, 8, 4], "texture": "#wax"},
                    "down": {"uv": [4, 0, 8, 4], "texture": "#wax", "cullface": "down"},
                },
            },
            {
                "from": [7.5, height, 8], "to": [8.5, height + 2, 8],
                "rotation": {"angle": 45, "axis": "y", "origin": [8, height, 8]},
                "shade": False,
                "faces": {"north": {"uv": [0, 0, 1, 2], "texture": "#wick"}, "south": {"uv": [0, 0, 1, 2], "texture": "#wick"}},
            },
            {
                "from": [8, height, 7.5], "to": [8, height + 2, 8.5],
                "rotation": {"angle": 45, "axis": "y", "origin": [8, height, 8]},
                "shade": False,
                "faces": {"east": {"uv": [0, 0, 1, 2], "texture": "#wick"}, "west": {"uv": [0, 0, 1, 2], "texture": "#wick"}},
            },
        ],
    }


def gen_candles():
    for name, h in CANDLES.items():
        write(f"{ASSETS}/models/block/{name}.json", candle_model(h, False))
        write(f"{ASSETS}/models/block/{name}_lit.json", candle_model(h, True))
        write(f"{ASSETS}/blockstates/{name}.json", {"variants": {
            "lit=false": {"model": f"{MOD}:block/{name}"},
            "lit=true": {"model": f"{MOD}:block/{name}_lit"},
        }})
        write(f"{ASSETS}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{name}"}})
        write(f"{DATA}/{MOD}/loot_tables/blocks/{name}.json", self_drop(name))


# --------------------------------------------------------------------------- núcleo

def box(frm, to, tex_side, tex_top, tex_bottom):
    def uv_side():
        return [frm[0], 16 - to[1], to[0], 16 - frm[1]]

    def uv_top():
        return [frm[0], frm[2], to[0], to[2]]
    return {
        "from": frm, "to": to,
        "faces": {
            "north": {"uv": uv_side(), "texture": tex_side},
            "south": {"uv": uv_side(), "texture": tex_side},
            "east": {"uv": uv_side(), "texture": tex_side},
            "west": {"uv": uv_side(), "texture": tex_side},
            "up": {"uv": uv_top(), "texture": tex_top},
            "down": {"uv": uv_top(), "texture": tex_bottom},
        },
    }


def gen_core():
    write(f"{ASSETS}/models/block/ritual_core.json", {
        "parent": "minecraft:block/block",
        "textures": {
            "side": f"{MOD}:block/ritual_core_side",
            "top": f"{MOD}:block/ritual_core_top",
            "bottom": f"{MOD}:block/ritual_core_bottom",
            "particle": f"{MOD}:block/ritual_core_side",
        },
        "elements": [
            box([1, 0, 1], [15, 4, 15], "#side", "#bottom", "#bottom"),
            box([3, 4, 3], [13, 10, 13], "#side", "#bottom", "#bottom"),
            box([2, 10, 2], [14, 13, 14], "#side", "#top", "#bottom"),
        ],
    })
    write(f"{ASSETS}/blockstates/ritual_core.json", {"variants": {"": {"model": f"{MOD}:block/ritual_core"}}})
    write(f"{ASSETS}/models/item/ritual_core.json", {"parent": f"{MOD}:block/ritual_core"})
    write(f"{DATA}/{MOD}/loot_tables/blocks/ritual_core.json", self_drop("ritual_core"))


def self_drop(name):
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    }


# --------------------------------------------------------------------------- daga y libro

def gen_items():
    write(f"{ASSETS}/models/item/ritual_dagger.json", {
        "parent": "minecraft:item/handheld",
        "textures": {"layer0": f"{MOD}:item/ritual_dagger"},
        "overrides": [{"predicate": {f"{MOD}:soul": 1}, "model": f"{MOD}:item/ritual_dagger_soul"}],
    })
    # Capa 1 emisiva y animada: brillo rojizo del alma (plan, 3.C).
    write(f"{ASSETS}/models/item/ritual_dagger_soul.json", {
        "parent": "minecraft:item/handheld",
        "loader": "forge:item_layers",
        "textures": {"layer0": f"{MOD}:item/ritual_dagger", "layer1": f"{MOD}:item/ritual_dagger_soul_glow"},
        "emissive_layers": [1],
        "render_types": {"minecraft:translucent": [1]},
    })
    write(f"{ASSETS}/models/item/ritual_tome.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/ritual_tome"}})


# --------------------------------------------------------------------------- recetas

def gen_recipes():
    r = f"{DATA}/{MOD}/recipes"
    essence = {"item": f"{ISS}:arcane_essence"}
    write(f"{r}/arcane_candle_from_candle.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": [" E ", "ECE", " E "],
        "key": {"E": essence, "C": {"item": "minecraft:candle"}},
        "result": {"item": f"{MOD}:arcane_candle", "count": 2},
    })
    write(f"{r}/arcane_candle_from_string.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["EEE", "ESE", "EEE"],
        "key": {"E": essence, "S": {"item": "minecraft:string"}},
        "result": {"item": f"{MOD}:arcane_candle", "count": 4},
    })
    write(f"{r}/ritual_core.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": [" B ", "CTC", "EEE"],
        "key": {
            "B": {"item": "minecraft:writable_book"},
            "C": {"item": f"{MOD}:arcane_candle"},
            "T": {"item": "minecraft:enchanting_table"},
            "E": essence,
        },
        "result": {"item": f"{MOD}:ritual_core"},
    })
    # Decisión de diseño (el plan no la define): hoja de hierro, empuñadura de esencia arcana.
    write(f"{r}/ritual_dagger.json", {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "pattern": ["I", "E", "S"],
        "key": {"I": {"item": "minecraft:iron_ingot"}, "E": essence, "S": {"item": "minecraft:stick"}},
        "result": {"item": f"{MOD}:ritual_dagger"},
    })


# --------------------------------------------------------------------------- tags

BOSSES = [
    "minecraft:ender_dragon", "minecraft:wither", "minecraft:warden",
]
ISS_BOSSES = [f"{ISS}:dead_king", f"{ISS}:fire_boss"]  # verificados con la sonda de ISS


def gen_tags():
    candles = [f"{MOD}:{n}" for n in CANDLES]
    write(f"{DATA}/{MOD}/tags/blocks/arcane_candles.json", {"values": candles})
    write(f"{DATA}/{MOD}/tags/items/arcane_candles.json", {"values": candles})
    # En minecraft:candles para que el mechero y la carga ígnea vanilla las enciendan.
    write(f"{DATA}/minecraft/tags/blocks/candles.json", {"replace": False, "values": candles})
    write(f"{DATA}/minecraft/tags/blocks/mineable/pickaxe.json", {"replace": False, "values": [f"{MOD}:ritual_core"]})

    optional = [{"id": e, "required": False} for e in ISS_BOSSES]
    write(f"{DATA}/{MOD}/tags/entity_types/soul_blacklist.json", {"values": BOSSES + optional})
    write(f"{DATA}/{MOD}/tags/entity_types/time_immune.json", {"values": BOSSES + optional})
    write(f"{DATA}/{MOD}/tags/blocks/terraformable.json", {"values": [
        "#minecraft:dirt", "#minecraft:base_stone_overworld", "#minecraft:sand", "minecraft:gravel",
        "minecraft:water", "minecraft:snow_block", "minecraft:ice",
    ]})


# --------------------------------------------------------------------------- lang

RITUALS = [
    # id, es, en, pista es, pista en
    ("sun", "Ritual del Sol", "Ritual of the Sun",
     "Que el oro llame a la aurora y la flor siga su rostro.", "Let gold call the dawn and the flower follow its face."),
    ("night", "Ritual de la Noche", "Ritual of the Night",
     "Cuando la sangre pide descanso, el sol no tiene voz.", "When blood begs for rest, the sun has no voice."),
    ("deluge", "Ritual del Diluvio", "Ritual of the Deluge",
     "Cinco mares, diez hojas y la lágrima de un ser que flota.", "Five seas, ten leaves and the tear of a floating being."),
    ("dome", "Ritual del Domo", "Ritual of the Dome",
     "Un muro que nadie ve y todos respetan.", "A wall no one sees and everyone respects."),
    ("prison", "Ritual de la Prisión", "Ritual of the Prison",
     "Lo que entra no siempre sale.", "What goes in does not always come out."),
    ("arcane_suppression", "Ritual de Supresión Arcana", "Ritual of Arcane Suppression",
     "Cuando el libro calla, hasta la brasa olvida su nombre.", "When the book falls silent, even the ember forgets its name."),
    ("sanctuary", "Ritual del Santuario", "Ritual of the Sanctuary",
     "Donde la luz guarda a los suyos, la sombra no encuentra cuna.", "Where light guards its own, shadow finds no cradle."),
    ("arcane_spring", "Ritual del Manantial Arcano", "Ritual of the Arcane Spring",
     "La fuente no se agota si se la nombra bien.", "The spring never runs dry if it is named rightly."),
    ("sentinel", "Ritual del Centinela", "Ritual of the Sentinel",
     "Un ojo que no duerme cuenta los pasos ajenos.", "An eye that never sleeps counts the steps of strangers."),
    ("sepulchral_silence", "Ritual del Silencio Sepulcral", "Ritual of Sepulchral Silence",
     "Donde muere el eco, ni el monstruo de las profundidades oye el latido de su presa.",
     "Where the echo dies, not even the beast of the deep hears its prey's heartbeat."),
    ("inverted_gravity", "Ritual de Gravedad Invertida", "Ritual of Inverted Gravity",
     "Cuando la piedra olvida su peso, hasta el hierro aprende a caer hacia las nubes.",
     "When stone forgets its weight, even iron learns to fall towards the clouds."),
    ("time_distortion", "Ritual de Distorsión Temporal", "Ritual of Time Distortion",
     "El grano cae más despacio hacia el centro del reloj; solo quien lo vuelve no se ahoga en la arena.",
     "The grain falls slower towards the heart of the hourglass; only the one who turns it does not drown in the sand."),
    ("mirror", "Ritual del Espejo", "Ritual of the Mirror",
     "Un lugar extraño, desolado y muerto, donde nadie te ve ni te toca.", "A strange place, desolate and dead, where no one sees or touches you."),
    ("phoenix", "Ritual del Fénix", "Ritual of the Phoenix",
     "Quien muere en llamas, nace en llamas.", "Who dies in flames is born in flames."),
    ("metamorphosis", "Ritual de Metamorfosis", "Ritual of Metamorphosis",
     "Lo que hecho de ti camina a tu lado.", "That which is made of you walks beside you."),
    ("shrinking", "Ritual del Encogimiento", "Ritual of Shrinking",
     "Pequeño es quien mira el mundo desde la seta.", "Small is the one who watches the world from the mushroom."),
    ("scapegoat", "Ritual del Chivo Expiatorio", "Ritual of the Scapegoat",
     "Dos corazones laten al unísono, mas solo uno siente el filo y el veneno.",
     "Two hearts beat as one, yet only one feels the blade and the poison."),
    ("temporal_paradox", "Ritual de la Paradoja Temporal", "Ritual of the Temporal Paradox",
     "El reloj de arena no cae; espera a que el hilo se rompa para devolver el grano a la cima.",
     "The hourglass does not fall; it waits for the thread to snap to return the grain to the top."),
    ("soul_anchor", "Ritual del Ancla del Alma", "Ritual of the Soul Anchor",
     "Lo que atas al alma, la muerte no lo desata.", "What you bind to the soul, death does not unbind."),
    ("silent_echo", "Ritual del Eco Silente", "Ritual of the Silent Echo",
     "Una forma sin pulso que imita al que fue.", "A pulseless form that mimics what once was."),
    ("eternal_bond", "Ritual del Vínculo Eterno", "Ritual of the Eternal Bond",
     "Lo que juró lealtad no se pierde del todo.", "What swore loyalty is never wholly lost."),
    ("guardian", "Ritual del Guardián", "Ritual of the Guardian",
     "Manda al hierro que no duerma.", "Command the iron never to sleep."),
    ("planar_nexus", "Ritual del Nexo Planar", "Ritual of the Planar Nexus",
     "Hierba, roca ardiente y piedra del fin: tres mundos por una puerta.", "Grass, burning rock and stone of the end: three worlds through one door."),
    ("intrusion", "Ritual de la Intrusión", "Ritual of Intrusion",
     "Una puerta que no es tuya se abre cuando dices su nombre.", "A door that is not yours opens when you speak its name."),
    ("astral_projection", "Ritual de la Proyección Astral", "Ritual of Astral Projection",
     "Deja tu carne junto a las velas y deja que tu mirada atraviese la piedra como la niebla.",
     "Leave your flesh beside the candles and let your gaze pass through stone like mist."),
    ("eternal_forge", "Ritual de Forja Eterna", "Ritual of the Eternal Forge",
     "Lo que el fuego forja, el tiempo no lo gasta.", "What fire forges, time does not wear."),
    ("terraforming", "Ritual de Transmutación Ambiental", "Ritual of Environmental Transmutation",
     "La memoria de la tierra es maleable cuando se le ofrece la semilla de otro mundo.",
     "The memory of the earth is malleable when offered the seed of another world."),
    ("harvest", "Ritual de la Cosecha", "Ritual of the Harvest",
     "Que la semilla olvide la espera.", "Let the seed forget the wait."),
    ("custody", "Ritual de Custodia", "Ritual of Custody",
     "Una cerradura que solo reconoce al dueño.", "A lock that knows only its master."),
]

LANG = {
    "itemGroup.ironrituals": ("Iron Rituals", "Iron Rituals"),
    "block.ironrituals.arcane_candle": ("Vela Arcana", "Arcane Candle"),
    "block.ironrituals.arcane_candle_worn": ("Vela Arcana gastada", "Worn Arcane Candle"),
    "block.ironrituals.arcane_candle_stub": ("Cabo de Vela Arcana", "Arcane Candle Stub"),
    "block.ironrituals.ritual_core": ("Núcleo del Ritual", "Ritual Core"),
    "item.ironrituals.ritual_dagger": ("Daga Ritual", "Ritual Dagger"),
    "item.ironrituals.ritual_tome": ("Grimorio de Ritual", "Ritual Tome"),
    "item.ironrituals.ritual_tome.named": ("Grimorio: %s", "Tome: %s"),
    "ritual.ironrituals.unknown": ("Ritual desconocido", "Unknown ritual"),
    "tooltip.ironrituals.dagger.empty": ("Sin alma", "No soul"),
    "tooltip.ironrituals.dagger.soul": ("Alma de %s", "Soul of %s"),
    "tooltip.ironrituals.dagger.player_soul": ("Alma del jugador %s", "Soul of player %s"),
    "tooltip.ironrituals.dagger.release_hint": ("Mantén Shift + clic derecho 3 s para liberarla", "Hold Shift + right-click for 3 s to release it"),
    "message.ironrituals.dagger.captured": ("La daga ha capturado el alma de %s", "The dagger captured the soul of %s"),
    "message.ironrituals.dagger.full": ("La daga ya guarda un alma", "The dagger already holds a soul"),
    "message.ironrituals.dagger.soulless": ("Esta criatura no tiene un alma que capturar", "This creature has no soul to capture"),
    "message.ironrituals.dagger.released": ("El alma se ha liberado", "The soul has been released"),
    "message.ironrituals.core.not_yours": ("Este núcleo pertenece a %s", "This core belongs to %s"),
    "message.ironrituals.core.occupied": ("El núcleo ya tiene un grimorio", "The core already holds a tome"),
    "message.ironrituals.core.no_book": ("Coloca un grimorio de ritual en el núcleo", "Place a ritual tome in the core"),
    "message.ironrituals.core.header": ("— %s —", "— %s —"),
    "message.ironrituals.core.circle_level": ("Círculo arcano válido: nivel %s", "Valid arcane circle: level %s"),
    "message.ironrituals.core.circle_none": ("No hay un círculo arcano válido", "There is no valid arcane circle"),
    "message.ironrituals.core.next_ring": ("Anillo del nivel %s: faltan %s velas y %s están apagadas (marcadas en rojo y amarillo)",
                                            "Level %s ring: %s candles missing and %s unlit (marked red and yellow)"),
    "message.ironrituals.core.components": ("Componentes en el área:", "Components in the area:"),
    "message.ironrituals.core.no_components": ("No hay componentes en el área central", "There are no components in the central area"),
    "message.ironrituals.core.engine_pending": ("(La canalización de rituales llega en la próxima versión)", "(Ritual channeling arrives in the next version)"),
}


def gen_lang():
    es, en = {}, {}
    for key, (s, e) in LANG.items():
        es[key], en[key] = s, e
    for rid, ns, ne, hs, he in RITUALS:
        es[f"ritual.{MOD}.{rid}"], en[f"ritual.{MOD}.{rid}"] = ns, ne
        es[f"ritual.{MOD}.{rid}.hint"], en[f"ritual.{MOD}.{rid}.hint"] = f"«{hs}»", f"\"{he}\""
    write(f"{ASSETS}/lang/es_es.json", es)
    write(f"{ASSETS}/lang/en_us.json", en)


# --------------------------------------------------------------------------- texturas provisionales

def png(path, img):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def tex_candle_block():
    rnd = random.Random(1)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            t = y / 15
            c = lerp((176, 120, 230, 255), (92, 48, 150, 255), t)
            if rnd.random() < 0.12:
                c = lerp(c, (230, 200, 255, 255), 0.4)
            img.putpixel((x, y), c)
    # Goteo de cera más claro bajo el borde superior.
    for x in (1, 3, 6, 10, 13):
        for y in range(0, rnd.randint(2, 5)):
            img.putpixel((x, y), (214, 180, 250, 255))
    png(f"{ASSETS}/textures/block/arcane_candle.png", img)

    wick = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    wick.putpixel((0, 0), (60, 50, 50, 255))
    wick.putpixel((0, 1), (40, 32, 32, 255))
    png(f"{ASSETS}/textures/block/arcane_candle_wick.png", wick)
    lit = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    lit.putpixel((0, 0), (255, 230, 140, 255))
    lit.putpixel((0, 1), (120, 70, 200, 255))
    png(f"{ASSETS}/textures/block/arcane_candle_wick_lit.png", lit)


def tex_candle_items():
    for name, h in CANDLES.items():
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        top = 15 - h * 2 + 2
        for y in range(top, 15):
            for x in range(5, 11):
                t = (y - top) / max(1, 14 - top)
                c = lerp((190, 140, 240, 255), (96, 52, 156, 255), t)
                if x == 5:
                    c = lerp(c, (60, 30, 100, 255), 0.4)
                if x == 10:
                    c = lerp(c, (230, 210, 255, 255), 0.3)
                img.putpixel((x, y), c)
        for x in range(4, 12):
            img.putpixel((x, 15), (70, 40, 110, 255))
        img.putpixel((7, top - 1), (50, 40, 40, 255))
        img.putpixel((7, top - 2), (50, 40, 40, 255))
        png(f"{ASSETS}/textures/item/{name}.png", img)


def tex_core():
    rnd = random.Random(7)

    def stone(seed):
        r = random.Random(seed)
        img = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                v = r.randint(38, 58)
                img.putpixel((x, y), (v, v - 4, v + 14, 255))
        return img

    side = stone(2)
    for x in range(16):
        side.putpixel((x, 3), (120, 70, 190, 255))
        side.putpixel((x, 12), (120, 70, 190, 255))
    for x in (3, 7, 8, 12):
        for y in range(5, 11):
            if rnd.random() < 0.7:
                side.putpixel((x, y), (170, 110, 240, 255))
    png(f"{ASSETS}/textures/block/ritual_core_side.png", side)

    top = stone(3)
    for i in range(16):
        top.putpixel((i, 2), (140, 90, 210, 255))
        top.putpixel((i, 13), (140, 90, 210, 255))
        top.putpixel((2, i), (140, 90, 210, 255))
        top.putpixel((13, i), (140, 90, 210, 255))
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8), (5, 5), (10, 10), (5, 10), (10, 5)):
        top.putpixel((x, y), (220, 170, 255, 255))
    png(f"{ASSETS}/textures/block/ritual_core_top.png", top)
    png(f"{ASSETS}/textures/block/ritual_core_bottom.png", stone(4))


def tex_dagger():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    blade = [(13, 2), (12, 3), (11, 4), (10, 5), (9, 6), (8, 7)]
    for (x, y) in blade:
        img.putpixel((x, y), (215, 215, 225, 255))
        img.putpixel((x - 1, y), (160, 160, 175, 255))
        img.putpixel((x, y - 1), (240, 240, 250, 255))
    img.putpixel((14, 1), (240, 240, 250, 255))
    for (x, y) in ((6, 7), (7, 8), (8, 9), (5, 6), (9, 10)):  # guarda
        img.putpixel((x, y), (120, 70, 190, 255))
    for (x, y) in ((6, 9), (5, 10), (4, 11), (3, 12)):  # empuñadura
        img.putpixel((x, y), (90, 50, 40, 255))
        img.putpixel((x + 1, y), (60, 34, 28, 255))
    img.putpixel((2, 13), (170, 110, 240, 255))
    png(f"{ASSETS}/textures/item/ritual_dagger.png", img)

    # Capa de brillo rojizo animada (8 fotogramas).
    frames = 8
    glow = Image.new("RGBA", (16, 16 * frames), (0, 0, 0, 0))
    for f in range(frames):
        for i, (x, y) in enumerate(blade + [(14, 1)]):
            a = int(110 + 110 * (0.5 + 0.5 * __import__("math").sin((i - f) * 0.9)))
            glow.putpixel((x, y + 16 * f), (255, 40, 60, a))
            if x - 1 >= 0:
                glow.putpixel((x - 1, y + 16 * f), (200, 20, 40, a // 2))
    png(f"{ASSETS}/textures/item/ritual_dagger_soul_glow.png", glow)
    with open(f"{ASSETS}/textures/item/ritual_dagger_soul_glow.png.mcmeta", "w") as fh:
        json.dump({"animation": {"frametime": 2, "interpolate": True}}, fh, indent=2)


def tex_tome():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(2, 15):
        for x in range(3, 13):
            img.putpixel((x, y), (70, 30, 90, 255))
        img.putpixel((3, y), (40, 16, 50, 255))
        img.putpixel((12, y), (230, 220, 200, 255))
    for x in range(4, 12):
        img.putpixel((x, 2), (100, 50, 130, 255))
        img.putpixel((x, 14), (40, 16, 50, 255))
    for (x, y) in ((7, 5), (8, 5), (6, 6), (9, 6), (6, 9), (9, 9), (7, 10), (8, 10), (5, 7), (10, 7), (5, 8), (10, 8)):
        img.putpixel((x, y), (220, 170, 255, 255))
    img.putpixel((7, 7), (255, 230, 140, 255))
    img.putpixel((8, 8), (255, 230, 140, 255))
    png(f"{ASSETS}/textures/item/ritual_tome.png", img)


def main():
    write(os.path.join(ROOT, "pack.mcmeta"), {"pack": {"description": "Iron Rituals resources", "pack_format": 15}})
    gen_candles()
    gen_core()
    gen_items()
    gen_recipes()
    gen_tags()
    gen_lang()
    tex_candle_block()
    tex_candle_items()
    tex_core()
    tex_dagger()
    tex_tome()
    print("Recursos generados en", os.path.normpath(ROOT))


if __name__ == "__main__":
    main()
