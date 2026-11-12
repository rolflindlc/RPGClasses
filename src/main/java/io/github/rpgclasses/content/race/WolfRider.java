package io.github.rpgclasses.content.race;

import io.github.rpgclasses.content.CombatAbilities;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.server.AbilityService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/**
 * Goblin Jinete de Lobo (anexo de razas, 3.5):
 * <ul>
 *   <li>R0: clic derecho con la mano vacía en tu lobo adulto para montarlo (agachado: sentarlo, como en vanilla).
 *       El movimiento lo hace {@code WolfRideMixin}; la velocidad del lobo no cuenta para el tope del jugador.</li>
 *   <li>R1: tus lobos cercanos tienen +25 % de vida.</li>
 *   <li>R2: el lobo que montas muerde a lo que golpeas (sin montar, los lobos vanilla ya atacan lo que atacas).</li>
 *   <li>R3: el lobo que montas tiene Regeneración II.</li>
 * </ul>
 */
public final class WolfRider {
    private static final UUID VIGOR_ID = UUID.fromString("2e8f6a4c-1b7d-4c8e-9f3a-5d6b7c8e9f01");

    /** Jinete que controla al lobo: su dueño, si va montado en él. */
    public static Player rider(Wolf wolf) {
        if (!(wolf.getFirstPassenger() instanceof Player p) || !wolf.isTame()) return null;
        UUID owner = wolf.getOwnerUUID();
        return owner != null && owner.equals(p.getUUID()) ? p : null;
    }

    public static float riddenSpeed(Wolf wolf, Player rider) {
        double factor = Races.number(rider, "race_wolf_mount", "speed_factor", 1.0);
        return (float) (wolf.getAttributeValue(Attributes.MOVEMENT_SPEED) * factor);
    }

    private static boolean ownWolf(Player p, Entity e) {
        return e instanceof Wolf wolf && wolf.isTame() && p.getUUID().equals(wolf.getOwnerUUID());
    }

    /** Montar (en los dos lados, para que el cliente no prediga el "sentarse"). */
    @SubscribeEvent
    public void onInteract(PlayerInteractEvent.EntityInteract event) {
        Player p = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND || p.isShiftKeyDown() || !p.getMainHandItem().isEmpty()
                || p.isPassenger() || p instanceof FakePlayer) return;
        if (!ownWolf(p, event.getTarget()) || ((Wolf) event.getTarget()).isBaby() || !Races.has(p, "race_wolf_mount")) return;
        Wolf wolf = (Wolf) event.getTarget();
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(p.level().isClientSide()));
        if (p.level().isClientSide()) return;
        wolf.setOrderedToSit(false);
        wolf.setInSittingPose(false);
        wolf.getNavigation().stop();
        wolf.setTarget(null);
        p.startRiding(wolf);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        if (p.tickCount % 20 != 0) return;
        // R3: Regeneración al lobo que montas.
        ActiveMechanic regen = Races.find(p, "race_wolf_regen");
        if (regen != null && p.getVehicle() instanceof Wolf wolf && ownWolf(p, wolf)) {
            wolf.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 50, (int) regen.number("amplifier", 1), true, false, true));
        }
        // R1: vida extra a tus lobos cercanos (se quita si ya no tienes la mecánica).
        if (p.tickCount % 100 != 0) return;
        ActiveMechanic vigor = Races.find(p, "race_wolf_vigor");
        double bonus = vigor == null ? 0 : vigor.number("health", 0.25);
        for (Wolf wolf : p.level().getEntitiesOfClass(Wolf.class, p.getBoundingBox().inflate(48), w -> ownWolf(p, w))) {
            AttributeInstance health = wolf.getAttribute(Attributes.MAX_HEALTH);
            if (health == null) continue;
            AttributeModifier current = health.getModifier(VIGOR_ID);
            if (current != null && current.getAmount() == bonus) continue;
            if (current != null) health.removeModifier(VIGOR_ID);
            if (bonus > 0) health.addPermanentModifier(new AttributeModifier(VIGOR_ID, "rpgclasses wolf vigor", bonus,
                    AttributeModifier.Operation.MULTIPLY_BASE));
            if (wolf.getHealth() > wolf.getMaxHealth()) wolf.setHealth(wolf.getMaxHealth());
        }
    }

    /** R2: el lobo montado muerde a lo que golpea su jinete. */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || !(p.getVehicle() instanceof Wolf wolf) || !ownWolf(p, wolf)) return;
        LivingEntity target = event.getEntity();
        if (target == wolf || target == p) return;
        ActiveMechanic bite = Races.find(p, "race_wolf_bite");
        if (bite == null || !CombatAbilities.canHit(p, target)) return;
        double reach = bite.number("reach", 3.5);
        if (wolf.distanceToSqr(target) > reach * reach || AbilityService.remainingTicks(p, "race_wolf_bite") > 0) return;
        AbilityService.setCooldown(p, "race_wolf_bite", (int) Math.round(bite.number("cooldown_seconds", 1) * 20));
        BITES.add(new Bite(wolf, target)); // al final del tick, fuera del daño en curso
    }

    private record Bite(Wolf wolf, LivingEntity target) {}
    private static final java.util.List<Bite> BITES = new java.util.ArrayList<>();

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BITES.isEmpty()) return;
        for (Bite b : BITES) {
            if (!b.wolf().isAlive() || !b.target().isAlive()) continue;
            b.target().invulnerableTime = 0; // el golpe del jinete no debe anular el mordisco
            b.wolf().swing(InteractionHand.MAIN_HAND);
            b.wolf().doHurtTarget(b.target());
        }
        BITES.clear();
    }
}
