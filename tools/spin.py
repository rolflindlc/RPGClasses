# Maestro del Giro (adenda del Maestro del Giro, 21-11-2026): clase experimental, ramas, perks, subclases, habilidades,
# objetos (bolas y bandolera), recetas, encantamientos, tipos de daño y textos.
# Lo importa gen_data.py. Los ids deben coincidir con io.github.rpgclasses.spin (SpinMaster.java, SphereMaterial.java).
# Las propuestas de la adenda sin confirmar se programan tal cual y quedan aquí, en datos. Todo es [MEDIR].
import json
import os

NS = "rites_and_paths"
MC = "minecraft:generic."


def R(x): return f"{NS}:{x}"
def to(v, op="add"): return {"to": v, "operation": op}
def attr(mods): return {"type": R("attribute"), "modifiers": [dict(attribute=a, **v) for a, v in mods]}
def mech(i, **p): return {"type": R("mechanic"), "id": R(i), **p}
def ms(level, grants, sub=False): return {("subclass_level" if sub else "total_level"): level, "grants": R(grants)}
def item(i): return {"item": i}
def tag(t): return {"tag": t}


CLASS_ID = "spin_master"

# ------------------------------------------------------------------ tiers y mejoras (deben coincidir con SphereMaterial.java
# y SphereUpgrade.java). Solo los tiers son objetos (en cruz con 5 unidades); las mejoras se le ponen a una bola de
# cualquier tier en la mesa de trabajo (SphereUpgradeRecipe) con unas unidades de su material.
# Tier: id, material de la receta, nombre, efecto (descripción corta)
TIERS = [
    ("iron", tag("forge:ingots/iron"), "Bola de hierro", "Empuje ligero."),
    ("diamond", tag("forge:gems/diamond"), "Bola de diamante", "Ignora parte de la armadura del objetivo."),
    ("netherite", tag("forge:ingots/netherite"), "Bola de netherita", "Quema y empuja fuerte. Resiste el fuego y la lava."),
]
TIER_IDS = [t[0] for t in TIERS]
# Mejora: id, nombre (tras «con»), efecto
UPGRADES = [
    ("slime", "slime", "Slime: rebota mucho más y casi no pierde velocidad."),
    ("copper", "cobre", "Cobre: salta a enemigos cercanos. Con tormenta, atrae rayos."),
    ("amethyst", "amatista", "Amatista: suelta una onda sónica en línea que atraviesa bloques y ciega."),
    ("ender_pearl", "perla de Ender", "Perla de Ender: al volver, te lleva al punto de impacto."),
    ("gold", "oro", "Oro: muy rápida y vuelve antes."),
    ("redstone", "redstone", "Redstone: activa la redstone que golpea."),
    ("dripstone", "estalactita", "Estalactita: rompe bloques blandos y te los trae."),
    ("flint", "pedernal", "Barrena (pedernal): menos daño por tick, pero se queda clavada mucho más tiempo y en total hace más daño."),
    ("obsidian", "obsidiana", "Mazazo (obsidiana): todo el daño de un solo golpe, sin quedarse clavada; es más lenta y tarda más en volver a estar lista."),
]
UPGRADE_IDS = [u[0] for u in UPGRADES]
MATERIAL_IDS = TIER_IDS + UPGRADE_IDS

# Multiplicador de daño de cada tier y de cada mejora (se multiplican: diamante con cobre = 1,25 × 1,0).
MATERIAL_DAMAGE = {"iron": 1.0, "diamond": 1.25, "netherite": 1.5, "slime": .70, "copper": 1.0, "amethyst": .85,
                   "ender_pearl": .80, "gold": .85, "redstone": .75, "dripstone": .90, "flint": 1.0, "obsidian": 1.0}

# ------------------------------------------------------------------ clase
CLASS = dict(icon=R("iron_sphere"), roles=["escaramuzador", "híbrido"], kit="spin_master", crit=0.0,
             # Adenda, 2: Magia −50 %, Arco −40 %, Pesadas −50 %, Ligeras −20 %, Armadura −30 %, Escudos −60 %. Total 250.
             walls=dict(magic=.50, bow=.40, heavy=.50, light=.20, armor=.30, shield=.60),
             branches=[
                 # Rotación: +3 % de ticks de daño (más pulsos en el mismo tiempo clavada; en los golpes únicos, +3 % de daño) y
                 # +3 % de ritmo de Giro por punto.
                 ("rotation", [mech("rotation", extra_ticks={"to": .30}, spin_rate={"to": .30})]),
                 # Rebote: +1 rebote con 5 puntos y +2 con 10; los rebotes en enemigos conservan del 53 % al 80 % del daño.
                 ("rebound", [mech("rebound", extra_bounces={"values": [0, 0, 0, 0, 1, 1, 1, 1, 1, 2]},
                                   hop_keep={"from": .53, "to": .80})]),
                 # Retorno: −4 % de cooldown de retorno por punto y +1 bola simultánea con 10 puntos.
                 ("sphere_return", [mech("sphere_return", return_reduction={"to": .40},
                                         extra_spheres={"values": [0, 0, 0, 0, 0, 0, 0, 0, 0, 1]})]),
             ],
             milestones=[ms(1, "sphere_throw"), ms(1, "spin_bar"), ms(5, "bounce_route"), ms(15, "reverse_spin"),
                         ms(15, "spin_step"), ms(30, "golden_rectangle")],
             subs=["spin_rider", "spin_scanner", "spin_trapper"])

EXTRA_BRANCHES = [
    # Escaneo: +1,5 puntos de crítico y +2 % de daño crítico por punto; los críticos con bola ignoran hasta el 15 % de armadura.
    ("scan", [attr([(R("crit_chance"), to(.15)), (R("crit_damage"), to(.20))]), mech("scan", crit_pierce={"to": .15})]),
    # Curva: curvatura del lanzamiento curvo +10 % y alcance +3 % por punto.
    ("curve", [mech("curve", curvature={"to": 1.0}, range_bonus={"to": .30})]),
]

# Rasgo base (adenda, 2): −10 % de vida máxima (18 HP).
TRAITS = [attr([(MC + "max_health", to(-.10, "multiply_base"))])]

PERKS = {
    "rotation": [("heavy_spin", [mech("heavy_spin", damage_bonus=.20, spin_rate=-.15)]),
                 ("swift_spin", [mech("swift_spin", spin_rate=.30, damage_bonus=-.10)])],
    "rebound": [("billiards", [mech("billiards", extra_bounces=1, chain_falloff=.15)]),
                ("dry_rebound", [mech("dry_rebound", extra_hops=2)])],
    "sphere_return": [("double_hand", [mech("double_hand", extra_spheres=1, return_change=.25)]),
                      ("swift_return", [mech("swift_return", extra_spheres=-1)])],
    "scan": [("cold_eye", [mech("cold_eye", icd_seconds=6), attr([(R("crit_chance"), to(-.10))])]),
             ("steady_eye", [mech("steady_eye", per_hit=.08, max_bonus=.32)])],
    "curve": [("tight_curve", [mech("tight_curve", curve_factor=2.0, range_change=-.20)]),
              ("guided_ball", [mech("guided_ball", speed_change=-.15)])],
}

BRANCH_ICONS = {"rotation": "minecraft:clock", "rebound": "minecraft:slime_block", "sphere_return": "minecraft:lead",
                "scan": "minecraft:spyglass", "curve": "minecraft:phantom_membrane"}

SUBS = {
    # Jinete: aturde al impactar a ≥70 % de la velocidad máxima (a pie o montado) y +1 % de daño por nivel a toda velocidad.
    "spin_rider": ("minecraft:saddle", [
        mech("spin_charge", speed_threshold=.70, stun_seconds={"from": .3, "to": 1.0}, boss_factor=.5, icd_seconds=8,
             speed_damage={"to": .10})], []),
    # Escáner: Punto Débil (6 s) y +2 % de daño crítico por nivel; en el nivel 5 la marca la ven los aliados y se renueva.
    "spin_scanner": ("minecraft:spyglass", [
        mech("weak_point", duration_seconds=6), attr([(R("crit_damage"), to(.20))])],
        [ms(5, "shared_weak_point", True)]),
    # Trampero de Giro: minas clavadas (agachado), Lentitud II 2 s; 2 minas (4 en el nivel 10). El daño de la mina es el
    # 110 % de un impacto (impacto de 1 s, 110 % de la espada): lo mismo que el original (150 % de un impacto del 80 %).
    "spin_trapper": ("minecraft:tripwire_hook", [
        mech("spin_mine", mine_seconds=30, trigger_radius=2, mine_damage=1.1, slow_seconds=2, slow_level=2,
             max_mines={"values": [2, 2, 2, 2, 2, 3, 3, 3, 3, 4]}, pickup_range=3)], []),
}

KIT = [{"item": R("sphere_bandolier"), "slot": "mainhand"}, {"item": R("iron_sphere"), "count": 3, "slot": "bandolier"}] + \
      [{"item": f"minecraft:leather_{p}", "slot": s} for p, s in
       (("helmet", "head"), ("chestplate", "chest"), ("leggings", "legs"), ("boots", "feet"))]

ABILITIES = {
    # Nivel Total 1 (pasivas que guardan los números base; funcionan desde el nivel 0).
    # Impacto: la bola se queda clavada 1 s con un pulso de daño cada 0,25 s (4 pulsos; más con Rotación). Daño total del
    # impacto de la bola de hierro: 110 % de la espada de hierro (era 80 % en 3 tics; cada lanzamiento tarda ahora ~1,6
    # veces más en volver, así que el daño por segundo queda en ~85 % del original). Clic izquierdo: guiado hacia la mira
    # (8°/tic), +15 % de alcance y −10 % de velocidad. Clic derecho: carga en 1 s hasta ×2 de alcance, ×2 de tiempo
    # clavada (2 s) y ×1,5 de daño. Barrena: ×2,5 de tiempo clavada a ×0,6 por tick (×1,5 en total). Mazazo: todo de un
    # golpe, −20 % de velocidad y +150 % de cooldown de retorno (deja su daño por segundo parecido al normal).
    "sphere_throw": dict(passive=True, sword_damage=6, iron_fraction=.32, spin_seconds=1.0, pulse_seconds=.25, hit_stop_ticks=3,
                         sphere_speed=1.3, sphere_range=16, aim_degrees=10, aimed_speed_change=-.10, aimed_range_change=.15,
                         full_charge_seconds=1.0, charge_range=1.5, charge_armor_ignore=.50, charge_guard_seconds=4, charge_slow=-.35, muscle_charge_seconds=2.0,
                         guard_break_armor=-.25, impact_knockback=.15,
                         return_speed=2.2, return_cooldown_seconds=.35, base_bounces=1, bounce_speed_keep=.85,
                         throw_interval_seconds=.25, base_spheres=1, extra_sphere_level=15, bounce_bonus_cap=5,
                         range_bonus_cap=1.0, hop_range=8, base_hop_keep=.50, curve_degrees=4, kinetic_per_bounce=.04,
                         kinetic_max=.20, catch_window_seconds=.25, catch_spin=.06, catch_icd_seconds=1.5,
                         **{f"damage_{m}": MATERIAL_DAMAGE[m] for m in MATERIAL_IDS},
                         iron_knockback=.10, diamond_armor=.20, netherite_burn_seconds=3, netherite_knockback=.25,
                         slime_bounces=3, slime_speed_keep=.98, copper_targets=2, copper_fraction=.40, copper_range=6,
                         copper_lightning_chance=.25, amethyst_length=6, amethyst_fraction=.50, amethyst_blind_seconds=1,
                         ender_return_change=.50, gold_speed=.40, gold_return_change=-.30, redstone_signal_ticks=3,
                         redstone_signal_ticks_spin=20, dripstone_blocks=3, dripstone_hardness=3,
                         drill_time=2.5, drill_tick_damage=.6, crush_speed_change=-.20, crush_return_change=1.5, crush_knockback=.5),
    # Con 2 s clavada los impactos llegan más espaciados: +5 % por impacto y la cadena aguanta 5 s entre impactos.
    "spin_bar": dict(passive=True, spin_gain=.05, chain_seconds=5, chain_step=.25, chain_max=2.0, miss_loss=.50,
                     decay_delay_seconds=5, decay_per_second=.05, threshold_1=.33, threshold_2=.66, threshold_1_bounces=1,
                     threshold_2_material=1.5, threshold_2_damage=.10),
    # Nivel Total 5. Tiro de billar: 4 golpes de enemigo en enemigo (el primero, el de la mira); los intermedios al 60 %
    # de un impacto, +15 % por cada golpe anterior; en el último se clava con ese bonus. Con un solo enemigo, rebota a
    # su alrededor y vuelve a por él.
    "bounce_route": dict(route_hits=4, route_range=16, route_hop=10, route_cone_degrees=70, bank_distance=3, hit_factor=.6,
                         per_hit_bonus=.15, route_speed=2.6, cooldown_seconds=10),
    # Nivel Total 15 (dos habilidades en el mismo hito).
    "reverse_spin": dict(pull_blocks=4, stuck_window_seconds=1.5, cooldown_seconds=14),
    "spin_step": dict(dash_speed=1.1, dash_lift=.25, step_spin=.06, step_window_seconds=1.5, dash_lock_seconds=.5,
                      cooldown_seconds=12),
    # Nivel Total 30 (ulti): secuencia automática de ~5,7 s. Daño (de la espada de hierro, con los bonus de daño de
    # Rotación): 14 pulsos del 40 % y golpe final crítico del 300 % al objetivo (~8,6 espadas); la rotación se propaga
    # al 150 % a hasta 6 enemigos a 12 bloques.
    "golden_rectangle": dict(target_range=24, freeze_radius=20, pulse_damage=.12, final_damage=.9, spread_targets=6,
                             spread_radius=12, spread_damage=.45, cooldown_seconds=120),
    # Escáner, nivel de subclase 5.
    "shared_weak_point": dict(passive=True, visible_range=32),
}

# ------------------------------------------------------------------ textos (castellano, texto fuente)
CLASS_NAME = "Maestro del Giro"
CLASS_DESC = ("Lanza bolas de distintos materiales que se clavan girando, rebotan y vuelven a la mano: clic izquierdo, "
              "guiada hacia donde apuntas; clic derecho, curva y se puede cargar; la bandolera guarda las bolas y una tecla "
              "cambia la que usas. Escaramuzador híbrido: golpea de cerca y de lejos y premia el ritmo, el combo y la "
              "precisión. Cada impacto encadenado sube su Giro, que potencia las bolas y carga el Rectángulo Dorado.")
TRAIT_DESC = "Rasgo de clase: algo menos de vida máxima."
BRANCH_NAMES = {"rotation": "Rotación", "rebound": "Rebote", "sphere_return": "Retorno", "scan": "Escaneo", "curve": "Curva"}
BRANCH_DESC = {
    "rotation": "Tus bolas hacen más ticks de daño mientras giran clavadas (en los golpes únicos, más daño) y tu Giro sube más rápido.",
    "rebound": "Más rebotes, y las bolas que rebotan de un enemigo a otro conservan más daño.",
    "sphere_return": "Tus bolas tardan menos en estar listas al volver y, con la rama llena, puedes tener una más en vuelo.",
    "scan": "Más probabilidad y daño de crítico; tus críticos con bola ignoran parte de la armadura.",
    "curve": "El lanzamiento curvo (clic derecho) se curva más y tus bolas llegan más lejos.",
}
PERK_TEXT = {
    ("rotation", "heavy_spin"): ("Giro Pesado", "Tus bolas hacen más daño mientras giran clavadas, pero el Giro sube algo más despacio."),
    ("rotation", "swift_spin"): ("Giro Veloz", "El Giro sube mucho más rápido, pero tus bolas hacen algo menos de daño."),
    ("rebound", "billiards"): ("Billar", "Un rebote más, pero cada rebote encadenado hace menos daño que el anterior."),
    ("rebound", "dry_rebound"): ("Rebote Seco", "Tus bolas ya no rebotan en los bloques, pero saltan a dos enemigos más."),
    ("sphere_return", "double_hand"): ("Doble Mano", "Una bola más en vuelo a la vez, pero tardan más en estar listas al volver."),
    ("sphere_return", "swift_return"): ("Retorno Veloz", "Si impactan, tus bolas vuelven a la mano al instante, pero tienes una bola menos en vuelo (mínimo una)."),
    ("scan", "cold_eye"): ("Ojo Frío", "El primer impacto a cada objetivo es crítico seguro (cada cierto tiempo por objetivo), pero bajas tu probabilidad de crítico general."),
    ("scan", "steady_eye"): ("Ojo Constante", "Cada impacto seguido al mismo objetivo hace más daño; se pierde al cambiar de objetivo."),
    ("curve", "tight_curve"): ("Curva Cerrada", "El lanzamiento curvo se curva el doble, pero tus bolas llegan menos lejos."),
    ("curve", "guided_ball"): ("Bola Guiada", "Mientras una bola vuela, un clic derecho la redirige una vez hacia donde miras al instante (en vez de cargar otra). Tus bolas van algo más lentas."),
}
SUB_NAMES = {"spin_rider": "Jinete", "spin_scanner": "Escáner", "spin_trapper": "Trampero de Giro"}
SUB_DESC = {
    "spin_rider": "Gira a toda velocidad: si tus bolas impactan mientras corres (o cabalgas) cerca de tu velocidad máxima, aturden al objetivo y hacen algo más de daño.",
    "spin_scanner": "Lee los puntos débiles: la primera bola que impacta a un objetivo lo marca y las siguientes son críticos seguros. Desde el nivel 5, la marca la ven tus aliados y se renueva con cada impacto.",
    "spin_trapper": "Clava sus bolas como minas: agachado, la bola se queda clavada en el suelo o en una pared y explota cuando pasa un enemigo cerca, frenándolo.",
}
ABILITY_TEXT = {
    "sphere_throw": ("Lanzamiento de Esferas", "Con la bandolera en la mano: clic izquierdo, la bola va guiada hacia donde apuntas (llega más lejos, pero algo más lenta); clic derecho, curva hacia donde miras, y si lo mantienes se carga (mientras cargas vas más lento): la bola cargada es un rompeguardias, ignora parte de la armadura según la carga y llega algo más lejos; con la carga completa desactiva el escudo del objetivo y le deja la Guardia Rota (menos armadura) unos segundos. Si la sigues manteniendo hasta sobrecargarla, al impactar el giro mueve los músculos del objetivo: usa a la fuerza lo que lleva en la mano (ataca con el arma o los puños, tensa el arco hasta disparar, carga la ballesta, se come la comida, lanza el conjuro de su bastón) y no puede parar hasta terminar. Los jefes no se ven afectados. La bola se clava y gira 1 segundo en el objetivo, dañándolo con varios ticks (se salta su invulnerabilidad); cada tick pierde contra la armadura lo mismo que perdería el impacto entero de un solo golpe. Cada rebote, en un bloque o al terminar de girar en un enemigo, la manda hacia donde mira tu cámara: el siguiente enemigo lo apuntas tú. Sin rebotes, vuelve a la mano. Pulsar justo cuando llega es una Captura Perfecta: más Giro y la relanzas al instante. Cada bloque rebotado antes del impacto le suma daño."),
    "spin_bar": ("Giro", "Barra de 0 a 100 % que sube con cada impacto, más si los encadenas. Si una bola vuelve sin impactar a ningún enemigo, pierdes la mitad; fuera de combate baja poco a poco. Con el 33 %: un rebote más y estelas doradas. Con el 66 %: el efecto del material es más fuerte y algo más de daño. Con el 100 %: puedes usar el Rectángulo Dorado (desde el Nivel Total 30)."),
    "bounce_route": ("Ruta de Rebotes", "Tiro de billar: tu bola sale disparada hacia el enemigo al que apuntas y salta de enemigo en enemigo, golpeando a cada uno de pasada (cada golpe pega más que el anterior), hasta clavarse en el último. Con un solo enemigo, rebota a su alrededor y vuelve a por él. Cada golpe sube tu Giro."),
    "reverse_spin": ("Giro Reverso", "Tira de tu bola clavada (o del último enemigo que golpeaste hace muy poco) y lo arrastra hacia ti. Los jefes no se mueven. Detona tus minas."),
    "spin_step": ("Paso de Giro", "Impulso en espiral de unos 6 bloques hacia donde te mueves (o hacia donde miras si estás quieto). Tu siguiente impacto poco después da más Giro."),
    "golden_rectangle": ("Rectángulo Dorado", "Con el Giro al 100 %, una secuencia automática con cámara de cine: el tiempo se detiene a tu alrededor, escaneas al enemigo al que apuntas y le lanzas una bola dorada que lo retiene en una rotación infinita, dañándolo sin parar; aunque intente escapar, la rotación lo arrastra de vuelta. Un último golpe crítico lo hunde contra el suelo y la rotación salta a los enemigos cercanos. Mientras dura no puedes moverte, pero no recibes daño."),
    "shared_weak_point": ("Punto Débil Compartido", "Tus aliados también ven las marcas de Punto Débil y cada impacto las renueva."),
}
MECHANIC_NAMES = {
    "rotation": "Rotación", "rebound": "Rebote", "sphere_return": "Retorno", "scan": "Escaneo", "curve": "Curva",
    "heavy_spin": "Giro Pesado", "swift_spin": "Giro Veloz", "billiards": "Billar", "dry_rebound": "Rebote Seco",
    "double_hand": "Doble Mano", "swift_return": "Retorno Veloz", "cold_eye": "Ojo Frío", "steady_eye": "Ojo Constante",
    "tight_curve": "Curva Cerrada", "guided_ball": "Bola Guiada", "spin_charge": "Carga de Giro", "weak_point": "Punto Débil",
    "spin_mine": "Minas Clavadas",
}
PARAMS = {
    "spin_rate": "Ritmo de ganancia de Giro|%", "extra_bounces": "Rebotes extra", "hop_keep": "Daño que conserva al rebotar en enemigos|%",
    "return_reduction": "Cooldown de retorno menos|%", "extra_spheres": "Bolas simultáneas extra", "crit_pierce": "Armadura que ignoran tus críticos|%",
    "curvature": "Curvatura extra|%", "range_bonus": "Alcance extra|%", "chain_falloff": "Daño menos por rebote encadenado|%",
    "extra_hops": "Enemigos extra a los que salta", "return_change": "Cambio del cooldown de retorno|%",
    "max_bonus": "Bonus máximo|%", "curve_factor": "Curvatura x", "range_change": "Cambio de alcance|%",
    "speed_change": "Cambio de velocidad de la bola|%", "speed_threshold": "Velocidad mínima (de la máxima)|%",
    "speed_damage": "Daño extra a toda velocidad|%", "mine_seconds": "Duración de la mina|s", "trigger_radius": "Radio de activación (bloques)",
    "mine_damage": "Daño de la mina x", "slow_level": "Nivel de Lentitud",
    "max_mines": "Minas a la vez", "pickup_range": "Distancia para recogerla (bloques)",
    "sword_damage": "Daño de referencia (espada de hierro)", "iron_fraction": "Daño de un impacto de la bola de hierro (de la espada)|%",
    "spin_seconds": "Tiempo clavada girando|s", "pulse_seconds": "Daño cada|s", "hit_stop_ticks": "Micro-pausa al impactar (tics)",
    "sphere_speed": "Velocidad (bloques por tic)", "sphere_range": "Alcance (bloques)",
    "aim_degrees": "Clic izquierdo: guía hacia la mira (grados por tic)", "aimed_speed_change": "Clic izquierdo: velocidad|%",
    "aimed_range_change": "Clic izquierdo: alcance|%", "full_charge_seconds": "Clic derecho: tiempo de carga completa|s",
    "charge_range": "Clic derecho cargado: alcance x", "charge_armor_ignore": "Clic derecho cargado: armadura ignorada|%",
    "charge_guard_seconds": "Clic derecho cargado: Guardia Rota y escudo desactivado|s",
    "charge_slow": "Clic derecho: velocidad mientras cargas|%", "guard_break_armor": "Guardia Rota: armadura y dureza|%",
    "impact_knockback": "Empuje del impacto", "muscle_charge_seconds": "Clic derecho: sobrecarga para el giro muscular|s",
    "return_speed": "Velocidad de vuelta (bloques por tic)", "return_cooldown_seconds": "Cooldown de retorno|s",
    "base_bounces": "Rebotes base", "bounce_speed_keep": "Velocidad que conserva al rebotar|%",
    "throw_interval_seconds": "Tiempo mínimo entre lanzamientos|s", "base_spheres": "Bolas simultáneas base",
    "extra_sphere_level": "Bola extra en el Nivel Total", "bounce_bonus_cap": "Tope de rebotes extra (ramas y encantamientos)",
    "range_bonus_cap": "Tope de alcance extra|%", "hop_range": "Alcance mínimo de cada rebote en un enemigo (bloques)",
    "base_hop_keep": "Daño al rebotar en enemigos (sin la rama)|%", "curve_degrees": "Curva del lanzamiento curvo (grados por tic)",
    "kinetic_per_bounce": "Rebote cinético: daño por bloque rebotado|%", "kinetic_max": "Rebote cinético: máximo|%",
    "catch_window_seconds": "Ventana de Captura Perfecta|s", "catch_spin": "Giro de la Captura Perfecta|%",
    "catch_icd_seconds": "Tiempo mínimo entre Capturas Perfectas|s",
    "damage_iron": "Daño de la bola de hierro x", "damage_diamond": "Daño de la bola de diamante x",
    "damage_netherite": "Daño de la bola de netherita x", "damage_slime": "Mejora de slime: daño x",
    "damage_copper": "Mejora de cobre: daño x", "damage_amethyst": "Mejora de amatista: daño x",
    "damage_ender_pearl": "Mejora de perla de Ender: daño x", "damage_gold": "Mejora de oro: daño x",
    "damage_redstone": "Mejora de redstone: daño x", "damage_dripstone": "Mejora de estalactita: daño x",
    "damage_flint": "Barrena: daño x", "damage_obsidian": "Mazazo: daño x",
    "drill_time": "Barrena: tiempo clavada x", "drill_tick_damage": "Barrena: daño por tick x",
    "crush_speed_change": "Mazazo: velocidad|%", "crush_return_change": "Mazazo: cooldown de retorno|%",
    "crush_knockback": "Mazazo: empuje", "extra_ticks": "Ticks de daño extra|%",
    "iron_knockback": "Hierro: empuje", "diamond_armor": "Diamante: armadura ignorada|%",
    "netherite_burn_seconds": "Netherita: quemadura|s", "netherite_knockback": "Netherita: empuje",
    "slime_bounces": "Slime: rebotes extra", "slime_speed_keep": "Slime: velocidad que conserva al rebotar|%",
    "copper_targets": "Cobre: enemigos a los que salta", "copper_fraction": "Cobre: daño del salto|%",
    "copper_range": "Cobre: distancia del salto (bloques)", "copper_lightning_chance": "Cobre: probabilidad de rayo con tormenta|%",
    "amethyst_length": "Amatista: largo de la onda (bloques)", "amethyst_fraction": "Amatista: daño de la onda|%",
    "amethyst_blind_seconds": "Amatista: ceguera|s", "ender_return_change": "Perla de Ender: cooldown de retorno|%",
    "gold_speed": "Oro: velocidad extra|%", "gold_return_change": "Oro: cooldown de retorno|%",
    "redstone_signal_ticks": "Redstone: tics de señal", "redstone_signal_ticks_spin": "Redstone: tics de señal con Giro II",
    "dripstone_blocks": "Estalactita: bloques por lanzamiento", "dripstone_hardness": "Estalactita: dureza máxima",
    "spin_gain": "Giro por impacto|%", "chain_seconds": "Cadena: tiempo entre impactos|s", "chain_step": "Cadena: multiplicador por eslabón|%",
    "chain_max": "Cadena: multiplicador máximo x", "miss_loss": "Giro que pierdes al fallar|%",
    "threshold_1": "Giro I desde|%", "threshold_2": "Giro II desde|%", "threshold_1_bounces": "Giro I: rebotes extra",
    "threshold_2_material": "Giro II: efecto del material x", "threshold_2_damage": "Giro II: daño extra|%",
    "route_hits": "Golpes de la ruta", "route_range": "Alcance del primer objetivo (bloques)",
    "route_hop": "Distancia entre enemigos de la ruta (bloques)", "route_cone_degrees": "Cono de la mira (grados)",
    "bank_distance": "Rebote junto a un enemigo solo (bloques)", "hit_factor": "Golpe de pasada (de un impacto)|%",
    "per_hit_bonus": "Daño extra por cada golpe anterior|%", "route_speed": "Velocidad en la ruta (bloques por tic)",
    "pull_blocks": "Arrastre (bloques)", "stuck_window_seconds": "Tiempo que sigue clavada la bola|s",
    "dash_lift": "Impulso hacia arriba", "step_spin": "Giro extra del siguiente impacto|%",
    "step_window_seconds": "Tiempo para aprovecharlo|s", "target_range": "Alcance para elegir objetivo (bloques)",
    "freeze_radius": "Radio en que se detiene el tiempo (bloques)", "pulse_damage": "Daño de cada pulso de la rotación (de la espada)|%",
    "final_damage": "Golpe final (de la espada)|%", "spread_targets": "Enemigos a los que se propaga",
    "spread_radius": "Alcance de la propagación (bloques)", "spread_damage": "Daño de la propagación (de la espada)|%",
    "visible_range": "Distancia a la que lo ven tus aliados (bloques)",
}

ENCHANTMENTS = {
    "friction": ("Fricción", "Tus bolas prenden fuego a los enemigos y a los bloques inflamables que golpean."),
    "density": ("Densidad", "Más daño, menos alcance."),
    "lightness": ("Ligereza", "Más alcance, menos daño."),
    "magnet": ("Imán", "Tus bolas están antes listas al volver, pero hacen algo menos de daño."),
    "claw": ("Garra", "La bola se queda más tiempo clavada y Giro Reverso arrastra más, pero vuela algo más despacio."),
    "elasticity": ("Elasticidad", "Más rebotes, pero cada rebote hace algo menos de daño."),
}

UI = {
    f"entity.{NS}.spin_sphere": "Bola",
    f"block.{NS}.sphere_signal": "Señal de bola",
    f"effect.{NS}.golden_spin": "Giro Dorado",
    f"effect.{NS}.guard_break": "Guardia Rota",
    f"message.{NS}.spin.muscle": "¡El giro mueve tus músculos solos!",
    f"item.{NS}.sphere_bandolier": "Bandolera de Esferas",
    f"discipline.{NS}.spheres.name": "Esferas",
    f"death.attack.{NS}.sphere": "%1$s cayó ante una bola que giraba",
    f"death.attack.{NS}.sphere.player": "%1$s cayó ante la bola de %2$s",
    f"death.attack.{NS}.sphere_effect": "%1$s fue alcanzado por el giro de una bola",
    f"death.attack.{NS}.sphere_effect.player": "%1$s fue alcanzado por el giro de la bola de %2$s",
    f"key.{NS}.ability_4": "Habilidad 4",
    f"key.{NS}.cycle_sphere": "Cambiar de bola (Maestro del Giro)",
    f"ui.{NS}.experimental.section": "Experimentales",
    f"ui.{NS}.experimental.badge": "Clase experimental",
    f"ui.{NS}.experimental.note": "Su balance no está garantizado y puede cambiar en cualquier versión.",
    f"ui.{NS}.experimental.warning": "Atención: es una clase experimental. Su balance no está garantizado y puede cambiar en cualquier versión.",
    f"ui.{NS}.wall_fixed_line": "%s: -%s fijo (desde que eliges la clase)",
    f"tooltip.{NS}.sphere.damage": "Daño: x%s",
    f"tooltip.{NS}.sphere.only_class": "Solo el Maestro del Giro sabe lanzarla, desde la bandolera.",
    f"tooltip.{NS}.sphere.upgrade_hint": "Mejórala en la mesa de trabajo con unidades de un material (por ejemplo, 4 lingotes de cobre).",
    f"item.{NS}.sphere_upgraded": "%s con %s",
    f"tooltip.{NS}.bandolier.selected": "Bola elegida: %s",
    f"tooltip.{NS}.bandolier.count": "%s/%s bolas",
    f"tooltip.{NS}.bandolier.empty": "Vacía",
    f"tooltip.{NS}.bandolier.help": "Clic derecho con una bola encima para guardarla; clic derecho sobre un hueco para sacar una.",
    f"tooltip.{NS}.bandolier.controls": "En la mano: clic izquierdo, guiada; clic derecho (mantén para cargar), curva; %s: cambiar de bola.",
    f"hud.{NS}.spin": "Giro",
    f"hud.{NS}.spin.ready": "¡Rectángulo Dorado listo! (%s)",
    f"hud.{NS}.sphere": "%s: %s/%s",
    f"message.{NS}.spin.not_spin_master": "Solo el Maestro del Giro sabe lanzar estas bolas.",
    f"message.{NS}.spin.empty": "Tu bandolera está vacía.",
    f"message.{NS}.spin.no_ready": "%s: ninguna lista ahora mismo.",
    f"message.{NS}.spin.limit": "Ya tienes %s bolas en vuelo.",
    f"message.{NS}.spin.selected": "Bola: %s",
    f"message.{NS}.spin.broken": "Se ha roto: %s.",
    f"message.{NS}.spin.need_full": "Necesitas el Giro al 100 %%.",
    f"message.{NS}.spin.nothing_stuck": "No tienes ninguna bola clavada ni has golpeado a nadie hace poco.",
    f"message.{NS}.spin.mine_limit": "Ya tienes %s minas clavadas.",
    f"message.{NS}.spin.mine_picked": "Recoges la mina.",
    f"message.{NS}.spin.no_targets": "No hay ningún enemigo a la vista.",
    f"message.{NS}.spin.perfect": "¡Captura Perfecta!",
    f"message.{NS}.spin.golden": "¡Rectángulo Dorado!",
}


def recipes():
    out = {}
    for m, ing, _, _ in TIERS:
        out[f"{m}_sphere"] = {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": [" M ", "MMM", " M "],
                              "key": {"M": ing}, "result": {"item": R(f"{m}_sphere"), "count": 1}}
    # Mejora de diamante a netherita en la mesa de herrería, como el resto del equipo (propuesta; la receta en cruz sigue).
    out["netherite_sphere_smithing"] = {"type": "minecraft:smithing_transform",
                                        "template": item("minecraft:netherite_upgrade_smithing_template"),
                                        "base": item(R("diamond_sphere")), "addition": tag("forge:ingots/netherite"),
                                        "result": {"item": R("netherite_sphere")}}
    out["sphere_bandolier"] = {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["LSL", "L L", "LIL"],
                               "key": {"L": item("minecraft:leather"), "S": item("minecraft:string"), "I": tag("forge:ingots/iron")},
                               "result": {"item": R("sphere_bandolier"), "count": 1}}
    # Mejoras de bola (receta especial: bola + unidades de un material; ver SphereUpgradeRecipe).
    out["sphere_upgrade"] = {"type": R("sphere_upgrade"), "category": "equipment"}
    return out


# Transformaciones en mano de las bolas (modelo voxel dibujado por SphereItemRenderer, centrado en el bloque).
SPHERE_DISPLAY = {
    "gui": {"rotation": [25, 35, 0], "translation": [0, 0, 0], "scale": [1.15, 1.15, 1.15]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.6, 0.6, 0.6]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 10, 0], "scale": [1, 1, 1]},
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2.5, 2], "scale": [0.7, 0.7, 0.7]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 2.5, 2], "scale": [0.7, 0.7, 0.7]},
    "firstperson_righthand": {"rotation": [0, -20, 0], "translation": [1.5, 3, -1], "scale": [0.8, 0.8, 0.8]},
    "firstperson_lefthand": {"rotation": [0, 20, 0], "translation": [1.5, 3, -1], "scale": [0.8, 0.8, 0.8]},
}


# La bandolera: en la mano, la bola elegida (mismas transformaciones); en el inventario, el suelo y los marcos, su icono
# plano (como un objeto normal: el código dibuja item/sphere_bandolier_icon sin transformación propia).
BANDOLIER_DISPLAY = dict(SPHERE_DISPLAY,
                         gui={"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
                         ground={"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
                         fixed={"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
                         head={"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]})


def build(dump, out_dir):
    data = os.path.join(out_dir, "data")
    assets = os.path.join(out_dir, "assets", NS)
    for name, recipe in recipes().items():
        dump(os.path.join(data, NS, "recipes", "spin", name + ".json"), recipe)
    # Modelos: bolas y bandolera los dibuja el código (núcleo voxel con un anillo que gira); el icono plano de la
    # bandolera y la textura de cada bola los usa ese dibujo.
    for m in TIER_IDS:
        dump(os.path.join(assets, "models", "item", f"{m}_sphere.json"),
             {"parent": "minecraft:builtin/entity", "gui_light": "side", "textures": {"particle": f"{NS}:item/sphere/{m}"},
              "display": SPHERE_DISPLAY})
    dump(os.path.join(assets, "models", "item", "sphere_bandolier.json"),
         {"parent": "minecraft:builtin/entity", "gui_light": "front", "textures": {"particle": f"{NS}:item/sphere_bandolier"},
          "display": BANDOLIER_DISPLAY})
    # Icono plano de la bandolera (lo dibuja el código en el inventario, en el suelo y en marcos).
    dump(os.path.join(assets, "models", "item", "sphere_bandolier_icon.json"),
         {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/sphere_bandolier"}})
    # Bloque invisible de la señal de la bola de redstone (no tiene modelo visible).
    dump(os.path.join(assets, "blockstates", "sphere_signal.json"),
         {"variants": {f"facing={d}": {"model": f"{NS}:block/sphere_signal"} for d in ("down", "up", "north", "south", "west", "east")}})
    dump(os.path.join(assets, "models", "block", "sphere_signal.json"), {"textures": {"particle": "minecraft:block/redstone_block"}})
    # Tipos de daño: el de la bola (pasa por el pipeline: muro de Esferas, crítico) y el de sus efectos (cobre, amatista: no).
    for d in ("sphere", "sphere_effect"):
        dump(os.path.join(data, NS, "damage_type", d + ".json"), {"message_id": f"{NS}.{d}", "exhaustion": 0.1, "scaling": "never"})
    # Sin el tag minecraft:is_projectile a propósito: con él, los enderman esquivarían siempre las bolas (como las flechas).
    # Sin empuje vanilla en cada tic del giro (el empuje del material lo pone el código al terminar el giro).
    nk = os.path.join(data, "minecraft", "tags", "damage_type", "no_knockback.json")
    cur = json.load(open(nk, encoding="utf-8")) if os.path.exists(nk) else {"replace": False, "values": []}
    for d in ("sphere", "sphere_effect"):
        if R(d) not in cur["values"]: cur["values"].append(R(d))
    dump(nk, cur)
    no_proc = os.path.join(data, NS, "tags", "damage_type", "no_proc.json")
    cur = json.load(open(no_proc, encoding="utf-8")) if os.path.exists(no_proc) else {"replace": False, "values": []}
    if R("sphere_effect") not in cur["values"]: cur["values"].append(R("sphere_effect"))
    dump(no_proc, cur)
    # Bloques que rompe la bola de estalactita (dureza máxima aparte, en sphere_throw). Respeta las protecciones de terreno.
    dump(os.path.join(data, NS, "tags", "blocks", "stalactite_breakable.json"), {"replace": False, "values": [
        "#minecraft:base_stone_overworld", "#minecraft:dirt", "#minecraft:sand", "#minecraft:coal_ores", "#minecraft:iron_ores",
        "#minecraft:copper_ores", "#minecraft:gold_ores", "#minecraft:redstone_ores", "#minecraft:lapis_ores",
        "#minecraft:diamond_ores", "#minecraft:emerald_ores", "minecraft:gravel", "minecraft:clay", "minecraft:ice",
        "minecraft:packed_ice", "minecraft:snow_block", "minecraft:cobblestone", "minecraft:mossy_cobblestone",
        "minecraft:sandstone", "minecraft:red_sandstone", "minecraft:terracotta", "minecraft:calcite", "minecraft:dripstone_block",
        "minecraft:pointed_dripstone", "minecraft:netherrack", "minecraft:nether_quartz_ore", "minecraft:nether_gold_ore",
        "minecraft:glowstone", "minecraft:soul_sand", "minecraft:soul_soil", "minecraft:end_stone", "minecraft:glass",
        "minecraft:amethyst_cluster", "minecraft:budding_amethyst", "minecraft:amethyst_block"]})
    # Todas las bolas (para datapacks y otros mods).
    dump(os.path.join(data, NS, "tags", "items", "spheres.json"), {"replace": False, "values": [R(f"{m}_sphere") for m in TIER_IDS]})
    lang = dict(UI)
    for m, _, name, effect in TIERS:
        lang[f"item.{NS}.{m}_sphere"] = name
        lang[f"tooltip.{NS}.sphere.effect.{m}"] = effect
    for u, name, effect in UPGRADES:
        lang[f"upgrade.{NS}.{u}"] = name
        lang[f"tooltip.{NS}.sphere.effect.{u}"] = effect
    for e, (name, desc) in ENCHANTMENTS.items():
        lang[f"enchantment.{NS}.{e}"] = name
        lang[f"enchantment.{NS}.{e}.desc"] = desc
    return lang
