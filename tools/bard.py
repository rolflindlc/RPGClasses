# Bardo (adenda del Bardo, rev. 0.1): clase, ramas, perks, subclases, habilidades, objetos, recetas y textos.
# Lo importa gen_data.py. Los ids deben coincidir con io.github.rpgclasses.bard (Instrument, MusicSpellInfo, BardItems).
# Las propuestas de la adenda sin confirmar se programan tal cual y quedan aquí, en datos.
import os

NS = "rites_and_paths"
IR = "irons_spellbooks:"
MC = "minecraft:generic."
IRONS_COND = [{"type": "forge:mod_loaded", "modid": "irons_spellbooks"}]
IAF_COND = [{"type": "forge:mod_loaded", "modid": "iceandfire"}]
MUSIC = f"{NS}:music"


def R(x): return f"{NS}:{x}"
def to(v, op="add"): return {"to": v, "operation": op}
def attr(mods): return {"type": R("attribute"), "modifiers": [dict(attribute=a, **v) for a, v in mods]}
def mech(i, **p): return {"type": R("mechanic"), "id": R(i), **p}
def ms(level, grants, sub=False): return {("subclass_level" if sub else "total_level"): level, "grants": R(grants)}
def opt(i): return {"id": i, "required": False}


# ------------------------------------------------------------------ clase
CLASS = dict(icon=R("lute"), roles=["apoyo", "híbrido"], kit="bard", crit=0.0,
             # Muros (adenda, sección 3): Magia y Ligeras decididos; Arco, Pesadas, Armadura y Escudos (propuesta). Total 245.
             walls=dict(magic=.35, bow=.60, heavy=.50, light=.15, armor=.30, shield=.55),
             branches=[
                 # Compás: poder de la escuela musical (+3 % por punto, +30 % con 10).
                 ("compas", [mech("music_power", music_bonus={"to": .30})]),
                 # Resonancia: radio de las auras de 8 a 12 bloques.
                 ("resonance", [mech("aura_radius", extra_blocks={"to": 4})]),
                 # Oído Absoluto: ventana de acierto +40 %; un fallo quita un cuarto del combo con 10 puntos (sin la rama, la mitad).
                 ("perfect_pitch", [mech("timing_window", window_bonus={"to": .40}, miss_keep={"from": .525, "to": .75})]),
             ],
             milestones=[ms(1, "rhythm"), ms(5, "harmony"), ms(15, "counterpoint"), ms(30, "crescendo")],
             subs=["war_chanter", "enchanter", "minstrel"])

EXTRA_BRANCHES = [
    # Reverberación: los efectos de las auras siguen 6 s tras salir del aura o tras terminar la canción.
    ("reverberation", [mech("aura_linger", linger_seconds={"to": 6})]),
    # Danza: sin ralentización al tocar (desde el primer punto) y +15 % de velocidad mientras suena una canción tuya.
    ("dance", [mech("bard_dance", speed_bonus={"to": .15})]),
]

# Rasgo base (propuesta): −10 % de vida máxima (18 HP).
TRAITS = [attr([(MC + "max_health", to(-.10, "multiply_base"))])]

PERKS = {
    "compas": [("soloist", [mech("soloist", radius=12, alone_bonus=.20, crowd_penalty=-.10)]),
               ("chorus", [mech("chorus", radius=9, per_ally=.08, chorus_cap=4, alone_penalty=-.12)])],
    "resonance": [("far_echo", [mech("aura_shape", radius_factor=.40, potency_factor=-.15)]),
                  ("inner_circle", [mech("aura_shape", radius_factor=-.30, potency_factor=.25)])],
    "perfect_pitch": [("tempo_presto", [mech("tempo_presto", forgiven_per_phrase=1, tempo_bonus=.25)]),
                      ("tempo_largo", [mech("tempo_largo", tempo_bonus=-.20, window_bonus=.15, power_penalty=-.10)])],
    "reverberation": [("trail", [mech("trail", linger_seconds=10, linger_potency=.70)]),
                      ("last_note", [mech("last_note", radius=4, sonic_damage=2, knockback_strength=1.0)])],
    "dance": [("shared_step", [mech("shared_step", ally_share=.50, self_penalty=-.30)]),
              ("perfect_march", [mech("perfect_march", speed_bonus=.25, min_stage=1)])],
}

SUBS = {
    # Cantor de Guerra: +1 % de daño por acierto con armas ligeras y pesadas (tope +30 %, sumado al muro) y Golpe Resonante.
    "war_chanter": ("minecraft:goat_horn", [
        mech("martial_combo", per_hit=.01, cap=.30),
        mech("resonant_strike", every_hits=5, radius=3, damage_fraction={"from": .30, "to": 1.0})],
        [ms(5, "rhythmic_fury", True)]),
    # Encantador: los efectos de control duran +5 % por nivel; Contagio del baile; Hechizar en el nivel 5.
    "enchanter": ("minecraft:amethyst_cluster", [
        mech("hypnotic_voice", control_duration={"to": .50}),
        mech("dance_contagion", radius=3, chance=.50, max_extra=5)],
        [ms(5, "bewitch", True)]),
    # Juglar: +1 ranura en los niveles 3, 6 y 9; −1 de combo mínimo cada 2 niveles; Recital en el nivel 5.
    "minstrel": ("minecraft:jukebox", [
        mech("extended_repertoire", extra_slots={"values": [0, 0, 1, 1, 1, 2, 2, 2, 3, 3]}),
        mech("fine_ear", combo_reduction={"values": [0, 1, 1, 2, 2, 3, 3, 4, 4, 5]})],
        [ms(5, "recital", True)]),
}

KIT = [{"item": R("lute"), "slot": "offhand"}, {"item": "minecraft:iron_sword", "slot": "mainhand"}] + \
      [{"item": f"minecraft:leather_{p}", "slot": s} for p, s in
       (("helmet", "head"), ("chestplate", "chest"), ("leggings", "legs"), ("boots", "feet"))]

ABILITIES = {
    # Ritmo (nivel 1): el minijuego y el combo. Poder musical por acierto según el Nivel Total (1 / 15 / 30).
    "rhythm": dict(passive=True, max_combo=30, power_per_hit=.01, power_per_hit_15=.02, power_per_hit_30=.03,
                   beat_bonus=.15, base_bpm=90, max_bpm=140, tempo_per_combo=.01, miss_keep=.5, miss_grace_seconds=2, window_ms=140, perfect_ms=70,
                   music_power_cap=1.5, buff_power_scaling=.5, play_slow=-.10, aura_radius=8,
                   idle_reset_seconds=3, aura_refresh_seconds=1.25),
    # Armonía (nivel 5): etapas por frase perfecta.
    "harmony": dict(passive=True, max_stage=5, buff_per_stage=.06, stage_loss=2),
    # Contrapunto (nivel 15): al cruzar 10, 20 y 30 de combo, los conjuros en recarga pierden el 50 % de lo que les queda.
    "counterpoint": dict(passive=True, refund=.5),
    # Crescendo (nivel 30, activa).
    "crescendo": dict(cooldown_seconds=90, duration_seconds=8, hit_multiplier=2),
    # Subclases (hito del nivel 5).
    "rhythmic_fury": dict(passive=True, min_stage=3, damage_bonus=.10),
    "bewitch": dict(passive=True, duration_seconds=6),
    "recital": dict(passive=True, distinct_spells=3, window_seconds=12, cooldown_cut=.5, icd_seconds=12),
}

# ------------------------------------------------------------------ textos de clase
CLASS_NAME = "Bardo"
CLASS_DESC = ("Mitad mago, mitad luchador, que combate tocando: mientras lleva un instrumento juega un minijuego de ritmo "
              "con clic izquierdo, clic derecho, Shift y espacio (cada carril sigue haciendo lo suyo). Acertar notas sube el combo, "
              "que da poder a la escuela musical, propia del Bardo. Sus conjuros van en partituras dentro del instrumento "
              "(mano secundaria) y se lanzan con el clic derecho: casi todos son auras para sus aliados.")
TRAIT_DESC = "Rasgo de clase: algo menos de vida máxima."
BRANCH_NAMES = {"compas": "Compás", "resonance": "Resonancia", "perfect_pitch": "Oído Absoluto",
                "reverberation": "Reverberación", "dance": "Danza"}
BRANCH_DESC = {
    "compas": "Aumenta el poder de la escuela musical.",
    "resonance": "Aumenta el radio de tus auras.",
    "perfect_pitch": "Ventana de acierto más amplia y, con más puntos, un fallo te quita menos combo (hasta solo un cuarto).",
    "reverberation": "Los efectos de tus auras siguen unos segundos después de salir de ellas o de que termine la canción.",
    "dance": "Tocar ya no te ralentiza, y corres más mientras suena una de tus canciones (cuenta para el tope de velocidad).",
}
PERK_TEXT = {
    ("compas", "soloist"): ("Solista", "Más poder musical si no hay aliados cerca; algo menos si los hay."),
    ("compas", "chorus"): ("Coro", "Más poder musical por cada aliado cercano (hasta 4); menos si tocas solo."),
    ("resonance", "far_echo"): ("Eco Lejano", "Tus auras llegan mucho más lejos, pero sus efectos son algo más débiles."),
    ("resonance", "inner_circle"): ("Círculo Íntimo", "Tus auras abarcan menos, pero sus efectos son bastante más fuertes."),
    ("perfect_pitch", "tempo_presto"): ("Tempo Presto", "Una pulsación de más por frase no rompe la Armonía, pero el tempo base es más rápido."),
    ("perfect_pitch", "tempo_largo"): ("Tempo Largo", "Tempo más lento (también su subida por combo) y ventana de acierto aún mayor, a cambio de algo de poder musical."),
    ("reverberation", "trail"): ("Estela", "Los efectos persisten más tiempo tras el aura, pero durante la persistencia son más débiles."),
    ("reverberation", "last_note"): ("Última Nota", "Al terminar un aura suelta una onda que empuja y hace un poco de daño sónico a los enemigos cercanos."),
    ("dance", "shared_step"): ("Paso Compartido", "Tus aliados dentro del aura reciben la mitad de tu bonus de velocidad de Danza; tú recibes algo menos."),
    ("dance", "perfect_march"): ("Marcha Perfecta", "El bonus de velocidad de Danza es mayor, pero solo con la Armonía en marcha."),
}
SUB_NAMES = {"war_chanter": "Cantor de Guerra", "enchanter": "Encantador", "minstrel": "Juglar"}
SUB_DESC = {
    "war_chanter": "Bardo de primera línea. Cada nota acertada suma daño con armas ligeras y pesadas (sobre tus muros), cada quinto golpe a compás suelta una onda sónica y, con la Armonía alta, golpeas aún más fuerte.",
    "enchanter": "Maestro del control. Tus efectos de control duran más, el baile se contagia a los mobs cercanos y, desde el nivel 5, los mobs que salen del baile o de la Disonancia quedan hechizados y pelean entre ellos.",
    "minstrel": "El más versátil: más ranuras de partitura en tus instrumentos, conjuros con menos combo mínimo y, desde el nivel 5, el Recital: tras tres conjuros distintos seguidos, el siguiente es gratis y se recarga antes.",
}
ABILITY_TEXT = {
    "rhythm": ("Ritmo", "Con un instrumento en la mano juegas un minijuego de ritmo: cada nota pide clic izquierdo, clic derecho, Shift o espacio, y la acción se hace de verdad. Solo es fallo no pulsar a tiempo el carril que pide la nota (pulsar otros no penaliza). Cada acierto sube el combo, da poder musical y acelera el tempo; un fallo te quita la mitad del combo. Las notas llegan en ráfagas con descansos entre ellas. Un clic izquierdo a compás hace más daño. Cada conjuro musical pide un combo mínimo al lanzarlo."),
    "harmony": ("Armonía", "Si tocas perfecto (todas las notas y ninguna pulsación de más), cada ciclo de ráfagas sube una etapa y tus auras son más fuertes. Un fallo o una pulsación de más baja dos etapas."),
    "counterpoint": ("Contrapunto", "Cada vez que el combo cruza 10, 20 o 30, todos tus conjuros en recarga pierden la mitad de lo que les queda."),
    "crescendo": ("Crescendo", "Durante unos segundos el combo no baja con los fallos y cada acierto cuenta doble."),
    "rhythmic_fury": ("Furia Rítmica", "Con la Armonía en la etapa 3 o más, haces más daño cuerpo a cuerpo."),
    "bewitch": ("Hechizar", "Al terminar el Baile Forzado o la Disonancia, los mobs normales quedan hechizados unos segundos: no te atacan y pelean entre sí. Los jefes son inmunes."),
    "recital": ("Recital", "Al lanzar tres conjuros distintos de tu instrumento en poco tiempo, el siguiente no cuesta maná y pierde la mitad de su recarga (con un tiempo mínimo entre usos)."),
}
MECHANIC_NAMES = {
    "music_power": "Poder musical", "aura_radius": "Radio de las auras", "timing_window": "Ventana de acierto",
    "aura_linger": "Persistencia de las auras", "bard_dance": "Danza", "soloist": "Solista", "chorus": "Coro",
    "aura_shape": "Forma de las auras", "tempo_presto": "Tempo Presto", "tempo_largo": "Tempo Largo", "trail": "Estela",
    "last_note": "Última Nota", "shared_step": "Paso Compartido", "perfect_march": "Marcha Perfecta",
    "martial_combo": "Combo Marcial", "resonant_strike": "Golpe Resonante", "hypnotic_voice": "Voz Hipnótica",
    "dance_contagion": "Contagio", "extended_repertoire": "Repertorio Ampliado", "fine_ear": "Oído Fino",
}
PARAMS = {
    "music_bonus": "Poder musical|%", "extra_blocks": "Radio extra (bloques)", "window_bonus": "Ventana de acierto|%",
    "miss_keep": "Combo que conservas al fallar|%", "alone_bonus": "Poder sin aliados cerca|%",
    "crowd_penalty": "Poder con aliados cerca|%", "per_ally": "Poder por aliado cercano|%", "chorus_cap": "Aliados que cuentan como máximo",
    "alone_penalty": "Poder sin aliados|%", "radius_factor": "Radio de las auras|%", "potency_factor": "Potencia de las auras|%",
    "forgiven_per_phrase": "Pulsaciones de más perdonadas por frase", "tempo_bonus": "Tempo|%", "power_penalty": "Poder musical|%",
    "linger_potency": "Potencia durante la persistencia|%", "sonic_damage": "Daño sónico", "ally_share": "Bonus para los aliados|%",
    "self_penalty": "Tu bonus|%", "min_stage": "Desde la etapa de Armonía", "per_hit": "Daño por acierto|%",
    "control_duration": "Duración del control|%", "max_extra": "Bailarines extra como máximo",
    "extra_slots": "Ranuras de partitura extra", "combo_reduction": "Combo mínimo menos",
    "max_combo": "Combo máximo", "power_per_hit": "Poder musical por acierto (Nivel Total 1)|%",
    "power_per_hit_15": "Poder musical por acierto (Nivel Total 15)|%", "power_per_hit_30": "Poder musical por acierto (Nivel Total 30)|%",
    "beat_bonus": "Daño del golpe a compás|%", "base_bpm": "Tempo base (pulsos por minuto)", "max_bpm": "Tempo máximo (pulsos por minuto)", "tempo_per_combo": "Tempo por punto de combo|%",
    "window_ms": "Ventana de acierto (ms)", "perfect_ms": "Ventana de acierto perfecto (ms)", "phrase_beats": "Pulsos por frase",
    "rest_beats": "Descanso entre frases (pulsos)", "note_density": "Pulsos con nota|%", "music_power_cap": "Tope de poder musical|%",
    "buff_power_scaling": "Parte del poder musical que reciben buffs y debuffs|%", "play_slow": "Velocidad mientras tocas|%",
    "aura_radius": "Radio base de las auras (bloques)", "idle_reset_seconds": "Combo perdido al guardar el instrumento tras|s",
    "aura_refresh_seconds": "Duración de los efectos del aura sin persistencia|s",
    "max_stage": "Etapas máximas", "buff_per_stage": "Potencia de las auras por etapa|%", "tempo_per_stage": "Tempo por etapa|%",
    "stage_loss": "Etapas perdidas por fallo", "refund": "Recarga restante que se pierde|%", "hit_multiplier": "Valor de cada acierto x",
    "distinct_spells": "Conjuros distintos", "cooldown_cut": "Recarga que se quita|%",
}

# ------------------------------------------------------------------ conjuros (deben coincidir con bard/MusicSpellInfo)
# id: (nombre, descripción para la guía de Iron's)
SPELLS = {
    "cutting_note": ("Nota Cortante", "Lanzas una nota afilada que hace daño sónico. En niveles altos atraviesa enemigos."),
    "march": ("Marcha", "Aura: tú y tus aliados corréis y atacáis más rápido."),
    "war_hymn": ("Himno de Guerra", "Aura: tú y tus aliados hacéis más daño."),
    "rest_ballad": ("Balada de Reposo", "Aura: tú y tus aliados regeneráis vida."),
    "elegy": ("Elegía", "Aura: los enemigos cercanos quedan débiles y lentos."),
    "rumble": ("Estruendo", "Un cono de sonido delante de ti que daña y empuja a los enemigos."),
    "iron_chorale": ("Coral de Hierro", "Aura: tú y tus aliados recibís menos daño."),
    "mana_cadence": ("Cadencia de Maná", "Aura: tú y tus aliados recuperáis maná más rápido."),
    "dissonance": ("Disonancia", "Una nota rota a tu alrededor: los enemigos quedan silenciados (sin conjuros) y aturdidos un instante."),
    "grand_piano": ("Piano de Cola", "Una sombra crece bajo tu objetivo y lo sigue; al final se queda quieta y cae un piano. ¡Apártate!"),
    "forced_dance": ("Baile Forzado", "Tu objetivo se pone a bailar: no puede atacar, lanzar conjuros ni usar objetos. Los jefes son inmunes."),
    "final_requiem": ("Réquiem Final", "El gran final: daño sónico enorme a tu alrededor y más daño para tus aliados durante unos segundos."),
    "fugue": ("Fuga", "Das un salto instantáneo hacia delante (unos 7 bloques, menos si hay un muro) dejando una estela de notas: los enemigos que atraviesas quedan empujados y aturdidos medio segundo."),
    "lullaby": ("Nana", "Un cono de canto suave delante de ti: los enemigos normales que alcanza se duermen. Cualquier daño los despierta. Los jefes son inmunes y los jugadores duermen la mitad."),
    "resolution": ("Resolución", "Un acorde que resuelve la tensión: tú y tus aliados cercanos perdéis todos los efectos negativos y ganáis un escudo de absorción durante unos segundos."),
    "echo": ("Eco", "Repite tu último conjuro musical con la mitad de su poder, sin gastar el maná ni la recarga de ese conjuro. Solo sirve si lo lanzaste hace poco."),
    "phantom_choir": ("Coro Fantasma", "Tres coristas espectrales cantan a tu alrededor unos segundos: los enemigos cercanos dejan de ir a por ti y tus aliados y se acercan a escuchar, y reciben daño sónico cada segundo."),
    "harmony_barrier": ("Barrera de Armonía", "Una cúpula de notas se queda donde la lanzas: absorbe los proyectiles enemigos y reduce el daño que reciben tú y tus aliados dentro de ella."),
    "siren_song": ("Canto de Sirena", "Un canto irresistible: los enemigos cercanos son arrastrados hacia ti durante unos segundos (los jefes, más despacio). Perfecto para juntarlos antes de un Estruendo o una Disonancia."),
    "grand_solo": ("Solo Magistral", "Tocas un solo: durante unos segundos tu combo está al máximo y no se pierde con los fallos, y el siguiente conjuro que lances no tiene recarga."),
    "eternal_lullaby": ("Canción de Cuna Eterna", "Una nana que protege a tus aliados cercanos: el primer golpe mortal que reciba cada uno mientras dura no los mata; sobreviven con parte de su vida y sin efectos negativos."),
}
SPELL_INFO = {
    "spell.rites_and_paths.info.damage": "Daño: %s",
    "spell.rites_and_paths.info.duration": "Duración: %s s",
    "spell.rites_and_paths.info.radius": "Radio: %s bloques",
    "spell.rites_and_paths.info.range": "Alcance: %s bloques",
    "spell.rites_and_paths.info.combo": "Combo mínimo: %s",
    "spell.rites_and_paths.info.pierce": "Atraviesa %s enemigos",
    "spell.rites_and_paths.info.speed": "+%s%% de velocidad y +%s%% de velocidad de ataque",
    "spell.rites_and_paths.info.damage_bonus": "+%s%% de daño",
    "spell.rites_and_paths.info.regen": "%s de vida por segundo",
    "spell.rites_and_paths.info.debuff": "Debilidad I y Lentitud I",
    "spell.rites_and_paths.info.reduction": "−%s%% de daño recibido",
    "spell.rites_and_paths.info.mana_regen": "+%s%% de regeneración de maná",
    "spell.rites_and_paths.info.control": "Silencio %s s y aturdimiento %s s",
    "spell.rites_and_paths.info.dance": "Baile %s s (jugadores %s s)",
    "spell.rites_and_paths.info.second_piano": "Cae un segundo piano con la mitad de daño",
    "spell.rites_and_paths.info.requiem": "+%s%% de daño a tus aliados durante %s s",
    "spell.rites_and_paths.info.dash": "Salto de %s bloques; aturde %s s",
    "spell.rites_and_paths.info.sleep": "Sueño %s s (el daño los despierta)",
    "spell.rites_and_paths.info.absorb": "Quita efectos negativos y da %s de absorción durante %s s",
    "spell.rites_and_paths.info.echo": "Repite tu último conjuro al %s%% de poder (hasta %s s después)",
    "spell.rites_and_paths.info.choir": "Tres coristas durante %s s: %s de daño por segundo cerca de ellos",
    "spell.rites_and_paths.info.barrier": "Absorbe proyectiles y −%s%% de daño recibido durante %s s",
    "spell.rites_and_paths.info.siren": "Atrae a los enemigos durante %s s",
    "spell.rites_and_paths.info.solo": "Combo al máximo y sin pérdida durante %s s; el siguiente conjuro sin recarga",
    "spell.rites_and_paths.info.ward": "Sobreviven a un golpe mortal con el %s%% de vida (%s s)",
}

# ------------------------------------------------------------------ instrumentos (deben coincidir con bard/Instrument)
COMMON = ["lute", "flute", "harp", "drum", "violin", "lyre", "ocarina", "pan_flute", "tambourine"]
RARE = ["bagpipe", "carillon", "hurdy_gurdy"]
EPIC = ["crystal_harp", "war_horn", "spectral_violin", "scale_lyre_fire", "scale_lyre_ice", "scale_lyre_lightning"]
LEGENDARY = ["eternal_sun_lute", "abyssal_organ", "colossus_drum"]
INSTRUMENTS = COMMON + RARE + EPIC + LEGENDARY
INSTRUMENT_NAMES = {
    "lute": "Laúd", "flute": "Flauta", "harp": "Arpa", "drum": "Tambor", "violin": "Violín", "lyre": "Lira",
    "ocarina": "Ocarina", "pan_flute": "Zampoña", "tambourine": "Pandereta", "bagpipe": "Gaita", "carillon": "Carrillón",
    "hurdy_gurdy": "Zanfona", "crystal_harp": "Arpa de Cristal", "war_horn": "Cuerno de Guerra",
    "spectral_violin": "Los fallos te quitan la mitad de combo de lo normal.", "scale_lyre_fire": "Lira de Escamas de Fuego",
    "scale_lyre_ice": "Tus auras reducen un 25 % el daño de hielo para ti y tus aliados.", "scale_lyre_lightning": "Lira de Escamas de Rayo",
    "eternal_sun_lute": "Los aciertos perfectos curan 0,5 de vida a tus aliados a 8 bloques y 0,25 a ti (como mucho cada medio segundo).", "abyssal_organ": "Órgano del Abismo", "colossus_drum": "Tambor del Coloso",
}
PASSIVES = {
    "crystal_harp": "Cada 10 aciertos seguidos recuperas el 5 % de tu maná máximo.",
    "war_horn": "Tus auras tienen un 25 % más de radio.",
    "spectral_violin": "Un fallo te quita la mitad de combo que de normal.",
    "scale_lyre_fire": "Tus auras reducen un 25 % el daño de fuego para ti y tus aliados.",
    "scale_lyre_ice": "Tus auras dan resistencia al hielo a tus aliados.",
    "scale_lyre_lightning": "Tus auras reducen un 25 % el daño de rayo para ti y tus aliados.",
    "eternal_sun_lute": "Los aciertos perfectos curan a tus aliados cercanos.",
    "abyssal_organ": "Tus auras tienen un 25 % más de radio, y Última Nota hace el doble de daño con un 25 % más de radio.",
    "colossus_drum": "Cada 10 aciertos seguidos suelta un estruendo que aturde 1 s a los enemigos a 4 bloques (no a los jefes).",
    "guitar_axe": "Hacha a dos manos que también se toca. El Bardo no tiene muro de Armas pesadas con ella; las demás clases la usan como hacha normal.",
}
MATERIALS = {"resonant_shard": "Fragmento resonante", "arcane_thread": "Hilo arcano", "abyssal_reed": "Lengüeta abisal",
             "colossus_drumskin": "Parche del Coloso"}

ITEM_UI = {
    "item.rites_and_paths.guitar_axe": "Guitarra-hacha",
    "item.rites_and_paths.sheet_music": "Partitura",
    "item.rites_and_paths.music_note": "Nota musical",
    "item.rites_and_paths.sheet_music.named": "Partitura: %s",
    "tooltip.rites_and_paths.sheet_music.level": "Nivel %s de %s",
    "tooltip.rites_and_paths.sheet_music.combo": "Combo mínimo: %s",
    "tooltip.rites_and_paths.sheet_music.empty": "Partitura en blanco",
    "tooltip.rites_and_paths.sheet_music.how": "Se guarda en un instrumento (atril o mesa de inscripción de Iron's). Sube de nivel con tinta en la mesa de trabajo.",
    "tooltip.rites_and_paths.instrument.rarity.common": "Común", "tooltip.rites_and_paths.instrument.rarity.rare": "Raro",
    "tooltip.rites_and_paths.instrument.rarity.epic": "Épico", "tooltip.rites_and_paths.instrument.rarity.legendary": "Legendario",
    "tooltip.rites_and_paths.instrument.header": "Instrumento %s (solo Bardo):",
    "tooltip.rites_and_paths.instrument.power": "+%s%% de poder musical",
    "tooltip.rites_and_paths.instrument.cast_speed": "+%s%% de velocidad de lanzamiento",
    "tooltip.rites_and_paths.instrument.mana_regen": "+%s%% de regeneración de maná",
    "tooltip.rites_and_paths.instrument.cooldown": "−%s%% de recarga de conjuros",
    "tooltip.rites_and_paths.instrument.max_mana": "+%s de maná máximo",
    "tooltip.rites_and_paths.instrument.slots": "Partituras: %s/%s · admite hasta el %s%% del nivel máximo de cada partitura",
    "tooltip.rites_and_paths.instrument.spell": " ♪ %s (nivel %s)",
    "tooltip.rites_and_paths.instrument.spell_capped": " ♪ %s (nivel %s, limitado a %s)",
    "tooltip.rites_and_paths.instrument.passive": "Pasiva: %s",
    "tooltip.rites_and_paths.instrument.how": "Clic derecho: lanza el conjuro elegido en la rueda de Iron's. Mayús + clic derecho (fuera de combate, en la mano principal): atril. También se ponen partituras en la mesa de inscripción de Iron's.",
    "ui.rites_and_paths.stand.title": "Atril: %s",
    "ui.rites_and_paths.stand.help": "Pon partituras en los huecos. Los huecos grises superan tus ranuras: solo se pueden vaciar.",
    "msg.rites_and_paths.bard.not_bard": "Solo un Bardo sabe tocar este instrumento.",
    "msg.rites_and_paths.bard.combo_needed": "%s necesita %s de combo (tienes %s)",
    "msg.rites_and_paths.bard.not_instrument": "Elige en la rueda un conjuro de tu instrumento",
    "msg.rites_and_paths.bard.use_instrument": "Los conjuros musicales se lanzan tocando el instrumento (clic derecho)",
    "msg.rites_and_paths.bard.no_target": "No hay ningún objetivo a la vista",
    "msg.rites_and_paths.bard.no_echo": "No hay ningún conjuro reciente que repetir",
    "msg.rites_and_paths.bard.no_irons": "Los conjuros musicales necesitan Iron's Spells 'n Spellbooks",
    "msg.rites_and_paths.bard.crescendo": "¡Crescendo!",
    "msg.rites_and_paths.bard.recital": "Recital: tu siguiente conjuro es gratis",
    "hud.rites_and_paths.bard.perfect": "¡Perfecto!",
    "hud.rites_and_paths.bard.good": "Bien",
    "hud.rites_and_paths.bard.miss": "Fallo",
    "hud.rites_and_paths.bard.combo": "x%s",
    "hud.rites_and_paths.bard.crescendo": "Crescendo",
    "school.rites_and_paths.music": "Musical",
    "entity.rites_and_paths.cutting_note": "Nota Cortante",
    "entity.rites_and_paths.grand_piano": "Piano de Cola",
    "death.attack.rites_and_paths.music_magic": "%1$s cayó ante la música de %2$s",
    "death.attack.rites_and_paths.music_magic.player": "%1$s cayó ante la música de %2$s",
    "death.attack.rites_and_paths.sonic": "%1$s fue reventado por una onda sónica de %2$s",
    "death.attack.rites_and_paths.sonic.player": "%1$s fue reventado por una onda sónica de %2$s",
    "death.attack.rites_and_paths.grand_piano": "%1$s fue aplastado por el piano de %2$s",
    "death.attack.rites_and_paths.grand_piano.player": "%1$s fue aplastado por el piano de %2$s",
}
EFFECTS = {
    "march": "Marcha", "war_hymn": "Himno de Guerra", "rest_ballad": "Balada de Reposo", "iron_chorale": "Coral de Hierro",
    "mana_cadence": "Cadencia de Maná", "requiem_fervor": "Fervor del Réquiem", "shared_step": "Paso Compartido",
    "scale_ward_fire": "Escamas contra el fuego", "scale_ward_ice": "Escamas contra el hielo",
    "scale_ward_lightning": "Escamas contra el rayo", "forced_dance": "Baile Forzado", "silenced": "Silencio",
    "charmed": "Hechizado",
}


# ------------------------------------------------------------------ recetas, tags y modelos
def item(i): return {"item": i}
def tag(t): return {"tag": t}


def shaped(pattern, key, result, count=1, conditions=None, nbt=None):
    res = {"item": result, "count": count}
    if nbt: res["nbt"] = nbt
    d = {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern, "key": key, "result": res}
    return {"conditions": conditions, **d} if conditions else d


def shapeless(ings, result, count=1, conditions=None, nbt=None):
    res = {"item": result, "count": count}
    if nbt: res["nbt"] = nbt
    d = {"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": ings, "result": res}
    return {"conditions": conditions, **d} if conditions else d


def smithing(template, base, addition, result, conditions=None):
    d = {"type": "minecraft:smithing_transform", "template": template, "base": base, "addition": addition, "result": {"item": result}}
    return {"conditions": conditions, **d} if conditions else d


PL, ST, SI = tag("minecraft:planks"), item("minecraft:string"), item("minecraft:stick")
SHARD = item(R("resonant_shard"))
ARCANE_INGOT = tag(R("bard/arcane_ingot"))

def recipes():
    out = {}
    out["resonant_shard"] = shapeless([item("minecraft:amethyst_shard"), item("minecraft:amethyst_shard"),
                                       item("minecraft:note_block"), item("minecraft:glowstone_dust")], R("resonant_shard"), 2)
    out["arcane_thread"] = shapeless([ST, ST, ST, tag(R("bard/arcane_essence"))], R("arcane_thread"), 3)
    # Comunes: madera y cuerda.
    out["lute"] = shaped(["  K", "PSK", "PP "], {"K": SI, "P": PL, "S": ST}, R("lute"))
    out["flute"] = shaped(["  B", "SB ", "B  "], {"B": item("minecraft:bamboo"), "S": ST}, R("flute"))
    out["harp"] = shaped(["PSS", "PSS", "PPP"], {"P": PL, "S": ST}, R("harp"))
    out["drum"] = shaped(["LLL", "P P", "PPP"], {"L": item("minecraft:leather"), "P": PL}, R("drum"))
    out["violin"] = shaped([" SK", "SPS", "PP "], {"K": SI, "P": PL, "S": ST}, R("violin"))
    out["lyre"] = shaped(["K K", "SSS", "PPP"], {"K": SI, "P": PL, "S": ST}, R("lyre"))
    out["ocarina"] = shaped([" C ", "CCC", " C "], {"C": item("minecraft:clay_ball")}, R("ocarina"))
    out["pan_flute"] = shaped(["RRR", "SSS"], {"R": item("minecraft:sugar_cane"), "S": ST}, R("pan_flute"))
    out["tambourine"] = shaped([" I ", "ILI", " I "], {"I": item("minecraft:iron_nugget"), "L": item("minecraft:leather")}, R("tambourine"))
    # Raros: hierro u oro.
    out["bagpipe"] = shaped(["I I", "LWL", "LLL"], {"I": item("minecraft:iron_ingot"), "L": item("minecraft:leather"), "W": tag("minecraft:wool")}, R("bagpipe"))
    out["carillon"] = shaped(["III", "GGG", "I I"], {"I": item("minecraft:iron_ingot"), "G": item("minecraft:gold_nugget")}, R("carillon"))
    out["hurdy_gurdy"] = shaped(["GSG", "PNP", "PPP"], {"G": item("minecraft:gold_ingot"), "S": ST, "N": item("minecraft:note_block"), "P": PL}, R("hurdy_gurdy"))
    # Épicos: mesa de herrería (fragmento resonante como plantilla + instrumento base + lingote arcano).
    out["crystal_harp"] = smithing(SHARD, item(R("harp")), ARCANE_INGOT, R("crystal_harp"), IRONS_COND)
    out["war_horn"] = smithing(SHARD, item("minecraft:goat_horn"), ARCANE_INGOT, R("war_horn"), IRONS_COND)
    out["spectral_violin"] = smithing(SHARD, item(R("violin")), ARCANE_INGOT, R("spectral_violin"), IRONS_COND)
    for e in ("fire", "ice", "lightning"):
        out[f"scale_lyre_{e}"] = shaped(["SSS", "SLS", "SSS"], {"S": tag(R(f"iceandfire/scales_{e}")), "L": item(R("lyre"))},
                                        R(f"scale_lyre_{e}"), conditions=IAF_COND)
    # Guitarra-hacha (decisión del usuario): F en diagonal, L arriba a la izquierda, H abajo a la derecha.
    out["guitar_axe"] = shaped(["LLF", "LFH", "FHH"], {"F": SHARD, "L": ARCANE_INGOT, "H": item(R("arcane_thread"))}, R("guitar_axe"),
                               conditions=IRONS_COND)
    # Legendarios mejorados con el material de un jefe.
    out["abyssal_organ"] = smithing(SHARD, item(R("hurdy_gurdy")), item(R("abyssal_reed")), R("abyssal_organ"))
    out["colossus_drum"] = smithing(SHARD, item(R("drum")), item(R("colossus_drumskin")), R("colossus_drum"))
    # Las partituras de nivel 1 salen de la forja de pergaminos de Iron's (ScrollForgeMenuMixin): papel + tinta + disco.
    out["sheet_music_upgrade"] = {"type": R("sheet_music_upgrade"), "category": "misc"}
    return out


def tags():
    t = {
        "items/instruments": [R(i) for i in INSTRUMENTS] + [R("guitar_axe")],
        # Foco de la escuela musical (forja de pergaminos de Iron's): cualquier disco, el bloque musical o la gramola.
        "items/music_focus": ["#minecraft:music_discs", "minecraft:note_block", "minecraft:jukebox"],
        "items/bard/arcane_ingot": [opt(IR + "arcane_ingot")],
        "items/bard/arcane_essence": [opt(IR + "arcane_essence"), "minecraft:lapis_lazuli"],
        "items/bard/ink_common": [opt(IR + "common_ink")],
        "items/bard/ink_uncommon": [opt(IR + "uncommon_ink")],
        "items/bard/ink_rare": [opt(IR + "rare_ink")],
        "items/bard/ink_epic": [opt(IR + "epic_ink")],
        "items/bard/ink_legendary": [opt(IR + "legendary_ink")],
        # Jefes que sueltan los materiales legendarios [LEER: ids en la versión del pack].
        "entity_types/bard/sun_lute": [opt("mowziesmobs:barako"), opt("mowziesmobs:umvuthi")],
        "entity_types/bard/abyssal_reed": [opt("cataclysm:the_leviathan")],
        "entity_types/bard/colossus_drumskin": [opt("cataclysm:netherite_monstrosity")],
    }
    return t


def lang():
    out = {}
    for i in INSTRUMENTS:
        out[f"item.{NS}.{i}"] = INSTRUMENT_NAMES[i]
    for i, p in PASSIVES.items():
        out[f"tooltip.{NS}.instrument.passive.{i}"] = p
    for m, n in MATERIALS.items():
        out[f"item.{NS}.{m}"] = n
    for sid, (name, guide) in SPELLS.items():
        out[f"spell.{NS}.{sid}"] = name
        out[f"spell.{NS}.{sid}.guide"] = guide
    for eid, name in EFFECTS.items():
        out[f"effect.{NS}.{eid}"] = name
    out.update(SPELL_INFO)
    out.update(ITEM_UI)
    return out


def build(dump, out_dir):
    data = os.path.join(out_dir, "data")
    assets = os.path.join(out_dir, "assets", NS)
    for name, recipe in recipes().items():
        dump(os.path.join(data, NS, "recipes", "bard", name + ".json"), recipe)
    for path, values in tags().items():
        dump(os.path.join(data, NS, "tags", path + ".json"), {"replace": False, "values": values})
    # Tipos de daño: conjuros musicales, ondas sónicas y el piano. No vuelven a pasar por el pipeline de golpes.
    for d in ("music_magic", "sonic", "grand_piano"):
        dump(os.path.join(data, NS, "damage_type", d + ".json"), {"message_id": f"{NS}.{d}", "exhaustion": 0.1, "scaling": "never"})
    no_proc = os.path.join(data, NS, "tags", "damage_type", "no_proc.json")
    import json
    cur = json.load(open(no_proc, encoding="utf-8")) if os.path.exists(no_proc) else {"replace": False, "values": []}
    for d in ("music_magic", "sonic", "grand_piano"):
        if R(d) not in cur["values"]: cur["values"].append(R(d))
    dump(no_proc, cur)
    # Epic Fight: la guitarra-hacha con el moveset de espadón (dos manos) [verificar en el juego].
    dump(os.path.join(data, NS, "capabilities", "weapons", "guitar_axe.json"),
         {"type": "epicfight:greatsword", "attributes": {"two_hand": {"armor_negation": 0.0, "impact": 2.0, "max_strikes": 3}}})
    # Modelos.
    for n in INSTRUMENTS + list(MATERIALS) + ["sheet_music", "music_note"]:
        dump(os.path.join(assets, "models", "item", n + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{n}"}})
    dump(os.path.join(assets, "models", "item", "guitar_axe.json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/guitar_axe"}})
    return lang()
