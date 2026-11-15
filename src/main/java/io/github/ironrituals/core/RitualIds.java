package io.github.ironrituals.core;

import java.util.List;

/** Ids (sin espacio de nombres) de los 29 rituales del catálogo, en el orden del plan (sección 4). */
public final class RitualIds {
    public static final List<String> ALL = List.of(
            // A. Entorno y tiempo
            "sun", "night", "deluge",
            // B. Zonas y barreras
            "dome", "prison", "arcane_suppression", "sanctuary", "arcane_spring", "sentinel",
            "sepulchral_silence", "inverted_gravity", "time_distortion",
            // C. Estado del jugador
            "mirror", "phoenix", "metamorphosis", "shrinking", "scapegoat", "temporal_paradox", "soul_anchor",
            // D. Entidades
            "silent_echo", "eternal_bond", "guardian",
            // E. Viaje
            "planar_nexus", "intrusion", "astral_projection",
            // F. Objetos y terreno
            "eternal_forge", "terraforming", "harvest", "custody"
    );

    private RitualIds() {}

    public static boolean isKnown(String id) {
        return ALL.contains(id);
    }
}
