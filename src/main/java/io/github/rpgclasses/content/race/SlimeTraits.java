package io.github.rpgclasses.content.race;

import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.DebugLog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Subrazas del Slime que decide el servidor (adenda 0.7, sección 3): Resorte (lanzamiento con giro, choques) y
 * Viscoso (estado pegado, golpes con Lentitud). El movimiento lo hace el cliente ({@code client.RaceBodyClient})
 * y lo avisa con {@code Packets.RaceMotion}.
 */
public final class SlimeTraits {
    private static final String SPRING_KEY = Ids.NS + ":race_spring";
    /** Viscoso: a qué está pegado cada jugador (id de entidad, o -1 si es una pared). */
    private static final Map<UUID, Integer> STUCK = new HashMap<>();
    /** Resorte: hasta cuándo dura el lanzamiento y a quién ha golpeado ya en él. */
    private static final Map<UUID, Long> LAUNCH_UNTIL = new HashMap<>();
    private static final Map<UUID, Set<UUID>> LAUNCH_HITS = new HashMap<>();

    public SlimeTraits() {
        Rites.STATE_SOURCES.put("sticky", p -> STUCK.containsKey(p.getUUID()));
    }

    public static boolean stuck(ServerPlayer p) {
        return STUCK.containsKey(p.getUUID());
    }

    /** Paquete del cliente: lanzamiento del Resorte o cambio de adherencia del Viscoso. Se valida aquí. */
    public static void onMotion(ServerPlayer p, int kind, int value, int entity) {
        if (!p.isAlive() || p.isSpectator()) return;
        switch (kind) {
            case 0 -> launch(p, value);
            case 1 -> {
                if (!Races.has(p, "race_sticky")) return;
                if (entity >= 0) {
                    Entity e = p.level().getEntity(entity);
                    if (e == null || e.distanceToSqr(p) > 16) return;
                }
                STUCK.put(p.getUUID(), entity);
            }
            case 2 -> STUCK.remove(p.getUUID());
            default -> {}
        }
    }

    private static void launch(ServerPlayer p, int chargeTicks) {
        ActiveMechanic m = Races.find(p, "race_spring");
        if (m == null || AbilityService.remainingTicks(p, SPRING_KEY) > 0) return;
        int max = (int) Math.round(m.number("charge_seconds", 3) * 20);
        int ticks = Math.max(0, Math.min(max, chargeTicks));
        if (ticks < m.number("min_charge_seconds", 0.5) * 20) return;
        AbilityService.setCooldown(p, SPRING_KEY, (int) Math.round(m.number("cooldown_seconds", 10) * 20));
        io.github.rpgclasses.network.Net.syncPlayer(p);
        if (ticks >= 40) Rites.count(p, "spring_launch", 1);
        Rites.onAbilityUsed(p, "race_spring");
        p.startAutoSpinAttack(20);
        long now = p.level().getGameTime();
        LAUNCH_UNTIL.put(p.getUUID(), now + 40);
        LAUNCH_HITS.put(p.getUUID(), new HashSet<>());
        p.level().playSound(null, p.blockPosition(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.2f, 0.8f);
        DebugLog.event(p, "raza", "Resorte: lanzamiento con {} ticks de carga", ticks);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer || event.getSource().getDirectEntity() != p) return;
        LivingEntity victim = event.getEntity();
        long now = p.level().getGameTime();
        // Resorte: lo que golpea el giro del lanzamiento cuenta como choque.
        Long until = LAUNCH_UNTIL.get(p.getUUID());
        if (until != null && now <= until && Races.has(p, "race_spring")) {
            Rites.credit(p, victim, "spring");
            Set<UUID> hits = LAUNCH_HITS.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
            if (hits.add(victim.getUUID()) && Rites.isOwnBoss(p, victim)) Rites.count(p, "spring_crash", 1);
        }
        // Viscoso: los golpes cuerpo a cuerpo dan Lentitud (jefes: la mitad y como mucho Lentitud I).
        ActiveMechanic slow = Races.find(p, "race_slowing_hits");
        if (slow != null && event.getSource().is(DamageTypes.PLAYER_ATTACK)) {
            boolean boss = Races.isBoss(victim);
            int ticks = (int) Math.round(slow.number("slow_seconds", 2) * 20 * (boss ? 0.5 : 1));
            int amp = boss ? 0 : (int) slow.number("amplifier", 0);
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, amp), p);
            if (Rites.isOwnBoss(p, victim)) Rites.count(p, "slow_hits", 1);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        Integer stuckTo = STUCK.get(p.getUUID());
        if (stuckTo == null) return;
        if (!p.isAlive() || !Races.has(p, "race_sticky")) {
            STUCK.remove(p.getUUID());
            return;
        }
        // Rito Garrapata: segundos pegado al jefe de rito.
        if (stuckTo >= 0) {
            Entity e = p.level().getEntity(stuckTo);
            if (e != null && Rites.isOwnBoss(p, e)) Rites.count(p, "sticky_boss_seconds", 1);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        STUCK.remove(id);
        LAUNCH_UNTIL.remove(id);
        LAUNCH_HITS.remove(id);
    }
}
