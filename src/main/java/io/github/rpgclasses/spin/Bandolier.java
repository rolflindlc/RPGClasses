package io.github.rpgclasses.spin;

import io.github.rpgclasses.server.KitService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.LongPredicate;

/**
 * Contenido de una bandolera, guardado en su NBT. Las bolas nunca salen de él: al lanzar una se marca "en vuelo" con el id
 * del lanzamiento y al volver se desmarca (con el momento en que vuelve a estar lista). Así no se pueden duplicar ni
 * perder: si la bola en vuelo desaparece (chunk descargado, servidor caído), la marca se limpia sola.
 * <pre>
 * Spheres: [{Item: {...}, Out: id del lanzamiento (0 = en la bandolera), OutAt: tic, Ready: tic en que está lista}]
 * Selected: id del tipo elegido (tier y mejora: iron, diamond+copper...)
 * </pre>
 */
public final class Bandolier {
    public static final int CAPACITY = 9;
    private static final String LIST = "Spheres", ITEM = "Item", OUT = "Out", OUT_AT = "OutAt", READY = "Ready", LAST = "Last", SELECTED = "Selected";

    /** Una bola de la bandolera: su objeto, si está en vuelo (id del lanzamiento) y cuándo vuelve a estar lista. */
    public static final class Entry {
        public final ItemStack stack;
        /** out: id del lanzamiento en curso (0 = en la bandolera); last: id del último lanzamiento (Captura Perfecta). */
        public long out, outAt, ready, last;

        Entry(ItemStack stack, long out, long outAt, long ready, long last) {
            this.stack = stack;
            this.out = out;
            this.outAt = outAt;
            this.ready = ready;
            this.last = last;
        }

        public SphereKind kind() {
            return SphereKind.of(stack);
        }

        public boolean inFlight() {
            return out != 0;
        }

        public boolean ready(long now) {
            return out == 0 && ready <= now;
        }
    }

    private Bandolier() {}

    public static boolean is(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BandolierItem;
    }

    public static List<Entry> entries(ItemStack bandolier) {
        List<Entry> out = new ArrayList<>();
        CompoundTag tag = bandolier.getTag();
        if (tag == null || !tag.contains(LIST, Tag.TAG_LIST)) return out;
        ListTag list = tag.getList(LIST, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            ItemStack s = ItemStack.of(e.getCompound(ITEM));
            if (s.isEmpty() || !(s.getItem() instanceof SphereItem)) continue;
            out.add(new Entry(s, e.getLong(OUT), e.getLong(OUT_AT), e.getLong(READY), e.getLong(LAST)));
        }
        return out;
    }

    public static void save(ItemStack bandolier, List<Entry> entries) {
        ListTag list = new ListTag();
        for (Entry e : entries) {
            CompoundTag t = new CompoundTag();
            t.put(ITEM, e.stack.save(new CompoundTag()));
            if (e.out != 0) {
                t.putLong(OUT, e.out);
                t.putLong(OUT_AT, e.outAt);
            }
            if (e.ready > 0) t.putLong(READY, e.ready);
            if (e.last != 0) t.putLong(LAST, e.last);
            list.add(t);
        }
        CompoundTag tag = bandolier.getOrCreateTag();
        if (list.isEmpty()) tag.remove(LIST);
        else tag.put(LIST, list);
    }

    public static int count(ItemStack bandolier) {
        CompoundTag tag = bandolier.getTag();
        return tag == null ? 0 : tag.getList(LIST, Tag.TAG_COMPOUND).size();
    }

    public static boolean canAdd(ItemStack bandolier, ItemStack sphere) {
        return sphere.getItem() instanceof SphereItem && count(bandolier) < CAPACITY;
    }

    /** Guarda una bola (una unidad de {@code sphere}). Devuelve true si cupo. */
    public static boolean add(ItemStack bandolier, ItemStack sphere) {
        if (!canAdd(bandolier, sphere)) return false;
        List<Entry> list = entries(bandolier);
        ItemStack one = sphere.copy();
        one.setCount(1);
        list.add(new Entry(one, 0, 0, 0, 0));
        save(bandolier, list);
        if (selected(bandolier) == null) select(bandolier, SphereKind.of(one));
        return true;
    }

    /** Saca la última bola que no esté en vuelo (para el clic derecho tipo saco). */
    public static ItemStack removeOne(ItemStack bandolier) {
        List<Entry> list = entries(bandolier);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).inFlight()) continue;
            Entry e = list.remove(i);
            save(bandolier, list);
            return e.stack;
        }
        return ItemStack.EMPTY;
    }

    public static SphereKind selected(ItemStack bandolier) {
        CompoundTag tag = bandolier.getTag();
        return tag == null ? null : SphereKind.byId(tag.getString(SELECTED));
    }

    public static void select(ItemStack bandolier, SphereKind k) {
        if (k == null) bandolier.getOrCreateTag().remove(SELECTED);
        else bandolier.getOrCreateTag().putString(SELECTED, k.id());
    }

    /** El tipo elegido; si ya no queda ninguna bola de él, el primero que haya. */
    public static SphereKind current(ItemStack bandolier) {
        SphereKind sel = selected(bandolier);
        List<Entry> list = entries(bandolier);
        for (Entry e : list) if (Objects.equals(e.kind(), sel)) return sel;
        return list.isEmpty() ? sel : list.get(0).kind();
    }

    /** Siguiente tipo de los que hay en la bandolera (por tier y luego por mejora). */
    public static SphereKind cycle(ItemStack bandolier) {
        List<Entry> list = entries(bandolier);
        if (list.isEmpty()) return null;
        SphereKind cur = current(bandolier);
        TreeMap<Integer, SphereKind> kinds = new TreeMap<>();
        for (Entry e : list) {
            SphereKind k = e.kind();
            if (k != null) kinds.put(k.order(), k);
        }
        if (kinds.isEmpty()) return cur;
        Map.Entry<Integer, SphereKind> next = cur == null ? null : kinds.higherEntry(cur.order());
        SphereKind k = next != null ? next.getValue() : kinds.firstEntry().getValue();
        select(bandolier, k);
        return k;
    }

    public static int total(ItemStack bandolier, SphereKind k) {
        int n = 0;
        for (Entry e : entries(bandolier)) if (Objects.equals(e.kind(), k)) n++;
        return n;
    }

    public static int ready(ItemStack bandolier, SphereKind k, long now) {
        int n = 0;
        for (Entry e : entries(bandolier)) if (Objects.equals(e.kind(), k) && e.ready(now)) n++;
        return n;
    }

    public static int inFlight(ItemStack bandolier) {
        int n = 0;
        for (Entry e : entries(bandolier)) if (e.inFlight()) n++;
        return n;
    }

    /** La primera bola de ese tipo que esté lista para lanzar (sin marcarla), o null. */
    public static Entry firstReady(ItemStack bandolier, SphereKind k, long now) {
        for (Entry e : entries(bandolier)) if (Objects.equals(e.kind(), k) && e.ready(now)) return e;
        return null;
    }

    /** Marca en vuelo la primera bola lista de ese tipo y devuelve una copia de su objeto (o EMPTY). */
    public static ItemStack take(ItemStack bandolier, SphereKind k, long now, long throwId) {
        List<Entry> list = entries(bandolier);
        for (Entry e : list) {
            if (!Objects.equals(e.kind(), k) || !e.ready(now)) continue;
            e.out = throwId;
            e.outAt = now;
            save(bandolier, list);
            return e.stack.copy();
        }
        return ItemStack.EMPTY;
    }

    /** Limpia las marcas de vuelo cuya bola ya no existe ({@code alive} dice si un lanzamiento sigue vivo). */
    public static boolean clearStale(ItemStack bandolier, LongPredicate alive, long now) {
        List<Entry> list = entries(bandolier);
        boolean changed = false;
        for (Entry e : list) {
            if (e.out != 0 && !alive.test(e.out)) {
                e.out = 0;
                e.ready = now;
                changed = true;
            }
        }
        if (changed) save(bandolier, list);
        return changed;
    }

    /** Bandoleras del inventario (mano secundaria incluida) y la que lleva el cursor. */
    public static List<ItemStack> all(Player player) {
        List<ItemStack> out = new ArrayList<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (is(s)) out.add(s);
        }
        ItemStack carried = player.containerMenu == null ? ItemStack.EMPTY : player.containerMenu.getCarried();
        if (is(carried)) out.add(carried);
        return out;
    }

    /** La bola en vuelo vuelve: se desmarca y queda lista en {@code readyAt}. Devuelve false si no se encontró su bandolera. */
    public static boolean giveBack(Player player, long throwId, long readyAt) {
        for (ItemStack b : all(player)) {
            List<Entry> list = entries(b);
            for (Entry e : list) {
                if (e.out != throwId) continue;
                e.out = 0;
                e.ready = readyAt;
                e.last = throwId;
                save(b, list);
                return true;
            }
        }
        return false;
    }

    /** Captura Perfecta: la bola que acaba de volver de ese lanzamiento queda lista ya. */
    public static void makeReady(Player player, long lastId, long now) {
        for (ItemStack b : all(player)) {
            List<Entry> list = entries(b);
            for (Entry e : list) {
                if (e.out != 0 || e.last != lastId) continue;
                e.ready = now;
                save(b, list);
                return;
            }
        }
    }

    /** La bola en vuelo de ese lanzamiento (copia), o EMPTY. */
    public static ItemStack peek(Player player, long throwId) {
        for (ItemStack b : all(player)) {
            for (Entry e : entries(b)) if (e.out == throwId) return e.stack.copy();
        }
        return ItemStack.EMPTY;
    }

    /**
     * Gasta durabilidad de la bola de ese lanzamiento (Irrompible y las bolas del kit no la pierden). Devuelve true si
     * se ha roto (se quita de la bandolera).
     */
    public static boolean damage(ServerPlayer player, long throwId, int amount) {
        if (amount <= 0) return false;
        for (ItemStack b : all(player)) {
            List<Entry> list = entries(b);
            for (int i = 0; i < list.size(); i++) {
                Entry e = list.get(i);
                if (e.out != throwId) continue;
                boolean broken = e.stack.isDamageableItem() && e.stack.hurt(amount, player.getRandom(), player);
                if (broken) list.remove(i);
                save(b, list);
                return broken;
            }
        }
        return false;
    }

    /**
     * Antes de retirar el kit al reiniciar la clase: las bolas del kit salen de cualquier bandolera, y las bolas propias que
     * estén en una bandolera del kit pasan al inventario (no se pierden con ella).
     */
    public static void beforeKitRemoval(ServerPlayer player) {
        List<ItemStack> toGive = new ArrayList<>();
        for (ItemStack b : all(player)) {
            boolean kitBandolier = b.hasTag() && b.getTag().contains(KitService.KIT_TAG);
            List<Entry> list = entries(b);
            List<Entry> keep = new ArrayList<>();
            for (Entry e : list) {
                boolean kitSphere = e.stack.hasTag() && e.stack.getTag().contains(KitService.KIT_TAG);
                if (kitSphere) continue;
                e.out = 0;
                e.ready = 0;
                if (kitBandolier) toGive.add(e.stack);
                else keep.add(e);
            }
            save(b, keep);
        }
        for (ItemStack s : toGive) player.getInventory().placeItemBackInInventory(s);
    }
}
