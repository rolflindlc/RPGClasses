package io.github.rpgclasses.content.race;

import net.minecraft.world.entity.player.Player;

/**
 * Formas temporales del Coloso (Titán, adenda 0.7, fase R3c). Mientras no esté programada la forma Titán,
 * ningún jugador está "crecido" y los valores son los del tronco.
 */
public final class ColossusForms {
    private ColossusForms() {}

    /** true mientras dura la forma Titán. */
    public static boolean isGrown(Player p) {
        return false;
    }

    /** Lado del cubo de rotura de la forma (1 = la forma no lo cambia). */
    public static int areaSize(Player p) {
        return 1;
    }

    /** Caída segura de la forma en bloques (0 = la forma no la cambia). */
    public static double fallHeight(Player p) {
        return 0;
    }
}
