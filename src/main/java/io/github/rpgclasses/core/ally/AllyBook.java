package io.github.rpgclasses.core.ally;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;

/**
 * Lista de aliados de cada jugador. Lógica pura.
 * <p>
 * La relación es <b>unilateral</b>: cada jugador decide a quién considera aliado. Sus efectos de apoyo
 * (auras, curas, purgas) alcanzan a sus aliados, y sus efectos ofensivos no les afectan. Quien no es
 * aliado cuenta como enemigo. "Aliados mutuos" = los dos se tienen marcados.
 */
public final class AllyBook {
    public enum Result { OK, SELF, ALREADY, NOT_ALLY, FULL }

    private final Map<UUID, LinkedHashSet<UUID>> allies = new HashMap<>();

    /** ¿{@code owner} considera aliado a {@code other}? Uno mismo siempre lo es. */
    public boolean considers(UUID owner, UUID other) {
        if (owner == null || other == null) return false;
        if (owner.equals(other)) return true;
        Set<UUID> set = allies.get(owner);
        return set != null && set.contains(other);
    }

    public boolean mutual(UUID a, UUID b) {
        return considers(a, b) && considers(b, a);
    }

    public Result add(UUID owner, UUID other, int max) {
        if (owner.equals(other)) return Result.SELF;
        LinkedHashSet<UUID> set = allies.computeIfAbsent(owner, k -> new LinkedHashSet<>());
        if (set.contains(other)) return Result.ALREADY;
        if (max >= 0 && set.size() >= max) return Result.FULL;
        set.add(other);
        return Result.OK;
    }

    public Result remove(UUID owner, UUID other) {
        Set<UUID> set = allies.get(owner);
        if (set == null || !set.remove(other)) return Result.NOT_ALLY;
        if (set.isEmpty()) allies.remove(owner);
        return Result.OK;
    }

    /** Quita todos los aliados de {@code owner}; devuelve cuántos tenía. */
    public int clear(UUID owner) {
        Set<UUID> set = allies.remove(owner);
        return set == null ? 0 : set.size();
    }

    /** Aliados de {@code owner}, en el orden en que se añadieron. */
    public List<UUID> alliesOf(UUID owner) {
        Set<UUID> set = allies.get(owner);
        return set == null ? List.of() : List.copyOf(set);
    }

    /** Jugadores que tienen a {@code target} como aliado. */
    public List<UUID> alliedBy(UUID target) {
        List<UUID> out = new ArrayList<>();
        for (Map.Entry<UUID, LinkedHashSet<UUID>> e : allies.entrySet()) {
            if (e.getValue().contains(target)) out.add(e.getKey());
        }
        return out;
    }

    /** Vista de solo lectura, para guardar. */
    public Map<UUID, Set<UUID>> view() {
        Map<UUID, Set<UUID>> out = new HashMap<>();
        allies.forEach((k, v) -> out.put(k, Collections.unmodifiableSet(v)));
        return Collections.unmodifiableMap(out);
    }

    /** Carga sin límite (el guardado manda; el tope solo se aplica al añadir). */
    public void put(UUID owner, UUID other) {
        if (!owner.equals(other)) allies.computeIfAbsent(owner, k -> new LinkedHashSet<>()).add(other);
    }

    public void clearAll() {
        allies.clear();
    }
}
