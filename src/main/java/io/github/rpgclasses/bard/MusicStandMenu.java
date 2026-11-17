package io.github.rpgclasses.bard;

import io.github.rpgclasses.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Atril: las partituras del instrumento de la mano principal, como un pequeño cofre de 12 huecos (2 filas de 6).
 * Solo se puede poner partituras en los huecos por debajo de las ranuras del jugador (las del instrumento más el
 * Repertorio Ampliado del Juglar) y con un nivel que admita la rareza del instrumento; de los demás solo se sacan.
 * El hueco de la barra con el instrumento queda bloqueado mientras el atril está abierto.
 */
public class MusicStandMenu extends AbstractContainerMenu {
    public static final int COLS = 6;
    public static final int SLOT_X = 35, SLOT_Y = 20;
    public static final int INV_Y = 72;
    public static final int HEIGHT = INV_Y + 82;

    private final SimpleContainer sheets = new SimpleContainer(Instrument.MAX_SLOTS);
    private final ItemStack instrument;
    private final Instrument kind;
    private final int capacity;
    private final int lockedSlot;

    public MusicStandMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readVarInt());
    }

    public MusicStandMenu(int id, Inventory inv, int capacity) {
        super(ModMenus.MUSIC_STAND.get(), id);
        this.instrument = inv.player.getMainHandItem();
        this.kind = BardItems.instrumentOf(instrument);
        this.capacity = Math.max(0, Math.min(Instrument.MAX_SLOTS, capacity));
        this.lockedSlot = inv.selected;
        Sheets.fill(instrument, sheets);
        if (!inv.player.level().isClientSide) sheets.addListener(c -> Sheets.store(instrument, sheets));
        for (int i = 0; i < Instrument.MAX_SLOTS; i++) {
            addSlot(new SheetSlot(i, SLOT_X + (i % COLS) * 18, SLOT_Y + (i / COLS) * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
        }
        for (int col = 0; col < 9; col++) {
            addSlot(col == lockedSlot ? new LockedSlot(inv, col, 8 + col * 18, INV_Y + 58) : new Slot(inv, col, 8 + col * 18, INV_Y + 58));
        }
    }

    public int capacity() {
        return capacity;
    }

    public Instrument kind() {
        return kind;
    }

    /** ¿Se puede poner esa partitura en ese hueco? */
    boolean accepts(int index, ItemStack stack) {
        if (kind == null || index >= capacity) return false;
        MusicSpellInfo spell = Sheets.spellOf(stack);
        if (spell == null || Sheets.levelOf(stack) > spell.levelCap(kind.levelCap())) return false;
        for (int i = 0; i < sheets.getContainerSize(); i++) {
            if (i != index && Sheets.spellOf(sheets.getItem(i)) == spell) return false; // sin repetir conjuro
        }
        return true;
    }

    private final class SheetSlot extends Slot {
        SheetSlot(int index, int x, int y) {
            super(sheets, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return accepts(getContainerSlot(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private static final class LockedSlot extends Slot {
        LockedSlot(Inventory inv, int index, int x, int y) {
            super(inv, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < Instrument.MAX_SLOTS) {
            if (!moveItemStackTo(stack, Instrument.MAX_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < Instrument.MAX_SLOTS && !moved; i++) {
                Slot s = slots.get(i);
                if (s.hasItem() || !s.mayPlace(stack)) continue;
                s.set(stack.split(1));
                moved = true;
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive() && kind != null && player.getMainHandItem() == instrument;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer sp && kind != null) {
            Sheets.store(instrument, sheets);
            if (Bard.magic != null) Bard.magic.syncContainer(sp, instrument);
        }
    }
}
