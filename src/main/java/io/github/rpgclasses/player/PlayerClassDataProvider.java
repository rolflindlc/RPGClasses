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
    private final Player player;

    public PlayerClassDataProvider(Player player) {
        this.player = player;
    }
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
        CompoundTag tag = data.save();
        // Los bonus de vida máxima son transitorios: al cargar, vanilla recorta la vida a 20.
        // Se guarda aquí la vida real para restaurarla tras recalcular las estadísticas al entrar.
        tag.putFloat("health", player.getHealth());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        data.load(tag);
        data.savedHealth = tag.contains("health") ? tag.getFloat("health") : -1f;
    }
}
