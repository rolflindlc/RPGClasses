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
 *   <li>Infernal (fuego, Iron's) y Tritón Moldeador (agua, T.O Magic 'n Extras): conjuros raciales siempre disponibles
 *       en la rueda (no ocupan ranura de libro). Sufren el muro de Magia de la clase (rev. 2.12) y usan el maná y el
 *       enfriamiento normales de Iron's. Los que no existan en el pack (p. ej. sin T.O) simplemente no aparecen.</li>
 *   <li>No-muerto: los conjuros de curación no le curan.</li>
 * </ul>
 */
public final class RacialSpells {
    /**
     * Mecánica → (parámetro con el nivel por rango → ids posibles del conjuro; se usa el primero que exista).
     * Jetstream lleva alternativas por si el id cambia entre versiones de T.O.
     */
    private static final Map<String, String[][]> SPELLS = Map.of(
            "race_racial_spells", new String[][]{
                    {"fire_bolt", "irons_spellbooks:firebolt"}, {"magma_bomb", "irons_spellbooks:magma_bomb"},
                    {"raise_hell", "irons_spellbooks:raise_hell"}},
            "race_aqua_spells", new String[][]{
                    {"hydroshot", "traveloptics:hydroshot"}, {"overflow", "traveloptics:overflow"},
                    {"jet_stream", "traveloptics:jet_steam", "traveloptics:jet_stream", "traveloptics:jetstream"},
                    {"tidal_grasp", "traveloptics:tidal_grasp"}});
    /** Conjuros de agua del Moldeador (para el rito Marea Viva). */
    public static final java.util.Set<String> AQUA_IDS = java.util.Set.of("traveloptics:hydroshot", "traveloptics:overflow",
            "traveloptics:jet_steam", "traveloptics:jet_stream", "traveloptics:jetstream", "traveloptics:tidal_grasp");
    static final String SLOT = "rites_and_paths_racial";

    private static final Map<UUID, Long> SPELL_HEAL_TICK = io.github.rpgclasses.server.SessionState.track(new HashMap<>());

    @SubscribeEvent
    public void onSpellSelection(SpellSelectionManager.SpellSelectionEvent event) {
        // Un Bardo con instrumento en la mano solo ve los conjuros del instrumento.
        if (io.github.rpgclasses.bard.Bard.isBard(event.getEntity()) && io.github.rpgclasses.bard.Bard.instrumentHand(event.getEntity()) != null) return;
        int index = 0;
        for (Map.Entry<String, String[][]> group : SPELLS.entrySet()) {
            ActiveMechanic m = Races.find(event.getEntity(), group.getKey());
            if (m == null) continue;
            for (String[] entry : group.getValue()) {
                int level = (int) m.number(entry[0], 0);
                if (level <= 0) continue;
                AbstractSpell spell = firstExisting(entry);
                if (spell != null) event.addSelectionOption(new SpellData(spell, level, true), SLOT, index++);
            }
        }
    }

    private static AbstractSpell firstExisting(String[] entry) {
        for (int i = 1; i < entry.length; i++) {
            ResourceLocation id = ResourceLocation.tryParse(entry[i]);
            if (id == null) continue;
            AbstractSpell spell = SpellRegistry.getSpell(id);
            if (spell != null && !"irons_spellbooks:none".equals(spell.getSpellId())) return spell;
        }
        return null;
    }

    /** Moldeador: cada conjuro de agua racial lanzado cuenta para el rito Marea Viva (como una habilidad racial). */
    @SubscribeEvent
    public void onCast(io.redspace.ironsspellbooks.api.events.SpellOnCastEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && AQUA_IDS.contains(event.getSpellId()) && Races.has(p, "race_aqua_spells")) {
            io.github.rpgclasses.content.race.Rites.onAbilityUsed(p, "race_aqua_spells");
        }
    }

    // Rev. 2.12: los conjuros raciales sufren el muro de Magia de la clase como cualquier otro conjuro (sin compensación).

    @SubscribeEvent
    public void onSpellHeal(SpellHealEvent event) {
        if (event.getTargetEntity() != null) io.github.rpgclasses.content.race.HealSources.spellHeal(event.getTargetEntity());
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
