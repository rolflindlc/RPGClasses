package io.github.rpgclasses.cleric;

import com.google.gson.JsonObject;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import io.github.rpgclasses.registry.ModEntities;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.DebugLog;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plegaria (Intercesor, desde el nivel 1 de subclase).
 * <ul>
 *   <li>La tecla abre una lista de aliados (jugadores y mascotas de la misma dimensión, dentro del alcance de carga del servidor),
 *       aunque no se vean. Elegir uno empieza a rezar; con la Plegaria activa la lista permite cambiar de aliado o
 *       dejar de rezar.</li>
 *   <li><b>Cuerpo:</b> donde estaba el Clérigo queda una copia agachada ({@link PrayingBody}) con su aspecto y su equipo.
 *       Su chunk se mantiene cargado. Los golpes que recibe se pasan al jugador, y los mobs que iban a por el jugador van
 *       a por ella.</li>
 *   <li><b>Cabeza:</b> el jugador real pasa junto al aliado como una cabeza flotante y semitransparente (hitbox de
 *       {@code spirit_size} bloques, por defecto 0,9 para caber por huecos de 1). Vuela libremente, pero no puede
 *       alejarse más de {@code spirit_range} bloques (15) del aliado. No recibe daño, no rompe ni usa bloques, no ataca,
 *       no recoge objetos ni cruza portales; sí lanza conjuros y usa objetos. Como el jugador está de verdad ahí, los
 *       conjuros salen de la cabeza sin trucos.</li>
 *   <li>El aliado recibe Regeneración I mientras dura.</li>
 *   <li><b>Poder:</b> 100 % hasta 10 bloques entre la cabeza y el cuerpo (+1 por nivel con Voz Lejana) y baja en línea
 *       hasta el mínimo (33 %; 50 % al nivel 10) a 50 bloques. Va en el poder de hechizo (multiplicador total).</li>
 *   <li>Termina al pulsar «dejar de rezar», al morir o desconectarse, si el aliado cambia de dimensión o sale del alcance
 *       (medido desde el cuerpo) o si el cuerpo desaparece. Al terminar, el jugador vuelve al sitio del cuerpo.</li>
 *   <li>El tamaño se aplica en {@code mixin.PlayerSizeMixin} (todas las poses) y aquí (altura de los ojos). Los clientes lo
 *       saben por {@link Packets.PrayerState}; la cabeza la dibuja {@code client.cleric.ClericClient}.</li>
 * </ul>
 */
public final class Prayer {
    private static final class State {
        UUID ally;
        final ResourceKey<Level> bodyLevel;
        final Vec3 bodyPos;
        final float bodyYaw;
        final UUID bodyId;
        final boolean mayfly;
        final boolean flying;
        final ChunkPos ticket;
        final float size;
        final double range;

        State(ResourceKey<Level> bodyLevel, Vec3 bodyPos, float bodyYaw, UUID bodyId, boolean mayfly, boolean flying,
              ChunkPos ticket, float size, double range) {
            this.bodyLevel = bodyLevel;
            this.bodyPos = bodyPos;
            this.bodyYaw = bodyYaw;
            this.bodyId = bodyId;
            this.mayfly = mayfly;
            this.flying = flying;
            this.ticket = ticket;
            this.size = size;
            this.range = range;
        }
    }

    private static final Map<UUID, State> PRAYING = new HashMap<>();
    /** Cliente: id de entidad de los jugadores que rezan (cabezas) → tamaño. Lo rellena {@link Packets.PrayerState}. */
    public static final Map<Integer, Float> CLIENT_SPIRITS = new ConcurrentHashMap<>();
    private static final UUID POWER_ID = UUID.fromString("2b6e9a14-c3d7-4f1e-8a52-7e0c4d9b1f63");
    private static final String POWER_NAME = "rites_and_paths_cleric:prayer";
    private static final TicketType<ChunkPos> TICKET = TicketType.create("rites_and_paths_prayer", Comparator.comparingLong(ChunkPos::toLong));
    /** Copia en el NBT persistente: si el servidor se cae rezando, al volver a entrar se restaura (sitio y vuelo). */
    private static final String SAVED_TAG = "rites_and_paths_prayer_body";
    /** Distancia a la que aparece la cabeza (detrás del aliado, según la mirada). */
    private static final double START_DISTANCE = 2.0;
    /** Daño que llega al cuerpo y se pasa al jugador: el único que puede herir a la cabeza. */
    private static boolean forwarding;

    public Prayer() {}

    public static boolean praying(ServerPlayer player) {
        return PRAYING.containsKey(player.getUUID());
    }

    /** Aliado por el que reza (o null). */
    public static LivingEntity target(ServerPlayer player) {
        State s = PRAYING.get(player.getUUID());
        return s == null ? null : find(player, s.ally);
    }

    /** Tamaño de la cabeza si el jugador reza (0 si no). Vale en el servidor y en el cliente. */
    public static float spiritSize(Player player) {
        if (player.level().isClientSide) {
            Float f = CLIENT_SPIRITS.get(player.getId());
            return f == null ? 0 : f;
        }
        State s = PRAYING.get(player.getUUID());
        return s == null ? 0 : s.size;
    }

    static boolean isBodyOf(ServerPlayer owner, PrayingBody body) {
        State s = PRAYING.get(owner.getUUID());
        return s != null && s.bodyId.equals(body.getUUID());
    }

    /** Aliado (jugador o mascota) por UUID en la dimensión del Clérigo. */
    private static LivingEntity find(ServerPlayer cleric, UUID id) {
        MinecraftServer server = cleric.getServer();
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(id);
        if (p != null) return p;
        return cleric.serverLevel().getEntity(id) instanceof LivingEntity l ? l : null;
    }

    /** Alcance de la lista: lo que el servidor carga alrededor del jugador (distancia de visión), menos un chunk. */
    private static double range(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        int chunks = server == null ? 8 : Math.max(2, server.getPlayerList().getViewDistance());
        return (chunks - 1) * 16.0;
    }

    /** Desde dónde se mide el alcance: el cuerpo si ya reza, el jugador si no. */
    private static Vec3 origin(ServerPlayer player) {
        State s = PRAYING.get(player.getUUID());
        return s != null ? s.bodyPos : player.position();
    }

    /** Jugadores aliados y mascotas (propias o de aliados) de la misma dimensión y dentro del alcance. */
    private static boolean eligible(ServerPlayer cleric, LivingEntity ally) {
        if (ally == null || ally == cleric || !ally.isAlive() || ally.isRemoved() || ally.level() != cleric.level()) return false;
        if (ally instanceof Player p ? p.isSpectator() : !io.github.rpgclasses.server.Allies.isPet(ally)) return false;
        Vec3 o = origin(cleric);
        double dx = o.x - ally.getX(), dz = o.z - ally.getZ();
        return Cleric.ally(cleric, ally) && Math.sqrt(dx * dx + dz * dz) <= range(cleric);
    }

    // ------------------------------------------------------------------ tecla y lista

    /** Tecla de la Plegaria: manda la lista de aliados al cliente (no gasta nada). */
    static boolean open(ServerPlayer player, JsonObject p) {
        List<Packets.PrayerList.Entry> entries = new ArrayList<>();
        Vec3 o = origin(player);
        double r = range(player);
        List<LivingEntity> candidates = new ArrayList<>(player.serverLevel().players());
        for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(o, o).inflate(r),
                e -> !(e instanceof Player) && io.github.rpgclasses.server.Allies.isPet(e))) {
            candidates.add(e);
        }
        for (LivingEntity ally : candidates) {
            if (eligible(player, ally)) {
                entries.add(new Packets.PrayerList.Entry(ally.getUUID(), ally.getDisplayName().getString(), (int) Math.round(ally.position().distanceTo(o))));
            }
        }
        entries.sort(Comparator.comparingInt(Packets.PrayerList.Entry::distance));
        State current = PRAYING.get(player.getUUID());
        if (entries.isEmpty() && current == null) {
            player.displayClientMessage(Component.translatable("message.rites_and_paths.cleric.prayer_none"), true);
            return false;
        }
        Net.toPlayer(player, new Packets.PrayerList(entries, current == null ? null : current.ally));
        return false;
    }

    /** El cliente elige aliado (o null = dejar de rezar). Todo se valida aquí. */
    public static void select(ServerPlayer player, UUID allyId) {
        if (allyId == null) {
            stop(player, null);
            return;
        }
        if (!AbilityService.has(player, Cleric.PRAYER) || !player.isAlive() || Miracle.isDowned(player) || player.isSpectator()) return;
        LivingEntity ally = find(player, allyId);
        if (!eligible(player, ally)) return;
        State s = PRAYING.get(player.getUUID());
        if (s == null) s = begin(player);
        if (s == null) return;
        s.ally = allyId;
        moveNear(player, ally, s);
        updatePower(player);
        sync(player, s, ally);
        player.displayClientMessage(Component.translatable("message.rites_and_paths.cleric.prayer_start", ally.getDisplayName()), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.5f);
        DebugLog.event(player, "clérigo", "Plegaria por {}", ally.getName().getString());
    }

    /** Deja el cuerpo agachado donde está el jugador y lo convierte en cabeza voladora. */
    private static State begin(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        PrayingBody body = ModEntities.PRAYING_BODY.get().create(level);
        if (body == null) return null;
        player.stopRiding();
        player.stopUsingItem();
        player.setShiftKeyDown(false);
        Vec3 pos = player.position();
        body.moveTo(pos.x, pos.y, pos.z, player.getYRot(), 0);
        body.setYHeadRot(player.getYRot());
        body.setYBodyRot(player.getYRot());
        body.setOwner(player.getUUID());
        body.setCustomName(player.getName());
        body.setScale(io.github.rpgclasses.content.race.RaceTraits.scale(player));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            body.setItemSlot(slot, player.getItemBySlot(slot).copy());
            body.setDropChance(slot, 0f);
        }
        level.addFreshEntity(body);
        ChunkPos chunk = new ChunkPos(player.blockPosition());
        level.getChunkSource().addRegionTicket(TICKET, chunk, 2, chunk);

        JsonObject p = Cleric.params(Cleric.PRAYER);
        float size = (float) Math.max(0.3, AbilityService.number(p, "spirit_size", 0.9));
        double spiritRange = Math.max(2, AbilityService.number(p, "spirit_range", 15));
        var abilities = player.getAbilities();
        State s = new State(level.dimension(), pos, player.getYRot(), body.getUUID(), abilities.mayfly, abilities.flying, chunk, size, spiritRange);
        PRAYING.put(player.getUUID(), s);

        CompoundTag saved = new CompoundTag();
        saved.putString("level", level.dimension().location().toString());
        saved.putDouble("x", pos.x);
        saved.putDouble("y", pos.y);
        saved.putDouble("z", pos.z);
        saved.putBoolean("mayfly", s.mayfly);
        saved.putBoolean("flying", s.flying);
        persisted(player).put(SAVED_TAG, saved);

        // Los enemigos que iban a por el jugador pasan a ir a por el cuerpo.
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32), m -> m.getTarget() == player)) {
            mob.setTarget(body);
        }
        abilities.mayfly = true;
        abilities.flying = true;
        player.onUpdateAbilities();
        player.refreshDimensions();
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1.2, pos.z, 20, 0.3, 0.5, 0.3, 0.05);
        return s;
    }

    /** Coloca la cabeza junto al aliado: algo por encima y detrás de él según la mirada, sin meterla en un bloque. */
    private static void moveNear(ServerPlayer player, LivingEntity ally, State s) {
        Vec3 eye = ally.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 want = eye.add(0, 0.5, 0).subtract(flat.scale(START_DISTANCE));
        BlockHitResult hit = ally.level().clip(new ClipContext(eye, want, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ally));
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 dir = want.subtract(eye);
            want = hit.getLocation().subtract(dir.normalize().scale(s.size * 0.6));
        }
        player.teleportTo(player.serverLevel(), want.x, want.y - s.size / 2, want.z, player.getYRot(), player.getXRot());
        player.resetFallDistance();
    }

    private static void sync(ServerPlayer player, State s, LivingEntity ally) {
        Packets.PrayerState msg = s == null
                ? new Packets.PrayerState(player.getId(), -1, 0, 0)
                : new Packets.PrayerState(player.getId(), ally == null ? -1 : ally.getId(), (float) s.range, s.size);
        Net.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), msg);
    }

    private static void stop(ServerPlayer player, Component reason) {
        State s = PRAYING.remove(player.getUUID());
        if (s == null) return;
        restore(player, s);
        sync(player, null, null);
        player.displayClientMessage(reason != null ? reason : Component.translatable("message.rites_and_paths.cleric.prayer_end"), true);
    }

    /** El jugador vuelve al sitio del cuerpo con su tamaño y su vuelo de antes; el cuerpo desaparece. */
    private static void restore(ServerPlayer player, State s) {
        MinecraftServer server = player.getServer();
        ServerLevel bodyLevel = server == null ? null : server.getLevel(s.bodyLevel);
        if (bodyLevel == null) bodyLevel = player.serverLevel();
        Entity body = bodyLevel.getEntity(s.bodyId);
        Vec3 back = body != null && !body.isRemoved() ? body.position() : s.bodyPos;
        if (body != null) {
            bodyLevel.sendParticles(ParticleTypes.END_ROD, back.x, back.y + 1, back.z, 15, 0.3, 0.5, 0.3, 0.03);
            body.discard();
        }
        bodyLevel.getChunkSource().removeRegionTicket(TICKET, s.ticket, 2, s.ticket);
        var abilities = player.getAbilities();
        boolean free = player.isCreative() || player.isSpectator();
        abilities.mayfly = s.mayfly || free;
        abilities.flying = abilities.mayfly && (s.flying || player.isSpectator());
        player.onUpdateAbilities();
        player.refreshDimensions();
        player.teleportTo(bodyLevel, back.x, back.y, back.z, s.bodyYaw, player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        var power = Cleric.ironsAttribute("spell_power");
        if (power != null && player.getAttribute(power) != null) player.getAttribute(power).removeModifier(POWER_ID);
        persisted(player).remove(SAVED_TAG);
    }

    private static CompoundTag persisted(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG)) data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Golpe al cuerpo: lo recibe el jugador (es el único daño que le llega mientras reza). */
    static boolean hurtBody(ServerPlayer owner, DamageSource source, float amount) {
        if (!PRAYING.containsKey(owner.getUUID())) return false;
        forwarding = true;
        try {
            return owner.hurt(source, amount);
        } finally {
            forwarding = false;
        }
    }

    /** Poder por la distancia entre la cabeza y el cuerpo (1 = sin pérdida). */
    public static double powerFactor(ServerPlayer player) {
        State s = PRAYING.get(player.getUUID());
        if (s == null) return 1;
        JsonObject p = Cleric.params(Cleric.PRAYER);
        ActiveMechanic voice = Cleric.mech(player, Cleric.FAR_VOICE);
        double full = AbilityService.number(p, "full_power_blocks", 10) + (voice == null ? 0 : voice.number("full_power_extra_blocks", 0));
        double minAt = Math.max(full + 1, AbilityService.number(p, "min_power_blocks", 50));
        double min = voice == null ? AbilityService.number(p, "min_power", 0.33) : voice.number("min_power", 0.33);
        double d = player.getEyePosition().distanceTo(s.bodyPos.add(0, 1.27, 0));
        if (d <= full) return 1;
        double t = Math.min(1, (d - full) / (minAt - full));
        return 1 - t * (1 - min);
    }

    private static void updatePower(ServerPlayer player) {
        var power = Cleric.ironsAttribute("spell_power");
        if (power == null) return;
        Cleric.setModifier(player, power, POWER_ID, POWER_NAME, powerFactor(player) - 1, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    // ------------------------------------------------------------------ cada tick

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PRAYING.isEmpty()) return;
        MinecraftServer server = event.getServer();
        for (Map.Entry<UUID, State> e : new ArrayList<>(PRAYING.entrySet())) {
            ServerPlayer cleric = server.getPlayerList().getPlayer(e.getKey());
            State s = e.getValue();
            if (cleric == null) {
                // Sin evento de desconexión (no debería pasar): se recoge el cuerpo y el ticket.
                PRAYING.remove(e.getKey());
                ServerLevel bl = server.getLevel(s.bodyLevel);
                if (bl != null) {
                    Entity body = bl.getEntity(s.bodyId);
                    if (body != null) body.discard();
                    bl.getChunkSource().removeRegionTicket(TICKET, s.ticket, 2, s.ticket);
                }
                continue;
            }
            if (!cleric.isAlive() || Miracle.isDowned(cleric) || !AbilityService.has(cleric, Cleric.PRAYER) || cleric.isSpectator()) {
                stop(cleric, null);
                continue;
            }
            ServerLevel bodyLevel = server.getLevel(s.bodyLevel);
            Entity body = bodyLevel == null ? null : bodyLevel.getEntity(s.bodyId);
            if (body == null || body.isRemoved()) {
                stop(cleric, null);
                continue;
            }
            LivingEntity ally = find(cleric, s.ally);
            if (ally == null || !eligible(cleric, ally)) {
                stop(cleric, Component.translatable("message.rites_and_paths.cleric.prayer_lost",
                        ally == null ? Component.literal("?") : ally.getDisplayName()));
                continue;
            }
            // La cabeza vuela siempre (en supervivencia, tocar el suelo quita el vuelo).
            if (!cleric.getAbilities().flying || !cleric.getAbilities().mayfly) {
                cleric.getAbilities().mayfly = true;
                cleric.getAbilities().flying = true;
                cleric.onUpdateAbilities();
            }
            // Correa: el cliente ya la aplica; aquí solo se corrige si se pasa de largo (lag, el aliado se teletransporta).
            Vec3 center = ally.getEyePosition();
            Vec3 head = cleric.getEyePosition();
            double d = head.distanceTo(center);
            if (d > s.range + 2) {
                Vec3 to = center.add(head.subtract(center).scale((s.range - 0.5) / d));
                cleric.teleportTo(cleric.serverLevel(), to.x, to.y - s.size / 2, to.z, cleric.getYRot(), cleric.getXRot());
            }
            long now = cleric.level().getGameTime();
            if (now % 40 == 0) {
                int level = Math.max(0, (int) Cleric.number(Cleric.PRAYER, "regen_level", 1) - 1);
                ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, level, true, true, true), cleric);
            }
            if (now % 5 == 0) {
                updatePower(cleric);
                cleric.serverLevel().sendParticles(ParticleTypes.END_ROD, head.x, head.y, head.z, 1, 0.2, 0.2, 0.2, 0.01);
                if (bodyLevel != null) bodyLevel.sendParticles(ParticleTypes.ENCHANT, body.getX(), body.getY() + 1.3, body.getZ(), 3, 0.3, 0.3, 0.3, 0.3);
            }
        }
    }

    // ------------------------------------------------------------------ tamaño de la cabeza

    /** La hitbox viene de {@code PlayerSizeMixin}; aquí, los ojos en el centro de la cabeza (después de la raza). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onSize(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player p)) return;
        float size = spiritSize(p);
        if (size <= 0) return;
        event.setNewSize(net.minecraft.world.entity.EntityDimensions.fixed(size, size), false);
        event.setNewEyeHeight(size / 2);
    }

    // ------------------------------------------------------------------ la cabeza no recibe daño ni toca el mundo

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttacked(LivingAttackEvent event) {
        if (forwarding || !(event.getEntity() instanceof ServerPlayer p) || !PRAYING.containsKey(p.getUUID())) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onKnockback(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && PRAYING.containsKey(p.getUUID())) event.setCanceled(true);
    }

    /** Los mobs que van a por la cabeza van a por el cuerpo (si está cerca de ellos) o no van. */
    @SubscribeEvent
    public void onTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof ServerPlayer p)) return;
        State s = PRAYING.get(p.getUUID());
        if (s == null) return;
        LivingEntity mob = event.getEntity();
        Entity body = mob.level() instanceof ServerLevel sl ? sl.getEntity(s.bodyId) : null;
        if (body instanceof LivingEntity lb && lb.isAlive() && lb.distanceToSqr(mob) < 48 * 48) event.setNewTarget(lb);
        else event.setCanceled(true);
    }

    private static boolean prayingPlayer(Player player) {
        return player instanceof ServerPlayer sp ? PRAYING.containsKey(sp.getUUID()) : CLIENT_SPIRITS.containsKey(player.getId());
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (prayingPlayer(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (prayingPlayer(event.getEntity())) event.setCanceled(true);
    }

    /** Ni abre cofres ni coloca bloques; usar el objeto en el aire (pergaminos, libros) sigue funcionando. */
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!prayingPlayer(event.getEntity())) return;
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
    }

    @SubscribeEvent
    public void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (prayingPlayer(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onInteractEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (prayingPlayer(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (prayingPlayer(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onPickup(EntityItemPickupEvent event) {
        if (prayingPlayer(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onPickupXp(PlayerXpEvent.PickupXp event) {
        if (prayingPlayer(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onTravel(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && PRAYING.containsKey(p.getUUID())) event.setCanceled(true);
    }

    // ------------------------------------------------------------------ fin y seguimiento

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        // Antes de soltar el inventario: el jugador vuelve al cuerpo y muere allí.
        if (event.getEntity() instanceof ServerPlayer p && PRAYING.containsKey(p.getUUID())) stop(p, null);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && PRAYING.containsKey(p.getUUID())) stop(p, null);
    }

    /** Si el servidor se cerró de golpe mientras rezaba: vuelve al sitio del cuerpo y recupera su vuelo. */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || PRAYING.containsKey(p.getUUID())) return;
        CompoundTag root = persisted(p);
        if (!root.contains(SAVED_TAG)) return;
        CompoundTag saved = root.getCompound(SAVED_TAG);
        root.remove(SAVED_TAG);
        MinecraftServer server = p.getServer();
        ResourceLocation id = ResourceLocation.tryParse(saved.getString("level"));
        ServerLevel level = server == null || id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        var abilities = p.getAbilities();
        boolean free = p.isCreative() || p.isSpectator();
        abilities.mayfly = saved.getBoolean("mayfly") || free;
        abilities.flying = abilities.mayfly && saved.getBoolean("flying");
        p.onUpdateAbilities();
        p.refreshDimensions();
        if (level != null) p.teleportTo(level, saved.getDouble("x"), saved.getDouble("y"), saved.getDouble("z"), p.getYRot(), p.getXRot());
        p.resetFallDistance();
    }

    @SubscribeEvent
    public void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && PRAYING.containsKey(p.getUUID())) stop(p, null);
    }

    /** Quien empieza a ver a un Clérigo que reza recibe su estado (para dibujarlo como cabeza). */
    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof ServerPlayer cleric) || !(event.getEntity() instanceof ServerPlayer viewer)) return;
        State s = PRAYING.get(cleric.getUUID());
        if (s == null) return;
        LivingEntity ally = find(cleric, s.ally);
        Net.toPlayer(viewer, new Packets.PrayerState(cleric.getId(), ally == null ? -1 : ally.getId(), (float) s.range, s.size));
    }
}
