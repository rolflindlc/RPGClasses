package io.github.rpgclasses.server;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.KitItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/** Equipo inicial: irrompible y marcado, para poder retirarlo al reiniciar la clase. */
public final class KitService {
    /** Marca NBT de los objetos del kit; su valor es la clase que los entregó. */
    public static final String KIT_TAG = "rpgclasses_kit";

    public static void grant(ServerPlayer player, ClassDef classDef) {
        for (KitItem k : classDef.startingKit()) {
            ResourceLocation id = ResourceLocation.tryParse(k.item());
            Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
            if (item == null || item == Items.AIR) {
                RpgClasses.LOGGER.warn("[rpgclasses] Objeto de kit desconocido: {}", k.item());
                continue;
            }
            ItemStack stack = new ItemStack(item, k.count());
            stack.getOrCreateTag().putString(KIT_TAG, classDef.id());
            if (stack.isDamageableItem()) stack.getOrCreateTag().putBoolean("Unbreakable", true);

            EquipmentSlot slot = slot(k.slot());
            if (slot != null && player.getItemBySlot(slot).isEmpty()) {
                player.setItemSlot(slot, stack);
            } else {
                // Si la ranura está ocupada va al inventario; si no cabe, cae al suelo.
                player.getInventory().placeItemBackInInventory(stack);
            }
        }
    }

    /** Retira del inventario los objetos de kit. Los que estén en cofres o en el suelo se dejan. */
    public static void removeAll(ServerPlayer player) {
        player.getInventory().clearOrCountMatchingItems(
                stack -> stack.hasTag() && stack.getTag().contains(KIT_TAG), -1, player.inventoryMenu.getCraftSlots());
        player.inventoryMenu.broadcastChanges();
    }

    private static EquipmentSlot slot(String name) {
        return switch (name) {
            case "mainhand" -> EquipmentSlot.MAINHAND;
            case "offhand" -> EquipmentSlot.OFFHAND;
            case "head" -> EquipmentSlot.HEAD;
            case "chest" -> EquipmentSlot.CHEST;
            case "legs" -> EquipmentSlot.LEGS;
            case "feet" -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    private KitService() {}
}
