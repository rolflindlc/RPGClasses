#!/usr/bin/env python3
"""Auditoría de balance de Rites & Paths (método de la sección 10 del documento v2).

Cada fila (clase, subclase, raza o subraza) se puntúa de 0 a 10 en cuatro ejes y el total es su media:

* Movilidad y Utilidad: valoración de diseño (entrada a mano).
* Resistencia: vida efectiva contra golpes de 15 con la curva de Improved Armor (K = 18, 1,5 puntos por EPF; la
  penalización de golpes grandes solo sobre la armadura) o la vanilla (--vanilla), como media geométrica entre equipo
  de hierro (15/0) y de diamante (20/8) con el muro de Armadura aplicado y Protección (--epf), por el factor de escudo
  1 + 0,5 / ((1 + 2w)(1 + w)). Puntuación = 2 * log2(HP / 10).
* DPS: índice frente a un jugador sin clase (1,00). Puntuación = 3 + 4 * (índice - 1).

Las razas y subrazas no tienen DPS ni resistencia propios: se aplican sus modificadores a la clase media y se puntúa la
diferencia. Tiers por diferencia con la media de su grupo: S >= +8 %, A >= +3 %, B dentro de +-3 %, C >= -8 %, D < -8 %.

Uso: python3 tools/balance_audit.py [--markdown] [--vanilla] [--epf N]
Todos los valores son estimaciones de diseño, no medidas en el juego. Cambia las tablas de abajo al medir.
"""
import math
import sys

# ---------------------------------------------------------------- fórmulas


# Curva de armadura: Improved Armor (plana, por defecto) o vanilla (--vanilla). EPF general de las piezas: --epf N
# (16 = Protección IV ×4, por defecto; 0 = sin encantar). Bárbaro y Monje llevan su equipo de clase de diamante
# encantado (no apaga sus pasivas desarmadas).
MODE = "vanilla" if "--vanilla" in sys.argv else "flat"
EPF = float(sys.argv[sys.argv.index("--epf") + 1]) if "--epf" in sys.argv else 16.0
def _van(armor, tough, dmg=15.0, epf=0.0):
    eff = max(armor / 5.0, armor - dmg / (2.0 + tough / 4.0))
    return (1.0 - min(20.0, eff) / 25.0) * (1.0 - min(20.0, epf) / 25.0)
def _flat(armor, tough, dmg=15.0, epf=0.0):
    a = max(armor / 5.0, armor - dmg / (2.0 + tough / 4.0)) if armor > 0 else armor
    A = max(a + 1.5 * min(epf, 20.0), 0.0)
    return 1.0 - A / (A + 18.0)
def reduction(armor, tough, dmg=15.0, epf=None):
    e = EPF if epf is None else epf
    return _flat(armor, tough, dmg, e) if MODE == "flat" else _van(armor, tough, dmg, e)
def armor_ratio(armor, tough, epf):
    # Los valores de vida efectiva fijados a mano se midieron con vanilla sin encantar; este factor los pasa al modo
    # actual (factor de vida efectiva del modo actual frente a la base de la auditoría (vanilla sin encantar)
    return (1.0 / reduction(armor, tough, epf=epf)) / (1.0 / _van(armor, tough))
def geared_ratio(mult_a=1.0, mult_t=1.0, wall=0.0):
    a = 1 - wall
    r1 = armor_ratio(15 * mult_a * a, 0, EPF); r2 = armor_ratio(20 * mult_a * a, 8 * mult_t * a, EPF)
    return (r1 * r2) ** 0.5


def shield_factor(w):
    return 1.0 + 0.5 / ((1.0 + 2.0 * w) * (1.0 + w))


def ehp_geared(hp, armor_wall, shield_wall, extra=1.0):
    """Vida efectiva con equipo normal (hierro y diamante) y los muros de la clase."""
    a = 1.0 - armor_wall
    iron = hp / reduction(15 * a, 0 * a)
    diamond = hp / reduction(20 * a, 8 * a)
    return math.sqrt(iron * diamond) * shield_factor(shield_wall) * extra


def ehp_fixed(hp, armor, tough, shield_wall, extra=1.0):
    """Vida efectiva con una armadura fija (Bárbaro y Monje sin equipo)."""
    return hp / reduction(armor, tough, epf=0) * shield_factor(shield_wall) * extra


def res_score(ehp):
    return 2.0 * math.log2(ehp / 10.0)


def dps_score(idx):
    return 3.0 + 4.0 * (idx - 1.0)


def row(name, mob, ehp, idx, util, note=""):
    r, d = res_score(ehp), dps_score(idx)
    return dict(name=name, mob=mob, ehp=ehp, idx=idx, util=util, res=r, dps=d,
                total=(mob + r + d + util) / 4.0, note=note)


# ---------------------------------------------------------------- Monje (rev. 2.20-2.21)
# Fuego +8 % por carga (+25 % con Puño) sobre un suelo del 72 % del DPS objetivo; objetivo = índice 1,17 (espada de
# diamante en Epic Fight, como en la rev. 2.17). Piedra +1,4 / +0,8 por carga (+25 % con Piel) sobre 13/4. Agua +3 %.

MONK_TARGET = 1.17


def monk(charges):
    fire = 0.72 * (1 + charges * 0.08 * 1.25) * MONK_TARGET
    armor = 13 + charges * 1.4 * 1.25
    tough = 4 + charges * 0.8 * 1.25
    stone = 20 / reduction(min(30, armor + 1), min(20, tough), epf=EPF) * shield_factor(1.0)  # Escudos −100 %
    water = charges * 3 * 1.25
    return fire, stone, water


M5_FIRE, M5_STONE, _ = monk(5)
M10_FIRE, M10_STONE, _ = monk(10)

# ---------------------------------------------------------------- clases (nivel 30)
# (nombre, movilidad, vida efectiva, índice DPS, utilidad, nota)

CLASSES = [
    row("Mago", 3, ehp_geared(16, .25, .50), 2.05, 6.5),
    row("Paladín", 2.5, 122.5 * geared_ratio(1.45, 1.6), 1.15, 7, "vida 24, Baluarte y Provocación (valor de la rev. 2.7)"),
    row("Bárbaro", 5, 56.5 * armor_ratio(17, 6, EPF), 1.75, 4, "Piel de Hierro 16/6 (valor de la rev. 2.7); el muro de Armadura no le afecta sin armadura"),
    row("Pícaro", 6, ehp_geared(17, .40, .70), 1.49, 7),
    row("Ranger", 5, ehp_geared(20, 0, .30), 1.61, 6.25, "+0,25 de utilidad por los conjuros de flecha sin muro"),
    row("Monje (5 cargas)", 5.5, M5_STONE, M5_FIRE, 7.5, "rev. 2.20: Fuego, Piedra y Agua con su rama llena"),
    row("Clérigo", 3, ehp_geared(22, .25, .50), 1.2, 9, "curación de grupo; DPS según equipo"),
    row("Bardo", 4.5, ehp_geared(18, .30, .55), 1.35, 9, "combo medio 20, Compás e instrumento raro; auras de grupo"),
    row("Druida", 5, ehp_geared(13, .30, .70) + 48 * 0.8 * 1.3 / 1.6, 1.45, 8,
        "rev. 2.23: vida −35 %; forma de bestia de ~30 HP ×1,3 al nivel 30 (+1 %/nivel, vida aparte)"),
    row("Maestro del Giro (exp.)", 6, ehp_geared(18, .30, .60), 1.55, 6, "experimental: fuera de la media"),
]

# ---------------------------------------------------------------- subclases (nivel 40)

SUBCLASSES = [
    ("Mago", row("Cronomante", 3.5, 30.3 * geared_ratio(wall=.25), 2.10, 7.5)),
    ("Mago", row("Elementalista", 3, 28.2 * geared_ratio(wall=.25), 2.30, 7.5, "sin naturaleza ni sagrada (−0,1 de DPS)")),
    ("Mago", row("Mago de Batalla", 3, 44.0 * geared_ratio(wall=.25), 2.00, 6.5, "sin naturaleza en Filo Imbuido")),
    ("Paladín", row("Guardián Bendito", 2.5, 140.9 * geared_ratio(1.45, 1.6), 1.15, 9)),
    ("Paladín", row("Bastión Inflexible", 3.5, 153.1 * geared_ratio(1.45, 1.6), 1.15, 7)),
    ("Paladín", row("Juggernaut de Carga", 4.5, 183.8 * geared_ratio(1.45, 1.6), 1.20, 6)),
    ("Bárbaro", row("Indestructible", 5, 110.1 * armor_ratio(17, 6, EPF), 1.75, 6)),
    ("Bárbaro", row("Berserker Suicida", 6, 67.7 * armor_ratio(17, 6, EPF), 2.16, 5)),
    ("Bárbaro", row("Corruptor de Plagas", 5, 62.1 * armor_ratio(17, 6, EPF), 2.00, 5)),
    ("Pícaro", row("Cambiarrostros", 6, ehp_geared(17, .40, .70) * 1.1, 1.49, 9,
                   "rev. 2.20: sin Paso de Sombra; infiltración e información (+10 % de vida efectiva por agro evitado)")),
    ("Pícaro", row("Ilusionista de Sombras", 7, 35.4 * geared_ratio(wall=.40), 1.48, 8)),
    ("Pícaro", row("Hoja Arcana", 6, 23.5 * geared_ratio(wall=.40), 1.55, 8)),
    ("Ranger", row("Maestro de Bestias", 5, 65.2 * geared_ratio(), 2.14, 7)),
    ("Ranger", row("Explorador Ágil", 8, 57.6 * geared_ratio(), 1.61, 7)),
    ("Ranger", row("Francotirador", 5, 50.1 * geared_ratio(), 2.05, 5)),
    ("Monje", row("Maestro de la Brisa (5 cargas)", 8, M5_STONE * 52.4 / 47.4, M5_FIRE, 7.5)),
    ("Monje", row("Palma Pesada (5 cargas)", 5.5, M5_STONE * 54.7 / 47.4, M5_FIRE * 1.13 / 1.05, 7.5)),
    ("Monje", row("Guardián Trimundial (5 cargas)", 5.5, M5_STONE * 59.5 / 47.4, M5_FIRE * 1.18 / 1.05, 8.5)),
    ("Clérigo", row("Ángel", 5, ehp_geared(22, .25, .50) * 1.15, 1.2, 9.5, "vuelo 10 s, Halo y purga")),
    ("Clérigo", row("Mártir", 3, ehp_geared(24.2, .25, .50) * 1.1, 1.2, 9.5, "Sacrificio sin cooldown; Resolución")),
    ("Clérigo", row("Intercesor", 4, ehp_geared(22, .25, .50) * 1.2, 1.2, 9, "cura a distancia; cuerpo −40 % de daño")),
    ("Bardo", row("Cantor de Guerra", 4.5, ehp_geared(18, .30, .55), 1.65, 8.5, "+30 % con combo 30, Golpe Resonante")),
    ("Bardo", row("Encantador", 4.5, ehp_geared(18, .30, .55) * 1.1, 1.35, 9.5, "controles +50 %, Hechizar")),
    ("Bardo", row("Juglar", 4.5, ehp_geared(18, .30, .55), 1.45, 9.5, "3 ranuras más, Recital")),
    ("Druida", row("Círculo de la Luna", 6, ehp_geared(13, .30, .70) + 48 * 0.8 * 1.4 / 1.6 * 1.2, 1.6, 8,
                   "formas en 1 s sin reserva; rev. 2.23: conserva el % de vida al cambiar")),
    ("Druida", row("Forma Sobrenatural", 5, ehp_geared(13, .30, .70) + 48 * 0.8 * 1.4 / 1.6 * 0.85, 1.75, 8.5,
                   "conjuros en forma; formas −15 %")),
    ("Druida", row("Escultor", 5.5, ehp_geared(13, .30, .70) + 60 * 0.8 * 1.4 / 1.6, 1.6, 8, "quimeras de 4 partes")),
    ("Maestro del Giro (exp.)", row("Jinete", 7, ehp_geared(18, .30, .60), 1.6, 7, "aturde a velocidad")),
    ("Maestro del Giro (exp.)", row("Escáner", 6, ehp_geared(18, .30, .60), 1.8, 6.5, "críticos seguros con marca")),
    ("Maestro del Giro (exp.)", row("Trampero de Giro", 6, ehp_geared(18, .30, .60) * 1.1, 1.65, 7, "minas")),
]

# ---------------------------------------------------------------- razas y subrazas (rango 3)
# Modificadores sobre la clase media: (movilidad +, factor de vida efectiva, factor de DPS, utilidad +, nota).

RACES = [
    ("Humano", 0, 1.08, 1.08, 0.5, "muro −50 % (≈ +8 % en el eje del muro elegido) y rasgos base −50 %"),
    ("Orco", 0, 1.10 * 1.15, 1.0, 0, "+10 % de vida y una muerte evitada por vida (≈ +15 %); hitbox mayor"),
    ("Enano", -0.5, 1.0, 1.0, 1.0, "minería y Fortuna; −10 % de velocidad; hitbox menor (+5 % de esquiva)"),
    ("Elfo", 0.5, 1.0, 1.0, 0.5, "hojas como camino y refugio; no come carne ni pescado"),
    ("Goblin", 0.5, 0.75 * 1.25, 1.0, 1.0, "−25 % de vida, pero hitbox −50 % (≈ +25 % de golpes fallados); Suerte y Saqueo"),
    ("Tritón", 0.5, 0.95, 1.0, 1.0, "agua: respiración, nado y minería; +30 % de daño de fuego"),
    ("Infernal", 0, 1.10, 1.08, 0, "inmune al fuego; conjuros de fuego raciales (con muro); golems y precios"),
    ("No-muerto", 0, 1.30, 1.0, 0, "1 HP/s de regeneración (cortada 3 s al recibir daño); sol y sin pociones de curación"),
    ("Slime", 1.0, 0.85 * 1.05, 1.0, 0, "sin caída, rebote y salto doble; −15 % de vida y +50 % de empuje recibido"),
    ("Coloso", -1.0, 2.0 * 0.85, 1.05, 1.0, "+100 % de vida, hitbox ×2 y +50 % de detección; minería en área y alcance"),
    ("Dracónido", 0, 1.08, 1.10, 0, "resistencia de su elemento (≈ +8 %) y aliento canalizado (con muro)"),
    ("Centauro", 1.5, 0.95, 1.08, 0.5, "+65 % de velocidad sin tope y embestida; hitbox de caballo; lleva a un aliado"),
]

SUBRACES = [
    ("Humano", "Polivalente", 0, 1.0, 1.04, 0.5),
    ("Humano", "Especialista", 0, 1.0, 1.08, 0),
    ("Humano", "Autodidacta", 0, 1.0, 1.0, 0.5),
    ("Orco", "Pétreo", 0, 1.25, 1.0, 0),
    ("Orco", "Devorador", 0, 1.10, 1.08, 0.5),
    ("Orco", "Caudillo", 0, 1.05, 1.0, 1.0),
    ("Enano", "Profundidades", 0, 1.0, 1.0, 1.0),
    ("Enano", "Herrero", 0, 1.0, 1.0, 1.0),
    ("Enano", "Demoledor", 0, 1.0, 1.0, 1.5),
    ("Elfo", "Oscuro", 0, 1.15, 1.0, 1.0),
    ("Elfo", "Arcano", 0, 1.03, 1.05, 1.0),
    ("Elfo", "Silvano", 0, 1.0, 1.0, 1.0),
    ("Goblin", "Jinete de Lobo", 1.0, 1.05, 1.05, 0),
    ("Goblin", "Ladrón", 0, 1.0, 1.0, 1.0),
    ("Goblin", "Artificiero", 0, 1.08, 1.0, 1.0),
    ("Tritón", "Pescador", 0, 1.0, 1.0, 1.0),
    ("Tritón", "Moldeador", 0, 1.05, 1.03, 1.0),
    ("Tritón", "Abisal", 0, 1.05, 1.0, 1.0),
    ("Infernal", "Nativo", 0, 1.05, 1.0, 1.0),
    ("Infernal", "Fundidor", 0.5, 1.0, 1.0, 1.0),
    ("Infernal", "Pirómano", 0, 1.0, 1.10, 0),
    ("No-muerto", "Vampiro", 0, 1.15, 1.0, 0),
    ("No-muerto", "Esqueleto", 0, 1.20, 1.0, 0),
    ("No-muerto", "Espectro", 0.5, 1.10, 1.0, 0.5),
    ("Slime", "Enjambre", 0.5, 1.30, 1.0, 0),
    ("Slime", "Resorte", 1.5, 1.0, 1.0, 0),
    ("Slime", "Mitótico", 0, 0.85 * 1.35, 1.0, 0),
    ("Slime", "Viscoso", 1.0, 1.0, 1.05, 0.5),
    ("Coloso", "Porteador", -0.5, 1.0, 1.0, 1.5),
    ("Coloso", "Titán", 0, 1.25, 1.10, 0),
    ("Dracónido", "Fuego", 0, 1.03, 1.03, 0.5),
    ("Dracónido", "Hielo", 0, 1.05, 1.0, 0.5),
    ("Dracónido", "Eléctrico", 0, 1.05, 1.05, 0.5),
    ("Dracónido", "Veneno", 0, 1.05, 1.05, 0),
    ("Dracónido", "Ácido", 0, 1.05, 1.08, 0),
    ("Dracónido", "Agua", 0.5, 1.05, 1.0, 0.5),
    ("Centauro", "Pegaso", 1.0, 1.0, 1.0, 0),
    ("Centauro", "Unicornio", 0, 1.08, 1.03, 1.0),
    ("Centauro", "Guerrero", 0, 1.0, 1.15, 0),
]


# ---------------------------------------------------------------- kit de conjuros de Iron's
# Los conjuros de movilidad y defensa de Iron's (Teletransporte, Paso Helado, Embestida Ardiente, Golpe Voltaico,
# Ascensión, Paso de Sangre, Evasión, Velo Abisal, Paro Cardíaco, Escudo, Invisibilidad...) escalan con su nivel, no con
# el poder de hechizo: el muro de Magia no los recorta y cualquier clase puede lanzarlos. Lo que cambia es cuántas veces:
# maná, regeneración, recarga y cargas gratis. Factor por clase: (movilidad +, vida efectiva ×, utilidad +).
# Las curaciones sagradas propias (Curación, Curación Mayor, Fortalecer) cuentan como vida efectiva del Clérigo y del
# Paladín. Las subclases heredan el factor de su clase.

SPELL_KIT = {
    "Mago": (3.0, 1.40, 1.5),       # 400 de maná, −25 % de recarga, Conjuros Gratuitos: Evasión casi permanente
    "Clérigo": (1.5, 1.50, 0.5),    # 400 de maná; Alas de Ángel y Prisa; curaciones propias sin muro
    "Paladín": (0.5, 1.15, 0.0),    # sagrada sin muro, pero ~100 de maná
    "Bardo": (1.0, 1.15, 0.5),      # maná de instrumentos; resto de escuelas con muro
    "Druida": (1.0, 1.15, 0.5),     # Piel de Roble sin muro; reserva de maná en forma
    "Pícaro": (0.5, 1.05, 0.5),     # ~100 de maná (Hoja Arcana: ver abajo)
    "Ranger": (0.5, 1.05, 0.5),
    "Bárbaro": (0.5, 1.05, 0.5),
    "Monje": (0.5, 1.05, 0.5),
    "Maestro del Giro (exp.)": (0.5, 1.05, 0.5),
}
# Subclases con más maná o recarga que su clase.
SUBCLASS_KIT = {"Hoja Arcana": (1.0, 1.15, 0.5)}


def kit_for(name):
    for k, v in SPELL_KIT.items():
        if name.startswith(k):
            return v
    return (0, 1, 0)


def apply_kit(r, kit):
    dm, fr, du = kit
    return row(r["name"], min(10, r["mob"] + dm), r["ehp"] * fr, r["idx"], min(10, r["util"] + du), r["note"])


# Correcciones de clase que el modelo general no ve (se aplican también a sus subclases).
# Bárbaro: crítico del 35 % (no 30 %) con multiplicador ×2,0 → DPS ×1,35/1,30.
# Pícaro: crítico del 25 % con ×2,25 (Filo), compensado en parte por el crítico seguro desde sigilo (DPS ×0,98), y el
# sigilo le quita agro (los mobs casi no lo ven): +25 % de vida efectiva.
CLASS_FIX = {"Bárbaro": (1.0, 1.35 / 1.30), "Pícaro": (1.25, 0.98)}


def apply_fix(r, group):
    fr, fd = CLASS_FIX.get(group, (1.0, 1.0))
    return row(r["name"], r["mob"], r["ehp"] * fr, r["idx"] * fd, r["util"], r["note"])


# Rev. 2.22 (subidas aplicadas): (movilidad +, vida efectiva ×, DPS ×, utilidad +).
# Paladín: Castigo Divino, cada 4.º golpe +40 % sagrado (×2 contra no-muertos y Nether) → DPS ×1,10.
# Bárbaro: Sed de Sangre 10 → 15 % (vida ×1,05), Furia Berserker 60 → 45 s (DPS ×1,04) y el grito hace huir 2 s (+1 util).
# Paladín: velocidad del rasgo base −10 % → −5 % (+0,25 de movilidad).
# Pícaro: sigilo con drenaje del 8 %/s (12,5 s en vez de 10 s): vida efectiva ×1,08.
REV222_CLASS = {"Paladín": (0.25, 1.0, 1.10, 0), "Bárbaro": (0, 1.05, 1.04, 1.0), "Pícaro": (0, 1.08, 1.0, 0)}
# Bastión: Tenacidad devuelve el 20 % de los golpes reducidos (DPS ×1,10).
# Francotirador: Camuflaje (vida ×1,2, +1 util) y Marca de Mira, +50 % al siguiente disparo (DPS ×1,08, +0,5 util).
# Bardo: Cantor de Guerra, Golpe Resonante hasta el 100 % (DPS ×1,06); Encantador, Contagio a 5 y Hechizar 6 s
# (vida ×1,05 y +0,25 util); Juglar, Recital con ICD de 12 s (DPS ×1,04 y +0,25 util).
REV222_SUB = {"Bastión Inflexible": (0, 1.0, 1.10, 0), "Francotirador": (0, 1.2, 1.08, 1.5),
              "Cantor de Guerra": (0, 1.0, 1.06, 0), "Encantador": (0, 1.05, 1.0, 0.25), "Juglar": (0, 1.0, 1.04, 0.25),
              # Vínculo de Sangre: el enemigo vinculado sufre el 25 % de lo que recibe el Mártir; protege a un aliado.
              "Mártir": (0, 1.0, 1.15, 0.5),
              # Ángel Guardián: anula el primer golpe letal del aliado por el que reza (60 s por aliado).
              "Intercesor": (0, 1.05, 1.0, 1.0)}

# Rev. 2.23 (bajadas de las altas): (movilidad +, ×vida, ×DPS, utilidad +)
REV223_CLASS = {"Mago": (0, 1.0, 0.95, 0)}  # rama Reserva +300 -> +250 de maná
# Revisión Improved Armor (26-11-2026): el Paladín ya aguanta ~2 veces lo que el Mago; se compensa con ataque y utilidad.
# Sin −5 % de velocidad (+0,25 de movilidad); Castigo Divino cada 3 golpes y +50 % (antes 4 y +40 %): DPS ×1,061;
# Provocación −30 % de daño durante 6 s (antes −20 % y 4 s): +1,5 de utilidad (protege al grupo).
REVIA_CLASS = {"Paladín": (0.25, 1.0, (1 + 0.5 / 3) / (1 + 0.4 / 4), 1.5)}
REV223_SUB = {"Elementalista": (0, 1.0, 0.96, 0),      # recarga −10 % -> −6 %
              "Cronomante": (0, 1.0, 0.97, 0.25),       # lanzamiento −15 % -> −10 %; Parada Temporal 2 s
              "Berserker Suicida": (0, 0.85, 0.92, 0),  # +25 % -> +15 % de daño; Furia 5 s fijos
              "Maestro de la Brisa (5 cargas)": (-0.5, 1.0, 1.0, 0)}  # velocidad sin armadura +40 % -> +30 %


def apply_rev(r, key, table):
    dm, fr, fd, du = table.get(key, (0, 1.0, 1.0, 0))
    return row(r["name"], min(10, r["mob"] + dm), r["ehp"] * fr, r["idx"] * fd, min(10, r["util"] + du), r["note"])


CLASSES = [apply_rev(apply_rev(apply_rev(apply_fix(apply_kit(r, kit_for(r["name"])), r["name"]), r["name"], REV222_CLASS),
                               r["name"], REV223_CLASS), r["name"], REVIA_CLASS)
           for r in CLASSES]
SUBCLASSES = [(g, apply_rev(apply_rev(apply_fix(apply_kit(apply_kit(r, kit_for(g)), SUBCLASS_KIT.get(r["name"], (0, 1, 0))), g),
                                      g, REV222_CLASS), r["name"], REV222_SUB))
              for g, r in SUBCLASSES]
SUBCLASSES = [(g, apply_rev(apply_rev(apply_rev(r, g, REV223_CLASS), r["name"], REV223_SUB), g, REVIA_CLASS))
              for g, r in SUBCLASSES]

# ---------------------------------------------------------------- cálculo

def tier(delta):
    return "S" if delta >= .08 else "A" if delta >= .03 else "B" if delta > -.03 else "C" if delta > -.08 else "D"


def mean(rows):
    xs = [r["total"] for r in rows if "exp." not in r["name"]]
    return sum(xs) / len(xs)


def base_class():
    core = [r for r in CLASSES if "exp." not in r["name"]]
    n = len(core)
    return dict(mob=sum(r["mob"] for r in core) / n, ehp=math.exp(sum(math.log(r["ehp"]) for r in core) / n),
                idx=sum(r["idx"] for r in core) / n, util=sum(r["util"] for r in core) / n)


def race_row(name, dmob, fres, fdps, dutil, note, base, parent=None):
    b = parent or base
    return row(name, min(10, b["mob"] + dmob), b["ehp"] * fres, b["idx"] * fdps,
               min(10, b["util"] + dutil), note)


def compute():
    base = base_class()
    races = {}
    race_rows = []
    for name, dm, fr, fd, du, note in RACES:
        r = race_row(name, dm, fr, fd, du, note, base)
        races[name] = dict(mob=r["mob"], ehp=r["ehp"], idx=r["idx"], util=r["util"])
        race_rows.append(r)
    sub_rows = []
    for race, name, dm, fr, fd, du in SUBRACES:
        sub_rows.append((race, race_row(f"{race} {name}", dm, fr, fd, du, "", base, races[race])))
    return base, CLASSES, SUBCLASSES, race_rows, sub_rows


def fmt(x, n=2):
    return f"{x:.{n}f}".replace(".", ",")


def table(rows, ref, group=None):
    out = ["| " + ("Grupo | " if group else "") + "Nombre | Movilidad | Resistencia (HP) | DPS (índice) | Utilidad | Total | vs media | Tier |",
           "| " + ("--- | " if group else "") + "--- | --- | --- | --- | --- | --- | --- | --- |"]
    items = sorted(rows, key=lambda gr: -(gr[1] if group else gr)["total"])
    for item in items:
        g, r = item if group else (None, item)
        d = r["total"] / ref - 1
        t = "exp." if "exp." in r["name"] or (g and "exp." in g) else tier(d)
        out.append("| " + (f"{g} | " if group else "") + f"{r['name']} | {fmt(r['mob'], 1)} | {fmt(r['ehp'], 1)} ({fmt(r['res'], 1)}) | "
                   f"{fmt(r['idx'])} ({fmt(r['dps'], 1)}) | {fmt(r['util'], 1)} | {fmt(r['total'])} | {d * 100:+.0f} % | {t} |")
    return "\n".join(out)


def main():
    base, classes, subclasses, races, subraces = compute()
    cm = mean(classes)
    sm = sum(r["total"] for g, r in subclasses if "exp." not in g) / sum(1 for g, _ in subclasses if "exp." not in g)
    rm = mean(races)
    srm = sum(r["total"] for _, r in subraces) / len(subraces)
    print(f"Clase media de referencia: movilidad {fmt(base['mob'], 1)}, vida efectiva {fmt(base['ehp'], 1)}, "
          f"DPS {fmt(base['idx'])}, utilidad {fmt(base['util'], 1)}\n")
    print(f"## Clases (nivel 30), media {fmt(cm)}\n\n{table(classes, cm)}\n")
    print(f"## Subclases (nivel 40), media {fmt(sm)}\n\n{table(subclasses, sm, group=True)}\n")
    print(f"## Razas (rango 3, sobre la clase media), media {fmt(rm)}\n\n{table(races, rm)}\n")
    print(f"## Subrazas (rango 3), media {fmt(srm)}\n\n{table(subraces, srm, group=True)}\n")
    print(f"Monje: 5 cargas = DPS {fmt(M5_FIRE)}, vida efectiva {fmt(M5_STONE, 1)}; "
          f"10 cargas = DPS {fmt(M10_FIRE)}, vida efectiva {fmt(M10_STONE, 1)}")


if __name__ == "__main__":
    main()
