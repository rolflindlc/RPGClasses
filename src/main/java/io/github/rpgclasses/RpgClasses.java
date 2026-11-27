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
    public static final String MODID = "rites_and_paths";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RpgClasses() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, io.github.rpgclasses.config.ClientConfig.SPEC);
        modBus.addListener(this::commonSetup);
        modBus.addListener(PlayerClassDataProvider::registerCapability);
        ModAttributes.ATTRIBUTES.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        io.github.rpgclasses.difficulty.ThreatPotions.POTIONS.register(modBus);
        CombatAbilities.register();
        MonkAbilities.register();
        MonkSubclasses.register();
        RogueAbilities.register();
        io.github.rpgclasses.content.Chronomancy.register();
        ModEntities.ENTITIES.register(modBus);
        io.github.rpgclasses.registry.ModMenus.MENUS.register(modBus);
        io.github.rpgclasses.registry.ModBlocks.BLOCKS.register(modBus);
        io.github.rpgclasses.registry.ModBlocks.ITEMS.register(modBus);
        io.github.rpgclasses.gear.ModGear.ITEMS.register(modBus);
        io.github.rpgclasses.registry.ClassBooks.register(modBus);
        io.github.rpgclasses.gear.ModGear.TABS.register(modBus);
        // Bardo (adenda del Bardo): objetos, receta de partituras, efectos y entidades.
        io.github.rpgclasses.bard.BardItems.ITEMS.register(modBus);
        io.github.rpgclasses.bard.BardItems.RECIPES.register(modBus);
        io.github.rpgclasses.bard.BardEffects.EFFECTS.register(modBus);
        io.github.rpgclasses.bard.BardEntities.ENTITIES.register(modBus);
        io.github.rpgclasses.server.AbilityService.register(io.github.rpgclasses.bard.Bard.CRESCENDO,
                io.github.rpgclasses.bard.BardState::startCrescendo);
        modBus.addListener(io.github.rpgclasses.registry.ModBlocks::onCreativeTab);
        modBus.addListener(ModEntities::onAttributes);
        modBus.addListener(ModAttributes::onAttributeModification);

        // Todo lo de cliente se registra aparte: el servidor dedicado nunca carga esas clases.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.init(modBus));

        io.github.rpgclasses.server.DebugEvents.install();
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.server.DebugEvents());
        // Estado en memoria de la partida: se vacía al cerrar el mundo (en un jugador no se arrastra al siguiente).
        MinecraftForge.EVENT_BUS.addListener(io.github.rpgclasses.server.SessionState::onStopped);
        MinecraftForge.EVENT_BUS.register(new ServerEvents());
        MinecraftForge.EVENT_BUS.register(new CombatTracker());
        MinecraftForge.EVENT_BUS.register(new DamagePipeline());
        MinecraftForge.EVENT_BUS.register(new AttributeHandlers());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.server.ClassPoints());
        MinecraftForge.EVENT_BUS.register(new CombatAbilities());
        MinecraftForge.EVENT_BUS.register(new RangerAbilities());
        MinecraftForge.EVENT_BUS.register(new MonkAbilities());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.MonkCharges());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.ClassFx());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.BattleMage());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.VoidMark());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.Chronomancy());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.CreativeAmuletItem.Events());
        MinecraftForge.EVENT_BUS.register(new RogueAbilities());
        MinecraftForge.EVENT_BUS.register(new ShieldWall());
        MinecraftForge.EVENT_BUS.register(MechanicService.listener());
        MinecraftForge.EVENT_BUS.register(new EffectRules());
        MinecraftForge.EVENT_BUS.register(io.github.rpgclasses.server.Allies.listener());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.DeathGamble());
        // Dificultad por zona (adenda de dificultad por zona): se apaga con difficulty.enabled en la config del servidor.
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.difficulty.DifficultyEvents());
        // Puente con Iron's: la clase solo se carga si el mod está instalado.
        if (net.minecraftforge.fml.ModList.get().isLoaded("irons_spellbooks")) {
            io.github.rpgclasses.compat.irons.IronsCompat.init();
            // Escuela musical y conjuros del Bardo (registro en Iron's).
            io.github.rpgclasses.compat.irons.music.MusicBridge.init(modBus);
            // Druida: Ritual del Druida y Forma Sobrenatural.
            io.github.rpgclasses.compat.irons.DruidSpells.init(modBus);
        }
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.bard.BardState());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.bard.BardAuras());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.bard.BardCombat());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.bard.BardZones());
        // Puente con NTGL (Create: Gunsmithing; adenda del Ingeniero): munición que causa cada impacto.
        if (io.github.rpgclasses.compat.ntgl.NtglCompat.loaded()) {
            io.github.rpgclasses.compat.ntgl.NtglCompat.init();
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
        io.github.rpgclasses.content.SniperTools.register(); // Francotirador: Camuflaje y Marca de Mira (rev. 2.22)
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.BondBook());
        MinecraftForge.EVENT_BUS.register(new RoguePerks());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.gear.GearEvents());
        MinecraftForge.EVENT_BUS.register(io.github.rpgclasses.gear.SetEffects.listener());
        if (io.github.rpgclasses.gear.Curio.ENABLED) MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.gear.CurioEffects());
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
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.race.PorterBelt());
        MinecraftForge.EVENT_BUS.register(new io.github.rpgclasses.content.race.SlimeForms());
        MinecraftForge.EVENT_BUS.register(io.github.rpgclasses.content.race.Rites.listener());
        // Clérigo (adenda del Clérigo). Después de las razas: Milagro actúa tras las supervivencias propias (regla 10).
        // Después de las razas: el Milagro es el último salvavidas a prioridad LOWEST (ver Miracle.onDamage).
        io.github.rpgclasses.cleric.Cleric.register();
        // Maestro del Giro (adenda del Maestro del Giro, clase experimental): bolas, bandolera, encantamientos y lógica.
        io.github.rpgclasses.spin.SpinRegistry.register(modBus);
        io.github.rpgclasses.spin.SpinMaster.register();
        // Druida (adenda del Druida): bestiario, transformación, activas, Escultor y su registro.
        io.github.rpgclasses.druid.Druid.register(modBus);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Net::register);
        event.enqueueWork(io.github.rpgclasses.difficulty.ThreatPotions::registerBrewing);
        if (net.minecraftforge.fml.ModList.get().isLoaded("curios")) {
            event.enqueueWork(io.github.rpgclasses.compat.curios.CuriosCompat::registerCurios);
        }
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
