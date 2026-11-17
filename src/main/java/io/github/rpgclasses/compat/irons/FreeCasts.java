package io.github.rpgclasses.compat.irons;

import com.google.gson.JsonObject;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.server.AbilityService;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Conjuros Gratuitos por Cargas (Mago, hitos 5/15/30). Cada ranura fija para siempre un conjuro
 * (<code>/clase conjuro &lt;ranura&gt;</code> con el conjuro elegido en la rueda de Iron's). Si quedan cargas,
 * el coste base del conjuro no supera el límite de la ranura y no hay ICD, el lanzamiento gasta una carga
 * en lugar de maná (cuenta como 0 de maná gastado). Recarga propia de 120 s por carga, sin CDR.
 *
 * <p>Datos en {@link PlayerClassData}: {@code texts["free_cast_spell_N"]} (conjuro fijado),
 * {@code counters["free_cast_used_N"]} (cargas gastadas) y {@code counters["free_cast_timer_N"]} (recarga en curso).
 * Se borran al reiniciar la clase.
 */
public final class FreeCasts {
    public static final int SLOTS = 3;
    private static final String ICD = "rites_and_paths:free_cast_icd";

    public static String abilityId(int slot) {
        return Ids.NS + ":free_cast_slot_" + slot;
    }

    private static String rechargeKey(int slot) {
        return "rites_and_paths:free_cast_recharge_" + slot;
    }

    /** Ranura (1..3) con la que se puede lanzar gratis ese conjuro ahora mismo, o 0. */
    static int usableSlot(ServerPlayer player, String spellId, float baseCost) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null || AbilityService.remainingTicks(player, ICD) > 0) return 0;
        for (int slot = 1; slot <= SLOTS; slot++) {
            if (!spellId.equals(data.texts.get("free_cast_spell_" + slot)) || !AbilityService.has(player, abilityId(slot))) continue;
            JsonObject p = AbilityService.params(abilityId(slot));
            recharge(player, data, slot, p);
            int max = (int) AbilityService.number(p, "charges", slot);
            if (data.counters.getOrDefault("free_cast_used_" + slot, 0) < max
                    && baseCost <= AbilityService.number(p, "max_mana_cost", 75)) {
                return slot;
            }
        }
        return 0;
    }

    static void consume(ServerPlayer player, int slot) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return;
        JsonObject p = AbilityService.params(abilityId(slot));
        int used = data.counters.getOrDefault("free_cast_used_" + slot, 0) + 1;
        data.counters.put("free_cast_used_" + slot, used);
        AbilityService.setCooldown(player, ICD, AbilityService.ticks(p, "icd_seconds", 3));
        recharge(player, data, slot, p);
        int max = (int) AbilityService.number(p, "charges", slot);
        player.displayClientMessage(Component.translatable("message.rites_and_paths.free_cast_used", slot, max - used, max), true);
        io.github.rpgclasses.network.Net.syncPlayer(player); // la interfaz muestra las cargas
    }

    /** Avanza las recargas de todas las ranuras desbloqueadas. */
    static void recharge(ServerPlayer player) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return;
        for (int slot = 1; slot <= SLOTS; slot++) {
            if (AbilityService.has(player, abilityId(slot))) recharge(player, data, slot, AbilityService.params(abilityId(slot)));
        }
    }

    private static void recharge(ServerPlayer player, PlayerClassData data, int slot, JsonObject p) {
        String usedKey = "free_cast_used_" + slot, timerKey = "free_cast_timer_" + slot;
        int used = data.counters.getOrDefault(usedKey, 0);
        if (used <= 0 || AbilityService.remainingTicks(player, rechargeKey(slot)) > 0) return;
        if (data.counters.getOrDefault(timerKey, 0) == 1) {
            used--;
            data.counters.put(usedKey, used);
            data.counters.put(timerKey, 0);
            io.github.rpgclasses.network.Net.syncPlayer(player);
        }
        if (used > 0) {
            AbilityService.setCooldown(player, rechargeKey(slot), AbilityService.ticks(p, "recharge_seconds", 120));
            data.counters.put(timerKey, 1);
        }
    }

    /** /clase conjuro &lt;ranura&gt;: fija el conjuro seleccionado en la rueda de Iron's. */
    static int bind(ServerPlayer player, int slot) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null || !AbilityService.has(player, abilityId(slot))) {
            player.sendSystemMessage(Component.translatable("message.rites_and_paths.free_cast_locked", slot));
            return 0;
        }
        String key = "free_cast_spell_" + slot;
        if (data.texts.containsKey(key)) {
            player.sendSystemMessage(Component.translatable("message.rites_and_paths.free_cast_already", slot,
                    spellName(player, data.texts.get(key))));
            return 0;
        }
        SpellData selected = new SpellSelectionManager(player).getSelectedSpellData();
        if (selected == null || selected == SpellData.EMPTY || "irons_spellbooks:none".equals(selected.getSpell().getSpellId())) {
            player.sendSystemMessage(Component.translatable("message.rites_and_paths.free_cast_none"));
            return 0;
        }
        String spellId = selected.getSpell().getSpellId();
        if (data.texts.containsValue(spellId)) {
            player.sendSystemMessage(Component.translatable("message.rites_and_paths.free_cast_duplicate"));
            return 0;
        }
        data.texts.put(key, spellId);
        int maxCost = (int) AbilityService.number(AbilityService.params(abilityId(slot)), "max_mana_cost", 75);
        player.sendSystemMessage(Component.translatable("message.rites_and_paths.free_cast_bound",
                selected.getSpell().getDisplayName(player), slot, maxCost));
        return 1;
    }

    private static Component spellName(ServerPlayer player, String spellId) {
        AbstractSpell spell = SpellRegistry.getSpell(spellId);
        return spell == null ? Component.literal(spellId) : spell.getDisplayName(player);
    }

    private FreeCasts() {}
}
