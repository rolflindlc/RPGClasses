# Changelog

## 1.0.0-beta

First public beta of Rolflin's Rites & Paths (Minecraft Forge 1.20.1).

### Added
- **7 classes** (Barbarian, Bard, Mage, Monk, Paladin, Ranger, Rogue) with branches, perks and **21 subclasses**, plus
  discipline walls, custom critical hits and caps.
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
- Class and racial ability HUD.
- Server and client configuration files; balance lives in datapacks.
- Optional integration with Iron's Spells 'n Spellbooks, Epic Fight and Curios.
- Translations: English, Spanish (all regional variants), Simplified and Traditional Chinese, Russian, Brazilian
  Portuguese, German, French, Japanese and Italian.
- Debug tools: `/clases debug` and `logs/rites_and_paths-debug.log`.

### Known issues and notes
- Beta: balance values are still being calibrated.
- Class gear curios are disabled in this version, and some textures are provisional.
- Non-Spanish and non-English translations have not been reviewed by native speakers.
- Worlds created with pre-release versions (mod id `rpgclasses`) are not compatible.
