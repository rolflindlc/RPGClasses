package io.github.rpgclasses.cleric;

import com.google.gson.JsonObject;
import io.github.rpgclasses.content.race.Races;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.Allies;
import io.github.rpgclasses.server.DebugLog;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Santuario (Clérigo, Nivel Total 15). Se mantiene la tecla: una esfera sagrada se expande hasta 3 bloques en 0,5 s y
 * sigue al Clérigo (con la Plegaria, al aliado por el que reza: el ángel está a su lado).
 * <ul>
 *   <li><b>Barrera:</b> empuja fuera a los enemigos (no a los jefes) y desvía los proyectiles que no son de aliados.</li>
 *   <li><b>Vida compartida:</b> 50 × poder sagrado; lo que llegue a quien esté dentro (aliado del Clérigo) resta primero
 *       de la vida del escudo. Regenera 5/s mientras se canaliza. Si llega a 0, se rompe.</li>
 *   <li><b>Duración:</b> mientras se canaliza (máx. 6 s) y, al soltar, la mitad del tiempo canalizado.</li>
 *   <li><b>Coste:</b> 28 de maná por segundo mientras se canaliza (sin maná, se suelta sola). Canalizando: −50 % de
 *       velocidad y sin conjuros. El cooldown (40 s) empieza al terminar.</li>
 * </ul>
 */
public final class Sanctuary {
    private static final class Zone {
        final UUID owner;
        final int id;
        final long start;
        final int expandTicks, maxChannelTicks;
        final double radius, maxShield, regenPerTick, manaPerTick, lingerFactor;
        boolean channeling = true;
        long end = Long.MAX_VALUE;
        double shield;

        Zone(UUID owner, int id, long start, JsonObject p, double power) {
            this.owner = owner;
            this.id = id;
            this.start = start;
            this.expandTicks = Math.max(1, AbilityService.ticks(p, "expand_seconds", 0.5));
            this.maxChannelTicks = Math.max(1, AbilityService.ticks(p, "max_channel_seconds", 6));
            this.radius = AbilityService.number(p, "radius", 3);
            this.maxShield = AbilityService.number(p, "shield_health", 50) * power;
            this.regenPerTick = AbilityService.number(p, "shield_regen_per_second", 5) * power / 20.0;
            this.manaPerTick = AbilityService.number(p, "channel_mana_per_second", 28) / 20.0;
            this.lingerFactor = AbilityService.number(p, "channel_linger", 0.5);
            this.shield = maxShield;
        }

        double radiusAt(long now) {
            return radius * Math.min(1.0, (now - start + 1) / (double) expandTicks);
        }
    }

    private static final Map<UUID, Zone> ZONES = new HashMap<>();
    private static int nextId = 1;
    private static final UUID SLOW_ID = UUID.fromString("c3f81e5a-7a2d-4b96-8e14-5d0b9a6c2e71");

    public Sanctuary() {}

    public static boolean channeling(ServerPlayer player) {
        Zone z = ZONES.get(player.getUUID());
        return z != null && z.channeling;
    }

    static boolean activate(ServerPlayer player, JsonObject p) {
        if (ZONES.containsKey(player.getUUID())) return false;
        var bridge = io.github.rpgclasses.content.Chronomancy.mana;
        double perSecond = AbilityService.number(p, "channel_mana_per_second", 28);
        if (bridge != null && !player.isCreative() && bridge.mana(player) < perSecond * 0.25) {
            player.displayClientMessage(Component.translatable("message.rites_and_paths.cleric.no_mana"), true);
            return false;
        }
        long now = player.level().getGameTime();
        Zone z = new Zone(player.getUUID(), nextId++, now, p, Cleric.holyPower(player));
        ZONES.put(player.getUUID(), z);
        Cleric.setModifier(player, Attributes.MOVEMENT_SPEED, SLOW_ID, "rites_and_paths_cleric:sanctuary",
                AbilityService.number(p, "channel_speed", -0.5), AttributeModifier.Operation.MULTIPLY_TOTAL);
        Entity anchor = anchor(player);
        sync(player, z, anchor, z.maxChannelTicks + (int) Math.round(z.maxChannelTicks * z.lingerFactor));
        player.level().playSound(null, anchor.getX(), anchor.getY(), anchor.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.3f);
        DebugLog.event(player, "clérigo", "Santuario: escudo {} (regenera {}/s)", z.maxShield, z.regenPerTick * 20);
        return true;
    }

    /** Se suelta la tecla (paquete) o se acaba el maná o el tiempo máximo. */
    public static void release(ServerPlayer player) {
        Zone z = ZONES.get(player.getUUID());
        if (z == null || !z.channeling) return;
        long now = player.level().getGameTime();
        z.channeling = false;
        long channeled = Math.max(0, now - z.start);
        z.end = now + Math.round(channeled * z.lingerFactor);
        player.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SLOW_ID);
        sync(player, z, anchor(player), (int) Math.max(1, z.end - now));
    }

    /** Ancla de la esfera: el aliado de la Plegaria (el ángel está con él) o el propio Clérigo. */
    private static Entity anchor(ServerPlayer player) {
        LivingEntity prayed = Prayer.target(player);
        return prayed != null ? prayed : player;
    }

    private static void end(ServerPlayer player, Zone z, boolean broken) {
        ZONES.remove(z.owner);
        if (player == null) return;
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SLOW_ID);
        AbilityService.setCooldown(player, Cleric.SANCTUARY, AbilityService.ticks(Cleric.params(Cleric.SANCTUARY), "cooldown_seconds", 40));
        Net.syncPlayer(player);
        sync(player, z, anchor(player), 0);
        if (broken) {
            player.displayClientMessage(Component.translatable("message.rites_and_paths.cleric.sanctuary_broken"), true);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 1.2f);
        }
    }

    private static void sync(ServerPlayer player, Zone z, Entity anchor, int ticks) {
        Net.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new Packets.SanctuarySync(z.id, anchor.getId(), (float) z.radius, z.expandTicks, ticks));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ZONES.isEmpty()) return;
        MinecraftServer server = event.getServer();
        for (Zone z : new ArrayList<>(ZONES.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(z.owner);
            if (player == null || !player.isAlive() || Miracle.isDowned(player)) {
                end(player, z, false);
                continue;
            }
            long now = player.level().getGameTime();
            if (z.channeling) {
                var bridge = io.github.rpgclasses.content.Chronomancy.mana;
                if (bridge != null && !player.isCreative()) {
                    float mana = bridge.mana(player);
                    if (mana < z.manaPerTick) release(player);
                    else bridge.setMana(player, (float) (mana - z.manaPerTick));
                }
                if (z.channeling && now - z.start >= z.maxChannelTicks) release(player);
                if (z.channeling) z.shield = Math.min(z.maxShield, z.shield + z.regenPerTick);
            }
            if (!z.channeling && now >= z.end) {
                end(player, z, false);
                continue;
            }
            Entity anchor = anchor(player);
            if (anchor.level() != player.level()) {
                end(player, z, false);
                continue;
            }
            barrier(player, z, anchor, now);
        }
    }

    /** Empuja fuera a los enemigos y desvía sus proyectiles. */
    private static void barrier(ServerPlayer player, Zone z, Entity anchor, long now) {
        ServerLevel level = player.serverLevel();
        double r = z.radiusAt(now);
        Vec3 c = anchor.position().add(0, anchor.getBbHeight() / 2, 0);
        AABB box = new AABB(c, c).inflate(r + 2);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != player)) {
            if (!Allies.isEnemy(player, e) || Races.isBoss(e)) continue;
            Vec3 ec = e.position().add(0, e.getBbHeight() / 2, 0);
            Vec3 d = ec.subtract(c);
            double dist = d.length();
            double reach = r + e.getBbWidth() / 2;
            if (dist >= reach) continue;
            Vec3 dir = dist < 1.0E-3 ? new Vec3(1, 0, 0) : d.scale(1 / dist);
            Vec3 flat = new Vec3(dir.x, 0, dir.z);
            if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(1, 0, 0);
            flat = flat.normalize();
            double push = 0.35 + (reach - dist) * 0.35;
            e.setDeltaMovement(flat.x * push, Math.max(0.15, e.getDeltaMovement().y), flat.z * push);
            e.hurtMarked = true;
            e.hasImpulse = true;
        }
        for (Projectile proj : level.getEntitiesOfClass(Projectile.class, box)) {
            Entity owner = proj.getOwner();
            if (owner != null && Allies.isAlly(player, owner)) continue;
            Vec3 pos = proj.position();
            Vec3 v = proj.getDeltaMovement();
            Vec3 next = pos.add(v);
            boolean inside = pos.distanceTo(c) < r;
            boolean entering = next.distanceTo(c) < r;
            if (!inside && !entering) continue;
            Vec3 n = pos.subtract(c);
            n = n.lengthSqr() < 1.0E-4 ? v.scale(-1).normalize() : n.normalize();
            double dot = v.dot(n);
            if (dot < 0) v = v.subtract(n.scale(2 * dot)); // refleja la parte que entra
            proj.setPos(c.add(n.scale(r + 0.3)));
            proj.setDeltaMovement(v.scale(0.6));
            proj.hasImpulse = true;
            proj.hurtMarked = true;
            level.sendParticles(ParticleTypes.END_ROD, proj.getX(), proj.getY(), proj.getZ(), 3, 0.05, 0.05, 0.05, 0.02);
        }
        if (now % 8 == 0) {
            for (int i = 0; i < 6; i++) {
                double a = (now / 8.0 + i) * Math.PI / 3;
                level.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(a) * r, c.y + Math.sin(now * 0.1 + i) * r * 0.6, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
        }
    }

    /** La vida del escudo absorbe primero el daño de quien está dentro (aliados del Clérigo). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onDamage(LivingDamageEvent event) {
        if (ZONES.isEmpty() || event.getAmount() <= 0) return;
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        if (victim.level().isClientSide() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || source.is(DamageTypes.FALL) || source.is(DamageTypes.STARVE) || source.is(DamageTypes.DROWN)
                || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FLY_INTO_WALL)) return;
        MinecraftServer server = victim.getServer();
        if (server == null) return;
        for (Zone z : new ArrayList<>(ZONES.values())) {
            ServerPlayer owner = server.getPlayerList().getPlayer(z.owner);
            if (owner == null || owner.level() != victim.level() || !Cleric.ally(owner, victim)) continue;
            Entity attacker = source.getEntity();
            if (attacker != null && attacker != victim && Allies.isAlly(owner, attacker)) continue;
            Entity anchor = anchor(owner);
            double r = z.radiusAt(victim.level().getGameTime());
            Vec3 c = anchor.position().add(0, anchor.getBbHeight() / 2, 0);
            if (victim.position().add(0, victim.getBbHeight() / 2, 0).distanceTo(c) > r + victim.getBbWidth() / 2) continue;
            float absorbed = (float) Math.min(z.shield, event.getAmount());
            z.shield -= absorbed;
            event.setAmount(event.getAmount() - absorbed);
            if (victim.level() instanceof ServerLevel sl) {
                sl.sendParticles(io.github.rpgclasses.content.ClassFx.GOLD, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 6, 0.3, 0.4, 0.3, 0);
            }
            if (z.shield <= 1.0E-3) end(owner, z, true);
            if (event.getAmount() <= 0) return;
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Zone z = ZONES.get(p.getUUID());
            if (z != null) end(p, z, false);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Zone z = ZONES.get(p.getUUID());
            if (z != null) end(p, z, false);
        }
    }

    @SubscribeEvent
    public void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Zone z = ZONES.get(p.getUUID());
            if (z != null) end(p, z, false);
        }
    }

    /** Lista de zonas activas (depuración). */
    public static List<String> describe() {
        List<String> out = new ArrayList<>();
        for (Zone z : ZONES.values()) out.add(z.owner + " escudo " + z.shield + "/" + z.maxShield + (z.channeling ? " canalizando" : ""));
        return out;
    }

    static Iterator<UUID> owners() {
        return ZONES.keySet().iterator();
    }
}
