package io.github.rpgclasses.bard;

import io.github.rpgclasses.RpgClasses;
import net.minecraft.resources.ResourceLocation;

/**
 * Los 12 conjuros de la escuela musical (adenda del Bardo, sección 7), sin dependencias de Iron's: los usan las
 * partituras, el atril, las auras y, con Iron's instalado, los conjuros registrados en {@code compat.irons.music}.
 * Valores a nivel 1 y poder musical 100 %. Los ids deben coincidir con tools/bard.py.
 *
 * <p>Escalado por nivel sobre el 1: los de 5 niveles +18 % de daño, +10 % de potencia, +0,5 bloques, +1,5 s y +12 % de
 * maná; los de 10 niveles (combo 0 y 10) la mitad (+9 %, +5 %, +0,25, +0,75 s, +6 %). Combo mínimo y recarga no cambian.
 */
public enum MusicSpellInfo {
    //            id               combo niv maná  cd   tipo             valor  dur   área
    CUTTING_NOTE("cutting_note",   0, 10, 15, 2, Kind.PROJECTILE, 6, 0, 20),
    MARCH("march",                 0, 10, 40, 30, Kind.AURA, 1, 15, 0),
    WAR_HYMN("war_hymn",          10, 10, 60, 45, Kind.AURA, 0.12, 12, 0),
    REST_BALLAD("rest_ballad",    10, 10, 60, 45, Kind.AURA, 0.5, 12, 0),
    ELEGY("elegy",                10, 10, 50, 40, Kind.AURA, 1, 10, 0),
    RUMBLE("rumble",              10, 10, 45, 12, Kind.CONE, 5, 0, 6),
    IRON_CHORALE("iron_chorale",  20, 5, 80, 60, Kind.AURA, 0.12, 12, 0),
    MANA_CADENCE("mana_cadence",  20, 5, 50, 60, Kind.AURA, 0.40, 15, 0),
    DISSONANCE("dissonance",      20, 5, 90, 50, Kind.AREA, 3, 1, 6),
    GRAND_PIANO("grand_piano",    20, 5, 120, 70, Kind.TARGET, 22, 1, 2),
    FORCED_DANCE("forced_dance",  20, 5, 100, 60, Kind.TARGET, 0, 4, 24),
    FINAL_REQUIEM("final_requiem", 30, 5, 200, 120, Kind.AREA, 30, 6, 8);

    public enum Kind { PROJECTILE, AURA, CONE, AREA, TARGET }

    public final String id;
    public final int combo;
    public final int maxLevel;
    public final int mana;
    public final double cooldownSeconds;
    public final Kind kind;
    /** Valor principal a nivel 1: daño, potencia del buff (fracción) o, para Disonancia, segundos de silencio. */
    public final double value;
    /** Duración a nivel 1 (s): auras, aturdimiento de Disonancia y del Piano, baile y buff del Réquiem. */
    public final double duration;
    /** Área, alcance o radio a nivel 1 (bloques); 0 = el radio de aura del Bardo. */
    public final double area;

    MusicSpellInfo(String id, int combo, int maxLevel, int mana, double cooldownSeconds, Kind kind, double value, double duration, double area) {
        this.id = id;
        this.combo = combo;
        this.maxLevel = maxLevel;
        this.mana = mana;
        this.cooldownSeconds = cooldownSeconds;
        this.kind = kind;
        this.value = value;
        this.duration = duration;
        this.area = area;
    }

    public ResourceLocation rl() {
        return RpgClasses.id(id);
    }

    public String fullId() {
        return RpgClasses.MODID + ":" + id;
    }

    private boolean low() {
        return maxLevel >= 10;
    }

    private int steps(int level) {
        return Math.max(0, Math.min(maxLevel, level) - 1);
    }

    public double damageFactor(int level) {
        return 1 + (low() ? 0.09 : 0.18) * steps(level);
    }

    public double potencyFactor(int level) {
        return 1 + (low() ? 0.05 : 0.10) * steps(level);
    }

    public double areaBonus(int level) {
        return (low() ? 0.25 : 0.5) * steps(level);
    }

    public double durationBonus(int level) {
        return (low() ? 0.75 : 1.5) * steps(level);
    }

    public int manaCost(int level) {
        return (int) Math.round(mana * (1 + (low() ? 0.06 : 0.12) * steps(level)));
    }

    /** Nivel máximo que admite un instrumento con esa fracción (40 % → 4 de 10, 2 de 5). */
    public int levelCap(double fraction) {
        return Math.max(1, Math.min(maxLevel, (int) Math.floor(maxLevel * fraction + 1e-9)));
    }

    public String nameKey() {
        return "spell." + RpgClasses.MODID + "." + id;
    }

    public static MusicSpellInfo byId(String id) {
        if (id == null) return null;
        String path = id.startsWith(RpgClasses.MODID + ":") ? id.substring(RpgClasses.MODID.length() + 1) : id;
        for (MusicSpellInfo s : values()) if (s.id.equals(path)) return s;
        return null;
    }
}
