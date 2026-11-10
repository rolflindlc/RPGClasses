package io.github.rpgclasses.network;

import io.github.rpgclasses.client.ClientHandlers;
import io.github.rpgclasses.content.RangerAbilities;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.ProgressionService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Todos los paquetes del mod. Los de servidor a cliente delegan en ClientHandlers a través de
 * DistExecutor, de modo que el servidor dedicado nunca carga clases de cliente.
 */
public final class Packets {
    private static final int MAX_JSON = 1 << 18;
    private static final int MAX_ID = 256;

    /** Servidor -> cliente: JSON en bruto de clases y subclases (el cliente lo interpreta igual que el servidor). */
    public record SyncDefinitions(Map<String, String> classes, Map<String, String> subclasses, Map<String, String> abilities) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeMap(classes, (b, k) -> b.writeUtf(k, MAX_ID), (b, v) -> b.writeUtf(v, MAX_JSON));
            buf.writeMap(subclasses, (b, k) -> b.writeUtf(k, MAX_ID), (b, v) -> b.writeUtf(v, MAX_JSON));
            buf.writeMap(abilities, (b, k) -> b.writeUtf(k, MAX_ID), (b, v) -> b.writeUtf(v, MAX_JSON));
        }

        public static SyncDefinitions decode(FriendlyByteBuf buf) {
            return new SyncDefinitions(
                    buf.readMap(b -> b.readUtf(MAX_ID), b -> b.readUtf(MAX_JSON)),
                    buf.readMap(b -> b.readUtf(MAX_ID), b -> b.readUtf(MAX_JSON)),
                    buf.readMap(b -> b.readUtf(MAX_ID), b -> b.readUtf(MAX_JSON)));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandlers.onDefinitions(this));
        }
    }

    /** Servidor -> cliente: progreso completo del propio jugador. */
    public record SyncPlayerData(CompoundTag data) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeNbt(data);
        }

        public static SyncPlayerData decode(FriendlyByteBuf buf) {
            CompoundTag tag = buf.readNbt();
            return new SyncPlayerData(tag == null ? new CompoundTag() : tag);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandlers.onPlayerData(this));
        }
    }

    /** Servidor -> cliente: el jugador no tiene clase; hay que mostrar la selección. */
    public record OpenClassSelection() {
        public void encode(FriendlyByteBuf buf) {}

        public static OpenClassSelection decode(FriendlyByteBuf buf) {
            return new OpenClassSelection();
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientHandlers::onOpenSelection);
        }
    }

    /** Cliente -> servidor: una intención de progresión. El servidor la valida entera. */
    public record ClassAction(Action action, String arg) {
        public enum Action { CHOOSE_CLASS, SPEND_BRANCH, CHOOSE_SUBCLASS, SPEND_SUBCLASS, RESPEC_CLASS, RESPEC_SUBCLASS }

        public void encode(FriendlyByteBuf buf) {
            buf.writeEnum(action);
            buf.writeUtf(arg, MAX_ID);
        }

        public static ClassAction decode(FriendlyByteBuf buf) {
            return new ClassAction(buf.readEnum(Action.class), buf.readUtf(MAX_ID));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) ProgressionService.performAndReport(sender, action, arg);
        }
    }

    /** Cliente -> servidor: tecla de habilidad pulsada (ranura 0, 1, 2). */
    public record UseAbility(int slot) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeByte(slot);
        }

        public static UseAbility decode(FriendlyByteBuf buf) {
            return new UseAbility(buf.readByte());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) AbilityService.use(sender, slot);
        }
    }

    /** Cliente -> servidor: el jugador ha hecho un salto aéreo (ya ejecutado en su cliente). */
    public record AirJump() {
        public void encode(FriendlyByteBuf buf) {}

        public static AirJump decode(FriendlyByteBuf buf) {
            return new AirJump();
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) RangerAbilities.onAirJump(sender);
        }
    }

    private Packets() {}
}
