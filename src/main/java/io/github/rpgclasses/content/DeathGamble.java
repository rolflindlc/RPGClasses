package io.github.rpgclasses.content;

import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.server.DebugLog;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Apuesta con la Muerte (Bárbaro Indestructible, rev. 2.10) como lanzamiento de moneda.
 * <ol>
 *   <li>Golpe letal: el Bárbaro queda a 1 HP, <b>inmortal e inmóvil</b> (no puede atacar, usar objetos, romper ni
 *       lanzar habilidades o conjuros) mientras se lanza una moneda con un <b>anj</b> y una <b>calavera</b>.</li>
 *   <li>Probabilidad del anj: <b>75 %</b> la primera vez de cada vida y <b>50 %</b> las siguientes, sin límite.
 *       El servidor decide el resultado al empezar y el cliente anima la moneda para que caiga en esa cara.</li>
 *   <li>Al terminar la animación se ve la cara ganadora en grande sobre la cabeza y se aplica el resultado:
 *       anj = sobrevive, grito con empuje y <b>1 s de inmortalidad</b>; calavera = recibe de nuevo el golpe letal
 *       (aún pueden salvarle la Última Resistencia del Orco o el Tótem).</li>
 * </ol>
 */
public final class DeathGamble {
    private record Flip(boolean win, long resolveTick, DamageSource source, ActiveMechanic mechanic, int uses) {}

    private static final Map<UUID, Flip> FLIPS = io.github.rpgclasses.server.SessionState.track(new HashMap<>());
    private static final Map<UUID, Long> IMMORTAL_UNTIL = io.github.rpgclasses.server.SessionState.track(new HashMap<>());
    /** Jugadores a los que se está aplicando la calavera: la Apuesta no vuelve a saltar en ese golpe. */
    private static final Set<UUID> RESOLVING = io.github.rpgclasses.server.SessionState.track(new HashSet<>());
    private static final UUID FREEZE_ID = UUID.fromString("6b1f3c55-2f6a-4d0e-9b8e-2a7c1d0c0f31");

    public DeathGamble() {}

    /** ¿Está lanzando la moneda? (inmortal e inmóvil). */
    public static boolean flipping(Player player) {
        return FLIPS.containsKey(player.getUUID());
    }

    /** ¿Se está aplicando una calavera? Entonces la Apuesta no salta. */
    public static boolean resolving(Player player) {
        return RESOLVING.contains(player.getUUID());
    }

    /** Probabilidad de que salga el anj en la tirada número {@code uses} (0 = primera de la vida). */
    public static double chance(ActiveMechanic m, int uses) {
        return uses == 0 ? m.number("first_chance", 0.75) : m.number("chance", 0.5);
    }

    /** Empieza la tirada. El golpe letal ya se ha reducido a dejarle con 1 HP. */
    public static void start(ServerPlayer player, DamageSource source, ActiveMechanic m, PlayerClassData data, String counter) {
        int uses = data.counters.getOrDefault(counter, 0);
        boolean win = player.getRandom().nextDouble() < chance(m, uses);
        data.counters.put(counter, uses + 1);
        int flipTicks = Math.max(10, (int) Math.round(m.number("flip_seconds", 1.6) * 20));
        int showTicks = Math.max(10, (int) Math.round(m.number("result_seconds", 1.5) * 20));
        FLIPS.put(player.getUUID(), new Flip(win, player.level().getGameTime() + flipTicks, source, m, uses));

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(FREEZE_ID) == null) {
            speed.addTransientModifier(new AttributeModifier(FREEZE_ID, "rites_and_paths death gamble",
                    -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        player.stopUsingItem();
        player.setDeltaMovement(0, Math.min(0, player.getDeltaMovement().y), 0);
        player.hurtMarked = true;
        Net.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new Packets.CoinFlip(player.getId(), win, flipTicks, showTicks));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_GOLD,
                SoundSource.PLAYERS, 1.2f, 1.6f);
        DebugLog.event(player, "subclase", "Apuesta con la Muerte: tirada {} al {}% -> {}", uses + 1,
                Math.round(chance(m, uses) * 100), win ? "anj" : "calavera");
    }

    private static void resolve(ServerPlayer player, Flip flip) {
        FLIPS.remove(player.getUUID());
        unfreeze(player);
        if (!player.isAlive()) return;
        if (flip.win()) {
            long until = player.level().getGameTime() + Math.round(flip.mechanic().number("immortal_seconds", 1) * 20);
            IMMORTAL_UNTIL.put(player.getUUID(), until);
            shout(player, flip.mechanic());
            String next = String.format(Locale.ROOT, "%.0f%%", chance(flip.mechanic(), flip.uses() + 1) * 100);
            player.displayClientMessage(Component.translatable("message.rites_and_paths.death_gamble", next), true);
        } else {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_SKELETON_DEATH,
                    SoundSource.PLAYERS, 1.0f, 0.7f);
            player.displayClientMessage(Component.translatable("message.rites_and_paths.death_gamble_lost"), true);
            RESOLVING.add(player.getUUID());
            try {
                player.invulnerableTime = 0;
                boolean hit = player.hurt(flip.source(), Float.MAX_VALUE);
                if (!hit && player.isAlive()) player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
            } finally {
                RESOLVING.remove(player.getUUID());
            }
        }
    }

    /** Grito al ganar: daño mínimo y empuje en área (solo a enemigos). */
    private static void shout(ServerPlayer player, ActiveMechanic m) {
        double radius = m.number("radius", 3);
        float damage = (float) m.number("area_damage", 1);
        double strength = m.number("knockback_blocks", 5) * 0.4; // [MEDIR] ~0,4 de fuerza por bloque
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> CombatAbilities.canHit(player, e) && e.distanceToSqr(player) <= radius * radius)) {
            if (damage > 0) target.hurt(new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(PaladinPerks.SHOCKWAVE_DAMAGE), player), damage);
            Vec3 away = target.position().subtract(player.position());
            if (away.horizontalDistanceSqr() > 1.0E-4) target.knockback(strength, -away.x, -away.z);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.2f, 1.2f);
    }

    private static void unfreeze(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(FREEZE_ID);
    }

    private static boolean immortal(Player player) {
        if (flipping(player)) return true;
        Long until = IMMORTAL_UNTIL.get(player.getUUID());
        return until != null && player.level().getGameTime() < until;
    }

    private static boolean busy(Entity e) {
        return e instanceof ServerPlayer p && flipping(p);
    }

    // ------------------------------------------------------------------ Eventos

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Flip flip = FLIPS.get(player.getUUID());
        if (flip == null) {
            Long until = IMMORTAL_UNTIL.get(player.getUUID());
            if (until != null && player.level().getGameTime() >= until) IMMORTAL_UNTIL.remove(player.getUUID());
            return;
        }
        Vec3 v = player.getDeltaMovement();
        if (v.x != 0 || v.z != 0) player.setDeltaMovement(0, v.y, 0);
        if (player.level().getGameTime() >= flip.resolveTick()) resolve(player, flip);
    }

    /** Inmortal durante la tirada y el segundo posterior; mientras lanza la moneda tampoco puede hacer daño. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (busy(event.getSource().getEntity()) || busy(event.getSource().getDirectEntity())) {
            event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && immortal(player)
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !resolving(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        if (flipping(player) && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true); // seguro: nada le mata mientras gira la moneda
            player.setHealth(1);
            return;
        }
        if (FLIPS.remove(player.getUUID()) != null) unfreeze(player);
        IMMORTAL_UNTIL.remove(player.getUUID());
    }

    /** Si se desconecta con la moneda en el aire, el resultado se aplica ya (no se puede huir de la calavera). */
    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Flip flip = FLIPS.get(player.getUUID());
        if (flip != null) resolve(player, flip);
        IMMORTAL_UNTIL.remove(player.getUUID());
    }

    // --- No puede hacer nada mientras gira la moneda ---

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttackEntity(AttackEntityEvent event) {
        if (busy(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (busy(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (busy(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (busy(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (busy(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (busy(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (busy(event.getEntity())) event.setCanceled(true);
    }
}
