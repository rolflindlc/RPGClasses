package io.github.ironrituals.ritual.recipe;

import com.google.gson.JsonObject;
import io.github.ironrituals.registry.ModItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.CraftingHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Componente de un ritual: un ingrediente con cantidad, o una Daga Ritual con un alma concreta.
 * JSON: {@code {"item": "minecraft:gold_ingot", "count": 5}}, {@code {"tag": "minecraft:beds"}},
 * {@code {"ingredient": [ ... ], "count": 1}} o {@code {"soul": {"entities": ["minecraft:pufferfish"]}}}.
 */
public record RitualComponent(Ingredient ingredient, int count, @Nullable SoulRequirement soul) {

    public boolean isSoul() {
        return soul != null;
    }

    public boolean test(ItemStack stack, @Nullable Level level) {
        return soul != null ? soul.test(stack, level) : ingredient.test(stack);
    }

    /** Pilas de ejemplo para la GUI y JEI. */
    public ItemStack[] displayStacks() {
        if (soul != null) return new ItemStack[]{new ItemStack(ModItems.RITUAL_DAGGER.get())};
        return ingredient.getItems();
    }

    public static RitualComponent fromJson(JsonObject json) {
        int count = GsonHelper.getAsInt(json, "count", 1);
        if (json.has("soul")) {
            return new RitualComponent(Ingredient.EMPTY, 1, SoulRequirement.fromJson(GsonHelper.getAsJsonObject(json, "soul")));
        }
        Ingredient ing = json.has("ingredient")
                ? CraftingHelper.getIngredient(json.get("ingredient"), false)
                : CraftingHelper.getIngredient(json, false);
        if (count < 1) throw new IllegalArgumentException("count debe ser >= 1");
        return new RitualComponent(ing, count, null);
    }

    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeBoolean(soul != null);
        if (soul != null) {
            soul.toNetwork(buf);
        } else {
            ingredient.toNetwork(buf);
            buf.writeVarInt(count);
        }
    }

    public static RitualComponent fromNetwork(FriendlyByteBuf buf) {
        if (buf.readBoolean()) return new RitualComponent(Ingredient.EMPTY, 1, SoulRequirement.fromNetwork(buf));
        Ingredient ing = Ingredient.fromNetwork(buf);
        return new RitualComponent(ing, buf.readVarInt(), null);
    }
}
