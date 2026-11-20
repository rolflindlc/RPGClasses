package io.github.rpgclasses.server;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.config.ServerConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Registro de depuración del mod: qué habilidades se usan, qué pasivas se comprueban, efectos, daño, curas y errores.
 * <ul>
 *   <li>Archivo propio: {@code logs/rites_and_paths-debug.log} (el de la sesión anterior queda en {@code rites_and_paths-debug.prev.log}).</li>
 *   <li>Memoria: últimas líneas por jugador, para {@code /clases debug log} y {@code /clases debug volcar}.</li>
 *   <li>Los errores van siempre (también a latest.log); los eventos, solo si el registro está activo
 *       (config {@code debugLog} o {@code /clases debug log on|off}).</li>
 * </ul>
 * Pensado para que Daniel pase un único archivo cuando algo falla.
 */
public final class DebugLog {
    /** Logger propio: el capturador de errores lo ignora para no duplicar líneas. */
    static final String LOGGER_NAME = "rites_and_paths-debug";
    private static final Logger OUT = LoggerFactory.getLogger(LOGGER_NAME);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final long MAX_FILE_BYTES = 32L * 1024 * 1024;
    private static final int PER_PLAYER = 400;
    private static final int GLOBAL = 300;
    private static final int ERRORS = 60;

    private static final Map<UUID, Deque<String>> BY_PLAYER = new HashMap<>();
    private static final Deque<String> GLOBAL_LINES = new ArrayDeque<>();
    private static final Deque<String> ERROR_LINES = new ArrayDeque<>();
    /** Última vez (ms) que se registró cada clave limitada. */
    private static final Map<String, Long> THROTTLE = new HashMap<>();

    private static Boolean override;
    private static BufferedWriter writer;
    private static boolean fileBroken;
    private static long written;
    /** Última vez (ms) que se volcó el archivo: las líneas INFO se agrupan y se vuelcan como mucho una vez por segundo. */
    private static long lastFlush;

    // ------------------------------------------------------------------ estado

    public static boolean enabled() {
        if (override != null) return override;
        try {
            return ServerConfig.DEBUG_LOG.get();
        } catch (Throwable notLoaded) {
            return true; // config aún sin cargar (arranque): mejor registrar de más
        }
    }

    public static void setEnabled(boolean on) {
        override = on;
        event(null, "registro", on ? "activado" : "desactivado");
    }

    // ------------------------------------------------------------------ escritura

    /** Evento normal. {@code player} puede ser null (evento global). Formato estilo SLF4J con {}. */
    public static void event(Player player, String source, String format, Object... args) {
        if (!enabled()) return;
        record(player, "INFO", source, format(format, args), null);
    }

    /** Igual que {@link #event}, pero como mucho una vez cada {@code millis} por clave (jugador + clave). */
    public static void throttled(Player player, String key, long millis, String source, String format, Object... args) {
        if (!enabled()) return;
        String full = (player == null ? "-" : player.getUUID().toString()) + "|" + key;
        long now = System.currentTimeMillis();
        synchronized (THROTTLE) {
            Long last = THROTTLE.get(full);
            if (last != null && now - last < millis) return;
            THROTTLE.put(full, now);
            if (THROTTLE.size() > 5000) THROTTLE.clear();
        }
        record(player, "INFO", source, format(format, args), null);
    }

    /** Aviso: algo raro pero sin excepción. Siempre se registra. */
    public static void warn(Player player, String source, String format, Object... args) {
        String msg = format(format, args);
        record(player, "WARN", source, msg, null);
        OUT.warn("[rites_and_paths] {}: {}", source, msg);
    }

    /** Error con excepción. Siempre se registra (archivo, memoria y latest.log). */
    public static void error(Player player, String source, Throwable error, String format, Object... args) {
        String msg = format(format, args);
        record(player, "ERROR", source, msg, error);
        OUT.error("[rites_and_paths] {}: {}", source, msg, error);
    }

    /** Ejecuta {@code body}; si lanza, se registra y el juego sigue. */
    public static void guard(Player player, String source, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            error(player, source, t, "excepción capturada");
        }
    }

    /** Línea de error que llega desde otro logger (capturador de log4j). */
    static void captured(String level, String loggerName, String message, Throwable error) {
        record(null, level, "capturado:" + shortLogger(loggerName), message, error);
    }

    private static synchronized void record(Player player, String level, String source, String message, Throwable error) {
        String who = player == null ? "" : " <" + player.getGameProfile().getName() + ">";
        String line = LocalTime.now().format(TIME) + " [" + level + "] [" + Thread.currentThread().getName() + "]"
                + who + " " + source + ": " + message;
        String trace = error == null ? null : stackTrace(error);

        push(GLOBAL_LINES, line, GLOBAL);
        if (player != null) push(BY_PLAYER.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>()), line, PER_PLAYER);
        if (!"INFO".equals(level)) push(ERROR_LINES, trace == null ? line : line + "\n" + trace, ERRORS);
        writeFile(trace == null ? line : line + "\n" + trace, !"INFO".equals(level));
    }

    private static void push(Deque<String> deque, String line, int cap) {
        deque.addLast(line);
        while (deque.size() > cap) deque.removeFirst();
    }

    /** Vuelca al disco lo pendiente (cada segundo desde el tick del servidor y al pararlo). */
    public static synchronized void flush() {
        if (writer == null || fileBroken) return;
        try {
            writer.flush();
            lastFlush = System.currentTimeMillis();
        } catch (IOException e) {
            fileBroken = true;
            RpgClasses.LOGGER.warn("[rites_and_paths] No se puede escribir logs/rites_and_paths-debug.log", e);
        }
    }

    /** {@code urgent}: avisos y errores se vuelcan al momento; el resto se agrupa para no escribir al disco en cada línea. */
    private static void writeFile(String text, boolean urgent) {
        if (fileBroken || written > MAX_FILE_BYTES) return;
        try {
            if (writer == null) {
                Path dir = FMLPaths.GAMEDIR.get().resolve("logs");
                Files.createDirectories(dir);
                Path file = dir.resolve("rites_and_paths-debug.log");
                if (Files.exists(file)) {
                    Files.move(file, dir.resolve("rites_and_paths-debug.prev.log"), StandardCopyOption.REPLACE_EXISTING);
                }
                writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                String header = "Rolflin's Rites & Paths " + modVersion() + " — registro de depuración, " + java.time.LocalDateTime.now();
                writer.write(header);
                writer.newLine();
            }
            writer.write(text);
            writer.newLine();
            long now = System.currentTimeMillis();
            if (urgent || now - lastFlush >= 1000) {
                writer.flush();
                lastFlush = now;
            }
            written += text.length() + 1;
            if (written > MAX_FILE_BYTES) {
                writer.write("[registro detenido: más de 32 MB; los errores siguen en latest.log]");
                writer.newLine();
                writer.flush();
            }
        } catch (IOException | RuntimeException e) {
            fileBroken = true;
            RpgClasses.LOGGER.warn("[rites_and_paths] No se puede escribir logs/rites_and_paths-debug.log", e);
        }
    }

    // ------------------------------------------------------------------ lectura

    public static synchronized List<String> recent(UUID player, int count) {
        Deque<String> deque = player == null ? GLOBAL_LINES : BY_PLAYER.getOrDefault(player, new ArrayDeque<>());
        List<String> all = new ArrayList<>(deque);
        return all.subList(Math.max(0, all.size() - count), all.size());
    }

    public static synchronized List<String> errors() {
        return new ArrayList<>(ERROR_LINES);
    }

    public static synchronized void forget(UUID player) {
        BY_PLAYER.remove(player);
    }

    // ------------------------------------------------------------------ utilidades

    /** Descripción corta de una entidad para los registros: tipo, nombre e id. */
    public static String describe(Entity entity) {
        if (entity == null) return "nadie";
        if (entity instanceof Player p) return p.getGameProfile().getName();
        net.minecraft.resources.ResourceLocation type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return (type == null ? entity.getClass().getSimpleName() : type.toString()) + "#" + entity.getId();
    }

    public static String num(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public static String modVersion() {
        return net.minecraftforge.fml.ModList.get() == null ? "?" : net.minecraftforge.fml.ModList.get()
                .getModContainerById(RpgClasses.MODID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("?");
    }

    static String stackTrace(Throwable error) {
        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        return sw.toString().stripTrailing();
    }

    private static String shortLogger(String name) {
        if (name == null) return "?";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }

    /** Sustituye cada {} por el siguiente argumento (como SLF4J, sin depender de él). */
    static String format(String format, Object... args) {
        if (args == null || args.length == 0) return format;
        StringBuilder sb = new StringBuilder();
        int arg = 0;
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);
            if (c == '{' && i + 1 < format.length() && format.charAt(i + 1) == '}' && arg < args.length) {
                Object value = args[arg++];
                sb.append(value instanceof Double d ? num(d) : value instanceof Float f ? num(f) : String.valueOf(value));
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private DebugLog() {}
}
