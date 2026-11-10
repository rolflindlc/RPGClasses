package io.github.rpgclasses.core.definition;

import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.SubclassDef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Definiciones cargadas. Hay dos instancias: SERVER (leída del datapack) y CLIENT (recibida por red).
 * Ambas se construyen con {@link #load} a partir del mismo JSON en bruto, así que no pueden diferir.
 */
public final class DefinitionStore {
    public static final DefinitionStore SERVER = new DefinitionStore();
    public static final DefinitionStore CLIENT = new DefinitionStore();

    private record Snapshot(
            Map<String, ClassDef> classes,
            Map<String, SubclassDef> subclasses,
            Map<String, List<String>> errors,
            Map<String, String> rawClasses,
            Map<String, String> rawSubclasses) {}

    private volatile Snapshot snapshot = new Snapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

    /** Sustituye todo el contenido de una vez. Las clases inválidas quedan fuera y sus errores registrados. */
    public void load(Map<String, String> rawClasses, Map<String, String> rawSubclasses) {
        Map<String, List<String>> errors = new TreeMap<>();
        Map<String, SubclassDef> subs = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : new TreeMap<>(rawSubclasses).entrySet()) {
            try {
                subs.put(e.getKey(), DefinitionParser.parseSubclass(e.getKey(), e.getValue()));
            } catch (RuntimeException ex) {
                errors.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(String.valueOf(ex.getMessage()));
            }
        }
        Map<String, ClassDef> classes = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : new TreeMap<>(rawClasses).entrySet()) {
            List<String> errs = new ArrayList<>();
            try {
                ClassDef def = DefinitionParser.parseClass(e.getKey(), e.getValue());
                errs.addAll(DefinitionValidator.validateClass(def, subs));
                if (errs.isEmpty()) classes.put(e.getKey(), def);
            } catch (RuntimeException ex) {
                errs.add(String.valueOf(ex.getMessage()));
            }
            if (!errs.isEmpty()) errors.put(e.getKey(), errs);
        }
        // Solo se conservan las subclases de clases válidas.
        Map<String, SubclassDef> usable = new LinkedHashMap<>();
        for (ClassDef c : classes.values()) {
            for (String id : c.subclasses()) usable.put(id, subs.get(id));
        }
        snapshot = new Snapshot(Map.copyOf(classes), Map.copyOf(usable), Map.copyOf(errors),
                Map.copyOf(rawClasses), Map.copyOf(rawSubclasses));
    }

    public ClassDef classDef(String id) {
        return id == null ? null : snapshot.classes().get(id);
    }

    public SubclassDef subclassDef(String id) {
        return id == null ? null : snapshot.subclasses().get(id);
    }

    public Collection<String> classIds() {
        return new java.util.TreeSet<>(snapshot.classes().keySet());
    }

    public Map<String, List<String>> errors() {
        return snapshot.errors();
    }

    public Map<String, String> rawClasses() {
        return snapshot.rawClasses();
    }

    public Map<String, String> rawSubclasses() {
        return snapshot.rawSubclasses();
    }
}
