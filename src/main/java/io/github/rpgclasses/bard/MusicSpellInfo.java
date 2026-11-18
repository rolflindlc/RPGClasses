package io.github.rpgclasses.bard;

import io.github.rpgclasses.RpgClasses;
import net.minecraft.resources.ResourceLocation;

/**
 * Los 21 conjuros de la escuela musical (los 12 de la adenda del Bardo, sección 7, y 9 añadidos el 18-11), sin dependencias de Iron's: los usan las
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
    FINAL_REQUIEM("final_requiem", 30, 5, 200, 120, Kind.AREA, 30, 6, 8),
    // Añadidos el 18-11. Valor: Fuga = distancia (bloques); Resolución = absorción (vida); Eco = fracción de poder que
    // repite; Coro = daño por segundo; Barrera = fracción de daño que absorbe; Sirena = fuerza de la atracción;
    // Canción de Cuna Eterna = fracción de vida con la que se sobrevive. Duración: Fuga = aturdimiento; Nana = sueño;
    // Eco = tiempo máximo desde el último conjuro; Solo = duración; el resto, lo que dura su efecto.
    FUGUE("fugue",                10, 10, 40, 14, Kind.DASH, 7, 0.5, 1.5),
    LULLABY("lullaby",            10, 10, 55, 30, Kind.CONE, 0, 4, 8),
    RESOLUTION("resolution",      10, 10, 60, 40, Kind.PULSE, 4, 10, 8),
    ECHO("echo",                  20, 5, 30, 45, Kind.SELF, 0.5, 30, 0),
    PHANTOM_CHOIR("phantom_choir", 20, 5, 100, 60, Kind.ZONE, 3, 8, 6),
    HARMONY_BARRIER("harmony_barrier", 20, 5, 90, 50, Kind.ZONE, 0.30, 6, 5),
    SIREN_SONG("siren_song",      20, 5, 85, 45, Kind.AREA, 0.35, 2.5, 12),
    GRAND_SOLO("grand_solo",      30, 5, 150, 120, Kind.SELF, 0, 8, 0),
    ETERNAL_LULLABY("eternal_lullaby", 30, 5, 180, 150, Kind.PULSE, 0.30, 15, 10);

    public enum Kind { PROJECTILE, AURA, CONE, AREA, TARGET, DASH, PULSE, ZONE, SELF }

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
