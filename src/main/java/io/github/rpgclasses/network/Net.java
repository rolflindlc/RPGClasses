package io.github.rpgclasses.network;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.network.Packets.ClassAction;
import io.github.rpgclasses.network.Packets.OpenClassSelection;
import io.github.rpgclasses.network.Packets.SyncDefinitions;
import io.github.rpgclasses.network.Packets.SyncPlayerData;
import io.github.rpgclasses.network.Packets.UseAbility;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
    /** Cliente y servidor con versiones de protocolo distintas no se conectan. Subir al cambiar cualquier paquete. */
    private static final String PROTOCOL = "3";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            RpgClasses.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SyncDefinitions.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncDefinitions::encode).decoder(SyncDefinitions::decode)
                .consumerMainThread(SyncDefinitions::handle).add();
        CHANNEL.messageBuilder(SyncPlayerData.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncPlayerData::encode).decoder(SyncPlayerData::decode)
                .consumerMainThread(SyncPlayerData::handle).add();
        CHANNEL.messageBuilder(OpenClassSelection.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenClassSelection::encode).decoder(OpenClassSelection::decode)
                .consumerMainThread(OpenClassSelection::handle).add();
        CHANNEL.messageBuilder(ClassAction.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClassAction::encode).decoder(ClassAction::decode)
                .consumerMainThread(ClassAction::handle).add();
        CHANNEL.messageBuilder(UseAbility.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UseAbility::encode).decoder(UseAbility::decode)
                .consumerMainThread(UseAbility::handle).add();
    }

    public static void toPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void syncDefinitions(ServerPlayer player) {
        toPlayer(player, new SyncDefinitions(DefinitionStore.SERVER.rawClasses(), DefinitionStore.SERVER.rawSubclasses(),
                DefinitionStore.SERVER.rawAbilities()));
    }

    /** Envía el progreso completo y, si no hay clase, pide al cliente que muestre la selección. */
    public static void syncPlayer(ServerPlayer player) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return;
        toPlayer(player, new SyncPlayerData(data.save()));
        if (data.state.classId == null) toPlayer(player, new OpenClassSelection());
    }

    private Net() {}
}
