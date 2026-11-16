package io.github.ironrituals.ritual.effect;

import io.github.ironrituals.ritual.RitualContext;
import io.github.ironrituals.ritual.RitualEffect;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Ritual del Ancla del Alma (plan, 4.C.19): estado hasta la próxima muerte. Al morir se conservan inventario,
 * equipo y Curios (lógica en {@code PlayerStateEvents}). Un solo uso.
 */
public class SoulAnchorEffect implements RitualEffect {
    public static final String FLAG = "ironrituals_soul_anchor";

    public static boolean has(Player p) {
        return p.getPersistentData().getBoolean(FLAG);
    }

    @Nullable
    @Override
    public Component checkStart(RitualContext ctx) {
        return has(ctx.caster()) ? Component.translatable("ritual.ironrituals.soul_anchor.already") : null;
    }

    @Override
    public void apply(RitualContext ctx) {
        ServerPlayer p = ctx.caster();
        p.getPersistentData().putBoolean(FLAG, true);
        ctx.level().sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1, p.getZ(), 30, 0.3, 0.6, 0.3, 0.03);
        ctx.level().playSound(null, p.blockPosition(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 1.0F, 1.0F);
        p.sendSystemMessage(Component.translatable("message.ironrituals.soul_anchor.bound"));
    }
}
