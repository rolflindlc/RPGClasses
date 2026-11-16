package io.github.rpgclasses.content.race;

import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ¿De dónde viene una curación? Las razas que cambian la curación de "pociones y conjuros" (No-muerto, Unicornio,
 * Slime Enjambre) lo consultan en {@code LivingHealEvent}. Las pociones las marca {@code InstantHealMixin} mientras
 * se aplica Curación instantánea o Regeneración; los conjuros de Iron's, {@code RacialSpells} con su evento de curación.
 */
public final class HealSources {
    private static final ThreadLocal<Boolean> POTION = ThreadLocal.withInitial(() -> false);
    private static final Map<UUID, Long> SPELL_TICK = new HashMap<>();

    private HealSources() {}

    public static void potionStart() {
        POTION.set(true);
    }

    public static void potionEnd() {
        POTION.set(false);
    }

    public static void spellHeal(LivingEntity target) {
        SPELL_TICK.put(target.getUUID(), target.level().getGameTime());
    }

    /** true si la curación que se está aplicando ahora viene de una poción o de un conjuro. */
    public static boolean potionOrSpell(LivingEntity target) {
        if (POTION.get()) return true;
        Long tick = SPELL_TICK.get(target.getUUID());
        if (tick == null) return false;
        if (tick != target.level().getGameTime()) {
            SPELL_TICK.remove(target.getUUID());
            return false;
        }
        return true;
    }
}
