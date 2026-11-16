"""Definiciones de los 29 rituales del Plan de Desarrollo v2 (sección 4), en el formato de datapack del mod.

Cada ritual produce data/ironrituals/recipes/rituals/<id>.json (tipo ironrituals:ritual).
"""

ISS = "irons_spellbooks"


def I(item, n=1):
    ns = item if ":" in item else f"minecraft:{item}"
    return {"item": ns, "count": n}


def S(item, n=1):  # ítem de ISS
    return {"item": f"{ISS}:{item}", "count": n}


def T(tag, n=1):
    return {"tag": tag, "count": n}


def ESS(n):
    return S("arcane_essence", n)


def POTION(*potions, n=1):
    return {"ingredient": [{"type": "forge:partial_nbt", "item": "minecraft:potion", "nbt": {"Potion": f"minecraft:{p}"}}
                           for p in potions], "count": n}


def SOUL(**kw):
    return {"soul": kw}


def tier(level, mana, comps, duration=0, **params):
    t = {"level": level, "mana": mana, "components": comps}
    if duration:
        t["duration_seconds"] = duration
    if params:
        t["params"] = params
    return t


def cat(cid, comps, group=None):
    c = {"id": cid, "components": comps}
    if group:
        c["group"] = group
    return c


RITUALS = {
    # ------------------------------------------------------------------ A. Entorno y tiempo
    "sun": dict(school="holy", rarity="common", tiers=[
        tier(1, 100, [I("gold_ingot", 5), I("sunflower", 3), S("divine_pearl")], duration=900, target_time=0)],
        catalysts=[cat("freeze_clock", [I("clock"), I("amethyst_shard", 4)])]),
    "night": dict(school="blood", rarity="common", tiers=[
        tier(1, 100, [S("blood_vial", 4), T("minecraft:beds")], duration=900, target_time=13000)],
        catalysts=[cat("freeze_clock", [I("clock"), I("amethyst_shard", 4)])]),
    "deluge": dict(school="lightning", rarity="common", tiers=[
        tier(1, 100, [I("water_bucket", 5), I("lily_pad", 10), I("ghast_tear")], duration=600)],
        catalysts=[cat("storm", [S("lightning_bottle")])]),

    # ------------------------------------------------------------------ B. Zonas y barreras
    "dome": dict(school="holy", rarity="common", extendable=True, tiers=[
        tier(1, 100, [ESS(5), I("obsidian", 10), I("glass", 10)], duration=3600, radius=7),
        tier(2, 200, [S("arcane_ingot", 2), I("obsidian", 15), I("glass", 20)], duration=3600, radius=15),
        tier(3, 300, [S("mithril_ingot", 2), S("arcane_ingot", 4), I("obsidian", 30), I("glass", 30)], duration=3600, radius=23)],
        catalysts=[cat("blaze", [I("blaze_powder", 4)]), cat("ender_seal", [I("ender_eye")])]),
    "prison": dict(school="ender", rarity="uncommon", tiers=[
        tier(1, 100, [ESS(5), I("obsidian", 10), I("iron_bars", 10)], duration=60, radius=20),
        tier(2, 200, [ESS(15), I("obsidian", 15), I("iron_bars", 20)], duration=180, radius=35),
        tier(3, 300, [ESS(25), I("obsidian", 30), I("iron_bars", 30)], duration=300, radius=50)],
        catalysts=[cat("one_way", [I("phantom_membrane", 5)]),
                   cat("contract", [SOUL(entities=["minecraft:pufferfish"])])]),
    "arcane_suppression": dict(school="eldritch", rarity="rare", extendable=True, tiers=[
        tier(1, 150, [I("gold_block", 4), I("book", 5), I("sponge", 5), I("echo_shard", 8)], duration=1800, radius=21),
        tier(2, 300, [I("gold_block", 8), I("book", 10), I("sponge", 10), I("echo_shard", 16), S("mithril_ingot", 2)], duration=3600, radius=45),
        tier(3, 450, [I("gold_block", 16), I("book", 20), I("sponge", 20), I("echo_shard", 32), S("mithril_ingot", 4)], duration=7200, radius=69)],
        catalysts=[cat("siphon", [{"ingredient": [{"item": "minecraft:dragon_breath"}, {"item": "minecraft:soul_lantern"}], "count": 1}])]),
    "sanctuary": dict(school="holy", rarity="uncommon", extendable=True, tiers=[
        tier(1, 150, [S("divine_pearl", 2), ESS(10), I("gold_ingot", 5)], duration=3600, radius=10),
        tier(2, 250, [S("divine_pearl", 4), ESS(20), I("gold_ingot", 10), S("mithril_ingot")], duration=3600, radius=20),
        tier(3, 350, [S("divine_pearl", 6), ESS(30), I("gold_ingot", 20), S("mithril_ingot", 3)], duration=3600, radius=30)]),
    "arcane_spring": dict(school="ender", rarity="common", extendable=True, tiers=[
        tier(1, 100, [ESS(10), I("amethyst_shard", 3), I("ender_pearl")], duration=1800, radius=8, regen_multiplier=1.5),
        tier(2, 150, [ESS(20), I("amethyst_shard", 6), I("ender_pearl", 2), S("arcane_ingot")], duration=1800, radius=12, regen_multiplier=2.0),
        tier(3, 200, [ESS(30), I("amethyst_shard", 12), I("ender_pearl", 4), S("mithril_ingot")], duration=1800, radius=16, regen_multiplier=3.0)]),
    "sentinel": dict(school="eldritch", rarity="common", extendable=True, tiers=[
        tier(1, 100, [I("ender_eye", 3), I("echo_shard", 3), I("sculk_sensor"), ESS(10)], duration=3600, radius=20),
        tier(2, 150, [I("ender_eye", 6), I("echo_shard", 6), I("sculk_sensor", 2), ESS(20)], duration=3600, radius=40),
        tier(3, 200, [I("ender_eye", 9), I("echo_shard", 9), I("sculk_sensor", 3), ESS(30)], duration=3600, radius=60)]),
    "sepulchral_silence": dict(school="eldritch", rarity="uncommon", tiers=[
        tier(2, 150, [T("minecraft:wool", 4), I("sculk_sensor", 3), ESS(10), I("echo_shard", 2)], duration=300, radius=12)]),
    "inverted_gravity": dict(school="ender", rarity="uncommon", tiers=[
        tier(1, 150, [ESS(10), I("shulker_shell", 2), I("ender_pearl", 2)], duration=300, radius=10),
        tier(2, 250, [ESS(20), I("shulker_shell", 4), I("ender_pearl", 4)], duration=300, radius=20),
        tier(3, 350, [ESS(30), I("shulker_shell", 6), I("ender_pearl", 6)], duration=300, radius=30)],
        catalysts=[cat("crushing", [T("minecraft:anvil")])]),
    "time_distortion": dict(school="ender", rarity="rare", tiers=[
        tier(1, 200, [I("clock"), ESS(8), I("echo_shard", 2), I("amethyst_shard", 4)], duration=120, radius=15, vmin=0.25),
        tier(2, 300, [I("clock", 2), ESS(16), I("echo_shard", 4), I("amethyst_shard", 8), S("arcane_ingot")], duration=300, radius=30, vmin=0.15),
        tier(3, 400, [I("clock", 3), ESS(32), I("echo_shard", 8), I("amethyst_shard", 16), S("mithril_ingot")], duration=600, radius=45, vmin=0.05)],
        catalysts=[cat("haste", [POTION("swiftness", "long_swiftness", "strong_swiftness")])]),

    # ------------------------------------------------------------------ C. Estado del jugador
    "mirror": dict(school="eldritch", rarity="uncommon", tiers=[
        tier(2, 200, [I("ender_pearl", 3), I("echo_shard", 3), ESS(20), I("phantom_membrane", 5)], duration=300)],
        catalysts=[cat("reveal", [I("ender_eye")])]),
    "phoenix": dict(school="fire", rarity="rare", tiers=[
        tier(2, 300, [I("totem_of_undying"), I("feather", 5), I("blaze_rod", 2), S("cinder_essence"), S("divine_pearl")])],
        catalysts=[cat("fire_wave", [I("fire_charge"), I("blaze_rod")])]),
    "metamorphosis": dict(school="blood", rarity="rare", tiers=[
        tier(3, 400, [SOUL(entities=["minecraft:villager"], player=True), S("blood_vial", 8), ESS(16), I("leather", 16)])]),
    "shrinking": dict(school="nature", rarity="common", tiers=[
        tier(1, 100, [I("poisonous_potato"), I("brown_mushroom", 3), ESS(5)], duration=300),
        tier(2, 150, [I("poisonous_potato", 2), I("brown_mushroom", 6), ESS(10), I("phantom_membrane", 2)], duration=900),
        tier(3, 200, [I("poisonous_potato", 3), I("brown_mushroom", 12), ESS(20), I("phantom_membrane", 4)], duration=1800)],
        catalysts=[cat("climb", [I("rabbit_foot")])]),
    "scapegoat": dict(school="blood", rarity="uncommon", tiers=[
        tier(2, 200, [S("blood_vial", 4), I("lead"), ESS(10)], duration=180)],
        catalysts=[cat("player_link", [SOUL(player=True)])]),
    "temporal_paradox": dict(school="ender", rarity="rare", tiers=[
        tier(3, 300, [I("clock"), I("echo_shard", 4), I("ender_pearl", 3), ESS(10)], duration=600)]),
    "soul_anchor": dict(school="holy", rarity="uncommon", tiers=[
        tier(2, 250, [S("divine_pearl", 2), ESS(20), I("diamond_block")])]),

    # ------------------------------------------------------------------ D. Entidades
    "silent_echo": dict(school="eldritch", rarity="uncommon", tiers=[
        tier(2, 150, [SOUL(any=True), I("echo_shard")])],
        catalysts=[cat("leash", [I("lead")])]),
    "eternal_bond": dict(school="holy", rarity="uncommon", tiers=[
        tier(2, 250, [SOUL(tamable=True), I("rotten_flesh", 10), POTION("regeneration", "long_regeneration", "strong_regeneration"), S("divine_pearl")])]),
    "guardian": dict(school="evocation", rarity="uncommon", tiers=[
        tier(1, 200, [ESS(10), I("iron_block", 2), I("emerald")], guardians=1),
        tier(2, 300, [ESS(20), I("iron_block", 4), I("emerald", 2), S("mithril_ingot")], guardians=2),
        tier(3, 400, [ESS(30), I("iron_block", 6), I("emerald", 4), S("mithril_ingot", 2)], guardians=3)]),

    # ------------------------------------------------------------------ E. Viaje
    "planar_nexus": dict(school="ender", rarity="rare", tiers=[
        tier(3, 300, [I("ender_pearl", 8), S("arcane_ingot", 2), I("grass_block", 3), I("netherrack", 3), I("end_stone", 3)])]),
    "intrusion": dict(school="ender", rarity="uncommon", tiers=[
        tier(2, 200, [SOUL(player=True), I("ender_pearl"), T("minecraft:doors")])]),
    "astral_projection": dict(school="eldritch", rarity="uncommon", tiers=[
        tier(2, 200, [I("ender_eye", 3), I("echo_shard", 5), ESS(15), POTION("night_vision", "long_night_vision")], duration=60, radius=120)]),

    # ------------------------------------------------------------------ F. Objetos y terreno
    "eternal_forge": dict(school="fire", rarity="uncommon", tiers=[
        tier(1, 150, [ESS(32), I("diamond", 5)], max_durability=500),
        tier(2, 250, [ESS(64), S("mithril_ingot", 2)], max_durability=1600),
        tier(3, 400, [ESS(128), S("divine_soulshard")], max_durability=2147483647)],
        catalysts=[cat("fireproof", [I("crying_obsidian")])]),
    "terraforming": dict(school="nature", rarity="uncommon", tiers=[
        tier(1, 150, [ESS(20)], radius=8),
        tier(2, 250, [ESS(40)], radius=16),
        tier(3, 350, [ESS(80)], radius=24)],
        catalysts=[cat("profane", [I("blackstone", 16), I("mycelium", 4)], group="biome"),
                   cat("frost", [I("packed_ice", 8), S("frozen_bone", 4)], group="biome"),
                   cat("oasis", [I("bone_meal", 16), I("water_bucket"), T("forge:seeds", 4)], group="biome")]),
    "harvest": dict(school="nature", rarity="common", extendable=True, tiers=[
        tier(1, 100, [I("poisonous_potato"), I("bone_meal", 16), ESS(10)], duration=1800, radius=8),
        tier(2, 150, [I("poisonous_potato", 2), I("bone_meal", 32), ESS(20)], duration=1800, radius=16),
        tier(3, 200, [I("poisonous_potato", 3), I("bone_meal", 64), ESS(30)], duration=1800, radius=24)]),
    "custody": dict(school="holy", rarity="common", tiers=[
        tier(1, 100, [S("arcane_ingot", 2), I("iron_ingot", 4), S("divine_pearl")], seals=4)]),
}


def recipe_json(rid, d):
    tiers = d["tiers"]
    out = {
        "type": "ironrituals:ritual",
        "ritual": f"ironrituals:{rid}",
        "school": f"{ISS}:{d['school']}",
        "min_circle": min(t["level"] for t in tiers),
        "rarity": d["rarity"],
        "extendable": d.get("extendable", False),
        "tiers": tiers,
    }
    if d.get("catalysts"):
        out["catalysts"] = d["catalysts"]
    return out


# Lore (es, en) y pistas de catalizadores (es, en) para el grimorio. No explican el efecto técnico.
LORE = {
    "sun": ("Los sacerdotes del alba guardaban oro en sus tumbas para que el sol los encontrara al despertar.",
            "The priests of dawn kept gold in their tombs so the sun would find them upon waking."),
    "night": ("Un vial de sangre sobre una cama vacía: así pedían los antiguos que el día callara.",
              "A vial of blood on an empty bed: so the ancients begged the day to fall silent."),
    "deluge": ("Las brujas del pantano no rezaban por lluvia: la convocaban con lo que el agua deja atrás.",
               "The swamp witches did not pray for rain: they summoned it with what water leaves behind."),
    "dome": ("Los muros más fuertes no son de piedra, sino de cristal que la fe ha endurecido.",
             "The strongest walls are not of stone, but of glass hardened by faith."),
    "prison": ("Los carceleros del End no construían celdas: doblaban el espacio hasta que no quedara salida.",
               "The jailers of the End built no cells: they folded space until no exit remained."),
    "arcane_suppression": ("Hubo una orden que temía a la magia más que a la muerte. Este fue su legado.",
                           "There was an order that feared magic more than death. This was their legacy."),
    "sanctuary": ("Donde un santo durmió, la tierra recuerda su paz durante generaciones.",
                  "Where a saint once slept, the earth remembers their peace for generations."),
    "arcane_spring": ("Bajo las torres de los magos siempre hubo una fuente que nadie veía beber.",
                      "Beneath the wizards' towers there was always a spring no one was seen drinking from."),
    "sentinel": ("El ojo del Ender no parpadea; solo aprende a quién debe mirar.",
                 "The Ender eye never blinks; it only learns whom it must watch."),
    "sepulchral_silence": ("En las ciudades antiguas, los que sobrevivieron aprendieron a no hacer ningún ruido.",
                           "In the ancient cities, those who survived learned to make no sound at all."),
    "inverted_gravity": ("Los shulkers flotan porque recuerdan un mundo donde el suelo estaba arriba.",
                         "Shulkers float because they remember a world where the ground was above."),
    "time_distortion": ("Un reloj roto en el centro de un círculo hace que el mundo entero dude.",
                        "A broken clock at the heart of a circle makes the whole world hesitate."),
    "mirror": ("Al otro lado del cristal hay un mundo gris donde los vivos son solo sombras.",
               "Beyond the glass lies a grey world where the living are only shadows."),
    "phoenix": ("Ningún fénix muere dos veces; el primero que lo intentó sigue ardiendo.",
                "No phoenix dies twice; the first one who tried is still burning."),
    "metamorphosis": ("Los magos de sangre aprendieron que un alma ajena puede vestir tu forma.",
                      "Blood mages learned that a borrowed soul can wear your shape."),
    "shrinking": ("Los druidas del bosque oscuro se hacían pequeños para escuchar a las raíces.",
                  "The druids of the dark forest made themselves small to listen to the roots."),
    "scapegoat": ("Todo dolor busca un cuerpo. Este ritual solo le enseña cuál.",
                  "All pain seeks a body. This ritual only teaches it which one."),
    "temporal_paradox": ("Quien guarda un instante en arena puede volver a él una sola vez.",
                         "Whoever keeps a moment in sand may return to it, but only once."),
    "soul_anchor": ("Un diamante no se pudre. Atado al alma, tampoco lo hace lo que llevas contigo.",
                    "A diamond does not rot. Bound to the soul, neither does what you carry."),
    "silent_echo": ("El eco de una criatura puede quedarse aunque la criatura ya no esté.",
                    "The echo of a creature can linger even when the creature is gone."),
    "eternal_bond": ("Los guardianes de las bestias nunca enterraban a sus compañeros: esperaban.",
                     "The keepers of beasts never buried their companions: they waited."),
    "guardian": ("Los evocadores no forjaban soldados; los llamaban desde el hierro dormido.",
                 "The evokers did not forge soldiers; they called them from sleeping iron."),
    "planar_nexus": ("Tres piedras de tres mundos, y la distancia deja de tener sentido.",
                     "Three stones from three worlds, and distance ceases to have meaning."),
    "intrusion": ("Toda alma deja una puerta entreabierta. Solo hay que saber su nombre.",
                  "Every soul leaves a door ajar. One only needs to know its name."),
    "astral_projection": ("Los videntes dejaban su cuerpo junto a las velas y volvían con secretos.",
                          "The seers left their bodies beside the candles and returned with secrets."),
    "eternal_forge": ("El primer herrero arcano templó su martillo en una llama que aún no se ha apagado.",
                      "The first arcane smith tempered his hammer in a flame that has yet to go out."),
    "terraforming": ("La tierra no olvida; solo espera que alguien le recuerde otro paisaje.",
                     "The earth does not forget; it only waits for someone to remind it of another landscape."),
    "harvest": ("Los alquimistas del valle tenían tres cosechas al año y ninguna estación.",
                "The valley alchemists had three harvests a year and no seasons."),
    "custody": ("Un sello arcano no impide abrir: impide querer hacerlo.",
                "An arcane seal does not prevent opening: it prevents wanting to."),
}

CATALYST_HINTS = {
    ("sun", "freeze_clock"): ("Si el reloj escucha a la amatista, el sol olvida moverse.", "If the clock listens to the amethyst, the sun forgets to move."),
    ("night", "freeze_clock"): ("Si el reloj escucha a la amatista, la luna no se marcha.", "If the clock listens to the amethyst, the moon does not leave."),
    ("deluge", "storm"): ("El cielo también puede embotellarse.", "The sky, too, can be bottled."),
    ("dome", "blaze"): ("El polvo del Nether enseña al muro a morder.", "Nether dust teaches the wall to bite."),
    ("dome", "ender_seal"): ("Un ojo vigilante cierra los atajos.", "A watchful eye closes the shortcuts."),
    ("prison", "one_way"): ("La piel de los que vuelan de noche solo deja entrar.", "The skin of those who fly by night only lets in."),
    ("prison", "contract"): ("El alma de lo que se hincha hace que la jaula encoja.", "The soul of that which swells makes the cage shrink."),
    ("arcane_suppression", "siphon"): ("Lo que se apaga puede guardarse en un farol o en un aliento.", "What is snuffed out can be kept in a lantern or a breath."),
    ("inverted_gravity", "crushing"): ("El hierro pesado recuerda al mundo su peso.", "Heavy iron reminds the world of its weight."),
    ("time_distortion", "haste"): ("Quien bebe prisa no siente la arena.", "Who drinks haste does not feel the sand."),
    ("mirror", "reveal"): ("Un ojo más ve a través de la piedra.", "One more eye sees through stone."),
    ("phoenix", "fire_wave"): ("Una carga y una vara: el renacer puede quemar.", "A charge and a rod: rebirth can burn."),
    ("shrinking", "climb"): ("La pata de la suerte no teme a los muros.", "The lucky foot fears no wall."),
    ("scapegoat", "player_link"): ("Un alma con nombre puede cargar con tu pena desde lejos.", "A soul with a name can bear your pain from afar."),
    ("silent_echo", "leash"): ("Una cuerda enseña al eco a seguirte.", "A rope teaches the echo to follow you."),
    ("eternal_forge", "fireproof"): ("La obsidiana que llora protege del fuego.", "Obsidian that weeps shields against fire."),
    ("terraforming", "profane"): ("Piedra negra y hongo: la tierra se oscurece.", "Black stone and fungus: the earth darkens."),
    ("terraforming", "frost"): ("Hielo y hueso helado: la tierra se congela.", "Ice and frozen bone: the earth freezes."),
    ("terraforming", "oasis"): ("Hueso, agua y semilla: la tierra florece.", "Bone, water and seed: the earth blooms."),
}

# Probabilidad por rareza y tablas de ISS donde aparece (verificadas con la sonda).
LOOT = {
    "common": (0.25, [
        "chests/mangrove_hut", "chests/priest_house", "chests/evoker_fort", "chests/evoker_fort/guard_tower",
        "chests/mountain_tower/mountain_tower", "chests/pyromancer_tower/pyromancer_supplies", "chests/catacombs/crypt_loot",
        "chests/magic_bookshelf_loot", "chests/citadel/citadel_bookshelf"]),
    "uncommon": (0.20, [
        "chests/catacombs/coffin_loot", "chests/catacombs/hidden_trough_treasure", "chests/citadel/rampart_chest",
        "chests/ice_spider_den/dungeon", "chests/impaled_icebreaker/captain_quarters", "chests/battleground/burial_loot",
        "chests/generic_magic_treasure", "chests/citadel/citadel_tomes"]),
    "rare": (0.35, [
        "chests/catacombs/dead_king_vault", "chests/citadel/citadel_vault", "chests/citadel/spawner_reward",
        "chests/ice_spider_den/spawner_reward", "chests/trial_chambers/additional_ominous_vault_loot"]),
}
