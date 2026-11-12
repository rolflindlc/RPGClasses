package io.github.rpgclasses.content.race;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.server.DebugLog;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Botín de bloques que cambian las razas (modificador global de botín):
 * Fortuna racial del Enano (no se suma al encantamiento: cuenta el mayor; nunca con Toque de Seda),
 * Cosecha Abundante del Elfo Silvano y fundición automática del Infernal Fundidor.
 */
public final class RaceLootModifier extends LootModifier {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, RpgClasses.MODID);
    public static final Supplier<Codec<RaceLootModifier>> CODEC = Suppliers.memoize(
            () -> RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, RaceLootModifier::new)));
    public static final RegistryObject<Codec<RaceLootModifier>> RACE_LOOT = SERIALIZERS.register("race_loot", CODEC);

    /** Evita que la tirada repetida con Fortuna vuelva a entrar aquí. */
    private static final ThreadLocal<Boolean> REROLLING = ThreadLocal.withInitial(() -> false);

    public RaceLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        if (REROLLING.get()) return loot;
        BlockState state = ctx.getParamOrNull(LootContextParams.BLOCK_STATE);
        Entity entity = ctx.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (state == null || !(entity instanceof ServerPlayer player) || player instanceof FakePlayer) return loot;
        ItemStack tool = ctx.getParamOrNull(LootContextParams.TOOL);
        Vec3 origin = ctx.getParamOrNull(LootContextParams.ORIGIN);
        boolean silk = tool != null && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) > 0;
        boolean ore = state.is(Tags.Blocks.ORES);
        try {
            // Enano: Fortuna racial en minerales.
            int fortune = ore ? (int) Races.number(player, "race_fortune", "level", 0) : 0;
            if (fortune > 0 && tool != null && !tool.isEmpty() && !silk && origin != null
                    && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool) < fortune) {
                ItemStack boosted = tool.copy();
                Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(boosted);
                enchantments.put(Enchantments.BLOCK_FORTUNE, fortune);
                EnchantmentHelper.setEnchantments(enchantments, boosted);
                LootParams.Builder params = new LootParams.Builder(ctx.getLevel())
                        .withParameter(LootContextParams.ORIGIN, origin)
                        .withParameter(LootContextParams.TOOL, boosted)
                        .withOptionalParameter(LootContextParams.THIS_ENTITY, entity)
                        .withOptionalParameter(LootContextParams.BLOCK_ENTITY, ctx.getParamOrNull(LootContextParams.BLOCK_ENTITY));
                REROLLING.set(true);
                try {
                    loot = new ObjectArrayList<>(state.getDrops(params));
                } finally {
                    REROLLING.set(false);
                }
            }
            // Elfo Silvano: los cultivos maduros dan más.
            if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                double bonus = Races.number(player, "race_harvest", "bonus", 0);
                if (bonus > 0) {
                    for (ItemStack stack : loot) {
                        double extra = stack.getCount() * bonus;
                        int whole = (int) extra + (player.getRandom().nextDouble() < extra - (int) extra ? 1 : 0);
                        stack.grow(whole);
                    }
                }
            }
            // Infernal Fundidor: los minerales salen fundidos (la Fortuna ya se ha aplicado).
            if (ore && !silk && Races.has(player, "race_auto_smelt")) {
                ObjectArrayList<ItemStack> smelted = new ObjectArrayList<>();
                for (ItemStack stack : loot) {
                    ItemStack result = ctx.getLevel().getRecipeManager()
                            .getRecipeFor(RecipeType.SMELTING, new SimpleContainer(stack.copyWithCount(1)), ctx.getLevel())
                            .map(r -> r.getResultItem(ctx.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
                    smelted.add(result.isEmpty() ? stack : result.copyWithCount(Math.min(64, result.getCount() * stack.getCount())));
                }
                loot = smelted;
            }
        } catch (RuntimeException e) {
            DebugLog.error(player, "raza", e, "fallo al modificar el botín de {}", state);
        }
        return loot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
