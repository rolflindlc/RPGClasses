package io.github.ironrituals.ritual.active;

import io.github.ironrituals.IronRituals;
import io.github.ironrituals.block.RitualCoreBlock;
import io.github.ironrituals.ritual.RitualEffect;
import io.github.ironrituals.ritual.RitualEffects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Bucle de rituales persistentes (plan, 2.5): temporizadores, pausas por Supresión y fin de efecto.
 * El tiempo solo avanza con el propietario conectado.
 */
@Mod.EventBusSubscriber(modid = IronRituals.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RitualTicker {
    public static final ResourceLocation SUPPRESSION = IronRituals.id("arcane_suppression");

    private RitualTicker() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        RitualSavedData data = RitualSavedData.get(server);
        if (data.all().isEmpty()) return;
        long now = server.getTickCount();

        List<ActiveRitual> ended = new ArrayList<>();
        for (ActiveRitual r : new ArrayList<>(data.all())) {
            if (!data.contains(r.id)) continue; // terminado por otro efecto en este mismo tick
            ServerLevel level = server.getLevel(r.dimension());
            if (level == null) {
                ended.add(r);
                continue;
            }
            // Si el chunk del núcleo está cargado y el núcleo ya no existe, el efecto termina.
            if (level.isLoaded(r.corePos()) && !(level.getBlockState(r.corePos()).getBlock() instanceof RitualCoreBlock)) {
                ended.add(r);
                continue;
            }
            if (now % 20 == 0) r.paused = !r.ritual.equals(SUPPRESSION) && isSuppressed(data, r.dimension(), r.center());

            RitualEffect effect = RitualEffects.get(r.ritual);
            boolean ownerOnline = server.getPlayerList().getPlayer(r.owner) != null;
            boolean needsOwner = effect == null || effect.timerNeedsOwner();
            if ((ownerOnline || !needsOwner) && !r.paused) r.remaining--;

            if (effect != null && !r.paused && now % Math.max(1, effect.tickInterval()) == 0) {
                effect.tick(r, level);
            }
            if (r.remaining <= 0) ended.add(r);
        }
        for (ActiveRitual r : ended) end(server, r);
        if (now % 100 == 0) data.setDirty();
    }

    /** Termina un ritual persistente y avisa a su efecto (una sola vez aunque se llame dos veces). */
    public static void end(MinecraftServer server, ActiveRitual r) {
        if (RitualSavedData.get(server).remove(r.id) == null) return;
        RitualEffect effect = RitualEffects.get(r.ritual);
        ServerLevel level = server.getLevel(r.dimension());
        if (effect != null && level != null) effect.onEnd(r, level);
    }

    /** Termina todos los rituales anclados a un núcleo (núcleo destruido). */
    public static void endAtCore(MinecraftServer server, ResourceKey<Level> dim, net.minecraft.core.BlockPos core) {
        for (ActiveRitual r : RitualSavedData.get(server).atCore(dim, core)) end(server, r);
    }

    /** ¿Hay un Campo de Supresión activo en esa posición? (sección 2.6) */
    public static boolean isSuppressed(MinecraftServer server, ResourceKey<Level> dim, Vec3 pos) {
        return isSuppressed(RitualSavedData.get(server), dim, pos);
    }

    private static boolean isSuppressed(RitualSavedData data, ResourceKey<Level> dim, Vec3 pos) {
        return !data.zonesAt(dim, pos, SUPPRESSION).isEmpty();
    }
}
