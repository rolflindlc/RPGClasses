package io.github.rpgclasses.compat.jei;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.bard.BardItems;
import io.github.rpgclasses.bard.Instrument;
import io.github.rpgclasses.bard.MusicSpellInfo;
import io.github.rpgclasses.bard.SheetMusicUpgradeRecipe;
import io.github.rpgclasses.bard.Sheets;
import io.github.rpgclasses.registry.ModBlocks;
import io.github.rpgclasses.spin.SphereMaterial;
import io.github.rpgclasses.spin.SphereUpgrade;
import io.github.rpgclasses.spin.SpinRegistry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI (opcional; solo se carga si está JEI). Las recetas normales las enseña JEI solo; aquí:
 * <ul>
 *   <li><b>Recetas especiales</b> como recetas de mesa de trabajo de muestra: mejoras de bola (cada tier con cada mejora)
 *       y subir de nivel las partituras (cada conjuro musical y nivel, con su tinta; solo con Iron's).</li>
 *   <li><b>Subtipos</b>: las bolas por mejora y las partituras por conjuro y nivel, para que JEI las distinga.</li>
 *   <li><b>Información</b> de los objetos que no se fabrican o que funcionan distinto (bolas, bandolera, partitura,
 *       materiales de jefe, amuleto de creativo). Textos en tools/descriptions.py.</li>
 *   <li>Oculta la nota musical (solo es el dibujo de un proyectil del Bardo).</li>
 * </ul>
 */
@JeiPlugin
public class RitesJeiPlugin implements IModPlugin {
    private static final String INFO = "jei.rites_and_paths.info.";

    @Override
    public ResourceLocation getPluginUid() {
        return RpgClasses.id("jei");
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration reg) {
        for (SphereMaterial m : SphereMaterial.values()) {
            reg.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK, SpinRegistry.sphere(m), (stack, ctx) -> {
                SphereUpgrade u = SphereUpgrade.of(stack);
                return u == null ? IIngredientSubtypeInterpreter.NONE : u.id;
            });
        }
        reg.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK, BardItems.SHEET_MUSIC.get(), (stack, ctx) -> {
            MusicSpellInfo spell = Sheets.spellOf(stack);
            return spell == null ? IIngredientSubtypeInterpreter.NONE : spell.fullId() + "@" + Sheets.levelOf(stack);
        });
    }

    @Override
    public void registerRecipes(IRecipeRegistration reg) {
        List<CraftingRecipe> crafting = new ArrayList<>();
        // Mejoras de bola: bola de cada tier + unidades del material de cada mejora.
        for (SphereMaterial m : SphereMaterial.values()) {
            for (SphereUpgrade u : SphereUpgrade.values()) {
                NonNullList<Ingredient> in = NonNullList.create();
                in.add(Ingredient.of(SpinRegistry.sphere(m)));
                for (int i = 0; i < u.count; i++) in.add(u.ingredient());
                ItemStack out = new ItemStack(SpinRegistry.sphere(m));
                SphereUpgrade.set(out, u);
                crafting.add(new ShapelessRecipe(RpgClasses.id("jei/sphere_upgrade/" + m.id + "_" + u.id), "rites_and_paths_sphere_upgrade",
                        CraftingBookCategory.EQUIPMENT, out, in));
            }
        }
        // Partituras: cada conjuro musical sube un nivel con la tinta que pide (necesita Iron's).
        if (ModList.get().isLoaded("irons_spellbooks")) {
            for (MusicSpellInfo s : MusicSpellInfo.values()) {
                for (int lvl = 1; lvl < s.maxLevel; lvl++) {
                    NonNullList<Ingredient> in = NonNullList.create();
                    in.add(Ingredient.of(Sheets.sheet(s, lvl)));
                    in.add(Ingredient.of(SheetMusicUpgradeRecipe.inkFor(s, lvl + 1)));
                    crafting.add(new ShapelessRecipe(RpgClasses.id("jei/sheet_music/" + s.name().toLowerCase(java.util.Locale.ROOT) + "_" + (lvl + 1)),
                            "rites_and_paths_sheet_music", CraftingBookCategory.MISC, Sheets.sheet(s, lvl + 1), in));
                }
            }
        }
        reg.addRecipes(RecipeTypes.CRAFTING, crafting);

        // Información.
        for (SphereMaterial m : SphereMaterial.values()) {
            info(reg, new ItemStack(SpinRegistry.sphere(m)), "sphere");
            for (SphereUpgrade u : SphereUpgrade.values()) {
                ItemStack s = new ItemStack(SpinRegistry.sphere(m));
                SphereUpgrade.set(s, u);
                info(reg, s, "sphere");
            }
        }
        info(reg, new ItemStack(SpinRegistry.BANDOLIER.get()), "sphere_bandolier");
        info(reg, new ItemStack(BardItems.SHEET_MUSIC.get()), "sheet_music");
        for (MusicSpellInfo s : MusicSpellInfo.values()) {
            for (int lvl = 1; lvl <= s.maxLevel; lvl++) info(reg, Sheets.sheet(s, lvl), "sheet_music");
        }
        info(reg, new ItemStack(BardItems.INSTRUMENTS.get(Instrument.ETERNAL_SUN_LUTE).get()), "eternal_sun_lute");
        info(reg, new ItemStack(BardItems.ABYSSAL_REED.get()), "abyssal_reed");
        info(reg, new ItemStack(BardItems.COLOSSUS_DRUMSKIN.get()), "colossus_drumskin");
        info(reg, new ItemStack(ModBlocks.CREATIVE_AMULET.get()), "creative_amulet");
    }

    private static void info(IRecipeRegistration reg, ItemStack stack, String key) {
        reg.addIngredientInfo(stack, VanillaTypes.ITEM_STACK, Component.translatable(INFO + key));
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        Item note = BardItems.MUSIC_NOTE.get();
        runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, List.of(new ItemStack(note)));
    }
}
