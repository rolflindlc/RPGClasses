package io.github.rpgclasses.cleric;

import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.DebugLog;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ángel Guardián (Intercesor, rev. 2.22): mientras el Intercesor reza por un aliado, el primer golpe que iba a matarlo se
 * anula. Recarga de {@code ally_cooldown_seconds} (60 s) por aliado. Va antes del Milagro, que sigue disponible después.
 */
public final class GuardianAngel {
    public static final String GUARDIAN_ANGEL = Ids.NS + ":guardian_angel";

    /** Aliado -> tick en que puede volver a salvarse. */
    private static final Map<UUID, Long> COOLDOWN = io.github.rpgclasses.server.SessionState.track(new HashMap<>());

    public GuardianAngel() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDamage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || event.getAmount() < victim.getHealth()) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || Miracle.isDowned(victim)) return;
        long now = victim.level().getGameTime();
        if (COOLDOWN.getOrDefault(victim.getUUID(), 0L) > now) return;
        ServerPlayer angel = intercessorFor(victim);
        if (angel == null) return;
        event.setCanceled(true);
        COOLDOWN.put(victim.getUUID(), now + AbilityService.ticks(AbilityService.params(GUARDIAN_ANGEL), "ally_cooldown_seconds", 60));
        ServerLevel level = (ServerLevel) victim.level();
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
        level.sendParticles(ParticleTypes.END_ROD, victim.getX(), victim.getY() + victim.getBbHeight() + 0.4, victim.getZ(), 12, 0.4, 0.1, 0.4, 0.02);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 1.6f);
        if (victim instanceof Player p) p.displayClientMessage(Component.translatable("msg.rites_and_paths.guardian_angel.saved"), true);
        angel.displayClientMessage(Component.translatable("msg.rites_and_paths.guardian_angel.ally", victim.getDisplayName()), true);
        DebugLog.event(angel, "clérigo", "Ángel Guardián salva a {}", DebugLog.describe(victim));
    }

    /** Intercesor con Ángel Guardián que está rezando ahora por la víctima. */
    private static ServerPlayer intercessorFor(LivingEntity victim) {
        for (Player other : victim.level().players()) {
            if (other == victim || !(other instanceof ServerPlayer sp) || !sp.isAlive()) continue;
            if (!Prayer.praying(sp) || !AbilityService.has(sp, GUARDIAN_ANGEL)) continue;
            if (Prayer.target(sp) == victim) return sp;
        }
        return null;
    }
}
