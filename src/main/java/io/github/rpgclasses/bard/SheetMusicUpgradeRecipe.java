package io.github.rpgclasses.bard;

import io.github.rpgclasses.RpgClasses;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Subir una partitura un nivel (propuesta): partitura + una tinta de Iron's de la rareza que pide el nivel de destino,
 * sin forma. Para un conjuro de N niveles, el nivel objetivo / N decide la tinta: hasta 20 % común, 40 % poco común,
 * 60 % rara, 80 % épica y el resto legendaria (10 niveles: 2 común, 3-4 poco común... 9-10 legendaria).
 */
public class SheetMusicUpgradeRecipe extends CustomRecipe {
    private static final String[] INKS = {"common", "uncommon", "rare", "epic", "legendary"};

    public SheetMusicUpgradeRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    /** Tinta que hace falta para llegar a {@code targetLevel}. */
    public static TagKey<Item> inkFor(MusicSpellInfo spell, int targetLevel) {
        double f = targetLevel / (double) spell.maxLevel;
        int tier = f <= 0.2 + 1e-9 ? 0 : f <= 0.4 + 1e-9 ? 1 : f <= 0.6 + 1e-9 ? 2 : f <= 0.8 + 1e-9 ? 3 : 4;
        return ItemTags.create(RpgClasses.id("bard/ink_" + INKS[tier]));
    }

    private static ItemStack[] find(CraftingContainer inv) {
        ItemStack sheet = ItemStack.EMPTY, ink = ItemStack.EMPTY;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof SheetMusicItem) {
                if (!sheet.isEmpty()) return null;
                sheet = s;
            } else {
                if (!ink.isEmpty()) return null;
                ink = s;
            }
        }
        return sheet.isEmpty() || ink.isEmpty() ? null : new ItemStack[] {sheet, ink};
    }

    @Override
    public boolean matches(CraftingContainer inv, Level level) {
        ItemStack[] parts = find(inv);
        if (parts == null) return false;
        MusicSpellInfo spell = Sheets.spellOf(parts[0]);
        if (spell == null) return false;
        int lvl = Sheets.levelOf(parts[0]);
        return lvl < spell.maxLevel && parts[1].is(inkFor(spell, lvl + 1));
    }

    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess access) {
        ItemStack[] parts = find(inv);
        if (parts == null) return ItemStack.EMPTY;
        MusicSpellInfo spell = Sheets.spellOf(parts[0]);
        if (spell == null) return ItemStack.EMPTY;
        return Sheets.sheet(spell, Sheets.levelOf(parts[0]) + 1);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BardItems.SHEET_UPGRADE.get();
    }
}
