package io.github.rpgclasses.server;

import io.github.rpgclasses.config.ServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** "En combate" = ha dado o recibido daño de otra entidad hace menos de N segundos. Solo servidor. */
public final class CombatTracker {
    private static final Map<UUID, Long> LAST_COMBAT_TICK = new HashMap<>();

    public static boolean inCombat(ServerPlayer player) {
        Long last = LAST_COMBAT_TICK.get(player.getUUID());
        if (last == null) return false;
        long elapsed = player.server.getTickCount() - last;
        return elapsed >= 0 && elapsed < ServerConfig.OUT_OF_COMBAT_SECONDS.get() * 20L;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDamage(LivingDamageEvent event) {
        if (event.getAmount() <= 0) return;
        LivingEntity victim = event.getEntity();
        if (victim instanceof ServerPlayer p && event.getSource().getEntity() instanceof LivingEntity attacker && attacker != victim) {
            LAST_COMBAT_TICK.put(p.getUUID(), (long) p.server.getTickCount());
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && attacker != victim) {
            LAST_COMBAT_TICK.put(attacker.getUUID(), (long) attacker.server.getTickCount());
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_COMBAT_TICK.remove(event.getEntity().getUUID());
    }
}
