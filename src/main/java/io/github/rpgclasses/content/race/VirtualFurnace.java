package io.github.rpgclasses.content.race;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeHooks;

/**
 * Horno, alto horno o ahumador del cinturón del Porteador (adenda 0.7, 4.3). No es un bloque del mundo: la lógica de
 * {@code AbstractFurnaceBlockEntity.serverTick} copiada sobre un contenedor de 3 huecos (entrada, combustible,
 * resultado), que avanza con el tick de su dueño, así que "sigue funcionando mientras camina". Se abre con los menús
 * vanilla ({@code FurnaceMenu} y compañía) y sus 4 valores de progreso. La experiencia se da al dueño al cocinar.
 */
final class VirtualFurnace {
    final RecipeType<? extends AbstractCookingRecipe> type;
    final PorterBelt.BeltStorage items;
    int litTime, litDuration, cookingProgress, cookingTotalTime = 200;
    float xp;
    private Item lastInput = Items.AIR;

    final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> litTime;
                case 1 -> litDuration;
                case 2 -> cookingProgress;
                case 3 -> cookingTotalTime;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int value) {
            switch (i) {
                case 0 -> litTime = value;
                case 1 -> litDuration = value;
                case 2 -> cookingProgress = value;
                case 3 -> cookingTotalTime = value;
                default -> {}
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    VirtualFurnace(RecipeType<? extends AbstractCookingRecipe> type, PorterBelt.BeltStorage items) {
        this.type = type;
        this.items = items;
    }

    /** Un tick de horno. @return true si ha cambiado algo que haya que guardar. */
    boolean tick(Level level) {
        ItemStack input = items.getItem(0);
        if (input.getItem() != lastInput) {
            // Como vanilla: cambiar lo que se cocina reinicia el progreso.
            lastInput = input.getItem();
            cookingProgress = 0;
            cookingTotalTime = totalTime(level);
        }
        boolean wasLit = litTime > 0;
        boolean changed = false;
        if (litTime > 0) litTime--;
        ItemStack fuel = items.getItem(1);
        boolean hasInput = !input.isEmpty();
        if (litTime > 0 || hasInput && !fuel.isEmpty()) {
            AbstractCookingRecipe recipe = hasInput ? find(type, items, level) : null;
            if (litTime <= 0 && canBurn(level, recipe)) {
                litTime = ForgeHooks.getBurnTime(fuel, type);
                litDuration = litTime;
                if (litTime > 0) {
                    changed = true;
                    if (fuel.hasCraftingRemainingItem()) {
                        items.setItem(1, fuel.getCraftingRemainingItem());
                    } else {
                        fuel.shrink(1);
                        if (fuel.isEmpty()) items.setItem(1, ItemStack.EMPTY);
                    }
                }
            }
            if (litTime > 0 && canBurn(level, recipe)) {
                cookingProgress++;
                if (cookingProgress >= cookingTotalTime) {
                    cookingProgress = 0;
                    burn(level, recipe);
                    xp += recipe.getExperience();
                    cookingTotalTime = totalTime(level);
                    changed = true;
                }
            } else {
                cookingProgress = 0;
            }
        } else if (litTime <= 0 && cookingProgress > 0) {
            cookingProgress = Mth.clamp(cookingProgress - 2, 0, cookingTotalTime);
        }
        return changed || wasLit != litTime > 0;
    }

    private boolean canBurn(Level level, AbstractCookingRecipe recipe) {
        if (recipe == null || items.getItem(0).isEmpty()) return false;
        ItemStack result = recipe.assemble(items, level.registryAccess());
        if (result.isEmpty()) return false;
        ItemStack out = items.getItem(2);
        if (out.isEmpty()) return true;
        if (!ItemStack.isSameItemSameTags(out, result)) return false;
        return out.getCount() + result.getCount() <= Math.min(items.getMaxStackSize(), out.getMaxStackSize());
    }

    private void burn(Level level, AbstractCookingRecipe recipe) {
        ItemStack result = recipe.assemble(items, level.registryAccess());
        ItemStack out = items.getItem(2);
        if (out.isEmpty()) items.setItem(2, result.copy());
        else out.grow(result.getCount());
        items.getItem(0).shrink(1);
        items.setChanged();
    }

    private int totalTime(Level level) {
        AbstractCookingRecipe recipe = items.getItem(0).isEmpty() ? null : find(type, items, level);
        return recipe == null ? 200 : recipe.getCookingTime();
    }

    private static <T extends AbstractCookingRecipe> AbstractCookingRecipe find(RecipeType<T> type, Container c, Level level) {
        return level.getRecipeManager().getRecipeFor(type, c, level).orElse(null);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Items", PorterBelt.saveItems(items));
        tag.putInt("Lit", litTime);
        tag.putInt("LitDuration", litDuration);
        tag.putInt("Progress", cookingProgress);
        tag.putInt("Total", cookingTotalTime);
        tag.putFloat("Xp", xp);
        return tag;
    }

    void load(CompoundTag tag) {
        PorterBelt.loadItems(items, tag.getList("Items", 10));
        litTime = tag.getInt("Lit");
        litDuration = tag.getInt("LitDuration");
        cookingProgress = tag.getInt("Progress");
        cookingTotalTime = tag.contains("Total") ? tag.getInt("Total") : 200;
        xp = tag.getFloat("Xp");
        lastInput = items.getItem(0).getItem();
    }
}
