package io.github.ironrituals;

import com.mojang.logging.LogUtils;
import io.github.ironrituals.config.ServerConfig;
import io.github.ironrituals.registry.ModBlockEntities;
import io.github.ironrituals.registry.ModBlocks;
import io.github.ironrituals.registry.ModCreativeTabs;
import io.github.ironrituals.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(IronRituals.MOD_ID)
public final class IronRituals {
    public static final String MOD_ID = "ironrituals";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IronRituals() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
