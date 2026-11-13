package io.github.rpgclasses.compat.irons;

import io.github.rpgclasses.content.race.Races;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.server.StatEngine;
import io.redspace.ironsspellbooks.api.events.SpellHealEvent;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Razas × Iron's Spells.
 * <ul>
 *   <li>Infernal: conjuros raciales de fuego siempre disponibles en la rueda (no ocupan ranura de libro) y a los
 *       que no afecta el muro de Magia. Usan el maná y el enfriamiento normales de Iron's.</li>
 *   <li>No-muerto: los conjuros de curación no le curan.</li>
 * </ul>
 */
public final class RacialSpells {
    /** parámetro de la mecánica (nivel por rango) → conjuro de Iron's. */
    private static final String[][] SPELLS = {{"fire_bolt", "firebolt"}, {"magma_bomb", "magma_bomb"}, {"raise_hell", "raise_hell"}};
    static final String SLOT = "rpgclasses_racial";

    private static final Map<UUID, Long> SPELL_HEAL_TICK = new HashMap<>();

    @SubscribeEvent
    public void onSpellSelection(SpellSelectionManager.SpellSelectionEvent event) {
        ActiveMechanic m = Races.find(event.getEntity(), "race_racial_spells");
        if (m == null) return;
        int index = 0;
        for (String[] entry : SPELLS) {
            int level = (int) m.number(entry[0], 0);
            if (level <= 0) continue;
            AbstractSpell spell = SpellRegistry.getSpell(new ResourceLocation(IronsCompat.IRONS, entry[1]));
            if (spell == null || "irons_spellbooks:none".equals(spell.getSpellId())) continue;
            event.addSelectionOption(new SpellData(spell, level, true), SLOT, index++);
        }
    }

    // Rev. 2.12: los conjuros raciales sufren el muro de Magia de la clase como cualquier otro conjuro (sin compensación).

    @SubscribeEvent
    public void onSpellHeal(SpellHealEvent event) {
        if (event.getTargetEntity() instanceof ServerPlayer target && Races.has(target, "race_lifeless")) {
            SPELL_HEAL_TICK.put(target.getUUID(), target.level().getGameTime());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHeal(LivingHealEvent event) {
        if (SPELL_HEAL_TICK.isEmpty() || !(event.getEntity() instanceof ServerPlayer player)) return;
        Long tick = SPELL_HEAL_TICK.remove(player.getUUID());
        if (tick != null && tick == player.level().getGameTime()) event.setCanceled(true);
    }
}
