package io.github.rpgclasses.server;

import io.github.rpgclasses.RpgClasses;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;

/**
 * Copia en {@code rpgclasses-debug.log} todo aviso o error de cualquier mod que mencione a RPG Classes
 * (excepciones en nuestros eventos, en el render, en paquetes, avisos de Forge...). Así no hace falta buscar
 * en latest.log: todo lo relevante acaba en el mismo archivo.
 */
final class DebugErrorCapture extends AbstractAppender {
    private static final String PACKAGE = "io.github.rpgclasses";
    private static boolean installed;

    private DebugErrorCapture() {
        super("RpgClassesDebugCapture", null, null, true, Property.EMPTY_ARRAY);
    }

    static synchronized void install() {
        if (installed) return;
        installed = true;
        try {
            if (!(LogManager.getContext(false) instanceof LoggerContext ctx)) return;
            DebugErrorCapture appender = new DebugErrorCapture();
            appender.start();
            Configuration config = ctx.getConfiguration();
            config.addAppender(appender);
            config.getRootLogger().addAppender(appender, Level.WARN, null);
            ctx.updateLoggers();
        } catch (Throwable t) {
            RpgClasses.LOGGER.warn("[rpgclasses] No se pudo instalar el capturador de errores del registro de depuración", t);
        }
    }

    @Override
    public void append(LogEvent event) {
        try {
            if (DebugLog.LOGGER_NAME.equals(event.getLoggerName())) return;
            if (!event.getLevel().isMoreSpecificThan(Level.WARN)) return;
            String message = event.getMessage() == null ? "" : event.getMessage().getFormattedMessage();
            Throwable error = event.getThrown();
            if (!mentionsUs(message, event.getLoggerName(), error)) return;
            DebugLog.captured(event.getLevel().name(), event.getLoggerName(), message, error);
        } catch (Throwable ignored) {
            // Un fallo aquí nunca debe romper el log del juego.
        }
    }

    private static boolean mentionsUs(String message, String logger, Throwable error) {
        if (message.contains("rpgclasses") || (logger != null && logger.startsWith(PACKAGE))) return true;
        for (Throwable t = error; t != null; t = t.getCause()) {
            for (StackTraceElement e : t.getStackTrace()) {
                if (e.getClassName().startsWith(PACKAGE)) return true;
            }
        }
        return false;
    }
}
