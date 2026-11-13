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
    public record SyncDefinitions(Map<String, String> classes, Map<String, String> subclasses, Map<String, String> abilities,
                                  Map<String, String> races) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeMap(classes, (b, k) -> b.writeUtf(k, MAX_ID), (b, v) -> b.writeUtf(v, MAX_JSON));
            buf.writeMap(subclasses, (b, k) -> b.writeUtf(k, MAX_ID), (b, v) -> b.writeUtf(v, MAX_JSON));
            buf.writeMap(abilities, (b, k) -> b.writeUtf(k, MAX_ID), (b, v) -> b.writeUtf(v, MAX_JSON));
            buf.writeMap(races, (b, k) -> b.writeUtf(k, MAX_ID), (b, v) -> b.writeUtf(v, MAX_JSON));
        }

        public static SyncDefinitions decode(FriendlyByteBuf buf) {
            return new SyncDefinitions(
                    buf.readMap(b -> b.readUtf(MAX_ID), b -> b.readUtf(MAX_JSON)),
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
        public enum Action { CHOOSE_CLASS, SPEND_BRANCH, CHOOSE_SUBCLASS, SPEND_SUBCLASS, RESPEC_CLASS, RESPEC_SUBCLASS, CHOOSE_PERK, CHOOSE_RACE, CHOOSE_WALL }

        /** Argumento de CHOOSE_PERK: "rama/perk". */
        public static String perkArg(String branchId, String perkId) {
            return branchId + "/" + perkId;
        }

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
            if (sender != null && !io.github.rpgclasses.content.DeathGamble.flipping(sender)) AbilityService.use(sender, slot);
        }
    }

    /** Cliente -> servidor: tecla de habilidad racial (0 o 1; +2 si se pulsa agachado). */
    public record UseRaceAbility(int slot) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeByte(slot);
        }

        public static UseRaceAbility decode(FriendlyByteBuf buf) {
            return new UseRaceAbility(buf.readByte());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null && !io.github.rpgclasses.content.DeathGamble.flipping(sender)) {
                io.github.rpgclasses.content.race.RaceAbilities.use(sender, slot);
            }
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

    /** Servidor -> cliente: velocidad real de una flecha rápida (el paquete vanilla la recorta a 3,9 por eje). */
    public record ArrowVelocity(int entityId, double x, double y, double z) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
        }

        public static ArrowVelocity decode(FriendlyByteBuf buf) {
            return new ArrowVelocity(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandlers.onArrowVelocity(this));
        }
    }

    /** Servidor -> cliente: Maestro de Bestias acaba de domesticar un animal; ¿vincularse con él? */
    public record BondPrompt(int entityId, String name) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeUtf(name, 256);
        }

        public static BondPrompt decode(FriendlyByteBuf buf) {
            return new BondPrompt(buf.readVarInt(), buf.readUtf(256));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandlers.onBondPrompt(this));
        }
    }

    /** Cliente -> servidor: respuesta al vínculo. */
    public record BondAnswer(int entityId, boolean accept) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeBoolean(accept);
        }

        public static BondAnswer decode(FriendlyByteBuf buf) {
            return new BondAnswer(buf.readVarInt(), buf.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) io.github.rpgclasses.content.RangerSubclasses.answerBond(player, entityId, accept);
        }
    }

    /** Servidor -> cliente: aliados del jugador y quién le tiene como aliado. */
    public record SyncAllies(java.util.List<Entry> allies, java.util.List<Entry> alliedBy) {
        public record Entry(java.util.UUID id, String name) {}

        private static void write(FriendlyByteBuf buf, java.util.List<Entry> list) {
            buf.writeVarInt(list.size());
            for (Entry e : list) {
                buf.writeUUID(e.id());
                buf.writeUtf(e.name(), 64);
            }
        }

        private static java.util.List<Entry> read(FriendlyByteBuf buf) {
            int n = Math.min(buf.readVarInt(), 4096);
            java.util.List<Entry> out = new java.util.ArrayList<>(n);
            for (int i = 0; i < n; i++) out.add(new Entry(buf.readUUID(), buf.readUtf(64)));
            return out;
        }

        public void encode(FriendlyByteBuf buf) {
            write(buf, allies);
            write(buf, alliedBy);
        }

        public static SyncAllies decode(FriendlyByteBuf buf) {
            return new SyncAllies(read(buf), read(buf));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandlers.onSyncAllies(this));
        }
    }

    /** Cliente -> servidor: marcar o desmarcar a un jugador como aliado (pantalla de aliados). */
    public record SetAlly(java.util.UUID target, boolean ally) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(target);
            buf.writeBoolean(ally);
        }

        public static SetAlly decode(FriendlyByteBuf buf) {
            return new SetAlly(buf.readUUID(), buf.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            io.github.rpgclasses.server.Allies.set(player, target,
                    io.github.rpgclasses.server.Allies.nameOf(player.server, target), ally);
        }
    }

    /** Servidor -> clientes que ven al jugador: Apuesta con la Muerte, moneda en el aire con el resultado ya decidido. */
    public record CoinFlip(int entityId, boolean win, int flipTicks, int showTicks) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeBoolean(win);
            buf.writeVarInt(flipTicks);
            buf.writeVarInt(showTicks);
        }

        public static CoinFlip decode(FriendlyByteBuf buf) {
            return new CoinFlip(buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> io.github.rpgclasses.client.CoinFlipFx.start(this));
        }
    }

    /** Cargas de postura de un Monje (para él: barra sobre la vida; para todos: efecto en los puños). */
    public record MonkChargesSync(int entityId, int stance, int charges, int max, int threshold, boolean armed) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeByte(stance);
            buf.writeVarInt(charges);
            buf.writeVarInt(max);
            buf.writeVarInt(threshold);
            buf.writeBoolean(armed);
        }

        public static MonkChargesSync decode(FriendlyByteBuf buf) {
            return new MonkChargesSync(buf.readVarInt(), buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> io.github.rpgclasses.client.MonkChargeFx.receive(this));
        }
    }

    /** Una mascota en el Libro de Vínculos: vinculada (con estadísticas si está cargada) o cercana sin vincular. */
    public record BookEntry(java.util.UUID id, String name, String type, boolean loaded, float health, float maxHealth,
                            double attack, double speed, boolean boosted) {
        void write(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUtf(name, 64);
            buf.writeUtf(type, 128);
            buf.writeBoolean(loaded);
            buf.writeFloat(health);
            buf.writeFloat(maxHealth);
            buf.writeDouble(attack);
            buf.writeDouble(speed);
            buf.writeBoolean(boosted);
        }

        static BookEntry read(FriendlyByteBuf buf) {
            return new BookEntry(buf.readUUID(), buf.readUtf(64), buf.readUtf(128), buf.readBoolean(), buf.readFloat(),
                    buf.readFloat(), buf.readDouble(), buf.readDouble(), buf.readBoolean());
        }
    }

    /** Un punto de destino guardado en el Libro de Vínculos. */
    public record BookPoint(boolean set, String dim, int x, int y, int z) {}

    /** Servidor -> cliente: contenido del Libro de Vínculos (abre la pantalla al llegar). */
    public record BondBookSync(java.util.List<BookEntry> bonded, java.util.List<BookEntry> nearby,
                               java.util.List<BookPoint> points, int selected) {
        public void encode(FriendlyByteBuf buf) {
            writeEntries(buf, bonded);
            writeEntries(buf, nearby);
            buf.writeVarInt(points.size());
            for (BookPoint p : points) {
                buf.writeBoolean(p.set());
                buf.writeUtf(p.dim(), 128);
                buf.writeVarInt(p.x());
                buf.writeVarInt(p.y());
                buf.writeVarInt(p.z());
            }
            buf.writeVarInt(selected);
        }

        private static void writeEntries(FriendlyByteBuf buf, java.util.List<BookEntry> list) {
            buf.writeVarInt(list.size());
            for (BookEntry e : list) e.write(buf);
        }

        private static java.util.List<BookEntry> readEntries(FriendlyByteBuf buf) {
            int n = Math.min(buf.readVarInt(), 64);
            java.util.List<BookEntry> out = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) out.add(BookEntry.read(buf));
            return out;
        }

        public static BondBookSync decode(FriendlyByteBuf buf) {
            java.util.List<BookEntry> bonded = readEntries(buf);
            java.util.List<BookEntry> nearby = readEntries(buf);
            int n = Math.min(buf.readVarInt(), 8);
            java.util.List<BookPoint> points = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                points.add(new BookPoint(buf.readBoolean(), buf.readUtf(128), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
            }
            return new BondBookSync(bonded, nearby, points, buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandlers.onBondBookSync(this));
        }
    }

    /** Cliente -> servidor: acción del Libro de Vínculos (vincular, romper, guardar punto, elegir destino). */
    public record BondBookAction(int kind, java.util.UUID pet, int slot) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(kind);
            buf.writeUUID(pet);
            buf.writeVarInt(slot);
        }

        public static BondBookAction decode(FriendlyByteBuf buf) {
            return new BondBookAction(buf.readVarInt(), buf.readUUID(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) io.github.rpgclasses.content.BondBook.act(player, kind, pet, slot);
        }
    }
}
