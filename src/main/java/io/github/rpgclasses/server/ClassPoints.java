package io.github.rpgclasses.server;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.config.ServerConfig;
import io.github.rpgclasses.core.progression.CatchUp;
import io.github.rpgclasses.core.progression.ProgressState;
import io.github.rpgclasses.core.progression.ProgressionRules;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Puntos de clase fuera del árbol (servidor):
 * <ul>
 *   <li><b>Equilibrio de puntos</b> ({@link CatchUp}): el precio en XP de los puntos baja para quien va por debajo de la
 *       media de los demás jugadores activos y sube para quien va muy por delante. Activo = ha jugado al menos
 *       {@code minPlayMinutes} en las últimas {@code activeHours} horas de tiempo real. El tiempo jugado (por horas) y los
 *       puntos de cada jugador se guardan en el mundo ({@link Activity}).</li>
 *   <li><b>Puntos por jefes:</b> la primera vez que muere cada especie de la etiqueta
 *       {@code rites_and_paths:class_point_bosses}, todos los jugadores que le hicieron daño reciben sus puntos. Si ya
 *       tienen los 40 puntos, el jefe queda apuntado igualmente.</li>
 * </ul>
 */
public final class ClassPoints {
    public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE,
            new ResourceLocation(RpgClasses.MODID, "class_point_bosses"));

    /** Jugadores que han dañado a cada jefe vivo (id de entidad → jugadores). */
    private static final Map<UUID, Set<UUID>> PARTICIPANTS = SessionState.track(new HashMap<>());

    // ------------------------------------------------------------------ equilibrio de puntos

    /** Último descuento enviado a cada cliente, para reenviarlo solo cuando cambia. */
    private static final Map<UUID, Double> SENT = SessionState.track(new HashMap<>());
    /** Última muestra de tiempo real de cada jugador conectado (para sumar su tiempo de juego). */
    private static final Map<UUID, Long> LAST_SAMPLE = SessionState.track(new HashMap<>());
    private static int tick;
    private static boolean resyncSoon;

    /** Descuento con signo para este jugador: positivo abarata, negativo encarece (ver {@link CatchUp}). */
    public static double discount(ServerPlayer p) {
        Balance b = balance(p);
        return b == null ? 0 : b.discount();
    }

    /** El descuento que se manda al cliente (se apunta para saber cuándo hay que reenviarlo). */
    public static double clientDiscount(ServerPlayer p) {
        double d = discount(p);
        SENT.put(p.getUUID(), d);
        return d;
    }

    /** Estado del equilibrio para un jugador: cuántos activos hay aparte de él, su media y el descuento resultante. */
    public record Balance(int activeOthers, double average, double discount) {}

    /** {@code null} si el equilibrio está desactivado o no aplica (jugador falso, sin datos). */
    public static Balance balance(ServerPlayer p) {
        if (!ServerConfig.CATCH_UP_ENABLED.get() || p.getServer() == null || p instanceof FakePlayer) return null;
        PlayerClassData data = PlayerClassDataProvider.get(p);
        if (data == null) return null;
        List<Integer> others = Activity.get(p.getServer()).activePoints(p.getServer(), System.currentTimeMillis(), p.getUUID());
        double avg = others.stream().mapToInt(Integer::intValue).average().orElse(0);
        return new Balance(others.size(), avg, CatchUp.discount(data.state.ownedPoints(), others, rules()));
    }

    private static CatchUp.Rules rules() {
        return new CatchUp.Rules(ServerConfig.CATCH_UP_MARGIN.get(), ServerConfig.CATCH_UP_PER_LEVEL.get(),
                ServerConfig.CATCH_UP_MAX.get(), ServerConfig.CATCH_UP_PENALTY_PER_LEVEL.get(),
                ServerConfig.CATCH_UP_MAX_PENALTY.get(), ServerConfig.CATCH_UP_MIN_PLAYERS.get());
    }

    /** Apunta los puntos del jugador (al entrar, salir y cambiar su progreso). */
    public static void touch(ServerPlayer p) {
        if (p.getServer() == null || p instanceof FakePlayer) return;
        PlayerClassData data = PlayerClassDataProvider.get(p);
        if (data == null) return;
        // Si cambian sus puntos, cambia la media de los demás: se les reenvía el precio en el siguiente segundo.
        if (Activity.get(p.getServer()).setPoints(p.getUUID(), data.state.ownedPoints())) resyncSoon = true;
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        LAST_SAMPLE.put(p.getUUID(), System.currentTimeMillis());
        touch(p);
        resyncSoon = true;
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer || p.getServer() == null) return;
        sample(Activity.get(p.getServer()), p.getUUID(), System.currentTimeMillis());
        LAST_SAMPLE.remove(p.getUUID());
        SENT.remove(p.getUUID());
        touch(p);
        resyncSoon = true;
    }

    /** Cada segundo suma el tiempo real jugado; cada minuto limpia las horas viejas y reenvía los precios que cambien. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tick % 20 != 0) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        long now = System.currentTimeMillis();
        Activity activity = Activity.get(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!(p instanceof FakePlayer)) sample(activity, p.getUUID(), now);
        }
        boolean minute = tick % 1200 == 0;
        if (minute) activity.prune(server, now, ServerConfig.CATCH_UP_ACTIVE_HOURS.get());
        if (minute || resyncSoon) {
            resyncSoon = false;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                Double sent = SENT.get(p.getUUID());
                if (sent != null && Math.abs(sent - discount(p)) > 1e-6) Net.syncProgress(p);
            }
        }
    }

    /** Suma el tiempo real desde la última muestra (como mucho un minuto: un servidor colgado no cuenta de más). */
    private static void sample(Activity activity, UUID id, long now) {
        Long last = LAST_SAMPLE.put(id, now);
        if (last != null) activity.addPlay(id, now, Math.max(0, Math.min(60_000, now - last)));
    }

    // ------------------------------------------------------------------ puntos por jefes

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBossHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || !target.getType().is(BOSSES)) return;
        if (event.getSource().getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer)) {
            PARTICIPANTS.computeIfAbsent(target.getUUID(), k -> new HashSet<>()).add(p.getUUID());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBossDeath(LivingDeathEvent event) {
        LivingEntity boss = event.getEntity();
        if (boss.level().isClientSide || event.isCanceled()) return;
        Set<UUID> players = PARTICIPANTS.remove(boss.getUUID());
        if (players == null || !ServerConfig.BOSS_POINTS_ENABLED.get() || boss.getServer() == null) return;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(boss.getType());
        if (id == null) return;
        int points = ServerConfig.BOSS_POINTS.get();
        for (UUID uuid : players) {
            ServerPlayer p = boss.getServer().getPlayerList().getPlayer(uuid);
            PlayerClassData data = p == null ? null : PlayerClassDataProvider.get(p);
            if (data == null) continue;
            ProgressState s = data.state;
            if (!s.bossesCredited.add(id.toString())) continue;
            int added = ProgressionRules.addPoints(s, points);
            if (added > 0) {
                p.sendSystemMessage(Component.translatable("msg.rites_and_paths.boss_points", added, boss.getDisplayName(), s.pointBalance)
                        .withStyle(ChatFormatting.GOLD));
                p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1.2f);
            } else {
                p.sendSystemMessage(Component.translatable("msg.rites_and_paths.boss_points_max", boss.getDisplayName())
                        .withStyle(ChatFormatting.GRAY));
            }
            DebugLog.event(p, "progreso", "jefe {} derrotado: +{} puntos (saldo {})", id, added, s.pointBalance);
            touch(p);
            Net.syncProgress(p);
        }
    }

    // ------------------------------------------------------------------ datos del mundo

    /** Puntos y tiempo jugado (por horas de tiempo real) de cada jugador, para el equilibrio de puntos. */
    public static final class Activity extends SavedData {
        private static final String NAME = "rites_and_paths_class_activity";
        private final Map<UUID, Entry> players = new HashMap<>();

        private static final class Entry {
            int points;
            /** Hora desde la época → milisegundos jugados en esa hora. */
            final TreeMap<Long, Long> hours = new TreeMap<>();
        }

        public static Activity get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Activity::load, Activity::new, NAME);
        }

        /** @return si han cambiado. */
        boolean setPoints(UUID id, int points) {
            Entry e = players.computeIfAbsent(id, k -> new Entry());
            if (e.points == points) return false;
            e.points = points;
            setDirty();
            return true;
        }

        void addPlay(UUID id, long now, long ms) {
            if (ms <= 0) return;
            players.computeIfAbsent(id, k -> new Entry()).hours.merge(now / CatchUp.HOUR_MS, ms, Long::sum);
            setDirty();
        }

        /** Quita las horas que ya han salido de la ventana y a quien, desconectado, no tiene ninguna. */
        void prune(MinecraftServer server, long now, int windowHours) {
            long first = CatchUp.firstHour(now, windowHours);
            boolean changed = false;
            for (var it = players.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Entry> en = it.next();
                Map<Long, Long> old = en.getValue().hours.headMap(first);
                if (!old.isEmpty()) {
                    old.clear();
                    changed = true;
                }
                if (en.getValue().hours.isEmpty() && server.getPlayerList().getPlayer(en.getKey()) == null) {
                    it.remove();
                    changed = true;
                }
            }
            if (changed) setDirty();
        }

        /** Puntos de los jugadores activos menos {@code exclude}; los conectados, con sus puntos de ahora mismo. */
        List<Integer> activePoints(MinecraftServer server, long now, UUID exclude) {
            long first = CatchUp.firstHour(now, ServerConfig.CATCH_UP_ACTIVE_HOURS.get());
            long minMs = ServerConfig.CATCH_UP_MIN_MINUTES.get() * 60_000L;
            List<Integer> out = new ArrayList<>();
            players.forEach((id, e) -> {
                if (id.equals(exclude) || CatchUp.playedMs(e.hours, first) < minMs) return;
                ServerPlayer online = server.getPlayerList().getPlayer(id);
                PlayerClassData data = online == null ? null : PlayerClassDataProvider.get(online);
                out.add(data != null ? data.state.ownedPoints() : e.points);
            });
            return out;
        }

        static Activity load(CompoundTag tag) {
            Activity a = new Activity();
            ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag t = list.getCompound(i);
                if (!t.hasUUID("id")) continue;
                Entry e = new Entry();
                e.points = t.getInt("points");
                long[] hours = t.getLongArray("hours"); // parejas (hora, ms); los datos antiguos no tienen
                for (int j = 0; j + 1 < hours.length; j += 2) e.hours.put(hours[j], hours[j + 1]);
                a.players.put(t.getUUID("id"), e);
            }
            return a;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            players.forEach((id, e) -> {
                CompoundTag t = new CompoundTag();
                t.putUUID("id", id);
                t.putInt("points", e.points);
                long[] hours = new long[e.hours.size() * 2];
                int j = 0;
                for (Map.Entry<Long, Long> h : e.hours.entrySet()) {
                    hours[j++] = h.getKey();
                    hours[j++] = h.getValue();
                }
                t.putLongArray("hours", hours);
                list.add(t);
            });
            tag.put("players", list);
            return tag;
        }
    }

}
