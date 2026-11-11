package io.github.rpgclasses.server;

import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.stat.StatCalculator;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mecánicas activas de cada jugador (ramas, perks y subclase), leídas de {@link StatCalculator#mechanics}.
 * Se cachean por jugador y se invalidan en {@link StatEngine#recompute}, que se llama tras cualquier cambio.
 */
public final class MechanicService {
    private static final Map<UUID, List<ActiveMechanic>> CACHE = new HashMap<>();

    /** Todas las mecánicas activas del jugador (lista vacía sin clase). */
    public static List<ActiveMechanic> all(ServerPlayer player) {
        if (player instanceof FakePlayer) return List.of();
        return CACHE.computeIfAbsent(player.getUUID(), id -> compute(player));
    }

    /** La mecánica con ese id (con o sin el prefijo "rpgclasses:"), o null si no la tiene. */
    public static ActiveMechanic find(ServerPlayer player, String id) {
        String full = id.contains(":") ? id : Ids.NS + ":" + id;
        for (ActiveMechanic m : all(player)) if (m.id().equals(full)) return m;
        return null;
    }

    public static boolean has(ServerPlayer player, String id) {
        return find(player, id) != null;
    }

    /** Valor de un parámetro de la mecánica, o {@code fallback} si no la tiene o no define ese parámetro. */
    public static double number(ServerPlayer player, String id, String key, double fallback) {
        ActiveMechanic m = find(player, id);
        return m == null ? fallback : m.number(key, fallback);
    }

    public static void invalidate(ServerPlayer player) {
        CACHE.remove(player.getUUID());
    }

    private static List<ActiveMechanic> compute(ServerPlayer player) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return List.of();
        ClassDef classDef = DefinitionStore.SERVER.classDef(data.state.classId);
        if (classDef == null) return List.of();
        return List.copyOf(StatCalculator.mechanics(data.state, classDef, DefinitionStore.SERVER.subclassDef(data.state.subclassId)));
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CACHE.remove(event.getEntity().getUUID());
    }

    private MechanicService() {}

    /** Instancia solo para el bus de eventos. */
    public static MechanicService listener() {
        return new MechanicService();
    }
}
