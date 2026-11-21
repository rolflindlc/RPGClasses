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
    public static final String KIT_TAG = "rites_and_paths_kit";
    /** Inscribe conjuros en un objeto del kit (lo pone el puente con Iron's; null si no está instalado). */
    public static java.util.function.BiConsumer<ItemStack, KitItem> spellInscriber;

    public static void grant(ServerPlayer player, ClassDef classDef) {
        // Bolas del kit con ranura "bandolier" (Maestro del Giro): van dentro de la bandolera del kit, que se llena antes
        // de colocarla (al colocarla en el inventario se copia). Sin bandolera en el kit, van al inventario.
        java.util.List<ItemStack> forBandolier = new java.util.ArrayList<>();
        for (KitItem k : classDef.startingKit()) {
            if (!"bandolier".equals(k.slot())) continue;
            ItemStack stack = create(k, classDef);
            if (stack.isEmpty()) continue;
            for (int i = 0; i < k.count(); i++) {
                ItemStack one = stack.copy();
                one.setCount(1);
                forBandolier.add(one);
            }
        }
        boolean filled = false;
        for (KitItem k : classDef.startingKit()) {
            if ("bandolier".equals(k.slot())) continue;
            ItemStack stack = create(k, classDef);
            if (stack.isEmpty()) continue;
            if (!filled && io.github.rpgclasses.spin.Bandolier.is(stack)) {
                for (ItemStack s : forBandolier) io.github.rpgclasses.spin.Bandolier.add(stack, s);
                filled = true;
            }
            EquipmentSlot slot = slot(k.slot());
            if (slot != null && player.getItemBySlot(slot).isEmpty()) {
                player.setItemSlot(slot, stack);
            } else {
                // Si la ranura está ocupada va al inventario; si no cabe, cae al suelo.
                player.getInventory().placeItemBackInInventory(stack);
            }
        }
        if (!filled) for (ItemStack s : forBandolier) player.getInventory().placeItemBackInInventory(s);
    }

    /** Objeto del kit, marcado e irrompible (EMPTY si el objeto no existe). */
    private static ItemStack create(KitItem k, ClassDef classDef) {
        ResourceLocation id = ResourceLocation.tryParse(k.item());
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) {
            RpgClasses.LOGGER.warn("[rites_and_paths] Objeto de kit desconocido: {}", k.item());
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, k.count());
        stack.getOrCreateTag().putString(KIT_TAG, classDef.id());
        if (stack.isDamageableItem()) stack.getOrCreateTag().putBoolean("Unbreakable", true);
        if (!k.spells().isEmpty() && spellInscriber != null) spellInscriber.accept(stack, k);
        return stack;
    }

    /** Retira del inventario los objetos de kit. Los que estén en cofres o en el suelo se dejan. */
    public static void removeAll(ServerPlayer player) {
        // Maestro del Giro: las bolas vuelven, las del kit salen de las bandoleras y las propias no se pierden con la del kit.
        io.github.rpgclasses.spin.SpinThrow.recallAll(player);
        io.github.rpgclasses.spin.Bandolier.beforeKitRemoval(player);
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
