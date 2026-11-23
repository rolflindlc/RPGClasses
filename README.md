# Rolflin's Rites & Paths (Forge 1.20.1) — modId `rites_and_paths`

A datapack-driven mod of classes, branches, subclasses and races. The functional design lives in
`documento_de_diseno_de_clases_v2.md`, the races and their status in
`documento_de_razas_implementacion.md`, and the architecture in the document
"Arquitectura técnica del mod de clases" (all design documents are written in Spanish).

## Current status (2026-11-17)

Everything below is in the code and compiles with its tests, but **it has not been tested in game** except phase 1.

- **Classes:** 7 classes with 5 branches (3 usable) and perks, 21 subclasses, abilities, discipline walls, custom
  criticals and caps (design v2, rev. 2.21). Optional integration with Iron's Spells and Epic Fight.
- **Class gear (rev. 2.20):** Barbarian and Monk armor of every tier (up to Ice and Fire's dragonsteel),
  Monk martial weapons, set effects and knuckles in the Curios hands slot (optional); the 32 curios are **disabled** in the release build (`Curio.ENABLED` and `CURIOS_ENABLED` in `tools/gear.py`). Placeholder textures
  (`python3 -I tools/art/make_gear.py src/main/resources`). Details in `documento_equipo_de_clase.md`.
- **Races:** 12 races, **39 subraces** and 117 rites, with a Race Altar and rite bosses; includes the 4 races of
  addendum 0.7 (Slime, Colossus, Dragonborn and Centaur). Status and tests in `documento_de_razas_implementacion.md`.
- **Allies** (J key), the Beastmaster's **Book of Bonds**, portraits and a custom interface
  (`python3 -I tools/art/make_ui.py src/main/resources`).
- **HUD:** class and racial abilities with their key and cooldown (bottom right), the Mage's free spells,
  stealth bars, charged jump, clinging and fusion.
- **Configuration:** server (`rites_and_paths-server.toml`: XP cost, respec, allies, debugging) and client
  (`rites_and_paths-client.toml`: Titan's screen-shake intensity).
- **Chronomancer** (replaces the Chaotic Evoker): Time Rewind and Time Stop (translucent grey sphere,
  shader-compatible). In-game tests: see "Testing the Chronomancer" below.
- **Bard** (Bard addendum): rhythm minigame with four lanes (left click, right click, Shift and
  Space), combo, Harmony, Counterpoint and Crescendo; a music school registered in Iron's (`rites_and_paths:music`) with 12
  spells, 21 instruments plus the axe-guitar, leveled sheet music and a lectern, 3 subclasses. Details and differences from
  the design in `documento_bardo.md`. Tests: see "Testing the Bard" below.
- Network: protocol **16** (server and client must use the same jar). 18 mixins (3 client-only).
- Debugging: `/clases debug volcar` writes a report to `logs/`; `logs/rites_and_paths-debug.log` keeps the log.

### Pending

- Test in game and calibrate the values marked **[MEDIR]** (to be measured) in the design documents.
- Pack ids: all fixed (Wet `traveloptics:wet`, the Shaper's spells, Ice and Fire dragons and creatures).
- Shield cooldown from the Shields wall (parked): for now it only increases wear.
- Custom nunchaku and bo animations; curios per subclass and per subrace (not designed yet).
- Merge the annexes (races, addendum 0.7, gear) into design document v2. Release on CurseForge.

Texts and JSON are generated with `python3 tools/gen_data.py src/main/resources`
(ability numbers and texts live in `tools/texts.py`).

## Build and test

Requires JDK 17 and an internet connection the first time.

```
./gradlew test        # unit tests for the pure logic
./gradlew runClient   # development client
./gradlew runServer   # development dedicated server
./gradlew build       # jar in build/libs/
```

## Testing phase 1 in game

When you join without a class, the selection screen opens.

| Command | Effect |
| --- | --- |
| `/clase elegir <id>` | Choose a class, e.g. `rites_and_paths:barbarian` |
| `/clase punto <branch>` | Spend a point in a branch (costs XP) |
| `/clase subclase <id>` | Choose a subclass (from 15 base points) |
| `/clase subpunto` | Raise a subclass level |
| `/clase reiniciar clase` / `subclase` | Unlimited respec; each one loses 33% of the XP (configurable) |
| `/clase info` | Current status and cost of the next level |
| `/clases info <player>`, `/clases reset <player>`, `/clases validar` | Administration (level 2) |
| `/clases debug item` | Discipline of the held item and why |
| `/clases debug stats` | Active modifiers, walls, critical and a breakdown of the last hit |

To get test XP: `/xp add @s 9000 points`.

**Creative Amulet** (Tools & Utilities creative tab, or `/give @s rites_and_paths:creative_amulet`):
while it is in any inventory slot, every class and race cooldown is 0 (abilities,
minimum delays between passives and perks, charge recharges, Beast Safeguard and the Thief Goblin's steal).
It has no recipe; it works in any game mode so you can test in survival.

## Testing the Chronomancer

`/clase elegir rites_and_paths:mage`, raise the base to 15 points and `/clase subclase rites_and_paths:chronomancer`; both
abilities are available from subclass level 1 (Z and X keys). To test the high levels, raise the subclass with
`/clase subpunto` (each level requires more base points).

1. **Rewind:** walk for a few seconds, take damage and spend mana; when you press it you return to where you were 3 s ago (5 s from
   level 4 and 8 s from level 8) with part of your health and mana. Purple sparks mark where you would return. Things to test:
   falling from a height (no damage), the Poison effect (removed from level 4), a spell recharge (halved
   from level 8), returning to a spot that now has a block (it looks for a gap or fails without spending the cooldown) and changing
   dimension (clears the history).
2. **Stop:** next to zombies and skeletons: they stand still (even in the air) and their arrows stop in mid-air and
   resume when it ends. With another non-allied player: grey screen, third person, ticking and unable to move or attack;
   when it ends their camera comes back. An ally (J key) is not stopped. A boss (Mowzie's, Cataclysm) is stopped for half the time.
3. **Shaders:** with Iris/Oculus and a shaderpack, the sphere should look the same (it is a translucent shell, with no
   post-processing).
4. **Safety:** disconnecting, dying or leaving the chunk during the stop must not leave anything frozen.

## Testing the Bard

`/clase elegir rites_and_paths:bard` (with Iron's Spells installed). The kit comes with a lute in the off hand. Rhythm arrives
with the first point (`/clase punto compas`); Harmony with Total Level 5, Counterpoint with 15 and Crescendo (Z key) with 30.
The level 1 and max-level sheet music are in the mod's creative tab.

1. **Minigame:** with an instrument in hand, notes come toward the crosshair: from the left,
   left click; from the right, right click; from above, Space; from below, Shift. Hit them and watch the combo (x12)
   under the crosshair. Let a note pass: the combo goes back to 0. Press other lanes: the combo does not drop.
2. **Lectern:** move the instrument to the main hand (F) and Shift + right click out of combat. Insert sheet music; one whose
   level is too high for the rarity does not fit. Move it back to the off hand.
3. **Spells:** pick one from the instrument in Iron's wheel and cast it with right click. Without the minimum combo a
   warning appears; with Iron's cast key (V) too (musical spells are only played). Test March and War Hymn
   with an ally (J key), the Grand Piano on a zombie (the shadow follows it and stops before it falls) and Forced
   Dance on a zombie and on another player (they cannot move or attack).
4. **Harmony:** from Total Level 5, play a whole phrase with no misses or extra presses: the stages (dots under
   the combo) and the tempo go up.
5. **Upgrading sheet music:** sheet music + Iron's ink on the crafting table (the ink's rarity depends on the level).
6. **Other classes:** a Mage cannot cast musical spells or play instruments.

## Adding a class

1. `data/<namespace>/rites_and_paths/classes/<id>.json` with 3 branches of 10 points and 3 subclasses.
2. Three files in `data/<namespace>/rites_and_paths/subclasses/`.
3. Names in `assets/<namespace>/lang/`.
4. Java code only if it needs a new mechanic.

`/clases validar` and the `DefinitionFilesTest` test check the structure.
