package io.github.rpgclasses.content.race;

import io.github.rpgclasses.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Menú del cinturón del Porteador: una fila con sus huecos (3–6) y el inventario del jugador. El dueño pone y quita
 * bloques; un aliado solo los abre. "Abrir" es un botón de menú vanilla ({@code clickMenuButton}): el servidor abre
 * entonces el menú del bloque de ese hueco ({@link PorterBelt#openEntry}).
 */
public class BeltMenu extends AbstractContainerMenu {
    public static final int SLOT_Y = 20;
    public static final int INV_Y = 66;
    public static final int HEIGHT = INV_Y + 82;

    private final Container belt;
    private final int capacity;
    private final boolean manage;
    private final UUID owner;

    /** Cliente: los datos llegan del servidor (huecos y si puede cambiar los bloques). */
    public BeltMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, new SimpleContainer(PorterBelt.MAX_SLOTS), buf.readVarInt(), buf.readBoolean(), null);
    }

    public BeltMenu(int id, Inventory inv, Container belt, int capacity, boolean manage, UUID owner) {
        super(ModMenus.BELT.get(), id);
        this.belt = belt;
        this.capacity = Math.max(0, Math.min(PorterBelt.MAX_SLOTS, capacity));
        this.manage = manage;
        this.owner = owner;
        for (int i = 0; i < this.capacity; i++) addSlot(new BeltSlot(belt, i, slotX(i, this.capacity), SLOT_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, INV_Y + 58));
    }

    /** X del hueco i (centrados, 28 px entre huecos para que quepa el botón "Abrir" debajo). */
    public static int slotX(int i, int capacity) {
        return 88 - capacity * 14 + i * 28 + 6;
    }

    public int capacity() {
        return capacity;
    }

    public boolean manage() {
        return manage;
    }

    /** Cuántas unidades de ese objeto caben en el hueco: 2 cofres (cofre grande) si queda capacidad; si no, 1. */
    int room(int slot, ItemStack stack) {
        int used = 0;
        for (int i = 0; i < capacity; i++) if (i != slot) used += PorterBelt.cost(belt.getItem(i));
        return Math.min(PorterBelt.maxCount(stack), capacity - used);
    }

    private final class BeltSlot extends Slot {
        BeltSlot(Container c, int index, int x, int y) {
            super(c, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return manage && PorterBelt.allowed(stack) && room(getContainerSlot(), stack) > 0;
        }

        @Override
        public boolean mayPickup(Player player) {
            return manage;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return Math.max(1, room(getContainerSlot(), stack));
        }
    }

    /** Botón "Abrir" del hueco {@code id}. En el cliente solo valida; el servidor abre el menú del bloque. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= capacity || belt.getItem(id).isEmpty()) return false;
        if (player instanceof ServerPlayer sp && owner != null) PorterBelt.openEntry(sp, owner, id);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!manage || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < capacity) {
            // Del cinturón al inventario.
            if (!moveItemStackTo(stack, capacity, slots.size(), true)) return ItemStack.EMPTY;
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
            return copy;
        }
        // Del inventario al primer hueco libre del cinturón, de uno en uno.
        for (int i = 0; i < capacity; i++) {
            Slot b = slots.get(i);
            if (b.hasItem() || !b.mayPlace(stack)) continue;
            b.set(stack.split(1));
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
            break;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return owner == null || PorterBelt.canUse(player, owner);
    }
}
