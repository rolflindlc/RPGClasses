package io.github.rpgclasses.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Paquetes del Cambiarrostros (protocolo 24). Los de servidor a cliente delegan en
 * {@code client.rogue.DisguiseClient} a través de DistExecutor, así que el servidor dedicado nunca carga clases de cliente.
 */
public final class DisguisePackets {
    private DisguisePackets() {}

    static int register(SimpleChannel channel, int id) {
        channel.messageBuilder(Sync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Sync::encode).decoder(Sync::decode).consumerMainThread(Sync::handle).add();
        channel.messageBuilder(FaceList.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FaceList::encode).decoder(FaceList::decode).consumerMainThread(FaceList::handle).add();
        channel.messageBuilder(FaceSelect.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FaceSelect::encode).decoder(FaceSelect::decode).consumerMainThread(FaceSelect::handle).add();
        return id;
    }

    /** Servidor -> todos: el jugador lleva el rostro {@code face} (vacío = sin disfraz). */
    public record Sync(UUID player, CompoundTag face) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(player);
            buf.writeNbt(face);
        }

        public static Sync decode(FriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            CompoundTag tag = buf.readNbt();
            return new Sync(id, tag == null ? new CompoundTag() : tag);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> io.github.rpgclasses.client.rogue.DisguiseClient.onSync(this));
        }
    }

    /** Servidor -> cliente: rostros aprendidos, para elegir uno. {@code value} es el nombre (jugador) o el id del mob. */
    public record FaceList(List<Entry> entries) {
        public record Entry(String key, boolean player, String value) {}

        private static final int MAX = 256;

        public void encode(FriendlyByteBuf buf) {
            int n = Math.min(entries.size(), MAX);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                Entry e = entries.get(i);
                buf.writeUtf(e.key(), 128);
                buf.writeBoolean(e.player());
                buf.writeUtf(e.value(), 128);
            }
        }

        public static FaceList decode(FriendlyByteBuf buf) {
            int n = Math.min(MAX, buf.readVarInt());
            List<Entry> list = new ArrayList<>();
            for (int i = 0; i < n; i++) list.add(new Entry(buf.readUtf(128), buf.readBoolean(), buf.readUtf(128)));
            return new FaceList(list);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> io.github.rpgclasses.client.rogue.DisguiseClient.onFaceList(this));
        }
    }

    /** Cliente -> servidor: ponerse el rostro {@code key}. */
    public record FaceSelect(String key) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(key, 128);
        }

        public static FaceSelect decode(FriendlyByteBuf buf) {
            return new FaceSelect(buf.readUtf(128));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) io.github.rpgclasses.rogue.FaceChanger.select(sender, key);
        }
    }
}
