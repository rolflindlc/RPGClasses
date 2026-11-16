package io.github.rpgclasses.content;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Choques que se solapan (adenda de razas 0.7, interacciones): la embestida del Centauro, la Embestida del Bárbaro
 * y el Baluarte de Embestida del Juggernaut. Si dos de ellos alcanzan al mismo objetivo casi a la vez (8 ticks),
 * cuenta solo el más fuerte: el segundo solo añade lo que supera al primero, y si no lo supera no hace nada (ni empuja).
 */
public final class RamOverlap {
    public static final String CENTAUR = "centaur", BARBARIAN = "barbarian", JUGGERNAUT = "juggernaut";
    private static final int WINDOW_TICKS = 8;

    private record Hit(long tick, float damage, String source) {}

    private static final Map<String, Hit> HITS = new HashMap<>();

    private RamOverlap() {}

    /** Daño que debe hacer este choque (0 = otro choque más fuerte ya lo alcanzó: no hacer nada). */
    public static float share(ServerPlayer attacker, LivingEntity victim, float damage, String source) {
        long now = attacker.level().getGameTime();
        if (HITS.size() > 256) HITS.values().removeIf(h -> now - h.tick() > WINDOW_TICKS);
        String key = attacker.getUUID() + "|" + victim.getUUID();
        Hit prev = HITS.get(key);
        if (prev != null && now - prev.tick() <= WINDOW_TICKS && !prev.source().equals(source)) {
            if (damage <= prev.damage()) return 0;
            HITS.put(key, new Hit(now, damage, source));
            return damage - prev.damage();
        }
        HITS.put(key, new Hit(now, damage, source));
        return damage;
    }
}
