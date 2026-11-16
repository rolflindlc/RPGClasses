# Razas y subrazas (anexo de razas, rev. 0.5): números -> races/*.json; textos en español.
# Lo importa gen_data.py. Los valores por rango son listas [R0, R1, R2, R3].
NS = "rpgclasses"
MC = "minecraft:generic."
def R(x): return f"{NS}:{x}"
def V(*v):
    assert len(v) == 4
    return {"values": list(v)}
def K(v): return V(v, v, v, v)                       # igual en todos los rangos
def attr(*mods):                                     # (atributo, valor, operación); valor = número o V(...)
    return {"type": R("attribute"), "modifiers": [dict(attribute=a, operation=op, **(v if isinstance(v, dict) else K(v))) for a, v, op in mods]}
def mech(i, min_rank=0, **p):
    e = {"type": R("mechanic"), "id": R("race_" + i)}
    if min_rank: e["min_rank"] = min_rank
    e.update(p)
    return e
CONTROL = ["minecraft:slowness", "minecraft:weakness", "minecraft:blindness", "minecraft:mining_fatigue", "minecraft:nausea", "minecraft:darkness"]

# id: (icono, [rasgos de raza], [(subraza, icono, disponible, [rasgos])])
RACES = {
 "human": ("minecraft:player_head", [mech("adaptable", wall_reduction=V(.10, .20, .35, .50))], [
    ("polyvalent", "minecraft:crafting_table", True, [mech("second_wall", factor=.5)]),
    ("specialist", "minecraft:diamond_sword", True, [mech("wall_mastery", factor=1.5)]),
    ("autodidact", "minecraft:writable_book", True, [mech("flexible_respec"), mech("scholar", 2, xp_bonus=.15)])]),
 "orc": ("minecraft:iron_axe", [
    attr((R("scale"), .10, "add"), (MC + "max_health", .10, "multiply_base")),
    mech("last_stand", invulnerable_seconds=1), mech("hunger", exhaustion=.25)], [
    ("stoneskin", "minecraft:stone", True, [mech("thick_skin", flat_reduction=V(1, 1.25, 1.5, 2))]),
    ("devourer", "minecraft:cooked_beef", True, [
        mech("iron_stomach"), mech("devour", 1, food=2, saturation=1, icd_seconds=1),
        mech("hunger_immune", 2), mech("feast", 3, mob_factor=.5, icd_seconds=15)]),
    ("warlord", "minecraft:goat_horn", True, [
        mech("warcry", radius=V(8, 12, 12, 12), cooldown_seconds=V(60, 60, 60, 40), immunity_seconds=V(0, 0, 4, 4), effects=CONTROL)])]),
 "dwarf": ("minecraft:iron_pickaxe", [
    attr((R("scale"), -.25, "add"), (MC + "movement_speed", -.10, "multiply_base")),
    mech("miner", mining_speed=.25), mech("fortune", level=V(1, 1, 2, 3))], [
    ("deep", "minecraft:deepslate", True, [
        mech("cave_eyes"),
        mech("stone_sense", 1, radius=V(8, 8, 12, 16), duration_seconds=V(5, 5, 8, 8), cooldown_seconds=V(30, 30, 30, 20))]),
    ("smith", "minecraft:anvil", True, [
        mech("forged", durability=V(.20, .20, .40, .40), unbreaking=V(0, 0, 0, 1)),
        mech("anvil_discount", 1, discount=.50), mech("forged_armor", 2, toughness_per_piece=.5)]),
    ("demolisher", "minecraft:tnt", True, [
        mech("blast_proof", tnt_radius=.25), mech("vein_miner", 1, max_blocks=V(8, 8, 16, 16)),
        mech("tunnel", 3, size=3, exhaustion_per_block=.1)])]),
 "elf": ("minecraft:oak_leaves", [
    attr((R("leaf_walker"), 1, "add")), mech("leaf_walker"), mech("no_meat")], [
    ("dark", "minecraft:campfire", True, [
        mech("dark_vision"),
        mech("smoke_cloud", 1, radius=V(8, 8, 10, 16), duration_seconds=V(6, 6, 8, 8), cooldown_seconds=22, slowness=V(0, 0, 0, 1))]),
    ("arcane", "minecraft:enchanting_table", True, [
        mech("arcane", enchant_bonus=V(.10, .20, .30, .30), xp_discount=V(.10, .20, .30, .30), fusion=V(0, 0, 0, 1), fusion_cost_levels=30)]),
    ("sylvan", "minecraft:wheat", True, [
        mech("harvest", bonus=V(.25, .25, .25, .50)), mech("plant_food", 1, bonus=.25),
        mech("green_touch", 2, cooldown_seconds=V(5, 5, 5, 2)), mech("forest_regen", 2, amplifier=0)])]),
 "goblin": ("minecraft:gold_nugget", [
    attr((R("scale"), -.50, "add"), (MC + "max_health", -.25, "multiply_base"), (MC + "luck", 1, "add")),
    mech("looting", level=V(1, 1, 2, 2))], [
    ("wolf_rider", "minecraft:bone", True, [
        mech("wolf_mount", speed_factor=1.0, jump_factor=1.6), mech("wolf_vigor", 1, health=.25),
        mech("wolf_bite", 2, cooldown_seconds=1, reach=3.5), mech("wolf_regen", 3, amplifier=1)]),
    ("thief", "minecraft:bundle", True, [
        mech("long_hand", mob_chance=V(.10, .15, .20, .25), player_chance=V(.05, .07, .10, .12), steals_per_mob=V(1, 1, 2, 2),
             equip_share_mob=V(0, 0, 0, .20), equip_share_player=V(0, 0, 0, .10), player_cooldown_seconds=30, cooldown_seconds=2)]),
    ("trapper", "minecraft:tripwire_hook", True, [
        mech("snare", traps=V(1, 2, 2, 3), cooldown_seconds=V(20, 20, 20, 12), root_seconds=V(3, 3, 5, 5),
             boss_root_seconds=V(1, 1, 2, 2), trap_damage=V(2, 4, 6, 8), pvp_factor=.5)])]),
 "triton": ("minecraft:trident", [
    attr(("forge:swim_speed", .50, "multiply_base")),
    mech("water_breathing"), mech("aquatic", fire_damage=.30), mech("nether_weakness", level=1)], [
    ("fisher", "minecraft:fishing_rod", True, [
        mech("fisher", luck=1, lure_speed=.40, extra_catch=V(0, .25, .25, .50), treasure_chance=V(0, 0, .05, .05), xp_bonus=V(0, 0, 0, 1)), mech("trident_mastery", 2, damage_bonus=.25)]),
    ("shaper", "minecraft:water_bucket", True, [
        mech("tame_water", duration_seconds=30, cooldown_seconds=5),
        mech("wave", 1, cooldown_seconds=V(15, 15, 15, 10), charges=V(1, 1, 1, 2), strength=1.5, range=6),
        mech("water_wall", 2, duration_seconds=8, cooldown_seconds=20)]),
    ("abyssal", "minecraft:prismarine_shard", True, [
        mech("sea_friend"), mech("abyss_immunity", 1), mech("camouflage", 2), mech("escort", 3, radius=16)])]),
 "infernal": ("minecraft:blaze_powder", [
    mech("fire_immune"), mech("cold_frailty", cold_damage=.30, water_damage=.30), mech("water_scald", scald_damage=1, interval_seconds=2),
    mech("racial_spells", fire_bolt=V(1, 2, 2, 2), magma_bomb=V(0, 0, 1, 1), raise_hell=V(0, 0, 0, 1)),
    mech("outcast", price_increase=.50, golem_radius=16)], [
    ("native", "minecraft:gold_ingot", True, [
        mech("nether_native"), mech("wither_immune", 1), mech("barter", 2, extra_chance=.25), mech("nether_compass", 3, cooldown_seconds=600)]),
    ("smelter", "minecraft:blast_furnace", True, [
        mech("lava_walk"), mech("forge_heat", 1, radius=8, furnace_speed=.50), mech("auto_smelt", 2), mech("searing_strikes", 2, burn_seconds=2), mech("portable_furnace", 3, max_items=16, cooldown_seconds=20)]),
    ("pyromaniac", "minecraft:fire_charge", True, [
        mech("piercing_fire", pierce=V(1, 2, 2, 2)),
        mech("flame_pool", 2, duration_seconds=4, radius=2.5, burn_seconds=3),
        mech("lava_pool", 3, duration_seconds=5, radius=2.5, burn_seconds=3, slowness=2)])]),
 "undead": ("minecraft:skeleton_skull", [
    mech("water_breathing"), mech("sunburn", damage_per_second=1), mech("fixed_hunger", food=10),
    mech("dead_regen", heal_per_second=1, pause_seconds=6), mech("lifeless")], [
    ("vampire", "minecraft:redstone", True, [
        mech("thirst", kill_heal=V(1, 1, 2, 2), lifesteal=V(0, .03, .05, .07), cap_per_second=5)]),
    ("skeleton", "minecraft:bone", True, [
        mech("bones", arrow_reduction=V(.10, .20, .30, .30), regen_pause_seconds=V(6, 6, 6, 3))]),
    ("specter", "minecraft:phantom_membrane", True, [
        mech("weightless"),
        mech("ethereal", 1, duration_seconds=V(2, 2, 3, 3), cooldown_seconds=V(60, 60, 60, 40))])]),
}

RACE_TEXT = {  # id: (nombre, descripción)
 "human": ("Humano", "Versátil: alivia las desventajas de su clase. Tras elegir clase escoge un muro, cuya penalización se reduce según el Rango; los rasgos base negativos de la clase se reducen igual. Sin ventajas de entorno."),
 "orc": ("Orco", "Más grande y resistente. Una vez por vida sobrevive a un golpe letal con medio corazón. A cambio consume más hambre y es más fácil de golpear."),
 "dwarf": ("Enano", "Pequeño y lento, pero minero nato: rompe piedra y minerales más rápido y tiene Fortuna racial en minerales (no se suma al encantamiento: cuenta el mayor)."),
 "elf": ("Elfo", "Atraviesa las hojas y trepa por ellas como si fueran andamios (saltar sube, agacharse baja). No puede comer carne ni pescado."),
 "goblin": ("Goblin", "La mitad de alto: muy difícil de golpear y cabe por huecos pequeños. Tiene Suerte y Saqueo racial (cuenta el mayor con el encantamiento). A cambio, mucha menos vida."),
 "triton": ("Tritón", "Respira bajo el agua, nada más rápido, pica sumergido a velocidad normal y ve en la oscuridad bajo el agua. El fuego le hace más daño y en el Nether tiene Debilidad."),
 "infernal": ("Infernal", "Inmune al fuego y la lava, pero el frío y el agua le hacen más daño y meterse en el agua le quema poco a poco. Con Iron's Spells tiene conjuros de fuego raciales siempre disponibles, que sufren el muro de Magia de tu clase como cualquier conjuro. Los golems de hierro lo atacan siempre y los aldeanos le cobran más."),
 "undead": ("No-muerto", "Respira bajo el agua, su hambre está fija y regenera vida constantemente (se corta unos segundos al recibir daño). El sol directo le daña aunque lleve casco, y ni las pociones de curación ni los conjuros le curan."),
}

SUBRACE_TEXT = {  # (raza, subraza): (nombre, descripción)
 ("human", "polyvalent"): ("Polivalente", "Elige un segundo muro, que recibe la mitad de la reducción."),
 ("human", "specialist"): ("Especialista", "La reducción de tu muro elegido es un 50 % mayor (hasta −75 % en Rango 3)."),
 ("human", "autodidact"): ("Autodidacta", "Pierdes menos XP al reiniciar la clase o la subclase: la pérdida se reduce en el porcentaje de tu Rango."),
 ("orc", "stoneskin"): ("Pétreo", "Piel Gruesa: cada golpe que recibes hace menos daño en un valor fijo, sin mínimo."),
 ("orc", "devourer"): ("Devorador", "Comes carne y pescado crudos sin efectos negativos y sacian como cocinados. Con los rangos, matar te alimenta y te da efectos de combate."),
 ("orc", "warlord"): ("Caudillo", "Voz del Caudillo (tecla racial 1): elimina los efectos de control de ti y de los aliados cercanos."),
 ("dwarf", "deep"): ("de las Profundidades", "Ves en la oscuridad bajo tierra y eres inmune a Oscuridad. Sentido de la Piedra (tecla racial 1, Rango 1) resalta minerales y lava cercanos."),
 ("dwarf", "smith"): ("Herrero", "Lo que fabricas o reparas dura más (+20 %, +40 % en Rango 2). Desde Rango 1 el yunque te cuesta la mitad de niveles. En Rango 3 lo que fabricas sale con Irrompibilidad +1 (máximo III)."),
 ("dwarf", "demolisher"): ("Demoledor", "Tu propia TNT no te hace daño y explota con un 25 % más de radio. Desde Rango 1, al romper un mineral rompes también los conectados (agachado no). En Rango 3, agachado, picas piedra en túnel de 3×3 (gasta más hambre)."),
 ("elf", "dark"): ("Oscuro", "Ves en cualquier oscuridad. Nube de Humo (tecla racial 1, Rango 1): tú y tus aliados dentro sois invisibles y los mobs de fuera os pierden de vista. No se revela por cercanía."),
 ("elf", "arcane"): ("Arcano", "La mesa de encantamientos cuenta como si su nivel fuera mayor y encantar (mesa o yunque) te cuesta menos experiencia. En Rango 3, fusiona en el yunque dos encantamientos iguales de nivel máximo para superar ese máximo en 1."),
 ("elf", "sylvan"): ("Silvano", "Tus cultivos dan más cosecha; con los rangos la comida vegetal sacia más y puedes hacer crecer cultivos con la mano vacía."),
 ("goblin", "wolf_rider"): ("Jinete de Lobo", "Clic derecho con la mano vacía en tu lobo para montarlo (agachado: sentarlo, como siempre). Con los rangos: tus lobos tienen más vida, el lobo montado muerde a lo que golpeas y regenera mientras lo montas."),
 ("goblin", "thief"): ("Ladrón", "Mano Larga: al golpear cuerpo a cuerpo puedes robar un objeto del botín de un mob (sin matarlo) o del inventario de un jugador (nunca de la barra rápida ni del kit)."),
 ("goblin", "trapper"): ("Artificiero", "Cepo (tecla racial 1): colocas una trampa que no caduca, solo tú la ves y, al primer enemigo que la pise, le hace daño y lo inmoviliza."),
 ("triton", "fisher"): ("Pescador", "Los peces pican antes y tienes Suerte del Mar racial al pescar; con los rangos, capturas extra, más tesoros y más experiencia."),
 ("triton", "shaper"): ("Moldeador", "Agua Dócil (tecla racial 1): fuente de agua temporal. Ola (tecla racial 2, Rango 1): empuja a los enemigos; 2 cargas en Rango 3. Muro de Agua (agachado + tecla racial 1, Rango 2)."),
 ("triton", "abyssal"): ("Abisal", "Ahogados y guardianes son neutrales contigo salvo que los ataques. Con los rangos: inmunidades bajo el agua, camuflaje y escolta de guardianes."),
 ("infernal", "native"): ("Nativo", "Piglins, brutos y hoglins son neutrales contigo salvo que los ataques. Con los rangos: inmune a Wither, trueques con piglins mejores y Brújula Infernal (tecla racial 1, Rango 3)."),
 ("infernal", "smelter"): ("Fundidor", "Caminas sobre la lava. Con los rangos, los hornos cercanos van más rápido, los minerales que picas salen fundidos y fundes al instante lo que llevas en la mano (tecla racial 1, Rango 3)."),
 ("infernal", "pyromaniac"): ("Pirómano", "Tu Disparo de fuego racial atraviesa enemigos. Con los rangos, tu Bomba de magma racial deja un charco de llamas y, al Rango 3, lava que ralentiza. Requiere Iron's Spells."),
 ("undead", "vampire"): ("Vampiro", "Sed: matar te cura y, con los rangos, robas vida con el daño que haces (con un tope por segundo)."),
 ("undead", "skeleton"): ("Esqueleto", "Los esqueletos son neutrales contigo salvo que los ataques y recibes menos daño de proyectiles. En Rango 3 tu regeneración se corta menos tiempo."),
 ("undead", "specter"): ("Espectro", "No recibes daño de caída y caes despacio (agacharte lo evita). Forma Etérea (tecla racial 1, Rango 1): unos segundos sin poder recibir daño ni atacar; no protege del sol."),
}

MECHANIC_TEXT = {  # id sin "race_": (nombre, descripción corta)
 "adaptable": ("Adaptable", "Reduce el muro elegido y los rasgos base negativos de tu clase."),
 "second_wall": ("Segundo muro", "Un segundo muro recibe parte de la reducción."),
 "wall_mastery": ("Maestría de Muro", "Multiplica la reducción del muro principal."),
 "scholar": ("Erudito", "Desde Rango 2, los enemigos que derrotas dan un 15 % más de experiencia."),
 "forged_armor": ("Armadura Forjada", "Desde Rango 2, cada pieza de armadura fabricada por ti que llevas da +0,5 de dureza."),
 "forest_regen": ("Savia", "Desde Rango 2, en biomas de bosque tienes Regeneración I."),
 "trident_mastery": ("Maestría del Tridente", "Desde Rango 2, el tridente (cuerpo a cuerpo o lanzado) hace un 25 % más de daño."),
 "searing_strikes": ("Golpes Abrasadores", "Desde Rango 2, tus golpes cuerpo a cuerpo prenden al objetivo 2 s."),
 "flexible_respec": ("Reinicio Flexible", "La pérdida de XP al reiniciar se reduce en el porcentaje de Adaptable."),
 "last_stand": ("Última Resistencia", "Sobrevives a un golpe letal con 1 HP, 1 vez por vida. Va después de las habilidades de clase y antes del Tótem."),
 "hunger": ("Apetito de orco", "Consumes más hambre."),
 "thick_skin": ("Piel Gruesa", "Daño restado a cada golpe recibido."),
 "iron_stomach": ("Estómago de Hierro", "Carne y pescado crudos sin efectos negativos; sacian como cocinados."),
 "devour": ("Devorar", "Matar a un enemigo te da hambre y saturación."),
 "hunger_immune": ("Estómago curtido", "Inmune al efecto Hambre."),
 "feast": ("Festín", "Al matar un mob hostil o un jefe ganas al azar Fuerza, Resistencia o Regeneración I tantos segundos como vida máxima tuviera (jefes) o una parte (resto)."),
 "warcry": ("Voz del Caudillo", "Purga Lentitud, Debilidad, Ceguera, Fatiga, Náusea y Oscuridad en el radio."),
 "miner": ("Minero", "Velocidad extra al romper piedra y minerales."),
 "fortune": ("Fortuna racial", "Nivel de Fortuna en minerales (nunca con Toque de Seda)."),
 "blast_proof": ("A prueba de explosiones", "Las explosiones que provocas tú no te hacen daño."),
 "vein_miner": ("Vetas", "Al romper un mineral rompes los del mismo tipo conectados."),
 "cave_eyes": ("Ojos de Cueva", "Visión nocturna sin luz de cielo; inmune a Oscuridad."),
 "stone_sense": ("Sentido de la Piedra", "Resalta minerales y lava cercanos."),
 "leaf_walker": ("Paso entre Hojas", "Atraviesas las hojas y trepas por ellas."),
 "no_meat": ("Dieta élfica", "No puedes comer carne ni pescado."),
 "dark_vision": ("Visión en la oscuridad", "Visión nocturna con poca luz."),
 "smoke_cloud": ("Nube de Humo", "Nube que vuelve invisible todo lo que hay dentro."),
 "arcane": ("Saber Arcano", "Mesa de encantamientos mejorada y Fusión Arcana."),
 "harvest": ("Cosecha Abundante", "Cosecha extra de cultivos maduros."),
 "plant_food": ("Dieta vegetal", "La comida que no es carne ni pescado sacia más."),
 "green_touch": ("Toque Verde", "Clic derecho con la mano vacía: polvo de hueso gratis en un cultivo."),
 "looting": ("Saqueo racial", "Nivel de Saqueo (cuenta el mayor con el encantamiento)."),
 "long_hand": ("Mano Larga", "Probabilidad de robar al golpear cuerpo a cuerpo."),
 "snare": ("Cepo", "Trampa que no caduca: al primer enemigo que la pisa le hace daño y lo inmoviliza. Solo la ves tú."),
 "water_breathing": ("Respiración Acuática", "No consumes aire bajo el agua."),
 "aquatic": ("Criatura del agua", "Picas sumergido a velocidad normal y ves bajo el agua. El fuego te daña más."),
 "nether_weakness": ("Lejos del mar", "En el Nether tienes Debilidad."),
 "cold_frailty": ("Sangre ígnea", "El frío (congelación y conjuros de hielo) y el agua (conjuros de agua de T.O Magic 'n Extras) te hacen más daño."),
 "water_scald": ("Llama que se apaga", "Mientras estás en el agua te quemas poco a poco."),
 "fisher": ("Pesca Experta", "Mejoras al pescar."),
 "tame_water": ("Agua Dócil", "Crea una fuente de agua temporal donde apuntas."),
 "wave": ("Ola", "Empuja a los enemigos que tienes delante, sin daño."),
 "water_wall": ("Muro de Agua", "Muro de agua temporal de 3×3 delante de ti."),
 "sea_friend": ("Amigo del Mar", "Ahogados y guardianes neutrales."),
 "abyss_immunity": ("Hijo del Abismo", "Bajo el agua, inmune a Fatiga de minero, Ceguera y Oscuridad."),
 "camouflage": ("Camuflaje", "Invisible bajo el agua mientras estás quieto."),
 "escort": ("Escolta", "Los guardianes cercanos atacan a tus enemigos."),
 "fire_immune": ("Piel de Brasa", "Inmune al fuego y la lava (no al daño mágico de fuego). La leche no lo quita."),
 "racial_spells": ("Conjuros Raciales", "Nivel de cada conjuro de fuego racial (0 = aún no lo tienes). Requiere Iron's Spells."),
 "outcast": ("Marcado", "Golems de hierro siempre hostiles y aldeanos más caros."),
 "nether_native": ("Nativo del Nether", "Piglins, brutos y hoglins neutrales."),
 "wither_immune": ("Sangre de ceniza", "Inmune a Wither."),
 "nether_compass": ("Brújula Infernal", "Localiza la fortaleza o el bastión más cercano."),
 "lava_walk": ("Caminalava", "Caminas sobre la lava (agacharte te hunde)."),
 "auto_smelt": ("Toque fundente", "Los minerales que picas salen fundidos."),
 "portable_furnace": ("Horno Portátil", "Funde al instante lo que llevas en la mano con combustible del inventario."),
 "sunburn": ("Luz Solar", "El sol directo te daña (ignora la armadura) y anula tu regeneración."),
 "fixed_hunger": ("Hambre Fija", "Tu hambre no sube ni baja y no tienes saturación."),
 "dead_regen": ("Regeneración Muerta", "Regeneras vida constantemente; se corta al recibir daño."),
 "lifeless": ("Cuerpo Sin Vida", "Las pociones de curación y los conjuros de curación no te curan."),
 "thirst": ("Sed", "Curación por matar y robo de vida."),
 "bones": ("Huesos", "Esqueletos neutrales y menos daño de proyectiles."),
 "weightless": ("Ingrávido", "Sin daño de caída y caída lenta."),
 "ethereal": ("Forma Etérea", "No puedes recibir daño ni atacar."),
 "forged": ("Forja Enana", "Lo que fabricas o reparas dura más; en Rango 3 lo que fabricas sale con Irrompibilidad extra."),
 "anvil_discount": ("Mano de Yunque", "El yunque te cuesta menos niveles."),
 "tunnel": ("Túnel", "Agachado, picas piedra en un túnel de 3×3 con más gasto de hambre."),
 "wolf_mount": ("Jinete", "Montas a tus lobos domesticados (clic derecho con la mano vacía)."),
 "wolf_vigor": ("Lobos Fuertes", "Tus lobos tienen más vida."),
 "wolf_bite": ("Mordisco", "El lobo que montas muerde a lo que golpeas."),
 "wolf_regen": ("Vínculo", "El lobo que montas tiene Regeneración."),
 "barter": ("Trato Infernal", "Probabilidad de que un trueque con piglins dé el doble."),
 "forge_heat": ("Calor de Forja", "Los hornos cercanos funcionan más rápido."),
 "piercing_fire": ("Fuego Penetrante", "Tu Disparo de fuego racial atraviesa enemigos."),
 "flame_pool": ("Charco de Llamas", "Tu Bomba de magma racial deja llamas que queman a los enemigos."),
 "lava_pool": ("Charco de Lava", "Tu Bomba de magma racial deja lava que quema y ralentiza a los enemigos."),
}

RACE_PARAMS = {
 "toughness_per_piece": "Dureza por pieza", "amplifier": "Nivel del efecto (0 = I)",
 "wall_reduction": "Reducción del muro y de los rasgos base|%", "factor": "Factor x", "invulnerable_seconds": "Invulnerable después|s", "exhaustion": "Hambre extra|%",
 "flat_reduction": "Daño restado por golpe (HP)", "food": "Hambre (puntos)", "saturation": "Saturación", "mob_factor": "Mobs que no son jefes x",
 "immunity_seconds": "Inmunidad posterior|s", "mining_speed": "Velocidad de minado extra|%", "level": "Nivel", "slowness": "Lentitud a enemigos (nivel)",
 "enchant_bonus": "Nivel extra de la mesa|%", "fusion": "Fusión Arcana|sí", "fusion_cost_levels": "Coste de la fusión (niveles)",
 "bonus": "Extra|%", "mob_chance": "Probabilidad contra mobs|%", "player_chance": "Probabilidad contra jugadores|%",
 "steals_per_mob": "Robos por mob", "equip_share_mob": "Manos Vacías: robos de equipo a mobs|%",
 "equip_share_player": "Manos Vacías: robos de equipo a jugadores|%", "player_cooldown_seconds": "Enfriamiento por jugador|s",
 "traps": "Trampas a la vez", "root_seconds": "Inmovilización|s", "boss_root_seconds": "Inmovilización a jefes|s",
 "trap_damage": "Daño del Cepo|HP", "jump_factor": "Fuerza del salto x", "pvp_factor": "Contra jugadores x", "fire_damage": "Daño de fuego recibido extra|%", "cold_damage": "Daño de frío recibido extra|%",
 "water_damage": "Daño de agua recibido extra|%", "scald_damage": "Daño del agua|HP",
 "luck": "Suerte del Mar racial", "extra_catch": "Red: captura extra|%", "treasure_chance": "Tesoro extra|%", "xp_bonus": "XP de pesca extra|%",
 "strength": "Fuerza del empuje", "range": "Alcance (bloques)", "fire_bolt": "Disparo de fuego (nivel)", "magma_bomb": "Bomba de magma (nivel)",
 "raise_hell": "Infierno que resurge (nivel)", "price_increase": "Precio de los aldeanos|+%", "golem_radius": "Radio de golems (bloques)",
 "max_items": "Objetos por uso", "pause_seconds": "Corte al recibir daño|s", "kill_heal": "Curación por muerte (HP)",
 "lifesteal": "Robo de vida|%", "cap_per_second": "Tope de curación por segundo (HP)", "arrow_reduction": "Menos daño de proyectiles|%",
 "regen_pause_seconds": "Corte de la regeneración|s", "max_blocks": "Bloques conectados",
 "durability": "Durabilidad extra|%", "unbreaking": "Irrompibilidad extra", "discount": "Descuento|%", "tnt_radius": "Radio de tu TNT|+%",
 "size": "Lado del área de rotura (bloques)", "exhaustion_per_block": "Hambre por bloque extra", "xp_discount": "Experiencia de encantar|−%",
 "speed_factor": "Velocidad del lobo x", "reach": "Alcance (bloques)", "amplifier": "Nivel del efecto (0 = I)",
 "lure_speed": "Picos más rápidos|%", "extra_chance": "Probabilidad de doble|%",
 "furnace_speed": "Velocidad de los hornos|+%", "pierce": "Enemigos atravesados", "burn_seconds": "Quemadura|s",
}

RACE_UI = {
 "ui.rpgclasses.race.title_select": "Elige tu raza y subraza",
 "ui.rpgclasses.race.title_view": "Razas",
 "ui.rpgclasses.race.title_wall": "Humano: elige tu muro",
 "ui.rpgclasses.race.button": "Raza",
 "ui.rpgclasses.race.permanent": "La raza y la subraza son permanentes: no se pueden cambiar.",
 "ui.rpgclasses.race.confirm_text": "La raza y la subraza son PERMANENTES. No se pueden cambiar ni reiniciar, tampoco al reiniciar la clase.",
 "ui.rpgclasses.race.traits": "Rasgos de la raza (Rango 0)",
 "ui.rpgclasses.race.by_rank": "Rasgos de la subraza. R0–R3 = valor en cada Rango (los Rangos 1–3 se ganan con ritos).",
 "ui.rpgclasses.race.from_rank": " (desde Rango %s)",
 "ui.rpgclasses.race.unavailable": "Aún no disponible: sus rasgos están en desarrollo.",
 "ui.rpgclasses.race.own": "Tu raza. Subraza: %s. Rango %s.",
 "ui.rpgclasses.race.wall_text": "Adaptable reduce la penalización del muro que elijas. Se elige al elegir clase y solo cambia al reiniciarla.",
 "ui.rpgclasses.race.wall_text_second": "Polivalente: elige un segundo muro, que recibe la mitad de la reducción.",
 "ui.rpgclasses.race.wall_option": "%s: −%s%% → −%s (con 30 puntos)",
 "key.rpgclasses.race_1": "Habilidad racial 1",
 "key.rpgclasses.race_2": "Habilidad racial 2",
 "attribute.rpgclasses.scale": "Tamaño",
 "attribute.rpgclasses.leaf_walker": "Paso entre Hojas",
 "attribute.rpgclasses.body": "Cuerpo de Centauro",
 "msg.rpgclasses.race_none": "%s aún no ha elegido raza.",
 "msg.rpgclasses.race_info": "%s — Raza: %s | Subraza: %s | Rango %s%s",
 "msg.rpgclasses.result.no_race": "Antes de elegir clase debes elegir raza.",
 "msg.rpgclasses.result.already_has_race": "Ya tienes raza: es permanente.",
 "msg.rpgclasses.result.unknown_race": "Esa raza o subraza no existe.",
 "msg.rpgclasses.result.unknown_subrace": "Esa subraza no pertenece a esa raza.",
 "msg.rpgclasses.result.subrace_unavailable": "Esa subraza aún no está disponible.",
 "msg.rpgclasses.result.not_human": "Solo los humanos eligen muro.",
 "msg.rpgclasses.result.wall_invalid": "Ese muro no se puede elegir (tu clase no lo tiene o ya lo elegiste).",
 "msg.rpgclasses.result.wall_slots_full": "Ya has elegido tus muros. Cambian al reiniciar la clase.",
 "message.rpgclasses.breath_no_mana": "No tienes maná para el aliento",
 "effect.rpgclasses.corrosion": "Corrosión",
 "message.rpgclasses.last_stand": "¡Última Resistencia! Sobrevives con medio corazón",
 "message.rpgclasses.elf_no_meat": "Los elfos no comen carne ni pescado",
 "message.rpgclasses.robbed": "¡%s te ha robado un objeto!",
 "message.rpgclasses.stole": "Has robado: %s",
 "message.rpgclasses.no_race_ability": "Tu subraza no tiene habilidad activa (o aún no tienes el Rango)",
 "message.rpgclasses.stone_sense": "Sentido de la Piedra: %s bloques resaltados",
 "message.rpgclasses.compass_not_nether": "La Brújula Infernal solo funciona en el Nether",
 "message.rpgclasses.compass_nothing": "No hay fortaleza ni bastión cerca",
 "message.rpgclasses.compass_found": "Brújula Infernal: estructura en X %s, Z %s (a %s bloques)",
 "message.rpgclasses.furnace_no_fuel": "Horno Portátil: no tienes combustible suficiente",
 "tooltip.rpgclasses.forged": "Forja Enana: +%s%% de durabilidad",
 "death.attack.rpgclasses.sunlight": "%1$s se consumió bajo el sol",
 "death.attack.rpgclasses.scald": "%1$s se apagó en el agua",
}

def build(dump, base):
    """Escribe races/*.json y devuelve las entradas de idioma."""
    import os
    import rites
    lang = {}
    objs = {rid: {"schema_version": 1, "display": {"icon": icon}, "effects": effects,
                  "subraces": [{"id": sid, "display": {"icon": sicon}, "available": ok, "effects": seffects}
                               for sid, sicon, ok, seffects in subraces]}
            for rid, (icon, effects, subraces) in RACES.items()}
    lang.update(rites.attach(objs))
    for rid, (icon, effects, subraces) in RACES.items():
        dump(os.path.join(base, "races", rid + ".json"), objs[rid])
        assert 2 <= len(subraces) <= 6, rid  # adenda 0.7, regla 3
        name, desc = RACE_TEXT[rid]
        lang[f"race.{NS}.{rid}.name"] = name
        lang[f"race.{NS}.{rid}.desc"] = desc
        for sid, _, _, seffects in subraces:
            sname, sdesc = SUBRACE_TEXT[(rid, sid)]
            lang[f"subrace.{NS}.{rid}.{sid}.name"] = sname
            lang[f"subrace.{NS}.{rid}.{sid}.desc"] = sdesc
        for e in effects + [x for _, _, _, se in subraces for x in se]:
            if e["type"] == R("mechanic"):
                key = e["id"].split(":")[1][len("race_"):]
                assert key in MECHANIC_TEXT, key
                for p in e:
                    assert p in ("type", "id", "min_rank", "effects") or p in RACE_PARAMS or p in EXISTING_PARAMS, (key, p)
    for mid, (name, desc) in MECHANIC_TEXT.items():
        lang[f"mechanic.{NS}.race_{mid}.name"] = name
        lang[f"mechanic.{NS}.race_{mid}.desc"] = desc
    lang.update(RACE_UI)
    return lang

EXISTING_PARAMS = set()

# ======================================================================================================
# Adenda rev. 0.7: Slime, Coloso, Dracónido y Centauro. Las subrazas se marcan disponibles cuando su fase
# está programada (R3b/R3c); mientras tanto se ven en la pantalla como "Aún no disponible".
# ======================================================================================================
FS = "forge:"
def resist(element, fire=False):
    return mech("element_resist", element=element, reduction=V(.20, .30, .40, .50) if fire else V(.30, .45, .60, .75), cap=.80)
RACES.update({
 "slime": ("minecraft:slime_block", [
    attr((MC + "max_health", -.15, "multiply_base"), (R("jump_bonus"), 1.0, "add")),
    mech("bouncy", bounce=.8, bounce_min_fall=3), mech("soft_body", knockback_taken=.50)], [
    ("swarm", "minecraft:slime_ball", True, [
        mech("swarm", slimes_per_heart=V(1, 1, 1, 2), duration_seconds=V(10, 15, 20, 30), cooldown_seconds=V(60, 50, 40, 30),
             touch_damage=V(1, 1.5, 1.5, 1), slime_speed=V(0, 0, .25, .25), max_slimes=25, leash=32)]),
    ("spring", "minecraft:piston", True, [
        mech("spring", charge_seconds=V(3, 5, 6.5, 8), power=V(1, 1.5, 2, 3), cooldown_seconds=V(10, 8, 6, 5),
             min_charge_seconds=.5, wall_bounce=1.05)]),
    ("mitotic", "minecraft:magma_cream", True, [
        attr((MC + "max_health", -.15, "multiply_base")),
        mech("mitosis", merge_health=V(0, .20, .30, .50), cooldown_seconds=V(75, 60, 45, 30), merge_seconds=V(3, 3, 2, 2))]),
    ("viscous", "minecraft:honey_block", True, [
        mech("sticky", grip_seconds=V(4, 6, 8, 12), release_damage=6), mech("honey_slide", slide_speed=.05),
        mech("slowing_hits", amplifier=V(0, 0, 1, 1), slow_seconds=V(2, 3, 3, 4))])]),
 "colossus": ("minecraft:iron_block", [
    attr((R("scale"), 1.0, "add"), (MC + "max_health", 1.0, "multiply_base"), (R("jump_bonus"), 1.0, "add"),
         (R("mob_detection"), .50, "add"), (FS + "step_height_addition", .6, "add"),
         (FS + "block_reach", 1.5, "add"), (FS + "entity_reach", 1.5, "add")),
    mech("hunger", exhaustion=.50), mech("area_break", size=2), mech("heavy"), mech("trample"), mech("no_mount"),
    mech("giant_fall", fall_height=6)], [
    ("porter", "minecraft:chest", True, [
        attr((MC + "movement_speed", -.10, "multiply_base")),
        mech("belt", slots=V(3, 4, 5, 6), passengers=V(1, 1, 2, 2), passenger_penalty=V(.05, .05, .05, 0))]),
    ("titan", "minecraft:obsidian", True, [
        mech("grow", duration_seconds=V(30, 42, 51, 60), cooldown_seconds=V(135, 120, 105, 90), absorption=V(.50, .70, .85, 1.0),
             shake_radius=24, size=3, fall_height=12)])]),
 "draconid": ("minecraft:dragon_head", [
    mech("hunger", exhaustion=.25),
    mech("breath", range=V(4, 5, 6, 8), damage_per_second=V(3, 4, 5, 6.5), channel_seconds=V(2, 3, 4, 5), cooldown_seconds=V(12, 10, 10, 8),
         mana_per_second=10)], [
    ("fire", "minecraft:blaze_powder", True, [resist("fire", True), mech("dragon_kin", element="fire", radius=32),
        mech("breath_fire", burn_seconds=3)]),
    ("ice", "minecraft:blue_ice", True, [resist("ice"), mech("dragon_kin", element="ice", radius=32),
        mech("breath_ice", amplifier=V(0, 0, 1, 1), slow_seconds=3)]),
    ("lightning", "minecraft:lightning_rod", True, [resist("lightning"), mech("dragon_kin", element="lightning", radius=32),
        mech("breath_lightning", chains=V(0, 1, 1, 2), chain_factor=.5)]),
    ("poison", "minecraft:spider_eye", True, [resist("poison"),
        mech("breath_poison", amplifier=V(0, 0, 1, 1), venom_seconds=V(4, 5, 6, 8)), mech("poison_immune", 2)]),
    ("acid", "minecraft:fermented_spider_eye", True, [resist("acid"),
        mech("breath_acid", corrosion=V(.10, .15, .20, .30), corrosion_seconds=5), mech("acid_stomach")]),
    ("water", "minecraft:heart_of_the_sea", True, [resist("water"), attr(("forge:swim_speed", V(0, .15, .15, .25), "multiply_base")),
        mech("breath_water", breath_push=1.0), mech("deep_lungs", air_factor=3), mech("water_breathing", 2)])]),
 "centaur": ("minecraft:saddle", [
    attr((R("body"), 1, "add")),
    mech("centaur", max_speed=V(.40, .50, .60, .75), accel_seconds=V(5, 4.3, 3.7, 3), ram_damage=V(6, 8, 10, 13),
         ram_knockback=V(4, 5, 6, 7), ram_threshold=.40, ram_speed_loss=.75, jump_charge_seconds=V(1, .9, .75, .6),
         passengers=V(1, 1, 2, 2), passenger_penalty=.10, speed_limit=3.0, fall_height=6),
    mech("no_mount")], [
    ("pegasus", "minecraft:feather", True, [
        mech("glide", min_fall=V(8, 6, 4, 3), air_control=V(0, 0, 1, 1), flap_cooldown_seconds=V(0, 0, 0, 20))]),
    ("unicorn", "minecraft:amethyst_shard", True, [
        mech("mana_aura", radius=V(8, 10, 12, 16), mana_regen=.50), mech("blessed", healing=.50),
        mech("horn", ram_bonus=V(.25, .33, .40, .50))]),
    ("warrior", "minecraft:iron_horse_armor", True, [
        mech("war_gallop", damage_bonus=V(.20, .30, .40, .50), ram_speed_loss=V(.60, .50, .40, .30))])]),
})

RACE_TEXT.update({
 "slime": ("Slime", "No recibe daño de caída, rebota al caer sobre cualquier bloque (agacharse lo evita) y salta el doble de alto. A cambio, menos vida y sale despedido más lejos con los golpes."),
 "colossus": ("Coloso", "El doble de grande y el doble de vida (sin tope). Rompe bloques en un cubo de 2×2×2 (agachado, de uno en uno) y su alcance, paso, salto y caída segura crecen con su tamaño. A cambio: más hambre, no nada (se hunde), los mobs lo detectan desde más lejos, pisa los cultivos y no puede montar animales ni barcas."),
 "draconid": ("Dracónido", "Descendiente de dragones: resiste el daño de su elemento y escupe un aliento de ese elemento (tecla racial 1), que gasta maná y sufre el muro de Magia de tu clase. Los dragones salvajes de su elemento (Ice and Fire) no lo atacan si no los provoca. Consume más hambre."),
 "centaur": ("Centauro", "Cuerpo de caballo: acelera poco a poco al correr en línea recta y, por encima del 40 % de su velocidad máxima, embiste a lo que choca (pierde velocidad al hacerlo). Salto cargado como el del caballo (mantén saltar) y un aliado puede montarse encima. No cabe por huecos de 1 bloque y no monta animales ni barcas."),
})

SUBRACE_TEXT.update({
 ("slime", "swarm"): ("Enjambre", "Tecla racial 1: te divides en un slime pequeño por corazón (máx. 25) que solo hacen daño por contacto. Cada golpe mata a un slime sin que el daño sobrante pase a otro. Al curarte dividido, aparecen slimes nuevos. Vuelve a pulsar para unirte."),
 ("slime", "spring"): ("Resorte", "Agachado, cargas un salto; al saltar sales disparado hacia donde miras (como con Impulso), sin daño de impacto y rebotando sin parar contra suelo, techo y paredes mientras dura la carga (agacharse lo corta)."),
 ("slime", "mitotic"): ("Mitótico", "Aún menos vida, pero al recibir un golpe letal de un enemigo te divides en dos copias de la mitad de tamaño. Agachado junto a tu copia os fusionáis."),
 ("slime", "viscous"): ("Viscoso", "Agachado contra una pared o una entidad te quedas pegado. Te deslizas despacio por las paredes como por la miel y tus golpes dan Lentitud."),
 ("colossus", "porter"): ("Porteador", "Llevas un cinturón de cofres, hornos y mesas que usas mientras caminas (tecla racial 1; tus aliados lo abren agachados con clic derecho sobre ti), y tus aliados pueden montarse encima de ti. Algo más lento, y más con pasajeros."),
 ("colossus", "titan"): ("Titán", "Crecer (tecla racial 1): duplicas otra vez tu tamaño con una segunda barra de vida, rompes en 3×3×3 y la tierra tiembla a cada paso."),
 ("draconid", "fire"): ("de Fuego", "Resistes el fuego y tu aliento prende a los enemigos. Los dragones de fuego salvajes no te atacan si no los provocas."),
 ("draconid", "ice"): ("de Hielo", "Resistes el hielo y la congelación y tu aliento ralentiza. Los dragones de hielo salvajes no te atacan si no los provocas."),
 ("draconid", "lightning"): ("Eléctrico", "Resistes el rayo y el rayo de tu aliento salta a enemigos cercanos. Los dragones eléctricos salvajes no te atacan si no los provocas."),
 ("draconid", "poison"): ("Venenoso", "Resistes el daño tóxico (inmune a Veneno desde Rango 2) y tu aliento envenena."),
 ("draconid", "acid"): ("Ácido", "Resistes el ácido, la comida no te sienta mal y tu aliento corroe la armadura del objetivo."),
 ("draconid", "water"): ("de Agua", "Resistes el agua y el ahogamiento, aguantas el triple de aire (respiras bajo el agua desde Rango 2) y tu aliento empuja y apaga el fuego."),
 ("centaur", "pegaso" if False else "pegasus"): ("Pegaso", "Al caer desde lo alto planeas despacio y sin daño; con los rangos, desde menos altura, con control en el aire y un aleteo."),
 ("centaur", "unicorn"): ("Unicornio", "Tus aliados cercanos regeneran maná más rápido, recibes más curación de pociones y conjuros y tu cuerno embiste más fuerte."),
 ("centaur", "warrior"): ("Guerrero", "Con al menos el 40 % de tu velocidad máxima atacas sin frenarte (Epic Fight) y haces más daño cuerpo a cuerpo cuanto más rápido vas. Embestir te frena menos."),
})

MECHANIC_TEXT.update({
 "bouncy": ("Cuerpo Elástico", "Sin daño de caída; rebotas al caer al menos 3 bloques y pierdes altura en cada rebote (agacharse lo evita)."),
 "soft_body": ("Cuerpo Blando", "Recibes más retroceso de los golpes."),
 "swarm": ("Enjambre", "Te divides en slimes pequeños que te siguen; si mueren todos, mueres."),
 "spring": ("Salto Resorte", "Salto cargado agachado que te dispara hacia donde miras."),
 "mitosis": ("Mitosis", "Al recibir un golpe letal de un enemigo te divides en dos copias en vez de morir."),
 "sticky": ("Adherencia", "Agachado, te pegas a paredes y entidades mientras dure la barra."),
 "honey_slide": ("Pared de Miel", "En el aire, te deslizas despacio por las paredes."),
 "slowing_hits": ("Golpe Viscoso", "Tus golpes cuerpo a cuerpo dan Lentitud (menos a los jefes)."),
 "area_break": ("Manos de Gigante", "Rompes bloques en un cubo (agachado, de uno en uno). Nunca contenedores."),
 "heavy": ("Peso de Piedra", "No puedes nadar: te hundes y caminas por el fondo."),
 "trample": ("Pisotón", "Rompes los cultivos al pisarlos (agachado no)."),
 "no_mount": ("Sin Montura", "No puedes montar animales ni barcas."),
 "giant_fall": ("Zancada", "Aguantas caídas más altas sin daño."),
 "belt": ("Cinturón", "Bloques funcionales que llevas encima y pasajeros aliados."),
 "grow": ("Crecer", "Duplicas tu tamaño con una segunda barra de vida."),
 "breath": ("Aliento", "Cono canalizado de tu elemento (tecla racial 1). Gasta maná y sufre el muro de Magia."),
 "element_resist": ("Escamas", "Menos daño de tu elemento."),
 "dragon_kin": ("Sangre de Dragón", "Los dragones salvajes de tu elemento no te atacan si no los provocas."),
 "breath_fire": ("Aliento de Fuego", "Tu aliento prende al objetivo."),
 "breath_ice": ("Aliento de Hielo", "Tu aliento ralentiza."),
 "breath_lightning": ("Aliento de Tormenta", "El rayo de tu aliento salta a enemigos cercanos."),
 "breath_poison": ("Aliento Tóxico", "Tu aliento envenena (a los no-muertos, Lentitud)."),
 "poison_immune": ("Sangre Tóxica", "Inmune al efecto Veneno."),
 "breath_acid": ("Aliento Ácido", "Tu aliento aplica Corrosión: menos armadura y dureza."),
 "acid_stomach": ("Estómago Ácido", "Inmune al efecto Hambre y a los efectos negativos de la comida."),
 "breath_water": ("Aliento de Agua", "Tu aliento empuja y apaga el fuego."),
 "deep_lungs": ("Branquias", "Tu aire dura más."),
 "centaur": ("Cuerpo de Caballo", "Aceleración, embestida, salto cargado y pasajeros aliados."),
 "glide": ("Alas", "Al caer desde lo alto planeas sin daño."),
 "mana_aura": ("Aura de Maná", "Los aliados cercanos regeneran maná más rápido (a ti no te afecta)."),
 "blessed": ("Bendecido", "Recibes más curación de pociones y conjuros."),
 "horn": ("Cuerno", "Tu embestida hace más daño."),
 "war_gallop": ("Galope de Guerra", "Daño cuerpo a cuerpo según tu velocidad; embestir te frena menos."),
})

# Fase R3c: textos nuevos de las habilidades de las subrazas complejas.
RACE_UI.update({
 "message.rpgclasses.titan_no_room": "No hay sitio para crecer: hace falta espacio libre del doble de tu tamaño",
 "container.rpgclasses.belt": "Cinturón de %s",
 "message.rpgclasses.mitosis_split": "¡Mitosis! Te divides en dos copias: agáchate junto a la otra para fusionaros",
 "message.rpgclasses.mitosis_merged": "Te fusionas con tu copia",
 "message.rpgclasses.mitosis_copy_lost": "Has perdido una copia: recuperarás tu tamaño al acabar el enfriamiento de la división",
 "entity.rpgclasses.swarm_slime": "Slime del Enjambre",
 "entity.rpgclasses.mitosis_copy": "Copia mitótica",
 "ui.rpgclasses.belt.open": "Abrir",
 "ui.rpgclasses.belt.capacity": "Huecos: %s/%s",
})

RACE_PARAMS.update({
 "bounce": "Rebote x", "mana_per_second": "Maná por segundo", "knockback_taken": "Retroceso recibido|+%", "slimes_per_heart": "Slimes por corazón",
 "touch_damage": "Daño por contacto|HP", "slime_speed": "Velocidad de los slimes|+%", "max_slimes": "Slimes máximos",
 "leash": "Distancia máxima (bloques)", "charge_seconds": "Carga máxima|s", "power": "Potencia (Impulso)",
 "min_charge_seconds": "Carga mínima|s", "wall_bounce": "Rebote en pared y suelo x", "bounce_min_fall": "Caída mínima para rebotar (bloques)", "merge_health": "Vida tras fusionar|%",
 "merge_seconds": "Tiempo de fusión|s", "grip_seconds": "Adherencia|s", "release_damage": "Te sueltas con un golpe de (HP)",
 "slide_speed": "Deslizamiento (bloques/tick)", "fall_height": "Caída sin daño (bloques)",
 "slots": "Huecos del cinturón", "passengers": "Pasajeros", "speed_penalty": "Velocidad|−%", "passenger_penalty": "Velocidad por pasajero|−%",
 "absorption": "Segunda barra de vida|%", "shake_radius": "Radio del temblor (bloques)",
 "channel_seconds": "Canalización máxima|s", "element": "Elemento",
 "chains": "Saltos del rayo", "chain_factor": "Daño del salto|%",
 "venom_seconds": "Veneno|s", "corrosion": "Corrosión|−%", "corrosion_seconds": "Corrosión|s", "breath_push": "Empuje del aliento",
 "air_factor": "Aire x", "max_speed": "Velocidad máxima racial|+%", "accel_seconds": "Tiempo hasta el máximo|s",
 "ram_damage": "Embestida máxima|HP", "ram_knockback": "Empuje de la embestida (bloques)", "ram_threshold": "Embestida desde|%",
 "ram_speed_loss": "Velocidad perdida al embestir|%", "jump_charge_seconds": "Carga del salto|s", "speed_limit": "Límite técnico de velocidad x",
 "min_fall": "Planeas desde (bloques)", "air_control": "Control en el aire|sí", "flap_cooldown_seconds": "Aleteo: enfriamiento|s",
 "mana_regen": "Regeneración de maná de aliados|+%", "healing": "Curación recibida|+%", "ram_bonus": "Daño de embestida|+%",
})
