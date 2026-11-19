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
    private static final String PROTOCOL = "19";

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
        CHANNEL.messageBuilder(Packets.AirJump.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.AirJump::encode).decoder(Packets.AirJump::decode)
                .consumerMainThread(Packets.AirJump::handle).add();
        CHANNEL.messageBuilder(Packets.ArrowVelocity.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.ArrowVelocity::encode).decoder(Packets.ArrowVelocity::decode)
                .consumerMainThread(Packets.ArrowVelocity::handle).add();
        CHANNEL.messageBuilder(Packets.UseRaceAbility.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.UseRaceAbility::encode).decoder(Packets.UseRaceAbility::decode)
                .consumerMainThread(Packets.UseRaceAbility::handle).add();
        CHANNEL.messageBuilder(Packets.SyncAllies.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.SyncAllies::encode).decoder(Packets.SyncAllies::decode)
                .consumerMainThread(Packets.SyncAllies::handle).add();
        CHANNEL.messageBuilder(Packets.SetAlly.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.SetAlly::encode).decoder(Packets.SetAlly::decode)
                .consumerMainThread(Packets.SetAlly::handle).add();
        CHANNEL.messageBuilder(Packets.CoinFlip.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.CoinFlip::encode).decoder(Packets.CoinFlip::decode)
                .consumerMainThread(Packets.CoinFlip::handle).add();
        CHANNEL.messageBuilder(Packets.BondBookSync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.BondBookSync::encode).decoder(Packets.BondBookSync::decode)
                .consumerMainThread(Packets.BondBookSync::handle).add();
        CHANNEL.messageBuilder(Packets.BondBookAction.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.BondBookAction::encode).decoder(Packets.BondBookAction::decode)
                .consumerMainThread(Packets.BondBookAction::handle).add();
        CHANNEL.messageBuilder(Packets.MonkChargesSync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.MonkChargesSync::encode).decoder(Packets.MonkChargesSync::decode)
                .consumerMainThread(Packets.MonkChargesSync::handle).add();
        CHANNEL.messageBuilder(Packets.RaceMotion.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.RaceMotion::encode).decoder(Packets.RaceMotion::decode)
                .consumerMainThread(Packets.RaceMotion::handle).add();
        CHANNEL.messageBuilder(Packets.Shake.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.Shake::encode).decoder(Packets.Shake::decode)
                .consumerMainThread(Packets.Shake::handle).add();
        CHANNEL.messageBuilder(Packets.BeltVisual.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.BeltVisual::encode).decoder(Packets.BeltVisual::decode)
                .consumerMainThread(Packets.BeltVisual::handle).add();
        CHANNEL.messageBuilder(Packets.BeltInsert.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.BeltInsert::encode).decoder(Packets.BeltInsert::decode)
                .consumerMainThread(Packets.BeltInsert::handle).add();
        CHANNEL.messageBuilder(Packets.TimeSphere.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.TimeSphere::encode).decoder(Packets.TimeSphere::decode)
                .consumerMainThread(Packets.TimeSphere::handle).add();
        CHANNEL.messageBuilder(Packets.TimeFrozen.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.TimeFrozen::encode).decoder(Packets.TimeFrozen::decode)
                .consumerMainThread(Packets.TimeFrozen::handle).add();
        // Bardo (protocolo 16).
        CHANNEL.messageBuilder(Packets.BardNote.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.BardNote::encode).decoder(Packets.BardNote::decode)
                .consumerMainThread(Packets.BardNote::handle).add();
        CHANNEL.messageBuilder(Packets.BardSync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.BardSync::encode).decoder(Packets.BardSync::decode)
                .consumerMainThread(Packets.BardSync::handle).add();
        CHANNEL.messageBuilder(Packets.DanceSync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.DanceSync::encode).decoder(Packets.DanceSync::decode)
                .consumerMainThread(Packets.DanceSync::handle).add();
        CHANNEL.messageBuilder(Packets.ClassPlan.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.ClassPlan::encode).decoder(Packets.ClassPlan::decode)
                .consumerMainThread(Packets.ClassPlan::handle).add();
        // Clérigo (protocolo 18).
        CHANNEL.messageBuilder(Packets.ClericDowned.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.ClericDowned::encode).decoder(Packets.ClericDowned::decode)
                .consumerMainThread(Packets.ClericDowned::handle).add();
        CHANNEL.messageBuilder(Packets.SanctuarySync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.SanctuarySync::encode).decoder(Packets.SanctuarySync::decode)
                .consumerMainThread(Packets.SanctuarySync::handle).add();
        CHANNEL.messageBuilder(Packets.PrayerState.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.PrayerState::encode).decoder(Packets.PrayerState::decode)
                .consumerMainThread(Packets.PrayerState::handle).add();
        CHANNEL.messageBuilder(Packets.PrayerList.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.PrayerList::encode).decoder(Packets.PrayerList::decode)
                .consumerMainThread(Packets.PrayerList::handle).add();
        CHANNEL.messageBuilder(Packets.PrayerSelect.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.PrayerSelect::encode).decoder(Packets.PrayerSelect::decode)
                .consumerMainThread(Packets.PrayerSelect::handle).add();
        CHANNEL.messageBuilder(Packets.AbilityRelease.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.AbilityRelease::encode).decoder(Packets.AbilityRelease::decode)
                .consumerMainThread(Packets.AbilityRelease::handle).add();
    }

    public static void toPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void syncDefinitions(ServerPlayer player) {
        toPlayer(player, new SyncDefinitions(DefinitionStore.SERVER.rawClasses(), DefinitionStore.SERVER.rawSubclasses(),
                DefinitionStore.SERVER.rawAbilities(), io.github.rpgclasses.core.definition.RaceStore.SERVER.raw()));
    }

    /** Envía el progreso completo y, si no hay clase, pide al cliente que muestre la selección. */
    public static void syncPlayer(ServerPlayer player) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return;
        toPlayer(player, new SyncPlayerData(data.save()));
        // Sin raza, el cliente abre antes la selección de raza (lo decide él con los datos sincronizados).
        if (data.state.classId == null) toPlayer(player, new OpenClassSelection());
    }

    private Net() {}
}
