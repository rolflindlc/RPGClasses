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
             milestones=[ms(0, "wild_shape"), ms(0, "beast_lore"), ms(5, "track_prey"), ms(15, "dominate_beast"),
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
TRAITS = [attr([(MC + "max_health", to(-.35, "multiply_base"))])]  # rev. 2.23: −35 % de vida

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
                       flight_speed=.03, form_min_speed=.5, form_max_speed=1.6,
                       health_level_scaling=.01),
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
    "minecraft:cave_spider": dict(health=12, damage=2, speed=1.35, passives=["climb"], hit_effects=[dict(effect="minecraft:poison", seconds=7, level=1)]),
    "minecraft:polar_bear": dict(health=30, damage=6, speed=1.15, attack_speed=1.2, ability="roar"),
    "minecraft:goat": dict(health=10, damage=2, speed=1.1, jump=.8, ability="ram"),
    "minecraft:hoglin": dict(health=40, damage=6, speed=1.0, attack_speed=1.0, knockback_resistance=.6, ability="toss"),
    "minecraft:ravager": dict(health=100, damage=12, speed=1.2, attack_speed=.8, knockback_resistance=.75, ability="stun_roar", passives=["leaf_breaker"]),
    "minecraft:horse": dict(health=22, damage=2, speed=1.6, jump=1.0),
    "minecraft:donkey": dict(health=22, damage=2, speed=1.4, jump=.8),
    "minecraft:mule": dict(health=22, damage=2, speed=1.4, jump=.8),
    "minecraft:skeleton_horse": dict(health=15, damage=2, speed=1.6, jump=1.0, amphibious=True),
    "minecraft:camel": dict(health=32, damage=2, speed=1.2, jump=.6, ability="dash"),
    "minecraft:llama": dict(health=22, damage=1, speed=1.0, ability="spit"),
    "minecraft:trader_llama": dict(health=22, damage=1, speed=1.0, ability="spit"),
    "minecraft:bee": dict(health=10, damage=2, speed=1.1, ability="sting", flying=True, hit_effects=[dict(effect="minecraft:poison", seconds=10, level=1)]),
    "minecraft:parrot": dict(health=6, damage=1, speed=1.0, flying=True),
    "minecraft:bat": dict(health=6, damage=1, speed=1.0, ability="echolocation", flying=True, passives=["night_vision"]),
    "minecraft:dolphin": dict(health=10, damage=3, speed=1.0, ability="water_dash", aquatic=True, passives=["dolphin_grace"]),
    "minecraft:turtle": dict(health=30, damage=1, speed=.5, ability="shell", amphibious=True),
    "minecraft:axolotl": dict(health=14, damage=2, speed=.8, ability="play_dead", amphibious=True),
    "minecraft:frog": dict(health=10, damage=2, speed=1.0, jump=.8, ability="tongue", amphibious=True),
    "minecraft:squid": dict(health=10, damage=1, ability="ink", aquatic=True),
    "minecraft:glow_squid": dict(health=10, damage=1, ability="ink", aquatic=True, passives=["night_vision"]),
    "minecraft:pufferfish": dict(health=3, damage=1, ability="inflate", aquatic=True, hit_effects=[], aura_effects=[]),
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
    "alexsmobs:alligator_snapping_turtle": dict(ability="jaw_lock", amphibious=True),
    "alexsmobs:anteater": dict(ability="tongue"),
    "alexsmobs:bald_eagle": dict(ability="dive", flying=True),
    "alexsmobs:banana_slug": dict(passives=["climb"]),
    "alexsmobs:bison": dict(ability="ram"),
    "alexsmobs:blobfish": dict(aquatic=True),
    "alexsmobs:blue_jay": dict(flying=True),
    "alexsmobs:bunfungus": dict(ability="super_jump"),
    "alexsmobs:cachalot_whale": dict(ability="whale_echo", aquatic=True),
    "alexsmobs:caiman": dict(ability="death_roll", amphibious=True),
    "alexsmobs:capuchin_monkey": dict(ability="dart"),
    "alexsmobs:catfish": dict(aquatic=True),
    "alexsmobs:cockroach": dict(),
    "alexsmobs:comb_jelly": dict(passives=["night_vision"], aquatic=True),
    "alexsmobs:cosmaw": dict(ability="ender_blink", flying=True),
    "alexsmobs:cosmic_cod": dict(flying=True),
    "alexsmobs:crocodile": dict(ability="death_roll", amphibious=True),
    "alexsmobs:crow": dict(flying=True),
    "alexsmobs:devils_hole_pupfish": dict(aquatic=True),
    "alexsmobs:dropbear": dict(ability="tree_drop", passives=["climb"]),
    "alexsmobs:elephant": dict(ability="stomp"),
    "alexsmobs:emu": dict(ability="dash", passives=["dodge"]),
    "alexsmobs:endergrade": dict(passives=["slow_fall"], flying=True),
    "alexsmobs:enderiophage": dict(ability="ender_blink", passives=["climb"]),
    "alexsmobs:flutter": dict(flying=True),
    "alexsmobs:fly": dict(flying=True),
    "alexsmobs:flying_fish": dict(ability="water_dash", passives=["glide"], aquatic=True),
    "alexsmobs:frilled_shark": dict(ability="water_dash", aquatic=True),
    "alexsmobs:froststalker": dict(ability="pounce", passives=["snow_walk"]),
    "alexsmobs:gazelle": dict(ability="dash"),
    "alexsmobs:gelada_monkey": dict(ability="troop_call"),
    "alexsmobs:giant_squid": dict(ability="tentacles", aquatic=True),
    "alexsmobs:gorilla": dict(ability="ground_pound"),
    "alexsmobs:grizzly_bear": dict(ability="roar"),
    "alexsmobs:hammerhead_shark": dict(ability="water_dash", aquatic=True),
    "alexsmobs:hummingbird": dict(flying=True),
    "alexsmobs:jerboa": dict(ability="super_jump"),
    "alexsmobs:kangaroo": dict(ability="kick"),
    "alexsmobs:komodo_dragon": dict(),
    "alexsmobs:laviathan": dict(passives=["lava_swim"]),
    "alexsmobs:leafcutter_ant": dict(),
    "alexsmobs:lobster": dict(ability="pinch", amphibious=True),
    "alexsmobs:maned_wolf": dict(ability="pounce"),
    "alexsmobs:mantis_shrimp": dict(ability="punch", aquatic=True),
    "alexsmobs:mimic_octopus": dict(ability="camouflage", aquatic=True),
    "alexsmobs:moose": dict(ability="ram"),
    "alexsmobs:mudskipper": dict(ability="mud_ball", amphibious=True),
    "alexsmobs:mungus": dict(),
    "alexsmobs:orca": dict(ability="water_dash", aquatic=True),
    "alexsmobs:platypus": dict(ability="electroreception", amphibious=True),
    "alexsmobs:potoo": dict(ability="camouflage", flying=True),
    "alexsmobs:raccoon": dict(),
    "alexsmobs:rain_frog": dict(),
    "alexsmobs:rattlesnake": dict(ability="rattle"),
    "alexsmobs:rhinoceros": dict(ability="rhino_charge"),
    "alexsmobs:roadrunner": dict(ability="sprint"),
    "alexsmobs:rocky_roller": dict(ability="roll"),
    "alexsmobs:seagull": dict(flying=True),
    "alexsmobs:seal": dict(ability="water_dash", amphibious=True),
    "alexsmobs:sea_bear": dict(ability="roar", aquatic=True),
    "alexsmobs:shoebill": dict(ability="peck"),
    "alexsmobs:skelewag": dict(ability="water_dash", aquatic=True),
    "alexsmobs:skreecher": dict(ability="skreech", passives=["climb"]),
    "alexsmobs:skunk": dict(ability="spray"),
    "alexsmobs:snow_leopard": dict(ability="leap", passives=["snow_walk", "no_fall"]),
    "alexsmobs:soul_vulture": dict(flying=True),
    "alexsmobs:spectre": dict(flying=True),
    "alexsmobs:stradpole": dict(passives=["lava_swim"]),
    "alexsmobs:straddler": dict(ability="harpoon", passives=["fire_immune", "lava_walk"]),
    "alexsmobs:sugar_glider": dict(passives=["glide", "climb"]),
    "alexsmobs:sunbird": dict(ability="sun_blessing", passives=["fire_immune"], flying=True),
    "alexsmobs:tarantula_hawk": dict(ability="paralysis", flying=True),
    "alexsmobs:tasmanian_devil": dict(ability="devil_screech"),
    "alexsmobs:terrapin": dict(ability="shell", amphibious=True),
    "alexsmobs:tiger": dict(ability="pounce"),
    "alexsmobs:toucan": dict(flying=True),
    "alexsmobs:triops": dict(aquatic=True),
    "alexsmobs:tusklin": dict(ability="toss"),
    "alexsmobs:crimson_mosquito": dict(ability="blood_drain", flying=True),
    "alexsmobs:warped_mosco": dict(ability="blood_drain", flying=True),
    "alexsmobs:warped_toad": dict(ability="tongue", passives=["fire_immune"]),
    "alexscaves:subterranodon": dict(passives=["no_fall"], flying=True),
    "alexscaves:vallumraptor": dict(ability="leap"),
    "alexscaves:grottoceratops": dict(ability="ram"),
    "alexscaves:relicheirus": dict(ability="claw_slash"),
    "alexscaves:tremorsaurus": dict(ability="stun_roar"),
    "alexscaves:atlatitan": dict(ability="big_stomp"),
    "alexscaves:raycat": dict(passives=["night_vision"]),
    "alexscaves:gammaroach": dict(),
    "alexscaves:brainiac": dict(ability="toxic_barrel"),
    "alexscaves:lanternfish": dict(aquatic=True),
    "alexscaves:radgill": dict(aquatic=True),
    "alexscaves:trilocaris": dict(aquatic=True),
    "alexscaves:tripodfish": dict(aquatic=True),
    "alexscaves:sea_pig": dict(aquatic=True),
    "alexscaves:sweetish_fish": dict(aquatic=True),
    "alexscaves:hullbreaker": dict(ability="abyssal_charge", aquatic=True),
    "alexscaves:gossamer_worm": dict(aquatic=True),
    "alexscaves:gloomoth": dict(flying=True),
    "alexscaves:corrodent": dict(ability="burrow"),
    "alexscaves:vesper": dict(ability="echolocation", passives=["night_vision"], flying=True),
    "alexscaves:forsaken": dict(ability="sonic_scream"),
    "alexscaves:candicorn": dict(ability="ram"),
    "alexscaves:gummy_bear": dict(),
    "alexscaves:caramel_cube": dict(),
    "mowziesmobs:frostmaw": dict(ability="ice_breath", passives=["snow_walk"]),
    "mowziesmobs:grottol": dict(ability="burrow"),
    "mowziesmobs:lantern": dict(flying=True),
    "mowziesmobs:naga": dict(ability="naga_spit", flying=True),
    "mowziesmobs:foliaath": dict(ability="camouflage", passives=["rooted"]),
    "mowziesmobs:baby_foliaath": dict(ability="camouflage", passives=["rooted"]),
    "mowziesmobs:elokosa_howler": dict(ability="howl", passives=["climb"]),
    "cataclysm:amethyst_crab": dict(ability="big_stomp"),
    "cataclysm:clawdian": dict(ability="big_pinch", amphibious=True),
    "cataclysm:lionfish": dict(ability="spine", aquatic=True),
    "cataclysm:urchinkin": dict(passives=["thorns"]),
    "cataclysm:symbiocto": dict(ability="ink", aquatic=True),
    "cataclysm:endermaptera": dict(ability="ender_blink", passives=["climb"]),
    "cataclysm:modern_remnant": dict(ability="roar"),
    "cataclysm:the_baby_leviathan": dict(ability="water_dash", aquatic=True),
    "cataclysm:hippocamtus": dict(ability="spear_charge", aquatic=True),
    "cataclysm:cindaria": dict(ability="jelly_shock", aquatic=True),
    "skarrier_mobs:bowlder": dict(ability="spin"),
    "skarrier_mobs:colossadon": dict(ability="big_stomp"),
    "skarrier_mobs:dangle": dict(ability="lasso"),
    "skarrier_mobs:gadlisk": dict(flying=True),
    "skarrier_mobs:glowin": dict(passives=["night_vision"]),
    "skarrier_mobs:hellamander": dict(ability="fire_breath", passives=["fire_immune"], amphibious=True),
    "skarrier_mobs:krill": dict(aquatic=True),
    "skarrier_mobs:quake": dict(ability="stomp"),
    "skarrier_mobs:roe": dict(flying=True),
    "skarrier_mobs:serene": dict(ability="tranquility", flying=True),
    "skarrier_mobs:slither": dict(),
    "skarrier_mobs:slither_matriarch": dict(ability="constrict"),
    "skarrier_mobs:trawler": dict(ability="ram"),
    "skarrier_mobs:vicemaw": dict(ability="bite_hold", aquatic=True),
    "skarrier_mobs:viruleon": dict(ability="tail_strike"),
    "skarrier_mobs:voidrake": dict(ability="void_breath", flying=True),
    "skarrier_mobs:wretch": dict(ability="roar"),
    "skarrier_mobs:eater": dict(ability="bite_hold", passives=["rooted"]),
    "skarrier_mobs:gorger": dict(ability="acid_spit", passives=["rooted"]),
    "skarrier_mobs:carniflore": dict(ability="bite_hold", passives=["rooted"]),
    "skarrier_mobs:snap": dict(ability="bite_hold", passives=["rooted"]),
    "irons_spellbooks:ice_spider": dict(ability="web", passives=["climb", "snow_walk"]),
    "traveloptics:snow_fox": dict(ability="leap", passives=["snow_walk", "night_vision"]),
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
    "dive": dict(type="leap", cooldown_seconds=8, range=16, lift=.5, stun_seconds=.75),
    "tree_drop": dict(type="leap", cooldown_seconds=10, range=10, lift=.7, stun_seconds=1),
    "rhino_charge": dict(type="ram", cooldown_seconds=12, distance=8, damage=8, knockback=3, sound="minecraft:entity.ravager.attack"),
    "roll": dict(type="ram", cooldown_seconds=8, distance=7, damage=5, knockback=1.5, sound="minecraft:block.stone.break"),
    "abyssal_charge": dict(type="ram", cooldown_seconds=14, distance=10, damage=10, knockback=3, follow_look=True, sound="minecraft:entity.guardian.attack"),
    "spear_charge": dict(type="ram", cooldown_seconds=10, distance=9, damage=7, knockback=2, follow_look=True, sound="minecraft:item.trident.riptide_1"),
    "sprint": dict(type="dash", cooldown_seconds=6, distance=12),
    "rattle": dict(type="roar", cooldown_seconds=15, radius=6, knockback=.3, effect="minecraft:slowness", effect_level=2, effect_seconds=5, sound="minecraft:entity.silverfish.ambient"),
    "devil_screech": dict(type="roar", cooldown_seconds=18, radius=6, knockback=.6, effect="minecraft:weakness", effect_level=2, effect_seconds=6, sound="minecraft:entity.fox.screech"),
    "skreech": dict(type="roar", cooldown_seconds=20, radius=8, knockback=.2, effect="minecraft:darkness", effect_seconds=6, sound="minecraft:entity.ghast.scream"),
    "paralysis": dict(type="sting", cooldown_seconds=10, range=3, damage=3, effect="minecraft:slowness", effect_level=4, effect_seconds=3),
    "electroreception": dict(type="echolocation", cooldown_seconds=15, radius=20, duration_seconds=6),
    "lasso": dict(type="tongue", cooldown_seconds=10, range=8, max_width=2.0, damage=3),
    "spray": dict(type="breath", cooldown_seconds=20, range=4, angle=40, duration_seconds=2, damage_per_second=0, effects=[dict(effect="minecraft:nausea", seconds=8, level=1), dict(effect="minecraft:blindness", seconds=3, level=1)], drop_aggro=True, particle="gas", sound="minecraft:entity.llama.spit"),
    "fire_breath": dict(type="breath", cooldown_seconds=14, range=5, angle=30, duration_seconds=3, damage_per_second=4, fire_seconds=4, particle="flame", sound="minecraft:entity.blaze.shoot"),
    "ice_breath": dict(type="breath", cooldown_seconds=16, range=7, angle=35, duration_seconds=3, damage_per_second=6, freeze_ticks=60, effects=[dict(effect="minecraft:slowness", seconds=3, level=3)], particle="snowflake", sound="minecraft:entity.player.hurt_freeze"),
    "void_breath": dict(type="breath", cooldown_seconds=16, range=6, angle=30, duration_seconds=3, damage_per_second=4, effects=[dict(effect="minecraft:wither", seconds=4, level=1)], particle="void", sound="minecraft:entity.ender_dragon.shoot"),
    "stomp": dict(type="slam", cooldown_seconds=12, radius=4, damage=6, lift=.5, slow_seconds=3, slow_level=2, sound="minecraft:entity.iron_golem.attack"),
    "big_stomp": dict(type="slam", cooldown_seconds=16, radius=6, damage=10, lift=.8, slow_seconds=3, slow_level=2, sound="minecraft:entity.ravager.stunned"),
    "ground_pound": dict(type="slam", cooldown_seconds=10, radius=3.5, damage=7, lift=.7, slow_seconds=2, slow_level=1, sound="minecraft:entity.ravager.attack"),
    "spin": dict(type="slam", cooldown_seconds=8, radius=3, damage=5, lift=.2, slow_seconds=0, sound="minecraft:entity.player.attack.sweep"),
    "kick": dict(type="strike", cooldown_seconds=8, range=3, damage=7, knockback=3),
    "pinch": dict(type="strike", cooldown_seconds=9, range=3, damage=7, knockback=.5, effects=[dict(effect="minecraft:slowness", seconds=3, level=2)], sound="minecraft:entity.player.attack.crit"),
    "big_pinch": dict(type="strike", cooldown_seconds=12, range=4, damage=12, knockback=1, effects=[dict(effect="minecraft:slowness", seconds=3, level=2)], sound="minecraft:entity.player.attack.crit"),
    "punch": dict(type="strike", cooldown_seconds=14, range=3, damage=10, knockback=2.5, armor_pierce=.5, sound="minecraft:entity.player.attack.crit"),
    "peck": dict(type="strike", cooldown_seconds=12, range=3, damage=5, knockback=.3, stun_seconds=1.5),
    "claw_slash": dict(type="strike", cooldown_seconds=10, range=4, damage=8, knockback=1, sound="minecraft:entity.player.attack.sweep"),
    "tail_strike": dict(type="strike", cooldown_seconds=10, range=4, damage=7, knockback=1, effects=[dict(effect="minecraft:poison", seconds=5, level=2)], sound="minecraft:entity.bee.sting"),
    "jaw_lock": dict(type="grapple", cooldown_seconds=16, range=3, seconds=3, damage_per_second=3),
    "death_roll": dict(type="grapple", cooldown_seconds=16, range=3, seconds=3, damage_per_second=4, sound="minecraft:entity.generic.splash"),
    "tentacles": dict(type="grapple", cooldown_seconds=18, range=6, seconds=4, damage_per_second=3, max_width=3, sound="minecraft:entity.squid.squirt"),
    "constrict": dict(type="grapple", cooldown_seconds=18, range=4, seconds=4, damage_per_second=5, max_width=3),
    "bite_hold": dict(type="grapple", cooldown_seconds=14, range=3, seconds=3, damage_per_second=3),
    "camouflage": dict(type="camouflage", cooldown_seconds=25, seconds=8),
    "ender_blink": dict(type="blink", cooldown_seconds=10, range=12),
    "burrow": dict(type="tunnel", cooldown_seconds=12, distance=6, max_hardness=3),
    "troop_call": dict(type="rally", cooldown_seconds=30, radius=10, effects=[dict(effect="minecraft:strength", seconds=10, level=1)], sound="minecraft:entity.evoker.celebrate"),
    "sun_blessing": dict(type="rally", cooldown_seconds=40, radius=10, effects=[dict(effect="minecraft:slow_falling", seconds=15, level=1), dict(effect="minecraft:regeneration", seconds=8, level=1)], particle="happy", sound="minecraft:block.amethyst_block.chime"),
    "tranquility": dict(type="rally", cooldown_seconds=35, radius=10, effects=[dict(effect="minecraft:regeneration", seconds=6, level=2)], particle="heart", sound="minecraft:block.amethyst_block.resonate"),
    "howl": dict(type="rally", cooldown_seconds=35, radius=12, effects=[dict(effect="minecraft:speed", seconds=10, level=1), dict(effect="minecraft:strength", seconds=10, level=1)], sound="minecraft:entity.wolf.howl"),
    "blood_drain": dict(type="drain", cooldown_seconds=8, range=3, damage=4, heal_ratio=.6),
    "dart": dict(type="throw", cooldown_seconds=4, damage=4, speed=1.8, item="minecraft:arrow"),
    "mud_ball": dict(type="throw", cooldown_seconds=5, damage=2, speed=1.3, item="alexsmobs:mud_ball", item_fallback="minecraft:mud", effects=[dict(effect="minecraft:slowness", seconds=3, level=2)]),
    "harpoon": dict(type="throw", cooldown_seconds=6, damage=5, speed=2.0, item="minecraft:trident"),
    "toxic_barrel": dict(type="throw", cooldown_seconds=10, damage=6, speed=1.0, item="alexscaves:waste_drum", item_fallback="minecraft:barrel", effects=[dict(effect="minecraft:poison", seconds=6, level=2), dict(effect="alexscaves:irradiated", seconds=10, level=1)]),
    "naga_spit": dict(type="throw", cooldown_seconds=6, damage=4, speed=1.6, item="minecraft:slime_ball", effects=[dict(effect="minecraft:poison", seconds=5, level=1)], sound="minecraft:entity.llama.spit"),
    "spine": dict(type="throw", cooldown_seconds=5, damage=3, speed=2.0, item="cataclysm:lionfish_spike", item_fallback="minecraft:pointed_dripstone", effects=[dict(effect="minecraft:poison", seconds=5, level=1)]),
    "acid_spit": dict(type="throw", cooldown_seconds=6, damage=5, speed=1.4, item="minecraft:slime_ball", effects=[dict(effect="minecraft:poison", seconds=4, level=2)], sound="minecraft:entity.llama.spit"),
    "whale_echo": dict(type="sonic", cooldown_seconds=22, range=20, width=1.5, damage=10, stun_seconds=2),
    "sonic_scream": dict(type="sonic", cooldown_seconds=20, range=16, width=1.2, damage=8, stun_seconds=1.5),
    "jelly_shock": dict(type="sonic", cooldown_seconds=12, range=10, width=1, damage=6, stun_seconds=1, particle="electric", sound="minecraft:entity.guardian.attack"),
}

# Bestias de otros grupos que valen (las automáticas son las de categoría de animal: CREATURE, AMBIENT, WATER_*,
# AXOLOTLS) y las que nunca valen (además de los jefes y los mobs de más vida que el máximo de beast_lore).
# Bestias de categoría "monstruo" que sí valen (el resto de monstruos no).
ALLOWED = [
    "minecraft:spider", "minecraft:cave_spider", "minecraft:ravager", "minecraft:hoglin",
    "alexsmobs:alligator_snapping_turtle", "alexsmobs:anteater", "alexsmobs:bald_eagle", "alexsmobs:banana_slug",
    "alexsmobs:bison", "alexsmobs:blobfish", "alexsmobs:blue_jay", "alexsmobs:bunfungus", "alexsmobs:cachalot_whale",
    "alexsmobs:caiman", "alexsmobs:capuchin_monkey", "alexsmobs:catfish", "alexsmobs:cockroach",
    "alexsmobs:comb_jelly", "alexsmobs:cosmaw", "alexsmobs:cosmic_cod", "alexsmobs:crocodile", "alexsmobs:crow",
    "alexsmobs:devils_hole_pupfish", "alexsmobs:dropbear", "alexsmobs:elephant", "alexsmobs:emu",
    "alexsmobs:endergrade", "alexsmobs:enderiophage", "alexsmobs:flutter", "alexsmobs:fly", "alexsmobs:flying_fish",
    "alexsmobs:frilled_shark", "alexsmobs:froststalker", "alexsmobs:gazelle", "alexsmobs:gelada_monkey",
    "alexsmobs:giant_squid", "alexsmobs:gorilla", "alexsmobs:grizzly_bear", "alexsmobs:hammerhead_shark",
    "alexsmobs:hummingbird", "alexsmobs:jerboa", "alexsmobs:kangaroo", "alexsmobs:komodo_dragon",
    "alexsmobs:laviathan", "alexsmobs:leafcutter_ant", "alexsmobs:lobster", "alexsmobs:maned_wolf",
    "alexsmobs:mantis_shrimp", "alexsmobs:mimic_octopus", "alexsmobs:moose", "alexsmobs:mudskipper",
    "alexsmobs:mungus", "alexsmobs:orca", "alexsmobs:platypus", "alexsmobs:potoo", "alexsmobs:raccoon",
    "alexsmobs:rain_frog", "alexsmobs:rattlesnake", "alexsmobs:rhinoceros", "alexsmobs:roadrunner",
    "alexsmobs:rocky_roller", "alexsmobs:seagull", "alexsmobs:seal", "alexsmobs:sea_bear", "alexsmobs:shoebill",
    "alexsmobs:skelewag", "alexsmobs:skreecher", "alexsmobs:skunk", "alexsmobs:snow_leopard",
    "alexsmobs:soul_vulture", "alexsmobs:spectre", "alexsmobs:stradpole", "alexsmobs:straddler",
    "alexsmobs:sugar_glider", "alexsmobs:sunbird", "alexsmobs:tarantula_hawk", "alexsmobs:tasmanian_devil",
    "alexsmobs:terrapin", "alexsmobs:tiger", "alexsmobs:toucan", "alexsmobs:triops", "alexsmobs:tusklin",
    "alexsmobs:crimson_mosquito", "alexsmobs:warped_mosco", "alexsmobs:warped_toad", "alexscaves:subterranodon",
    "alexscaves:vallumraptor", "alexscaves:grottoceratops", "alexscaves:relicheirus", "alexscaves:tremorsaurus",
    "alexscaves:atlatitan", "alexscaves:raycat", "alexscaves:gammaroach", "alexscaves:brainiac",
    "alexscaves:lanternfish", "alexscaves:radgill", "alexscaves:trilocaris", "alexscaves:tripodfish",
    "alexscaves:sea_pig", "alexscaves:sweetish_fish", "alexscaves:hullbreaker", "alexscaves:gossamer_worm",
    "alexscaves:gloomoth", "alexscaves:corrodent", "alexscaves:vesper", "alexscaves:forsaken", "alexscaves:candicorn",
    "alexscaves:gummy_bear", "alexscaves:caramel_cube", "mowziesmobs:frostmaw", "mowziesmobs:grottol",
    "mowziesmobs:lantern", "mowziesmobs:naga", "mowziesmobs:foliaath", "mowziesmobs:baby_foliaath",
    "mowziesmobs:elokosa_howler", "cataclysm:amethyst_crab", "cataclysm:clawdian", "cataclysm:lionfish",
    "cataclysm:urchinkin", "cataclysm:symbiocto", "cataclysm:endermaptera", "cataclysm:modern_remnant",
    "cataclysm:the_baby_leviathan", "cataclysm:hippocamtus", "cataclysm:cindaria", "skarrier_mobs:bowlder",
    "skarrier_mobs:colossadon", "skarrier_mobs:dangle", "skarrier_mobs:gadlisk", "skarrier_mobs:glowin",
    "skarrier_mobs:hellamander", "skarrier_mobs:krill", "skarrier_mobs:quake", "skarrier_mobs:roe",
    "skarrier_mobs:serene", "skarrier_mobs:slither", "skarrier_mobs:slither_matriarch", "skarrier_mobs:trawler",
    "skarrier_mobs:vicemaw", "skarrier_mobs:viruleon", "skarrier_mobs:voidrake", "skarrier_mobs:wretch",
    "skarrier_mobs:eater", "skarrier_mobs:gorger", "skarrier_mobs:carniflore", "skarrier_mobs:snap",
    "irons_spellbooks:ice_spider", "traveloptics:snow_fox",
]
# Criaturas pasivas que no son animales ni bestias, jefes y bestias de mods demasiado grandes.
FORBIDDEN = [
    "minecraft:allay", "minecraft:wandering_trader", "#forge:bosses", "iceandfire:fire_dragon",
    "iceandfire:ice_dragon", "iceandfire:lightning_dragon", "iceandfire:sea_serpent", "iceandfire:hydra",
    "alexsmobs:underminer", "alexsmobs:anaconda", "alexsmobs:centipede_head", "alexsmobs:centipede_body",
    "alexsmobs:centipede_tail", "alexsmobs:bone_serpent", "alexsmobs:void_worm", "alexsmobs:murmur",
    "alexsmobs:murmur_head", "alexsmobs:farseer", "alexsmobs:mimicube", "alexsmobs:guster", "alexscaves:tremorzilla",
    "alexscaves:luxtructosaurus", "alexscaves:gum_worm", "alexscaves:notor", "mowziesmobs:umvuthana_follower_player",
    "mowziesmobs:umvuthana_crane_player", "mowziesmobs:umvuthana_follower_raptor",
    "mowziesmobs:elokosa_follower_howler", "mowziesmobs:bluff", "cataclysm:netherite_ministrosity",
    "skarrier_mobs:istukan", "skarrier_mobs:stone_golem", "skarrier_mobs:carniflore_daytime_spawning_dummy",
    "skarrier_mobs:devourer_tunnel_segment_ore_gen_dummy", "skarrier_mobs:snap_trap", "skarrier_mobs:roe_summon",
    "skarrier_mobs:wisper_minion", "skarrier_mobs:healing_flesh", "skarrier_mobs:flore_head",
    "irons_spellbooks:priest", "irons_spellbooks:spectral_steed", "irons_spellbooks:summoned_polar_bear",
    "irons_spellbooks:summoned_vex", "traveloptics:summoned_atlatitan", "traveloptics:summoned_forsaken",
    "traveloptics:summoned_gum_worm", "traveloptics:summoned_tremorsaurus", "traveloptics:summoned_vallumraptor",
    "traveloptics:summoned_vesper", "traveloptics:summoned_void_tome", "traveloptics:void_tome",
    "traveloptics:summoned_hullbreaker",
]

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
    "moon_circle": "Druida del Círculo de la Luna: se transforma en un instante, casi sin recarga, y sus formas no le reservan maná. Al cambiar de una forma a otra conservas el mismo porcentaje de vida.",
    "supernatural_form": "Bestia que hace magia: puede lanzar conjuros de naturaleza mientras está transformado, aunque sus formas son algo más débiles y esos conjuros cuestan más.",
    "sculptor": "Rituales oscuros: arranca partes de los restos de las bestias (cabeza, torso, patas e instinto), las une en un Recipiente de Carne y, sobre un Altar de Carne, da vida a una quimera en la que puede transformarse. Cada creación le cuesta mucho maná y parte de su vida durante un rato.",
}
ABILITY_TEXT = {
    "wild_shape": ("Forma Salvaje", "Abre el bestiario y elige una especie que conozcas: tras unos segundos lanzando (recibir daño lo interrumpe) te conviertes en ella. Pagas maná según lo fuerte que sea la bestia y, mientras la mantienes, ese maná no se regenera. Tienes la vida, el tamaño, la velocidad y los ataques de la bestia, más fuertes cuanto más nivel tengas; tu inventario se guarda aparte hasta que vuelves (no recoges objetos ni abres contenedores), el clic derecho usa la habilidad de la bestia y no puedes lanzar conjuros, y tus rasgos de raza se suspenden. Si la bestia cae, vuelves a tu forma con la vida que tenías. Vuelve a pulsar (agachado, al instante) para volver a tu forma; la recarga empieza entonces. Al dormir, morir o desconectarte también vuelves."),
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
    "level_scaling": "Daño de la forma por Nivel Total|%", "mana_base": "Maná base de una forma",
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
    "health_level_scaling": "Vida de la forma por Nivel Total|%",
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
    "dive": ("Picado", "Te lanzas en picado sobre el enemigo al que miras y, al caer cerca, lo aturdes un instante."),
    "tree_drop": ("Caída del árbol", "Te dejas caer sobre el enemigo al que miras y lo aturdes al aterrizar."),
    "rhino_charge": ("Carga de rinoceronte", "Una embestida larga y brutal que lanza lejos al primer enemigo que golpeas."),
    "roll": ("Rodar", "Te haces una bola y ruedas hacia delante, golpeando al primer enemigo."),
    "abyssal_charge": ("Embestida abisal", "Te lanzas hacia donde miras (también bajo el agua) y golpeas con fuerza al primer enemigo."),
    "spear_charge": ("Carga de lanza", "Cargas hacia donde miras, también bajo el agua, y ensartas al primer enemigo."),
    "sprint": ("Carrera del correcaminos", "Un impulso larguísimo hacia delante."),
    "rattle": ("Cascabel", "Agitas el cascabel: los enemigos cercanos se ralentizan y retroceden un poco."),
    "devil_screech": ("Chillido del diablo", "Un chillido que debilita mucho a los enemigos cercanos."),
    "skreech": ("Chillido", "Un grito que deja en la oscuridad a los enemigos cercanos."),
    "paralysis": ("Parálisis", "Picas al enemigo que tienes delante y lo dejas casi paralizado."),
    "electroreception": ("Electrorrecepción", "Notas las criaturas cercanas: brillan unos segundos (solo para ti)."),
    "lasso": ("Lazo", "Atrapas con un lazo a una criatura y la acercas, aunque sea grande."),
    "spray": ("Rociada", "Rocías un gas apestoso delante: los enemigos se marean, se ciegan y dejan de perseguirte. No hace daño."),
    "fire_breath": ("Aliento de fuego", "Escupes fuego delante unos segundos: quema a los enemigos."),
    "ice_breath": ("Aliento helado", "Exhalas un viento helado delante: daña, ralentiza y congela."),
    "void_breath": ("Aliento del vacío", "Exhalas energía del vacío delante: daña y marchita."),
    "stomp": ("Pisotón", "Golpeas el suelo: dañas, levantas y ralentizas a los enemigos cercanos."),
    "big_stomp": ("Pisotón colosal", "Un pisotón enorme: daña, levanta y ralentiza a los enemigos en un área grande."),
    "ground_pound": ("Golpe al suelo", "Aporreas el suelo con los puños: los enemigos cercanos saltan por los aires."),
    "spin": ("Giro", "Giras sobre ti mismo y golpeas a todo lo que tienes alrededor."),
    "kick": ("Patada", "Una patada que manda lejos al enemigo que miras."),
    "pinch": ("Pinza", "Atenazas al enemigo que miras: daño y lo ralentizas."),
    "big_pinch": ("Gran pinza", "Un pinzazo enorme al enemigo que miras: mucho daño y lo ralentizas."),
    "punch": ("Puñetazo", "Un puñetazo devastador: ignora la mitad de la armadura y manda lejos al enemigo."),
    "peck": ("Picotazo", "Un picotazo seco que aturde al enemigo que miras."),
    "claw_slash": ("Zarpazo", "Un zarpazo con las garras al enemigo que miras."),
    "tail_strike": ("Coletazo", "Golpeas con la cola venenosa al enemigo que miras."),
    "jaw_lock": ("Mandíbula de cepo", "Atrapas al enemigo con la boca: no puede moverse y recibe daño cada segundo."),
    "death_roll": ("Giro de la muerte", "Atrapas al enemigo y giras con él: no puede moverse y recibe daño cada segundo."),
    "tentacles": ("Tentáculos", "Atrapas con los tentáculos a un enemigo algo lejano: no se mueve y recibe daño."),
    "constrict": ("Constricción", "Te enroscas en el enemigo: no puede moverse y recibe mucho daño cada segundo."),
    "bite_hold": ("Mordisco", "Atrapas al enemigo con un mordisco: no puede moverse y recibe daño cada segundo."),
    "camouflage": ("Camuflaje", "Te vuelves invisible y los enemigos te pierden. Atacar o usar otra habilidad lo rompe."),
    "ender_blink": ("Salto del End", "Te teletransportas al punto que miras."),
    "burrow": ("Excavar", "Te lanzas hacia delante abriendo un túnel en tierra, arena, grava y piedra."),
    "troop_call": ("Grito de la tropa", "Das fuerza a ti y a tus aliados cercanos."),
    "sun_blessing": ("Bendición del sol", "Tú y tus aliados cercanos caéis lento y regeneráis vida."),
    "tranquility": ("Tranquilidad", "Tú y tus aliados cercanos regeneráis vida rápido."),
    "howl": ("Aullido", "Tú y tus aliados cercanos ganáis velocidad y fuerza."),
    "blood_drain": ("Drenar sangre", "Muerdes al enemigo que miras y te curas parte del daño."),
    "dart": ("Dardo", "Lanzas un dardo."),
    "mud_ball": ("Bola de barro", "Lanzas una bola de barro que ralentiza."),
    "harpoon": ("Arpón", "Lanzas un arpón."),
    "toxic_barrel": ("Barril tóxico", "Lanzas un barril de residuos que envenena e irradia."),
    "naga_spit": ("Escupitajo de naga", "Escupes veneno a distancia."),
    "spine": ("Púa", "Disparas una púa venenosa."),
    "acid_spit": ("Ácido", "Escupes ácido que envenena."),
    "whale_echo": ("Eco de cachalote", "Una onda sónica enorme en línea recta: daña y aturde a todo lo que atraviesa."),
    "sonic_scream": ("Grito sónico", "Un grito en línea recta que daña y aturde a todo lo que atraviesa."),
    "jelly_shock": ("Descarga", "Una descarga en línea recta que daña y aturde."),
}
PASSIVE_NAMES = {
    "climb": "Trepa paredes", "poison_bite": "Mordisco venenoso", "no_fall": "Sin daño de caída",
    "scare_creepers": "Los creepers huyen", "night_vision": "Visión nocturna", "fire_immune": "Inmune al fuego",
    "lava_walk": "Camina sobre la lava", "slow_fall": "Caída lenta", "leaf_breaker": "Rompe hojas al andar",
    "dolphin_grace": "Gracia del delfín para los aliados", "fly": "Vuela", "swim": "Nada rápido y respira bajo el agua",
    "amphibious": "Respira bajo el agua",
    "glide": "Planea (agáchate para caer)",
    "dodge": "Esquiva proyectiles",
    "lava_swim": "Nada en lava",
    "thorns": "Púas",
    "snow_walk": "Anda sobre la nieve y no se congela",
    "rooted": "Arraigada: no se mueve",
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
    f"message.{NS}.druid.miniboss_level": "La forma de un minijefe pide nivel %s.",
    f"ui.{NS}.bestiary.miniboss": "Minijefe: forma desde el nivel %s",
    f"message.{NS}.druid.no_containers": "En forma de bestia no puedes usar contenedores.",
    f"commands.{NS}.bestiary.list": "Bestiario de %s: %s",
    f"commands.{NS}.bestiary.empty": "%s no conoce ninguna especie.",
    f"commands.{NS}.bestiary.added": "%s conoce ahora %s (%s/%s puntos).",
    f"commands.{NS}.bestiary.removed": "%s ha olvidado %s.",
    f"commands.{NS}.bestiary.unknown": "%s no tiene conocimiento de %s.",
    f"commands.{NS}.bestiary.not_beast": "%s no es una especie válida para el Druida.",
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
    dump(os.path.join(data, NS, "tags", "entity_types", "druid", "allowed.json"), {"replace": False, "values": [
        v if v.startswith("minecraft:") else {"id": v, "required": False} for v in ALLOWED]})
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
