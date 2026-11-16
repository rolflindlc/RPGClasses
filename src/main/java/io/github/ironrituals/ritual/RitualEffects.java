package io.github.ironrituals.ritual;

import io.github.ironrituals.IronRituals;
import io.github.ironrituals.ritual.effect.DelugeEffect;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Registro de efectos por id de ritual. Un ritual con definición (datapack) pero sin efecto registrado se puede
 * leer en su grimorio, pero el núcleo se niega a canalizarlo. Otros mods pueden registrar efectos propios.
 */
public final class RitualEffects {
    private static final Map<ResourceLocation, RitualEffect> EFFECTS = new HashMap<>();

    static {
        register(IronRituals.id("deluge"), new DelugeEffect());
    }

    private RitualEffects() {}

    public static synchronized void register(ResourceLocation ritual, RitualEffect effect) {
        EFFECTS.put(ritual, effect);
    }

    @Nullable
    public static RitualEffect get(ResourceLocation ritual) {
        return EFFECTS.get(ritual);
    }
}
