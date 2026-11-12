package io.github.rpgclasses.core.definition;

import io.github.rpgclasses.core.definition.Definitions.RaceDef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Razas cargadas del datapack (races/*.json). Como {@link DefinitionStore}: SERVER lee el datapack y CLIENT lo recibe por red. */
public final class RaceStore {
    public static final RaceStore SERVER = new RaceStore();
    public static final RaceStore CLIENT = new RaceStore();

    private record Snapshot(Map<String, RaceDef> races, Map<String, List<String>> errors, Map<String, String> raw) {}

    private volatile Snapshot snapshot = new Snapshot(Map.of(), Map.of(), Map.of());

    public void load(Map<String, String> rawRaces) {
        Map<String, RaceDef> races = new LinkedHashMap<>();
        Map<String, List<String>> errors = new TreeMap<>();
        for (Map.Entry<String, String> e : new TreeMap<>(rawRaces).entrySet()) {
            try {
                RaceDef def = DefinitionParser.parseRace(e.getKey(), e.getValue());
                if (def.subraces().isEmpty()) throw new IllegalArgumentException("la raza no tiene subrazas");
                races.put(e.getKey(), def);
            } catch (RuntimeException ex) {
                errors.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(String.valueOf(ex.getMessage()));
            }
        }
        snapshot = new Snapshot(java.util.Collections.unmodifiableMap(races), Map.copyOf(errors), Map.copyOf(rawRaces));
    }

    public RaceDef race(String id) {
        return id == null ? null : snapshot.races().get(id);
    }

    /** Ids en orden alfabético. */
    public Collection<String> ids() {
        return snapshot.races().keySet();
    }

    public Map<String, List<String>> errors() {
        return snapshot.errors();
    }

    public Map<String, String> raw() {
        return snapshot.raw();
    }
}
