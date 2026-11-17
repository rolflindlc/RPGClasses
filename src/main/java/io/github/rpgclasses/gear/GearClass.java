package io.github.rpgclasses.gear;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Clases con armadura propia. La armadura la puede llevar cualquiera; sus efectos de set completo, solo esta clase. */
public enum GearClass {
    BARBARIAN("barbarian"),
    MONK("monk");

    public final String id;
    /** Id de la clase en el datapack ("rites_and_paths:barbarian"). */
    public final String classId;
    /** Piezas que no desactivan las pasivas desarmadas de esta clase (ni borran las cargas del Monje). */
    public final TagKey<Item> armorTag;

    GearClass(String id) {
        this.id = id;
        this.classId = RpgClasses.MODID + ":" + id;
        this.armorTag = ItemTags.create(RpgClasses.id("class_armor/" + id));
    }

    public boolean is(ServerPlayer player) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        return data != null && classId.equals(data.state.classId);
    }

    /** Clase con armadura propia del jugador, o null. */
    public static GearClass of(ServerPlayer player) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null || data.state.classId == null) return null;
        for (GearClass c : values()) if (c.classId.equals(data.state.classId)) return c;
        return null;
    }
}
