package io.github.rpgclasses;

import com.mojang.logging.LogUtils;
import io.github.rpgclasses.config.ServerConfig;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.server.CombatTracker;
import io.github.rpgclasses.server.ServerEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(RpgClasses.MODID)
public class RpgClasses {
    public static final String MODID = "rpgclasses";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RpgClasses() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        modBus.addListener(this::commonSetup);
        modBus.addListener(PlayerClassDataProvider::registerCapability);

        MinecraftForge.EVENT_BUS.register(new ServerEvents());
        MinecraftForge.EVENT_BUS.register(new CombatTracker());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Net::register);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
