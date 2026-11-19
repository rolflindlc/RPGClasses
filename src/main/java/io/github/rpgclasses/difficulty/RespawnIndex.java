package io.github.rpgclasses.difficulty;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Último punto de reaparición conocido de cada jugador (cama o ancla), guardado con el mundo
 * (data/rites_and_paths_respawn.dat del Overworld). Va aparte para que la zona protegida de una base
 * siga valiendo aunque su dueño esté desconectado.
 * Los jugadores que nunca han tenido punto propio protegen el spawn del mundo.
 */
public final class RespawnIndex extends SavedData {
    private static final String NAME = "rites_and_paths_respawn";

    private record Point(String dimension, int x, int z) {}

    private final Map<UUID, Point> points = new HashMap<>();
    /** Jugadores conocidos sin cama ni ancla: reaparecen en el spawn del mundo. */
    private final Set<UUID> worldSpawnPlayers = new HashSet<>();

    public static RespawnIndex get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RespawnIndex::load, RespawnIndex::new, NAME);
    }

    /** Actualiza el punto de un jugador conectado. Barato: solo escribe si cambió. */
    public void update(ServerPlayer player) {
        UUID id = player.getUUID();
        BlockPos pos = player.getRespawnPosition();
        if (pos == null) {
            boolean changed = points.remove(id) != null;
            changed |= worldSpawnPlayers.add(id);
            if (changed) setDirty();
            return;
        }
        Point point = new Point(player.getRespawnDimension().location().toString(), pos.getX(), pos.getZ());
        boolean changed = !point.equals(points.put(id, point));
        changed |= worldSpawnPlayers.remove(id);
        if (changed) setDirty();
    }

    /** True si la posición está a menos de {@code radius} bloques (en horizontal) de algún punto de reaparición. */
    public boolean isProtected(ServerLevel level, Vec3 pos, double radius) {
        if (radius <= 0) return false;
        double radiusSq = radius * radius;
        String dimension = level.dimension().location().toString();
        for (Point p : points.values()) {
            if (!p.dimension.equals(dimension)) continue;
            double dx = p.x + 0.5 - pos.x;
            double dz = p.z + 0.5 - pos.z;
            if (dx * dx + dz * dz <= radiusSq) return true;
        }
        if (!worldSpawnPlayers.isEmpty() && level.dimension() == Level.OVERWORLD) {
            BlockPos spawn = level.getSharedSpawnPos();
            double dx = spawn.getX() + 0.5 - pos.x;
            double dz = spawn.getZ() + 0.5 - pos.z;
            return dx * dx + dz * dz <= radiusSq;
        }
        return false;
    }

    public static RespawnIndex load(CompoundTag tag) {
        RespawnIndex index = new RespawnIndex();
        for (Tag t : tag.getList("points", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (!c.contains("id")) continue;
            index.points.put(NbtUtils.loadUUID(c.get("id")), new Point(c.getString("dim"), c.getInt("x"), c.getInt("z")));
        }
        for (Tag t : tag.getList("worldSpawn", Tag.TAG_INT_ARRAY)) index.worldSpawnPlayers.add(NbtUtils.loadUUID(t));
        return index;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("v", 1);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Point> e : points.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.put("id", NbtUtils.createUUID(e.getKey()));
            c.putString("dim", e.getValue().dimension);
            c.putInt("x", e.getValue().x);
            c.putInt("z", e.getValue().z);
            list.add(c);
        }
        tag.put("points", list);
        ListTag world = new ListTag();
        for (UUID id : worldSpawnPlayers) world.add(NbtUtils.createUUID(id));
        tag.put("worldSpawn", world);
        return tag;
    }
}
