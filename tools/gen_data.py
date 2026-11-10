import json, os, sys
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
             ("brutality",[attr([(R("crit_damage"), to(1.0))])]),
             ("iron_skin",[attr([(MC+"armor", to(16)), (MC+"armor_toughness", to(6)), (MC+"movement_speed", to(.25,"multiply_base"))], NOARM)])],
   milestones=[ms(5,"bloodthirst"), ms(15,"charge"), ms(30,"berserker_rage")],
   subs=["indestructible","suicidal_berserker","plague_corruptor"]),
 "rogue": dict(icon="minecraft:iron_sword", roles=["melee","sigilo"], kit="rogue", crit=-0.05,
   walls=dict(magic=.45, bow=.35, heavy=.55, armor=.40, shield=.70),
   branches=[("shadow",[attr([(R("mob_detection"), to(-.5)), (R("backstab_crit_damage"), to(1.0))])]),
             ("edge",[attr([(MC+"attack_speed", to(.6,"multiply_base")), (R("crit_damage"), to(.75))])]),
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
             ("wind",[attr([(MC+"movement_speed", to(.4,"multiply_base")), (R("jump_bonus"), to(.4))], NOARM)]),
             ("skin",[attr([(MC+"armor", to(16)), (MC+"armor_toughness", to(6))], NOARM)])],
   milestones=[ms(5,"stances"), ms(15,"deflect"), ms(30,"light_step")],
   subs=["breeze_master","heavy_palm","triworld_guardian"]),
}

subs = {
 "chaotic_evoker": ("mage","minecraft:fire_charge",[attr([(R("spell_size"), to(.3))]),
     mech("wild_magic", chance_per_mana=.0033, chance_cap=.5, replica_damage=.33, directions=8, icd_ticks=30)], []),
 "elementalist": ("mage","minecraft:blue_ice",[attr([(IR+"cooldown_reduction", to(.10,"multiply_base"))]),
     mech("elemental_debuffs", chance={"from":.4,"to":.75}, max_stacks={"values":[3,3,3,4,4,4,4,5,5,5]},
          duration_seconds={"values":[4,4,4,6,6,6,6,8,8,8]}, refresh_from_level=8)], []),
 "battle_mage": ("mage","minecraft:iron_chestplate",[mech("unbreakable_concentration"),
     mech("wall_mitigation", disciplines=[R("heavy"),R("light")], mitigation={"to":1.0}),
     mech("arcane_shield", mana_fraction={"from":.03,"to":.07}, cap_health_fraction=.5, decay_delay_seconds=6, decay_per_second=.1)], []),
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
     mech("afflicting_strike", damage_per_second=2, duration_seconds=6, boss_factor=.5)], []),
 "shadow_walker": ("rogue","minecraft:ender_pearl",[attr([(R("sneak_speed"), to(1.0))])], [ms(1,"shadow_step",True)]),
 "shadow_illusionist": ("rogue","minecraft:armor_stand",[attr([(R("mob_detection"), to(-.25))])], [ms(1,"decoy",True)]),
 "arcane_blade": ("rogue","minecraft:amethyst_shard",[
     attr([(IR+"cooldown_reduction", to(.15,"multiply_base")), (IR+"spell_power", to(.15,"multiply_base")),
           (IR+"mana_regen", to(.15,"multiply_base")), (IR+"max_mana", to(150))]),
     mech("wall_mitigation", disciplines=[R("magic")], mitigation={"to":1.0}),
     mech("arcane_ambush", damage_bonus=.4, icd_seconds=8, stealth_cost=.4)], []),
 "beast_master": ("ranger","minecraft:bone",[
     mech("wild_bond", health=.5, damage=.5, speed=.25, max_pets=8),
     mech("beast_safeguard", cooldown_seconds=300, idle_seconds=60)], []),
 "agile_scout": ("ranger","minecraft:feather",[
     attr([(MC+"movement_speed", to(.15,"multiply_base")), (MC+"luck", {"from":3,"to":5,"operation":"add"})])],
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

def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2); f.write("\n")

base = os.path.join(out, "data", NS, NS)
for cid, c in classes.items():
    o = {"schema_version": 1, "display": {"icon": c["icon"], "roles": c["roles"]}}
    if c["kit"]: o["starting_kit"] = R(c["kit"])
    o["crit_chance_bonus"] = c["crit"]
    o["walls"] = {R(k): v for k, v in c["walls"].items()}
    if c.get("armor_rule"): o["armor_rule"] = "disables_unarmored_passives"
    o["branches"] = [{"id": b, "max_points": 10, "effects": e} for b, e in c["branches"]]
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
 "in_combat":"No puedes hacer eso en combate.","class_disabled":"Esa clase está desactivada en este servidor."}
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
attr_names = {"crit_damage":"Daño crítico","backstab_crit_damage":"Daño crítico desde invisibilidad","jump_bonus":"Altura de salto","unarmed_damage":"Daño desarmado","unarmed_knockback":"Empuje desarmado","bow_damage":"Daño con arco","draw_speed":"Velocidad de carga del arco","projectile_velocity":"Velocidad de proyectiles","zoom":"Zoom","mob_detection":"Detección por mobs","debuff_resistance":"Resistencia a efectos negativos","knockback_dealt":"Empuje infligido","healing_received":"Curación recibida","sneak_speed":"Velocidad agachado","spell_size":"Tamaño de conjuros"}
for k, v in attr_names.items(): lang[f"attribute.{NS}.{k}"] = v
for locale in ("es_es", "en_us"):  # en_us: copia provisional hasta traducir
    dump(os.path.join(out, "assets", NS, "lang", locale + ".json"), dict(sorted(lang.items())))
