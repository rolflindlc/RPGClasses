package io.github.rpgclasses.cleric;

import io.github.rpgclasses.content.ClassFx;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import io.github.rpgclasses.registry.ModEffects;
import io.github.rpgclasses.server.AbilityService;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Milagro (Clérigo, Nivel Total 30, pasiva).
 * <ul>
 *   <li>Si un aliado (no el Clérigo) iba a morir a ≤10 bloques de un Clérigo con Milagro, queda <b>tumbado</b> 10 s con
 *       medio corazón: inmóvil, sin poder hacer nada, invulnerable (también en PvP) e ignorado por los mobs.</li>
 *   <li>Si otro jugador le cura (conjuro o habilidad), se levanta con lo curado, mínimo el 25 % de su vida, y 2 s de
 *       inmunidad. Su regeneración o sus pociones no le levantan.</li>
 *   <li>Si pasan los 10 s, muere por el flujo normal (pueden actuar la moneda del Bárbaro, la Última Furia o el Tótem).</li>
 *   <li>Recarga de 90 s por aliado, guardada en el aliado (dos Clérigos no encadenan salvaciones).</li>
 *   <li>Orden (regla 10): se comprueba en {@code LivingDamageEvent} con prioridad mínima y se registra después de las
 *       supervivencias propias (Bárbaro, Orco), así que solo actúa si ninguna ha salvado al jugador; el Tótem va después
 *       porque Milagro deja al jugador con vida. No cubre el vacío ni /kill (daño que ignora la invulnerabilidad).</li>
 * </ul>
 */
public final class Miracle {
    private record Downed(UUID cleric, long endTick, DamageSource source) {}

    private static final Map<UUID, Downed> DOWNED = new HashMap<>();
    private static final Map<UUID, Long> IMMUNE_UNTIL = new HashMap<>();
    private static final String ALLY_CD = "rites_and_paths:miracle_ally";
    private static final UUID FREEZE_ID = UUID.fromString("4e2a9c71-3b5d-4f08-a6e1-9d7c2b0f5a36");
    /** Mientras se le aplica la muerte al acabar el tiempo: Milagro no vuelve a saltar. */
    private static boolean expiring;

    public Miracle() {}

    public static boolean isDowned(Entity e) {
        return e != null && DOWNED.containsKey(e.getUUID());
    }

    /** Inmunidad breve (al levantarse, Sacrificio final). */
    public static void grantImmunity(LivingEntity e, int ticks) {
        IMMUNE_UNTIL.merge(e.getUUID(), e.level().getGameTime() + ticks, Math::max);
    }

    private static boolean immune(Entity e) {
        Long until = IMMUNE_UNTIL.get(e.getUUID());
        if (until == null) return false;
        if (e.level().getGameTime() < until) return true;
        IMMUNE_UNTIL.remove(e.getUUID());
        return false;
    }

    // ------------------------------------------------------------------ caer

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDamage(LivingDamageEvent event) {
        if (expiring || !(event.getEntity() instanceof ServerPlayer victim) || victim instanceof FakePlayer) return;
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || event.getAmount() < victim.getHealth()) return;
        if (isDowned(victim)) { // seguro: nada le mata mientras está tumbado
            event.setAmount(0);
            return;
        }
        if (AbilityService.remainingTicks(victim, ALLY_CD) > 0) return;
        ServerPlayer cleric = clericFor(victim);
        if (cleric == null) return;
        event.setAmount(Math.max(0, victim.getHealth() - 1));
        down(victim, cleric, source);
    }

    /** El Clérigo con Milagro más cercano que tiene a la víctima como aliado (ni ella misma, ni tumbado). */
    private static ServerPlayer clericFor(ServerPlayer victim) {
        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player other : victim.level().players()) {
            if (other == victim || !(other instanceof ServerPlayer cleric) || !cleric.isAlive() || cleric.isSpectator() || isDowned(cleric)) continue;
            if (!AbilityService.has(cleric, Cleric.MIRACLE)) continue;
            double range = Cleric.number(Cleric.MIRACLE, "range", 10);
            double d = cleric.distanceToSqr(victim);
            if (d > range * range || !Cleric.ally(cleric, victim)) continue;
            if (d < bestDist) {
                bestDist = d;
                best = cleric;
            }
        }
        return best;
    }

    private static void down(ServerPlayer victim, ServerPlayer cleric, DamageSource source) {
        int ticks = (int) Math.round(Cleric.number(Cleric.MIRACLE, "downed_seconds", 10) * 20);
        long now = victim.level().getGameTime();
        DOWNED.put(victim.getUUID(), new Downed(cleric.getUUID(), now + ticks, source));
        AbilityService.setCooldown(victim, ALLY_CD, (int) Math.round(Cleric.number(Cleric.MIRACLE, "ally_cooldown_seconds", 90) * 20));
        victim.addEffect(new MobEffectInstance(ModEffects.DOWNED.get(), ticks, 0, false, false, true));
        AttributeInstance speed = victim.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(FREEZE_ID) == null) {
            speed.addTransientModifier(new AttributeModifier(FREEZE_ID, "rites_and_paths_cleric:downed", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        victim.stopUsingItem();
        victim.clearFire();
        victim.setDeltaMovement(0, Math.min(0, victim.getDeltaMovement().y), 0);
        victim.hurtMarked = true;
        dropAggro(victim);
        sync(victim, ticks);
        ServerLevel level = victim.serverLevel();
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.4f);
        level.sendParticles(ParticleTypes.END_ROD, victim.getX(), victim.getY() + 0.5, victim.getZ(), 30, 0.4, 0.4, 0.4, 0.08);
        victim.displayClientMessage(Component.translatable("message.rites_and_paths.cleric.downed", ticks / 20), true);
        cleric.displayClientMessage(Component.translatable("message.rites_and_paths.cleric.ally_downed", victim.getDisplayName(), ticks / 20), true);
        DebugLog.event(victim, "clérigo", "Milagro: tumbado {} s gracias a {}", ticks / 20, cleric.getGameProfile().getName());
    }

    // ------------------------------------------------------------------ levantarse

    /** Le cura otro jugador: se levanta. Devuelve la curación a aplicar (mínimo el 25 % de su vida máxima). */
    public static float revive(ServerPlayer victim, ServerPlayer healer, float amount) {
        if (DOWNED.remove(victim.getUUID()) == null) return amount;
        clearState(victim);
        float min = (float) (victim.getMaxHealth() * Cleric.number(Cleric.MIRACLE, "revive_min_health", 0.25)) - victim.getHealth();
        grantImmunity(victim, (int) Math.round(Cleric.number(Cleric.MIRACLE, "revive_immunity_seconds", 2) * 20));
        ServerLevel level = victim.serverLevel();
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.5f);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, victim.getX(), victim.getY() + 1, victim.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
        victim.displayClientMessage(Component.translatable("message.rites_and_paths.cleric.revived", healer.getDisplayName()), true);
        healer.displayClientMessage(Component.translatable("message.rites_and_paths.cleric.revived_other", victim.getDisplayName()), true);
        DebugLog.event(victim, "clérigo", "Milagro: levantado por {}", healer.getGameProfile().getName());
        return Math.max(amount, min);
    }

    private static void clearState(ServerPlayer victim) {
        victim.removeEffect(ModEffects.DOWNED.get());
        AttributeInstance speed = victim.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(FREEZE_ID);
        sync(victim, 0);
    }

    private static void sync(ServerPlayer victim, int ticks) {
        Net.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> victim), new Packets.ClericDowned(victim.getId(), ticks));
    }

    /** Los mobs que le persiguen se olvidan de él. */
    private static void dropAggro(ServerPlayer victim) {
        for (Mob mob : victim.level().getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(48), m -> m.getTarget() == victim)) {
            mob.setTarget(null);
        }
    }

    // ------------------------------------------------------------------ cada tick

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || DOWNED.isEmpty()) return;
        MinecraftServer server = event.getServer();
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, Downed> e : DOWNED.entrySet()) {
            ServerPlayer victim = server.getPlayerList().getPlayer(e.getKey());
            if (victim == null || !victim.isAlive()) {
                expired.add(e.getKey());
                continue;
            }
            long now = victim.level().getGameTime();
            if (now >= e.getValue().endTick()) {
                expired.add(e.getKey());
                continue;
            }
            Vec3 v = victim.getDeltaMovement();
            if (v.x != 0 || v.z != 0 || v.y > 0) {
                victim.setDeltaMovement(0, Math.min(0, v.y), 0);
                victim.hurtMarked = true;
            }
            if (now % 10 == 0) dropAggro(victim);
            if (now % 20 == 0) victim.serverLevel().sendParticles(ParticleTypes.END_ROD, victim.getX(), victim.getY() + 0.3, victim.getZ(), 3, 0.5, 0.1, 0.5, 0.01);
        }
        for (UUID id : expired) {
            Downed d = DOWNED.remove(id);
            ServerPlayer victim = server.getPlayerList().getPlayer(id);
            if (victim == null || d == null) continue;
            clearState(victim);
            if (victim.isAlive()) die(victim, d.source());
        }
    }

    /** Se acabó el tiempo: muere por el flujo normal (con el daño que le tumbó). */
    private static void die(ServerPlayer victim, DamageSource source) {
        DebugLog.event(victim, "clérigo", "Milagro: nadie le curó a tiempo");
        expiring = true;
        try {
            victim.invulnerableTime = 0;
            boolean hit = victim.hurt(source, Float.MAX_VALUE);
            if (!hit && victim.isAlive()) victim.hurt(victim.damageSources().generic(), Float.MAX_VALUE);
        } catch (RuntimeException ex) {
            if (victim.isAlive()) victim.hurt(victim.damageSources().generic(), Float.MAX_VALUE);
        } finally {
            expiring = false;
        }
    }

    // ------------------------------------------------------------------ invulnerable, ignorado y sin poder hacer nada

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        Entity attacker = event.getSource().getEntity();
        if (isDowned(attacker) || isDowned(event.getSource().getDirectEntity())) {
            event.setCanceled(true);
            return;
        }
        LivingEntity victim = event.getEntity();
        if ((isDowned(victim) || immune(victim)) && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onChangeTarget(LivingChangeTargetEvent event) {
        if (isDowned(event.getNewTarget())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (DOWNED.remove(p.getUUID()) != null) clearState(p);
        IMMUNE_UNTIL.remove(p.getUUID());
    }

    /** No se puede huir desconectándose: se aplica ya la muerte. */
    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Downed d = DOWNED.remove(p.getUUID());
        if (d != null) {
            clearState(p);
            if (p.isAlive()) die(p, d.source());
        }
        IMMUNE_UNTIL.remove(p.getUUID());
    }

    @SubscribeEvent
    public void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && isDowned(p)) sync(p, (int) Math.max(1, DOWNED.get(p.getUUID()).endTick() - p.level().getGameTime()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttackEntity(AttackEntityEvent event) {
        if (isDowned(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isDowned(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isDowned(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isDowned(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (isDowned(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (isDowned(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (isDowned(event.getEntity())) event.setCanceled(true);
    }

    /** Partículas doradas al caer (las ve todo el mundo). */
    static void fx(ServerPlayer p) {
        p.serverLevel().sendParticles(ClassFx.GOLD, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.5, 0.4, 0);
    }
}
