package io.github.ironrituals.client;

import io.github.ironrituals.IronRituals;
import io.github.ironrituals.item.SoulData;
import io.github.ironrituals.registry.ModBlockEntities;
import io.github.ironrituals.registry.ModItems;
import io.github.ironrituals.registry.ModRecipes;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraftforge.client.event.RegisterRecipeBookCategoriesEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = IronRituals.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // La daga cambia de modelo (capa roja emisiva) cuando lleva un alma.
        event.enqueueWork(() -> ItemProperties.register(ModItems.RITUAL_DAGGER.get(), IronRituals.id("soul"),
                (stack, level, entity, seed) -> SoulData.hasSoul(stack) ? 1.0F : 0.0F));
    }

    /** Los rituales no van al libro de recetas; se registran como categoría desconocida para que no avise en el log. */
    @SubscribeEvent
    public static void onRecipeBookCategories(RegisterRecipeBookCategoriesEvent event) {
        event.registerRecipeCategoryFinder(ModRecipes.RITUAL_TYPE.get(), r -> RecipeBookCategories.UNKNOWN);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.RITUAL_CORE.get(), ctx -> new RitualCoreRenderer());
    }
}
