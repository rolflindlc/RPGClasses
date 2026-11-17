package io.github.rpgclasses.bard;

import io.github.rpgclasses.RpgClasses;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/** Objetos del Bardo: instrumentos, guitarra-hacha, partituras, materiales y la receta de subir partituras. */
public final class BardItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RpgClasses.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, RpgClasses.MODID);

    public static final Map<Instrument, RegistryObject<Item>> INSTRUMENTS = new EnumMap<>(Instrument.class);

    static {
        for (Instrument i : Instrument.values()) {
            if (i == Instrument.GUITAR_AXE) continue;
            Item.Properties props = new Item.Properties().rarity(rarity(i.tier));
            if (i.tier == Instrument.Tier.LEGENDARY) props.fireResistant();
            INSTRUMENTS.put(i, ITEMS.register(i.id, () -> new InstrumentItem(i, props)));
        }
    }

    public static final RegistryObject<Item> GUITAR_AXE = ITEMS.register("guitar_axe",
            () -> new GuitarAxeItem(new Item.Properties().rarity(Rarity.RARE).fireResistant()));
    public static final RegistryObject<Item> SHEET_MUSIC = ITEMS.register("sheet_music", () -> new SheetMusicItem(new Item.Properties()));
    public static final RegistryObject<Item> RESONANT_SHARD = ITEMS.register("resonant_shard", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ARCANE_THREAD = ITEMS.register("arcane_thread", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ABYSSAL_REED = ITEMS.register("abyssal_reed",
            () -> new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> COLOSSUS_DRUMSKIN = ITEMS.register("colossus_drumskin",
            () -> new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    /** Solo para dibujar la Nota Cortante (no sale en el creativo). */
    public static final RegistryObject<Item> MUSIC_NOTE = ITEMS.register("music_note", () -> new Item(new Item.Properties()));

    public static final RegistryObject<RecipeSerializer<SheetMusicUpgradeRecipe>> SHEET_UPGRADE = RECIPES.register("sheet_music_upgrade",
            () -> new SimpleCraftingRecipeSerializer<>(SheetMusicUpgradeRecipe::new));

    private BardItems() {}

    private static Rarity rarity(Instrument.Tier tier) {
        return switch (tier) {
            case COMMON -> Rarity.COMMON;
            case RARE -> Rarity.UNCOMMON;
            case EPIC -> Rarity.RARE;
            case LEGENDARY -> Rarity.EPIC;
        };
    }

    /** El instrumento de un objeto (la guitarra-hacha incluida), o null. */
    public static Instrument instrumentOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.getItem() instanceof InstrumentItem i) return i.instrument;
        if (stack.getItem() instanceof GuitarAxeItem) return Instrument.GUITAR_AXE;
        return null;
    }

    /** Objetos para la pestaña de creativo: instrumentos, materiales y cada partitura a nivel 1 y al máximo. */
    public static void creativeItems(Consumer<ItemStack> out) {
        for (Instrument i : Instrument.values()) {
            out.accept(new ItemStack(i == Instrument.GUITAR_AXE ? GUITAR_AXE.get() : INSTRUMENTS.get(i).get()));
        }
        out.accept(new ItemStack(RESONANT_SHARD.get()));
        out.accept(new ItemStack(ARCANE_THREAD.get()));
        out.accept(new ItemStack(ABYSSAL_REED.get()));
        out.accept(new ItemStack(COLOSSUS_DRUMSKIN.get()));
        for (MusicSpellInfo s : MusicSpellInfo.values()) {
            out.accept(Sheets.sheet(s, 1));
            out.accept(Sheets.sheet(s, s.maxLevel));
        }
    }
}
