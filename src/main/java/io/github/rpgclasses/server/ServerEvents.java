package io.github.rpgclasses.server;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.config.ServerConfig;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Ciclo de vida de los datos del jugador y de las definiciones. */
public final class ServerEvents {

    @SubscribeEvent
    public void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player && !(event.getObject() instanceof FakePlayer)) {
            event.addCapability(RpgClasses.id("class_data"), new PlayerClassDataProvider((Player) event.getObject()));
        }
    }

    /** Muerte y regreso del End: el jugador es una entidad nueva y hay que copiarle los datos. */
    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        try {
            PlayerClassData from = PlayerClassDataProvider.get(original);
            PlayerClassData to = PlayerClassDataProvider.get(event.getEntity());
            if (from != null && to != null) {
                to.state.copyFrom(from.state);
                // Al morir se reinician todos los enfriamientos de raza y clase; regresar del End no.
                if (!event.isWasDeath()) to.cooldowns.putAll(from.cooldowns);
                to.counters.putAll(from.counters);
                to.texts.putAll(from.texts);
                to.druid = from.druid.copy(); // Druida: el bestiario no se pierde al morir
            }
            if (event.getEntity() instanceof ServerPlayer player) {
                StatEngine.recompute(player);
                // Regreso del End: la vida se copió antes de tener los bonus de vida máxima.
                if (!event.isWasDeath()) player.setHealth(Math.min(original.getHealth(), player.getMaxHealth()));
            }
        } finally {
            original.invalidateCaps();
        }
    }

    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new DefinitionReloadListener());
    }

    /** Entrada de un jugador (player != null) o /reload (player == null): reenviar definiciones. */
    @SubscribeEvent
    public void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            Net.syncDefinitions(event.getPlayer());
        } else {
            for (ServerPlayer player : event.getPlayerList().getPlayers()) {
                Net.syncDefinitions(player);
                StatEngine.refresh(player);
            }
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        sync(event.getEntity());
        PlayerClassData data = PlayerClassDataProvider.get(event.getEntity());
        if (data != null && data.savedHealth > event.getEntity().getHealth()) {
            event.getEntity().setHealth(Math.min(data.savedHealth, event.getEntity().getMaxHealth()));
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        sync(event.getEntity());
        // Tras morir se reaparece con la vida máxima real (incluidos los bonus de clase).
        if (!event.isEndConquered()) event.getEntity().setHealth(event.getEntity().getMaxHealth());
        // Los enfriamientos se reiniciaron en onClone: el cliente debe enterarse.
        if (!event.isEndConquered() && event.getEntity() instanceof ServerPlayer sp && !(sp instanceof FakePlayer)) {
            io.github.rpgclasses.network.Net.syncPlayer(sp);
        }
    }

    @SubscribeEvent
    public void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        sync(event.getEntity());
    }

    private static void sync(Player player) {
        if (player instanceof ServerPlayer sp && !(player instanceof FakePlayer)) StatEngine.refresh(sp);
    }

    /**
     * Mientras elige raza y clase (la pantalla no se puede cerrar), el jugador ni recibe daño ni actúa sobre el mundo
     * (configurable con protectUnclassed). Sin lo segundo, un cliente modificado podría cerrar la pantalla y jugar
     * invulnerable. Si el servidor no tiene clases (o razas) definidas, no se exige elegirlas.
     */
    public static boolean choosing(Player player) {
        if (!(player instanceof ServerPlayer sp) || sp instanceof FakePlayer || !ServerConfig.PROTECT_UNCLASSED.get()) return false;
        PlayerClassData data = PlayerClassDataProvider.get(sp);
        if (data == null) return false;
        boolean noClass = data.state.classId == null && !io.github.rpgclasses.core.definition.DefinitionStore.SERVER.classIds().isEmpty();
        boolean noRace = data.state.raceId == null && !io.github.rpgclasses.core.definition.RaceStore.SERVER.ids().isEmpty();
        return noClass || noRace;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(LivingAttackEvent event) {
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        // No recibe daño ni lo hace (también con proyectiles o mascotas que lancen a su nombre).
        Player victim = event.getEntity() instanceof Player v ? v : null;
        Player attacker = event.getSource().getEntity() instanceof Player a ? a : null;
        if (choosing(victim) || choosing(attacker)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttackEntity(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (choosing(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (choosing(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickItem(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (choosing(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLeftClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        if (choosing(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onInteractEntity(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (choosing(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onInteractEntitySpecific(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific event) {
        if (choosing(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (choosing(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlace(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (choosing(event.getEntity() instanceof Player p ? p : null)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ClassCommands.register(event.getDispatcher());
        AllyCommands.register(event.getDispatcher());
    }
}
