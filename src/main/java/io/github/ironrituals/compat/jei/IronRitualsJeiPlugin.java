package io.github.ironrituals.compat.jei;

import io.github.ironrituals.IronRituals;
import io.github.ironrituals.registry.ModItems;
import io.github.ironrituals.registry.ModRecipes;
import io.github.ironrituals.ritual.recipe.RitualRecipe;
import io.github.ironrituals.ritual.recipe.RitualTier;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Integración con JEI (y EMI, que lee los plugins de JEI). Muestra los componentes base de cada nivel;
 * los catalizadores no aparecen, para no estropear su descubrimiento.
 */
@JeiPlugin
public class IronRitualsJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return IronRituals.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new RitualCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (Minecraft.getInstance().level == null) return;
        List<RitualJeiEntry> entries = new ArrayList<>();
        for (RitualRecipe r : Minecraft.getInstance().level.getRecipeManager().getAllRecipesFor(ModRecipes.RITUAL_TYPE.get())) {
            for (RitualTier t : r.tiers()) entries.add(new RitualJeiEntry(r, t));
        }
        registration.addRecipes(RitualCategory.TYPE, entries);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModItems.RITUAL_CORE.get()), RitualCategory.TYPE);
    }
}
