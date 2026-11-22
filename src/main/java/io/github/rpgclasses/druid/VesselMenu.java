package io.github.rpgclasses.druid;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Los cuatro huecos del Recipiente de Carne (cabeza, torso, patas, instinto) y el inventario. Cada hueco solo admite
 * su parte. El hueco de la barra con el recipiente queda bloqueado mientras está abierto.
 */
public class VesselMenu extends AbstractContainerMenu {
    public static final int SLOT_Y = 26, INV_Y = 66, HEIGHT = INV_Y + 82;
    public static final int[] SLOT_X = {35, 62, 98, 125};

    private final SimpleContainer parts = new SimpleContainer(4);
    private final ItemStack vessel;
    private final int lockedSlot;

    public VesselMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
    }

    public VesselMenu(int id, Inventory inv, InteractionHand hand) {
        super(DruidRegistry.VESSEL_MENU.get(), id);
        this.vessel = inv.player.getItemInHand(hand);
        this.lockedSlot = hand == InteractionHand.MAIN_HAND ? inv.selected : -1;
        ItemStack[] stored = FleshVesselItem.parts(vessel);
        for (int i = 0; i < 4; i++) parts.setItem(i, stored[i]);
        if (!inv.player.level().isClientSide) parts.addListener(c -> store());
        for (int i = 0; i < 4; i++) addSlot(new PartSlot(i, SLOT_X[i], SLOT_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
        }
        for (int col = 0; col < 9; col++) {
            addSlot(col == lockedSlot ? new LockedSlot(inv, col, 8 + col * 18, INV_Y + 58) : new Slot(inv, col, 8 + col * 18, INV_Y + 58));
        }
    }

    private void store() {
        ItemStack[] out = new ItemStack[4];
        for (int i = 0; i < 4; i++) out[i] = parts.getItem(i);
        FleshVesselItem.setParts(vessel, out);
    }

    static boolean fits(int index, ItemStack stack) {
        return stack.getItem() instanceof BeastPartItem part && part.part == index && !BeastPartItem.species(stack).isEmpty();
    }

    private final class PartSlot extends Slot {
        PartSlot(int index, int x, int y) {
            super(parts, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return fits(getContainerSlot(), stack);
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
        if (index < 4) {
            if (!moveItemStackTo(stack, 4, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < 4 && !moved; i++) {
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
        return player.isAlive() && (player.getMainHandItem() == vessel || player.getOffhandItem() == vessel);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) store();
    }
}
