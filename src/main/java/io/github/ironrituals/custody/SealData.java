package io.github.ironrituals.custody;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bloques sellados con un Sello de Custodia en una dimensión (plan, 3.F). Cada entrada guarda el propietario y,
 * para los cofres dobles, la otra mitad (un solo sello cubre las dos).
 */
public final class SealData extends SavedData {
    private static final String NAME = "ironrituals_seals";

    public record Seal(UUID owner, String ownerName, @Nullable BlockPos partner) {}

    private final Map<Long, Seal> seals = new HashMap<>();

    public static SealData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SealData::load, SealData::new, NAME);
    }

    @Nullable
    public Seal get(BlockPos pos) {
        return seals.get(pos.asLong());
    }

    public void put(BlockPos pos, Seal seal) {
        seals.put(pos.asLong(), seal);
        setDirty();
    }

    /** Quita el sello de la posición y de su otra mitad. Devuelve el sello quitado. */
    @Nullable
    public Seal remove(BlockPos pos) {
        Seal s = seals.remove(pos.asLong());
        if (s != null) {
            if (s.partner() != null) seals.remove(s.partner().asLong());
            setDirty();
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Seal> e : seals.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Pos", e.getKey());
            t.putUUID("Owner", e.getValue().owner());
            t.putString("OwnerName", e.getValue().ownerName());
            if (e.getValue().partner() != null) t.putLong("Partner", e.getValue().partner().asLong());
            list.add(t);
        }
        tag.put("Seals", list);
        return tag;
    }

    private static SealData load(CompoundTag tag) {
        SealData d = new SealData();
        for (Tag raw : tag.getList("Seals", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            if (!t.hasUUID("Owner")) continue;
            BlockPos partner = t.contains("Partner") ? BlockPos.of(t.getLong("Partner")) : null;
            d.seals.put(t.getLong("Pos"), new Seal(t.getUUID("Owner"), t.getString("OwnerName"), partner));
        }
        return d;
    }
}
