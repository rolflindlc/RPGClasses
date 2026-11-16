package io.github.ironrituals.ritual.active;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ritual persistente registrado en {@link RitualSavedData} (plan, 2.5). No depende de que el chunk del núcleo esté
 * cargado. {@code data} es libre para el efecto.
 */
public final class ActiveRitual {
    public final UUID id;
    public final ResourceLocation ritual;
    public final int level;
    public final UUID owner;
    public final String ownerName;
    public final GlobalPos core;
    public final int radius;
    public final List<String> catalysts;
    public final CompoundTag data;
    public long remaining;
    public long total;
    public boolean paused;

    public ActiveRitual(UUID id, ResourceLocation ritual, int level, UUID owner, String ownerName, GlobalPos core, int radius,
                        List<String> catalysts, long duration, CompoundTag data) {
        this.id = id;
        this.ritual = ritual;
        this.level = level;
        this.owner = owner;
        this.ownerName = ownerName;
        this.core = core;
        this.radius = radius;
        this.catalysts = List.copyOf(catalysts);
        this.remaining = duration;
        this.total = duration;
        this.data = data;
    }

    public boolean hasCatalyst(String id) {
        return catalysts.contains(id);
    }

    public ResourceKey<Level> dimension() {
        return core.dimension();
    }

    public BlockPos corePos() {
        return core.pos();
    }

    public Vec3 center() {
        return Vec3.atCenterOf(core.pos());
    }

    /** ¿Está {@code pos} dentro de la zona esférica del ritual (en su misma dimensión)? */
    public boolean contains(ResourceKey<Level> dim, Vec3 pos) {
        return radius > 0 && dim.equals(core.dimension()) && pos.distanceToSqr(center()) <= (double) radius * radius;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("Id", id);
        t.putString("Ritual", ritual.toString());
        t.putInt("Level", level);
        t.putUUID("Owner", owner);
        t.putString("OwnerName", ownerName);
        t.putString("Dim", core.dimension().location().toString());
        t.put("Pos", NbtUtils.writeBlockPos(core.pos()));
        t.putInt("Radius", radius);
        ListTag cats = new ListTag();
        for (String c : catalysts) cats.add(StringTag.valueOf(c));
        t.put("Catalysts", cats);
        t.putLong("Remaining", remaining);
        t.putLong("Total", total);
        t.putBoolean("Paused", paused);
        t.put("Data", data);
        return t;
    }

    public static ActiveRitual load(CompoundTag t) {
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(t.getString("Dim")));
        List<String> cats = new ArrayList<>();
        for (Tag c : t.getList("Catalysts", Tag.TAG_STRING)) cats.add(c.getAsString());
        ActiveRitual a = new ActiveRitual(t.getUUID("Id"), ResourceLocation.tryParse(t.getString("Ritual")), t.getInt("Level"),
                t.getUUID("Owner"), t.getString("OwnerName"), GlobalPos.of(dim, NbtUtils.readBlockPos(t.getCompound("Pos"))),
                t.getInt("Radius"), cats, t.getLong("Total"), t.getCompound("Data"));
        a.remaining = t.getLong("Remaining");
        a.paused = t.getBoolean("Paused");
        return a;
    }
}
