package io.github.ironrituals.registry;

import io.github.ironrituals.IronRituals;
import io.github.ironrituals.block.RitualCoreBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, IronRituals.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<RitualCoreBlockEntity>> RITUAL_CORE = BLOCK_ENTITIES.register("ritual_core",
            () -> BlockEntityType.Builder.of(RitualCoreBlockEntity::new, ModBlocks.RITUAL_CORE.get()).build(null));

    private ModBlockEntities() {}
}
