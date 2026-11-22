package io.github.rpgclasses.druid;

import io.github.rpgclasses.RpgClasses;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/** Registro del Druida: Libro de Estudio, Recipiente y partes, Altar de Carne, restos, menú del recipiente y efecto. */
public final class DruidRegistry {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RpgClasses.MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, RpgClasses.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, RpgClasses.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, RpgClasses.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, RpgClasses.MODID);

    public static final RegistryObject<Item> STUDY_BOOK = ITEMS.register("study_book", () -> new StudyBookItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> FLESH_VESSEL = ITEMS.register("flesh_vessel", () -> new FleshVesselItem(new Item.Properties().stacksTo(1)));
    public static final List<RegistryObject<Item>> PARTS = List.of(
            ITEMS.register("beast_head", () -> new BeastPartItem(0, new Item.Properties().stacksTo(1))),
            ITEMS.register("beast_torso", () -> new BeastPartItem(1, new Item.Properties().stacksTo(1))),
            ITEMS.register("beast_legs", () -> new BeastPartItem(2, new Item.Properties().stacksTo(1))),
            ITEMS.register("beast_instinct", () -> new BeastPartItem(3, new Item.Properties().stacksTo(1))));

    public static final RegistryObject<Block> FLESH_ALTAR = BLOCKS.register("flesh_altar", () -> new FleshAltarBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(3f, 9f).requiresCorrectToolForDrops()
                    .lightLevel(s -> 4).sound(SoundType.BONE_BLOCK)));
    public static final RegistryObject<Item> FLESH_ALTAR_ITEM = ITEMS.register("flesh_altar", () -> new BlockItem(FLESH_ALTAR.get(), new Item.Properties()));

    public static final RegistryObject<EntityType<BeastRemains>> BEAST_REMAINS = ENTITIES.register("beast_remains",
            () -> EntityType.Builder.<BeastRemains>of(BeastRemains::new, MobCategory.MISC)
                    .sized(1.0f, .5f).noSave().fireImmune().clientTrackingRange(8).updateInterval(20)
                    .build(RpgClasses.MODID + ":beast_remains"));

    public static final RegistryObject<MenuType<VesselMenu>> VESSEL_MENU = MENUS.register("flesh_vessel", () -> IForgeMenuType.create(VesselMenu::new));

    /** Precio de la Carne (Escultor): menos vida máxima durante un rato tras crear una quimera. */
    public static final RegistryObject<MobEffect> FLESH_PRICE = EFFECTS.register("flesh_price", () -> new FleshPriceEffect()
            .addAttributeModifier(Attributes.MAX_HEALTH, "8f3d2c1b-5a6e-4f7d-9c0b-1e2a3b4c5d6e", -.3, AttributeModifier.Operation.MULTIPLY_TOTAL));

    private DruidRegistry() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        ENTITIES.register(modBus);
        MENUS.register(modBus);
        EFFECTS.register(modBus);
        modBus.addListener(DruidRegistry::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(STUDY_BOOK);
            event.accept(FLESH_VESSEL);
        }
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(FLESH_ALTAR_ITEM);
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            for (RegistryObject<Item> part : PARTS) {
                ItemStack s = new ItemStack(part.get());
                s.getOrCreateTag().putString(BeastPartItem.SPECIES, "minecraft:wolf");
                event.accept(s);
            }
        }
    }

    /** Efecto con el porcentaje de vida máxima de los datos del Escultor (no curable con leche). */
    private static final class FleshPriceEffect extends MobEffect {
        FleshPriceEffect() {
            super(MobEffectCategory.HARMFUL, 0x7A1414);
        }

        @Override
        public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
            return -Sculptor.param("health_penalty", .3) * (amplifier + 1);
        }

        @Override
        public List<ItemStack> getCurativeItems() {
            return List.of();
        }
    }
}
