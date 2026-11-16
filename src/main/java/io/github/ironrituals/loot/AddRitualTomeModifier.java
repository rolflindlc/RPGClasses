package io.github.ironrituals.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.ironrituals.config.ServerConfig;
import io.github.ironrituals.item.RitualTomeItem;
import io.github.ironrituals.registry.ModRecipes;
import io.github.ironrituals.ritual.recipe.RitualRecipe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Añade un grimorio de ritual a cofres de estructuras de ISS (plan, 3.D). Elige al azar entre los rituales de la
 * rareza indicada que haya en los datapacks, de modo que los rituales añadidos por otros packs también aparecen.
 */
public class AddRitualTomeModifier extends LootModifier {
    public static final Supplier<Codec<AddRitualTomeModifier>> CODEC = Suppliers.memoize(() -> RecordCodecBuilder.create(inst ->
            codecStart(inst)
                    .and(Codec.STRING.fieldOf("rarity").forGetter(m -> m.rarity))
                    .and(Codec.FLOAT.fieldOf("chance").forGetter(m -> m.chance))
                    .apply(inst, AddRitualTomeModifier::new)));

    private final String rarity;
    private final float chance;

    public AddRitualTomeModifier(LootItemCondition[] conditions, String rarity, float chance) {
        super(conditions);
        this.rarity = rarity;
        this.chance = chance;
    }

    @NotNull
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        float p = (float) (chance * ServerConfig.TOME_LOOT_CHANCE_MULTIPLIER.get());
        if (ctx.getRandom().nextFloat() >= p) return loot;
        RitualRecipe.Rarity wanted = RitualRecipe.Rarity.valueOf(rarity.toUpperCase(Locale.ROOT));
        List<RitualRecipe> pool = ctx.getLevel().getRecipeManager().getAllRecipesFor(ModRecipes.RITUAL_TYPE.get())
                .stream().filter(r -> r.rarity() == wanted).toList();
        if (!pool.isEmpty()) loot.add(RitualTomeItem.create(pool.get(ctx.getRandom().nextInt(pool.size())).ritual()));
        return loot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
