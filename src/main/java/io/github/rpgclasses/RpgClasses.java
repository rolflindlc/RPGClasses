package io.github.rpgclasses;

import com.mojang.logging.LogUtils;
import io.github.rpgclasses.client.ClientSetup;
import io.github.rpgclasses.config.ServerConfig;
import io.github.rpgclasses.content.CombatAbilities;
import io.github.rpgclasses.content.MonkAbilities;
import io.github.rpgclasses.content.MonkSubclasses;
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
import io.github.rpgclasses.content.BarbarianSubclasses;
import io.github.rpgclasses.content.PaladinPerks;
import io.github.rpgclasses.content.PaladinSubclasses;
import io.github.rpgclasses.content.BannerAura;
import io.github.rpgclasses.content.RangerPerks;
import io.github.rpgclasses.content.RangerSubclasses;
import io.github.rpgclasses.content.RoguePerks;
import io.github.rpgclasses.content.MonkPerks;
import io.github.rpgclasses.content.MovementPerks;
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
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, io.github.rpgclasses.config.ClientConfig.SPEC);
        modBus.addListener(this::commonSetup);
        modBus.addListener(PlayerClassDataProvider::registerCapability);
        ModAttributes.ATTRIBUTES.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        CombatAbilities.register();
        MonkAbilities.register();
        MonkSubclasses.register();
        RogueAbilities.register();
        ModEntities.ENTITIES.register(modBus);
        io.github.rpgclasses.registry.ModBlocks.BLOCKS.register(modBus);
        io.github.rpgclasses.registry.ModBlocks.ITEMS.register(modBus);
        io.github.rpgclasses.gear.ModGear.ITEMS.register(modBus);
        io.github.rpgclasses.gear.ModGear.TABS.register(modBus);
        modBus.addListener(io.github.rpgclasses.registry.ModBlocks::onCreativeTab);
        modBus.addListener(ModEntities::onAttributes);
        modBus.addListener(ModAttributes::onAttributeModification);

        // Todo lo de cliente se registra aparte: el servidor dedicado nunca carga esas clases.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.init(modBus));

        io.github.rpgclasses.server.DebugEvents.install();
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.server.DebugEvents());
        MinecraftForge.EVENT_BUS.register(new ServerEvents());
        MinecraftForge.EVENT_BUS.register(new CombatTracker());
        MinecraftForge.EVENT_BUS.register(new DamagePipeline());
        MinecraftForge.EVENT_BUS.register(new AttributeHandlers());
        MinecraftForge.EVENT_BUS.register(new CombatAbilities());
        MinecraftForge.EVENT_BUS.register(new RangerAbilities());
        MinecraftForge.EVENT_BUS.register(new MonkAbilities());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.MonkCharges());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.ClassFx());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.BattleMage());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.VoidMark());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.ChaosMagic());
        MinecraftForge.EVENT_BUS.register(new RogueAbilities());
        MinecraftForge.EVENT_BUS.register(new ShieldWall());
        MinecraftForge.EVENT_BUS.register(MechanicService.listener());
        MinecraftForge.EVENT_BUS.register(new EffectRules());
        MinecraftForge.EVENT_BUS.register(io.github.rpgclasses.server.Allies.listener());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.DeathGamble());
        // Puente con Iron's: la clase solo se carga si el mod está instalado.
        if (net.minecraftforge.fml.ModList.get().isLoaded("irons_spellbooks")) {
            io.github.rpgclasses.compat.irons.IronsCompat.init();
        }
        // Puente con Epic Fight (Acrobacia del Pícaro): solo si el mod está instalado.
        if (net.minecraftforge.fml.ModList.get().isLoaded("epicfight")) {
            io.github.rpgclasses.compat.epicfight.EpicFightCompat.init();
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.initEpicFight());
        }
        MinecraftForge.EVENT_BUS.register(new Toxins());
        MinecraftForge.EVENT_BUS.register(new BarbarianPerks());
        MinecraftForge.EVENT_BUS.register(new BarbarianSubclasses());
        MinecraftForge.EVENT_BUS.register(new PaladinPerks());
        MinecraftForge.EVENT_BUS.register(new PaladinSubclasses());
        MinecraftForge.EVENT_BUS.register(new BannerAura());
        MinecraftForge.EVENT_BUS.register(new RangerPerks());
        MinecraftForge.EVENT_BUS.register(new RangerSubclasses());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.BondBook());
        MinecraftForge.EVENT_BUS.register(new RoguePerks());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.gear.GearEvents());
        MinecraftForge.EVENT_BUS.register(io.github.rpgclasses.gear.SetEffects.listener());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.gear.CurioEffects());
        // Puente con Curios (nudilleras y curios): solo si el mod está instalado.
        if (net.minecraftforge.fml.ModList.get().isLoaded("curios")) {
            io.github.rpgclasses.compat.curios.CuriosCompat.init();
        }
        MinecraftForge.EVENT_BUS.register(new MonkPerks());
        MinecraftForge.EVENT_BUS.register(new MonkSubclasses());
        MinecraftForge.EVENT_BUS.register(new MovementPerks());
        // Razas (anexo de razas): rasgos, subrazas, habilidades raciales y botín.
        io.github.rpgclasses.content.race.RaceLootModifier.SERIALIZERS.register(modBus);
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.race.RaceTraits());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.race.SubraceTraits());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.race.RaceAbilities());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.race.CraftTraits());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.race.WolfRider());
        // Adenda de razas 0.7: Slime, Coloso, Dracónido y Centauro.
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.race.BodyTraits());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.race.DraconicTraits());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.race.DraconicBreath());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.race.SlimeTraits());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.race.CentaurTraits());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.race.ColossusForms());
        MinecraftForge.EVENT_BUS.register(io.github.rpgclasses.content.race.Rites.listener());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Net::register);
        if (net.minecraftforge.fml.ModList.get().isLoaded("curios")) {
            event.enqueueWork(io.github.rpgclasses.compat.curios.CuriosCompat::registerCurios);
        }
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
