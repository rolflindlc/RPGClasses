# Changelog

## 1.0.0-beta

First public beta of Rolflin's Rites & Paths (Minecraft Forge 1.20.1).

### Added
- **8 classes** (Barbarian, Bard, Cleric, Mage, Monk, Paladin, Ranger, Rogue) with branches, perks and
  **24 subclasses**, plus discipline walls, custom critical hits and caps.
- **Cleric:** holy healer that shares the holy school with the Paladin. Healing, Blessing, Divine Reserve, Purification
  and Aegis branches; Healer's Eye, Laying on Hands, a channeled Sanctuary and Miracle (allies who would die are
  downed and can be lifted up with a heal). Subclasses: Angel (Angelic Form flight, Winged Grace, Halo), Martyr
  (Sacrifice and Final Sacrifice) and Intercessor (Prayer: the camera moves to an angel beside an ally and spells are
  cast from it).
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

### Known issues and notes
- Beta: balance values are still being calibrated.
- Class gear curios are disabled in this version, and some textures are provisional.
- Non-Spanish and non-English translations have not been reviewed by native speakers.
- Worlds created with pre-release versions (mod id `rpgclasses`) are not compatible.
