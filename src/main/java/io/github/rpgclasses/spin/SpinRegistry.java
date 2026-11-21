package io.github.rpgclasses.spin;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.registry.ModEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/** Registro del Maestro del Giro: bolas, bandolera, la entidad de la bola, el bloque de señal y el efecto Giro Dorado. */
public final class SpinRegistry {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RpgClasses.MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, RpgClasses.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, RpgClasses.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, RpgClasses.MODID);

    public static final Map<SphereMaterial, RegistryObject<Item>> SPHERES = new EnumMap<>(SphereMaterial.class);

    static {
        for (SphereMaterial m : SphereMaterial.values()) {
            SPHERES.put(m, ITEMS.register(m.itemName(), () -> {
                Item.Properties p = new Item.Properties().durability(m.durability);
                if (m.fireResistant) p.fireResistant();
                return new SphereItem(m, p);
            }));
        }
    }

    public static final RegistryObject<Item> BANDOLIER = ITEMS.register("sphere_bandolier",
            () -> new BandolierItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Block> SIGNAL = BLOCKS.register("sphere_signal", () -> new SphereSignalBlock(
            BlockBehaviour.Properties.of().noCollission().noOcclusion().instabreak().noLootTable().replaceable()
                    .pushReaction(PushReaction.DESTROY)));

    public static final RegistryObject<EntityType<SphereEntity>> SPHERE_ENTITY = ENTITIES.register("spin_sphere",
            () -> EntityType.Builder.<SphereEntity>of(SphereEntity::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f).noSave().fireImmune().clientTrackingRange(6).updateInterval(1)
                    .setShouldReceiveVelocityUpdates(true)
                    .build(RpgClasses.MODID + ":spin_sphere"));

    /** Giro Dorado (fase 1 del Rectángulo Dorado): solo icono y tiempo; la lógica está en {@link SpinState}. */
    public static final RegistryObject<MobEffect> GOLDEN_SPIN = EFFECTS.register("golden_spin",
            () -> new ModEffects.StateEffect(MobEffectCategory.BENEFICIAL, 0xF2C230));

    private SpinRegistry() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
        SpinEnchantments.ENCHANTMENTS.register(modBus);
    }

    public static Item sphere(SphereMaterial m) {
        return SPHERES.get(m).get();
    }

    /** Para la pestaña de creativo: la bandolera llena de hierro y una bola de cada material. */
    public static void creativeItems(Consumer<ItemStack> out) {
        ItemStack bandolier = new ItemStack(BANDOLIER.get());
        out.accept(bandolier.copy());
        for (int i = 0; i < 3; i++) Bandolier.add(bandolier, new ItemStack(sphere(SphereMaterial.IRON)));
        out.accept(bandolier);
        for (SphereMaterial m : SphereMaterial.values()) out.accept(new ItemStack(sphere(m)));
    }
}
