package io.github.rpgclasses.player;

import io.github.rpgclasses.core.progression.ProgressState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Map;

/** Progreso de clase de un jugador y su serialización NBT. */
public final class PlayerClassData {
    /** Subir al cambiar el formato guardado, y añadir la migración en {@link #load}. */
    public static final int DATA_VERSION = 1;

    public final ProgressState state = new ProgressState();

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("v", DATA_VERSION);
        if (state.classId != null) tag.putString("class", state.classId);
        CompoundTag branches = new CompoundTag();
        for (Map.Entry<String, Integer> e : state.branchPoints.entrySet()) branches.putInt(e.getKey(), e.getValue());
        tag.put("branches", branches);
        if (state.subclassId != null) tag.putString("subclass", state.subclassId);
        tag.putInt("subLevel", state.subclassLevel);
        tag.putLong("xpBase", state.xpPaidBase);
        tag.putLong("xpSub", state.xpPaidSubclass);
        tag.putBoolean("firstRespec", state.firstRespecUsed);
        tag.putBoolean("kit", state.kitGranted);
        return tag;
    }

    public void load(CompoundTag tag) {
        // Aquí irán las migraciones cuando DATA_VERSION pase de 1: leer tag.getInt("v") y convertir.
        state.classId = tag.contains("class", Tag.TAG_STRING) ? tag.getString("class") : null;
        state.branchPoints.clear();
        CompoundTag branches = tag.getCompound("branches");
        for (String key : branches.getAllKeys()) state.branchPoints.put(key, Math.max(0, branches.getInt(key)));
        state.subclassId = tag.contains("subclass", Tag.TAG_STRING) ? tag.getString("subclass") : null;
        state.subclassLevel = Math.max(0, tag.getInt("subLevel"));
        state.xpPaidBase = Math.max(0, tag.getLong("xpBase"));
        state.xpPaidSubclass = Math.max(0, tag.getLong("xpSub"));
        state.firstRespecUsed = tag.getBoolean("firstRespec");
        state.kitGranted = tag.getBoolean("kit");
    }
}
