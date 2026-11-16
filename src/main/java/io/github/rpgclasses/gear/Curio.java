package io.github.rpgclasses.gear;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import net.minecraft.world.entity.player.Player;

/**
 * Catálogo de curios (rev. 2.20). Cada uno tiene su ranura de Curios, una rareza y una restricción: general, de clase
 * o de raza. Solo se pueden equipar si se cumple la restricción, y como máximo 1 Reliquia a la vez. Sus efectos están
 * en {@link CurioEffects} y solo funcionan si la restricción se sigue cumpliendo (p. ej., tras reiniciar la clase).
 */
public enum Curio {
    /*
     * Desactivados en la versión de salida: con ENABLED = false no se registran los objetos, no se escuchan sus
     * efectos y el generador de datos (tools/gear.py, CURIOS_ENABLED) no crea sus recetas, modelos ni ranuras.
     * Para recuperarlos: true aquí y en tools/gear.py, y volver a generar los datos.
     */

    // --- generales
    WISP_LANTERN("wisp_lantern", "charm", Rarity.RARE, null, null),
    LAST_BREATH_COMPASS("last_breath_compass", "necklace", Rarity.COMMON, null, null),
    HERMIT_MEDALLION("hermit_medallion", "necklace", Rarity.COMMON, null, null),
    TRAVELER_BELT("traveler_belt", "belt", Rarity.COMMON, null, null),
    FROST_RING("frost_ring", "ring", Rarity.RARE, null, null),
    PIXIE_BELL("pixie_bell", "charm", Rarity.RARE, null, null),
    SERPENT_PENDANT("serpent_pendant", "necklace", Rarity.RARE, null, null),
    CYCLOPS_HORN("cyclops_horn", "charm", Rarity.EPIC, null, null),
    GORGON_EYE("gorgon_eye", "charm", Rarity.EPIC, null, null),
    STORM_TALISMAN("storm_talisman", "bracelet", Rarity.EPIC, null, null),
    PHOENIX_FEATHER("phoenix_feather", "back", Rarity.RELIC, null, null),
    // --- de clase
    ARCHMAGE_PRISM("archmage_prism", "charm", Rarity.EPIC, Kind.CLASS, "mage"),
    ARCANE_HOURGLASS("arcane_hourglass", "necklace", Rarity.RARE, Kind.CLASS, "mage"),
    OATH_RELIQUARY("oath_reliquary", "necklace", Rarity.EPIC, Kind.CLASS, "paladin"),
    POCKET_BANNER("pocket_banner", "back", Rarity.RARE, Kind.CLASS, "paladin"),
    FANG_COLLAR("fang_collar", "necklace", Rarity.RARE, Kind.CLASS, "barbarian"),
    WAR_DRUM("war_drum", "belt", Rarity.EPIC, Kind.CLASS, "barbarian"),
    TROPHY_SKULL("trophy_skull", "belt", Rarity.RELIC, Kind.CLASS, "barbarian"),
    CROW_CLOAK("crow_cloak", "back", Rarity.EPIC, Kind.CLASS, "rogue"),
    LOADED_DICE("loaded_dice", "charm", Rarity.RARE, Kind.CLASS, "rogue"),
    HIPPOGRYPH_FEATHER("hippogryph_feather", "charm", Rarity.EPIC, Kind.CLASS, "ranger"),
    DRAGONSLAYER_QUIVER("dragonslayer_quiver", "back", Rarity.RELIC, Kind.CLASS, "ranger"),
    STANCE_ROSARY("stance_rosary", "necklace", Rarity.EPIC, Kind.CLASS, "monk"),
    TEMPLE_BELL("temple_bell", "belt", Rarity.RARE, Kind.CLASS, "monk"),
    // --- de raza
    GUILD_INSIGNIA("guild_insignia", "charm", Rarity.RARE, Kind.RACE, "human"),
    TROLL_EARRING("troll_earring", "necklace", Rarity.RARE, Kind.RACE, "orc"),
    MINE_LANTERN("mine_lantern", "head", Rarity.RARE, Kind.RACE, "dwarf"),
    EVERGREEN_LEAF("evergreen_leaf", "charm", Rarity.RARE, Kind.RACE, "elf"),
    TRINKET_POUCH("trinket_pouch", "belt", Rarity.RARE, Kind.RACE, "goblin"),
    TIDE_CONCH("tide_conch", "necklace", Rarity.RARE, Kind.RACE, "triton"),
    MAGMA_HEART("magma_heart", "charm", Rarity.EPIC, Kind.RACE, "infernal"),
    MIDNIGHT_VEIL("midnight_veil", "head", Rarity.RARE, Kind.RACE, "undead");

    /** Interruptor de la sección de curios (amuletos, anillos, colgantes...). Ver el comentario del principio. */
    public static final boolean ENABLED = false;

    public enum Rarity { COMMON, RARE, EPIC, RELIC }

    public enum Kind { CLASS, RACE }

    public final String id;
    /** Ranura de Curios (tag curios:&lt;ranura&gt;). */
    public final String slot;
    public final Rarity rarity;
    public final Kind kind;
    /** Clase o raza requerida ("mage", "orc"...), o null si es general. */
    public final String requirement;

    Curio(String id, String slot, Rarity rarity, Kind kind, String requirement) {
        this.id = id;
        this.slot = slot;
        this.rarity = rarity;
        this.kind = kind;
        this.requirement = requirement;
    }

    /** El jugador cumple la restricción (clase o raza). */
    public boolean allowed(Player player) {
        if (kind == null) return true;
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return false;
        String want = RpgClasses.MODID + ":" + requirement;
        return want.equals(kind == Kind.CLASS ? data.state.classId : data.state.raceId);
    }
}
