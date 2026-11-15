package io.github.ironrituals.registry;

import io.github.ironrituals.IronRituals;
import io.github.ironrituals.item.RitualDaggerItem;
import io.github.ironrituals.item.RitualTomeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, IronRituals.MOD_ID);

    public static final RegistryObject<Item> ARCANE_CANDLE = ITEMS.register("arcane_candle",
            () -> new BlockItem(ModBlocks.ARCANE_CANDLE.get(), new Item.Properties()));
    public static final RegistryObject<Item> ARCANE_CANDLE_WORN = ITEMS.register("arcane_candle_worn",
            () -> new BlockItem(ModBlocks.ARCANE_CANDLE_WORN.get(), new Item.Properties()));
    public static final RegistryObject<Item> ARCANE_CANDLE_STUB = ITEMS.register("arcane_candle_stub",
            () -> new BlockItem(ModBlocks.ARCANE_CANDLE_STUB.get(), new Item.Properties()));

    public static final RegistryObject<Item> RITUAL_CORE = ITEMS.register("ritual_core",
            () -> new BlockItem(ModBlocks.RITUAL_CORE.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> RITUAL_DAGGER = ITEMS.register("ritual_dagger",
            () -> new RitualDaggerItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> RITUAL_TOME = ITEMS.register("ritual_tome",
            () -> new RitualTomeItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    private ModItems() {}
}
