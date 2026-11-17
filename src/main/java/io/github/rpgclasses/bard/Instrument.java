package io.github.rpgclasses.bard;

import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

import java.util.Locale;

/**
 * Catálogo de instrumentos del Bardo (adenda del Bardo, sección 6). Los bonus propios de los Comunes y Raros se
 * multiplican por su rareza ({@link Tier#bonusFactor}); los Épicos y Legendarios llevan además su pasiva única.
 * Los ids deben coincidir con tools/bard.py.
 */
public enum Instrument {
    // Comunes (bonus base; ×1)
    LUTE("lute", Tier.COMMON, NoteBlockInstrument.GUITAR, 0.03, 0.02, 0.03, 0, 0, Passive.NONE),
    FLUTE("flute", Tier.COMMON, NoteBlockInstrument.FLUTE, 0, 0.08, 0, 0, 0, Passive.NONE),
    HARP("harp", Tier.COMMON, NoteBlockInstrument.HARP, 0, 0, 0.10, 0, 0, Passive.NONE),
    DRUM("drum", Tier.COMMON, NoteBlockInstrument.BASEDRUM, 0, 0, 0, 0.03, 0, Passive.NONE),
    VIOLIN("violin", Tier.COMMON, NoteBlockInstrument.BANJO, 0.06, 0, 0, 0, 0, Passive.NONE),
    LYRE("lyre", Tier.COMMON, NoteBlockInstrument.HARP, 0, 0, 0, 0, 50, Passive.NONE),
    OCARINA("ocarina", Tier.COMMON, NoteBlockInstrument.FLUTE, 0, 0.04, 0, 0, 25, Passive.NONE),
    PAN_FLUTE("pan_flute", Tier.COMMON, NoteBlockInstrument.FLUTE, 0, 0, 0.06, 0.015, 0, Passive.NONE),
    TAMBOURINE("tambourine", Tier.COMMON, NoteBlockInstrument.SNARE, 0, 0.04, 0, 0.02, 0, Passive.NONE),
    // Raros (×1,5)
    BAGPIPE("bagpipe", Tier.RARE, NoteBlockInstrument.DIDGERIDOO, 0, 0, 0.05, 0, 100, Passive.NONE),
    CARILLON("carillon", Tier.RARE, NoteBlockInstrument.BELL, 0, 0, 0.08, 0.05, 0, Passive.NONE),
    HURDY_GURDY("hurdy_gurdy", Tier.RARE, NoteBlockInstrument.BANJO, 0.04, 0.10, 0, 0, 0, Passive.NONE),
    // Épicos (×2; bonus secundario del instrumento del que salen, propuesta)
    CRYSTAL_HARP("crystal_harp", Tier.EPIC, NoteBlockInstrument.CHIME, 0, 0, 0.10, 0, 0, Passive.CRYSTAL_HARP),
    WAR_HORN("war_horn", Tier.EPIC, NoteBlockInstrument.DIDGERIDOO, 0, 0.04, 0, 0.02, 0, Passive.WAR_HORN),
    SPECTRAL_VIOLIN("spectral_violin", Tier.EPIC, NoteBlockInstrument.BIT, 0.06, 0, 0, 0, 0, Passive.SPECTRAL_VIOLIN),
    SCALE_LYRE_FIRE("scale_lyre_fire", Tier.EPIC, NoteBlockInstrument.HARP, 0, 0, 0, 0, 50, Passive.SCALE_FIRE),
    SCALE_LYRE_ICE("scale_lyre_ice", Tier.EPIC, NoteBlockInstrument.HARP, 0, 0, 0, 0, 50, Passive.SCALE_ICE),
    SCALE_LYRE_LIGHTNING("scale_lyre_lightning", Tier.EPIC, NoteBlockInstrument.HARP, 0, 0, 0, 0, 50, Passive.SCALE_LIGHTNING),
    // Legendarios (×3)
    ETERNAL_SUN_LUTE("eternal_sun_lute", Tier.LEGENDARY, NoteBlockInstrument.PLING, 0.03, 0.02, 0.03, 0, 0, Passive.SUN_LUTE),
    ABYSSAL_ORGAN("abyssal_organ", Tier.LEGENDARY, NoteBlockInstrument.BASS, 0.04, 0.10, 0, 0, 0, Passive.ABYSSAL_ORGAN),
    COLOSSUS_DRUM("colossus_drum", Tier.LEGENDARY, NoteBlockInstrument.BASEDRUM, 0, 0, 0, 0.03, 0, Passive.COLOSSUS_DRUM),
    /**
     * Guitarra-hacha (Épica, objeto único): +7,5 % de poder musical en vez del de su rareza, bonus secundarios a la
     * mitad que un Épico (los del Laúd ×1) y 5 ranuras (propuesta).
     */
    GUITAR_AXE("guitar_axe", Tier.EPIC, NoteBlockInstrument.GUITAR, 0.03, 0.02, 0.03, 0, 0, Passive.GUITAR_AXE);

    /** Rareza: poder musical, ranuras de partitura, multiplicador de los bonus propios y nivel máximo de partitura. */
    public enum Tier {
        COMMON(0.05, 3, 1.0, 0.4), RARE(0.09, 5, 1.5, 0.6), EPIC(0.15, 7, 2.0, 0.8), LEGENDARY(0.45, 9, 3.0, 1.0);

        public final double power;
        public final int slots;
        public final double bonusFactor;
        public final double levelCap;

        Tier(double power, int slots, double bonusFactor, double levelCap) {
            this.power = power;
            this.slots = slots;
            this.bonusFactor = bonusFactor;
            this.levelCap = levelCap;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Pasivas únicas (solo Épicos y Legendarios). */
    public enum Passive { NONE, CRYSTAL_HARP, WAR_HORN, SPECTRAL_VIOLIN, SCALE_FIRE, SCALE_ICE, SCALE_LIGHTNING, SUN_LUTE, ABYSSAL_ORGAN, COLOSSUS_DRUM, GUITAR_AXE }

    /** Ranuras máximas de un instrumento contando el Repertorio Ampliado del Juglar (+3). */
    public static final int MAX_SLOTS = 12;

    public final String id;
    public final Tier tier;
    public final NoteBlockInstrument sound;
    private final double power, castSpeed, manaRegen, cooldown, maxMana;
    public final Passive passive;

    Instrument(String id, Tier tier, NoteBlockInstrument sound, double power, double castSpeed, double manaRegen,
               double cooldown, double maxMana, Passive passive) {
        this.id = id;
        this.tier = tier;
        this.sound = sound;
        this.power = power;
        this.castSpeed = castSpeed;
        this.manaRegen = manaRegen;
        this.cooldown = cooldown;
        this.maxMana = maxMana;
        this.passive = passive;
    }

    private double factor() {
        return this == GUITAR_AXE ? 1.0 : tier.bonusFactor;
    }

    /** Poder musical total que da (rareza + bonus propio multiplicado). */
    public double musicPower() {
        return (this == GUITAR_AXE ? 0.075 : tier.power) + power * factor();
    }

    public double castSpeed() {
        return castSpeed * factor();
    }

    public double manaRegen() {
        return manaRegen * factor();
    }

    public double cooldownReduction() {
        return cooldown * factor();
    }

    public double maxMana() {
        return maxMana * factor();
    }

    public int slots() {
        return this == GUITAR_AXE ? 5 : tier.slots;
    }

    /** Fracción del nivel máximo de cada conjuro que admite (40 / 60 / 80 / 100 %). */
    public double levelCap() {
        return tier.levelCap;
    }

    /** Multiplicador del radio de las auras (Cuerno de Guerra y Órgano del Abismo: +25 %). */
    public double auraRadiusFactor() {
        return passive == Passive.WAR_HORN || passive == Passive.ABYSSAL_ORGAN ? 1.25 : 1.0;
    }

    public static Instrument byId(String id) {
        for (Instrument i : values()) if (i.id.equals(id)) return i;
        return null;
    }
}
