package io.github.rpgclasses.registry;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.content.race.RaceAltarBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Bloques del mod: el Altar de Raza (ritos). */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, RpgClasses.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RpgClasses.MODID);

    public static final RegistryObject<Block> RACE_ALTAR = BLOCKS.register("race_altar", () -> new RaceAltarBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.5f, 1200f).requiresCorrectToolForDrops()
                    .lightLevel(s -> 7).sound(SoundType.POLISHED_DEEPSLATE)));
    public static final RegistryObject<Item> RACE_ALTAR_ITEM = ITEMS.register("race_altar",
            () -> new BlockItem(RACE_ALTAR.get(), new Item.Properties()));

    public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(RACE_ALTAR_ITEM);
    }

    private ModBlocks() {}
}
