# Druida (adenda del Druida, 22-11-2026): clase, ramas, perks, subclases, habilidades, bestias, objetos, recetas,
# tipos de daño, tags y textos. Lo importa gen_data.py. Los ids deben coincidir con io.github.rpgclasses.druid (Druid.java).
# Las propuestas de la adenda sin confirmar se programan tal cual y quedan aquí, en datos. Todo es [MEDIR].
import json
import os

NS = "rites_and_paths"
IR = "irons_spellbooks:"
MC = "minecraft:generic."


def R(x): return f"{NS}:{x}"
def to(v, op="add"): return {"to": v, "operation": op}
def attr(mods): return {"type": R("attribute"), "modifiers": [dict(attribute=a, **v) for a, v in mods]}
def mech(i, **p): return {"type": R("mechanic"), "id": R(i), **p}
def ms(level, grants, sub=False): return {("subclass_level" if sub else "total_level"): level, "grants": R(grants)}
def item(i): return {"item": i}
def tag(t): return {"tag": t}


CLASS_ID = "druid"
NATURE = IR + "nature"

# ------------------------------------------------------------------ clase
CLASS = dict(icon="minecraft:oak_sapling", roles=["magia", "transformación"], kit="druid", crit=0.0,
             # Adenda, 1: Magia −35 % (sin la naturaleza), Arco −50 %, Pesadas −60 %, Ligeras −40 %, Armadura −30 %,
             # Escudos −70 %. Total 285.
             walls=dict(magic=.35, bow=.50, heavy=.60, light=.40, armor=.30, shield=.70),
             branches=[
                 # Savia: +5 % de poder de la escuela de naturaleza por punto (+50 % con 10).
                 ("sap", [attr([(IR + "nature_spell_power", to(.50, "multiply_base"))])]),
                 # Fiera: +3 % de vida y de daño de las formas por punto (+30 % con 10).
                 ("ferocity", [mech("druid_ferocity", form_health={"to": .30}, form_damage={"to": .30})]),
                 # Reserva Salvaje: −3 % de maná al transformarte y +15 de maná máximo por punto.
                 ("wild_reserve", [mech("druid_wild_reserve", cost={"to": -.30}), attr([(IR + "max_mana", to(150))])]),
             ],
             milestones=[ms(1, "wild_shape"), ms(1, "beast_lore"), ms(5, "track_prey"), ms(15, "dominate_beast"),
                         ms(30, "entangling_vines")],
             subs=["moon_circle", "supernatural_form", "sculptor"])

EXTRA_BRANCHES = [
    # Saber: −5 % de tiempo de estudio y +1 s de margen del ritual por punto; con la rama llena, −1 punto de
    # conocimiento necesario por especie (mínimo 1).
    ("lore", [mech("druid_lore", study_time={"to": -.50}, ritual_margin={"to": 10},
                   points_reduction={"values": [0, 0, 0, 0, 0, 0, 0, 0, 0, 1]})]),
    # Raíz: regeneración en forma (0,05 de vida por segundo por punto) y curación al salir de la forma (1 % de tu vida
    # máxima por punto).
    ("roots", [mech("druid_roots", form_regen={"to": .50}, exit_heal={"to": .10})]),
]

# Rasgo base (adenda, 1): −10 % de vida máxima (18 HP).
TRAITS = [attr([(MC + "max_health", to(-.10, "multiply_base"))])]

PERKS = {
    "sap": [("venom_sap", [mech("druid_venom_sap", poison_seconds=5, poison_level=1),
                           attr([(IR + "nature_spell_power", to(-.10, "multiply_base"))])]),
            ("vital_sap", [mech("druid_vital_sap", lifesteal=.15),
                           attr([(IR + "nature_spell_power", to(-.10, "multiply_base"))])])],
    "ferocity": [("hunter", [mech("druid_hunter", form_damage=.25, form_health=-.15)]),
                 ("colossus", [mech("druid_colossus", form_health=.25, form_damage=-.15)])],
    "wild_reserve": [("cheap_form", [mech("druid_cheap_form", cost=-.40, max_seconds=180)]),
                     ("rich_form", [mech("druid_rich_form", cost=.25, stats=.20)])],
    "lore": [("scholar", [mech("druid_scholar", study_points=2, study_time=.50)]),
             ("scavenger", [mech("druid_scavenger", ritual_points=2, ritual_mana=1.0, ritual_margin=-10)])],
    "roots": [("body_sap", [mech("druid_body_sap", form_regen=.50, form_health=-.10)]),
              ("healing_exit", [mech("druid_healing_exit", exit_heal=.25)])],
}

BRANCH_ICONS = {"sap": "minecraft:spore_blossom", "ferocity": "minecraft:bone", "wild_reserve": "minecraft:lapis_lazuli",
                "lore": "minecraft:writable_book", "roots": "minecraft:hanging_roots"}

SUBS = {
    # Círculo de la Luna: transformación en 1 s, recarga de 5 s y sin maná reservado. Sin contrapeso (decisión del usuario).
    "moon_circle": ("minecraft:sea_lantern", [mech("druid_moon", cast_seconds=1, exit_cooldown_seconds=5)], []),
    # Forma Sobrenatural: conjuros de naturaleza en forma; las formas −15 % y esos conjuros +25 % de maná.
    "supernatural_form": ("minecraft:glow_berries", [mech("druid_supernatural", stats=-.15, spell_cost=.25)], []),
    # Escultor: quimeras con partes de criaturas (rituales oscuros).
    "sculptor": ("minecraft:rotten_flesh", [mech("druid_sculptor", chimera_mana=120, cinematic_seconds=4, health_penalty=.30,
                                                 penalty_seconds=600, max_chimeras=3)], []),
}

KIT = [{"item": R("study_book"), "count": 4, "slot": "inventory"},
       {"item": IR + "copper_spell_book", "slot": "inventory", "spells": [{"id": IR + "poison_splash", "level": 1}]}] + \
      [{"item": f"minecraft:leather_{p}", "slot": s} for p, s in
       (("helmet", "head"), ("chestplate", "chest"), ("leggings", "legs"), ("boots", "feet"))]

ABILITIES = {
    # Nivel Total 1. El enfriamiento empieza al salir de la forma (no al pulsar la tecla).
    "wild_shape": dict(cast_seconds=6, exit_cooldown_seconds=30, level_scaling=.02, mana_base=15, mana_per_power=1.5,
                       damage_weight=3, flying_cost=.25, aquatic_cost=.25, cast_slow=-.50, form_attack_speed=1.6,
                       aquatic_land_speed=.35, form_swim_bonus=1.5, flight_seconds=10, flight_recharge=.1, flight_ceiling=40,
                       flight_speed=.03, form_min_speed=.5, form_max_speed=1.6),
    "beast_lore": dict(passive=True, study_seconds=5, ritual_mana=35, ritual_cast_seconds=2, ritual_margin_seconds=20,
                       ritual_range=8, power_per_point=20, max_points=6, max_health=100, remains_radius=48),
    # Nivel Total 5.
    "track_prey": dict(radius=48, duration_seconds=10, cooldown_seconds=30, fixed_radius=96),
    # Nivel Total 15.
    "dominate_beast": dict(range=12, duration_seconds=180, cooldown_seconds=90, follow_distance=6, teleport_distance=24),
    # Nivel Total 30. Daño al moverse dentro, como las bayas dulces (un poco más).
    "entangling_vines": dict(radius=5, duration_seconds=20, heal_per_second=.5, slow_level=1, damage=1.5,
                             damage_interval_seconds=.5, cooldown_seconds=70),
}

# ------------------------------------------------------------------ bestias
# Tabla de bestias (sobrescribe la fórmula; lo que no esté aquí se calcula de los atributos del mob). Campos opcionales:
# health, damage, armor, speed (x de la velocidad a pie del jugador), attack_speed (golpes por segundo), jump (salto
# extra), knockback_resistance, points, mana, flying, aquatic, amphibious, ability, passives.
BEASTS = {
    "minecraft:wolf": dict(health=20, damage=4, speed=1.35, ability="pounce"),
    "minecraft:fox": dict(health=10, damage=3, speed=1.3, ability="leap", passives=["night_vision"]),
    "minecraft:cat": dict(health=10, damage=3, speed=1.3, jump=.5, passives=["no_fall", "scare_creepers"]),
    "minecraft:ocelot": dict(health=10, damage=3, speed=1.35, jump=.5, passives=["no_fall", "scare_creepers"]),
    "minecraft:rabbit": dict(health=3, damage=1, speed=1.3, jump=1.0, ability="super_jump"),
    "minecraft:spider": dict(health=16, damage=3, speed=1.35, ability="web", passives=["climb", "night_vision"]),
    "minecraft:cave_spider": dict(health=12, damage=2, speed=1.35, passives=["climb", "poison_bite"]),
    "minecraft:polar_bear": dict(health=30, damage=6, speed=1.15, attack_speed=1.2, ability="roar"),
    "minecraft:goat": dict(health=10, damage=2, speed=1.1, jump=.8, ability="ram"),
    "minecraft:hoglin": dict(health=40, damage=6, speed=1.0, attack_speed=1.0, knockback_resistance=.6, ability="toss"),
    "minecraft:ravager": dict(health=100, damage=12, speed=1.2, attack_speed=.8, knockback_resistance=.75,
                              ability="stun_roar", passives=["leaf_breaker"]),
    "minecraft:horse": dict(health=22, damage=2, speed=1.6, jump=1.0),
    "minecraft:donkey": dict(health=22, damage=2, speed=1.4, jump=.8),
    "minecraft:mule": dict(health=22, damage=2, speed=1.4, jump=.8),
    "minecraft:skeleton_horse": dict(health=15, damage=2, speed=1.6, jump=1.0, amphibious=True),
    "minecraft:camel": dict(health=32, damage=2, speed=1.2, jump=.6, ability="dash"),
    "minecraft:llama": dict(health=22, damage=1, speed=1.0, ability="spit"),
    "minecraft:trader_llama": dict(health=22, damage=1, speed=1.0, ability="spit"),
    "minecraft:bee": dict(health=10, damage=2, speed=1.1, ability="sting", flying=True),
    "minecraft:parrot": dict(health=6, damage=1, speed=1.0, flying=True),
    "minecraft:bat": dict(health=6, damage=1, speed=1.0, ability="echolocation", flying=True, passives=["night_vision"]),
    "minecraft:dolphin": dict(health=10, damage=3, speed=1.0, ability="water_dash", aquatic=True, passives=["dolphin_grace"]),
    "minecraft:turtle": dict(health=30, damage=1, speed=.5, ability="shell", amphibious=True),
    "minecraft:axolotl": dict(health=14, damage=2, speed=.8, ability="play_dead", amphibious=True),
    "minecraft:frog": dict(health=10, damage=2, speed=1.0, jump=.8, ability="tongue", amphibious=True),
    "minecraft:squid": dict(health=10, damage=1, ability="ink", aquatic=True),
    "minecraft:glow_squid": dict(health=10, damage=1, ability="ink", aquatic=True, passives=["night_vision"]),
    "minecraft:pufferfish": dict(health=3, damage=1, ability="inflate", aquatic=True),
    "minecraft:cod": dict(health=3, damage=1, aquatic=True),
    "minecraft:salmon": dict(health=3, damage=1, aquatic=True),
    "minecraft:tropical_fish": dict(health=3, damage=1, aquatic=True),
    "minecraft:strider": dict(health=20, damage=1, speed=1.0, passives=["lava_walk", "fire_immune"]),
    "minecraft:chicken": dict(health=4, damage=1, speed=.9, passives=["slow_fall"]),
    "minecraft:panda": dict(health=20, damage=6, speed=.7, attack_speed=1.0),
    "minecraft:sniffer": dict(health=14, damage=1, speed=.6),
    "minecraft:cow": dict(damage=1, speed=.8),
    "minecraft:mooshroom": dict(damage=1, speed=.8),
    "minecraft:pig": dict(damage=1, speed=1.0),
    "minecraft:sheep": dict(damage=1, speed=.9),
}

# Habilidades especiales de bestia (tecla de habilidad de bestia). Los números son [MEDIR].
BEAST_ABILITIES = {
    "pounce": dict(cooldown_seconds=6, distance=5, lift=.4, damage_bonus=.5, window_seconds=3),
    "leap": dict(cooldown_seconds=8, range=12, lift=.9, stun_seconds=.5),
    "super_jump": dict(cooldown_seconds=5, lift=1.1, forward=.6),
    "web": dict(cooldown_seconds=10, range=16, duration_seconds=5),
    "roar": dict(cooldown_seconds=15, radius=5, weakness_seconds=5, knockback=1.0),
    "ram": dict(cooldown_seconds=10, distance=6, damage=4, knockback=2.0),
    "toss": dict(cooldown_seconds=8, range=3, damage=4, lift=1.0),
    "stun_roar": dict(cooldown_seconds=20, radius=4, stun_seconds=1, boss_factor=.5),
    "dash": dict(cooldown_seconds=8, distance=8),
    "spit": dict(cooldown_seconds=3, damage=3, speed=1.5),
    "sting": dict(cooldown_seconds=6, range=3, damage=2, poison_seconds=5, poison_level=2),
    "echolocation": dict(cooldown_seconds=15, radius=16, duration_seconds=5),
    "water_dash": dict(cooldown_seconds=4, distance=8),
    "shell": dict(cooldown_seconds=12, duration_seconds=4, resistance_level=2, slow_level=2),
    "play_dead": dict(cooldown_seconds=20, duration_seconds=5, regen_level=2),
    "tongue": dict(cooldown_seconds=8, range=6, max_width=1.0, damage=2),
    "ink": dict(cooldown_seconds=10, radius=4, blind_seconds=3),
    "inflate": dict(cooldown_seconds=12, duration_seconds=4, radius=1.5, poison_seconds=4),
}

# Bestias de otros grupos que valen (las automáticas son las de categoría de animal: CREATURE, AMBIENT, WATER_*,
# AXOLOTLS) y las que nunca valen (además de los jefes y los mobs de más vida que el máximo de beast_lore).
ALLOWED = ["minecraft:spider", "minecraft:cave_spider", "minecraft:ravager"]
FORBIDDEN = ["minecraft:allay", "#forge:bosses", "iceandfire:fire_dragon", "iceandfire:ice_dragon",
             "iceandfire:lightning_dragon", "iceandfire:sea_serpent", "iceandfire:hydra"]

# ------------------------------------------------------------------ textos (castellano, texto fuente)
CLASS_NAME = "Druida"
CLASS_DESC = ("Mago de la naturaleza que se transforma en las bestias que conoce. Estudia a las criaturas vivas con su "
              "libro y hace un ritual sobre sus restos para aprender su forma; cuando conoce bien una especie puede "
              "convertirse en ella, con su cuerpo, su vida y sus ataques. La escuela de naturaleza es suya: el resto de "
              "escuelas pierde un 35 % y es muy torpe con armas, armaduras y escudos.")
TRAIT_DESC = "Rasgo de clase: algo menos de vida máxima."
BRANCH_NAMES = {"sap": "Savia", "ferocity": "Fiera", "wild_reserve": "Reserva Salvaje", "lore": "Saber", "roots": "Raíz"}
BRANCH_DESC = {
    "sap": "Más poder en tus conjuros de la escuela de naturaleza.",
    "ferocity": "Tus formas de bestia tienen más vida y hacen más daño.",
    "wild_reserve": "Transformarte cuesta menos maná y tienes más maná máximo.",
    "lore": "Estudias antes, tienes más tiempo para el ritual y, con la rama llena, cada especie pide un punto de conocimiento menos.",
    "roots": "Regeneras vida mientras eres una bestia y te curas al volver a tu forma.",
}
PERK_TEXT = {
    ("sap", "venom_sap"): ("Savia Venenosa", "Tus conjuros de naturaleza envenenan a lo que dañan, pero tu poder de naturaleza baja un poco."),
    ("sap", "vital_sap"): ("Savia Vital", "Los conjuros de naturaleza que dañan te curan una parte del daño, pero tu poder de naturaleza baja un poco."),
    ("ferocity", "hunter"): ("Cazador", "Tus formas hacen bastante más daño, pero tienen menos vida."),
    ("ferocity", "colossus"): ("Coloso", "Tus formas tienen bastante más vida, pero hacen menos daño."),
    ("wild_reserve", "cheap_form"): ("Forma Barata", "Transformarte cuesta mucho menos maná, pero cada forma dura como máximo unos minutos."),
    ("wild_reserve", "rich_form"): ("Forma Rica", "Transformarte cuesta más maná, pero tus formas son más fuertes en todo."),
    ("lore", "scholar"): ("Erudito", "Cada estudio da dos puntos de conocimiento, pero estudiar tarda más."),
    ("lore", "scavenger"): ("Carroñero", "Cada ritual da dos puntos de conocimiento, pero cuesta el doble de maná y tienes menos tiempo para hacerlo."),
    ("roots", "body_sap"): ("Savia Corporal", "Regeneras mucho más en forma de bestia, pero tus formas tienen algo menos de vida."),
    ("roots", "healing_exit"): ("Salida Sanadora", "Al volver a tu forma te curas mucho más, pero ya no regeneras mientras eres una bestia."),
}
SUB_NAMES = {"moon_circle": "Círculo de la Luna", "supernatural_form": "Forma Sobrenatural", "sculptor": "Escultor"}
SUB_DESC = {
    "moon_circle": "Druida del Círculo de la Luna: se transforma en un instante, casi sin recarga, y sus formas no le reservan maná.",
    "supernatural_form": "Bestia que hace magia: puede lanzar conjuros de naturaleza mientras está transformado, aunque sus formas son algo más débiles y esos conjuros cuestan más.",
    "sculptor": "Rituales oscuros: arranca partes de los restos de las bestias (cabeza, torso, patas e instinto), las une en un Recipiente de Carne y, sobre un Altar de Carne, da vida a una quimera en la que puede transformarse. Cada creación le cuesta mucho maná y parte de su vida durante un rato.",
}
ABILITY_TEXT = {
    "wild_shape": ("Forma Salvaje", "Abre el bestiario y elige una especie que conozcas: tras unos segundos lanzando (recibir daño lo interrumpe) te conviertes en ella. Pagas maná según lo fuerte que sea la bestia y, mientras la mantienes, ese maná no se regenera. Tienes la vida, el tamaño, la velocidad y los ataques de la bestia, más fuertes cuanto más nivel tengas; tu equipo se oculta y no cuenta, no puedes usar objetos ni lanzar conjuros, y tus rasgos de raza se suspenden. Si la bestia cae, vuelves a tu forma con la vida que tenías. Vuelve a pulsar (agachado, al instante) para volver a tu forma; la recarga empieza entonces. Al dormir, morir o desconectarte también vuelves."),
    "beast_lore": ("Saber de las Bestias", "Aprendes las formas de los animales y las bestias. Estudio: mantén clic derecho con un Libro de Estudio sobre una criatura viva hasta llenar la barra (si recibes daño, se reinicia; el libro se gasta al terminar). Ritual: lanza el conjuro Ritual del Druida (sin pergamino, está siempre en tu rueda) sobre los restos de una bestia recién muerta. Cada uno da un punto de conocimiento de su especie, y cada criatura solo se puede estudiar una vez y usar en un ritual una vez. Cada especie pide más puntos cuanto más fuerte es. Lo aprendido no se pierde al reiniciar la clase."),
    "track_prey": ("Rastrear Presas", "Las bestias cercanas brillan durante unos segundos (solo para ti). Si fijas una especie en el bestiario, solo ves esa, y desde más lejos. Puedes usarla transformado."),
    "dominate_beast": ("Dominar Animal", "El animal de una especie que conoces al que miras te obedece unos minutos como mascota: te sigue y ataca a tus enemigos (si sabe atacar). Puedes usarla transformado."),
    "entangling_vines": ("Enredaderas", "Brotan enredaderas del suelo a tu alrededor durante un rato: tus aliados (y tú) se curan poco a poco dentro, y los enemigos que caminan sobre ellas van más lentos y se hieren al moverse, como en un arbusto de bayas. Tienes que salir de la forma para usarla."),
}
MECHANIC_NAMES = {
    "druid_ferocity": "Fiera", "druid_wild_reserve": "Reserva Salvaje", "druid_lore": "Saber", "druid_roots": "Raíz",
    "druid_venom_sap": "Savia Venenosa", "druid_vital_sap": "Savia Vital", "druid_hunter": "Cazador", "druid_colossus": "Coloso",
    "druid_cheap_form": "Forma Barata", "druid_rich_form": "Forma Rica", "druid_scholar": "Erudito", "druid_scavenger": "Carroñero",
    "druid_body_sap": "Savia Corporal", "druid_healing_exit": "Salida Sanadora", "druid_moon": "Círculo de la Luna",
    "druid_supernatural": "Forma Sobrenatural", "druid_sculptor": "Escultor de Carne",
}
PARAMS = {
    "form_health": "Vida de tus formas|%", "form_damage": "Daño de tus formas|%", "cost": "Maná al transformarte|%",
    "study_time": "Tiempo de estudio|%", "ritual_margin": "Margen del ritual|s", "points_reduction": "Puntos de conocimiento menos por especie",
    "form_regen": "Regeneración en forma (vida por segundo)", "exit_heal": "Curación al volver a tu forma (de tu vida máxima)|%",
    "lifesteal": "Curación (del daño que haces)|%", "max_seconds": "Duración máxima de cada forma|s", "stats": "Vida y daño de tus formas|%",
    "study_points": "Puntos por estudio", "ritual_points": "Puntos por ritual", "ritual_mana": "Maná del ritual|%",
    "cast_seconds": "Tiempo de lanzamiento|s", "exit_cooldown_seconds": "Recarga al volver a tu forma|s",
    "spell_cost": "Maná de los conjuros de naturaleza en forma|%", "chimera_mana": "Maná de cada creación",
    "cinematic_seconds": "Duración de la creación|s", "health_penalty": "Vida máxima que pierdes|%",
    "penalty_seconds": "Durante|s", "max_chimeras": "Quimeras guardadas como máximo",
    "level_scaling": "Vida y daño de la forma por Nivel Total|%", "mana_base": "Maná base de una forma",
    "mana_per_power": "Maná por punto de poder de la bestia", "damage_weight": "Poder de la bestia: vida + daño x",
    "flying_cost": "Maná extra de las formas voladoras|%", "aquatic_cost": "Maná extra de las formas acuáticas|%",
    "cast_slow": "Velocidad mientras te transformas|%", "form_attack_speed": "Golpes por segundo (por defecto)",
    "aquatic_land_speed": "Velocidad de las formas acuáticas fuera del agua x", "form_swim_bonus": "Velocidad de nado de las formas acuáticas|%",
    "flight_seconds": "Vuelo seguido|s", "flight_recharge": "Recarga del vuelo en el suelo (por segundo)|%",
    "flight_ceiling": "Altura máxima de vuelo sobre el suelo (bloques)", "flight_speed": "Velocidad de vuelo",
    "form_min_speed": "Velocidad mínima de una forma x", "form_max_speed": "Velocidad máxima de una forma x",
    "study_seconds": "Tiempo de estudio|s", "ritual_cast_seconds": "Lanzamiento del ritual|s",
    "ritual_margin_seconds": "Margen para el ritual tras la muerte|s", "ritual_range": "Alcance del ritual (bloques)",
    "power_per_point": "Poder de la bestia por punto de conocimiento", "max_points": "Puntos de conocimiento como máximo",
    "max_health": "Vida máxima de una bestia válida", "remains_radius": "Distancia de un Druida para que queden restos (bloques)",
    "fixed_radius": "Radio con una especie fijada (bloques)", "follow_distance": "Te sigue a más de (bloques)",
    "teleport_distance": "Se teletransporta a más de (bloques)", "damage_interval_seconds": "Daño al moverse como mucho cada|s",
}

ABILITY_NAMES = {
    "pounce": ("Salto de caza", "Te lanzas hacia delante; tu siguiente mordisco poco después hace más daño."),
    "leap": ("Abalanzarse", "Saltas alto sobre el enemigo al que miras y, al caer cerca, lo aturdes un instante."),
    "super_jump": ("Supersalto", "Un salto altísimo hacia delante."),
    "web": ("Telaraña", "Lanzas una telaraña a lo que miras: deja una telaraña unos segundos."),
    "roar": ("Rugido", "Debilitas y empujas a los enemigos cercanos."),
    "ram": ("Embestida", "Cargas hacia delante y lanzas por los aires al primer enemigo que golpeas."),
    "toss": ("Corneada", "Lanzas hacia arriba al enemigo que tienes delante."),
    "stun_roar": ("Rugido aturdidor", "Aturdes a los enemigos cercanos (a los jefes, menos)."),
    "dash": ("Carrera", "Un impulso largo hacia delante."),
    "spit": ("Escupitajo", "Escupes a distancia."),
    "sting": ("Aguijón", "Picas al enemigo que tienes delante y lo envenenas."),
    "echolocation": ("Ecolocalización", "Las criaturas cercanas brillan unos segundos (solo para ti)."),
    "water_dash": ("Embestida acuática", "Un impulso muy rápido bajo el agua."),
    "shell": ("Caparazón", "Te escondes en el caparazón: recibes mucho menos daño, pero vas muy lento."),
    "play_dead": ("Hacerse el muerto", "Regeneras rápido y los enemigos dejan de perseguirte."),
    "tongue": ("Lengua", "Atrapas con la lengua a una criatura pequeña y la acercas."),
    "ink": ("Nube de tinta", "Cegas a los enemigos cercanos."),
    "inflate": ("Inflarse", "Te hinchas unos segundos: los enemigos que te tocan se envenenan."),
}
PASSIVE_NAMES = {
    "climb": "Trepa paredes", "poison_bite": "Mordisco venenoso", "no_fall": "Sin daño de caída",
    "scare_creepers": "Los creepers huyen", "night_vision": "Visión nocturna", "fire_immune": "Inmune al fuego",
    "lava_walk": "Camina sobre la lava", "slow_fall": "Caída lenta", "leaf_breaker": "Rompe hojas al andar",
    "dolphin_grace": "Gracia del delfín para los aliados", "fly": "Vuela", "swim": "Nada rápido y respira bajo el agua",
    "amphibious": "Respira bajo el agua",
}

PARTS = {"head": ("Cabeza de %s", "Cabeza (ataque)"), "torso": ("Torso de %s", "Torso (vida y tamaño)"),
         "legs": ("Patas de %s", "Patas (movimiento)"), "instinct": ("Instinto de %s", "Instinto (habilidad)")}

UI = {
    f"item.{NS}.study_book": "Libro de Estudio",
    f"item.{NS}.flesh_vessel": "Recipiente de Carne",
    f"block.{NS}.flesh_altar": "Altar de Carne",
    f"entity.{NS}.beast_remains": "Restos",
    f"effect.{NS}.flesh_price": "Precio de la Carne",
    f"spell.{NS}.druid_ritual": "Ritual del Druida",
    f"spell.{NS}.druid_ritual.guide": "Sobre los restos de una bestia recién muerta: aprendes un poco más de su especie (un punto de conocimiento). Solo el Druida.",
    f"death.attack.{NS}.druid_vines": "%1$s quedó atrapado entre las enredaderas",
    f"death.attack.{NS}.druid_vines.player": "%1$s quedó atrapado entre las enredaderas de %2$s",
    f"key.{NS}.beast_ability": "Habilidad de bestia (Druida)",
    f"tooltip.{NS}.study_book": "Mantén clic derecho sobre un animal o una bestia vivos para estudiarlos (solo el Druida). Se gasta al terminar.",
    f"tooltip.{NS}.flesh_vessel": "Clic derecho: abre el recipiente. Llénalo con una cabeza, un torso, unas patas y un instinto de bestia, y lánzalo sobre un Altar de Carne.",
    f"tooltip.{NS}.flesh_vessel.parts": "Partes: %s/4",
    f"tooltip.{NS}.beast_part.slot": "Va en el hueco: %s",
    f"tooltip.{NS}.beast_part.sculptor": "Solo el Escultor sabe usarla.",
    f"tooltip.{NS}.flesh_altar": "Lanza encima un Recipiente de Carne completo y haz clic derecho en el altar (solo el Escultor).",
    f"druid.{NS}.chimera": "Quimera de %s",
    f"druid.{NS}.unknown_species": "especie desconocida",
    f"message.{NS}.druid.not_druid": "Solo un Druida sabe usar esto.",
    f"message.{NS}.druid.study_done": "Has estudiado a %s: %s/%s de conocimiento.",
    f"message.{NS}.druid.study_already": "Ya estudiaste a esta criatura.",
    f"message.{NS}.druid.study_invalid": "Solo puedes estudiar animales y bestias vivos.",
    f"message.{NS}.druid.study_interrupted": "El estudio se ha interrumpido.",
    f"message.{NS}.druid.ritual_done": "Ritual completado: %s/%s de conocimiento de %s.",
    f"message.{NS}.druid.ritual_already": "Ya hiciste el ritual con esta criatura.",
    f"message.{NS}.druid.ritual_no_target": "No hay restos frescos de una bestia a la vista.",
    f"message.{NS}.druid.ritual_too_old": "Estos restos ya no sirven: llegas tarde.",
    f"message.{NS}.druid.species_learned": "¡Ya puedes transformarte en %s!",
    f"message.{NS}.druid.part_gained": "Arrancas de los restos: %s.",
    f"message.{NS}.druid.form_unknown": "Aún no conoces bien a esa especie.",
    f"message.{NS}.druid.no_mana": "Necesitas %s de maná.",
    f"message.{NS}.druid.cast_start": "Te transformas en %s…",
    f"message.{NS}.druid.cast_interrupted": "La transformación se ha interrumpido.",
    f"message.{NS}.druid.form_start": "Ahora eres: %s.",
    f"message.{NS}.druid.form_end": "Vuelves a tu forma.",
    f"message.{NS}.druid.form_broken": "Tu forma de bestia ha caído: vuelves a tu forma.",
    f"message.{NS}.druid.form_expired": "Tu forma barata se agota.",
    f"message.{NS}.druid.in_form": "No puedes hacer eso transformado.",
    f"message.{NS}.druid.vines_in_form": "Vuelve a tu forma para invocar las enredaderas.",
    f"message.{NS}.druid.casting": "Ya te estás transformando.",
    f"message.{NS}.druid.track_none": "No hay presas cerca.",
    f"message.{NS}.druid.track_found": "Rastreas %s presas.",
    f"message.{NS}.druid.track_fixed": "Rastrearás solo: %s.",
    f"message.{NS}.druid.track_cleared": "Rastrearás todas las bestias.",
    f"message.{NS}.druid.dominate_no_target": "No miras a ningún animal al alcance.",
    f"message.{NS}.druid.dominate_unknown": "No conoces bien a esa especie.",
    f"message.{NS}.druid.dominate_done": "%s te obedece durante %s s.",
    f"message.{NS}.druid.dominate_end": "%s ya no te obedece.",
    f"message.{NS}.druid.chimera_incomplete": "El recipiente necesita las cuatro partes.",
    f"message.{NS}.druid.chimera_no_vessel": "Lanza un Recipiente de Carne completo sobre el altar.",
    f"message.{NS}.druid.chimera_not_sculptor": "Solo un Escultor sabe dar vida a la carne.",
    f"message.{NS}.druid.chimera_busy": "La carne ya se está moviendo.",
    f"message.{NS}.druid.chimera_start": "La carne empieza a moverse…",
    f"message.{NS}.druid.chimera_done": "Has creado: %s.",
    f"message.{NS}.druid.chimera_failed": "La creación se ha deshecho.",
    f"message.{NS}.druid.beast_ability_none": "Esta forma no tiene habilidad especial.",
    f"message.{NS}.druid.beast_ability_cooldown": "%s: %s s",
    f"message.{NS}.druid.water_only": "Solo bajo el agua.",
    f"message.{NS}.druid.flight_tired": "Tus alas están cansadas: aterriza para recuperarte.",
    f"message.{NS}.druid.no_target": "No hay ningún objetivo delante.",
    f"ui.{NS}.bestiary.title": "Bestiario",
    f"ui.{NS}.bestiary.return": "Volver a tu forma",
    f"ui.{NS}.bestiary.empty": "Aún no conoces ninguna bestia. Estudia a una criatura viva con el Libro de Estudio o haz el Ritual del Druida sobre sus restos.",
    f"ui.{NS}.bestiary.knowledge": "Conocimiento: %s/%s",
    f"ui.{NS}.bestiary.mana": "Maná: %s",
    f"ui.{NS}.bestiary.health": "Vida: %s",
    f"ui.{NS}.bestiary.damage": "Daño: %s",
    f"ui.{NS}.bestiary.speed": "Velocidad: x%s",
    f"ui.{NS}.bestiary.ability": "Habilidad: %s",
    f"ui.{NS}.bestiary.no_ability": "Sin habilidad especial",
    f"ui.{NS}.bestiary.passives": "Rasgos: %s",
    f"ui.{NS}.bestiary.chimeras": "Quimeras",
    f"ui.{NS}.bestiary.parts": "Cabeza: %s · Torso: %s · Patas: %s · Instinto: %s",
    f"ui.{NS}.bestiary.track": "Clic derecho: fijar para Rastrear Presas",
    f"ui.{NS}.bestiary.tracked": "Fijada para Rastrear Presas (clic derecho para quitarla)",
    f"ui.{NS}.bestiary.locked": "Te falta conocimiento de esta especie.",
    f"ui.{NS}.bestiary.cooldown": "Recarga: %s s",
    f"ui.{NS}.bestiary.help": "Clic: transformarte. Agachado + tecla de Forma Salvaje: volver al instante.",
    f"ui.{NS}.ritual_choice.title": "¿Qué tomas de los restos de %s?",
    f"ui.{NS}.ritual_choice.knowledge": "Conocimiento (un punto)",
    f"ui.{NS}.chimera_replace.title": "Ya tienes el máximo de quimeras: ¿cuál sustituyes?",
    f"ui.{NS}.chimera_replace.cancel": "No crear la quimera",
    f"hud.{NS}.druid.casting": "Transformándote: %s",
    f"hud.{NS}.druid.study": "Estudiando: %s",
    f"hud.{NS}.druid.flight": "Vuelo",
    f"hud.{NS}.druid.form": "%s",
    f"hud.{NS}.druid.form_timer": "%s (%s s)",
    f"hud.{NS}.druid.ability": "[%s] %s",
    f"hud.{NS}.druid.ability_cooldown": "[%s] %s: %s s",
    f"hud.{NS}.druid.chimera": "Creando quimera…",
    f"druid.{NS}.part.slot.head": PARTS["head"][1], f"druid.{NS}.part.slot.torso": PARTS["torso"][1],
    f"druid.{NS}.part.slot.legs": PARTS["legs"][1], f"druid.{NS}.part.slot.instinct": PARTS["instinct"][1],
}
for _p, (_name, _slot) in PARTS.items():
    UI[f"item.{NS}.beast_{_p}"] = _name.replace(" de %s", " de bestia")
    UI[f"item.{NS}.beast_{_p}.named"] = _name
for _a, (_n, _d) in ABILITY_NAMES.items():
    UI[f"druid.{NS}.ability.{_a}"] = _n
    UI[f"druid.{NS}.ability.{_a}.desc"] = _d
for _p, _n in PASSIVE_NAMES.items():
    UI[f"druid.{NS}.passive.{_p}"] = _n


def recipes():
    out = {}
    out["study_book"] = {"type": "minecraft:crafting_shapeless", "category": "misc",
                         "ingredients": [item("minecraft:book"), tag("minecraft:leaves"), item("minecraft:wheat_seeds")],
                         "result": {"item": R("study_book"), "count": 2}}
    out["flesh_vessel"] = {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["BFB", "FPF", "BFB"],
                           "key": {"B": item("minecraft:bone"), "F": item("minecraft:rotten_flesh"),
                                   "P": item("minecraft:decorated_pot")},
                           "result": {"item": R("flesh_vessel"), "count": 1}}
    out["flesh_altar"] = {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["CEC", "FIF", "BBB"],
                          "key": {"C": tag("minecraft:candles"), "E": item("minecraft:fermented_spider_eye"),
                                  "F": item("minecraft:rotten_flesh"), "I": tag("forge:ingots/iron"),
                                  "B": item("minecraft:bone_block")},
                          "result": {"item": R("flesh_altar"), "count": 1}}
    return out


def build(dump, out_dir):
    data = os.path.join(out_dir, "data")
    assets = os.path.join(out_dir, "assets", NS)
    # Tabla de bestias: una habilidad oculta (ningún hito la da), que se sincroniza con el cliente como las demás.
    dump(os.path.join(data, NS, NS, "abilities", "druid_beasts.json"),
         {"schema_version": 1, "passive": True, "beasts": BEASTS, "abilities": BEAST_ABILITIES})
    for name, recipe in recipes().items():
        dump(os.path.join(data, NS, "recipes", "druid", name + ".json"), recipe)
    dump(os.path.join(data, NS, "tags", "entity_types", "druid", "allowed.json"), {"replace": False, "values": ALLOWED})
    # Las entradas de otros mods (y forge:bosses, que puede no existir) son opcionales.
    dump(os.path.join(data, NS, "tags", "entity_types", "druid", "forbidden.json"), {"replace": False, "values": [
        v if v.startswith("minecraft:") else {"id": v, "required": False} for v in FORBIDDEN]})
    # Daño de las Enredaderas: lleva al Druida como causante, pero no pasa por el pipeline (muros, críticos).
    dump(os.path.join(data, NS, "damage_type", "druid_vines.json"), {"message_id": f"{NS}.druid_vines", "exhaustion": 0.0, "scaling": "never"})
    no_proc = os.path.join(data, NS, "tags", "damage_type", "no_proc.json")
    cur = json.load(open(no_proc, encoding="utf-8")) if os.path.exists(no_proc) else {"replace": False, "values": []}
    if R("druid_vines") not in cur["values"]: cur["values"].append(R("druid_vines"))
    dump(no_proc, cur)
    # Modelos de objetos (texturas en tools/art/make_druid.py) y del altar.
    for i in ("study_book", "flesh_vessel", "beast_head", "beast_torso", "beast_legs", "beast_instinct"):
        dump(os.path.join(assets, "models", "item", i + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{i}"}})
    dump(os.path.join(assets, "blockstates", "flesh_altar.json"), {"variants": {"": {"model": f"{NS}:block/flesh_altar"}}})
    dump(os.path.join(assets, "models", "block", "flesh_altar.json"),
         {"parent": "minecraft:block/cube_bottom_top", "textures": {"top": f"{NS}:block/flesh_altar_top",
                                                                    "bottom": f"{NS}:block/flesh_altar_bottom",
                                                                    "side": f"{NS}:block/flesh_altar_side"}})
    dump(os.path.join(assets, "models", "item", "flesh_altar.json"), {"parent": f"{NS}:block/flesh_altar"})
    # El altar se rompe con pico y se suelta a sí mismo.
    dump(os.path.join(data, NS, "loot_tables", "blocks", "flesh_altar.json"), {"type": "minecraft:block", "pools": [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": R("flesh_altar")}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    mp = os.path.join(data, "minecraft", "tags", "blocks", "mineable", "pickaxe.json")
    cur = json.load(open(mp, encoding="utf-8")) if os.path.exists(mp) else {"replace": False, "values": []}
    if R("flesh_altar") not in cur["values"]: cur["values"].append(R("flesh_altar"))
    dump(mp, cur)
    return dict(UI)
