# Clérigo (adenda del Clérigo, 19-11-2026): clase, ramas, perks, subclases, habilidades, tipos de daño y textos.
# Lo importa gen_data.py. Los ids deben coincidir con io.github.rpgclasses.cleric (Cleric.java).
# Las propuestas de la adenda sin confirmar se programan tal cual y quedan aquí, en datos.
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


# Reserva Divina = Reserva del Mago: +300 de maná máximo y +40 % de regeneración real (ver RESERVE_REGEN en gen_data.py).
RESERVE_REGEN = {"values": [round((1 + .04 * l) * 100 / (100 + 30 * l) - 1, 3) for l in range(1, 11)], "operation": "multiply_base"}

# ------------------------------------------------------------------ clase
CLASS = dict(icon="minecraft:beacon", roles=["apoyo", "curación"], kit="cleric", crit=0.0,
             # Adenda, 2.3: Magia −35 % (sin la sagrada), Arco −25 %, Pesadas y Ligeras −50 %, Armadura −25 %, Escudos −50 %. Total 235.
             walls=dict(magic=.35, bow=.25, heavy=.50, light=.50, armor=.25, shield=.50),
             branches=[
                 # Sanación: +5 % por punto a la curación y la absorción de tus conjuros (+50 % con 10).
                 ("healing", [mech("healing_power", heal_bonus={"to": .50})]),
                 # Bendición: +5 % por punto a la duración de los efectos beneficiosos de tus conjuros.
                 ("blessing", [mech("blessing_duration", duration_bonus={"to": .50})]),
                 # Reserva Divina: +300 de maná máximo y +40 % de regeneración de maná.
                 ("divine_reserve", [attr([(IR + "max_mana", to(300)), (IR + "mana_regen", RESERVE_REGEN)])]),
             ],
             milestones=[ms(5, "healer_eye"), ms(5, "laying_on_hands"), ms(15, "sanctuary"), ms(30, "miracle")],
             subs=["angel", "martyr", "intercessor"])

EXTRA_BRANCHES = [
    # Purificación: tus curas reducen un 5 % por punto la duración restante de los efectos negativos del curado.
    ("purification", [mech("purification", debuff_reduction={"to": .50})]),
    # Égida: el 5 % por punto de la curación sobrante pasa a absorción (tope 25 % de la vida máxima, 10 s; propuesta).
    ("aegis", [mech("aegis", conversion={"to": .50}, cap_health_fraction=.25, absorption_seconds=10)]),
]

# Rasgo base (adenda, 2.4): +10 % de vida máxima (22 HP).
TRAITS = [attr([(MC + "max_health", to(.10, "multiply_base"))])]

PERKS = {
    "healing": [("life_channel", [mech("life_channel", self_heal=-.75, recovery=.50, max_targets=3)]),
                ("own_faith", [mech("own_faith", self_heal=.50, others_heal=-.75)])],
    "blessing": [("greater_blessing", [mech("greater_blessing", extra_levels=1, max_amplifier=2, extra_mana_cost=.20, duration_change=-.25)]),
                 ("selfish_blessing", [mech("selfish_blessing", duration_change=.50)])],
    "divine_reserve": [("faith_return", [attr([(IR + "max_mana", to(-100))]), mech("faith_return", mana_refund=.15, icd_seconds=2)]),
                       ("blind_faith", [mech("blind_faith", out_of_combat_regen=.80, in_combat_regen=-.25, combat_seconds=8)])],
    "purification": [("absolution", [mech("absolution", icd_seconds=12, reduction_factor=.50)]),
                     ("penance", [mech("penance", radius=8, transfer=.50)])],
    "aegis": [("deep_aegis", [mech("deep_aegis", conversion_factor=-.75, cap_health_fraction=.50)]),
              ("swift_aegis", [mech("swift_aegis", conversion_factor=.50, cap_health_fraction=.15)])],
}

BRANCH_ICONS = {"healing": "minecraft:glistering_melon_slice", "blessing": "minecraft:golden_apple",
                "divine_reserve": "minecraft:lapis_block", "purification": "minecraft:water_bucket",
                "aegis": "minecraft:golden_chestplate"}

SUBS = {
    # Ángel: Gracia Alada (−10 % de daño de caída por nivel; caída lenta desde el nivel 5), Halo y Forma Angelical (nivel 5).
    "angel": ("minecraft:elytra", [
        mech("winged_grace", fall_reduction={"to": 1.0}, slow_fall_level=5),
        mech("halo", radius=8, ally_heal_per_second={"to": .5})],
        [ms(5, "angelic_form", True)]),
    # Mártir: solo Vida Entregada y Resolución, y Sacrificio en el nivel 5.
    "martyr": ("minecraft:redstone", [
        attr([(MC + "max_health", to(.10, "multiply_base"))]),
        mech("martyr_resolve", damage_reduction={"to": .20}, health_threshold=.50)],
        [ms(5, "sacrifice", True)]),
    # Intercesor: Plegaria desde el nivel 1, Voz Lejana y Cuerpo en Oración.
    "intercessor": ("minecraft:end_rod", [
        mech("far_voice", full_power_extra_blocks={"to": 10}, min_power={"from": .347, "to": .50}),
        mech("prayer_body", damage_reduction={"to": .40})],
        [ms(1, "prayer", True)]),
}

KIT = [{"item": IR + "copper_spell_book", "slot": "inventory", "spells": [{"id": IR + "heal", "level": 1}]}] + \
      [{"item": f"minecraft:leather_{p}", "slot": s} for p, s in
       (("helmet", "head"), ("chestplate", "chest"), ("leggings", "legs"), ("boots", "feet"))]

ABILITIES = {
    # Nivel Total 5.
    "healer_eye": dict(passive=True, range=32, low_health=.40),
    "laying_on_hands": dict(heal_fraction=.30, range=8, cooldown_seconds=40),
    # Nivel Total 15. Vida y regeneración × poder de hechizo × poder sagrado.
    "sanctuary": dict(radius=3, expand_seconds=.5, shield_health=50, shield_regen_per_second=5, max_channel_seconds=6,
                      channel_linger=.5, channel_mana_per_second=28, channel_speed=-.5, cooldown_seconds=40),
    # Nivel Total 30 (pasiva).
    "miracle": dict(passive=True, range=10, downed_seconds=10, revive_min_health=.25, revive_immunity_seconds=2,
                    ally_cooldown_seconds=90),
    # Subclases.
    "angelic_form": dict(duration_seconds=10, radius=10, regen_level=2, absorption_level=1, cooldown_seconds=120),
    "sacrifice": dict(cost_fraction=.15, heal_fraction=.20, range=12, cooldown_seconds=.5, final_missing_heal=1.0,
                      final_absorption=1.0, final_immunity_seconds=2, confirm_seconds=5),
    "prayer": dict(spirit_range=15, spirit_size=.9, full_power_blocks=10, min_power=.33, min_power_blocks=50, regen_level=1),
}

# ------------------------------------------------------------------ textos
CLASS_NAME = "Clérigo"
CLASS_DESC = ("Sanador sagrado: una especie de mago de curación que comparte la escuela sagrada con el Paladín. Su magia "
              "sagrada no tiene muro y el resto de escuelas pierde un 35 %. Es muy débil con armas, armaduras y escudos. "
              "Cura, alarga los buffs, purifica, protege con escudos de luz y salva del último golpe a sus aliados.")
TRAIT_DESC = "Rasgo de clase: algo más de vida máxima."
BRANCH_NAMES = {"healing": "Sanación", "blessing": "Bendición", "divine_reserve": "Reserva Divina",
                "purification": "Purificación", "aegis": "Égida"}
BRANCH_DESC = {
    "healing": "Tus conjuros curan más y dan más absorción (también los que te lanzas a ti). No afecta a las habilidades de clase.",
    "blessing": "Los efectos beneficiosos que das con tus conjuros duran más. No afecta a las habilidades de clase.",
    "divine_reserve": "Más maná máximo y más regeneración de maná.",
    "purification": "Tus curas acortan los efectos negativos que tenga el curado.",
    "aegis": "Parte de la curación que sobra (la que pasa de la vida máxima) se convierte en absorción durante unos segundos.",
}
PERK_TEXT = {
    ("healing", "life_channel"): ("Canal de Vida", "Te curas mucho menos con tus conjuros, pero recuperas la mitad de lo que curas a otros (hasta 3 por lanzamiento)."),
    ("healing", "own_faith"): ("Fe Propia", "Tus conjuros te curan mucho más a ti, pero mucho menos a los demás."),
    ("blessing", "greater_blessing"): ("Bendición Mayor", "Tus buffs suben un nivel (hasta III), pero cuestan más maná y duran algo menos."),
    ("blessing", "selfish_blessing"): ("Bendición Egoísta", "Tus buffs de área solo te afectan a ti, pero duran mucho más."),
    ("divine_reserve", "faith_return"): ("Retorno de Fe", "Menos maná máximo; al curar de verdad a otro recuperas parte del maná del conjuro."),
    ("divine_reserve", "blind_faith"): ("Fe Ciega", "Regeneras mucho más maná fuera de combate y algo menos en combate."),
    ("purification", "absolution"): ("Absolución", "La primera cura a cada aliado cada cierto tiempo le quita todos sus efectos negativos, pero la reducción de la rama es la mitad."),
    ("purification", "penance"): ("Penitencia", "Lo que acortas de un efecto negativo se lo pasas al enemigo más cercano, con la mitad de esa duración."),
    ("aegis", "deep_aegis"): ("Égida Profunda", "Conviertes mucho menos sobrante en absorción, pero el tope sube a la mitad de la vida máxima."),
    ("aegis", "swift_aegis"): ("Égida Veloz", "Conviertes más sobrante en absorción, pero el tope baja mucho."),
}
SUB_NAMES = {"angel": "Ángel", "martyr": "Mártir", "intercessor": "Intercesor"}
SUB_DESC = {
    "angel": "Alas de luz: menos daño de caída y caída lenta, un halo que regenera a tus aliados cercanos y, desde el nivel 5, la Forma Angelical: vuelas unos segundos mientras tú y tus aliados regeneráis y os purificáis.",
    "martyr": "Cura con su propia sangre: más vida, menos daño recibido cuando está malherido y, desde el nivel 5, el Sacrificio: pagas vida para curar a un aliado, sin recarga. Si ya no puedes pagar, puedes dar tu vida por él.",
    "intercessor": "Reza por un aliado aunque no lo vea: tu cuerpo se queda agachado y tú pasas a ser una cabeza flotante que lo acompaña. Desde ella lanzas tus conjuros, con menos poder cuanto más lejos esté tu cuerpo.",
}
ABILITY_TEXT = {
    "healer_eye": ("Ojo del Sanador", "Ves la vida de tus aliados cercanos sobre sus cabezas (también a través de las paredes). Los que bajan del 40 % brillan, y los tumbados por el Milagro muestran su cuenta atrás. Solo lo ves tú."),
    "laying_on_hands": ("Imposición de Manos", "Curas al aliado al que miras (cerca) un 30 % de su vida máxima, sin gastar maná. Escala con tu poder sagrado. Levanta a un aliado tumbado."),
    "sanctuary": ("Santuario", "Mantén la tecla: una esfera sagrada te rodea, empuja fuera a los enemigos y no deja pasar sus proyectiles (salvo los que tienen penetración). Bloquea el daño de área (explosiones, conjuros y nubes) a quien está dentro, que se lleva solo el escudo, y recibe los golpes de los enemigos que la tocan; se agrieta a medida que se debilita y acaba rompiéndose. Cuesta maná cada segundo; al soltar dura la mitad de lo que la mantuviste. Mientras la mantienes vas más lento y no lanzas conjuros."),
    "miracle": ("Milagro", "Si un aliado (también una mascota) iba a morir cerca de ti, queda tumbado unos segundos: no puede hacer nada ni recibir daño. Si otro jugador lo cura, se levanta; si no, muere. Cada aliado solo puede salvarse así cada cierto tiempo. No te salva a ti."),
    "angelic_form": ("Forma Angelical", "Durante unos segundos vuelas (sin correr en el aire). Al activarla se purgan tus efectos negativos y los de tus aliados cercanos, y todos recibís Regeneración II y Absorción I mientras dura. Al terminar no sufres daño de caída."),
    "sacrifice": ("Sacrificio", "Pagas parte de tu vida máxima y el aliado al que miras recupera parte de la suya (al menos lo que has pagado). Sin recarga. Si pagar te mataría, el icono se pone rojo: pulsa dos veces para dar tu vida: tu aliado recupera toda la vida que le falta, gana tantos corazones dorados como vida tenía y es invulnerable un instante. Su curación es verdadera: nada puede aumentarla ni reducirla."),
    "prayer": ("Plegaria", "Eliges a un aliado de la lista: tu cuerpo se queda agachado donde estás y tú apareces junto a tu aliado como una cabeza flotante que puede moverse a su alrededor (hasta 15 bloques). Tu aliado recibe Regeneración. Desde la cabeza lanzas tus conjuros, la Imposición de Manos y el Santuario, con menos poder cuanto más lejos esté tu cuerpo. Si golpean a tu cuerpo, el daño lo recibes tú; la cabeza no recibe daño. Vuelve a pulsar para cambiar de aliado o dejar de rezar."),
}
MECHANIC_NAMES = {
    "healing_power": "Sanación", "blessing_duration": "Bendición", "purification": "Purificación", "aegis": "Égida",
    "life_channel": "Canal de Vida", "own_faith": "Fe Propia", "greater_blessing": "Bendición Mayor",
    "selfish_blessing": "Bendición Egoísta", "faith_return": "Retorno de Fe", "blind_faith": "Fe Ciega",
    "absolution": "Absolución", "penance": "Penitencia", "deep_aegis": "Égida Profunda", "swift_aegis": "Égida Veloz",
    "winged_grace": "Gracia Alada", "halo": "Halo", "martyr_resolve": "Resolución", "far_voice": "Voz Lejana",
    "prayer_body": "Cuerpo en Oración",
}
PARAMS = {
    "heal_bonus": "Curación y absorción de tus conjuros|%", "duration_bonus": "Duración de tus buffs|%",
    "debuff_reduction": "Duración de los efectos negativos que quitas|%", "conversion": "Sobrante convertido en absorción|%",
    "absorption_seconds": "Duración de la absorción|s", "self_heal": "Curación sobre ti|%", "recovery": "Recuperas de lo que curas|%",
    "max_targets": "Objetivos que cuentan por lanzamiento", "others_heal": "Curación sobre otros|%",
    "extra_levels": "Niveles extra de tus buffs", "max_amplifier": "Nivel máximo (0 = I)", "extra_mana_cost": "Maná de tus buffs|%",
    "duration_change": "Duración de tus buffs|%", "mana_refund": "Maná del conjuro que recuperas|%",
    "out_of_combat_regen": "Regeneración de maná fuera de combate|%", "in_combat_regen": "Regeneración de maná en combate|%",
    "combat_seconds": "En combate durante|s", "reduction_factor": "Reducción de la rama x", "transfer": "Duración que pasas al enemigo|%",
    "conversion_factor": "Conversión|%", "fall_reduction": "Reducción del daño de caída|%", "slow_fall_level": "Caída lenta desde el nivel",
    "full_power_extra_blocks": "Distancia sin pérdida extra (bloques)",
    "min_power": "Poder mínimo a distancia|%", "low_health": "Brillan por debajo de|%", "expand_seconds": "Tiempo de expansión|s",
    "shield_health": "Vida del escudo", "shield_regen_per_second": "Regeneración del escudo por segundo",
    "max_channel_seconds": "Canalización máxima|s", "channel_linger": "Dura tras soltar (del tiempo canalizado)|%",
    "channel_mana_per_second": "Maná por segundo canalizando", "channel_speed": "Velocidad mientras canalizas|%",
    "downed_seconds": "Tiempo tumbado|s", "revive_min_health": "Vida mínima al levantarse|%",
    "revive_immunity_seconds": "Inmunidad al levantarse|s", "ally_cooldown_seconds": "Recarga por aliado|s",
    "regen_level": "Nivel de Regeneración", "absorption_level": "Nivel de Absorción", "cost_fraction": "Vida máxima que pagas|%",
    "final_missing_heal": "Vida que le falta que recupera (Sacrificio final)|%",
    "final_absorption": "Corazones dorados (de la vida que tenía)|%", "final_immunity_seconds": "Invulnerabilidad del Sacrificio final|s",
    "confirm_seconds": "Tiempo para confirmar|s", "spirit_range": "Distancia máxima al aliado (bloques)",
    "spirit_size": "Tamaño de la cabeza (bloques)",
    "full_power_blocks": "Poder completo hasta (bloques)", "min_power_blocks": "Poder mínimo desde (bloques)",
}

UI = {
    "entity.rites_and_paths.praying_body": "Cuerpo que reza",
    "effect.rites_and_paths.downed": "Tumbado",
    "effect.rites_and_paths.angelic_form": "Forma Angelical",
    "death.attack.rites_and_paths.martyrdom": "%1$s dio su vida por sus aliados",
    "death.attack.rites_and_paths.martyrdom.player": "%1$s dio su vida por sus aliados luchando contra %2$s",
    "message.rites_and_paths.cleric.no_target": "No miras a ningún aliado al alcance.",
    "message.rites_and_paths.cleric.full_health": "%s ya tiene la vida llena.",
    "message.rites_and_paths.cleric.no_mana": "No tienes maná suficiente.",
    "message.rites_and_paths.cleric.downed": "¡Has caído! Si un aliado te cura en %s s, te levantas.",
    "message.rites_and_paths.cleric.ally_downed": "¡%s ha caído! Cúrale en %s s.",
    "message.rites_and_paths.cleric.revived": "¡%s te ha levantado!",
    "message.rites_and_paths.cleric.revived_other": "Has levantado a %s.",
    "message.rites_and_paths.cleric.final_armed": "Sacrificio final: pulsa de nuevo para dar tu vida por %s.",
    "message.rites_and_paths.cleric.final_done": "%s ha dado su vida por ti.",
    "message.rites_and_paths.cleric.sanctuary_broken": "El Santuario se ha roto.",
    "message.rites_and_paths.cleric.prayer_none": "No hay aliados al alcance por los que rezar.",
    "message.rites_and_paths.cleric.prayer_start": "Rezas por %s.",
    "message.rites_and_paths.cleric.prayer_end": "Dejas de rezar.",
    "message.rites_and_paths.cleric.prayer_lost": "La plegaria se ha roto: %s ya no está a tu alcance.",
    "ui.rites_and_paths.prayer.title": "Plegaria: ¿por quién rezas?",
    "ui.rites_and_paths.prayer.stop": "Dejar de rezar",
    "ui.rites_and_paths.prayer.current": "%s (ahora)",
    "ui.rites_and_paths.prayer.distance": "%s (%s bloques)",
    "hud.rites_and_paths.downed": "Tumbado: %s s",
    "hud.rites_and_paths.sacrifice_final": "Sacrificio final",
}


def build(dump, out_dir):
    """Tipo de daño del Sacrificio final (muerte voluntaria: se salta el Tótem y las habilidades de supervivencia)."""
    data = os.path.join(out_dir, "data")
    dump(os.path.join(data, NS, "damage_type", "martyrdom.json"), {"message_id": f"{NS}.martyrdom", "exhaustion": 0.0, "scaling": "never"})
    for tag in ("bypasses_invulnerability", "bypasses_armor", "bypasses_effects", "bypasses_resistance", "bypasses_enchantments", "no_knockback"):
        path = os.path.join(data, "minecraft", "tags", "damage_type", tag + ".json")
        cur = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {"replace": False, "values": []}
        if R("martyrdom") not in cur["values"]: cur["values"].append(R("martyrdom"))
        dump(path, cur)
    # Daño de área que bloquea el Santuario (además de las explosiones y las zonas de Iron's).
    dump(os.path.join(data, NS, "tags", "damage_type", "sanctuary_area.json"), {"replace": False, "values": [
        "minecraft:magic", "minecraft:indirect_magic", "minecraft:dragon_breath", "minecraft:lightning_bolt",
        "minecraft:sonic_boom", "minecraft:fireworks"]})
    # Proyectiles de otros mods que atraviesan el Santuario como las flechas con Perforación (vacío por defecto).
    dump(os.path.join(data, NS, "tags", "entity_types", "sanctuary_piercing.json"), {"replace": False, "values": []})
    no_proc = os.path.join(data, NS, "tags", "damage_type", "no_proc.json")
    cur = json.load(open(no_proc, encoding="utf-8")) if os.path.exists(no_proc) else {"replace": False, "values": []}
    if R("martyrdom") not in cur["values"]: cur["values"].append(R("martyrdom"))
    dump(no_proc, cur)
    return dict(UI)
