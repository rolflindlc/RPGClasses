# Equipo de Clase (rev. 2.20): armaduras de Bárbaro y Monje, armas marciales y objetos intermedios.
# Genera modelos, recetas, tags, tipos de daño, capacidades de Epic Fight y textos. Lo importa gen_data.py.
# Los ids y tiers deben coincidir con io.github.rpgclasses.gear.ArmorTier / WeaponTier / WeaponKind.
import json, os

NS = "rpgclasses"
IAF = "iceandfire"
IAF_COND = [{"type": "forge:mod_loaded", "modid": IAF}]

CLASSES = ["barbarian", "monk"]
PIECES = ["helmet", "chestplate", "leggings", "boots"]
ARMOR_TIERS = ["leather", "chain", "iron", "gold", "diamond", "netherite",
               "dragon_fire", "dragon_ice", "dragon_lightning",
               "dragonsteel_fire", "dragonsteel_ice", "dragonsteel_lightning"]
WEAPON_KINDS = ["nunchaku", "sai", "bo", "knuckles"]
WEAPON_TIERS = ["wood", "stone", "iron", "gold", "diamond", "netherite", "dragonbone",
                "dragonsteel_fire", "dragonsteel_ice", "dragonsteel_lightning"]
ELEMENTS = ["fire", "ice", "lightning"]

# --- materiales de Ice and Fire (entradas opcionales: el tag no falla sin el mod) [LEER: nombres en la versión del pack]
SCALES = {"fire": ["red", "green", "bronze", "gray"], "ice": ["blue", "white", "sapphire", "silver"],
          "lightning": ["electric", "amythest", "copper", "black"]}

def opt(i): return {"id": i, "required": False}

# --- textos ---
ARMOR_MAT = {"leather": "cuero", "chain": "anillas", "iron": "hierro", "gold": "oro", "diamond": "diamante",
             "netherite": "netherita", "dragon_fire": "escamas de fuego", "dragon_ice": "escamas de hielo",
             "dragon_lightning": "escamas de rayo", "dragonsteel_fire": "dragonsteel de fuego",
             "dragonsteel_ice": "dragonsteel de hielo", "dragonsteel_lightning": "dragonsteel de rayo"}
PIECE_NAME = {"barbarian": {"helmet": "Yelmo bárbaro", "chestplate": "Arnés bárbaro", "leggings": "Grebas bárbaras", "boots": "Botas bárbaras"},
              "monk": {"helmet": "Cinta de monje", "chestplate": "Gi de monje", "leggings": "Pantalón de monje", "boots": "Sandalias de monje"}}
WEAPON_MAT = {"wood": "madera", "stone": "piedra", "iron": "hierro", "gold": "oro", "diamond": "diamante",
              "netherite": "netherita", "dragonbone": "hueso de dragón", "dragonsteel_fire": "dragonsteel de fuego",
              "dragonsteel_ice": "dragonsteel de hielo", "dragonsteel_lightning": "dragonsteel de rayo"}
WEAPON_NAME = {"nunchaku": "Nunchaku", "sai": "Sai", "bo": "Bo", "knuckles": "Nudilleras"}

SETS = {
 "barbarian_leather": ("Pieles de Bestia", "Olfato: los mobs hostiles a 12 bloques con menos del 30 % de vida brillan."),
 "barbarian_chain": ("Cinchas de Anillas", "Tintineo de Guerra: el primer golpe tras 2 s corriendo empuja más."),
 "barbarian_iron": ("Arnés de Hierro", "Pecho de Hierro: −5 % de daño de proyectiles."),
 "barbarian_gold": ("Galas del Saqueador", "Botín Dorado: al matar un mob hostil, 10 % de que suelte una pepita de oro que salta hacia ti."),
 "barbarian_diamond": ("Arnés del Rompehuesos", "Crujido: tus críticos ralentizan 1 s al objetivo (una vez cada 3 s por objetivo)."),
 "barbarian_netherite": ("Placas de Ceniza", "Corazón de Brasas: cada golpe recibido acumula una brasa (máx. 5, 8 s). Embestida o Furia Berserker las liberan en un anillo de fuego de 4 bloques."),
 "barbarian_dragon": ("Muda del Dragón", "Rugido de Dragón: Furia Berserker suelta un rugido; los mobs no jefes a 8 bloques huyen y sufren el elemento de las escamas."),
 "barbarian_dragonsteel": ("Furia del Wyrm", "Ira del Wyrm: con menos del 40 % de vida o en Furia Berserker te salen alas espectrales y cada crítico suelta un cono elemental de 4 bloques (una vez por segundo)."),
 "monk_leather": ("Vendas del Novicio", "Primer Paso: al entrar en una postura sin cargas recibes 1, y las cargas decaen un 10 % más despacio."),
 "monk_chain": ("Gi Anillado", "Anillas Sonoras: cada carga suena y, al llegar al umbral, las anillas suenan en acorde."),
 "monk_iron": ("Gi de Hierro", "Postura Firme: Piedra gana carga con golpes desde 1,5 HP."),
 "monk_gold": ("Túnica del Peregrino", "Peregrinaje: la Meditación cura un 20 % más."),
 "monk_diamond": ("Hábito de Cristal", "Reflejo de Cristal: el Desvío de Proyectiles da +1 carga con un destello prismático."),
 "monk_netherite": ("Kesa de Obsidiana", "Sello de Postura: las cargas de Piedra decaen la mitad de rápido y, al cambiar de postura con 5 cargas o más, dejas un sello rúnico que repite la descarga al 30 % a los 3 s."),
 "monk_dragon": ("Manto Escamado", "Danza del Dragón: al disparar un efecto propio, un dragón espectral del color de la postura te rodea 4 s y muerde al enemigo más cercano cada segundo."),
 "monk_dragonsteel": ("Vestiduras del Wyrm", "Puño del Wyrm: cada efecto propio lanza una cabeza de dragón de energía que recorre 8 bloques en línea. Fuego: caminar sobre lava en Postura del Fuego da velocidad. Hielo: en Agua congelas el agua al caminar. Rayo: en Piedra, los golpes fuertes que recibes sueltan una chispa."),
}
WEAPON_TEXT = {
 "nunchaku": ("Rápido y de alcance corto. Para el Monje cuenta como desarmado.",
              ["Molinete: cada 8 golpes encadenados, giro que golpea alrededor al 50 %.",
               "Eco de Hueso: el Molinete deja un nunchaku fantasma que repite 3 golpes al 30 %.",
               "Tormenta de Cadenas: el Molinete se vuelve un torbellino de 3 s que atrae y aplica el elemento."]),
 "sai": ("Velocidad media. Mantén el clic derecho para la guardia: un golpe en los primeros instantes se para (parry) y aturde al atacante; si no, reduce el daño.",
         ["Parada Férrea: el parry devuelve el 50 % del golpe.",
          "Desarme: el parry da Debilidad II 3 s al atacante.",
          "Contraataque del Wyrm: tras un parry, tu siguiente golpe en 1,5 s es una estocada elemental que perfora en línea."]),
 "bo": ("Lento, con más alcance y barrido en arco al 60 % a los demás enemigos.",
        ["Barrido de Ceniza: el barrido ciega 1 s a los mobs.",
         "Salto de Pértiga: agachado + saltar te impulsa hacia delante y aterrizas con una onda (8 s).",
         "Ola del Horizonte: si esperas 1,5 s entre golpes, el golpe lanza una onda elemental en arco."]),
 "knuckles": ("Van en la ranura de manos de Curios (o en la mano secundaria) y mejoran los golpes con la mano vacía.",
              ["Puños de Obsidiana: a los 5 golpes al mismo objetivo, estallido de 2 bloques.",
               "Garras de Dragón: tus golpes pueden causar Herida (−30 % de curación 4 s).",
               "Puños del Wyrm: tus golpes aplican el elemento y los efectos propios del Monje son un 20 % más fuertes."]),
}

def lang():
    out = {"itemGroup.rpgclasses.class_gear": "RPG Classes",
           "item.rpgclasses.cured_strap": "Correa curtida",
           "item.rpgclasses.martial_cloth": "Tela marcial",
           "item.rpgclasses.draconic_forge_template": "Plantilla de forja dracónica",
           "gear.rpgclasses.set_header": "Set completo (4 piezas, solo %s):",
           "gear.rpgclasses.exempt.barbarian": "No desactiva las pasivas desarmadas del Bárbaro.",
           "gear.rpgclasses.exempt.monk": "No desactiva Cuerpo Marcial ni borra las cargas del Monje.",
           "gear.rpgclasses.element.fire": "Escamas de fuego: cada pieza reduce el daño de fuego.",
           "gear.rpgclasses.element.ice": "Escamas de hielo: cada pieza reduce el daño de hielo.",
           "gear.rpgclasses.element.lightning": "Escamas de rayo: cada pieza reduce el daño de rayo.",
           "gear.rpgclasses.weapon.monk": "Monje: cuenta como desarmado (cargas, perks y subclase).",
           "gear.rpgclasses.weapon.element.fire": "Fuego: cada golpe quema.",
           "gear.rpgclasses.weapon.element.ice": "Hielo: cada golpe ralentiza.",
           "gear.rpgclasses.weapon.element.lightning": "Rayo: cada golpe salta a un enemigo cercano.",
           "death.attack.rpgclasses.martial": "%1$s cayó ante el arte marcial de %2$s",
           "death.attack.rpgclasses.martial.player": "%1$s cayó ante el arte marcial de %2$s",
           "death.attack.rpgclasses.dragon_flame": "%1$s ardió en el fuego de dragón de %2$s",
           "death.attack.rpgclasses.dragon_flame.player": "%1$s ardió en el fuego de dragón de %2$s",
           "death.attack.rpgclasses.dragon_frost": "%1$s se congeló con la escarcha de dragón de %2$s",
           "death.attack.rpgclasses.dragon_frost.player": "%1$s se congeló con la escarcha de dragón de %2$s",
           "death.attack.rpgclasses.dragon_spark": "%1$s fue fulminado por la chispa de dragón de %2$s",
           "death.attack.rpgclasses.dragon_spark.player": "%1$s fue fulminado por la chispa de dragón de %2$s",
           "death.attack.rpgclasses.breath_fire": "%1$s ardió en el aliento de %2$s",
           "death.attack.rpgclasses.breath_fire.player": "%1$s ardió en el aliento de %2$s",
           "death.attack.rpgclasses.breath_ice": "%1$s se congeló en el aliento de %2$s",
           "death.attack.rpgclasses.breath_ice.player": "%1$s se congeló en el aliento de %2$s",
           "death.attack.rpgclasses.breath_lightning": "%1$s fue fulminado por el aliento de %2$s",
           "death.attack.rpgclasses.breath_lightning.player": "%1$s fue fulminado por el aliento de %2$s",
           "death.attack.rpgclasses.breath_poison": "%1$s se envenenó con el aliento de %2$s",
           "death.attack.rpgclasses.breath_poison.player": "%1$s se envenenó con el aliento de %2$s",
           "death.attack.rpgclasses.breath_acid": "%1$s se disolvió en el aliento de %2$s",
           "death.attack.rpgclasses.breath_acid.player": "%1$s se disolvió en el aliento de %2$s",
           "death.attack.rpgclasses.breath_water": "%1$s se ahogó en el aliento de %2$s",
           "death.attack.rpgclasses.breath_water.player": "%1$s se ahogó en el aliento de %2$s",
           "death.attack.rpgclasses.swarm": "%1$s fue engullido por el enjambre de %2$s",
           "death.attack.rpgclasses.swarm.player": "%1$s fue engullido por el enjambre de %2$s"}
    for c in CLASSES:
        for t in ARMOR_TIERS:
            for p in PIECES:
                out[f"item.{NS}.{c}_{t}_{p}"] = f"{PIECE_NAME[c][p]} de {ARMOR_MAT[t]}"
    for k in WEAPON_KINDS:
        for t in WEAPON_TIERS:
            out[f"item.{NS}.{k}_{t}"] = f"{WEAPON_NAME[k]} de {WEAPON_MAT[t]}"
        desc, late = WEAPON_TEXT[k]
        out[f"gear.{NS}.weapon.{k}.desc"] = desc
        for i, line in enumerate(late, 1):
            out[f"gear.{NS}.weapon.{k}.late{i}"] = line
    for key, (name, desc) in SETS.items():
        out[f"gear.{NS}.set.{key}.name"] = name
        out[f"gear.{NS}.set.{key}.desc"] = desc
    return out

# --- recetas ---
def item(i): return {"item": i}
def tag(t): return {"tag": t}
def r(i): return f"{NS}:{i}"

ARMOR_PATTERNS = {
 "barbarian": {"helmet": ["MCM", "C C"], "chestplate": ["C C", "MCM", "CMC"], "leggings": ["CMC", "C C", "M M"], "boots": ["C C", "M M"]},
 "monk": {"helmet": ["CCC", "M M"], "chestplate": ["C C", "MCM", "CCC"], "leggings": ["CMC", "C C", "C C"], "boots": ["M M", "C C"]},
}
ARMOR_MATERIAL = {"leather": item("minecraft:leather"), "chain": item("minecraft:chain"), "iron": item("minecraft:iron_ingot"),
                  "gold": item("minecraft:gold_ingot"), "diamond": item("minecraft:diamond")}
SCALE_COUNT = {"barbarian": {"helmet": 4, "chestplate": 6, "leggings": 5, "boots": 3},
               "monk": {"helmet": 3, "chestplate": 5, "leggings": 4, "boots": 2}}
WEAPON_PATTERNS = {"knuckles": ["MMM", "S S"], "nunchaku": ["M M", " C "], "sai": ["M M", " M ", " P "], "bo": ["  M", " P ", "M  "]}
WEAPON_MATERIAL = {"wood": tag("minecraft:planks"), "stone": tag("minecraft:stone_tool_materials"), "iron": item("minecraft:iron_ingot"),
                   "gold": item("minecraft:gold_ingot"), "diamond": item("minecraft:diamond"), "dragonbone": tag(r("iceandfire/dragonbone"))}

def shaped(pattern, key, result, count=1, conditions=None):
    d = {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern, "key": key,
         "result": {"item": result, "count": count}}
    if conditions: d = {"conditions": conditions, **d}
    return d

def smithing(template, base, addition, result, conditions=None):
    d = {"type": "minecraft:smithing_transform", "template": template, "base": base, "addition": addition, "result": {"item": result}}
    if conditions: d = {"conditions": conditions, **d}
    return d

def recipes():
    out = {}
    out["cured_strap"] = {"type": "minecraft:crafting_shapeless", "category": "misc",
                          "ingredients": [item("minecraft:leather"), item("minecraft:leather"), item("minecraft:string")],
                          "result": {"item": r("cured_strap"), "count": 2}}
    out["martial_cloth"] = {"type": "minecraft:crafting_shapeless", "category": "misc",
                            "ingredients": [tag("minecraft:wool")] * 3 + [item("minecraft:string")],
                            "result": {"item": r("martial_cloth"), "count": 2}}
    out["draconic_forge_template"] = shaped(["BSB", "SNS", "BSB"], {"B": tag(r("iceandfire/dragonbone")), "S": tag(r("iceandfire/scales_any")),
                                            "N": item("minecraft:netherite_upgrade_smithing_template")}, r("draconic_forge_template"), conditions=IAF_COND)
    out["draconic_forge_template_duplicate"] = shaped(["DTD", "DXD", "DDD"], {"D": item("minecraft:diamond"), "T": item(r("draconic_forge_template")),
                                                      "X": tag(r("iceandfire/dragonsteel_any"))}, r("draconic_forge_template"), 2, IAF_COND)
    template = item(r("draconic_forge_template"))
    for c in CLASSES:
        binder = item(r("cured_strap")) if c == "barbarian" else item(r("martial_cloth"))
        for p in PIECES:
            for t, mat in ARMOR_MATERIAL.items():
                out[f"{c}_{t}_{p}"] = shaped(ARMOR_PATTERNS[c][p], {"M": mat, "C": binder}, r(f"{c}_{t}_{p}"))
            out[f"{c}_netherite_{p}"] = smithing(item("minecraft:netherite_upgrade_smithing_template"), item(r(f"{c}_diamond_{p}")),
                                                 item("minecraft:netherite_ingot"), r(f"{c}_netherite_{p}"))
            for e in ELEMENTS:
                out[f"{c}_dragon_{e}_{p}"] = {"conditions": IAF_COND, "type": "minecraft:crafting_shapeless", "category": "equipment",
                                              "ingredients": [item(r(f"{c}_netherite_{p}"))] + [tag(r(f"iceandfire/scales_{e}"))] * SCALE_COUNT[c][p],
                                              "result": {"item": r(f"{c}_dragon_{e}_{p}")}}
                out[f"{c}_dragonsteel_{e}_{p}"] = smithing(template, tag(r(f"gear/{c}_dragon_{p}")), tag(r(f"iceandfire/dragonsteel_{e}")),
                                                           r(f"{c}_dragonsteel_{e}_{p}"), IAF_COND)
    for k in WEAPON_KINDS:
        for t, mat in WEAPON_MATERIAL.items():
            key = {"M": mat}
            pat = WEAPON_PATTERNS[k]
            if "P" in "".join(pat): key["P"] = item("minecraft:stick")
            if "C" in "".join(pat): key["C"] = item("minecraft:chain")
            if "S" in "".join(pat): key["S"] = item(r("cured_strap"))
            if k == "nunchaku" and t == "wood": key["M"] = item("minecraft:stick")
            out[f"{k}_{t}"] = shaped(pat, key, r(f"{k}_{t}"), conditions=IAF_COND if t == "dragonbone" else None)
        out[f"{k}_netherite"] = smithing(item("minecraft:netherite_upgrade_smithing_template"), item(r(f"{k}_diamond")),
                                         item("minecraft:netherite_ingot"), r(f"{k}_netherite"))
        for e in ELEMENTS:
            out[f"{k}_dragonsteel_{e}"] = smithing(template, item(r(f"{k}_dragonbone")), tag(r(f"iceandfire/dragonsteel_{e}")),
                                                   r(f"{k}_dragonsteel_{e}"), IAF_COND)
    return out

def tags():
    t = {}
    for c in CLASSES:
        t[f"items/class_armor/{c}"] = [r(f"{c}_{tier}_{p}") for tier in ARMOR_TIERS for p in PIECES]
        for p in PIECES:
            t[f"items/gear/{c}_dragon_{p}"] = [r(f"{c}_dragon_{e}_{p}") for e in ELEMENTS]
    t["items/martial_weapons"] = [r(f"{k}_{tier}") for k in WEAPON_KINDS if k != "knuckles" for tier in WEAPON_TIERS]
    for e, colors in SCALES.items():
        t[f"items/iceandfire/scales_{e}"] = [opt(f"{IAF}:dragonscales_{col}") for col in colors]
        t[f"items/iceandfire/dragonsteel_{e}"] = [opt(f"{IAF}:dragonsteel_{e}_ingot")]
    t["items/iceandfire/scales_any"] = [f"#{NS}:iceandfire/scales_{e}" for e in ELEMENTS]
    t["items/iceandfire/dragonsteel_any"] = [f"#{NS}:iceandfire/dragonsteel_{e}" for e in ELEMENTS]
    t["items/iceandfire/dragonbone"] = [opt(f"{IAF}:dragonbone")]
    t["damage_type/element/fire"] = ["#minecraft:is_fire", r("dragon_flame"), r("breath_fire"), opt(f"{IAF}:dragon_fire")]
    t["damage_type/element/ice"] = ["#minecraft:is_freezing", r("dragon_frost"), r("breath_ice"), opt(f"{IAF}:dragon_ice")]
    t["damage_type/element/lightning"] = ["#minecraft:is_lightning", r("dragon_spark"), r("breath_lightning"), opt(f"{IAF}:dragon_lightning")]
    return t

DAMAGE_TYPES = ["martial", "dragon_flame", "dragon_frost", "dragon_spark",
                # Aliento del Dracónido (adenda de razas 0.7)
                "breath_fire", "breath_ice", "breath_lightning", "breath_poison", "breath_acid", "breath_water",
                # Contacto del Slime Enjambre (adenda de razas 0.7)
                "swarm"]
# Capacidades de Epic Fight (primera versión: movesets existentes; animaciones propias en una fase posterior).
EF_TYPE = {
 "nunchaku": {"type": "epicfight:dagger", "attributes": {"common": {"armor_negation": 0.0, "impact": 0.6, "max_strikes": 1}}},
 "sai": {"type": "epicfight:dagger", "attributes": {"common": {"armor_negation": 10.0, "impact": 0.8, "max_strikes": 1}}},
 "bo": {"type": "epicfight:spear", "attributes": {"one_hand": {"armor_negation": 0.0, "impact": 1.6, "max_strikes": 2},
                                                  "two_hand": {"armor_negation": 0.0, "impact": 1.4, "max_strikes": 3}}},
}

def build(dump, out_dir):
    data = os.path.join(out_dir, "data")
    assets = os.path.join(out_dir, "assets", NS)
    for name, recipe in recipes().items():
        dump(os.path.join(data, NS, "recipes", "gear", name + ".json"), recipe)
    for path, values in tags().items():
        dump(os.path.join(data, NS, "tags", path + ".json"), {"replace": False, "values": values})
    dump(os.path.join(data, "minecraft", "tags", "damage_type", "is_fire.json"), {"replace": False, "values": [r("dragon_flame"), r("breath_fire")]})
    dump(os.path.join(data, "minecraft", "tags", "damage_type", "is_freezing.json"), {"replace": False, "values": [r("dragon_frost"), r("breath_ice")]})
    dump(os.path.join(data, "minecraft", "tags", "damage_type", "is_lightning.json"), {"replace": False, "values": [r("dragon_spark"), r("breath_lightning")]})
    for d in DAMAGE_TYPES:
        dump(os.path.join(data, NS, "damage_type", d + ".json"), {"message_id": f"{NS}.{d}", "exhaustion": 0.1, "scaling": "never"})
    # no_proc: el daño del equipo no vuelve a pasar por el pipeline (se añade a lo que ya hay).
    no_proc = os.path.join(data, NS, "tags", "damage_type", "no_proc.json")
    cur = json.load(open(no_proc, encoding="utf-8")) if os.path.exists(no_proc) else {"replace": False, "values": []}
    for d in DAMAGE_TYPES:
        if r(d) not in cur["values"]: cur["values"].append(r(d))
    dump(no_proc, cur)
    for k, ef in EF_TYPE.items():
        for t in WEAPON_TIERS:
            dump(os.path.join(data, NS, "capabilities", "weapons", f"{k}_{t}.json"), ef)
    # Modelos de objeto.
    names = ["cured_strap", "martial_cloth", "draconic_forge_template"]
    names += [f"{c}_{t}_{p}" for c in CLASSES for t in ARMOR_TIERS for p in PIECES]
    for n in names:
        dump(os.path.join(assets, "models", "item", n + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{n}"}})
    for k in WEAPON_KINDS:
        for t in WEAPON_TIERS:
            parent = "minecraft:item/generated" if k == "knuckles" else "minecraft:item/handheld"
            dump(os.path.join(assets, "models", "item", f"{k}_{t}.json"), {"parent": parent, "textures": {"layer0": f"{NS}:item/{k}_{t}"}})
    out = lang()
    out.update(build_curios(dump, out_dir))
    return out

# ================================================================== curios (rev. 2.20)
# id, ranura, nombre, descripción. Deben coincidir con io.github.rpgclasses.gear.Curio.
CURIOS = [
 ("wisp_lantern", "charm", "Farol de Fuegos Fatuos", "Cada 30 s, un fuego fatuo vuela hacia el cofre o spawner más cercano en 24 bloques."),
 ("last_breath_compass", "necklace", "Brújula del Último Aliento", "Al morir, una columna de luz marca durante 10 min el lugar donde caíste."),
 ("hermit_medallion", "necklace", "Medallón del Ermitaño", "Atrae los objetos y orbes de experiencia a 6 bloques."),
 ("traveler_belt", "belt", "Cinturón del Viajero", "Por debajo de 6 muslitos comes solo comida de tu inventario (sin efectos)."),
 ("frost_ring", "ring", "Anillo de Escarcha Errante", "Congelas el agua al caminar sobre ella."),
 ("pixie_bell", "charm", "Campanilla de Pixie", "Agachado en el aire, flotas 3 s (10 s de espera)."),
 ("serpent_pendant", "necklace", "Colgante de Serpiente Marina", "Nadas más rápido y, al salir del agua, corres más durante 5 s."),
 ("cyclops_horn", "charm", "Cuerno de Cíclope", "Al entrar en combate suena el cuerno: los enemigos a 16 bloques brillan 8 s y tus aliados ganan Fuerza 8 s (90 s)."),
 ("gorgon_eye", "charm", "Ojo de Gorgona", "Mirar fijamente 2 s a un mob que no sea jefe lo petrifica 2 s (30 s)."),
 ("storm_talisman", "bracelet", "Talismán de la Tormenta", "Al recibir un golpe cuerpo a cuerpo, 10 % de que un rayo castigue al atacante (8 s)."),
 ("phoenix_feather", "back", "Pluma de Fénix", "Un golpe letal te hace renacer en llamas con el 30 % de la vida (20 min). Se resuelve tras las habilidades de clase y raza."),
 ("archmage_prism", "charm", "Prisma del Archimago", "Cada 5.º conjuro que hace daño se refracta: un 30 % salta a otro enemigo cercano."),
 ("arcane_hourglass", "necklace", "Reloj de Arena Arcano", "Al bajar del 30 % de vida, los proyectiles a 6 bloques se frenan (60 s)."),
 ("oath_reliquary", "necklace", "Relicario del Juramento", "Quien golpee a un mob que te persigue (tú o tus aliados) se cura 1 HP (1 vez por segundo)."),
 ("pocket_banner", "back", "Estandarte de Bolsillo", "Llevas un estandarte dorado a la espalda."),
 ("fang_collar", "necklace", "Collar de Colmillos", "Cada mob hostil que matas añade un colmillo (máx. 10, se pierden al morir): +1 % de daño cuerpo a cuerpo por colmillo."),
 ("war_drum", "belt", "Tambor de Guerra", "Furia Berserker hace sonar tambores: tus aliados a 10 bloques atacan más rápido 10 s."),
 ("trophy_skull", "belt", "Cráneo Trofeo", "Al matar a un jefe, el cráneo guarda su trofeo y te da un rasgo de ese jefe hasta el siguiente (inmunidad al fuego, a la lentitud, al wither... o +10 % de daño)."),
 ("crow_cloak", "back", "Capa de Cuervos", "Al salir de la invisibilidad, una bandada de cuervos ciega 1 s a los enemigos a 3 bloques (15 s)."),
 ("loaded_dice", "charm", "Dados Trucados", "Cada crítico tira un dado: con un 6 hace ×1,5 extra; con un 1 se queda en golpe normal."),
 ("hippogryph_feather", "charm", "Pluma de Hipogrifo", "Tensar el arco en el aire te hace planear."),
 ("dragonslayer_quiver", "back", "Carcaj del Matadragones", "Cada 11.ª flecha es de hueso de dragón: +50 % de daño y perfora a un enemigo."),
 ("stance_rosary", "necklace", "Rosario de las Tres Posturas", "Al cambiar de postura con 8 cargas o más, las cuentas rebotan entre 3 enemigos."),
 ("temple_bell", "belt", "Campana del Templo", "Al terminar de meditar, tus aliados a 8 bloques pierden un efecto negativo."),
 ("guild_insignia", "charm", "Insignia de Mil Oficios", "Al cambiar a un arma de otra disciplina, +10 % de daño cuerpo a cuerpo durante 3 s (6 s)."),
 ("troll_earring", "necklace", "Pendiente de Hueso de Trol", "Cuando se activa tu Última Resistencia, los mobs a 6 bloques huyen."),
 ("mine_lantern", "head", "Farolillo de Mina", "Los minerales a 4 bloques chispean."),
 ("evergreen_leaf", "charm", "Hoja del Árbol Eterno", "Dentro de hojas regeneras vida y los mobs a más de 8 bloques no te fijan como objetivo."),
 ("trinket_pouch", "belt", "Bolsa de Chucherías", "5 % de duplicar un drop común al matar."),
 ("tide_conch", "necklace", "Caracola de las Mareas", "Bajo el agua, tus aliados a 5 bloques recuperan aire. Fuera, si ardes, te apaga el fuego (10 s)."),
 ("magma_heart", "charm", "Corazón de Magma", "Al recibir un golpe cuerpo a cuerpo, 15 % de quemar al atacante. Dejas huellas incandescentes."),
 ("midnight_veil", "head", "Velo de Medianoche", "El sol te hace la mitad de daño."),
]
# Materiales de jefe con alternativa vanilla (para que el curio se pueda fabricar sin el mod).
CURIO_MATS = {
 "pixie_dust": [opt(f"{IAF}:pixie_dust"), "minecraft:glowstone_dust"],
 "sea_serpent": [opt(f"{IAF}:sea_serpent_scales_{c}") for c in ["blue", "bronze", "deepblue", "green", "purple", "red", "teal"]] + ["minecraft:nautilus_shell"],
 "cyclops": [opt(f"{IAF}:cyclops_eye"), "minecraft:ghast_tear"],
 "gorgon": [opt(f"{IAF}:gorgon_head"), "minecraft:wither_skeleton_skull"],
 "storm": [opt(f"{IAF}:lightning_dragon_heart"), "minecraft:trident"],
 "phoenix": [opt("cataclysm:ignitium_ingot"), "minecraft:nether_star"],
 "arcane": [opt("irons_spellbooks:arcane_essence"), "minecraft:lapis_block"],
 "hippogryph": [opt(f"{IAF}:hippogryph_talon"), "minecraft:phantom_membrane"],
 "dragon_relic": [opt(f"{IAF}:dragonbone"), "minecraft:dragon_breath"],
 "troll": [opt(f"{IAF}:troll_tusk"), "minecraft:bone_block"],
}

def curio_recipes():
    m = lambda n: tag(r(f"curio/{n}"))
    S = item(r("cured_strap"))
    def sl(name, ings): return {"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": ings, "result": {"item": r(name)}}
    I = item
    return {
     "wisp_lantern": shaped([" A ", "GLG", " A "], {"A": I("minecraft:amethyst_shard"), "G": I("minecraft:glowstone_dust"), "L": I("minecraft:soul_lantern")}, r("wisp_lantern")),
     "last_breath_compass": shaped([" G ", "GCG", " E "], {"G": I("minecraft:gold_ingot"), "C": I("minecraft:compass"), "E": I("minecraft:ender_pearl")}, r("last_breath_compass")),
     "hermit_medallion": shaped(["III", "IRI", " I "], {"I": I("minecraft:iron_ingot"), "R": I("minecraft:redstone_block")}, r("hermit_medallion")),
     "traveler_belt": shaped(["SSS", "SCS"], {"S": S, "C": I("minecraft:chest")}, r("traveler_belt")),
     "frost_ring": shaped([" B ", "G G", " G "], {"B": I("minecraft:blue_ice"), "G": I("minecraft:gold_ingot")}, r("frost_ring")),
     "pixie_bell": shaped(["P P", " B ", "P P"], {"P": m("pixie_dust"), "B": I("minecraft:bell")}, r("pixie_bell")),
     "serpent_pendant": shaped([" C ", "S S", " S "], {"C": I("minecraft:chain"), "S": m("sea_serpent")}, r("serpent_pendant")),
     "cyclops_horn": sl("cyclops_horn", [I("minecraft:goat_horn"), m("cyclops"), I("minecraft:gold_ingot"), I("minecraft:gold_ingot")]),
     "gorgon_eye": shaped([" G ", "GEG", " G "], {"G": I("minecraft:gold_ingot"), "E": m("gorgon")}, r("gorgon_eye")),
     "storm_talisman": shaped(["CRC", "RTR", "CRC"], {"C": I("minecraft:copper_ingot"), "R": I("minecraft:lightning_rod"), "T": m("storm")}, r("storm_talisman")),
     "phoenix_feather": shaped(["FBF", "BNB", "FBF"], {"F": I("minecraft:feather"), "B": I("minecraft:blaze_powder"), "N": m("phoenix")}, r("phoenix_feather")),
     "archmage_prism": shaped([" A ", "AEA", " A "], {"A": I("minecraft:amethyst_shard"), "E": m("arcane")}, r("archmage_prism")),
     "arcane_hourglass": shaped(["GGG", "GCG", "GGG"], {"G": I("minecraft:glass"), "C": I("minecraft:clock")}, r("arcane_hourglass")),
     "oath_reliquary": shaped([" G ", "GAG", " G "], {"G": I("minecraft:gold_ingot"), "A": I("minecraft:golden_apple")}, r("oath_reliquary")),
     "pocket_banner": sl("pocket_banner", [tag("minecraft:banners"), I("minecraft:gold_ingot"), I("minecraft:gold_ingot")]),
     "fang_collar": sl("fang_collar", [I("minecraft:string"), I("minecraft:bone"), I("minecraft:bone"), I("minecraft:bone"), S]),
     "war_drum": shaped(["LLL", "PNP"], {"L": I("minecraft:leather"), "P": tag("minecraft:planks"), "N": I("minecraft:note_block")}, r("war_drum")),
     "trophy_skull": sl("trophy_skull", [I("minecraft:wither_skeleton_skull"), I("minecraft:gold_block"), S, S]),
     "crow_cloak": shaped(["PFP", "FLF", "PFP"], {"P": I("minecraft:phantom_membrane"), "F": I("minecraft:feather"), "L": I("minecraft:black_wool")}, r("crow_cloak")),
     "loaded_dice": sl("loaded_dice", [I("minecraft:bone_block"), I("minecraft:gold_nugget"), I("minecraft:gold_nugget")]),
     "hippogryph_feather": sl("hippogryph_feather", [I("minecraft:feather"), I("minecraft:feather"), I("minecraft:feather"), m("hippogryph")]),
     "dragonslayer_quiver": shaped(["LDL", "LAL", "LLL"], {"L": I("minecraft:leather"), "D": m("dragon_relic"), "A": I("minecraft:arrow")}, r("dragonslayer_quiver")),
     "stance_rosary": sl("stance_rosary", [I("minecraft:string"), I("minecraft:amethyst_shard"), I("minecraft:amethyst_shard"), I("minecraft:amethyst_shard"), I("minecraft:quartz"), I("minecraft:quartz")]),
     "temple_bell": sl("temple_bell", [I("minecraft:bell"), item(r("martial_cloth"))]),
     "guild_insignia": sl("guild_insignia", [I("minecraft:gold_ingot"), I("minecraft:iron_ingot"), I("minecraft:copper_ingot"), I("minecraft:emerald")]),
     "troll_earring": sl("troll_earring", [I("minecraft:chain"), m("troll")]),
     "mine_lantern": sl("mine_lantern", [I("minecraft:lantern"), S, S, I("minecraft:glow_ink_sac")]),
     "evergreen_leaf": sl("evergreen_leaf", [I("minecraft:emerald"), tag("minecraft:leaves"), tag("minecraft:leaves"), tag("minecraft:leaves"), tag("minecraft:leaves"), I("minecraft:glistering_melon_slice")]),
     "trinket_pouch": sl("trinket_pouch", [I("minecraft:leather"), I("minecraft:leather"), I("minecraft:string"), I("minecraft:gold_nugget"), I("minecraft:gold_nugget"), I("minecraft:gold_nugget")]),
     "tide_conch": sl("tide_conch", [I("minecraft:nautilus_shell"), I("minecraft:prismarine_crystals"), I("minecraft:prismarine_crystals")]),
     "magma_heart": sl("magma_heart", [I("minecraft:magma_cream"), I("minecraft:magma_cream"), I("minecraft:magma_cream"), I("minecraft:blaze_powder"), I("minecraft:blaze_powder"), I("minecraft:fire_charge")]),
     "midnight_veil": sl("midnight_veil", [I("minecraft:black_wool"), I("minecraft:black_wool"), I("minecraft:phantom_membrane"), I("minecraft:string")]),
    }

def build_curios(dump, out_dir):
    data = os.path.join(out_dir, "data")
    assets = os.path.join(out_dir, "assets", NS)
    recs = curio_recipes()
    assert set(recs) == {c[0] for c in CURIOS}
    for name, recipe in recs.items():
        dump(os.path.join(data, NS, "recipes", "curio", name + ".json"), recipe)
    for name, values in CURIO_MATS.items():
        dump(os.path.join(data, NS, "tags", "items", "curio", name + ".json"), {"replace": False, "values": values})
    by_slot = {}
    for cid, slot, _, _ in CURIOS: by_slot.setdefault(slot, []).append(r(cid))
    by_slot.setdefault("hands", [])
    for slot, items in by_slot.items():
        path = os.path.join(data, "curios", "tags", "items", slot + ".json")
        if slot == "hands": items = items + [r(f"knuckles_{t}") for t in WEAPON_TIERS]
        dump(path, {"replace": False, "values": items})
    slots = sorted(set(by_slot) | {"hands"})
    dump(os.path.join(data, NS, "curios", "entities", "player.json"), {"entities": ["player"], "slots": slots})
    lang = {f"curio.{NS}.rarity.common": "Común", f"curio.{NS}.rarity.rare": "Raro", f"curio.{NS}.rarity.epic": "Épico",
            f"curio.{NS}.rarity.relic": "Reliquia", f"curio.{NS}.requires_class": "Solo %s",
            f"curio.{NS}.requires_race": "Solo raza %s", f"curio.{NS}.relic_rule": "Solo puedes llevar una Reliquia a la vez.",
            f"curio.{NS}.trophy_skull.taken": "El cráneo guarda el trofeo de %s"}
    for cid, slot, name, desc in CURIOS:
        lang[f"item.{NS}.{cid}"] = name
        lang[f"curio.{NS}.{cid}.desc"] = desc
        dump(os.path.join(assets, "models", "item", cid + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{cid}"}})
    return lang
