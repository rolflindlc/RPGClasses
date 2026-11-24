package io.github.rpgclasses.server;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.config.ServerConfig;
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
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Puntos de clase fuera del árbol (servidor):
 * <ul>
 *   <li><b>Ayuda a rezagados:</b> descuento en el precio en XP de los puntos para quien tiene menos que la media de los
 *       jugadores activos (los que han entrado en los últimos {@code activeDays} días). Los datos se guardan en el mundo
 *       ({@link Activity}).</li>
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

    // ------------------------------------------------------------------ ayuda a rezagados

    /** Descuento en [0, max] para este jugador. */
    public static double discount(ServerPlayer p) {
        if (!ServerConfig.CATCH_UP_ENABLED.get() || p.getServer() == null) return 0;
        PlayerClassData data = PlayerClassDataProvider.get(p);
        if (data == null) return 0;
        double avg = Activity.get(p.getServer()).average(System.currentTimeMillis(), ServerConfig.CATCH_UP_DAYS.get());
        double below = avg - data.state.ownedPoints();
        if (below <= 0) return 0;
        return Math.min(ServerConfig.CATCH_UP_MAX.get(), below * ServerConfig.CATCH_UP_PER_LEVEL.get());
    }

    /** Apunta los puntos del jugador y la fecha (al entrar, salir y cambiar su progreso). */
    public static void touch(ServerPlayer p) {
        if (p.getServer() == null || p instanceof FakePlayer) return;
        PlayerClassData data = PlayerClassDataProvider.get(p);
        if (data == null) return;
        Activity.get(p.getServer()).put(p.getUUID(), data.state.ownedPoints(), System.currentTimeMillis());
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) touch(p);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) touch(p);
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

    /** Puntos y última entrada de cada jugador (para la media de la ayuda a rezagados). */
    public static final class Activity extends SavedData {
        private static final String NAME = "rites_and_paths_class_activity";
        private static final long DAY_MS = 24L * 60 * 60 * 1000;
        private final Map<UUID, long[]> players = new HashMap<>();

        public static Activity get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Activity::load, Activity::new, NAME);
        }

        void put(UUID id, int points, long now) {
            long[] old = players.get(id);
            if (old != null && old[0] == points && now - old[1] < 60_000) return;
            players.put(id, new long[] {points, now});
            setDirty();
        }

        /** Media de puntos de los jugadores que han entrado en los últimos {@code days} días (0 si no hay). */
        double average(long now, int days) {
            long since = now - days * DAY_MS;
            long sum = 0;
            int n = 0;
            for (long[] v : players.values()) {
                if (v[1] < since) continue;
                sum += v[0];
                n++;
            }
            return n == 0 ? 0 : (double) sum / n;
        }

        static Activity load(CompoundTag tag) {
            Activity a = new Activity();
            ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag t = list.getCompound(i);
                if (t.hasUUID("id")) a.players.put(t.getUUID("id"), new long[] {t.getInt("points"), t.getLong("seen")});
            }
            return a;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            players.forEach((id, v) -> {
                CompoundTag t = new CompoundTag();
                t.putUUID("id", id);
                t.putInt("points", (int) v[0]);
                t.putLong("seen", v[1]);
                list.add(t);
            });
            tag.put("players", list);
            return tag;
        }
    }

}
