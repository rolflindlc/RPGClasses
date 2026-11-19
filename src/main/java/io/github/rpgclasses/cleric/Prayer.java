package io.github.rpgclasses.cleric;

import com.google.gson.JsonObject;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.DebugLog;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plegaria (Intercesor, desde el nivel 1 de subclase).
 * <ul>
 *   <li>La tecla abre una lista de aliados (jugadores y mascotas de la misma dimensión, dentro del alcance de carga del servidor),
 *       aunque no se vean. Elegir uno empieza a rezar; con la Plegaria activa la lista permite cambiar de aliado o
 *       dejar de rezar.</li>
 *   <li>El cuerpo se queda quieto y agachado; la cámara del cliente pasa a un ángel que orbita al aliado
 *       ({@code client.cleric.ClericClient}). El ángel está en {@code ojos del aliado − mirada × 3}: el servidor y el
 *       cliente lo calculan igual con la rotación del jugador.</li>
 *   <li>El aliado recibe Regeneración I mientras dura.</li>
 *   <li><b>Conjuros desde el ángel:</b> mientras Iron's ejecuta un conjuro ({@code castSpell}) el cuerpo se coloca en el
 *       ángel y vuelve al terminar (mixin {@code irons.CastSpellMixin}). Los conjuros continuos, que actúan cada tick,
 *       siguen saliendo del cuerpo. Imposición de Manos apunta desde el ángel y el Santuario se centra en el aliado.</li>
 *   <li><b>Poder:</b> 100 % hasta 10 bloques entre el ángel y el cuerpo (+1 por nivel con Voz Lejana) y baja en línea
 *       hasta el mínimo (33 %; 50 % al nivel 10) a 50 bloques. Va en el poder de hechizo (multiplicador total).</li>
 *   <li>Termina al pulsar «dejar de rezar», al morir o desconectarse cualquiera de los dos, si el aliado cambia de
 *       dimensión o sale del alcance. Recibir daño no la corta.</li>
 * </ul>
 */
public final class Prayer {
    private static final Map<UUID, UUID> PRAYING = new HashMap<>();
    private static final UUID FREEZE_ID = UUID.fromString("8d1f4b2c-6e3a-4c79-b05d-3a9e7c1f2d48");
    private static final UUID POWER_ID = UUID.fromString("2b6e9a14-c3d7-4f1e-8a52-7e0c4d9b1f63");
    private static final String POWER_NAME = "rites_and_paths_cleric:prayer";
    /** Posición real del cuerpo mientras Iron's lanza un conjuro desde el ángel. */
    private static final Map<UUID, Vec3> SHIFTED = new HashMap<>();

    public Prayer() {}

    public static boolean praying(ServerPlayer player) {
        return PRAYING.containsKey(player.getUUID());
    }

    /** Aliado por el que reza (o null). */
    public static LivingEntity target(ServerPlayer player) {
        UUID id = PRAYING.get(player.getUUID());
        return id == null ? null : find(player, id);
    }

    /** Aliado (jugador o mascota) por UUID en la dimensión del Clérigo. */
    private static LivingEntity find(ServerPlayer cleric, UUID id) {
        MinecraftServer server = cleric.getServer();
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(id);
        if (p != null) return p;
        return cleric.serverLevel().getEntity(id) instanceof LivingEntity l ? l : null;
    }

    /** Posición del ángel (o null si no reza). */
    public static Vec3 angelPos(ServerPlayer player) {
        LivingEntity t = target(player);
        if (t == null) return null;
        double orbit = Cleric.number(Cleric.PRAYER, "orbit_distance", 3);
        return t.getEyePosition().subtract(player.getLookAngle().scale(orbit));
    }

    /** Alcance de la lista: lo que el servidor carga alrededor del jugador (distancia de visión), menos un chunk. */
    private static double range(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        int chunks = server == null ? 8 : Math.max(2, server.getPlayerList().getViewDistance());
        return (chunks - 1) * 16.0;
    }

    /** Jugadores aliados y mascotas (propias o de aliados) de la misma dimensión y dentro del alcance. */
    private static boolean eligible(ServerPlayer cleric, LivingEntity ally) {
        if (ally == null || ally == cleric || !ally.isAlive() || ally.isRemoved() || ally.level() != cleric.level()) return false;
        if (ally instanceof Player p ? p.isSpectator() : !io.github.rpgclasses.server.Allies.isPet(ally)) return false;
        return Cleric.ally(cleric, ally) && horizontalDistance(cleric, ally) <= range(cleric);
    }

    private static double horizontalDistance(net.minecraft.world.entity.Entity a, net.minecraft.world.entity.Entity b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ------------------------------------------------------------------ tecla y lista

    /** Tecla de la Plegaria: manda la lista de aliados al cliente (no gasta nada). */
    static boolean open(ServerPlayer player, JsonObject p) {
        List<Packets.PrayerList.Entry> entries = new ArrayList<>();
        for (LivingEntity ally : io.github.rpgclasses.server.Allies.alliesAround(player, range(player), false)) {
            if (eligible(player, ally)) {
                entries.add(new Packets.PrayerList.Entry(ally.getUUID(), ally.getDisplayName().getString(), (int) Math.round(ally.distanceTo(player))));
            }
        }
        entries.sort(java.util.Comparator.comparingInt(Packets.PrayerList.Entry::distance));
        UUID current = PRAYING.get(player.getUUID());
        if (entries.isEmpty() && current == null) {
            player.displayClientMessage(Component.translatable("message.rites_and_paths.cleric.prayer_none"), true);
            return false;
        }
        Net.toPlayer(player, new Packets.PrayerList(entries, current));
        return false;
    }

    /** El cliente elige aliado (o null = dejar de rezar). Todo se valida aquí. */
    public static void select(ServerPlayer player, UUID allyId) {
        if (allyId == null) {
            stop(player, null);
            return;
        }
        if (!AbilityService.has(player, Cleric.PRAYER) || !player.isAlive() || Miracle.isDowned(player)) return;
        LivingEntity ally = find(player, allyId);
        if (!eligible(player, ally)) return;
        PRAYING.put(player.getUUID(), allyId);
        Cleric.setModifier(player, Attributes.MOVEMENT_SPEED, FREEZE_ID, "rites_and_paths_cleric:prayer_body", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
        player.stopUsingItem();
        player.setShiftKeyDown(true);
        updatePower(player);
        Net.toPlayer(player, new Packets.PrayerState(ally.getId(), (float) Cleric.number(Cleric.PRAYER, "orbit_distance", 3)));
        player.displayClientMessage(Component.translatable("message.rites_and_paths.cleric.prayer_start", ally.getDisplayName()), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.5f);
        DebugLog.event(player, "clérigo", "Plegaria por {}", ally.getName().getString());
    }

    private static void stop(ServerPlayer player, Component reason) {
        if (PRAYING.remove(player.getUUID()) == null) return;
        restore(player);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(FREEZE_ID);
        var power = Cleric.ironsAttribute("spell_power");
        if (power != null && player.getAttribute(power) != null) player.getAttribute(power).removeModifier(POWER_ID);
        player.setShiftKeyDown(false);
        Net.toPlayer(player, new Packets.PrayerState(-1, 0));
        player.displayClientMessage(reason != null ? reason : Component.translatable("message.rites_and_paths.cleric.prayer_end"), true);
    }

    /** Poder por la distancia entre el ángel y el cuerpo (1 = sin pérdida). */
    public static double powerFactor(ServerPlayer player) {
        Vec3 angel = angelPos(player);
        if (angel == null) return 1;
        JsonObject p = Cleric.params(Cleric.PRAYER);
        ActiveMechanic voice = Cleric.mech(player, Cleric.FAR_VOICE);
        double full = AbilityService.number(p, "full_power_blocks", 10) + (voice == null ? 0 : voice.number("full_power_extra_blocks", 0));
        double minAt = Math.max(full + 1, AbilityService.number(p, "min_power_blocks", 50));
        double min = voice == null ? AbilityService.number(p, "min_power", 0.33) : voice.number("min_power", 0.33);
        Vec3 body = SHIFTED.getOrDefault(player.getUUID(), player.getEyePosition());
        double d = angel.distanceTo(body);
        if (d <= full) return 1;
        double t = Math.min(1, (d - full) / (minAt - full));
        return 1 - t * (1 - min);
    }

    private static void updatePower(ServerPlayer player) {
        var power = Cleric.ironsAttribute("spell_power");
        if (power == null) return;
        Cleric.setModifier(player, power, POWER_ID, POWER_NAME, powerFactor(player) - 1, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    // ------------------------------------------------------------------ conjuros desde el ángel

    /** Iron's va a ejecutar un conjuro (SpellOnCastEvent): el cuerpo pasa al ángel hasta {@link #castEnd}. */
    public static void castStart(ServerPlayer player) {
        Vec3 angel = angelPos(player);
        if (angel == null || SHIFTED.containsKey(player.getUUID())) return;
        SHIFTED.put(player.getUUID(), player.position());
        player.setPos(angel.x, angel.y - player.getEyeHeight(), angel.z);
    }

    /** Fin de {@code castSpell} (mixin) o, si el mixin no está, final del tick: el cuerpo vuelve a su sitio. */
    public static void castEnd() {
        if (SHIFTED.isEmpty()) return;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        for (Map.Entry<UUID, Vec3> e : new ArrayList<>(SHIFTED.entrySet())) {
            ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(e.getKey());
            SHIFTED.remove(e.getKey());
            if (p != null) p.setPos(e.getValue().x, e.getValue().y, e.getValue().z);
        }
    }

    private static void restore(ServerPlayer player) {
        Vec3 body = SHIFTED.remove(player.getUUID());
        if (body != null) player.setPos(body.x, body.y, body.z);
    }

    // ------------------------------------------------------------------ cada tick

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        castEnd();
        if (PRAYING.isEmpty()) return;
        MinecraftServer server = event.getServer();
        for (Map.Entry<UUID, UUID> e : new ArrayList<>(PRAYING.entrySet())) {
            ServerPlayer cleric = server.getPlayerList().getPlayer(e.getKey());
            if (cleric == null) {
                PRAYING.remove(e.getKey());
                continue;
            }
            LivingEntity ally = find(cleric, e.getValue());
            if (!cleric.isAlive() || Miracle.isDowned(cleric) || !AbilityService.has(cleric, Cleric.PRAYER)) {
                stop(cleric, null);
                continue;
            }
            if (ally == null || !eligible(cleric, ally)) {
                stop(cleric, Component.translatable("message.rites_and_paths.cleric.prayer_lost",
                        ally == null ? Component.literal("?") : ally.getDisplayName()));
                continue;
            }
            // El cuerpo, quieto y arrodillado.
            var v = cleric.getDeltaMovement();
            if (v.x != 0 || v.z != 0) {
                cleric.setDeltaMovement(0, Math.min(0, v.y), 0);
                cleric.hurtMarked = true;
            }
            if (!cleric.isShiftKeyDown()) cleric.setShiftKeyDown(true);
            long now = cleric.level().getGameTime();
            if (now % 40 == 0) {
                int level = Math.max(0, (int) Cleric.number(Cleric.PRAYER, "regen_level", 1) - 1);
                ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, level, true, true, true), cleric);
            }
            if (now % 5 == 0) {
                updatePower(cleric);
                ServerLevel sl = cleric.serverLevel();
                Vec3 angel = angelPos(cleric);
                if (angel != null) sl.sendParticles(ParticleTypes.END_ROD, angel.x, angel.y, angel.z, 2, 0.15, 0.15, 0.15, 0.01);
                sl.sendParticles(ParticleTypes.ENCHANT, cleric.getX(), cleric.getY() + 1.4, cleric.getZ(), 3, 0.3, 0.3, 0.3, 0.3);
            }
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (PRAYING.containsKey(p.getUUID())) stop(p, null);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && PRAYING.containsKey(p.getUUID())) stop(p, null);
    }

    @SubscribeEvent
    public void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && PRAYING.containsKey(p.getUUID())) stop(p, null);
    }
}
