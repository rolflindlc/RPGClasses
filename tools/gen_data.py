import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import texts
out = sys.argv[1]
NS = "rpgclasses"
def R(x): return f"{NS}:{x}"
def attr(mods, cond=None):
    e = {"type": R("attribute")}
    if cond: e["condition"] = R(cond)
    e["modifiers"] = [dict(attribute=a, **v) for a, v in mods]
    return e
def to(v, op="add"): return {"to": v, "operation": op}
def mech(i, **p): return {"type": R("mechanic"), "id": R(i), **p}
def ms(level, grants, sub=False, upgrades=None):
    m = {("subclass_level" if sub else "total_level"): level, "grants": R(grants)}
    if upgrades: m["upgrades"] = R(upgrades)
    return m
MC = "minecraft:generic."; IR = "irons_spellbooks:"
NOARM = "no_armor"

classes = {
 "mage": dict(icon="minecraft:enchanted_book", roles=["magia","daño"], kit="mage", crit=0.0,
   walls=dict(bow=.25, heavy=.5, light=.5, armor=.25, shield=.5),
   branches=[("power",[attr([(IR+"spell_power", to(.5,"multiply_base"))])]),
             ("reserve",[attr([(IR+"max_mana", to(300)), (IR+"mana_regen", to(.4,"multiply_base"))])]),
             ("cadence",[attr([(IR+"cooldown_reduction", to(.25,"multiply_base"))])])],
   milestones=[ms(5,"free_cast_slot_1"), ms(15,"free_cast_slot_2"), ms(30,"free_cast_slot_3")],
   subs=["chaotic_evoker","elementalist","battle_mage"]),
 "paladin": dict(icon="minecraft:shield", roles=["tanque","apoyo"], kit="paladin", crit=0.0,
   walls=dict(magic=.75, bow=.75),
   branches=[("cuirass",[{"type":R("equipped_armor_multiplier"),"armor":{"to":.25},"toughness":{"to":.4}}]),
             ("will",[attr([(R("debuff_resistance"), to(.5))])]),
             ("push",[attr([(MC+"knockback_resistance", to(.5)), (R("knockback_dealt"), to(.25))])])],
   milestones=[ms(10,"taunt"), ms(10,"steady_guard"), ms(30,"bulwark")],
   subs=["blessed_guardian","unyielding_bastion","charging_juggernaut"]),
 "barbarian": dict(icon="minecraft:iron_axe", roles=["melee","daño"], kit="barbarian", crit=0.05, armor_rule=True,
   walls=dict(magic=.6, bow=.45, light=.45),
   branches=[("fury",[{"type":R("wound_fury"),"per_missing_health_pct":{"from":.001,"to":.01},"cap":.75}]),
             ("brutality",[attr([(R("crit_damage"), to(.5))])]),
             ("iron_skin",[attr([(MC+"armor", to(16)), (MC+"armor_toughness", to(6)), (MC+"movement_speed", to(.25,"multiply_base"))], NOARM)])],
   milestones=[ms(5,"bloodthirst"), ms(15,"charge"), ms(30,"berserker_rage")],
   subs=["indestructible","suicidal_berserker","plague_corruptor"]),
 "rogue": dict(icon="minecraft:iron_sword", roles=["melee","sigilo"], kit="rogue", crit=-0.05,
   walls=dict(magic=.45, bow=.35, heavy=.55, armor=.40, shield=.70),
   branches=[("shadow",[attr([(R("mob_detection"), to(-.5)), (R("backstab_crit_damage"), to(1.0))])]),
             ("edge",[attr([(MC+"attack_speed", to(.25,"multiply_base")), (R("crit_damage"), to(.75))])]),
             ("wind",[attr([(MC+"movement_speed", to(.35,"multiply_base"))])])],
   milestones=[ms(5,"stealth")],
   subs=["shadow_walker","shadow_illusionist","arcane_blade"]),
 "ranger": dict(icon="minecraft:bow", roles=["distancia","daño"], kit="ranger", crit=0.0,
   walls=dict(magic=.4, heavy=.45, light=.35, shield=.30),
   branches=[("aim",[attr([(R("bow_damage"), to(.3)), (R("zoom"), to(.35))])]),
             ("cadence",[attr([(R("draw_speed"), to(.3))])]),
             ("range",[attr([(R("projectile_velocity"), to(1.0))])])],
   milestones=[ms(5,"ammo_conservation"), ms(15,"double_jump"), ms(30,"heavy_shot")],
   subs=["beast_master","agile_scout","sniper"]),
 "monk": dict(icon="minecraft:leather", roles=["melee","movilidad"], kit=None, crit=0.0, armor_rule=True,
   walls=dict(magic=.7, bow=.3, heavy=.35, light=.4, shield=1.0),
   branches=[("fist",[attr([(R("unarmed_damage"), to(6.0))])]),
             ("wind",[attr([(MC+"movement_speed", to(.35,"multiply_base")), (R("jump_bonus"), to(.35))], NOARM)]),
             ("skin",[attr([(MC+"armor", to(16)), (MC+"armor_toughness", to(6))], NOARM)])],
   milestones=[ms(5,"stances"), ms(15,"deflect"), ms(30,"light_step")],
   subs=["breeze_master","heavy_palm","triworld_guardian"]),
}

# --- Rev. 2.7c: rasgos base (regla 12), ramas extra (regla 13) y perks (regla 11) ---
def mb(v): return to(v, "multiply_base")
TRAITS = {
 "mage": [attr([(MC+"max_health", mb(-.2))])],
 "paladin": [attr([(MC+"max_health", mb(.2)), (MC+"movement_speed", mb(-.1))])],
 "rogue": [attr([(MC+"max_health", mb(-.15))])],
}
EXTRA_BRANCHES = {
 "mage": [("summoning",[mech("summon_bonus", summon_health={"to":.4}, summon_damage={"to":.4})]),
          ("fluidity",[attr([(MC+"movement_speed", mb(.15))]), mech("cast_no_slow", min_points=10)])],
 "paladin": [("banner",[mech("banner_aura", radius=8, aura_armor={"to":.1}, aura_debuff_duration={"to":-.1})]),
             ("smite",[mech("smite", undead_nether_damage={"to":.25}, no_shield_damage={"to":.1})])],
 "barbarian": [("stampede",[mech("charge_upgrade", distance_bonus={"to":.5}, cooldown_change={"to":-.25})]),
               ("hardened",[mech("hardened", hazard_reduction={"to":.4}, hunger_reduction={"to":.4})])],
 "rogue": [("toxins",[mech("toxin", chance={"to":.3}, toxin_damage=1, interval_seconds=3, duration_seconds=6)]),
           ("acrobatics",[mech("acrobatics", stamina_cost={"to":-.3}, dodge_invulnerability={"to":.25})])],
 "ranger": [("special_arrows",[mech("potion_arrows", potion_bonus={"to":.25})]),
            ("retreat",[mech("retreat", speed_bonus=.25, duration_seconds=2, icd_seconds=5, min_points=10)])],
 "monk": [("stance_mastery",[mech("stance_mastery", stance_bonus={"to":.2})]),
          ("meditation",[mech("meditation", delay_seconds=2, heal_per_second={"to":.5}, health_limit=.6)])],
}
SLOWS = ["minecraft:slowness","minecraft:weakness","minecraft:blindness","minecraft:mining_fatigue","minecraft:nausea","minecraft:darkness"]
PERKS = {
 ("mage","power"): [("ranged_power",[mech("distance_power", near_blocks=6, far_blocks=15, near_bonus=-.2, far_bonus=.35)]),
                    ("point_blank_power",[mech("distance_power", near_blocks=6, far_blocks=15, near_bonus=.4, far_bonus=-.2)])],
 ("mage","reserve"): [("mana_burst",[attr([(IR+"max_mana", to(-150)), (IR+"mana_regen", mb(.6))])]),
                      ("mana_well",[attr([(IR+"max_mana", to(200)), (IR+"mana_regen", mb(-.4))])])],
 ("mage","cadence"): [("arcane_cadence",[attr([(IR+"cooldown_reduction", mb(.25)), (IR+"spell_power", mb(-.1))])]),
                      ("blood_magic",[mech("blood_magic", mana_per_health=15, min_health_fraction=.25)])],
 ("mage","summoning"): [("legion",[mech("summon_count", count_change=1, summon_stats=-.25)]),
                        ("elite",[mech("summon_count", count_change=-1, min_count=1, summon_stats=.3, summon_mana_cost=.15)])],
 ("mage","fluidity"): [("arcane_step",[mech("cast_speed_burst", speed_bonus=.3, duration_seconds=2, icd_seconds=4)]),
                       ("feather_fall",[mech("feather_fall"), attr([(MC+"movement_speed", mb(-.1))])])],
 ("paladin","cuirass"): [("heavy_cuirass",[{"type":R("equipped_armor_multiplier"),"armor":{"to":.2},"toughness":{"to":.2}}, attr([(MC+"movement_speed", mb(-.05))])]),
                         ("colossal_body",[attr([(MC+"max_health", mb(.2)), (MC+"movement_speed", mb(-.05))])])],
 ("paladin","will"): [("will_vs_damage",[mech("effect_immunity", effects=["minecraft:poison","minecraft:wither","rpgclasses:toxin"], fire=1, other_duration=.5)]),
                      ("will_vs_control",[mech("effect_immunity", effects=SLOWS, other_duration=.5)])],
 ("paladin","push"): [("shield_counter",[mech("shield_counter", knockback_blocks=3, icd_seconds=3)]),
                      ("shockwave",[mech("shockwave", every_hits=5, radius=3, damage_fraction=.3)])],
 ("paladin","banner"): [("war_banner",[mech("war_banner", ally_damage=.08), attr([(MC+"armor", to(-.15,"multiply_total"))])]),
                        ("life_banner",[mech("life_banner", ally_heal_per_second=.5)])],
 ("paladin","smite"): [("judgement",[mech("judgement", every_hits=6, damage_bonus=.4, radius=2), attr([(MC+"attack_damage", mb(-.1))])]),
                       ("condemn",[mech("condemn", duration_seconds=6, damage_bonus=.15)])],
 ("barbarian","fury"): [("unleashed_fury",[mech("fury_perk", damage_per_missing_pct=.005), attr([(R("healing_received"), to(-.5))])]),
                        ("tempered_fury",[mech("fury_perk", damage_per_missing_pct=-.0015, healing_per_missing_pct=.005)])],
 ("barbarian","brutality"): [("crushing_blow",[attr([(R("crit_damage"), to(.75)), (R("crit_chance"), to(-.15))])]),
                             ("steady_blow",[attr([(R("crit_damage"), to(-.35)), (R("crit_chance"), to(.2))])])],
 ("barbarian","iron_skin"): [("reinforced_skin",[attr([(MC+"armor", to(2)), (MC+"armor_toughness", to(1)), (MC+"movement_speed", mb(-.15))], NOARM)]),
                             ("light_skin",[attr([(MC+"armor", to(-2)), (MC+"armor_toughness", to(-1)), (MC+"movement_speed", mb(.15))], NOARM)])],
 ("barbarian","stampede"): [("unstoppable_charge",[mech("unstoppable_charge", damage_fraction=.6)]),
                            ("crushing_charge",[mech("crushing_charge", stun_factor=2, cooldown_change=.3, wave_radius=3)])],
 ("barbarian","hardened"): [("carnivore",[mech("carnivore", meat_saturation=1.0, other_food=-.3)]),
                            ("endurance",[mech("endurance", health_threshold=.3, damage_reduction=.2, duration_seconds=5, icd_seconds=30), attr([(R("healing_received"), to(-.1))])])],
 ("rogue","shadow"): [("treacherous_strike",[mech("treacherous_strike", damage_bonus=.25, pvp_window_seconds=5, stealth_duration=-.2)]),
                      ("deep_stealth",[mech("deep_stealth", stealth_duration=.4), attr([(R("mob_detection"), to(.2))])])],
 ("rogue","edge"): [("flurry",[attr([(MC+"attack_speed", mb(.25)), (MC+"attack_damage", mb(-.12))])]),
                    ("thrust",[mech("armor_pierce", armor_ignored=.3), attr([(MC+"attack_speed", mb(-.15))])])],
 ("rogue","wind"): [("evasion",[mech("evasion", chance=.15, leap_blocks=1, icd_seconds=1), attr([(MC+"max_health", mb(-.1))])]),
                    ("lightness",[attr([(MC+"movement_speed", mb(.1)), (MC+"attack_speed", mb(.1))]), mech("stealth_drain", drain_change=.3)])],
 ("rogue","toxins"): [("concentrated_dose",[mech("toxin_variant", toxin_damage=1, interval_seconds=1, duration_seconds=2)]),
                      ("prolonged_dose",[mech("toxin_variant", toxin_damage=.5, interval_seconds=3, duration_seconds=12, slowness_level=1)])],
 ("rogue","acrobatics"): [("counterattack",[mech("dodge_counter", damage_bonus=.3, window_seconds=3)]),
                          ("long_roll",[mech("long_roll", dodge_distance=.5, dodge_invulnerability=-.15)])],
 ("ranger","aim"): [("steady_shooter",[mech("steady_shooter", still_seconds=1, still_bonus=.25, moving_penalty=-.15)]),
                    ("mobile_shooter",[mech("mobile_shooter", moving_bonus=.2, still_penalty=-.15)])],
 ("ranger","cadence"): [("agile_shooter",[mech("draw_no_slow"), attr([(R("draw_speed"), to(-.15))])]),
                        ("quick_counter",[mech("quick_counter", window_seconds=3, damage_fraction=.7, icd_seconds=8)])],
 ("ranger","range"): [("piercing",[mech("piercing", extra_targets=1, second_damage=.6, first_damage=-.1)]),
                      ("hunters_mark",[mech("hunters_mark", duration_seconds=6, icd_seconds=10, damage_bonus=.12)])],
 ("ranger","special_arrows"): [("explosive_arrow",[mech("explosive_arrow", radius=2, damage_fraction=.3, direct_damage=-.1)]),
                               ("sticky_arrow",[mech("sticky_arrow", slowness_seconds=2), attr([(R("projectile_velocity"), to(-.1))])])],
 ("ranger","retreat"): [("evasive_leap",[mech("evasive_leap", leap_blocks=4, icd_seconds=12)]),
                        ("hunter_trap",[mech("hunter_trap", duration_seconds=3, slowness_level=2, icd_seconds=12)])],
 ("monk","fist"): [("flow",[mech("flow", switch_cooldown_seconds=3, linger_seconds=3, linger_factor=.5, unarmed_damage_change=-.05)]),
                   ("elemental_wave",[mech("elemental_wave", radius=3, damage_fraction=.75, switch_cooldown_seconds=7)])],
 ("monk","wind"): [("running_momentum",[mech("running_momentum", sprint_seconds=2, damage_bonus=.3, knockback_bonus=.5)]),
                   ("light_feet",[mech("light_feet"), attr([(MC+"movement_speed", mb(-.1))])])],
 ("monk","skin"): [("spirit_skin",[attr([(MC+"armor", to(-2)), (MC+"armor_toughness", to(-1))], NOARM), mech("spirit_skin", magic_reduction=.3, combined_cap=.85)]),
                   ("inner_calm",[mech("inner_calm", absorb_per_second=.5, absorb_max=5)])],
 ("monk","stance_mastery"): [("dominant_stance",[mech("dominant_stance", favorite_bonus=.5, others_bonus=-.25)]),
                             ("hybrid_stance",[mech("hybrid_stance", stance_factor=.5, switch_cooldown_change=2)])],
 ("monk","meditation"): [("deep_trance",[mech("meditation_variant", heal_per_second_fixed=1, delay_seconds=4)]),
                         ("clear_mind",[mech("meditation_variant", purge_interval_seconds=5, heal_factor=.5)])],
}
for cid, extra in EXTRA_BRANCHES.items(): classes[cid]["branches"] = classes[cid]["branches"] + extra
for cid, c in classes.items():
    for b, _ in c["branches"]: assert (cid, b) in PERKS, (cid, b)

subs = {
 "chaotic_evoker": ("mage","minecraft:fire_charge",[attr([(R("spell_size"), to(.3))]),
     mech("wild_magic", chance_per_mana=.0033, chance_cap=.5, replica_damage=.33, directions=8, icd_ticks=30)], []),
 "elementalist": ("mage","minecraft:blue_ice",[attr([(IR+"cooldown_reduction", to(.10,"multiply_base"))]),
     mech("elemental_debuffs", chance={"from":.4,"to":.75}, max_stacks={"values":[3,3,3,4,4,4,4,5,5,5]},
          duration_seconds={"values":[4,4,4,6,6,6,6,8,8,8]}, refresh_from_level=8)], []),
 "battle_mage": ("mage","minecraft:iron_chestplate",[mech("unbreakable_concentration"),
     mech("wall_mitigation", disciplines=[R("heavy"),R("light")], mitigation={"to":1.0}),
     mech("arcane_shield", mana_fraction={"from":.03,"to":.10}, cap_health_fraction=.5, decay_delay_seconds=6, decay_per_second=.1)], []),
 "blessed_guardian": ("paladin","minecraft:golden_apple",[attr([(R("healing_received"), to(.5))])],
     [ms(5,"healing_bulwark",True,"bulwark")]),
 "unyielding_bastion": ("paladin","minecraft:obsidian",[attr([(MC+"knockback_resistance", to(1.0))]),
     mech("tenacity", window_ticks=20, reduction=.5)], [ms(5,"incorruptible_bulwark",True,"bulwark")]),
 "charging_juggernaut": ("paladin","minecraft:anvil",[attr([(MC+"max_health", to(.6,"multiply_base"))])],
     [ms(5,"charging_bulwark",True,"bulwark")]),
 "indestructible": ("barbarian","minecraft:totem_of_undying",[attr([(MC+"max_health", to(10))]),
     mech("death_gamble", chances=[.75,.45,.15], area_damage=1, radius=3, knockback_blocks=5)], []),
 "suicidal_berserker": ("barbarian","minecraft:netherite_axe",[attr([(MC+"attack_damage", to(.25,"multiply_base"))]),
     mech("last_immortal_fury", min_seconds=7, max_seconds=17, cooldown_seconds=300, weakness_seconds=10)], []),
 "plague_corruptor": ("barbarian","minecraft:rotten_flesh",[
     mech("resistant_blood", effects=["minecraft:hunger","minecraft:poison","minecraft:wither"], duration_reduction={"to":1.0}),
     mech("afflicting_strike", damage_per_second=2, duration_seconds=6, boss_factor=.5, contagion_radius=5)], []),
 "shadow_walker": ("rogue","minecraft:ender_pearl",[attr([(R("sneak_speed"), to(1.0))])], [ms(1,"shadow_step",True)]),
 "shadow_illusionist": ("rogue","minecraft:armor_stand",[attr([(R("mob_detection"), to(-.25))])], [ms(1,"decoy",True)]),
 "arcane_blade": ("rogue","minecraft:amethyst_shard",[
     attr([(IR+"cooldown_reduction", to(.15,"multiply_base")), (IR+"spell_power", to(.15,"multiply_base")),
           (IR+"mana_regen", to(.15,"multiply_base")), (IR+"max_mana", to(150))]),
     mech("wall_mitigation", disciplines=[R("magic")], mitigation={"to":1.0}),
     mech("arcane_ambush", damage_bonus=.6, icd_seconds=8, stealth_cost=.4)], []),
 "beast_master": ("ranger","minecraft:bone",[
     mech("wild_bond", health=.35, damage=.35, speed=.25, max_pets=8),
     mech("beast_safeguard", cooldown_seconds=300, idle_seconds=60)], []),
 "agile_scout": ("ranger","minecraft:feather",[
     attr([(MC+"movement_speed", to(.10,"multiply_base"))])],
     [ms(1,"triple_jump",True,"double_jump"), ms(1,"wall_climb",True)]),
 "sniper": ("ranger","minecraft:spyglass",[
     attr([(R("projectile_velocity"), to(2.0)), (R("bow_damage"), to(.45))]),
     mech("draw_speed_cut", fraction_removed={"to":1.0})], [ms(1,"lethal_shot",True,"heavy_shot")]),
 "breeze_master": ("monk","minecraft:phantom_membrane",[
     attr([(MC+"movement_speed", to(.4,"multiply_base")), (R("jump_bonus"), to(.8))], NOARM)], [ms(1,"wind_dash",True)]),
 "heavy_palm": ("monk","minecraft:piston",[
     attr([(R("unarmed_knockback"), to(1.0)), ("forge:entity_reach", to(.5,"multiply_base"))]),
     mech("disabling_fist", chance=.1, icd_seconds=6, duration_seconds=1.5)], []),
 "triworld_guardian": ("monk","minecraft:heart_of_the_sea",[
     mech("stance_affinity", fire_damage_reduction={"to":.75}, projectile_reduction=.33, melee_reduction=.2),
     mech("elemental_manifest", ignite_seconds=6, swim_bonus=1.0, reflect_fraction=.15)], []),
}

def armor(material): return [{"item": f"minecraft:{material}_{piece}", "slot": slot} for piece, slot in
                         (("helmet","head"),("chestplate","chest"),("leggings","legs"),("boots","feet"))]
KITS = {
 "mage": [{"item": IR+"copper_spell_book", "slot": "inventory", "spells": [{"id": IR+"magic_missile", "level": 1}]}] + armor("leather"),
 "paladin": [{"item": "minecraft:stone_sword", "slot": "mainhand"}] + armor("chainmail"),
 "barbarian": [{"item": "minecraft:iron_axe", "slot": "mainhand"}],
 "rogue": [{"item": "minecraft:iron_sword", "slot": "mainhand"}] + armor("leather"),
 "ranger": [{"item": "minecraft:bow", "slot": "mainhand"}, {"item": "minecraft:arrow", "count": 64, "slot": "inventory"}] + armor("leather"),
}

def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2); f.write("\n")

base = os.path.join(out, "data", NS, NS)
for cid, c in classes.items():
    o = {"schema_version": 1, "display": {"icon": c["icon"], "roles": c["roles"]}}
    if KITS.get(cid): o["starting_kit"] = KITS[cid]
    o["crit_chance_bonus"] = c["crit"]
    o["walls"] = {R(k): v for k, v in c["walls"].items()}
    # Regla 14: la escuela sagrada de Iron's solo es del Paladín; su muro de Magia no la toca.
    if cid == "paladin": o["magic_wall_exempt_schools"] = [IR + "holy"]
    else: o["school_walls"] = {IR + "holy": 1.0}
    if TRAITS.get(cid): o["traits"] = TRAITS[cid]
    if c.get("armor_rule"): o["armor_rule"] = "disables_unarmored_passives"
    o["branches"] = [{"id": b, "max_points": 10, "effects": e,
                      "perks": [{"id": pid, "effects": pe} for pid, pe in PERKS[(cid, b)]]} for b, e in c["branches"]]
    o["milestones"] = c["milestones"]
    o["subclasses"] = [R(s) for s in c["subs"]]
    dump(os.path.join(base, "classes", cid + ".json"), o)
for sid, (parent, icon, effects, miles) in subs.items():
    dump(os.path.join(base, "subclasses", sid + ".json"),
         {"schema_version": 1, "class": R(parent), "display": {"icon": icon}, "effects": effects, "milestones": miles})

names = {
 "class": {"mage":"Mago","paladin":"Paladín","barbarian":"Bárbaro","rogue":"Pícaro","ranger":"Ranger","monk":"Monje"},
 "subclass": {"chaotic_evoker":"Evocador Caótico","elementalist":"Elementalista","battle_mage":"Mago de Batalla",
   "blessed_guardian":"Guardián Bendito","unyielding_bastion":"Bastión Inflexible","charging_juggernaut":"Juggernaut de Carga",
   "indestructible":"Indestructible","suicidal_berserker":"Berserker Suicida","plague_corruptor":"Corruptor de Plagas",
   "shadow_walker":"Caminante de Sombras","shadow_illusionist":"Ilusionista de Sombras","arcane_blade":"Hoja Arcana",
   "beast_master":"Maestro de Bestias","agile_scout":"Explorador Ágil","sniper":"Francotirador",
   "breeze_master":"Maestro de la Brisa","heavy_palm":"Palma Pesada","triworld_guardian":"Guardián Trimundial"},
}
branch_names = {"mage":{"power":"Poder","reserve":"Reserva","cadence":"Cadencia"},
 "paladin":{"cuirass":"Coraza","will":"Voluntad","push":"Empuje"},
 "barbarian":{"fury":"Furia","brutality":"Brutalidad","iron_skin":"Piel de Hierro"},
 "rogue":{"shadow":"Sombra","edge":"Filo","wind":"Viento"},
 "ranger":{"aim":"Puntería","cadence":"Cadencia","range":"Alcance"},
 "monk":{"fist":"Puño","wind":"Viento","skin":"Piel"}}
for (cid, bid), name in texts.EXTRA_BRANCH_NAMES.items(): branch_names[cid][bid] = name
lang = {}
for kind, m in names.items():
    for k, v in m.items(): lang[f"{kind}.{NS}.{k}.name"] = v
for c, m in branch_names.items():
    for b, v in m.items(): lang[f"branch.{NS}.{c}.{b}.name"] = v
results = {"ok":"Hecho.","no_class":"Aún no has elegido clase.","already_has_class":"Ya tienes una clase. Reiníciala para elegir otra.",
 "unknown_class":"Esa clase no existe o está desactivada.","unknown_branch":"Esa rama no existe en tu clase.","branch_full":"Esa rama ya tiene 10 puntos.",
 "subclass_locked":"La subclase se desbloquea con 15 puntos de clase base.","unknown_subclass":"Esa subclase no pertenece a tu clase.",
 "already_has_subclass":"Ya tienes una subclase. Reiníciala para elegir otra.","no_subclass":"Aún no has elegido subclase.",
 "subclass_cap":"Necesitas más puntos de clase base para subir la subclase.","not_enough_xp":"No tienes suficiente XP.",
 "in_combat":"No puedes hacer eso en combate.",
 "branch_locked":"Ya usas 3 ramas: las demás están bloqueadas hasta que reinicies la clase.",
 "unknown_perk":"Ese perk no existe en esa rama.","perk_unavailable":"Llena la rama (10 puntos) para elegir su perk.",
 "perk_already_chosen":"Ya elegiste el perk de esa rama. Solo cambia al reiniciar la clase.","class_disabled":"Esa clase está desactivada en este servidor."}
for k, v in results.items(): lang[f"msg.{NS}.result.{k}"] = v
lang.update({
 f"msg.{NS}.choose_prompt": "Elige tu clase (haz clic en una):",
 f"msg.{NS}.xp_charged": "Has gastado %s de XP. Siguiente nivel: %s de XP.",
 f"msg.{NS}.xp_refunded": "Reinicio hecho. Se te han devuelto %s de XP (se pierde una parte en cada reinicio).",
 f"msg.{NS}.info": "Clase: %s | Base: %s/30 | Subclase: %s (%s/%s) | %s",
 f"msg.{NS}.next_cost": "Siguiente nivel: %s XP",
 f"msg.{NS}.max_level": "Nivel máximo alcanzado",
 f"msg.{NS}.xp_charged_max": "Has gastado %s de XP. Has alcanzado el nivel máximo.",
 f"msg.{NS}.none": "ninguna",
 f"msg.{NS}.suspended": "Tu clase ya no existe en este servidor. Usa /clase reiniciar clase.",
 f"msg.{NS}.validate_ok": "Todas las definiciones son válidas (%s clases).",
 f"msg.{NS}.reset_done": "Clase de %s reiniciada.",
})
for aid, params in texts.ABILITIES.items():
    dump(os.path.join(base, "abilities", aid + ".json"), {"schema_version": 1, **params})
for aid, (name, desc) in texts.ABILITY_TEXT.items():
    lang[f"ability.{NS}.{aid}.name"] = name
    lang[f"ability.{NS}.{aid}.desc"] = desc
assert set(texts.ABILITY_TEXT) == set(texts.ABILITIES)
for cid, desc in texts.CLASS_DESC.items(): lang[f"class.{NS}.{cid}.desc"] = desc
for (cid, bid), desc in texts.BRANCH_DESC.items(): lang[f"branch.{NS}.{cid}.{bid}.desc"] = desc
for (cid, bid, pid), (name, desc) in texts.PERK_TEXT.items():
    lang[f"perk.{NS}.{cid}.{bid}.{pid}.name"] = name
    lang[f"perk.{NS}.{cid}.{bid}.{pid}.desc"] = desc
assert set(texts.PERK_TEXT) == {(c, b, p) for (c, b), ps in PERKS.items() for p, _ in ps}
for cid, desc in texts.TRAIT_DESC.items(): lang[f"class.{NS}.{cid}.trait"] = desc
assert set(texts.TRAIT_DESC) == set(TRAITS)
for sid, desc in texts.SUBCLASS_DESC.items(): lang[f"subclass.{NS}.{sid}.desc"] = desc
for mid, name in texts.MECHANIC_NAMES.items(): lang[f"mechanic.{NS}.{mid}.name"] = name
for did, name in texts.DISCIPLINE_NAMES.items(): lang[f"discipline.{NS}.{did}.name"] = name
for key, label in texts.PARAMS.items(): lang[f"param.{NS}.{key}"] = label.split("|")[0]
lang.update(texts.UI)
# Unidades de los parámetros, para que el cliente sepa formatearlos: "%" porcentaje, "s" segundos.
dump(os.path.join(out, "assets", NS, "rpgclasses_param_units.json"),
     {k: v.split("|")[1] for k, v in sorted(texts.PARAMS.items()) if "|" in v})
attr_names = {"crit_damage":"Daño crítico","backstab_crit_damage":"Daño crítico desde invisibilidad","jump_bonus":"Altura de salto","unarmed_damage":"Daño desarmado","unarmed_knockback":"Empuje desarmado","bow_damage":"Daño con arco","draw_speed":"Velocidad de carga del arco","projectile_velocity":"Velocidad de proyectiles","zoom":"Zoom","mob_detection":"Detección por mobs","debuff_resistance":"Resistencia a efectos negativos","knockback_dealt":"Empuje infligido","healing_received":"Curación recibida","sneak_speed":"Velocidad agachado","spell_size":"Tamaño de conjuros","crit_chance":"Probabilidad de crítico"}
for k, v in attr_names.items(): lang[f"attribute.{NS}.{k}"] = v
for locale in ("es_es", "en_us"):  # en_us: copia provisional hasta traducir
    dump(os.path.join(out, "assets", NS, "lang", locale + ".json"), dict(sorted(lang.items())))
