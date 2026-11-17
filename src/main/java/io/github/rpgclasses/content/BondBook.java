package io.github.rpgclasses.content;

import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Libro de Vínculos del Maestro de Bestias: lista sus mascotas vinculadas (con imagen, nombre y estadísticas), deja
 * vincular las cercanas o romper vínculos, y elige a dónde se teletransportan las mascotas cuando la Salvaguarda las salva.
 * <p>
 * Fuente de verdad: los datos persistentes del Ranger ({@link #KEY}). El vínculo sigue marcado también en la mascota
 * ({@code RangerSubclasses.TAG_BONDED}), que es lo que usa el resto del sistema.
 */
public final class BondBook {
    /** Máximo de mascotas vinculadas a la vez. */
    public static final int MAX_BONDS = 8;
    /** Radio en el que el libro ofrece mascotas sin vincular o permite romper un vínculo. */
    public static final int RANGE = 16;
    /** Puntos de destino que se pueden guardar. */
    public static final int SLOTS = 3;
    /** Destino "punto de aparición" (el de siempre). */
    public static final int SPAWN = -1;

    public static final int ACT_BIND = 0, ACT_UNBIND = 1, ACT_SAVE = 2, ACT_SELECT = 3;

    private static final String KEY = "rites_and_paths.bond_book";

    public record Bond(UUID id, String name) {}
    public record Point(ResourceKey<Level> dim, BlockPos pos) {}

    /** Listener de eventos (la muerte de una mascota vinculada). */
    public BondBook() {}

    // ------------------------------------------------------------------ almacenamiento

    private static CompoundTag root(ServerPlayer p) {
        CompoundTag data = p.getPersistentData();
        if (!data.contains(KEY)) data.put(KEY, new CompoundTag());
        return data.getCompound(KEY);
    }

    public static List<Bond> bonds(ServerPlayer p) {
        ListTag list = root(p).getList("bonds", Tag.TAG_COMPOUND);
        List<Bond> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            out.add(new Bond(t.getUUID("id"), t.getString("name")));
        }
        return out;
    }

    private static void saveBonds(ServerPlayer p, List<Bond> bonds) {
        ListTag list = new ListTag();
        for (Bond b : bonds) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", b.id());
            t.putString("name", b.name());
            list.add(t);
        }
        root(p).put("bonds", list);
    }

    /** Añade o actualiza una mascota en el libro (sin tope: el tope se comprueba al vincular). */
    public static void ensureBond(ServerPlayer p, UUID id, String name) {
        if (isBonded(p, id)) return; // no reordena: la lista se muestra en el orden en que se vincularon
        List<Bond> bonds = new ArrayList<>(bonds(p));
        bonds.add(new Bond(id, trim(name)));
        saveBonds(p, bonds);
    }

    public static void removeBond(ServerPlayer p, UUID id) {
        List<Bond> bonds = new ArrayList<>(bonds(p));
        if (bonds.removeIf(b -> b.id().equals(id))) saveBonds(p, bonds);
    }

    public static boolean isBonded(ServerPlayer p, UUID id) {
        return bonds(p).stream().anyMatch(b -> b.id().equals(id));
    }

    /** Puntos guardados: siempre {@link #SLOTS} entradas; null = vacío. */
    public static Point[] points(ServerPlayer p) {
        ListTag list = root(p).getList("points", Tag.TAG_COMPOUND);
        Point[] out = new Point[SLOTS];
        for (int i = 0; i < SLOTS && i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.contains("dim")) continue;
            ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    new net.minecraft.resources.ResourceLocation(t.getString("dim")));
            out[i] = new Point(dim, new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z")));
        }
        return out;
    }

    private static void savePoints(ServerPlayer p, Point[] points) {
        ListTag list = new ListTag();
        for (Point pt : points) {
            CompoundTag t = new CompoundTag();
            if (pt != null) {
                t.putString("dim", pt.dim().location().toString());
                t.putInt("x", pt.pos().getX());
                t.putInt("y", pt.pos().getY());
                t.putInt("z", pt.pos().getZ());
            }
            list.add(t);
        }
        root(p).put("points", list);
    }

    /** Índice del punto elegido, o {@link #SPAWN}. */
    public static int selected(ServerPlayer p) {
        CompoundTag r = root(p);
        return r.contains("selected") ? r.getInt("selected") : SPAWN;
    }

    /** Destino de la Salvaguarda para este Ranger: el punto elegido, o null para el punto de aparición. */
    public static Point destination(ServerPlayer p) {
        int sel = selected(p);
        if (sel < 0 || sel >= SLOTS) return null;
        return points(p)[sel];
    }

    // ------------------------------------------------------------------ vista

    /** Vista para el cliente: vinculadas, cercanas sin vincular, puntos y destino elegido. */
    public static Packets.BondBookSync build(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        List<Packets.BookEntry> bonded = new ArrayList<>();
        for (Bond b : bonds(p)) {
            LivingEntity pet = findPet(server, b.id());
            bonded.add(pet != null ? entryOf(p, pet, b.name(), true) : new Packets.BookEntry(b.id(), b.name(), "", false, 0, 0, 0, 0, false));
        }
        List<Packets.BookEntry> nearby = new ArrayList<>();
        if (bonded.size() < MAX_BONDS) {
            for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(RANGE),
                    e -> RangerSubclasses.ownedBy(e, p) && !RangerSubclasses.isPetOf(e, p))) {
                if (nearby.size() >= 4) break;
                nearby.add(entryOf(p, e, e.getName().getString(), false));
            }
        }
        Point[] pts = points(p);
        List<Packets.BookPoint> points = new ArrayList<>();
        for (Point pt : pts) {
            points.add(pt == null ? new Packets.BookPoint(false, "", 0, 0, 0)
                    : new Packets.BookPoint(true, pt.dim().location().toString(), pt.pos().getX(), pt.pos().getY(), pt.pos().getZ()));
        }
        return new Packets.BondBookSync(bonded, nearby, points, selected(p));
    }

    private static Packets.BookEntry entryOf(ServerPlayer p, LivingEntity pet, String name, boolean bonded) {
        String type = String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(pet.getType()));
        return new Packets.BookEntry(pet.getUUID(), trim(bonded ? pet.getName().getString() : name), type, true,
                pet.getHealth(), pet.getMaxHealth(), value(pet, Attributes.ATTACK_DAMAGE), value(pet, Attributes.MOVEMENT_SPEED),
                bonded && RangerSubclasses.isBoosted(p.getUUID(), pet.getUUID()));
    }

    /** Los nombres viajan en paquetes con tope de 64 caracteres. */
    static String trim(String s) {
        return s.length() <= 64 ? s : s.substring(0, 64);
    }

    private static double value(LivingEntity e, Attribute a) {
        return e.getAttribute(a) == null ? 0 : e.getAttributeValue(a);
    }

    /** Mascota cargada en cualquier dimensión, o null. */
    private static LivingEntity findPet(MinecraftServer server, UUID id) {
        if (server == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(id);
            if (e instanceof LivingEntity living) return living;
        }
        return null;
    }

    /** Envía el libro al jugador; el cliente abre la pantalla al recibirlo. */
    public static void open(ServerPlayer p) {
        Net.toPlayer(p, build(p));
    }

    // ------------------------------------------------------------------ acciones

    public static void act(ServerPlayer p, int kind, UUID pet, int slot) {
        switch (kind) {
            case ACT_BIND -> {
                LivingEntity living = findPet(p.getServer(), pet);
                if (living != null && living.distanceToSqr(p) <= RANGE * RANGE) {
                    RangerSubclasses.bond(p, living);
                } else {
                    p.displayClientMessage(Component.translatable("msg.rites_and_paths.bond.need_near", RANGE), true);
                }
            }
            case ACT_UNBIND -> {
                LivingEntity living = findPet(p.getServer(), pet);
                if (living != null && RangerSubclasses.isPetOf(living, p)) RangerSubclasses.unbond(p, living);
                else if (living == null) p.displayClientMessage(Component.translatable("msg.rites_and_paths.bond.need_near", RANGE), true);
            }
            case ACT_SAVE -> {
                if (slot < 0 || slot >= SLOTS) return;
                Point[] pts = points(p);
                pts[slot] = new Point(p.level().dimension(), p.blockPosition());
                savePoints(p, pts);
                p.displayClientMessage(Component.translatable("msg.rites_and_paths.bond.saved", slot + 1), true);
            }
            case ACT_SELECT -> {
                if (slot == SPAWN || (slot >= 0 && slot < SLOTS && points(p)[slot] != null)) root(p).putInt("selected", slot);
            }
            default -> { return; }
        }
        open(p);
    }

    // ------------------------------------------------------------------ kit

    /** Valor de la marca de kit del libro: lo entrega la subclase, no la clase. */
    private static final String KIT_OWNER = io.github.rpgclasses.core.Ids.NS + ":beast_master";

    private static boolean isKitBook(net.minecraft.world.item.ItemStack stack) {
        return stack.is(io.github.rpgclasses.registry.ModBlocks.BOND_BOOK.get()) && stack.hasTag()
                && KIT_OWNER.equals(stack.getTag().getString(io.github.rpgclasses.server.KitService.KIT_TAG));
    }

    /**
     * Kit del Maestro de Bestias: al tener la subclase recibe una vez un Libro de Vínculos marcado como kit; al perderla
     * (reinicio de subclase o de clase) se le retira. Cubre también a quien ya era Maestro de Bestias antes del libro.
     */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        if (p.tickCount % 20 != 7 || !p.isAlive()) return;
        CompoundTag r = root(p);
        boolean master = RangerSubclasses.isBeastMaster(p);
        if (master && !r.getBoolean("kit_given")) {
            net.minecraft.world.item.ItemStack book = new net.minecraft.world.item.ItemStack(io.github.rpgclasses.registry.ModBlocks.BOND_BOOK.get());
            book.getOrCreateTag().putString(io.github.rpgclasses.server.KitService.KIT_TAG, KIT_OWNER);
            p.getInventory().placeItemBackInInventory(book);
            r.putBoolean("kit_given", true);
            p.displayClientMessage(Component.translatable("msg.rites_and_paths.bond.kit"), true);
        } else if (!master && r.getBoolean("kit_given")) {
            p.getInventory().clearOrCountMatchingItems(BondBook::isKitBook, -1, p.inventoryMenu.getCraftSlots());
            p.inventoryMenu.broadcastChanges();
            r.remove("kit_given");
        }
    }

    /** Forge no conserva los datos persistentes del jugador al morir: se copian los del libro. */
    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains(KEY)) event.getEntity().getPersistentData().put(KEY, old.getCompound(KEY).copy());
    }

    /** Mascota vinculada que muere: se quita del libro. */
    @SubscribeEvent
    public void onPetDeath(LivingDeathEvent event) {
        LivingEntity pet = event.getEntity();
        if (pet.level().isClientSide() || !pet.getPersistentData().hasUUID(RangerSubclasses.TAG_BONDED)) return;
        ServerPlayer ranger = pet.getServer().getPlayerList().getPlayer(pet.getPersistentData().getUUID(RangerSubclasses.TAG_BONDED));
        if (ranger != null) removeBond(ranger, pet.getUUID());
    }
}
