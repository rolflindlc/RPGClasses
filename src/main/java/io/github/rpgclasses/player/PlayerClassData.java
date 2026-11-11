package io.github.rpgclasses.player;

import io.github.rpgclasses.core.progression.ProgressState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Map;

/** Progreso de clase de un jugador y su serialización NBT. */
public final class PlayerClassData {
    /** Subir al cambiar el formato guardado, y añadir la migración en {@link #load}. */
    public static final int DATA_VERSION = 2;

    public final ProgressState state = new ProgressState();
    /** Vida con la que se guardó el jugador; -1 si no se conoce. Solo se usa al iniciar sesión. */
    public float savedHealth = -1f;
    /** Cooldowns de habilidades: id -> tiempo de juego (ticks) en que terminan. */
    public final Map<String, Long> cooldowns = new java.util.HashMap<>();
    /** Estado de mecánicas (postura del Monje...), por clave. */
    public final Map<String, Integer> counters = new java.util.HashMap<>();
    /** Valores de texto de la clase (p. ej. conjuro fijado en cada ranura de Conjuro Gratuito). */
    public final Map<String, String> texts = new java.util.HashMap<>();

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("v", DATA_VERSION);
        if (state.classId != null) tag.putString("class", state.classId);
        CompoundTag branches = new CompoundTag();
        for (Map.Entry<String, Integer> e : state.branchPoints.entrySet()) branches.putInt(e.getKey(), e.getValue());
        tag.put("branches", branches);
        CompoundTag perks = new CompoundTag();
        for (Map.Entry<String, String> e : state.perks.entrySet()) perks.putString(e.getKey(), e.getValue());
        tag.put("perks", perks);
        if (state.subclassId != null) tag.putString("subclass", state.subclassId);
        tag.putInt("subLevel", state.subclassLevel);
        tag.putLong("xpBase", state.xpPaidBase);
        tag.putLong("xpSub", state.xpPaidSubclass);
        tag.putBoolean("firstRespec", state.firstRespecUsed);
        tag.putBoolean("kit", state.kitGranted);
        CompoundTag cds = new CompoundTag();
        for (Map.Entry<String, Long> e : cooldowns.entrySet()) cds.putLong(e.getKey(), e.getValue());
        tag.put("cooldowns", cds);
        CompoundTag cnt = new CompoundTag();
        for (Map.Entry<String, Integer> e : counters.entrySet()) cnt.putInt(e.getKey(), e.getValue());
        tag.put("counters", cnt);
        CompoundTag txt = new CompoundTag();
        for (Map.Entry<String, String> e : texts.entrySet()) txt.putString(e.getKey(), e.getValue());
        tag.put("texts", txt);
        return tag;
    }

    public void load(CompoundTag tag) {
        // v1 -> v2: se añaden los perks (ausentes = ninguno elegido). Las ramas nuevas valen 0 si no están.
        state.classId = tag.contains("class", Tag.TAG_STRING) ? tag.getString("class") : null;
        state.branchPoints.clear();
        CompoundTag branches = tag.getCompound("branches");
        for (String key : branches.getAllKeys()) state.branchPoints.put(key, Math.max(0, branches.getInt(key)));
        state.perks.clear();
        CompoundTag perks = tag.getCompound("perks");
        for (String key : perks.getAllKeys()) state.perks.put(key, perks.getString(key));
        state.subclassId = tag.contains("subclass", Tag.TAG_STRING) ? tag.getString("subclass") : null;
        state.subclassLevel = Math.max(0, tag.getInt("subLevel"));
        state.xpPaidBase = Math.max(0, tag.getLong("xpBase"));
        state.xpPaidSubclass = Math.max(0, tag.getLong("xpSub"));
        state.firstRespecUsed = tag.getBoolean("firstRespec");
        state.kitGranted = tag.getBoolean("kit");
        cooldowns.clear();
        CompoundTag cds = tag.getCompound("cooldowns");
        for (String key : cds.getAllKeys()) cooldowns.put(key, cds.getLong(key));
        counters.clear();
        CompoundTag cnt = tag.getCompound("counters");
        for (String key : cnt.getAllKeys()) counters.put(key, cnt.getInt(key));
        texts.clear();
        CompoundTag txt = tag.getCompound("texts");
        for (String key : txt.getAllKeys()) texts.put(key, txt.getString(key));
    }

    /** Al reiniciar la clase se borra lo que era propio de ella (conjuros fijados, cargas). */
    public void clearClassBindings() {
        texts.clear();
        counters.keySet().removeIf(k -> k.startsWith("free_cast") || k.startsWith("death_gamble"));
    }
}
