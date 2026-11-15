package io.github.ironrituals.registry;

import io.github.ironrituals.IronRituals;
import io.github.ironrituals.block.ArcaneCandleBlock;
import io.github.ironrituals.block.RitualCoreBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, IronRituals.MOD_ID);

    // Cada nivel de desgaste es un bloque propio: picarla conserva su estado (plan, 3.A).
    public static final RegistryObject<Block> ARCANE_CANDLE_STUB = BLOCKS.register("arcane_candle_stub",
            () -> new ArcaneCandleBlock(candleProps(), 1, 3, null));
    public static final RegistryObject<Block> ARCANE_CANDLE_WORN = BLOCKS.register("arcane_candle_worn",
            () -> new ArcaneCandleBlock(candleProps(), 2, 5, ModBlocks.ARCANE_CANDLE_STUB));
    public static final RegistryObject<Block> ARCANE_CANDLE = BLOCKS.register("arcane_candle",
            () -> new ArcaneCandleBlock(candleProps(), 3, 7, ModBlocks.ARCANE_CANDLE_WORN));

    public static final RegistryObject<Block> RITUAL_CORE = BLOCKS.register("ritual_core",
            () -> new RitualCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F, 1200.0F)
                    .lightLevel(s -> 5)
                    .sound(SoundType.AMETHYST)
                    .noOcclusion()));

    private ModBlocks() {}

    private static BlockBehaviour.Properties candleProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .noOcclusion()
                .strength(0.1F)
                .sound(SoundType.CANDLE)
                .lightLevel(ArcaneCandleBlock::lightLevel)
                .pushReaction(PushReaction.DESTROY);
    }
}
