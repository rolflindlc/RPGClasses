package io.github.rpgclasses.server;

import com.google.gson.JsonObject;
import io.github.rpgclasses.core.ability.UnlockResolver;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.definition.Definitions.AbilityDef;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Punto de entrada de las habilidades en el servidor: qué tiene cada jugador, cooldowns y activación.
 * Los números de cada habilidad se leen siempre de su JSON (abilities/*.json).
 */
public final class AbilityService {

    /** Comportamiento de una habilidad activa. Devuelve true si se ha usado (y debe empezar el cooldown). */
    public interface Active {
        boolean activate(ServerPlayer player, JsonObject params);
    }

    private static final Map<String, Active> ACTIVES = new HashMap<>();
    private static final Map<String, java.util.function.ToDoubleFunction<ServerPlayer>> COOLDOWN_MODIFIERS = new HashMap<>();
    private static final JsonObject NO_PARAMS = new JsonObject();

    public static void register(String id, Active active) {
        ACTIVES.put(id, active);
    }

    /** Factor sobre el cooldown de una habilidad (perks y ramas que lo cambian). */
    public static void registerCooldownModifier(String id, java.util.function.ToDoubleFunction<ServerPlayer> factor) {
        COOLDOWN_MODIFIERS.put(id, factor);
    }

    public static Set<String> unlocked(ServerPlayer player) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return Set.of();
        ClassDef classDef = DefinitionStore.SERVER.classDef(data.state.classId);
        return UnlockResolver.unlocked(data.state, classDef, DefinitionStore.SERVER.subclassDef(data.state.subclassId));
    }

    public static boolean has(ServerPlayer player, String abilityId) {
        return unlocked(player).contains(abilityId);
    }

    public static JsonObject params(String abilityId) {
        AbilityDef def = DefinitionStore.SERVER.abilityDef(abilityId);
        return def == null ? NO_PARAMS : def.params();
    }

    public static double number(JsonObject params, String key, double fallback) {
        return params.has(key) && params.get(key).isJsonPrimitive() ? params.get(key).getAsDouble() : fallback;
    }

    public static int ticks(JsonObject params, String secondsKey, double fallbackSeconds) {
        return (int) Math.round(number(params, secondsKey, fallbackSeconds) * 20);
    }

    // --- Cooldowns (guardados con el jugador: reconectar no los reinicia) ---

    public static long remainingTicks(ServerPlayer player, String key) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return 0;
        return Math.max(0, data.cooldowns.getOrDefault(key, 0L) - player.level().getGameTime());
    }

    public static void setCooldown(ServerPlayer player, String key, int ticks) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return;
        long now = player.level().getGameTime();
        data.cooldowns.values().removeIf(end -> end <= now);
        if (ticks > 0) data.cooldowns.put(key, now + ticks);
    }

    /** El jugador pulsa la tecla de la ranura {@code slot} (0, 1, 2). Todo se valida aquí. */
    public static void use(ServerPlayer player, int slot) {
        if (slot < 0 || !player.isAlive() || player.isSpectator()) return;
        List<String> actives = UnlockResolver.actives(unlocked(player), DefinitionStore.SERVER::abilityDef);
        if (slot >= actives.size()) return;
        String id = actives.get(slot);
        Component name = Component.translatable(Ids.langKey("ability", id, "name"));

        Active active = ACTIVES.get(id);
        if (active == null) {
            player.displayClientMessage(Component.translatable("msg.rpgclasses.ability_not_ready", name), true);
            return;
        }
        long remaining = remainingTicks(player, id);
        if (remaining > 0) {
            player.displayClientMessage(Component.translatable("msg.rpgclasses.ability_cooldown", name,
                    String.format(Locale.ROOT, "%.1f", remaining / 20.0)), true);
            return;
        }
        JsonObject params = params(id);
        if (active.activate(player, params)) {
            double factor = COOLDOWN_MODIFIERS.containsKey(id) ? Math.max(0, COOLDOWN_MODIFIERS.get(id).applyAsDouble(player)) : 1;
            setCooldown(player, id, (int) Math.round(ticks(params, "cooldown_seconds", 0) * factor));
            player.displayClientMessage(Component.translatable("msg.rpgclasses.ability_used", name), true);
            Net.syncPlayer(player);
        }
    }

    private AbilityService() {}
}
