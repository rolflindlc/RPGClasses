package io.github.rpgclasses.client;

import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.network.Packets.SyncDefinitions;
import io.github.rpgclasses.network.Packets.SyncPlayerData;
import io.github.rpgclasses.player.PlayerClassData;
import net.minecraft.client.Minecraft;

/** Solo cliente. Se invoca únicamente a través de DistExecutor desde los paquetes. */
public final class ClientHandlers {
    /** Copia local del progreso del jugador, para la interfaz. La verdad está siempre en el servidor. */
    public static final PlayerClassData LOCAL_DATA = new PlayerClassData();

    private static boolean needsSelection;
    /** true cuando ya ha llegado el progreso del servidor (para saber si falta elegir raza). */
    private static boolean dataReceived;

    public static boolean dataReceived() {
        return dataReceived;
    }

    /** true mientras el servidor diga que el jugador no tiene clase. */
    public static boolean needsSelection() {
        return needsSelection;
    }

    public static void onDefinitions(SyncDefinitions msg) {
        DefinitionStore.CLIENT.load(msg.classes(), msg.subclasses(), msg.abilities());
        io.github.rpgclasses.core.definition.RaceStore.CLIENT.load(msg.races());
        // Tras un /reload, una pantalla abierta se reconstruye con los datos nuevos.
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ClassScreen screen) mc.setScreen(new ClassScreen(screen.isSelecting()));
    }

    public static void onArrowVelocity(io.github.rpgclasses.network.Packets.ArrowVelocity msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        net.minecraft.world.entity.Entity e = mc.level.getEntity(msg.entityId());
        if (e instanceof net.minecraft.world.entity.projectile.AbstractArrow) e.setDeltaMovement(msg.x(), msg.y(), msg.z());
    }

    /** Último contenido del Libro de Vínculos recibido del servidor. */
    public static io.github.rpgclasses.network.Packets.BondBookSync BOND_BOOK;

    /** Maestro de Bestias: abre el Libro de Vínculos, o refresca la pantalla si ya está abierta. */
    public static void onBondBookSync(io.github.rpgclasses.network.Packets.BondBookSync msg) {
        BOND_BOOK = msg;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BondBookScreen screen) screen.onDataChanged();
        else mc.setScreen(new BondBookScreen());
    }

    public static void onPlayerData(SyncPlayerData msg) {
        String raceBefore = LOCAL_DATA.state.raceId + "/" + LOCAL_DATA.state.raceRank;
        LOCAL_DATA.load(msg.data());
        dataReceived = true;
        if (!raceBefore.equals(LOCAL_DATA.state.raceId + "/" + LOCAL_DATA.state.raceRank)) {
            refreshIronsSpellWheel();
            RaceBodyClient.refreshBodySoon();
        }
        if (Minecraft.getInstance().screen instanceof RaceScreen rs) rs.onDataChanged();
        if (LOCAL_DATA.state.classId != null) {
            needsSelection = false;
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ClassScreen screen && screen.isSelecting()) mc.setScreen(null);
        }
        if (Minecraft.getInstance().screen instanceof ClassScreen screen) screen.onDataChanged();
    }

    /** Conjuros raciales del Infernal: Iron's solo reconstruye la rueda al cambiar el equipo, así que se le pide aquí. */
    private static void refreshIronsSpellWheel() {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("irons_spellbooks")) return;
        try {
            Class.forName("io.redspace.ironsspellbooks.player.ClientMagicData").getMethod("updateSpellSelectionManager").invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            io.github.rpgclasses.RpgClasses.LOGGER.debug("[rpgclasses] No se pudo refrescar la rueda de conjuros de Iron's", e);
        }
    }

    /** Aliados propios y quién nos tiene como aliado (copia local para la pantalla de aliados). */
    public static java.util.List<io.github.rpgclasses.network.Packets.SyncAllies.Entry> ALLIES = java.util.List.of();
    public static java.util.List<io.github.rpgclasses.network.Packets.SyncAllies.Entry> ALLIED_BY = java.util.List.of();

    public static void onSyncAllies(io.github.rpgclasses.network.Packets.SyncAllies msg) {
        ALLIES = java.util.List.copyOf(msg.allies());
        ALLIED_BY = java.util.List.copyOf(msg.alliedBy());
        if (Minecraft.getInstance().screen instanceof AllyScreen screen) screen.onDataChanged();
    }

    public static boolean isAlly(java.util.UUID id) {
        for (var e : ALLIES) if (e.id().equals(id)) return true;
        return false;
    }

    public static boolean isAlliedBy(java.util.UUID id) {
        for (var e : ALLIED_BY) if (e.id().equals(id)) return true;
        return false;
    }

    /** El servidor avisa de que el jugador no tiene clase; ClientSetup abre la pantalla en el siguiente tick. */
    public static void onOpenSelection() {
        needsSelection = true;
    }

    /** Al desconectar: nada de un servidor debe quedar para el siguiente. */
    public static void reset() {
        needsSelection = false;
        dataReceived = false;
        ALLIES = java.util.List.of();
        ALLIED_BY = java.util.List.of();
        CoinFlipFx.reset();
        MonkChargeFx.reset();
        LOCAL_DATA.load(new net.minecraft.nbt.CompoundTag());
    }

    private ClientHandlers() {}
}
