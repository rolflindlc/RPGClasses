package io.github.rpgclasses.player;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Adjunta {@link PlayerClassData} a cada jugador y lo guarda con él. */
public final class PlayerClassDataProvider implements ICapabilitySerializable<CompoundTag> {
    public static final Capability<PlayerClassData> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});

    private final PlayerClassData data = new PlayerClassData();
    private final LazyOptional<PlayerClassData> optional = LazyOptional.of(() -> data);

    public static void registerCapability(RegisterCapabilitiesEvent event) {
        event.register(PlayerClassData.class);
    }

    /** @return los datos del jugador, o null si no los tiene (no debería ocurrir con jugadores reales). */
    @Nullable
    public static PlayerClassData get(Player player) {
        return player.getCapability(CAPABILITY).resolve().orElse(null);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return CAPABILITY.orEmpty(cap, optional);
    }

    @Override
    public CompoundTag serializeNBT() {
        return data.save();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        data.load(tag);
    }
}
