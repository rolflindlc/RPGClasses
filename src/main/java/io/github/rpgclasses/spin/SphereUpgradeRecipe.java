package io.github.rpgclasses.spin;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Mejora de bola en la mesa de trabajo (sin forma): una bola de cualquier tier y {@link SphereUpgrade#count} unidades del
 * material de una mejora (todas de la misma). Sale la misma bola (con su durabilidad y encantamientos) con esa mejora; si
 * ya tenía otra, la sustituye.
 */
public class SphereUpgradeRecipe extends CustomRecipe {
    public SphereUpgradeRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    /** La bola del grid y la mejora que forman los demás objetos (null si no es una mejora válida). */
    private static Object[] parse(CraftingContainer grid) {
        ItemStack sphere = ItemStack.EMPTY;
        SphereUpgrade upgrade = null;
        int units = 0;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack s = grid.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof SphereItem) {
                if (!sphere.isEmpty()) return null;
                sphere = s;
                continue;
            }
            SphereUpgrade u = null;
            for (SphereUpgrade c : SphereUpgrade.values()) {
                if (c.ingredient().test(s)) {
                    u = c;
                    break;
                }
            }
            if (u == null || (upgrade != null && upgrade != u)) return null;
            upgrade = u;
            units++;
        }
        if (sphere.isEmpty() || upgrade == null || units != upgrade.count || SphereUpgrade.of(sphere) == upgrade) return null;
        return new Object[] {sphere, upgrade};
    }

    @Override
    public boolean matches(CraftingContainer grid, Level level) {
        return parse(grid) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
        Object[] r = parse(grid);
        if (r == null) return ItemStack.EMPTY;
        ItemStack out = ((ItemStack) r[0]).copy();
        out.setCount(1);
        SphereUpgrade.set(out, (SphereUpgrade) r[1]);
        return out;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid) {
        return NonNullList.withSize(grid.getContainerSize(), ItemStack.EMPTY);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SpinRegistry.UPGRADE_RECIPE.get();
    }
}
