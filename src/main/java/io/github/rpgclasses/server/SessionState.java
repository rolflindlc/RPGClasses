package io.github.rpgclasses.server;

import io.github.rpgclasses.RpgClasses;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Estado en memoria que solo vale mientras dura una partida: zonas, trampas, marcas, enfriamientos internos, cachés...
 * Muchos guardan tiempos de juego y UUID de jugadores. En un servidor dedicado se borran solos al apagarlo, pero en un
 * jugador el juego sigue abierto: al salir de un mundo y entrar en otro (o en el mismo) se arrastrarían, con los
 * tiempos del mundo anterior y el mismo UUID del jugador en todos los mundos. Cada colección se apunta aquí al crearse
 * y todas se vacían cuando el servidor termina de pararse ({@link ServerStoppedEvent}), después de que los sistemas
 * que guardan algo al pararse (cinturón del Porteador, formas del Slime y del Coloso...) lo hayan hecho.
 */
public final class SessionState {
    private static final List<Runnable> CLEARERS = new ArrayList<>();

    private SessionState() {}

    /** Apunta un mapa para vaciarlo al cerrar la partida. Devuelve el mismo mapa. */
    public static <T extends Map<?, ?>> T track(T map) {
        synchronized (CLEARERS) {
            CLEARERS.add(map::clear);
        }
        return map;
    }

    /** Apunta una lista o un conjunto para vaciarlo al cerrar la partida. Devuelve la misma colección. */
    public static <T extends Collection<?>> T track(T collection) {
        synchronized (CLEARERS) {
            CLEARERS.add(collection::clear);
        }
        return collection;
    }

    public static void onStopped(ServerStoppedEvent event) {
        List<Runnable> all;
        synchronized (CLEARERS) {
            all = new ArrayList<>(CLEARERS);
        }
        for (Runnable clear : all) {
            try {
                clear.run();
            } catch (RuntimeException e) {
                RpgClasses.LOGGER.warn("[rites_and_paths] No se pudo vaciar el estado de la partida", e);
            }
        }
    }
}
