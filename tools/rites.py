# Ritos de raza (anexo de razas, rev. 0.5, regla 5 y fichas de la sección 3) -> "rites" de cada subraza en
# races/*.json, textos, etiquetas y el Altar de Raza. Lo importa gen_data.py.
# Cada subraza tiene 3 ritos en orden: el 1 lleva al Rango 1, el 2 al 2 y el 3 al 3.
# Tipos de objetivo (los interpreta content/race/Rites.java):
#   kill        count + filtros          | kill_variety count por tipo + types | kill_styles count por estilo + styles
#   hits_taken  count (+ projectile)     | steals / barters count
#   offer       items: [{tag|item, count, distinct, pred}] (+ altar)          -> se hace en el altar
#   structure   blocks: [{tag|block, count}], radius, dy                       -> se comprueba en el altar
#   counter     key + count: lo suben las mecánicas raciales (lanzamientos, fusiones, bloques rotos…)
#   kill.by     derrota acreditada a una fuente racial (swarm, spring, sticky, breath, ram, gallop…)
#   boss        jefe de rito invocado en el altar (agachado + clic) con condiciones de invocación y de victoria
NS = "rpgclasses"

def kill(n, text, **f):          return dict(type="kill", count=n, filter=f, _t=text)
def variety(per, types, text):   return dict(type="kill_variety", count=per, types=types, _t=text)
def styles(per, n, text):        return dict(type="kill_styles", count=per, styles=n, _t=text)
def hits(n, text, **p):          return dict(type="hits_taken", count=n, _t=text, **p)
def steals(n, text):             return dict(type="steals", count=n, _t=text)
def barters(n, text):            return dict(type="barters", count=n, _t=text)
def offer(text, *items, **p):    return dict(type="offer", items=list(items), _t=text, **p)
def struct(text, *blocks, **p):  return dict(type="structure", blocks=list(blocks), _t=text, **p)
def boss(text, **p):             return dict(type="boss", _t=text, **p)
def counter(key, n, text):       return dict(type="counter", key=key, count=n, _t=text)   # contador que suben las mecánicas
def it(ref, n=1, distinct=0, pred=None, types=0):
    e = {"tag": ref[1:]} if ref.startswith("#") else {"item": ref}
    e["count"] = n
    if distinct: e["distinct"] = distinct   # n en total, de al menos `distinct` tipos
    if types: e["types"] = types            # n de cada uno de `types` tipos distintos
    if pred: e["pred"] = pred
    return e
def bl(ref, n):
    e = {"tag": ref[1:]} if ref.startswith("#") else {"block": ref}
    e["count"] = n
    return e
def rite(rid, kind, name, desc, *objectives):
    return dict(id=rid, kind=kind, _name=name, _desc=desc, objectives=list(objectives))

T = "#" + NS + ":rite/"
ENEMIES = "enemigos"

RITES = {
 # ------------------------------------------------------------------ Humano
 ("human", "polyvalent"): [
  rite("three_styles", "feat", "Los Tres Estilos", "Derrota 10 enemigos con cada uno de 3 estilos de combate distintos (magia, arco, armas pesadas, ligeras o desarmado).",
       styles(10, 3, "Estilos con 10 derrotas")),
  rite("guild_offering", "craft", "Ofrenda de los Gremios", "Ofrece en el altar un objeto de cada categoría: arma pesada, arma ligera, arco o ballesta, escudo, pieza de armadura y un pergamino o libro de hechizos.",
       offer("Ofrenda de los 6 gremios", it("*", pred="discipline_heavy"), it("*", pred="discipline_light"),
             it("*", pred="discipline_bow"), it("*", pred="discipline_shield"), it("*", pred="armor"), it("*", pred="spell_item"))),
  rite("two_walls", "ritual", "Prueba de los Dos Muros", "Invoca un jefe de rito en el altar y derrótalo usando las disciplinas de tus dos muros elegidos, con al menos el 25 % del daño en cada una (Armadura: llevar 3+ piezas; Escudos: bloquear 10 golpes).",
       boss("Jefe derrotado con tus dos muros", wall_share=.25, wall_mode="both"))],
 ("human", "specialist"): [
  rite("master_the_alien", "feat", "Dominar lo Ajeno", "Derrota 40 enemigos con la disciplina de tu muro elegido.",
       kill(40, "Enemigos derrotados con tu muro", target="enemy", style="wall")),
  rite("masterpiece", "craft", "Obra Maestra", "Ofrece en el altar un objeto de calidad alta (diamante o mejor, o encantado si no tiene nivel) de la disciplina de tu muro.",
       offer("Obra maestra de tu muro", it("*", pred="wall_masterpiece"))),
  rite("mastery_duel", "ritual", "Duelo de Maestría", "Invoca un jefe de rito y derrótalo causando al menos el 90 % del daño con la disciplina de tu muro.",
       boss("Jefe derrotado con tu muro (90 %)", wall_share=.90, wall_mode="main"))],
 ("human", "autodidact"): [
  rite("curiosity", "feat", "Curiosidad", "Derrota 6 enemigos de cada uno de 5 tipos de mob distintos.",
       variety(6, 5, "Tipos de enemigo con 6 derrotas")),
  rite("library", "craft", "Biblioteca", "Rodea el altar con 15 estanterías (como una mesa de encantamientos: a 2 bloques, en su altura o una más) y ofrece 10 libros.",
       struct("Estanterías alrededor del altar", bl("#" + NS + ":rite/bookshelves", 15), radius=2, dy=1),
       offer("10 libros", it("minecraft:book", 10))),
  rite("three_lores", "ritual", "Los Tres Saberes", "Ofrece un libro encantado con un encantamiento a su nivel máximo, una poción de nivel II y un mapa explorado casi por completo (90 %).",
       offer("Libro, poción y mapa", it("minecraft:enchanted_book", pred="max_level_book"), it("*", pred="potion_2"),
             it("minecraft:filled_map", pred="explored_map")))],
 # ------------------------------------------------------------------ Orco
 ("orc", "stoneskin"): [
  rite("rock_skin", "feat", "Piel de Roca", "Recibe 100 golpes y derrota 30 enemigos.",
       hits(100, "Golpes recibidos"), kill(30, "Enemigos derrotados", target="enemy")),
  rite("endurance_forge", "craft", "Forja del Aguante", "Ofrece 4 obsidianas, 16 lingotes de hierro y 1 yunque.",
       offer("Obsidiana, hierro y yunque", it("minecraft:obsidian", 4), it("minecraft:iron_ingot", 16), it("minecraft:anvil"))),
  rite("wall_trial", "ritual", "Prueba del Muro", "Derrota a un jefe de rito tras recibir al menos 10 golpes suyos sin morir.",
       boss("Jefe derrotado tras 10 golpes suyos", boss_hits=10))],
 ("orc", "devourer"): [
  rite("first_prey", "feat", "Primera Presa", "Derrota 20 enemigos y caza 20 animales.",
       kill(20, "Enemigos derrotados", target="enemy"), kill(20, "Animales cazados", target="animal")),
  rite("banquet", "craft", "Banquete", "Ofrece 16 carnes cocinadas de al menos 3 tipos.",
       offer("16 carnes cocinadas (3 tipos)", it(T + "cooked_meat", 16, distinct=3))),
  rite("boss_feast", "ritual", "Festín del Jefe", "Derrota a un jefe de rito y ofrece en el altar uno de sus botines.",
       boss("Jefe de rito derrotado"), offer("Un botín del jefe", it("*", pred="rite_drop")))],
 ("orc", "warlord"): [
  rite("brothers_in_arms", "feat", "Compañía de Armas", "Derrota 40 enemigos con un aliado (jugador o mascota) a 12 bloques o menos.",
       kill(40, "Enemigos derrotados junto a un aliado", target="enemy", ally_radius=12)),
  rite("camp", "craft", "Campamento", "Construye junto al altar (6 bloques) un campamento: 1 fogata, 4 estandartes y 2 camas.",
       struct("Campamento junto al altar", bl("#minecraft:campfires", 1), bl("#minecraft:banners", 4), bl("#minecraft:beds", 2), radius=6, dy=3)),
  rite("call_to_arms", "ritual", "Llamada a las Armas", "Derrota a un jefe de rito con al menos 2 aliados (jugadores o mascotas) a 12 bloques o menos.",
       boss("Jefe derrotado con 2 aliados cerca", allies=2, ally_radius=12))],
 # ------------------------------------------------------------------ Enano
 ("dwarf", "deep"): [
  rite("under_the_rock", "feat", "Bajo la Roca", "Derrota 30 enemigos bajo tierra (sin cielo encima).",
       kill(30, "Enemigos derrotados bajo tierra", target="enemy", underground=True)),
  rite("vein_tribute", "craft", "Tributo de las Vetas", "Ofrece 64 minerales de al menos 3 tipos.",
       offer("64 minerales (3 tipos)", it(T + "ores", 64, distinct=3))),
  rite("abyss_rite", "ritual", "Rito del Abismo", "Derrota a un jefe de rito invocado con el altar bajo tierra.",
       boss("Jefe derrotado (altar bajo tierra)", altar="underground"))],
 ("dwarf", "smith"): [
  rite("own_edge", "feat", "Filo Propio", "Derrota 30 enemigos con un arma fabricada por ti.",
       kill(30, "Enemigos derrotados con tu arma", target="enemy", weapon_crafted=True)),
  rite("masters_work", "craft", "Obra del Maestro", "Ofrece un conjunto de armadura de hierro y un arma de hierro fabricados por ti.",
       offer("Armadura y arma de hierro tuyas", it("minecraft:iron_helmet", pred="crafted_by_you"), it("minecraft:iron_chestplate", pred="crafted_by_you"),
             it("minecraft:iron_leggings", pred="crafted_by_you"), it("minecraft:iron_boots", pred="crafted_by_you"),
             it(T + "iron_weapons", pred="crafted_by_you"))),
  rite("forge_trial", "ritual", "Prueba de la Forja", "Derrota a un jefe de rito llevando las 4 piezas de armadura de diamante (o netherita) fabricadas por ti.",
       boss("Jefe derrotado con tu armadura de diamante", crafted_diamond=True))],
 ("dwarf", "demolisher"): [
  rite("blasting", "feat", "Voladura", "Derrota 20 enemigos con explosiones tuyas.",
       kill(20, "Enemigos derrotados con explosiones", target="enemy", explosion=True)),
  rite("powder_store", "craft", "Polvorín", "Fabrica y ofrece 16 TNT (fabricadas mientras haces este rito).",
       offer("16 TNT fabricadas por ti", it("minecraft:tnt", 16, pred="crafted_by_you"))),
  rite("cataclysm", "ritual", "Cataclismo", "Derrota a un jefe de rito que reciba al menos 4 explosiones tuyas.",
       boss("Jefe derrotado tras 4 explosiones", explosions=4))],
 # ------------------------------------------------------------------ Elfo
 ("elf", "dark"): [
  rite("night_hunt", "feat", "Caza Nocturna", "Derrota 30 enemigos con luz 7 o menos.",
       kill(30, "Enemigos derrotados en la oscuridad", target="enemy", max_light=7)),
  rite("smoke_offering", "craft", "Ofrenda de Humo", "Ofrece 8 fogatas y 16 carbón vegetal.",
       offer("Fogatas y carbón vegetal", it("minecraft:campfire", 8), it("minecraft:charcoal", 16))),
  rite("shadow_veil", "ritual", "Velo de Sombra", "Derrota de noche a un jefe de rito usando la Nube de Humo al menos 3 veces en el combate.",
       boss("Jefe derrotado de noche con 3 Nubes", night=True, ability="race_smoke_cloud", uses=3))],
 ("elf", "arcane"): [
  rite("enchanted_edge", "feat", "Filo Encantado", "Derrota 30 enemigos con un arma que tú encantaste en la mesa.",
       kill(30, "Enemigos derrotados con tu arma encantada", target="enemy", weapon_enchanted=True)),
  rite("three_enchantments", "craft", "Tres Encantamientos", "Encanta 3 objetos en la mesa y ofrécelos.",
       offer("3 objetos encantados por ti", it("*", 3, pred="enchanted_by_you"))),
  rite("twin_books", "ritual", "Dos Libros Gemelos", "Ofrece 2 libros con el mismo encantamiento a su nivel máximo.",
       offer("2 libros gemelos", it("minecraft:enchanted_book", 2, pred="twin_books")))],
 ("elf", "sylvan"): [
  rite("ranger", "feat", "Guardabosques", "Derrota 30 enemigos en biomas de bosque.",
       kill(30, "Enemigos derrotados en el bosque", target="enemy", biome="minecraft:is_forest")),
  rite("ritual_harvest", "craft", "Cosecha Ritual", "Ofrece 16 de cada uno de 4 cultivos distintos (64 en total).",
       offer("4 cultivos × 16", it(T + "crops", 16, types=4))),
  rite("forest_heart", "ritual", "Corazón del Bosque", "Derrota a un jefe de rito invocado con el altar en un bioma de bosque.",
       boss("Jefe derrotado (altar en el bosque)", altar="forest"))],
 # ------------------------------------------------------------------ Goblin
 ("goblin", "wolf_rider"): [
  rite("cavalcade", "feat", "Cabalgata", "Derrota 30 enemigos montado.",
       kill(30, "Enemigos derrotados montado", target="enemy", mounted=True)),
  rite("pack", "craft", "Manada", "Ofrece 32 huesos y 16 carnes cocinadas.",
       offer("Huesos y carne", it("minecraft:bone", 32), it(T + "cooked_meat", 16))),
  rite("inseparable_rider", "ritual", "Jinete Inseparable", "Derrota a un jefe de rito montado en tu lobo durante todo el combate sin que muera.",
       boss("Jefe derrotado sin bajar del lobo", mounted=True))],
 ("goblin", "thief"): [
  rite("light_fingers", "feat", "Dedos Ligeros", "Roba con éxito a 30 mobs.",
       steals(30, "Robos a mobs")),
  rite("varied_loot", "craft", "Botín Variado", "Ofrece 64 objetos de botín de mob de al menos 8 tipos.",
       offer("64 botines de mob (8 tipos)", it(T + "mob_loot", 64, distinct=8))),
  rite("great_heist", "ritual", "Atraco Mayor", "Ofrece 3 piezas de equipo de mob (robadas o soltadas por mobs que mataste) y derrota a un jefe de rito.",
       offer("3 piezas de equipo de mob", it("*", 3, pred="mob_equipment")), boss("Jefe de rito derrotado"))],
 ("goblin", "trapper"): [
  rite("trap_hunter", "feat", "Cazador de Trampas", "Derrota 20 enemigos atrapados por tu Cepo.",
       kill(20, "Enemigos atrapados derrotados", target="enemy", snared=True)),
  rite("mechanisms", "craft", "Mecanismos", "Ofrece 4 dispensadores, 4 pistones y 16 hilos.",
       offer("Dispensadores, pistones e hilo", it("minecraft:dispenser", 4), it("minecraft:piston", 4), it("minecraft:string", 16))),
  rite("caught_prey", "ritual", "Presa Atrapada", "Derrota a un jefe de rito inmovilizado por tu Cepo al menos 3 veces.",
       boss("Jefe derrotado tras 3 Cepos", snares=3))],
 # ------------------------------------------------------------------ Tritón
 ("triton", "fisher"): [
  rite("deep_sea_fishing", "feat", "Pesca de Altura", "Derrota 20 mobs acuáticos.",
       kill(20, "Mobs acuáticos derrotados", target="any", entity_tag=NS + ":rite/aquatic")),
  rite("big_catch", "craft", "Gran Captura", "Ofrece 32 peces de 4 tipos y una caña encantada.",
       offer("Peces y caña encantada", it(T + "fish", 32, distinct=4), it("minecraft:fishing_rod", pred="enchanted"))),
  rite("sunken_treasure", "ritual", "Tesoro del Fondo", "Ofrece un objeto de tesoro que hayas pescado y derrota a un jefe de rito.",
       offer("Un tesoro pescado", it(T + "fishing_treasure", pred="fished")), boss("Jefe de rito derrotado"))],
 ("triton", "shaper"): [
  rite("current", "feat", "Corriente", "Derrota 30 enemigos que estén en el agua.",
       kill(30, "Enemigos derrotados en el agua", target="enemy", target_in_water=True)),
  rite("reef", "craft", "Arrecife", "Ofrece 4 esponjas, 8 bloques de coral y 3 cubos de agua.",
       offer("Esponjas, coral y agua", it("minecraft:sponge", 4), it("#minecraft:coral_blocks", 8), it("minecraft:water_bucket", 3))),
  rite("spring_tide", "ritual", "Marea Viva", "Derrota a un jefe de rito usando Ola o Muro de Agua al menos 3 veces.",
       boss("Jefe derrotado con 3 olas o muros", ability="race_wave,race_water_wall", uses=3))],
 ("triton", "abyssal"): [
  rite("depth_hunt", "feat", "Caza en las Profundidades", "Derrota 30 enemigos mientras estás sumergido.",
       kill(30, "Enemigos derrotados sumergido", target="enemy", submerged=True)),
  rite("ocean_offering", "craft", "Ofrenda del Océano", "Con el altar bajo el agua, ofrece 16 cristales de prismarina, 8 faroles marinos y 3 conchas de nautilo.",
       offer("Prismarina, faroles y nautilos (altar bajo el agua)", it("minecraft:prismarine_crystals", 16), it("minecraft:sea_lantern", 8),
             it("minecraft:nautilus_shell", 3), altar="underwater")),
  rite("abyss_court", "ritual", "Corte del Abismo", "Derrota a un guardián ancestral, o a un jefe de rito invocado con el altar bajo el agua.",
       boss("Guardián ancestral o jefe (altar bajo el agua)", altar="underwater", alt_entity="minecraft:elder_guardian"))],
 # ------------------------------------------------------------------ Infernal
 ("infernal", "native"): [
  rite("nether_hunt", "feat", "Caza Infernal", "Derrota 30 mobs hostiles del Nether (no cuentan piglins ni hoglins).",
       kill(30, "Mobs del Nether derrotados", target="any", entity_tag=NS + ":rite/nether_hostile")),
  rite("nether_trade", "craft", "Comercio Infernal", "Haz 20 trueques con piglins y ofrece 16 cuarzos del Nether.",
       barters(20, "Trueques con piglins"), offer("16 cuarzos", it("minecraft:quartz", 16))),
  rite("nether_rite", "ritual", "Rito del Nether", "Derrota a un jefe de rito invocado con el altar en el Nether.",
       boss("Jefe derrotado (altar en el Nether)", altar="nether"))],
 ("infernal", "smelter"): [
  rite("lava_combat", "feat", "Combate sobre Lava", "Derrota 20 enemigos mientras estás de pie sobre lava.",
       kill(20, "Enemigos derrotados sobre lava", target="enemy", on_lava=True)),
  rite("smelting_offering", "craft", "Ofrenda de Fundición", "Ofrece 64 lingotes de al menos 3 tipos.",
       offer("64 lingotes (3 tipos)", it("#forge:ingots", 64, distinct=3))),
  rite("forge_rite", "ritual", "Rito de la Forja", "Derrota a un jefe de rito invocado con el altar a 4 bloques o menos de lava.",
       boss("Jefe derrotado (altar junto a lava)", altar="near_lava"))],
 ("infernal", "pyromaniac"): [
  rite("crossfire", "feat", "Fuego Cruzado", "Derrota 40 enemigos con tus conjuros raciales.",
       kill(40, "Enemigos derrotados con conjuros raciales", target="enemy", racial_spell=True)),
  rite("fuels", "craft", "Combustibles", "Ofrece 8 cargas de fuego, 4 bloques de magma y 8 varas de blaze.",
       offer("Cargas de fuego, magma y blaze", it("minecraft:fire_charge", 8), it("minecraft:magma_block", 4), it("minecraft:blaze_rod", 8))),
  rite("ritual_blaze", "ritual", "Incendio Ritual", "Derrota a un jefe de rito causando al menos el 50 % del daño con conjuros raciales.",
       boss("Jefe derrotado con 50 % de conjuros raciales", racial_share=.5))],
 # ------------------------------------------------------------------ No-muerto
 ("undead", "vampire"): [
  rite("night_hunt", "feat", "Caza Nocturna", "Derrota 30 mobs hostiles de noche o con luz 7 o menos.",
       kill(30, "Enemigos derrotados de noche", target="enemy", dark_or_night=True)),
  rite("blood_offering", "craft", "Ofrenda de Sangre", "Ofrece 16 polvos de redstone, 8 remolachas y 8 hongos rojos.",
       offer("Redstone, remolacha y hongos", it("minecraft:redstone", 16), it("minecraft:beetroot", 8), it("minecraft:red_mushroom", 8))),
  rite("crimson_banquet", "ritual", "Banquete Carmesí", "Derrota a un jefe de rito recuperando al menos 100 HP con Sed durante el combate.",
       boss("Jefe derrotado curando 100 HP con Sed", thirst=100))],
 ("undead", "skeleton"): [
  rite("broken_bones", "feat", "Huesos Rotos", "Recibe 50 impactos de proyectil y derrota 20 enemigos (no cuentan los esqueletos).",
       hits(50, "Impactos de proyectil recibidos", projectile=True),
       kill(20, "Enemigos derrotados (sin esqueletos)", target="enemy", exclude_tag="minecraft:skeletons")),
  rite("ossuary", "craft", "Osario", "Ofrece 64 huesos, 32 flechas y 4 bloques de hueso.",
       offer("Huesos, flechas y bloques de hueso", it("minecraft:bone", 64), it("#minecraft:arrows", 32), it("minecraft:bone_block", 4))),
  rite("crossfire_trial", "ritual", "Prueba del Fuego Cruzado", "Derrota a un jefe de rito tras recibir al menos 10 proyectiles suyos o de su escolta sin morir.",
       boss("Jefe derrotado tras 10 proyectiles", projectiles=10))],
 ("undead", "specter"): [
  rite("air_hunt", "feat", "Caza en el Aire", "Derrota 20 mobs voladores (phantoms, vexes, blazes, ghasts…).",
       kill(20, "Mobs voladores derrotados", target="any", entity_tag=NS + ":rite/flying")),
  rite("ethereal_offering", "craft", "Ofrenda Etérea", "Ofrece 16 membranas de phantom, 16 plumas y 4 perlas de ender.",
       offer("Membranas, plumas y perlas", it("minecraft:phantom_membrane", 16), it("minecraft:feather", 16), it("minecraft:ender_pearl", 4))),
  rite("ethereal_dance", "ritual", "Danza Etérea", "Derrota a un jefe de rito usando Forma Etérea al menos 3 veces en el combate.",
       boss("Jefe derrotado con 3 Formas Etéreas", ability="race_ethereal", uses=3))],
 # ------------------------------------------------------------------ Slime (adenda 0.7)
 ("slime", "swarm"): [
  rite("first_division", "feat", "Primera División", "Derrota 30 enemigos con el contacto de tus slimes pequeños.",
       kill(30, "Enemigos derrotados por tus slimes", target="enemy", by="swarm")),
  rite("jelly_brood", "craft", "Cría de Gelatina", "Ofrece 32 bolas de slime, 4 bloques de slime y 3 pociones de curación.",
       offer("Slime y pociones de curación", it("minecraft:slime_ball", 32), it("minecraft:slime_block", 4), it("minecraft:potion", 3, pred="potion:healing"))),
  rite("jelly_tide", "ritual", "Marea de Gelatina", "Derrota a un jefe de rito estando dividido, con al menos 6 slimes vivos al final y al menos el 50 % del daño por contacto.",
       boss("Jefe derrotado por el Enjambre", source="swarm", source_share=.5, min_slimes=6))],
 ("slime", "spring"): [
  rite("launched", "feat", "Disparado", "Haz 40 lanzamientos con al menos 2 s de carga y derrota 15 enemigos con el choque de un lanzamiento.",
       counter("spring_launch", 40, "Lanzamientos cargados"), kill(15, "Enemigos derrotados con el choque", target="enemy", by="spring")),
  rite("springs", "craft", "Muelles", "Ofrece 8 pistones, 4 pistones pegajosos, 16 bolas de slime y 4 bloques de slime.",
       offer("Pistones y slime", it("minecraft:piston", 8), it("minecraft:sticky_piston", 4), it("minecraft:slime_ball", 16), it("minecraft:slime_block", 4))),
  rite("giant_leap", "ritual", "Salto de Gigante", "Derrota a un jefe de rito golpeándolo con al menos 3 choques de lanzamiento durante el combate.",
       boss("Jefe derrotado con 3 choques", counts={"spring_crash": 3}))],
 ("slime", "mitotic"): [
  rite("two_in_one", "feat", "Dos en Uno", "Divídete (por el golpe de un enemigo) y vuelve a fusionarte con tu copia 2 veces.",
       counter("mitosis_merge", 2, "Divisiones con fusión")),
  rite("jelly_mold", "craft", "Molde de Gelatina", "Ofrece 2 objetos iguales de cada uno de 6 tipos distintos de calidad hierro o superior.",
       offer("6 pares de objetos de hierro o mejores", it("*", 2, types=6, pred="iron_quality"))),
  rite("twins", "ritual", "Gemelos", "Derrota a un jefe de rito habiéndote dividido durante el combate y fusiónate con tu copia viva al final.",
       boss("Jefe derrotado tras dividirte y fusionarte", counts={"mitosis_split": 1, "mitosis_merge": 1}))],
 ("slime", "viscous"): [
  rite("sticky", "feat", "Pegajoso", "Derrota 20 enemigos mientras estás pegado a una pared o a una entidad.",
       kill(20, "Enemigos derrotados estando pegado", target="enemy", by="sticky")),
  rite("sap_and_honey", "craft", "Savia y Miel", "Ofrece 8 bloques de miel, 16 botellas de miel, 16 bolas de slime y 8 telarañas.",
       offer("Miel, slime y telarañas", it("minecraft:honey_block", 8), it("minecraft:honey_bottle", 16), it("minecraft:slime_ball", 16), it("minecraft:cobweb", 8))),
  rite("tick", "ritual", "Garrapata", "Derrota a un jefe de rito habiendo estado pegado a él al menos 10 s en total y con al menos 20 golpes que apliquen Lentitud.",
       boss("Jefe derrotado pegado a él", counts={"sticky_boss_seconds": 10, "slow_hits": 20}))],
 # ------------------------------------------------------------------ Coloso
 ("colossus", "porter"): [
  rite("burden", "feat", "Carga a Cuestas", "Derrota 30 enemigos con al menos 3 huecos del cinturón ocupados.",
       kill(30, "Enemigos derrotados con el cinturón cargado", target="enemy", belt_min=3)),
  rite("leather_belt", "craft", "Cinturón de Cuero", "Ofrece 4 cofres, 2 hornos, 1 mesa de crafteo, 16 cueros y 16 lingotes de hierro.",
       offer("Cofres, hornos, mesa, cuero y hierro", it("minecraft:chest", 4), it("minecraft:furnace", 2), it("minecraft:crafting_table"),
             it("minecraft:leather", 16), it("minecraft:iron_ingot", 16))),
  rite("caravan", "ritual", "Caravana", "Derrota a un jefe de rito con un aliado montado sobre ti que cause al menos el 25 % del daño.",
       boss("Jefe derrotado con un pasajero", source="passenger", source_share=.25))],
 ("colossus", "titan"): [
  rite("heavy_steps", "feat", "Pisadas Pesadas", "Derrota 20 enemigos y rompe 200 bloques con la rotura de área (de pie).",
       kill(20, "Enemigos derrotados", target="enemy"), counter("area_blocks", 200, "Bloques rotos en área")),
  rite("stone_on_stone", "craft", "Piedra sobre Piedra", "Ofrece 4 bloques de hierro, 8 obsidianas, 64 adoquines y 1 yunque.",
       offer("Hierro, obsidiana, adoquín y yunque", it("minecraft:iron_block", 4), it("minecraft:obsidian", 8), it("minecraft:cobblestone", 64), it("minecraft:anvil"))),
  rite("titan_roar", "ritual", "Rugido de Titán", "Derrota a un jefe de rito con la forma Titán activa y causa al menos el 50 % del daño en esa forma.",
       boss("Jefe derrotado en forma Titán", source="titan", source_share=.5))],
 # ------------------------------------------------------------------ Dracónido
 ("draconid", "fire"): [
  rite("burning_breath", "feat", "Aliento Ardiente", "Derrota 30 enemigos con tu aliento.",
       kill(30, "Enemigos derrotados con el aliento", target="enemy", by="breath")),
  rite("embers", "craft", "Brasas", "Ofrece 8 varas de blaze, 16 carbones vegetales y 4 cubos de lava.",
       offer("Blaze, carbón vegetal y lava", it("minecraft:blaze_rod", 8), it("minecraft:charcoal", 16), it("minecraft:lava_bucket", 4))),
  rite("dragon_pyre", "ritual", "Hoguera del Dragón", "Con el altar en el Nether, derrota a un jefe de rito causando al menos el 50 % del daño con el aliento.",
       boss("Jefe derrotado con el aliento (Nether)", altar="nether", source="breath", source_share=.5))],
 ("draconid", "ice"): [
  rite("frost_breath", "feat", "Aliento Gélido", "Derrota 30 enemigos con tu aliento.",
       kill(30, "Enemigos derrotados con el aliento", target="enemy", by="breath")),
  rite("frost", "craft", "Escarcha", "Ofrece 64 bloques de nieve, 16 de hielo compacto y 4 de hielo azul.",
       offer("Nieve y hielo", it("minecraft:snow_block", 64), it("minecraft:packed_ice", 16), it("minecraft:blue_ice", 4))),
  rite("eternal_winter", "ritual", "Invierno Eterno", "Con el altar en un bioma nevado o helado, derrota a un jefe de rito causando al menos el 50 % del daño con el aliento.",
       boss("Jefe derrotado con el aliento (nieve)", altar="snowy", source="breath", source_share=.5))],
 ("draconid", "lightning"): [
  rite("storm_breath", "feat", "Aliento de Tormenta", "Derrota 30 enemigos con tu aliento (cuentan los saltos del rayo).",
       kill(30, "Enemigos derrotados con el aliento", target="enemy", by="breath")),
  rite("lightning_rods", "craft", "Pararrayos", "Ofrece 4 pararrayos, 32 lingotes de cobre y 16 de redstone.",
       offer("Pararrayos, cobre y redstone", it("minecraft:lightning_rod", 4), it("minecraft:copper_ingot", 32), it("minecraft:redstone", 16))),
  rite("ritual_storm", "ritual", "Tormenta Ritual", "Invoca un jefe de rito durante una tormenta, con el altar a cielo abierto, y derrótalo causando al menos el 50 % del daño con el aliento.",
       boss("Jefe derrotado con el aliento (tormenta)", altar="storm", source="breath", source_share=.5))],
 ("draconid", "poison"): [
  rite("toxic_breath", "feat", "Aliento Tóxico", "Derrota 30 enemigos envenenados por tu aliento (no cuentan los no-muertos).",
       kill(30, "Enemigos envenenados derrotados", target="enemy", by="breath_poison")),
  rite("antidote", "craft", "Antídoto", "Ofrece 16 ojos de araña, 4 pociones de veneno, 8 patatas venenosas y 4 peces globo.",
       offer("Ojos de araña, veneno, patatas y peces globo", it("minecraft:spider_eye", 16), it("minecraft:potion", 4, pred="potion:poison"),
             it("minecraft:poisonous_potato", 8), it("minecraft:pufferfish", 4))),
  rite("deadly_venom", "ritual", "Ponzoña Mortal", "Derrota a un jefe de rito aplicándole Veneno con tu aliento al menos 5 veces.",
       boss("Jefe derrotado con 5 venenos", counts={"breath_poisoned": 5}))],
 ("draconid", "acid"): [
  rite("corrosion", "feat", "Corrosión", "Derrota 30 enemigos con armadura corroídos por tu aliento.",
       kill(30, "Enemigos con armadura corroídos derrotados", target="enemy", by="breath_acid")),
  rite("corrosive_alchemy", "craft", "Alquimia Corrosiva", "Ofrece 4 pociones de Daño, 16 ojos de araña y 16 bolas de slime.",
       offer("Pociones de Daño, ojos de araña y slime", it("minecraft:potion", 4, pred="potion:harming"), it("minecraft:spider_eye", 16), it("minecraft:slime_ball", 16))),
  rite("melt_iron", "ritual", "Fundir Hierro", "Derrota a un jefe de rito aplicándole Corrosión al menos 5 veces.",
       boss("Jefe derrotado con 5 corrosiones", counts={"breath_corroded": 5}))],
 ("draconid", "water"): [
  rite("sea_breath", "feat", "Aliento del Mar", "Derrota 30 enemigos con tu aliento, 10 de ellos estando tú sumergido.",
       kill(30, "Enemigos derrotados con el aliento", target="enemy", by="breath"),
       kill(10, "Derrotados con el aliento estando sumergido", target="enemy", by="breath", submerged=True)),
  rite("tide_offering", "craft", "Ofrenda de la Marea", "Ofrece 16 peces de al menos 3 tipos, 32 algas y 8 pociones de respiración acuática.",
       offer("Peces, algas y pociones", it(T + "fish", 16, distinct=3), it("minecraft:kelp", 32), it("minecraft:potion", 8, pred="potion:water_breathing"))),
  rite("ocean_court", "ritual", "Corte del Océano", "Con el altar bajo el agua (al menos 3 bloques de profundidad), derrota a un jefe de rito causando al menos el 50 % del daño con el aliento.",
       boss("Jefe derrotado con el aliento (bajo el agua)", altar="deep_water", source="breath", source_share=.5))],
 # ------------------------------------------------------------------ Centauro
 ("centaur", "pegasus"): [
  rite("winged_gallop", "feat", "Cabalgata Alada", "Derrota 20 enemigos con la embestida y haz 20 caídas lentas desde al menos 8 bloques.",
       kill(20, "Enemigos derrotados con la embestida", target="enemy", by="ram"), counter("glide_8", 20, "Planeos desde 8 bloques")),
  rite("plumage", "craft", "Plumaje", "Ofrece 64 plumas, 16 membranas de phantom y 8 cohetes de fuegos artificiales.",
       offer("Plumas, membranas y cohetes", it("minecraft:feather", 64), it("minecraft:phantom_membrane", 16), it("minecraft:firework_rocket", 8))),
  rite("top_of_the_world", "ritual", "Cima del Mundo", "Con el altar a una altura de 150 o más, derrota a un jefe de rito planeando al menos 3 veces durante el combate.",
       boss("Jefe derrotado en lo alto con 3 planeos", altar="high", counts={"glide": 3}))],
 ("centaur", "unicorn"): [
  rite("silver_horn", "feat", "Cuerno de Plata", "Derrota 30 enemigos con la embestida.",
       kill(30, "Enemigos derrotados con la embestida", target="enemy", by="ram")),
  rite("lunar_offering", "craft", "Ofrenda Lunar", "Ofrece 4 manzanas doradas, 8 fragmentos de amatista, 16 lingotes de oro y 8 flores de al menos 4 tipos.",
       offer("Manzanas doradas, amatista, oro y flores", it("minecraft:golden_apple", 4), it("minecraft:amethyst_shard", 8), it("minecraft:gold_ingot", 16),
             it("#minecraft:small_flowers", 8, distinct=4))),
  rite("radiant_aura", "ritual", "Aura Radiante", "Derrota a un jefe de rito con al menos 2 aliados dentro de tu aura y causando al menos el 40 % del daño con la embestida.",
       boss("Jefe derrotado con aliados en el aura", allies=2, ally_radius=8, source="ram", source_share=.4))],
 ("centaur", "warrior"): [
  rite("war_charge", "feat", "Carga de Guerra", "Derrota 30 enemigos con ataques cuerpo a cuerpo a al menos el 40 % de tu velocidad máxima.",
       kill(30, "Enemigos derrotados al galope", target="enemy", by="gallop")),
  rite("panoply", "craft", "Panoplia", "Ofrece una armadura de hierro completa, 1 arma de hierro, 8 cueros y 16 lingotes de hierro.",
       offer("Armadura y arma de hierro, cuero y hierro", it("minecraft:iron_helmet"), it("minecraft:iron_chestplate"), it("minecraft:iron_leggings"),
             it("minecraft:iron_boots"), it(T + "iron_weapons"), it("minecraft:leather", 8), it("minecraft:iron_ingot", 16))),
  rite("unbroken_gallop", "ritual", "Galope Ininterrumpido", "Derrota a un jefe de rito encadenando al menos 10 golpes cuerpo a cuerpo sin bajar del 40 % de tu velocidad.",
       boss("Jefe derrotado con 10 golpes al galope", counts={"gallop_chain": 10}))],
}

# ------------------------------------------------------------------ etiquetas
def opt(ids): return [{"id": i, "required": False} for i in ids]
ENTITY_TAGS = {
 "rite/aquatic": ["minecraft:guardian", "minecraft:elder_guardian", "minecraft:drowned", "minecraft:squid", "minecraft:glow_squid",
                  "minecraft:cod", "minecraft:salmon", "minecraft:pufferfish", "minecraft:tropical_fish", "minecraft:dolphin",
                  "minecraft:turtle", "minecraft:axolotl", "minecraft:tadpole"],
 "rite/nether_hostile": ["minecraft:blaze", "minecraft:ghast", "minecraft:magma_cube", "minecraft:wither_skeleton",
                         "minecraft:skeleton", "minecraft:enderman", "minecraft:strider"],
 "rite/flying": ["minecraft:phantom", "minecraft:vex", "minecraft:blaze", "minecraft:ghast", "minecraft:bat", "minecraft:bee",
                 "minecraft:allay", "minecraft:parrot"],
 # Jefes de rito: de mods de jefes medianos si están (si un id no existe se ignora); si no hay ninguno, la lista vanilla.
 "rite_bosses": opt(["cataclysm:ender_golem", "cataclysm:ignited_revenant", "cataclysm:the_prowler", "cataclysm:coralssus",
                     "cataclysm:kobolediator", "cataclysm:wadjet", "cataclysm:aptrgangr",
                     "mowziesmobs:ferrous_wroughtnaut", "mowziesmobs:frostmaw", "mowziesmobs:umvuthi"]),
 "rite_bosses_fallback": ["minecraft:ravager", "minecraft:evoker"],
}
ITEM_TAGS = {
 "rite/cooked_meat": ["minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:cooked_mutton", "minecraft:cooked_chicken", "minecraft:cooked_rabbit"],
 "rite/ores": ["#forge:ores", "#forge:raw_materials", "minecraft:coal", "minecraft:diamond", "minecraft:emerald", "minecraft:lapis_lazuli",
               "minecraft:redstone", "minecraft:quartz", "minecraft:amethyst_shard"],
 "rite/crops": ["minecraft:wheat", "minecraft:carrot", "minecraft:potato", "minecraft:beetroot", "minecraft:melon_slice", "minecraft:pumpkin",
                "minecraft:sugar_cane", "minecraft:cocoa_beans", "minecraft:nether_wart", "minecraft:sweet_berries", "minecraft:glow_berries"],
 "rite/mob_loot": ["minecraft:rotten_flesh", "minecraft:bone", "minecraft:string", "minecraft:spider_eye", "minecraft:gunpowder",
                   "minecraft:ender_pearl", "minecraft:slime_ball", "minecraft:blaze_rod", "minecraft:ghast_tear", "minecraft:phantom_membrane",
                   "minecraft:arrow", "minecraft:magma_cream", "minecraft:prismarine_shard", "minecraft:prismarine_crystals",
                   "minecraft:ink_sac", "minecraft:glow_ink_sac", "minecraft:feather", "minecraft:leather", "minecraft:rabbit_hide",
                   "minecraft:shulker_shell", "minecraft:gold_nugget", "minecraft:copper_ingot", "minecraft:iron_ingot", "minecraft:glowstone_dust",
                   "minecraft:redstone", "minecraft:sugar", "minecraft:glass_bottle", "minecraft:stick", "minecraft:carrot", "minecraft:potato"],
 "rite/fish": ["#minecraft:fishes"],
 "rite/fishing_treasure": ["minecraft:bow", "minecraft:enchanted_book", "minecraft:fishing_rod", "minecraft:name_tag",
                           "minecraft:nautilus_shell", "minecraft:saddle"],
 "rite/iron_weapons": ["minecraft:iron_sword", "minecraft:iron_axe"],
}
BLOCK_TAGS = {
 "rite/bookshelves": ["minecraft:bookshelf", "minecraft:chiseled_bookshelf"],
 # Coloso (adenda 0.7): cultivos que rompe al pisarlos.
 "colossus_tramplable": ["#minecraft:crops", "minecraft:sweet_berry_bush", "minecraft:nether_wart", "minecraft:cocoa", "minecraft:pitcher_crop",
                         "minecraft:torchflower_crop"],
}
# Dracónido (adenda 0.7): dragones de Ice and Fire de cada elemento (opcionales).
ENTITY_TAGS.update({
 "dragons/fire": opt(["iceandfire:fire_dragon"]),
 "dragons/ice": opt(["iceandfire:ice_dragon"]),
 "dragons/lightning": opt(["iceandfire:lightning_dragon"]),
})
# Elementos sin etiqueta propia en el equipo: el código añade además el Veneno (daño mágico con el efecto activo),
# el ahogamiento y las escuelas de Iron's / T.O Magic 'n Extras.
DAMAGE_TAGS = {
 "element/poison": [NS + ":breath_poison"],
 "element/acid": [NS + ":breath_acid"],
 "element/water": ["#minecraft:is_drowning", NS + ":breath_water"],
}

UI = {
 "block.rpgclasses.race_altar": "Altar de Raza",
 "msg.rpgclasses.rite.none": "No tienes ritos pendientes (sin raza o Rango máximo).",
 "msg.rpgclasses.rite.header": "Rito hacia el Rango %s: %s",
 "msg.rpgclasses.rite.objective": " %s %s: %s/%s",
 "msg.rpgclasses.rite.progress": "%s: %s/%s",
 "msg.rpgclasses.rite.completed": "¡Rito completado: %s! Ahora tienes Rango %s.",
 "msg.rpgclasses.rite.objective_done": "Rito: %s ✔",
 "msg.rpgclasses.rite.offered": "Ofrenda aceptada: %s",
 "msg.rpgclasses.rite.missing": "Falta para la ofrenda: %s",
 "msg.rpgclasses.rite.need_altar_place": "El altar no está en el sitio que pide el rito: %s",
 "msg.rpgclasses.rite.structure_missing": "Construcción incompleta (%s): %s de %s",
 "msg.rpgclasses.rite.structure_done": "Construcción aceptada: %s",
 "msg.rpgclasses.rite.boss_hint": "Agáchate y haz clic derecho en el altar para invocar al jefe de rito.",
 "msg.rpgclasses.rite.boss_summoned": "¡%s acude a tu llamada! Solo cuenta para ti.",
 "msg.rpgclasses.rite.boss_active": "Tu jefe de rito sigue vivo.",
 "msg.rpgclasses.rite.boss_cooldown": "Aún no puedes invocar otro jefe de rito (%s s).",
 "msg.rpgclasses.rite.boss_no_room": "No hay sitio libre junto al altar para el jefe.",
 "msg.rpgclasses.rite.boss_none": "No hay ningún jefe de rito disponible.",
 "msg.rpgclasses.rite.boss_failed": "El jefe de rito ha caído, pero no se cumplió: %s",
 "msg.rpgclasses.rite.boss_gone": "Tu jefe de rito se ha desvanecido.",
 "msg.rpgclasses.rite.boss_name": "Jefe de rito: %s",
 "msg.rpgclasses.rite.not_now": "Este rito no necesita jefe ahora mismo.",
 "msg.rpgclasses.rite.done_mark": "✔",
 "msg.rpgclasses.rite.todo_mark": "•",
 "msg.rpgclasses.rite.hint": "Ritos: clic derecho en el Altar de Raza para ver el rito y entregar ofrendas; agachado + clic para invocar al jefe.",
 "rite.rpgclasses.place.underground": "bajo tierra",
 "rite.rpgclasses.place.nether": "en el Nether",
 "rite.rpgclasses.place.near_lava": "a 4 bloques o menos de lava",
 "rite.rpgclasses.place.underwater": "bajo el agua",
 "rite.rpgclasses.place.forest": "en un bioma de bosque",
 "rite.rpgclasses.place.snowy": "en un bioma nevado o helado",
 "rite.rpgclasses.place.storm": "a cielo abierto durante una tormenta",
 "rite.rpgclasses.place.deep_water": "bajo al menos 3 bloques de agua",
 "rite.rpgclasses.place.high": "a una altura de 150 o más",
 "rite.rpgclasses.fail.source_share": "poco daño con tu rasgo racial",
 "rite.rpgclasses.fail.counts": "no cumpliste las acciones del rito durante el combate",
 "rite.rpgclasses.fail.min_slimes": "quedaban pocos slimes",
 "rite.rpgclasses.fail.night": "no era de noche",
 "rite.rpgclasses.fail.boss_hits": "pocos golpes recibidos del jefe",
 "rite.rpgclasses.fail.projectiles": "pocos proyectiles recibidos",
 "rite.rpgclasses.fail.explosions": "pocas explosiones",
 "rite.rpgclasses.fail.ability": "pocos usos de la habilidad",
 "rite.rpgclasses.fail.thirst": "poca curación con Sed",
 "rite.rpgclasses.fail.mounted": "bajaste del lobo o murió",
 "rite.rpgclasses.fail.allies": "pocos aliados cerca",
 "rite.rpgclasses.fail.snares": "pocos Cepos",
 "rite.rpgclasses.fail.crafted_diamond": "no llevabas tu armadura de diamante",
 "rite.rpgclasses.fail.racial_share": "poco daño con conjuros raciales",
 "rite.rpgclasses.fail.wall_share": "poco daño con la disciplina de tu muro",
 "rite.rpgclasses.fail.damage": "apenas le hiciste daño",
}

def _lang_key(race, sub, n): return f"rite.{NS}.{race}.{sub}.{n}"

def attach(races_json_by_id):
    """Añade "rites" a cada subraza de los JSON de raza (dict id -> objeto) y devuelve las entradas de idioma."""
    lang = dict(UI)
    for (race, sub), rites in RITES.items():
        assert len(rites) == 3, (race, sub)
        data = races_json_by_id[race]
        target = next(s for s in data["subraces"] if s["id"] == sub)
        out = []
        for n, r in enumerate(rites, start=1):
            base = _lang_key(race, sub, n)
            lang[base] = r["_name"]
            lang[base + ".desc"] = r["_desc"]
            objs = []
            for i, o in enumerate(r["objectives"]):
                lang[f"{base}.o{i}"] = o["_t"]
                objs.append({k: v for k, v in o.items() if not k.startswith("_")})
            out.append({"id": f"{race}.{sub}.{n}", "kind": r["kind"], "objectives": objs})
        target["rites"] = out
    for race, data in races_json_by_id.items():
        for s in data["subraces"]:
            assert "rites" in s, (race, s["id"])
    return lang

def write_static(dump, resources):
    """Etiquetas, Altar de Raza (bloque, modelo, receta, botín)."""
    import os
    data = os.path.join(resources, "data")
    for name, values in ENTITY_TAGS.items():
        dump(os.path.join(data, NS, "tags", "entity_types", name + ".json"), {"replace": False, "values": values})
    for name, values in ITEM_TAGS.items():
        dump(os.path.join(data, NS, "tags", "items", name + ".json"), {"replace": False, "values": values})
    for name, values in BLOCK_TAGS.items():
        dump(os.path.join(data, NS, "tags", "blocks", name + ".json"), {"replace": False, "values": values})
    for name, values in DAMAGE_TAGS.items():
        dump(os.path.join(data, NS, "tags", "damage_type", name + ".json"), {"replace": False, "values": values})
    dump(os.path.join(data, "minecraft", "tags", "blocks", "mineable", "pickaxe.json"), {"replace": False, "values": [NS + ":race_altar"]})
    dump(os.path.join(data, NS, "loot_tables", "blocks", "race_altar.json"), {
        "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": NS + ":race_altar"}],
                                               "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    dump(os.path.join(data, NS, "recipes", "race_altar.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["CBC", "GDG", "SSS"],
        "key": {"C": {"item": "minecraft:candle"}, "B": {"item": "minecraft:book"}, "G": {"item": "minecraft:gold_ingot"},
                "D": {"item": "minecraft:diamond"}, "S": {"tag": "minecraft:stone_bricks"}},
        "result": {"item": NS + ":race_altar"}})
    assets = os.path.join(resources, "assets", NS)
    dump(os.path.join(assets, "blockstates", "race_altar.json"), {"variants": {"": {"model": NS + ":block/race_altar"}}})
    dump(os.path.join(assets, "models", "block", "race_altar.json"), {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {"top": "minecraft:block/enchanting_table_top", "bottom": "minecraft:block/polished_blackstone",
                     "side": "minecraft:block/chiseled_polished_blackstone"}})
    dump(os.path.join(assets, "models", "item", "race_altar.json"), {"parent": NS + ":block/race_altar"})
