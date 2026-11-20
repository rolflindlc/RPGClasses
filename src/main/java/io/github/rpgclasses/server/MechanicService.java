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
    private static final Map<UUID, List<ActiveMechanic>> CACHE = io.github.rpgclasses.server.SessionState.track(new HashMap<>());
    /** Ids de la última lista calculada, para registrar qué pasivas se ganan o se pierden. */
    private static final Map<UUID, java.util.Set<String>> LAST_IDS = io.github.rpgclasses.server.SessionState.track(new HashMap<>());

    /** Todas las mecánicas activas del jugador (lista vacía sin clase). */
    public static List<ActiveMechanic> all(ServerPlayer player) {
        if (player instanceof FakePlayer) return List.of();
        return CACHE.computeIfAbsent(player.getUUID(), id -> compute(player));
    }

    /** La mecánica con ese id (con o sin el prefijo "rites_and_paths:"), o null si no la tiene. */
    public static ActiveMechanic find(ServerPlayer player, String id) {
        String full = id.contains(":") ? id : Ids.NS + ":" + id;
        for (ActiveMechanic m : all(player)) {
            if (m.id().equals(full)) {
                // Se consulta decenas de veces por jugador y tick: sin registro activo no se monta ningún texto.
                if (DebugLog.enabled()) {
                    DebugLog.throttled(player, "mech:" + full, 15_000, "pasiva", "{} comprobada (la tiene, nivel {}/{})",
                            full, m.level(), m.maxLevel());
                }
                return m;
            }
        }
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
        List<ActiveMechanic> both = new java.util.ArrayList<>();
        if (classDef != null) both.addAll(StatCalculator.mechanics(data.state, classDef, DefinitionStore.SERVER.subclassDef(data.state.subclassId)));
        // Rasgos de raza y subraza: van con el jugador aunque no tenga clase.
        both.addAll(StatCalculator.raceMechanics(data.state, io.github.rpgclasses.core.definition.RaceStore.SERVER.race(data.state.raceId)));
        List<ActiveMechanic> list = List.copyOf(both);
        java.util.Set<String> ids = new java.util.TreeSet<>();
        for (ActiveMechanic m : list) ids.add(m.id() + "@" + m.level());
        java.util.Set<String> before = LAST_IDS.put(player.getUUID(), ids);
        if (!ids.equals(before)) {
            java.util.Set<String> gained = new java.util.TreeSet<>(ids);
            if (before != null) gained.removeAll(before);
            java.util.Set<String> lost = before == null ? new java.util.TreeSet<>() : new java.util.TreeSet<>(before);
            lost.removeAll(ids);
            DebugLog.event(player, "pasivas", "activas ahora {}; nuevas {}; perdidas {}", ids, gained, lost);
        }
        return list;
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CACHE.remove(event.getEntity().getUUID());
        LAST_IDS.remove(event.getEntity().getUUID());
    }

    private MechanicService() {}

    /** Instancia solo para el bus de eventos. */
    public static MechanicService listener() {
        return new MechanicService();
    }
}
