package io.github.rpgclasses.difficulty;

import io.github.rpgclasses.config.ServerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Los dos ajustes de la dificultad que se cambian en la partida (menú de {@link DifficultyMenu}) y se guardan en el
 * config del servidor del mundo: de qué depende T ({@link Mode}) y cuánto pesan los incrementos ({@link Intensity}).
 */
public final class DifficultySettings {
    /** De qué depende la amenaza T. */
    public enum Mode {
        /** Media del nivel de clase de los jugadores cercanos (el modo original). */
        PLAYERS("players", "jugadores"),
        /** Solo crece con la distancia al spawn del mundo. */
        DISTANCE("distance", "distancia"),
        /** Media ponderada de los dos. */
        MIXED("mixed", "mixto");

        /** Sufijo de las claves de idioma. */
        public final String id;
        /** Nombre en el comando /clases dificultad modo. */
        public final String literal;

        Mode(String id, String literal) {
            this.id = id;
            this.literal = literal;
        }
    }

    /** Cuánto pesan los incrementos de dificultad: apagado los quita, fácil los divide por dos y difícil los duplica. */
    public enum Intensity {
        OFF("off", "apagado", 0.0),
        EASY("easy", "facil", 0.5),
        NORMAL("normal", "normal", 1.0),
        HARD("hard", "dificil", 2.0);

        public final String id;
        public final String literal;
        public final double factor;

        Intensity(String id, String literal, double factor) {
            this.id = id;
            this.literal = literal;
            this.factor = factor;
        }
    }

    private DifficultySettings() {}

    public static Mode mode() { return ServerConfig.DIFFICULTY_MODE.get(); }

    public static Intensity intensity() { return ServerConfig.DIFFICULTY_INTENSITY.get(); }

    /** Multiplicador de los incrementos (0,5 fácil, 1 normal, 2 difícil). */
    public static double factor() { return intensity().factor; }

    /** El módulo trabaja: interruptor general encendido e intensidad distinta de Apagado. */
    public static boolean active() {
        return ServerConfig.DIFFICULTY_ENABLED.get() && intensity() != Intensity.OFF;
    }

    public static void setMode(Mode mode) {
        ServerConfig.DIFFICULTY_MODE.set(mode);
        ServerConfig.DIFFICULTY_MODE.save();
    }

    public static void setIntensity(Intensity intensity) {
        ServerConfig.DIFFICULTY_INTENSITY.set(intensity);
        ServerConfig.DIFFICULTY_INTENSITY.save();
    }

    /** Operadores y, en un mundo de un jugador, su dueño aunque no tenga los trucos activados. */
    public static boolean canConfigure(CommandSourceStack source) {
        if (source.hasPermission(2)) return true;
        return source.getEntity() instanceof ServerPlayer player && source.getServer().isSingleplayerOwner(player.getGameProfile());
    }
}
