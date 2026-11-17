package io.github.rpgclasses.server;

import io.github.rpgclasses.core.ally.AllyBook;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Aliados de todos los jugadores, guardados con el mundo (data/rites_and_paths_allies.dat del Overworld).
 * Va aparte de los datos del jugador para poder consultarlos aunque el otro esté desconectado.
 */
public final class AllyStore extends SavedData {
    private static final String NAME = "rites_and_paths_allies";

    public final AllyBook book = new AllyBook();

    public static AllyStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(AllyStore::load, AllyStore::new, NAME);
    }

    public static AllyStore load(CompoundTag tag) {
        AllyStore store = new AllyStore();
        CompoundTag players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            UUID owner;
            try {
                owner = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ListTag list = players.getList(key, Tag.TAG_INT_ARRAY);
            for (Tag t : list) store.book.put(owner, NbtUtils.loadUUID(t));
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("v", 1);
        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, Set<UUID>> e : book.view().entrySet()) {
            ListTag list = new ListTag();
            for (UUID id : e.getValue()) list.add(NbtUtils.createUUID(id));
            players.put(e.getKey().toString(), list);
        }
        tag.put("players", players);
        return tag;
    }
}
