package io.github.rpgclasses.difficulty;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.registry.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Pociones de Tranquilidad y de Desafío, niveles I a IV (un nivel por poción, porque las de vanilla solo tienen I y II).
 * <ul>
 *   <li>Tranquilidad I: poción rara + aciano. Desafío I: poción rara + carne podrida.</li>
 *   <li>Cada nivel siguiente (hasta IV): la poción del nivel anterior + polvo de piedra luminosa.</li>
 * </ul>
 * Aceptan frasco normal, arrojadiza y persistente (la conversión con pólvora y aliento de dragón es la de vanilla).
 */
public final class ThreatPotions {
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(ForgeRegistries.POTIONS, RpgClasses.MODID);

    public static final int MAX_LEVEL = 4;
    /** 5 minutos [MEDIR]. */
    private static final int DURATION_TICKS = 6000;

    public static final List<RegistryObject<Potion>> TRANQUILITY = new ArrayList<>();
    public static final List<RegistryObject<Potion>> CHALLENGE = new ArrayList<>();

    static {
        for (int level = 1; level <= MAX_LEVEL; level++) {
            int amplifier = level - 1;
            // El mismo nombre en todos los niveles: el objeto se llama igual y el nivel se ve en la descripción del efecto.
            TRANQUILITY.add(POTIONS.register("tranquility_" + level,
                    () -> new Potion("tranquility", new MobEffectInstance(ModEffects.TRANQUILITY.get(), DURATION_TICKS, amplifier))));
            CHALLENGE.add(POTIONS.register("challenge_" + level,
                    () -> new Potion("challenge", new MobEffectInstance(ModEffects.CHALLENGE.get(), DURATION_TICKS, amplifier))));
        }
    }

    private ThreatPotions() {}

    /** Llamar en el arranque común (enqueueWork). */
    public static void registerBrewing() {
        addLadder(TRANQUILITY, Items.CORNFLOWER);
        addLadder(CHALLENGE, Items.ROTTEN_FLESH);
    }

    private static void addLadder(List<RegistryObject<Potion>> ladder, Item base) {
        BrewingRecipeRegistry.addRecipe(new Mix(() -> Potions.AWKWARD, Ingredient.of(base), ladder.get(0)));
        for (int i = 1; i < ladder.size(); i++) {
            BrewingRecipeRegistry.addRecipe(new Mix(ladder.get(i - 1), Ingredient.of(Items.GLOWSTONE_DUST), ladder.get(i)));
        }
    }

    /** Transforma una poción en otra con un ingrediente, conservando el tipo de frasco. */
    private record Mix(Supplier<? extends Potion> from, Ingredient ingredient, Supplier<? extends Potion> to) implements IBrewingRecipe {
        @Override
        public boolean isInput(ItemStack input) {
            return (input.is(Items.POTION) || input.is(Items.SPLASH_POTION) || input.is(Items.LINGERING_POTION))
                    && PotionUtils.getPotion(input) == from.get();
        }

        @Override
        public boolean isIngredient(ItemStack stack) {
            return ingredient.test(stack);
        }

        @Override
        public ItemStack getOutput(ItemStack input, ItemStack stack) {
            if (!isInput(input) || !isIngredient(stack)) return ItemStack.EMPTY;
            return PotionUtils.setPotion(new ItemStack(input.getItem()), to.get());
        }
    }
}
