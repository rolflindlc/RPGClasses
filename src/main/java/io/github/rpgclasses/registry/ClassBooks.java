package io.github.rpgclasses.registry;

import com.google.common.base.Suppliers;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.config.ServerConfig;
import io.github.rpgclasses.core.progression.ProgressionRules;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.server.ClassPoints;
import io.github.rpgclasses.server.DebugLog;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Libros de clase. Sin receta: salen del creativo, de comandos y, si la config lo activa, como botín raro.
 * <ul>
 *   <li><b>Libro de Conocimiento:</b> clic derecho, +1 punto de clase al saldo. Con 40 puntos no se gasta.</li>
 *   <li><b>Libro de Olvido:</b> clic derecho abre una confirmación; al aceptar, reinicio completo (clase y subclase)
 *       sin pérdida, fuera de combate.</li>
 * </ul>
 * El botín se añade con un modificador global ({@link Loot}): en cada tabla donde aparece la manzana de oro encantada,
 * cada tirada de esa reserva puede dar el libro con la misma probabilidad que la manzana × el peso de la config.
 */
public final class ClassBooks {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RpgClasses.MODID);
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, RpgClasses.MODID);

    public static final RegistryObject<Item> KNOWLEDGE_BOOK = ITEMS.register("knowledge_book",
            () -> new KnowledgeBook(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> OBLIVION_BOOK = ITEMS.register("oblivion_book",
            () -> new OblivionBook(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));

    public static final Supplier<Codec<Loot>> LOOT_CODEC = Suppliers.memoize(
            () -> RecordCodecBuilder.create(inst -> LootModifier.codecStart(inst).apply(inst, Loot::new)));
    public static final RegistryObject<Codec<Loot>> CLASS_BOOKS_LOOT = LOOT.register("class_books", LOOT_CODEC);

    private ClassBooks() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        LOOT.register(modBus);
    }

    // ------------------------------------------------------------------ objetos

    static final class KnowledgeBook extends Item {
        KnowledgeBook(Properties p) {
            super(p);
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return true;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            PlayerClassData data = PlayerClassDataProvider.get(sp);
            if (data == null) return InteractionResultHolder.fail(stack);
            int added = ProgressionRules.addPoints(data.state, 1);
            if (added <= 0) {
                sp.displayClientMessage(Component.translatable("msg.rites_and_paths.knowledge_book_max").withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
            if (!sp.getAbilities().instabuild) stack.shrink(1);
            sp.displayClientMessage(Component.translatable("msg.rites_and_paths.knowledge_book_used", data.state.pointBalance)
                    .withStyle(ChatFormatting.GREEN), true);
            level.playSound(null, sp.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 1.3f);
            DebugLog.event(sp, "progreso", "libro de conocimiento: saldo {}", data.state.pointBalance);
            ClassPoints.touch(sp);
            Net.syncProgress(sp);
            return InteractionResultHolder.consume(stack);
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
            tip.add(Component.translatable("tooltip.rites_and_paths.knowledge_book").withStyle(ChatFormatting.GRAY));
        }
    }

    static final class OblivionBook extends Item {
        OblivionBook(Properties p) {
            super(p);
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return true;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            // La confirmación es del cliente; al aceptar manda USE_OBLIVION y el servidor lo valida todo.
            if (level.isClientSide) net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> io.github.rpgclasses.client.OblivionConfirm::open);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
            tip.add(Component.translatable("tooltip.rites_and_paths.oblivion_book").withStyle(ChatFormatting.GRAY));
        }
    }

    // ------------------------------------------------------------------ botín

    /** Por cada tabla, sus reservas con manzana de oro encantada: {tiradas medias, probabilidad por tirada}. */
    private static final Map<LootTable, List<double[]>> APPLE_POOLS = new WeakHashMap<>();
    private static final ResourceLocation NOTCH_APPLE = new ResourceLocation("minecraft", "enchanted_golden_apple");

    public static final class Loot extends LootModifier {
        public Loot(LootItemCondition[] conditions) {
            super(conditions);
        }

        @Override
        public Codec<? extends IGlobalLootModifier> codec() {
            return LOOT_CODEC.get();
        }

        @Override
        protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
            boolean knowledge = ServerConfig.KNOWLEDGE_BOOK_LOOT.get(), oblivion = ServerConfig.OBLIVION_BOOK_LOOT.get();
            if (!knowledge && !oblivion) return loot;
            List<double[]> pools = applePools(ctx);
            if (pools.isEmpty()) return loot;
            RandomSource rnd = ctx.getRandom();
            for (double[] pool : pools) {
                int rolls = (int) Math.floor(pool[0]) + (rnd.nextDouble() < pool[0] - Math.floor(pool[0]) ? 1 : 0);
                for (int i = 0; i < rolls; i++) {
                    if (knowledge && rnd.nextDouble() < pool[1] * ServerConfig.KNOWLEDGE_BOOK_WEIGHT.get()) loot.add(new ItemStack(KNOWLEDGE_BOOK.get()));
                    if (oblivion && rnd.nextDouble() < pool[1] * ServerConfig.OBLIVION_BOOK_WEIGHT.get()) loot.add(new ItemStack(OBLIVION_BOOK.get()));
                }
            }
            return loot;
        }
    }

    private static List<double[]> applePools(LootContext ctx) {
        ResourceLocation id = ctx.getQueriedLootTableId();
        if (id == null || ctx.getLevel().getServer() == null) return List.of();
        LootTable table = ctx.getLevel().getServer().getLootData().getLootTable(id);
        synchronized (APPLE_POOLS) {
            return APPLE_POOLS.computeIfAbsent(table, ClassBooks::scan);
        }
    }

    /**
     * Lee la tabla (serializándola a JSON, sin tocar sus campos privados) y devuelve, por cada reserva que contiene la
     * manzana de oro encantada, sus tiradas medias y la probabilidad de la manzana en cada tirada (su peso / peso total).
     */
    private static List<double[]> scan(LootTable table) {
        try {
            JsonElement json = LootDataType.TABLE.parser().toJsonTree(table);
            if (!json.isJsonObject() || !json.getAsJsonObject().has("pools")) return List.of();
            List<double[]> out = new java.util.ArrayList<>();
            for (JsonElement pe : json.getAsJsonObject().getAsJsonArray("pools")) {
                JsonObject pool = pe.getAsJsonObject();
                JsonArray entries = pool.has("entries") ? pool.getAsJsonArray("entries") : new JsonArray();
                double total = 0, apple = 0;
                for (JsonElement ee : entries) {
                    JsonObject e = ee.getAsJsonObject();
                    double w = e.has("weight") ? e.get("weight").getAsDouble() : 1;
                    total += w;
                    if (e.has("name") && NOTCH_APPLE.toString().equals(e.get("name").getAsString())) apple += w;
                }
                if (apple > 0 && total > 0) out.add(new double[] {rolls(pool.get("rolls")), apple / total});
            }
            return List.copyOf(out);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static double rolls(JsonElement r) {
        if (r == null) return 1;
        if (r.isJsonPrimitive()) return r.getAsDouble();
        JsonObject o = r.getAsJsonObject();
        if (o.has("min") && o.has("max")) return (o.get("min").getAsDouble() + o.get("max").getAsDouble()) / 2;
        if (o.has("value")) return o.get("value").getAsDouble();
        return 1;
    }
}
