package io.github.rpgclasses.compat.irons;

import io.github.rpgclasses.cleric.ClericBuffs;
import io.github.rpgclasses.cleric.ClericHealing;
import io.github.rpgclasses.cleric.Prayer;
import io.redspace.ironsspellbooks.api.events.SpellHealEvent;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Clérigo × Iron's Spells.
 * <ul>
 *   <li>{@code SpellHealEvent} (lanzador y objetivo, justo antes de curar) → {@link ClericHealing}.</li>
 *   <li>{@code SpellOnCastEvent} (ya con el coste final, dentro de {@code castSpell}, justo antes de ejecutar el conjuro)
 *       → abre la ventana de lanzamiento de {@link ClericBuffs} y lleva el cuerpo del Intercesor al ángel ({@link Prayer}).</li>
 *   <li>Retorno de Fe: maná del último conjuro de cada jugador.</li>
 * </ul>
 */
public final class ClericSpells {
    private record LastCast(float mana, long tick) {}

    private static final Map<UUID, LastCast> LAST = io.github.rpgclasses.server.SessionState.track(new HashMap<>());

    public static void init() {
        ClericHealing.manaInfo = player -> {
            LastCast c = LAST.get(player.getUUID());
            // Conjuros continuos (nubes): cuenta el maná del lanzamiento durante unos segundos.
            return c == null || player.level().getGameTime() - c.tick() > 20 * 10 ? 0 : c.mana();
        };
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCast(SpellOnCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        float mana = event.getCastSource().consumesMana() ? event.getManaCost() : 0;
        LAST.put(player.getUUID(), new LastCast(mana, player.level().getGameTime()));
        ClericBuffs.castStart(player, event.getSpellId(), mana);
    }

    @SubscribeEvent
    public void onSpellHeal(SpellHealEvent event) {
        ClericHealing.spellHeal(event.getEntity(), event.getTargetEntity());
    }

    @SubscribeEvent
    public void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
    }
}
