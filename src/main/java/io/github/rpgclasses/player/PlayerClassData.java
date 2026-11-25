package io.github.rpgclasses.player;

import io.github.rpgclasses.core.progression.ProgressState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Map;

/** Progreso de clase de un jugador y su serialización NBT. */
public final class PlayerClassData {
    /** Subir al cambiar el formato guardado, y añadir la migración en {@link #load}. */
    public static final int DATA_VERSION = 4;

    public final ProgressState state = new ProgressState();
    /** Vida con la que se guardó el jugador; -1 si no se conoce. Solo se usa al iniciar sesión. */
    public float savedHealth = -1f;
    /** Cooldowns de habilidades: id -> tiempo de juego (ticks) en que terminan. */
    public final Map<String, Long> cooldowns = new java.util.HashMap<>();
    /** Estado de mecánicas (postura del Monje...), por clave. */
    public final Map<String, Integer> counters = new java.util.HashMap<>();
    /** Valores de texto de la clase (p. ej. conjuro fijado en cada ranura de Conjuro Gratuito). */
    public final Map<String, String> texts = new java.util.HashMap<>();
    /**
     * Druida (adenda del Druida): bestiario (conocimiento por especie, criaturas ya usadas, quimeras) y forma en curso.
     * Es del jugador, no de la clase: no se borra al reiniciarla. Ver {@code druid.Bestiary}.
     */
    public net.minecraft.nbt.CompoundTag druid = new net.minecraft.nbt.CompoundTag();
    /**
     * Cambiarrostros (Pícaro): rostros aprendidos con el catalejo y estudios a medias. Es del jugador, no de la clase:
     * no se borra al reiniciarla ni al morir. Solo lo mira el servidor. Ver {@code rogue.Faces}.
     */
    public net.minecraft.nbt.CompoundTag faces = new net.minecraft.nbt.CompoundTag();
    /** Solo en el cliente: descuento con signo del equilibrio de puntos que manda el servidor (&lt;0 = recargo; no se guarda). */
    public double catchUpDiscount;

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
        tag.putInt("points", state.pointBalance);
        net.minecraft.nbt.ListTag bosses = new net.minecraft.nbt.ListTag();
        for (String b : state.bossesCredited) bosses.add(net.minecraft.nbt.StringTag.valueOf(b));
        tag.put("bosses", bosses);
        net.minecraft.nbt.ListTag builds = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < state.builds.length; i++) {
            io.github.rpgclasses.core.progression.SavedBuild b = state.builds[i];
            if (b == null || b.classId() == null) continue;
            CompoundTag bt = new CompoundTag();
            bt.putInt("slot", i);
            bt.putString("class", b.classId());
            if (b.subclassId() != null) bt.putString("subclass", b.subclassId());
            bt.putInt("subLevel", b.subclassLevel());
            CompoundTag bb = new CompoundTag();
            b.branchPoints().forEach(bb::putInt);
            bt.put("branches", bb);
            CompoundTag bp = new CompoundTag();
            b.perks().forEach(bp::putString);
            bt.put("perks", bp);
            builds.add(bt);
        }
        tag.put("builds", builds);
        if (state.raceId != null) tag.putString("race", state.raceId);
        if (state.subraceId != null) tag.putString("subrace", state.subraceId);
        tag.putInt("raceRank", state.raceRank);
        net.minecraft.nbt.ListTag walls = new net.minecraft.nbt.ListTag();
        for (String w : state.humanWalls) walls.add(net.minecraft.nbt.StringTag.valueOf(w));
        tag.put("humanWalls", walls);
        net.minecraft.nbt.CompoundTag rites = new net.minecraft.nbt.CompoundTag();
        state.riteProgress.forEach(rites::putInt);
        tag.put("riteProgress", rites);
        CompoundTag cds = new CompoundTag();
        for (Map.Entry<String, Long> e : cooldowns.entrySet()) cds.putLong(e.getKey(), e.getValue());
        tag.put("cooldowns", cds);
        CompoundTag cnt = new CompoundTag();
        for (Map.Entry<String, Integer> e : counters.entrySet()) cnt.putInt(e.getKey(), e.getValue());
        tag.put("counters", cnt);
        CompoundTag txt = new CompoundTag();
        for (Map.Entry<String, String> e : texts.entrySet()) txt.putString(e.getKey(), e.getValue());
        tag.put("texts", txt);
        if (!druid.isEmpty()) tag.put("druid", druid.copy());
        if (!faces.isEmpty()) tag.put("faces", faces.copy());
        return tag;
    }

    /**
     * Lo que se manda al cliente: lo mismo que {@link #save()} sin las listas de criaturas ya estudiadas o usadas en un
     * ritual del Druida (solo las mira el servidor y pueden crecer mucho).
     */
    public CompoundTag saveForClient() {
        CompoundTag tag = save();
        if (tag.contains("druid")) {
            CompoundTag d = tag.getCompound("druid").copy();
            d.remove("used");
            tag.put("druid", d);
        }
        tag.remove("faces"); // rostros del Cambiarrostros: solo los usa el servidor
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
        state.subclassId = renamedSubclass(tag.contains("subclass", Tag.TAG_STRING) ? tag.getString("subclass") : null);
        state.subclassLevel = Math.max(0, tag.getInt("subLevel"));
        state.xpPaidBase = Math.max(0, tag.getLong("xpBase"));
        state.xpPaidSubclass = Math.max(0, tag.getLong("xpSub"));
        state.firstRespecUsed = tag.getBoolean("firstRespec");
        state.kitGranted = tag.getBoolean("kit");
        // v3 -> v4: puntos de clase (saldo), jefes que ya dieron su punto y builds guardadas. Ausentes = 0 / vacío: los
        // puntos ya gastados se conservan tal cual.
        state.pointBalance = Math.max(0, tag.getInt("points"));
        state.bossesCredited.clear();
        net.minecraft.nbt.ListTag bosses = tag.getList("bosses", Tag.TAG_STRING);
        for (int i = 0; i < bosses.size(); i++) state.bossesCredited.add(bosses.getString(i));
        java.util.Arrays.fill(state.builds, null);
        net.minecraft.nbt.ListTag builds = tag.getList("builds", Tag.TAG_COMPOUND);
        for (int i = 0; i < builds.size(); i++) {
            CompoundTag bt = builds.getCompound(i);
            int slot = bt.getInt("slot");
            if (slot < 0 || slot >= state.builds.length || !bt.contains("class", Tag.TAG_STRING)) continue;
            java.util.Map<String, Integer> pts = new java.util.LinkedHashMap<>();
            CompoundTag bb = bt.getCompound("branches");
            for (String k : bb.getAllKeys()) pts.put(k, Math.max(0, bb.getInt(k)));
            java.util.Map<String, String> pk = new java.util.LinkedHashMap<>();
            CompoundTag bp = bt.getCompound("perks");
            for (String k : bp.getAllKeys()) pk.put(k, bp.getString(k));
            state.builds[slot] = new io.github.rpgclasses.core.progression.SavedBuild(bt.getString("class"), pts, pk,
                    renamedSubclass(bt.contains("subclass", Tag.TAG_STRING) ? bt.getString("subclass") : null), bt.getInt("subLevel"));
        }
        // v2 -> v3: razas (ausente = sin raza; se pedirá elegirla al entrar).
        state.raceId = tag.contains("race", Tag.TAG_STRING) ? tag.getString("race") : null;
        state.subraceId = tag.contains("subrace", Tag.TAG_STRING) ? tag.getString("subrace") : null;
        state.raceRank = Math.max(0, Math.min(3, tag.getInt("raceRank")));
        state.riteProgress.clear();
        net.minecraft.nbt.CompoundTag rites = tag.getCompound("riteProgress");
        for (String k : rites.getAllKeys()) state.riteProgress.put(k, rites.getInt(k));
        state.humanWalls.clear();
        net.minecraft.nbt.ListTag walls = tag.getList("humanWalls", Tag.TAG_STRING);
        for (int i = 0; i < walls.size(); i++) state.humanWalls.add(walls.getString(i));
        cooldowns.clear();
        CompoundTag cds = tag.getCompound("cooldowns");
        for (String key : cds.getAllKeys()) cooldowns.put(key, cds.getLong(key));
        counters.clear();
        CompoundTag cnt = tag.getCompound("counters");
        for (String key : cnt.getAllKeys()) counters.put(key, cnt.getInt(key));
        texts.clear();
        CompoundTag txt = tag.getCompound("texts");
        for (String key : txt.getAllKeys()) texts.put(key, txt.getString(key));
        druid = tag.getCompound("druid").copy();
        faces = tag.getCompound("faces").copy();
        catchUpDiscount = tag.contains("catchUp") ? tag.getDouble("catchUp") : 0;
    }

    /** Subclases sustituidas por otras: quien tenía la antigua pasa a la nueva con el mismo nivel. */
    private static String renamedSubclass(String id) {
        // Rev. 2.20: el Caminante de Sombras (Paso de Sombra) se sustituye por el Cambiarrostros.
        if ("rites_and_paths:shadow_walker".equals(id)) return "rites_and_paths:face_changer";
        return id;
    }

    /** Al reiniciar la clase se borra lo que era propio de ella (conjuros fijados, cargas). */
    public void clearClassBindings() {
        texts.clear();
        counters.keySet().removeIf(k -> k.startsWith("free_cast") || k.startsWith("death_gamble"));
    }
}
