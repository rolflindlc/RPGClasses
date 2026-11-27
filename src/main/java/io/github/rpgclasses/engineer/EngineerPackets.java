package io.github.rpgclasses.engineer;

import io.github.rpgclasses.network.Net;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Paquetes del Ingeniero (protocolo 25). Los de servidor a cliente delegan en {@code client.engineer.EngineerClient} a
 * través de DistExecutor (el servidor dedicado no carga clases de cliente). Todo lo que pide el cliente se valida aquí.
 */
public final class EngineerPackets {
    private EngineerPackets() {
    }

    public static int register(SimpleChannel channel, int id) {
        channel.messageBuilder(Sync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Sync::encode).decoder(Sync::decode).consumerMainThread(Sync::handle).add();
        channel.messageBuilder(Bar.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Bar::encode).decoder(Bar::decode).consumerMainThread(Bar::handle).add();
        channel.messageBuilder(Result.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Result::encode).decoder(Result::decode).consumerMainThread(Result::handle).add();
        channel.messageBuilder(Marks.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Marks::encode).decoder(Marks::decode).consumerMainThread(Marks::handle).add();
        channel.messageBuilder(Press.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Press::encode).decoder(Press::decode).consumerMainThread(Press::handle).add();
        channel.messageBuilder(Gadget.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Gadget::encode).decoder(Gadget::decode).consumerMainThread(Gadget::handle).add();
        channel.messageBuilder(Throw.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Throw::encode).decoder(Throw::decode).consumerMainThread(Throw::handle).add();
        return id;
    }

    // ------------------------------------------------------------------ envío

    /** Estado para el HUD y los factores de NTGL del cliente; solo se manda si ha cambiado. {@code s == null}: ya no es Ingeniero. */
    static void sync(ServerPlayer p, Engineer.State s) {
        if (s == null) {
            Net.toPlayer(p, Sync.NONE);
            return;
        }
        boolean artificer = Engineer.sub(p, Engineer.ARTIFICER);
        int max = artificer ? Artificer.maxCharges(p) : 0;
        int charges = artificer ? Artificer.available(p, s) : 0;
        int nextCharge = artificer ? (Artificer.nextChargeTicks(p, s) + 19) / 20 : 0;
        int streakMax = Engineer.has(p, Engineer.STREAK) ? (int) Engineer.mech(p, Engineer.STREAK, "max_stacks", 6) : 0;
        int loaded = s.specialLeft > 0 ? s.loadedSpecial : -1;
        int next = Engineer.has(p, Engineer.IMPROVISED) ? s.nextSpecial : -1;
        long now = p.level().getGameTime();
        int perfect = now < s.perfectUntil ? (int) ((s.perfectUntil - now + 19) / 20) : 0;
        int flags = Engineer.has(p, Engineer.THROWING) ? Sync.THROWING : 0;
        Sync msg = new Sync(true, s.factors.clone(), s.streak, streakMax, loaded, s.specialLeft, next, charges, max, nextCharge, perfect, flags);
        String key = msg.key();
        if (key.equals(s.synced)) return;
        s.synced = key;
        Net.toPlayer(p, msg);
    }

    static void bar(ServerPlayer p, int total, float ps, float pe, float gs, float ge) {
        Net.toPlayer(p, new Bar(total, ps, pe, gs, ge));
    }

    static void result(ServerPlayer p, int zone) {
        Net.toPlayer(p, new Result(zone));
    }

    static void marks(ServerPlayer p, Collection<Integer> ids, int ticks) {
        Net.toPlayer(p, new Marks(new ArrayList<>(ids), ticks));
    }

    // ------------------------------------------------------------------ mensajes

    /**
     * Servidor -> cliente. {@code factors}: cadencia, dispersión, recarga, desenfunde. Racha y su tope (0 = sin la rama),
     * bala especial cargada y cuántas quedan, siguiente especial (-1 = ninguna), cargas del Artificiero, segundos para la
     * próxima, segundos de la recarga perfecta y banderas ({@link #THROWING}).
     */
    public record Sync(boolean engineer, float[] factors, int streak, int streakMax, int loaded, int loadedLeft, int next,
                       int charges, int maxCharges, int nextCharge, int perfect, int flags) {
        /** Bit de {@code flags}: tiene el perk Arma Arrojadiza (el cliente intercepta el clic izquierdo). */
        public static final int THROWING = 1;
        static final Sync NONE = new Sync(false, new float[] {1, 1, 1, 0}, 0, 0, -1, 0, -1, 0, 0, 0, 0, 0);

        String key() {
            return engineer + ":" + factors[0] + "," + factors[1] + "," + factors[2] + "," + factors[3] + ":" + streak + "/" + streakMax
                    + ":" + loaded + "x" + loadedLeft + ":" + next + ":" + charges + "/" + maxCharges + ":" + nextCharge + ":" + perfect + ":" + flags;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(engineer);
            for (int i = 0; i < 4; i++) buf.writeFloat(factors[i]);
            buf.writeVarInt(streak);
            buf.writeVarInt(streakMax);
            buf.writeVarInt(loaded + 1);
            buf.writeVarInt(loadedLeft);
            buf.writeVarInt(next + 1);
            buf.writeVarInt(charges);
            buf.writeVarInt(maxCharges);
            buf.writeVarInt(nextCharge);
            buf.writeVarInt(perfect);
            buf.writeVarInt(flags);
        }

        public static Sync decode(FriendlyByteBuf buf) {
            boolean engineer = buf.readBoolean();
            float[] f = new float[4];
            for (int i = 0; i < 4; i++) f[i] = buf.readFloat();
            return new Sync(engineer, f, buf.readVarInt(), buf.readVarInt(), buf.readVarInt() - 1, buf.readVarInt(), buf.readVarInt() - 1,
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> io.github.rpgclasses.client.engineer.EngineerClient.onSync(this));
        }
    }

    /** Servidor -> cliente: empieza una recarga activa de {@code total} tics (0 = quitar la barra) y sus zonas (0..1). */
    public record Bar(int total, float perfectStart, float perfectEnd, float goodStart, float goodEnd) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(total);
            buf.writeFloat(perfectStart);
            buf.writeFloat(perfectEnd);
            buf.writeFloat(goodStart);
            buf.writeFloat(goodEnd);
        }

        public static Bar decode(FriendlyByteBuf buf) {
            return new Bar(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> io.github.rpgclasses.client.engineer.EngineerClient.onBar(this));
        }
    }

    /** Servidor -> cliente: resultado de la recarga activa (0 perfecta, 1 buena, 2 encasquillada). */
    public record Result(int zone) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(zone);
        }

        public static Result decode(FriendlyByteBuf buf) {
            return new Result(buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> io.github.rpgclasses.client.engineer.EngineerClient.onResult(this));
        }
    }

    /** Servidor -> cliente: entidades marcadas por Ojo Muerto (brillan) durante {@code ticks}. */
    public record Marks(List<Integer> ids, int ticks) {
        private static final int MAX = 32;

        public void encode(FriendlyByteBuf buf) {
            int n = Math.min(MAX, ids.size());
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) buf.writeVarInt(ids.get(i));
            buf.writeVarInt(ticks);
        }

        public static Marks decode(FriendlyByteBuf buf) {
            int n = Math.min(MAX, buf.readVarInt());
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < n; i++) ids.add(buf.readVarInt());
            return new Marks(ids, buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> io.github.rpgclasses.client.engineer.EngineerClient.onMarks(this));
        }
    }

    /** Cliente -> servidor: pulsó recargar con la barra en pantalla; {@code fraction} es donde veía el cursor. */
    public record Press(float fraction) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeFloat(fraction);
        }

        public static Press decode(FriendlyByteBuf buf) {
            return new Press(buf.readFloat());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null && Float.isFinite(fraction)) ActiveReload.press(sender, fraction);
        }
    }

    /** Cliente -> servidor: tecla de artilugio del Artificiero (0 torreta, 1 mina, 2 trinchera). */
    public record Gadget(int kind) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(kind);
        }

        public static Gadget decode(FriendlyByteBuf buf) {
            return new Gadget(buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null && kind >= 0 && kind <= 2) Artificer.use(sender, kind);
        }
    }

    /** Cliente -> servidor: clic izquierdo con el arma vacía (perk Arma Arrojadiza). */
    public record Throw() {
        public void encode(FriendlyByteBuf buf) {
        }

        public static Throw decode(FriendlyByteBuf buf) {
            return new Throw();
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) Holster.throwGun(sender);
        }
    }
}
