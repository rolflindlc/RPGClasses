package io.github.rpgclasses.engineer;

import io.github.rpgclasses.RpgClasses;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registro del Ingeniero: torreta, mina, arma lanzada y el alambre de espino de la Trinchera. */
public final class EngineerRegistry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, RpgClasses.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, RpgClasses.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, RpgClasses.MODID);

    /** Como una telaraña (dureza 4, sin colisión), pero de metal. */
    public static final RegistryObject<Block> BARBED_WIRE = BLOCKS.register("barbed_wire", () -> new BarbedWireBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.CHAIN).forceSolidOn().noCollission()
                    .noOcclusion().strength(4.0f).noLootTable().pushReaction(PushReaction.DESTROY)));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<BarbedWireBlock.Owner>> BARBED_WIRE_ENTITY = BLOCK_ENTITIES.register("barbed_wire",
            () -> BlockEntityType.Builder.of(BarbedWireBlock.Owner::new, BARBED_WIRE.get()).build(null));

    public static final RegistryObject<EntityType<TurretEntity>> TURRET = ENTITIES.register("engineer_turret",
            () -> EntityType.Builder.<TurretEntity>of(TurretEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.1f).clientTrackingRange(10).updateInterval(2)
                    .build(RpgClasses.MODID + ":engineer_turret"));

    public static final RegistryObject<EntityType<MineEntity>> MINE = ENTITIES.register("engineer_mine",
            () -> EntityType.Builder.<MineEntity>of(MineEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.15f).clientTrackingRange(8).updateInterval(4)
                    .build(RpgClasses.MODID + ":engineer_mine"));

    public static final RegistryObject<EntityType<ThrownFirearm>> THROWN_FIREARM = ENTITIES.register("thrown_firearm",
            () -> EntityType.Builder.<ThrownFirearm>of(ThrownFirearm::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f).noSave().clientTrackingRange(6).updateInterval(1)
                    .build(RpgClasses.MODID + ":thrown_firearm"));

    private EngineerRegistry() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener(EngineerRegistry::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(TURRET.get(), TurretEntity.attributes().build());
    }
}
