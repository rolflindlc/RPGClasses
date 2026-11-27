# Ingeniero (adenda del Ingeniero, 27-11-2026): armero de armas de fuego (NTGL / Create: Gunsmithing), con las subclases
# Pistolero, Artillero y Artificiero. Lo importa gen_data.py. Los ids deben coincidir con io.github.rpgclasses.engineer
# (Engineer.java). Las propuestas sin confirmar de la adenda se programan tal cual. Todo es [MEDIR].
import json
import os

NS = "rites_and_paths"
MC = "minecraft:generic."
CGS = "cgs:"


def R(x): return f"{NS}:{x}"
def to(v, op="add"): return {"to": v, "operation": op}
def mech(i, **p): return {"type": R("mechanic"), "id": R(i), **p}
def ms(level, grants, sub=False): return {("subclass_level" if sub else "total_level"): level, "grants": R(grants)}


CLASS_ID = "engineer"
DISCIPLINE = "firearm"

# Adenda, 2: muro de Pólvora por clase. El Ingeniero es afín (0 %). Maestro del Giro: propuesta.
FIREARM_WALLS = {
    "ranger": .40,
    "rogue": .50,
    "barbarian": .60, "paladin": .60, "monk": .60, "bard": .60,
    "mage": .75, "cleric": .75, "druid": .75,
    "spin_master": .60,
}


def firearm_wall(cid):
    """Muro de Pólvora de una clase (las clases nuevas sin valor fallan al generar, para no olvidarlo)."""
    if cid == CLASS_ID: return 0.0
    assert cid in FIREARM_WALLS, ("falta el muro de Pólvora de la clase", cid)
    return FIREARM_WALLS[cid]


DISCIPLINE_NAME = "Pólvora"

# ------------------------------------------------------------------ clase
CLASS = dict(icon=CGS + "revolver", roles=["distancia", "munición"], kit=CLASS_ID, crit=0.0,
             # Adenda, 3 (propuesta): Ligeras −20 %, Armadura −20 %, Pesadas −40 % (el martillo neumático sin muro),
             # Arco −50 %, Escudos −50 %, Magia −50 %. Pólvora 0 % (afín).
             walls=dict(magic=.50, bow=.50, heavy=.40, light=.20, armor=.20, shield=.50),
             branches=[
                 # Munición Improvisada: 2,5 % por punto (25 % con 10) de que la primera bala tras recargar sea especial
                 # (las 5 primeras en armas automáticas). Tipos base: incendiaria, aturdidora y congelante.
                 ("improvised_ammo", [mech("engineer_improvised", chance={"to": .25}, auto_shots=5, fire_seconds=4,
                                           stun_ticks=10, freeze_seconds=3, freeze_level=1)]),
                 # Recarga en Marcha: NTGL no frena al recargar, así que la rama da +1 % de velocidad por punto mientras
                 # recargas (propuesta: sustituye a «no ralentiza») y la recarga perfecta da +20 % de velocidad 2 s.
                 ("moving_reload", [mech("engineer_moving_reload", reload_speed={"to": .10}, perfect_speed=.20,
                                         perfect_speed_seconds=2)]),
             ],
             milestones=[ms(0, "active_reload"), ms(0, "armorer"), ms(5, "rifle_butt"), ms(15, "suppressing_fire"),
                         ms(30, "last_magazine")],
             subs=["gunslinger", "artillerist", "artificer"])

EXTRA_BRANCHES = [
    # Racha Perfecta: +0,5 % de daño por punto y por recarga perfecta seguida (+5 % por carga con 10), hasta 6 cargas.
    # Se pierde al encasquillar o tras 15 s sin disparar (propuesta).
    ("perfect_streak", [mech("engineer_streak", per_stack={"to": .05}, max_stacks=6, idle_seconds=15)]),
    # Cartuchera: las armas de fuego de la barra rápida que no llevas en la mano se recargan solas, al 5 % por punto
    # de la velocidad normal (50 % con 10), con munición de tu inventario.
    ("holster", [mech("engineer_holster", speed={"to": .50})]),
    # Puntería: −4 % de dispersión y +1 % de daño a más de 15 bloques por punto.
    ("marksman", [mech("engineer_marksman", spread={"to": -.40}, far_bonus={"to": .10}, far_distance=15)]),
]

TRAITS = []

PERKS = {
    "improvised_ammo": [("rusted_ammo", [mech("engineer_rusted_ammo", poison_seconds=6, poison_level=0, wither_seconds=3,
                                              wither_level=1)]),
                        ("arcane_ammo", [mech("engineer_arcane_ammo", levitation_seconds=3, pull_fraction=.25,
                                              boss_slow_seconds=3, boss_slow_level=1)])],
    "moving_reload": [("tactical_retreat", [mech("engineer_tactical_retreat", radius=5, speed=.30, perfect_speed=.10)]),
                      ("reload_dash", [mech("engineer_reload_dash", distance=4, icd_seconds=6, slow_reload=.10)])],
    "perfect_streak": [("fragile_streak", [mech("engineer_fragile_streak", per_stack=.07, hit_loss=2)]),
                       ("steady_streak", [mech("engineer_steady_streak", per_stack=.04, jam_loss=2)])],
    "holster": [("rotating_bandolier", [mech("engineer_rotating_bandolier", speed=.35)]),
                ("throwing_gun", [mech("engineer_throwing_gun", damage=4, stun_ticks=10, return_seconds=4)])],
    "marksman": [("headhunter", [mech("engineer_headhunter", headshot=1.75, body=-.15)]),
                 ("suppression", [mech("engineer_suppression", headshot=1.25, slow_seconds=1, slow_level=0)])],
}

BRANCH_ICONS = {"improvised_ammo": CGS + "round_shotgun_incendiary", "moving_reload": "minecraft:leather_boots",
                "perfect_streak": "minecraft:clock", "holster": "minecraft:leather_chestplate", "marksman": CGS + "scope"}

SUBS = {
    # Pistolero (armas ligeras): Mano Rápida (+2 % de cadencia por nivel; cambio de arma de fuego instantáneo) y Duelista.
    "gunslinger": (CGS + "revolver", [
        mech("gunslinger_quick_hand", rate={"to": .20}),
        mech("gunslinger_duelist", damage={"to": .20}, reduction=.10, kill_speed=.20, kill_speed_seconds=3),
    ], [ms(5, "fan_the_hammer", True), ms(10, "dead_eye", True)]),
    # Artillero (armas pesadas): Cañón Caliente y Brazo de Hierro. NTGL no frena con las armas pesadas, así que Brazo de
    # Hierro da +1 % de velocidad por nivel con un arma pesada en la mano (propuesta) y +5 % de resistencia al empuje.
    "artillerist": (CGS + "gatling", [
        mech("artillerist_hot_barrel", rate_per_second=.02, cap={"to": .20}, cool_seconds=2),
        mech("artillerist_iron_arm", speed={"to": .10}, knockback_resistance={"to": .50}),
    ], [ms(5, "bombardment", True), ms(10, "siege_mode", True)]),
    # Artificiero: cargas compartidas (1/2/3 a los niveles 1/4/8) para torretas, minas y trinchera.
    "artificer": ("minecraft:lightning_rod", [
        mech("artificer_gadgets", charges={"values": [1, 1, 1, 2, 2, 2, 2, 3, 3, 3]},
             mine_damage={"values": [7, 8, 9, 10, 11, 12, 13, 14, 15, 16]}),
    ], [ms(1, "deploy_turret", True), ms(1, "throw_mine", True), ms(1, "dig_trench", True)]),
}

# Adenda, 3 (propuesta): trabuco y 24 cartuchos de papel (de Create: Gunsmithing; sin el mod no se dan) y cuero.
KIT = [{"item": CGS + "flintlock", "slot": "mainhand"}, {"item": CGS + "paper_cartridge", "count": 24, "slot": "inventory"}] + \
      [{"item": f"minecraft:leather_{p}", "slot": s} for p, s in
       (("helmet", "head"), ("chestplate", "chest"), ("leggings", "legs"), ("boots", "feet"))]

ABILITIES = {
    # Nivel Total 1: la recarga activa (zonas en fracción de la recarga) y el rasgo Armero.
    "active_reload": dict(passive=True, perfect_start=.55, perfect_end=.65, good_start=.40, good_end=.80,
                          perfect_damage=.15, perfect_seconds=4, jam_slow=.50),
    "armorer": dict(passive=True, ammo_bonus=.50),
    # Nivel Total 5: el golpe con el arma de fuego empuja y aturde (sustituye a la tecla: el Ingeniero solo tiene 4).
    "rifle_butt": dict(passive=True, knockback=1.6, stun_ticks=10, cooldown_seconds=12),
    # Nivel Total 15 y 30.
    "suppressing_fire": dict(duration_seconds=6, rate=.25, slow_seconds=2, slow_level=0, cooldown_seconds=40),
    "last_magazine": dict(duration_seconds=8, cooldown_seconds=120),
    # Pistolero.
    "fan_the_hammer": dict(duration_seconds=.5, spread_factor=3, damage_factor=.70, cooldown_seconds=12),
    "dead_eye": dict(duration_seconds=3, max_targets=6, range=64, cooldown_seconds=60),
    # Artillero.
    "bombardment": dict(range=30, delay_seconds=1.5, shells=6, radius=4, damage=8, blast_radius=2, powder_cost=3,
                        cooldown_seconds=25),
    "siege_mode": dict(duration_seconds=10, rate=.40, area_damage=.25, damage_reduction=.30, cooldown_seconds=75),
    # Artificiero (teclas propias, no ocupan ranura de habilidad).
    "deploy_turret": dict(passive=True, stats_factor=.70, range=24, ammo_range=32, charge_seconds=15),
    "throw_mine": dict(passive=True, throw_speed=.9, arm_seconds=1, blast_radius=2, mines_per_charge=3),
    "dig_trench": dict(passive=True, radius=3, max_seconds=60, damage=1, damage_interval_seconds=.5, ally_slow=.5),
}

# Munición de Create: Gunsmithing (para el rasgo Armero y la Cartuchera): todo lo que se dispara.
AMMO = ["paper_cartridge", "paper_shot", "nail", "nail_steel", "round_revolver", "round_revolver_piercing", "round_shotgun",
        "round_shotgun_flechette", "round_shotgun_flechette_steel", "round_shotgun_incendiary", "round_gatling",
        "round_gatling_piercing", "rocket", "rocket_small", "spear", "frag_grenade"]
LIGHT_FIREARMS = ["revolver", "flintlock", "shotgun"]
HEAVY_FIREARMS = ["gatling", "launcher", "nailgun", "blazegun", "frag_grenade"]

# ------------------------------------------------------------------ textos (castellano, texto fuente)
CLASS_NAME = "Ingeniero"
CLASS_DESC = ("Armero de pólvora: dispara las armas de fuego de Create: Gunsmithing mejor que nadie y fabrica la munición "
              "de todo el servidor. Su recarga es un minijuego: acierta la zona perfecta para recargar al instante y pegar "
              "más fuerte, o encasquilla el arma. Pica fuerte, pero depende de una munición que cuesta fabricar, y es "
              "torpe con magia, arcos, escudos y armas pesadas.")
TRAIT_DESC = "Rasgo de clase, Armero: al fabricar munición a mano obtienes la mitad más."
BRANCH_NAMES = {"improvised_ammo": "Munición Improvisada", "moving_reload": "Recarga en Marcha",
                "perfect_streak": "Racha Perfecta", "holster": "Cartuchera", "marksman": "Puntería"}
BRANCH_DESC = {
    "improvised_ammo": "Probabilidad de que la primera bala tras recargar sea especial (incendiaria, aturdidora o congelante); en armas automáticas, las cinco primeras. Un icono junto a la mira te dice cuál será la siguiente.",
    "moving_reload": "Vas más rápido mientras recargas, y una recarga perfecta te da un acelerón de unos segundos.",
    "perfect_streak": "Cada recarga perfecta seguida suma daño, hasta seis veces. Se pierde al encasquillar o si pasas un rato sin disparar.",
    "holster": "Las armas de fuego de tu barra rápida que no llevas en la mano se recargan solas, más despacio, con la munición de tu inventario.",
    "marksman": "Menos dispersión y más daño a los blancos lejanos.",
}
PERK_TEXT = {
    ("improvised_ammo", "rusted_ammo"): ("Munición Oxidada", "Añade dos balas especiales: una envenena y otra marchita."),
    ("improvised_ammo", "arcane_ammo"): ("Munición Arcana", "Añade dos balas especiales: una hace levitar y otra teletransporta al objetivo hacia ti. Los jefes, en su lugar, se ralentizan."),
    ("moving_reload", "tactical_retreat"): ("Huida Táctica", "Si recargas con un enemigo cerca, vas mucho más rápido mientras dura la recarga, pero el acelerón de la recarga perfecta es menor."),
    ("moving_reload", "reload_dash"): ("Impulso de Recarga", "Una recarga perfecta te lanza unos bloques hacia delante (una vez cada pocos segundos), pero las recargas que no son perfectas tardan algo más."),
    ("perfect_streak", "fragile_streak"): ("Racha Frágil", "Cada carga da más daño, pero recibir daño te quita dos cargas."),
    ("perfect_streak", "steady_streak"): ("Racha Sostenida", "Encasquillar solo te quita dos cargas en vez de todas, pero cada carga da menos daño."),
    ("holster", "rotating_bandolier"): ("Bandolera Giratoria", "Al vaciarse el arma cambias al instante a la siguiente arma cargada de tu barra, pero la cartuchera recarga más despacio."),
    ("holster", "throwing_gun"): ("Arma Arrojadiza", "Con el arma vacía, el clic izquierdo la lanza (daño de culata y aturdimiento); vuelve recargada a tu inventario a los pocos segundos. La cartuchera solo recarga las armas lanzadas."),
    ("marksman", "headhunter"): ("Cazacabezas", "Los disparos a la cabeza hacen más daño, pero el resto de impactos hace menos."),
    ("marksman", "suppression"): ("Supresión", "Los impactos que no son en la cabeza ralentizan un instante, pero los disparos a la cabeza hacen menos daño."),
}
SUB_NAMES = {"gunslinger": "Pistolero", "artillerist": "Artillero", "artificer": "Artificiero"}
SUB_DESC = {
    "gunslinger": "Duelista de armas ligeras (revólver, trabuco y escopeta): más cadencia, cambio de arma instantáneo y un enemigo retado contra el que pega más fuerte y que le hace menos daño.",
    "artillerist": "Fuego pesado (gatling, lanzacohetes, clavadora, lanzallamas y granadas): dispara más rápido cuanto más tiempo mantiene el gatillo, se mueve más rápido y aguanta mejor los empujones con las armas pesadas y bombardea zonas.",
    "artificer": "Torretas y artilugios: convierte el arma de su mano en una torreta, lanza minas invisibles y levanta trincheras de alambre. Las tres cosas gastan las mismas cargas.",
}
ABILITY_TEXT = {
    "active_reload": ("Recarga Activa", "Al recargar un arma de fuego, un cursor recorre una barra. Vuelve a pulsar la tecla de recargar: en la zona perfecta recargas al instante y haces más daño unos segundos; en la zona buena recargas al instante; fuera de ellas el arma se encasquilla y la recarga va más lenta. Sin pulsar, la recarga es la normal."),
    "armorer": ("Armero", "Al fabricar munición de armas de fuego a mano obtienes la mitad más (las líneas automáticas de Create no cuentan)."),
    "rifle_butt": ("Culatazo", "Tu golpe cuerpo a cuerpo con un arma de fuego empuja al enemigo y lo aturde un instante (una vez cada pocos segundos)."),
    "suppressing_fire": ("Fuego Supresor", "Durante unos segundos disparas más rápido y tus balas ralentizan."),
    "last_magazine": ("Último Cargador", "Durante unos segundos no gastas munición y todas tus recargas son perfectas."),
    "fan_the_hammer": ("Abanico", "Con el revólver, vacías el tambor en medio segundo golpeando el percutor: mucha más dispersión y algo menos de daño por bala. Después recargas."),
    "dead_eye": ("Ojo Muerto", "Durante unos segundos marcas a los enemigos por los que pasas la mira. Al terminar, disparas una bala a la cabeza de cada marcado, gastando munición del cargador. Necesitas un arma ligera en la mano."),
    "bombardment": ("Bombardeo", "Marcas un punto lejano y, poco después, caen varios obuses a su alrededor (sin romper bloques ni dañar a tus aliados). Gasta un cohete pequeño o, si no tienes, algo de pólvora."),
    "siege_mode": ("Modo Asedio", "Te anclas al suelo unos segundos: no puedes moverte, pero disparas mucho más rápido, sin gastar munición, haces más daño en área, recibes menos daño y nada te empuja."),
    "deploy_turret": ("Torreta", "Tecla de torreta: el arma de fuego de tu mano se coloca como torreta y dispara sola a tus enemigos con munición de tu inventario. Se rompe de un golpe y vuelve a tu inventario. Con la mano vacía recoges todas tus torretas y recuperas sus cargas."),
    "throw_mine": ("Mina", "Tecla de mina: lanzas una mina que al segundo se vuelve invisible para tus enemigos y explota cuando la pisa uno. Puedes tener tres por carga; la más antigua desaparece."),
    "dig_trench": ("Trinchera", "Tecla de trinchera: levantas un semicírculo de alambre de espino delante de ti. Frena como una telaraña y hiere a los enemigos que se mueven dentro; tú y tus aliados solo os frenáis un poco. Solo puede haber una."),
}
MECHANIC_NAMES = {
    "engineer_improvised": "Munición Improvisada", "engineer_moving_reload": "Recarga en Marcha",
    "engineer_streak": "Racha Perfecta", "engineer_holster": "Cartuchera", "engineer_marksman": "Puntería",
    "engineer_rusted_ammo": "Munición Oxidada", "engineer_arcane_ammo": "Munición Arcana",
    "engineer_tactical_retreat": "Huida Táctica", "engineer_reload_dash": "Impulso de Recarga",
    "engineer_fragile_streak": "Racha Frágil", "engineer_steady_streak": "Racha Sostenida",
    "engineer_rotating_bandolier": "Bandolera Giratoria", "engineer_throwing_gun": "Arma Arrojadiza",
    "engineer_headhunter": "Cazacabezas", "engineer_suppression": "Supresión",
    "gunslinger_quick_hand": "Mano Rápida", "gunslinger_duelist": "Duelista",
    "artillerist_hot_barrel": "Cañón Caliente", "artillerist_iron_arm": "Brazo de Hierro",
    "artificer_gadgets": "Artilugios",
}
PARAMS = {
    "auto_shots": "Balas especiales en armas automáticas", "fire_seconds": "Fuego de la incendiaria|s",
    "stun_ticks": "Aturdimiento (ticks)", "freeze_seconds": "Lentitud de la congelante|s", "freeze_level": "Nivel de la lentitud",
    "reload_speed": "Velocidad al recargar|%", "perfect_speed": "Velocidad tras recarga perfecta|%",
    "perfect_speed_seconds": "Duración del acelerón|s", "per_stack": "Daño por carga|%", "max_stacks": "Cargas máximas",
    "idle_seconds": "Tiempo sin disparar que borra la racha|s", "spread": "Dispersión|%", "far_bonus": "Daño a distancia|%",
    "far_distance": "Distancia mínima (bloques)", "poison_seconds": "Veneno|s", "poison_level": "Nivel del veneno",
    "wither_seconds": "Marchitamiento|s", "wither_level": "Nivel del marchitamiento", "levitation_seconds": "Levitación|s",
    "pull_fraction": "Distancia que acerca la atracción|%", "boss_slow_seconds": "Lentitud de los jefes|s",
    "boss_slow_level": "Nivel de la lentitud de los jefes", "speed": "Velocidad|%",
    "distance": "Distancia (bloques)", "icd_seconds": "Tiempo entre activaciones|s", "slow_reload": "Recarga más lenta|%",
    "hit_loss": "Cargas perdidas al recibir daño", "jam_loss": "Cargas perdidas al encasquillar",
    "return_seconds": "Vuelta al inventario|s", "headshot": "Multiplicador de disparo a la cabeza", "body": "Daño del resto de impactos|%",
    "slow_seconds": "Lentitud|s", "slow_level": "Nivel de la lentitud", "rate": "Cadencia|%", "reduction": "Daño recibido del retado|%",
    "kill_speed": "Velocidad al matar al retado|%", "kill_speed_seconds": "Duración de la velocidad|s",
    "rate_per_second": "Cadencia por segundo de fuego|%", "cap": "Cadencia máxima|%", "cool_seconds": "Enfriamiento|s",
    "knockback_resistance": "Resistencia al empuje|%", "charges": "Cargas", "mine_damage": "Daño de las minas",
}

UI = {
    f"discipline.{NS}.{DISCIPLINE}.name": DISCIPLINE_NAME,
    f"hud.{NS}.engineer.perfect": "¡Perfecta!",
    f"hud.{NS}.engineer.good": "Buena",
    f"hud.{NS}.engineer.jam": "¡Encasquillada!",
    f"hud.{NS}.engineer.streak": "Racha x%s",
    f"hud.{NS}.engineer.charges": "Cargas: %s/%s",
    f"hud.{NS}.engineer.loaded": "Cargada: %s ×%s",
    f"hud.{NS}.engineer.next": "Siguiente: %s",
    f"hud.{NS}.engineer.perfect_buff": "Recarga perfecta: %ss",
    f"special.{NS}.fire": "Incendiaria",
    f"special.{NS}.stun": "Aturdidora",
    f"special.{NS}.frost": "Congelante",
    f"special.{NS}.poison": "Venenosa",
    f"special.{NS}.wither": "Marchita",
    f"special.{NS}.levitation": "Levitante",
    f"special.{NS}.pull": "Atrayente",
    f"key.{NS}.engineer_turret": "Torreta (Artificiero)",
    f"key.{NS}.engineer_mine": "Mina (Artificiero)",
    f"key.{NS}.engineer_trench": "Trinchera (Artificiero)",
    f"message.{NS}.engineer.no_firearm": "Necesitas un arma de fuego en la mano.",
    f"message.{NS}.engineer.no_light_firearm": "Necesitas un arma de fuego ligera en la mano.",
    f"message.{NS}.engineer.no_revolver": "Necesitas el revólver en la mano.",
    f"message.{NS}.engineer.empty": "El arma no tiene balas.",
    f"message.{NS}.engineer.no_charges": "No te quedan cargas.",
    f"message.{NS}.engineer.no_powder": "Necesitas un cohete pequeño o pólvora.",
    f"message.{NS}.engineer.cant_place": "No puedes colocar eso ahí.",
    f"message.{NS}.engineer.turrets_recalled": "Has recogido %s torretas.",
    f"hud_editor.{NS}.element.engineer_reload": "Recarga activa del Ingeniero",
    f"hud_editor.{NS}.element.engineer_ammo": "Bala especial y racha del Ingeniero",
    f"hud_editor.{NS}.element.artificer_charges": "Cargas del Artificiero",
    f"entity.{NS}.engineer_turret": "Torreta",
    f"entity.{NS}.engineer_mine": "Mina",
    f"entity.{NS}.thrown_firearm": "Arma lanzada",
    f"block.{NS}.barbed_wire": "Alambre de espino",
    f"death.attack.{NS}.engineer_bullet": "%1$s fue abatido por %2$s",
    f"death.attack.{NS}.engineer_bullet.player": "%1$s fue abatido por %2$s",
    f"death.attack.{NS}.engineer_blast": "%1$s voló por los aires",
    f"death.attack.{NS}.engineer_blast.player": "%1$s voló por los aires por culpa de %2$s",
    f"death.attack.{NS}.barbed_wire": "%1$s se desangró en el alambre de espino",
    f"death.attack.{NS}.barbed_wire.player": "%1$s se desangró en el alambre de espino de %2$s",
}


def build(dump, out_dir):
    data = os.path.join(out_dir, "data")
    assets = os.path.join(out_dir, "assets", NS)
    opt = lambda ids: [{"id": CGS + i, "required": False} for i in ids]
    dump(os.path.join(data, NS, "tags", "items", "firearm_ammo.json"), {"replace": False, "values": opt(AMMO)})
    dump(os.path.join(data, NS, "tags", "items", "firearms", "light.json"), {"replace": False, "values": opt(LIGHT_FIREARMS)})
    dump(os.path.join(data, NS, "tags", "items", "firearms", "heavy.json"), {"replace": False, "values": opt(HEAVY_FIREARMS)})
    # Tipos de daño de las balas propias (torretas, Abanico, Ojo Muerto) y de las explosiones (minas, Bombardeo).
    for t in ("engineer_bullet", "engineer_blast", "barbed_wire"):
        dump(os.path.join(data, NS, "damage_type", t + ".json"),
             {"exhaustion": 0.1, "message_id": f"{NS}.{t}", "scaling": "when_caused_by_living_non_player"})
    for tag, value in (("is_projectile", "engineer_bullet"), ("is_explosion", "engineer_blast")):
        path = os.path.join(data, "minecraft", "tags", "damage_type", tag + ".json")
        cur = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {"replace": False, "values": []}
        if R(value) not in cur["values"]: cur["values"].append(R(value))
        dump(path, cur)
    # Alambre de espino de la Trinchera: modelo en cruz con la textura de los barrotes de hierro; sin botín.
    dump(os.path.join(assets, "blockstates", "barbed_wire.json"), {"variants": {"": {"model": f"{NS}:block/barbed_wire"}}})
    dump(os.path.join(assets, "models", "block", "barbed_wire.json"),
         {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
          "textures": {"cross": "minecraft:block/chain", "particle": "minecraft:block/chain"}})
    dump(os.path.join(data, NS, "loot_tables", "blocks", "barbed_wire.json"), {"type": "minecraft:block", "pools": []})
    return dict(UI)
