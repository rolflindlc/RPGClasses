package io.github.rpgclasses.server;

import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.stat.StatMath;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Muro de Escudos (parte vanilla): un muro de X% alarga el cooldown de recuperación un 2X%
 * y aumenta el desgaste un X%. La guardia de Epic Fight se tratará en su puente (fase 4).
 */
public final class ShieldWall {
    /** Ticks que vanilla deja inutilizable un escudo al desactivarlo con un hacha. */
    private static final int VANILLA_DISABLE_TICKS = 100;
    /** Daño mínimo bloqueado a partir del cual vanilla desgasta el escudo. */
    private static final float VANILLA_WEAR_THRESHOLD = 3.0f;

    private static final Map<UUID, Boolean> WAS_ON_COOLDOWN = new HashMap<>();

    /**
     * Vanilla no avisa cuando desactiva un escudo, así que se detecta el tick en que empieza su cooldown
     * y se sustituye por uno más largo. Supone la duración vanilla (5 s).
     */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        ItemStack shield = heldShield(player);
        if (shield.isEmpty()) {
            WAS_ON_COOLDOWN.remove(player.getUUID());
            return;
        }
        boolean onCooldown = player.getCooldowns().isOnCooldown(shield.getItem());
        Boolean before = WAS_ON_COOLDOWN.put(player.getUUID(), onCooldown);
        if (onCooldown && Boolean.FALSE.equals(before)) {
            double wall = StatEngine.wall(player, Ids.SHIELD);
            if (wall > 0) {
                int ticks = (int) Math.round(VANILLA_DISABLE_TICKS * StatMath.shieldCooldownFactor(wall));
                player.getCooldowns().addCooldown(shield.getItem(), ticks);
                // Aviso sobre la barra de objetos: deja ver la penalización (y comprobar que se aplica).
                player.displayClientMessage(Component.translatable("msg.rpgclasses.shield_disabled",
                        String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0)), true);
            }
        }
    }

    /** Desgaste extra: vanilla quita 1 + daño bloqueado; aquí se añade la fracción del muro. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onShieldBlock(ShieldBlockEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        if (!event.shieldTakesDamage() || event.getBlockedDamage() < VANILLA_WEAR_THRESHOLD) return;
        double wall = StatEngine.wall(player, Ids.SHIELD);
        if (wall <= 0) return;
        ItemStack shield = player.getUseItem();
        if (shield.isEmpty() || !shield.isDamageableItem()) return;
        int vanillaWear = 1 + (int) Math.floor(event.getBlockedDamage());
        int extra = (int) Math.round(vanillaWear * (StatMath.shieldDurabilityFactor(wall) - 1));
        if (extra <= 0) return;
        InteractionHand hand = player.getUsedItemHand();
        shield.hurtAndBreak(extra, player, p -> p.broadcastBreakEvent(hand));
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        WAS_ON_COOLDOWN.remove(event.getEntity().getUUID());
    }

    private static ItemStack heldShield(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.isEmpty() && Ids.SHIELD.equals(WeaponClassifier.classify(stack).discipline())) return stack;
        }
        return ItemStack.EMPTY;
    }
}
