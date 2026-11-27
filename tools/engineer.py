# Ingeniero (adenda del Ingeniero, 27-11-2026): disciplina Pólvora (armas de fuego de NTGL / Create: Gunsmithing).
# Lo importa gen_data.py. Fase 1 del plan técnico: muros de Pólvora de todas las clases y textos de la disciplina.
NS = "rites_and_paths"


def R(x): return f"{NS}:{x}"


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
