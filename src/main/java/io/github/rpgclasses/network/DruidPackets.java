package io.github.rpgclasses.network;

import io.github.rpgclasses.client.druid.DruidClient;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Paquetes del Druida (protocolo 23). Los de servidor a cliente delegan en {@link DruidClient} a través de
 * DistExecutor, así que el servidor dedicado nunca carga clases de cliente.
 */
public final class DruidPackets {
    private DruidPackets() {}

    static int register(SimpleChannel channel, int id) {
        channel.messageBuilder(Form.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Form::encode).decoder(Form::decode).consumerMainThread(Form::handle).add();
        channel.messageBuilder(State.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(State::encode).decoder(State::decode).consumerMainThread(State::handle).add();
        channel.messageBuilder(OpenBestiary.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenBestiary::encode).decoder(OpenBestiary::decode).consumerMainThread(OpenBestiary::handle).add();
        channel.messageBuilder(Action.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Action::encode).decoder(Action::decode).consumerMainThread(Action::handle).add();
        channel.messageBuilder(Glow.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Glow::encode).decoder(Glow::decode).consumerMainThread(Glow::handle).add();
        channel.messageBuilder(RitualChoice.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RitualChoice::encode).decoder(RitualChoice::decode).consumerMainThread(RitualChoice::handle).add();
        channel.messageBuilder(ChimeraReplace.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ChimeraReplace::encode).decoder(ChimeraReplace::decode).consumerMainThread(ChimeraReplace::handle).add();
        channel.messageBuilder(ChimeraFx.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ChimeraFx::encode).decoder(ChimeraFx::decode).consumerMainThread(ChimeraFx::handle).add();
        return id;
    }

    /** Servidor -> clientes que lo ven (y él): el jugador está transformado en {@code spec} (vacío = forma propia). */
    public record Form(int entityId, UUID player, CompoundTag spec) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeUUID(player);
            buf.writeNbt(spec);
        }

        public static Form decode(FriendlyByteBuf buf) {
            int id = buf.readVarInt();
            UUID player = buf.readUUID();
            CompoundTag tag = buf.readNbt();
            return new Form(id, player, tag == null ? new CompoundTag() : tag);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DruidClient.onForm(this));
        }
    }

    /**
     * Servidor -> el propio Druida: barras del HUD. {@code cast} progreso del lanzamiento (−1 si no), {@code castType}
     * forma que se lanza; {@code study} progreso del estudio (−1 si no) y {@code studyTarget} su criatura;
     * {@code flight} vuelo que queda (−1 si la forma no vuela); {@code formTicks} lo que queda de Forma Barata (−1 sin
     * límite); {@code abilityCooldown}/{@code abilityTotal} recarga de la habilidad de bestia (tics).
     */
    public record State(float cast, String castType, float study, int studyTarget, float flight, int formTicks,
                        int abilityCooldown, int abilityTotal) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeFloat(cast);
            buf.writeUtf(castType, 256);
            buf.writeFloat(study);
            buf.writeVarInt(studyTarget + 1);
            buf.writeFloat(flight);
            buf.writeVarInt(formTicks + 1);
            buf.writeVarInt(abilityCooldown);
            buf.writeVarInt(abilityTotal);
        }

        public static State decode(FriendlyByteBuf buf) {
            return new State(buf.readFloat(), buf.readUtf(256), buf.readFloat(), buf.readVarInt() - 1, buf.readFloat(),
                    buf.readVarInt() - 1, buf.readVarInt(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DruidClient.onState(this));
        }
    }

    /** Servidor -> cliente: abre el bestiario (tecla de Forma Salvaje). */
    public record OpenBestiary() {
        public void encode(FriendlyByteBuf buf) {}

        public static OpenBestiary decode(FriendlyByteBuf buf) {
            return new OpenBestiary();
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DruidClient.onOpenBestiary(this));
        }
    }

    /**
     * Cliente -> servidor. {@code kind}: 0 transformarse en {@code tag} (FormSpec), 1 volver a su forma, 2 habilidad de
     * bestia, 3 fijar especie para Rastrear ({@code tag.species}; vacía = todas), 4 elección del ritual del Escultor
     * ({@code value}: 0 conocimiento, 1-4 una parte), 5 quimera a sustituir ({@code value}, −1 = cancelar).
     */
    public record Action(int kind, CompoundTag tag, int value) {
        public static final int TRANSFORM = 0, RETURN = 1, BEAST_ABILITY = 2, TRACK = 3, RITUAL_CHOICE = 4, CHIMERA_REPLACE = 5;

        public void encode(FriendlyByteBuf buf) {
            buf.writeByte(kind);
            buf.writeNbt(tag);
            buf.writeVarInt(value + 1);
        }

        public static Action decode(FriendlyByteBuf buf) {
            int kind = buf.readByte();
            CompoundTag tag = buf.readNbt();
            return new Action(kind, tag == null ? new CompoundTag() : tag, buf.readVarInt() - 1);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) io.github.rpgclasses.druid.DruidNet.onAction(player, this);
        }
    }

    /** Servidor -> cliente: estas entidades brillan solo para él durante {@code ticks} (Rastrear Presas, Ecolocalización). */
    public record Glow(int[] ids, int ticks) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarIntArray(ids);
            buf.writeVarInt(ticks);
        }

        public static Glow decode(FriendlyByteBuf buf) {
            return new Glow(buf.readVarIntArray(512), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DruidClient.onGlow(this));
        }
    }

    /** Servidor -> cliente (Escultor): elige qué tomar de los restos de {@code species} (partes si ya la conoce). */
    public record RitualChoice(String species, boolean parts) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(species, 256);
            buf.writeBoolean(parts);
        }

        public static RitualChoice decode(FriendlyByteBuf buf) {
            return new RitualChoice(buf.readUtf(256), buf.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DruidClient.onRitualChoice(this));
        }
    }

    /** Servidor -> cliente (Escultor): ya tiene el máximo de quimeras; elige cuál sustituir (las ve en sus datos). */
    public record ChimeraReplace() {
        public void encode(FriendlyByteBuf buf) {}

        public static ChimeraReplace decode(FriendlyByteBuf buf) {
            return new ChimeraReplace();
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DruidClient.onChimeraReplace(this));
        }
    }

    /** Servidor -> clientes cerca del altar: una quimera se forma en {@code pos} durante {@code ticks}. */
    public record ChimeraFx(BlockPos pos, CompoundTag chimera, int ticks) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeNbt(chimera);
            buf.writeVarInt(ticks);
        }

        public static ChimeraFx decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            CompoundTag tag = buf.readNbt();
            return new ChimeraFx(pos, tag == null ? new CompoundTag() : tag, buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DruidClient.onChimeraFx(this));
        }
    }
}
