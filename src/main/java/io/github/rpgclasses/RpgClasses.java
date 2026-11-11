package io.github.rpgclasses;

import com.mojang.logging.LogUtils;
import io.github.rpgclasses.client.ClientSetup;
import io.github.rpgclasses.config.ServerConfig;
import io.github.rpgclasses.content.CombatAbilities;
import io.github.rpgclasses.content.MonkAbilities;
import io.github.rpgclasses.content.RangerAbilities;
import io.github.rpgclasses.content.RogueAbilities;
import io.github.rpgclasses.registry.ModEntities;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.registry.ModAttributes;
import io.github.rpgclasses.registry.ModEffects;
import io.github.rpgclasses.server.AttributeHandlers;
import io.github.rpgclasses.server.CombatTracker;
import io.github.rpgclasses.server.DamagePipeline;
import io.github.rpgclasses.server.ServerEvents;
import io.github.rpgclasses.server.ShieldWall;
import io.github.rpgclasses.server.MechanicService;
import io.github.rpgclasses.server.EffectRules;
import io.github.rpgclasses.content.Toxins;
import io.github.rpgclasses.content.BarbarianPerks;
import io.github.rpgclasses.content.PaladinPerks;
import io.github.rpgclasses.content.BannerAura;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
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
        ModAttributes.ATTRIBUTES.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        CombatAbilities.register();
        MonkAbilities.register();
        RogueAbilities.register();
        ModEntities.ENTITIES.register(modBus);
        modBus.addListener(ModEntities::onAttributes);
        modBus.addListener(ModAttributes::onAttributeModification);

        // Todo lo de cliente se registra aparte: el servidor dedicado nunca carga esas clases.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.init(modBus));

        MinecraftForge.EVENT_BUS.register(new ServerEvents());
        MinecraftForge.EVENT_BUS.register(new CombatTracker());
        MinecraftForge.EVENT_BUS.register(new DamagePipeline());
        MinecraftForge.EVENT_BUS.register(new AttributeHandlers());
        MinecraftForge.EVENT_BUS.register(new CombatAbilities());
        MinecraftForge.EVENT_BUS.register(new RangerAbilities());
        MinecraftForge.EVENT_BUS.register(new MonkAbilities());
        MinecraftForge.EVENT_BUS.register(new RogueAbilities());
        MinecraftForge.EVENT_BUS.register(new ShieldWall());
        MinecraftForge.EVENT_BUS.register(MechanicService.listener());
        MinecraftForge.EVENT_BUS.register(new EffectRules());
        MinecraftForge.EVENT_BUS.register(new Toxins());
        MinecraftForge.EVENT_BUS.register(new BarbarianPerks());
        MinecraftForge.EVENT_BUS.register(new PaladinPerks());
        MinecraftForge.EVENT_BUS.register(new BannerAura());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Net::register);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
