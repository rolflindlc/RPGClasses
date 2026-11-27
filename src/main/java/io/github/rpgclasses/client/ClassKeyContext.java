package io.github.rpgclasses.client;

import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;

/**
 * Contexto de tecla de una clase concreta: activo en juego como cualquier tecla, pero no choca con la tecla de otra
 * clase (nadie es de dos clases a la vez). Así la G del Maestro del Giro y la del Druida no salen en rojo en Controles;
 * cada una comprueba la clase al pulsarse.
 */
public record ClassKeyContext(String classId) implements IKeyConflictContext {
    public static final ClassKeyContext SPIN_MASTER = new ClassKeyContext("spin_master");
    public static final ClassKeyContext DRUID = new ClassKeyContext("druid");
    public static final ClassKeyContext ENGINEER = new ClassKeyContext("engineer");

    @Override
    public boolean isActive() {
        return KeyConflictContext.IN_GAME.isActive();
    }

    @Override
    public boolean conflicts(IKeyConflictContext other) {
        if (other instanceof ClassKeyContext c) return c.classId.equals(classId);
        return KeyConflictContext.IN_GAME.conflicts(other);
    }
}
