# Changelog

## Unreleased

### Changed
- **Face Changer:** learning a mob face takes 45 s of study (players still 90 s; `mob_study_seconds`). Mob disguises
  are now animated every tick like Druid forms (walking, idle, swimming, crouching and the mob's own attack: native,
  arm swing or head-butt; zombies raise their arms for a moment when you hit) and no longer show the items you hold.
  Disguise and Druid bodies skip Epic Fight's animator, which left them frozen because they are not world entities.
- **HUD layout:** the centre bars (Stealth, Spin, Slime merge) now stack above whatever already sits on both sides of
  the hotbar (health, armor including Improved Armor's extra rows, Monk charges, food, air, mount health) instead of
  fixed heights. New **HUD editor** (F7, rebindable): drag each element, right click to hide it, arrows to nudge (Shift
  ×10), R to reset; saved in the `[hud]` section of `rites_and_paths-client.toml` (x/y offset and hidden per element:
  abilities, stealth, monk_charges, spin_bar, mitosis_merge, wolf_jump, centaur_jump, druid_cast, druid_form,
  bard_rhythm). A moved element no longer reserves space in the side stacks.
- **Balance rev. 2.23 (top tiers):** **Druid** max health −35% (was −10%) and beast forms gain +1% health per Total
  Level (was +2%; damage still +2%): ×1.4 at level 40. **Moon Circle:** switching from one form to another keeps your
  health percentage. **Mage:** *Reserve* branch gives up to +250 mana (was +300). **Elementalist:** cooldown reduction
  6% (was 10%). **Chronomancer:** cast time reduction 10% (was 15%) and *Time Stop* lasts 1–2 s (was 0.5–1 s).
  **Suicidal Berserker:** +15% damage (was +25%) and *Last Immortal Fury* lasts a fixed 5 s (was 7–17 s).
  **Breeze Master:** +30% speed without armor (was +40%). **Centaur** max speed 0.40/0.45/0.55/0.65 by rank (was
  0.40/0.50/0.60/0.75). **Colossus Titan:** *Grow* absorption 0.40/0.55/0.70/0.80 (was 0.50/0.70/0.85/1.00).

### Added
- **Balance rev. 2.22:** **Paladin** gains *Divine Smite* (every 4th melee hit deals +40% holy damage, ×2 against
  undead and Nether mobs). **Barbarian:** *Bloodthirst* heals 15% (was 10%), *Berserker Rage* cooldown 45 s (was 60 s)
  and its roar makes regular mobs flee for 2 s. **Unyielding Bastion:** hits reduced by *Tenacity* return 20% to the
  attacker. **Sniper:** *Camouflage* (crouch still 2 s: invisible to mobs and players until you move or shoot) and
  *Aimed Mark* (watch an enemy through a spyglass for 5 s: your next shot that hits it within 30 s deals +50%).
  **Paladin** speed penalty −5% (was −10%). **Rogue** stealth drains 8%/s (was 10%/s). **War Chanter:** *Resonant
  Strike* up to 100% (was 75%). **Enchanter:** *Contagion* up to 5 dancers (was 3) and *Bewitch* 6 s (was 4 s).
  **Minstrel:** *Recital* internal cooldown 12 s (was 20 s). **Martyr**, new active *Blood Bond* (look at a target
  within 10 blocks and press): bind one ally (breaks beyond 15 blocks) and one enemy (beyond 10) with a dripping blood
  tether. Half the final damage the ally takes goes to the Martyr as true damage; the bound enemy also takes 25% of
  all damage the Martyr takes, Sacrifice included. Press on a bound target (or at nothing) to break it; 15 s cooldown
  starts when a bond breaks. **Intercessor**, new passive *Guardian Angel*: the first lethal hit on the ally you are
  praying for is negated (60 s cooldown per ally).
- **Rogue, new subclass Face Changer** (replaces Shadow Walker; existing Shadow Walkers become Face Changers with
  the same level). *Spy's Spyglass* (passive): looking through a spyglass shows the target's health (armor from
  subclass level 4, effects from level 7) and makes it glow only for you (3 to 10 s). Watching a player or a
  villager, illager, witch, piglin or humanoid undead for 90 s in total (progress is kept) teaches you its face; mobs
  are learned by type, players one by one (`rites_and_paths:disguise/*` entity type tags). *Disguise* (active): pick
  a learned face. A mob face makes its whole faction ignore you; a player face shows their skin, model, racial size
  (visual only) and name, hides you from the Tab list and puts their name in death and kill messages. Allies see the
  disguise with your real name. Attacking reveals you to the target and its faction within 16 blocks; mobs already
  chasing you when you disguise know you are an impostor (and warn their faction) until they lose aggro. Water, rain,
  fire or stealth break it: Speed II 4 s + 1 s invisibility, then a 30 s cooldown. Bosses are never fooled.
- **Class points:** levelling no longer charges XP when you spend in the tree. You hold a balance of class points
  (shown in the tree) and every branch or subclass level costs 1 point. Points are bought with XP (`+1` / `all`
  buttons, same price curve: point n costs (80 + 6n) XP, where n counts every point you own) or come from items and
  bosses; 40 at most. Respecs give points back with the usual loss (rounded to the nearest). `buyPointsWithXp`
  turns XP purchases off. Existing players keep everything they spent and start with a balance of 0.
- **Book of Knowledge** (+1 point) and **Book of Oblivion** (full class + subclass respec with no loss, with
  confirmation): no recipe, creative only; optional rare loot in the chests where the enchanted golden apple appears
  (same weight by default for the Book of Knowledge, half for the Book of Oblivion; `classBooks` config).
- **Boss points:** the first kill of each boss species (`rites_and_paths:class_point_bosses`: Wither, Ender Dragon,
  Warden, Elder Guardian and `forge:bosses`) gives 1 class point to every player who damaged it (`bossPoints`).
- **Point balance:** the XP price of class points follows how you compare with the average of the *other* active
  players (active = played at least 1 hour in the last 48 real hours; tracked per hour in the world save). Within ±3
  points of that average nothing changes; beyond it, each point below makes the next point 3% cheaper (up to −50%)
  and each point above 3% dearer (up to +50%). Needs at least 2 other active players. The buy button tooltip shows
  the discount or surcharge and `/clase info` shows the active players, their average and the price change. All of
  it is configurable in `catchUp` (`margin`, `discountPerLevel`, `maxDiscount`, `penaltyPerLevel`, `maxPenalty`,
  `minActivePlayers`, `activeHours`, `minPlayMinutes`). Books and bosses are not affected.
- **Saved builds:** 3 slots; loading one resets the class with the usual loss and spends your points following it.
- `/clase comprar [todos]`, `/clases puntos <player> <amount>`; `/clase info` shows the balance.

### Changed
- **Druid:** Wild Shape and Beast Lore are granted at level 0 (when choosing the class). Class milestones may now be
  level 0.
- **Druid, beasts from other mods:** about 145 creatures from Alex's Mobs, Alex's Caves, Mowzie's Mobs, L_Ender's
  Cataclysm, Skarrier Mobs, Iron's Spellbooks and Travel Optics are valid Wild Shape forms (optional entries: missing
  mods are ignored), with abilities and traits. Bosses, humanoids, constructs, summons and segmented creatures are
  excluded. Mini-boss beasts (over 100 health) need total level 30.
- **Druid, new beast ability types** (each creature uses a variant with its own name and numbers in
  `druid_beasts.json`, `"abilities": {"id": {"type": ...}}`): breath, slam, strike, grapple, camouflage, blink,
  tunnel, rally, drain, throw and sonic. Roar and sting accept another effect (`"effect"`); ram can follow the look
  (`"follow_look"`) for swimmers.
- **Druid, new form traits:** glide, dodge, lava swim, thorns, snow walk and rooted (forms that cannot move).
- **Druid, Wild Shape:** your inventory (including armor and off-hand) is stored away while you are transformed and
  given back exactly as it was when you return (also after a crash or on death, before loot drops). In beast form you
  cannot pick up items or open containers, and anything that enters the beast's inventory (`/give`, other mods) goes
  straight to the stored inventory. Right-click now triggers the beast's special ability (the ability key still works).
- **Druid, Wild Shape:** first-person hands and items are hidden in beast form (also Epic Fight's arms); the beast's
  attack plays its own animation (iron golem, ravager, hoglin, zoglin, warden), swings its arm if its model has arms,
  or head-butts otherwise; attack reach scales with the creature's size (wolf 3 blocks, chicken ~2.4, spider ~4.6,
  1.5-6). Both can be overridden per species in `druid_beasts.json` (`"reach"`, `"attack_anim"`).
- **Druid, Wild Shape:** forms apply the status effects their creature applies, on hit (cave spider and bee poison)
  or to whoever is near. Vanilla creatures come in `druid_beasts.json` (`"hit_effects"`, `"aura_effects"`); for
  creatures from other mods the server learns them by watching what those mobs apply (saved in the world; the table
  always wins). Same effect and level as the mob, duration +2% per Druid level. Chimeras: hit effects from the head,
  nearby ones from the instinct.
- **`/clases bestiario <player> ver | dar <species> [points] | quitar <species>`** (permission 2): view, grant or
  remove a Druid's known species.
- **Improved Armor compatibility** (optional, no hard dependency): when the mod `improvedarmor` is loaded, the
  effects that ignore part of the target's armor (Rogue's Lunge, Monk's Fiery Strike, Spin Master's diamond and
  critical-pierce spheres) evaluate its flat armor curve instead of vanilla's, so they still remove the intended
  share of the armor. Class gear and class defenses are balanced in armor points (a class set plus the class's own
  defense equals the normal set of its tier), so they work unchanged under either curve. `/clases debug volcar` shows
  the effective armor when Improved Armor is active.

## 1.0.0-beta

First public beta of Rolflin's Rites & Paths (Minecraft Forge 1.20.1).

### Added
- **9 classes** (Barbarian, Bard, Cleric, Druid, Mage, Monk, Paladin, Ranger, Rogue) with branches, perks and
  **27 subclasses**, plus discipline walls, custom critical hits and caps.
- **Cleric:** holy healer that shares the holy school with the Paladin. Healing, Blessing, Divine Reserve, Purification
  and Aegis branches; Healer's Eye, Laying on Hands, a channeled Sanctuary and Miracle (allies who would die are
  downed and can be lifted up with a heal). Subclasses: Angel (Angelic Form flight, Winged Grace, Halo), Martyr
  (Sacrifice and Final Sacrifice) and Intercessor (Prayer: a crouching copy of your body stays behind and you
  fly beside the ally as a translucent floating head, up to 15 blocks from them, casting your spells from there; hits
  on the body hurt you, the head cannot be hurt).
- **Spin Master** (experimental, hidden): a hidden "Experimental" section in the class selection screen (type 1618 or
  click the faint φ in the top-left corner) holds classes whose balance is not guaranteed; choosing one shows a
  warning. The Spin Master throws balls of three tiers (iron, diamond and netherite, crafted in a cross with 5 of the material)
  that can carry one of nine upgrades applied at a crafting table (slime, copper, amethyst, ender pearl, gold,
  redstone, dripstone, flint "Drill" that stays stuck longer for more total damage, and obsidian "Crusher" that deals
  everything in one hit), from a **Sphere Bandolier** (bundle-like: right click to store or take out; key G to switch
  ball): left click throws a ball guided toward your crosshair, right click throws a curved ball that can be charged
  by holding it (up to twice the range, twice the time stuck and ×1.5 damage). The ball drills into the target and
  spins on it for 1 second (2 when charged), damaging it in ticks that skip invulnerability and lose to armor only as
  much as the whole impact would in one hit. Every bounce, off a block or after spinning in an enemy, sends it toward
  where your camera looks, so you aim at the next enemy yourself; with no bounces left it returns to the hand, and a
  click right as it arrives is a Perfect Catch. Every hit raises the **Spin** bar (33%: extra bounce; 66%: stronger material effect;
  100%: Golden Rectangle, announced on the HUD only once it is unlocked). Branches Rotation (more damage ticks instead of
  flat damage), Rebound, Return, Scan and Curve; **Bounce Route** (a billiard shot that ricochets from enemy to enemy, hitting harder each time, and drills
  into the last one), Reverse Spin, Spin Step and the **Golden Rectangle** ultimate: an automatic cinematic sequence
  (time stops around you, a golden ball holds the target in an infinite rotation, drags it back when it tries to
  escape and slams it into the ground, then the spin spreads to nearby enemies), with its own camera shots, floating
  onomatopoeias and hit-stop shake, all shader-friendly. Subclasses Rider,
  Scanner and Spin Trapper (planted mines). Six ball enchantments (Friction, Density, Lightness, Magnet, Claw,
  Elasticity). New **Spheres** discipline: every other class has a fixed −100% wall on it. A fourth ability key (H).
  Server options `sphere.redstone.enabled`, `sphere.stalactite.breakBlocks` and `sphere.friction.igniteBlocks`;
  client options `spinShake` and `spinCinematic`.
- **Druid:** nature mage who turns into the beasts they know. The nature school is now the Druid's (every other class
  has a fixed −100% wall on it; the Ranger keeps Poison Arrow), −35% to the other schools, clumsy with weapons, armor
  and shields, −10% max health. **Beast Lore** (level 1): study a living animal or beast by holding right click with a
  **Study Book** (taking damage resets the bar) or cast the class-only **Druid's Ritual** spell on fresh remains;
  each gives a knowledge point for the species (once per individual creature), and stronger species need more points.
  Knowledge survives a class reset. **Wild Shape** (key → **Bestiary**): a 6 s cast (damage interrupts) turns you into
  a known species with its size, health pool (falling returns you to your previous health), speed, attacks and a
  special ability on key G, scaled +2% per Total Level; the mana cost (by beast power, more for flying and aquatic
  forms) stays reserved from max mana while transformed. Equipment is hidden, items and spells are blocked, race traits
  are suspended; flying forms have stamina and a ceiling. Also Track Prey, Dominate Animal and Entangling Vines.
  Branches Sap, Ferocity, Wild Reserve, Lore and Roots. Subclasses **Circle of the Moon** (1 s cast, 5 s cooldown, no
  mana reserve), **Supernatural Form** (nature spells while transformed) and **Sculptor** (rituals give a knowledge
  point or a beast part; put head, torso, legs and instinct in a **Flesh Vessel**, throw it on a **Flesh Altar** and
  right click it to create a chimera you can turn into, at the cost of mana and 30% max health for 10 minutes).
  ~40 vanilla beasts with abilities; allowed and forbidden species are entity tags in the datapack.
- **JEI support** (optional): special recipes (ball upgrades, leveling sheet music) are shown as crafting recipes, and
  items that are not crafted (balls, Sphere Bandolier, sheet music, boss-dropped Bard materials, Creative Amulet) have
  information pages. Every status effect has a description for Just Enough Effect Descriptions, and every enchantment
  one for Enchantment Descriptions.
- **12 races** with **39 subraces** and **117 rites**, with a Race Altar and rite bosses.
- **Bard** rhythm minigame, with its own music school for Iron's Spells 'n Spellbooks, instruments, sheet music and a
  music stand.
- **Chronomancer** subclass (Time Rewind and Time Stop).
- **Allies** system (key J), the Bond Book for the Beast Master, and a custom class and race interface.
- **Skill tree** tab in the class book: a draggable, zoomable star map with one arm per branch, its perks at the tip,
  abilities set in the core ring and the subclass island across a bridge. The tree itself enforces the 3-branch limit
  (locked arms turn dark, and hovering a third branch previews which ones will lock). Clicks build a **plan** that is
  applied in one go (with a summary when it locks branches or picks a perk or subclass); other classes can be
  previewed. Branches can define their icon in the datapack (`display.icon`).
- **Race tab** in the class book: your race as a star map in the same style as the skill tree. The race core, the
  subrace and the three rites that raise your Rank, with the rite in progress and the progress of each objective
  (it updates live), and, at each Rank, the new abilities and the values that improve. The old "Branches and
  abilities" and "Subclasses" tabs are gone: the skill tree tooltips already show all of that information.
- Class and racial ability HUD.
- Magic walls are multiplicative for every class (final spell power = total power × (1 − wall)); the Barbarian has an
  Armor wall of −25% (his class harnesses are exempt). Bard songs show colored aura rings.
- Server and client configuration files; balance lives in datapacks.
- Optional integration with Iron's Spells 'n Spellbooks, Epic Fight and Curios.
- Translations: English, Spanish (all regional variants), Simplified and Traditional Chinese, Russian, Brazilian
  Portuguese, European Portuguese, German, French, Japanese and Italian.
- Debug tools: `/clases debug` and `logs/rites_and_paths-debug.log`.
- **Zone difficulty** (optional, `difficulty.enabled` in the server config): hostile mobs get varied armor and weapons,
  more health and damage, and larger groups depending on the average class level of the players nearby (threat T,
  0-40). Netherite only appears at T = 40 and rarely. No effect within 96 blocks of any player's respawn point, on
  mobs with more than 80 base health or on bosses (`rites_and_paths:difficulty_exempt` tag). Kills give extra
  Looting; XP does not change. Each lever can be switched off on its own, and `/clases dificultad` shows the
  current T and effects where you stand. **Potions of Tranquility** (-20% per level of your class level when T is
  calculated) and **Challenge** (+25% per level), levels I-IV, brewed from an awkward potion with a cornflower or
  rotten flesh and raised with glowstone dust. T is no longer capped at 40: with Challenge it can reach 80 (the cap is
  configurable). From T = 40 mobs can wear a full set of enchanted netherite (30% at T = 80). From T = 10 (0% below)
  mobs can become **elites** with 1 to 3 traits, each with its own unlock T, particles and config section
  (`difficulty.traits.<id>`): swift, poisonous, igneous, regenerating, crippling, volatile, vampiric, teleporting,
  deflecting (projectiles bounce) and colossus (bigger, +50% health, -25% speed; dies in a visual explosion and drops
  3 equal mobs without traits).
- **Ranger:** the arrow spells of Iron's (**Magic Arrow**, **Arrow Volley**, **Fire Arrow** and **Poison Arrow**) are cast without the Ranger's Magic wall
  (full spell power). Data-driven: `magic_wall_exempt_spells` in a class JSON (per spell, unlike the per-school
  `magic_wall_exempt_schools`).
- **Welcome message** the first time each player joins a world, with a short explanation and (for operators and the
  owner of a single-player world) a clickable link to the difficulty settings. **Difficulty settings menu**
  (`/clases dificultad ajustes`, clickable): mode **Players** (class level), **Distance** (only grows away from the world
  spawn) or **Mixed** (weighted average), and intensity **Off** (no increases), **Easy** (half), **Normal** or **Hard**
  (double).

### Fixed (general review)
- **Performance:** the debug log is now **off by default** (`debugLog = false`). When on, it groups its disk writes
  (once per second; warnings and errors still at once). With it off, hits and passive checks no longer build any
  debug text.
- **Porter's belt:** if the Porter removes or swaps a block, or dies, while an ally has it open, the ally's menu now
  closes (items put there were lost). The belt's items now go into the player's death drops, so grave mods (Corpse,
  Gravestone...) pick them up, and item changes are saved in the same tick.
- **Singleplayer:** zones, traps, marks and other in-memory state are cleared when the world closes, so they no
  longer carry over into the next world opened in the same session.
- **Elite Colossus trait** now also looks bigger on mobs drawn by Epic Fight.
- **Spring Slime:** the server checks the launch charge against how long it saw you crouching.
- **Choosing race/class:** while protected (`protectUnclassed`), you can no longer attack, break, place or use
  things either; with no classes or races defined, nothing has to be chosen.
- Extra mobs from zone difficulty fire Forge's spawn event, so other spawn-control mods see them.

### Known issues and notes
- Beta: balance values are still being calibrated.
- Class gear curios are disabled in this version, and some textures are provisional.
- Non-Spanish and non-English translations have not been reviewed by native speakers.
- Worlds created with pre-release versions (mod id `rpgclasses`) are not compatible.
