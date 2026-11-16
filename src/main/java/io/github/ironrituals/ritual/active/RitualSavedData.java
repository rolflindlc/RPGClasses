package io.github.ironrituals.ritual.active;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Registro global de rituales persistentes (plan, 2.5), guardado con el Overworld. */
public final class RitualSavedData extends SavedData {
    private static final String NAME = "ironrituals_active";
    private final Map<UUID, ActiveRitual> rituals = new LinkedHashMap<>();

    public static RitualSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RitualSavedData::load, RitualSavedData::new, NAME);
    }

    public Collection<ActiveRitual> all() {
        return Collections.unmodifiableCollection(rituals.values());
    }

    public boolean contains(UUID id) {
        return rituals.containsKey(id);
    }

    public void add(ActiveRitual r) {
        rituals.put(r.id, r);
        setDirty();
    }

    @Nullable
    public ActiveRitual remove(UUID id) {
        ActiveRitual r = rituals.remove(id);
        if (r != null) setDirty();
        return r;
    }

    /** Rituales anclados a un núcleo. */
    public List<ActiveRitual> atCore(ResourceKey<Level> dim, BlockPos core) {
        List<ActiveRitual> out = new ArrayList<>();
        for (ActiveRitual r : rituals.values()) if (r.dimension().equals(dim) && r.corePos().equals(core)) out.add(r);
        return out;
    }

    /** Rituales cuya zona contiene la posición, opcionalmente filtrados por tipo. */
    public List<ActiveRitual> zonesAt(ResourceKey<Level> dim, Vec3 pos, @Nullable ResourceLocation type) {
        return matching(r -> (type == null || r.ritual.equals(type)) && r.contains(dim, pos));
    }

    public List<ActiveRitual> matching(Predicate<ActiveRitual> filter) {
        List<ActiveRitual> out = new ArrayList<>();
        for (ActiveRitual r : rituals.values()) if (filter.test(r)) out.add(r);
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (ActiveRitual r : rituals.values()) list.add(r.save());
        tag.put("Rituals", list);
        return tag;
    }

    private static RitualSavedData load(CompoundTag tag) {
        RitualSavedData d = new RitualSavedData();
        for (Tag t : tag.getList("Rituals", Tag.TAG_COMPOUND)) {
            try {
                ActiveRitual r = ActiveRitual.load((CompoundTag) t);
                d.rituals.put(r.id, r);
            } catch (RuntimeException ignored) {
                // Entrada corrupta: se descarta en vez de romper la carga del mundo.
            }
        }
        return d;
    }
}
