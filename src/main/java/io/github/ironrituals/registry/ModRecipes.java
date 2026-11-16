package io.github.ironrituals.registry;

import io.github.ironrituals.IronRituals;
import io.github.ironrituals.ritual.recipe.RitualRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, IronRituals.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, IronRituals.MOD_ID);

    public static final RegistryObject<RecipeType<RitualRecipe>> RITUAL_TYPE = TYPES.register("ritual",
            () -> RecipeType.simple(IronRituals.id("ritual")));
    public static final RegistryObject<RecipeSerializer<RitualRecipe>> RITUAL_SERIALIZER = SERIALIZERS.register("ritual",
            RitualRecipe.Serializer::new);

    private ModRecipes() {}
}
