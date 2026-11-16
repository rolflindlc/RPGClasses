package io.github.ironrituals.ritual;

import io.github.ironrituals.ritual.active.ActiveRitual;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Efecto de un ritual. Los instantáneos solo implementan {@link #apply}; los persistentes registran un
 * {@link ActiveRitual} con {@link RitualContext#startPersistent} y reciben {@link #tick} y {@link #onEnd}.
 */
public interface RitualEffect {

    /**
     * Comprobación previa a la canalización (dimensión, objetivo válido...). Devuelve el motivo del rechazo o
     * {@code null} si puede empezar. Si se rechaza, no se toma ningún componente.
     */
    @Nullable
    default Component checkStart(RitualContext ctx) {
        return null;
    }

    /** Se aplica al completar la canalización, con los componentes ya consumidos. */
    void apply(RitualContext ctx);

    /** Tick de un ritual persistente activo (cada {@link #tickInterval()} ticks, solo si no está pausado). */
    default void tick(ActiveRitual ritual, ServerLevel level) {}

    /** Fin de un ritual persistente (por tiempo, núcleo destruido o comando). */
    default void onEnd(ActiveRitual ritual, ServerLevel level) {}

    default int tickInterval() {
        return 20;
    }
}
