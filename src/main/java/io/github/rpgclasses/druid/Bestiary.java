package io.github.rpgclasses.druid;

import io.github.rpgclasses.player.PlayerClassData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Bestiario del Druida, guardado en {@link PlayerClassData#druid} (del jugador: sobrevive a la muerte y a reiniciar la
 * clase). Lo leen el servidor y, sin la sublista {@code used}, el cliente (pantalla del bestiario).
 * <pre>
 * druid: {
 *   know: { "minecraft:wolf": 2, ... },      puntos de conocimiento por especie
 *   used: { study: [uuid...], ritual: [uuid...] }  criaturas ya estudiadas / usadas en un ritual (solo servidor)
 *   chimeras: [ {head, torso, legs, instinct}, ... ]
 *   track: "minecraft:wolf"                  especie fijada para Rastrear Presas
 *   form: { spec: {...}, health: 17.5 }       forma en curso (para recuperar la vida si el servidor se cae)
 * }
 * </pre>
 */
public final class Bestiary {
    /** Tope de criaturas recordadas por lista: las más antiguas se olvidan (solo evita estudiar dos veces seguidas). */
    private static final int MAX_USED = 1024;
    public static final String STUDY = "study", RITUAL = "ritual";

    private Bestiary() {}

    private static CompoundTag root(PlayerClassData data) {
        return data.druid;
    }

    // ------------------------------------------------------------------ conocimiento

    public static int points(PlayerClassData data, String species) {
        return data == null ? 0 : root(data).getCompound("know").getInt(species);
    }

    /** Suma puntos (con tope) y devuelve el total nuevo. */
    public static int addPoints(PlayerClassData data, String species, int amount, int cap) {
        CompoundTag know = root(data).getCompound("know");
        int total = Math.max(0, Math.min(cap, know.getInt(species) + amount));
        know.putInt(species, total);
        root(data).put("know", know);
        return total;
    }

    /** Especies con algún punto, en el orden en que se aprendieron. */
    public static List<String> species(PlayerClassData data) {
        if (data == null) return List.of();
        CompoundTag know = root(data).getCompound("know");
        List<String> out = new ArrayList<>();
        for (String k : know.getAllKeys()) if (know.getInt(k) > 0) out.add(k);
        Collections.sort(out);
        return out;
    }

    // ------------------------------------------------------------------ criaturas ya usadas

    public static boolean used(PlayerClassData data, String list, UUID creature) {
        ListTag tag = root(data).getCompound("used").getList(list, Tag.TAG_INT_ARRAY);
        for (Tag t : tag) {
            if (creature.equals(NbtUtils.loadUUID(t))) return true;
        }
        return false;
    }

    public static void markUsed(PlayerClassData data, String list, UUID creature) {
        CompoundTag used = root(data).getCompound("used");
        ListTag tag = used.getList(list, Tag.TAG_INT_ARRAY);
        tag.add(NbtUtils.createUUID(creature));
        while (tag.size() > MAX_USED) tag.remove(0);
        used.put(list, tag);
        root(data).put("used", used);
    }

    // ------------------------------------------------------------------ quimeras

    public static List<FormSpec.Chimera> chimeras(PlayerClassData data) {
        if (data == null) return List.of();
        List<FormSpec.Chimera> out = new ArrayList<>();
        ListTag list = root(data).getList("chimeras", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            FormSpec.Chimera c = FormSpec.Chimera.load(list.getCompound(i));
            if (c != null) out.add(c);
        }
        return out;
    }

    /** Añade la quimera, o sustituye la de {@code replace} (si es un índice válido). */
    public static void putChimera(PlayerClassData data, FormSpec.Chimera chimera, int replace) {
        ListTag list = root(data).getList("chimeras", Tag.TAG_COMPOUND);
        if (replace >= 0 && replace < list.size()) list.set(replace, chimera.save());
        else list.add(chimera.save());
        root(data).put("chimeras", list);
    }

    // ------------------------------------------------------------------ Rastrear Presas

    public static String tracked(PlayerClassData data) {
        return data == null ? "" : root(data).getString("track");
    }

    public static void setTracked(PlayerClassData data, String species) {
        if (species == null || species.isEmpty()) root(data).remove("track");
        else root(data).putString("track", species);
    }

    // ------------------------------------------------------------------ forma en curso (recuperación)

    public static void saveForm(PlayerClassData data, FormSpec spec, float health) {
        CompoundTag form = new CompoundTag();
        form.put("spec", spec.save());
        form.putFloat("health", health);
        root(data).put("form", form);
    }

    public static boolean hasSavedForm(PlayerClassData data) {
        return data != null && root(data).contains("form");
    }

    public static float savedHealth(PlayerClassData data) {
        return root(data).getCompound("form").getFloat("health");
    }

    public static void clearForm(PlayerClassData data) {
        root(data).remove("form");
    }

    /** Especies que salen en el bestiario: las de los puntos y las de las quimeras (sin repetir). */
    public static Set<String> allSpecies(PlayerClassData data) {
        Set<String> out = new LinkedHashSet<>(species(data));
        for (FormSpec.Chimera c : chimeras(data)) for (int i = 0; i < 4; i++) out.add(c.part(i));
        return out;
    }
}
