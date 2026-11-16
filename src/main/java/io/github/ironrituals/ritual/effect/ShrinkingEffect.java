package io.github.ironrituals.ritual.effect;

import io.github.ironrituals.IronRituals;
import io.github.ironrituals.compat.ScaleCompat;
import io.github.ironrituals.registry.ModEffects;
import io.github.ironrituals.ritual.RitualContext;
import io.github.ironrituals.ritual.RitualEffect;
import io.github.ironrituals.ritual.active.ActiveRitual;
import io.github.ironrituals.ritual.active.RitualSavedData;
import io.github.ironrituals.ritual.active.RitualTicker;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Ritual del Encogimiento (plan, 4.C.16): escala del lanzador × 0.5 durante la duración del nivel. No se acumula;
 * volver a lanzarlo renueva la duración. Con el catalizador {@code climb} (Pata de Conejo): Caída Lenta y trepa
 * por paredes. Termina también si el jugador muere.
 */
public class ShrinkingEffect implements RitualEffect {
    public static final ResourceLocation ID = IronRituals.id("shrinking");
    public static final double FACTOR = 0.5;
    private static final int BUFF_TICKS = 60;

    @Nullable
    @Override
    public Component checkStart(RitualContext ctx) {
        return ScaleCompat.available() ? null : Component.translatable("ritual.ironrituals.shrinking.no_backend");
    }

    @Override
    public void apply(RitualContext ctx) {
        ServerPlayer caster = ctx.caster();
        MinecraftServer server = ctx.level().getServer();
        for (ActiveRitual old : activeFor(server, caster.getUUID())) RitualTicker.end(server, old);

        CompoundTag data = new CompoundTag();
        data.putUUID("Target", caster.getUUID());
        ActiveRitual r = ctx.startPersistent(0, ctx.scaledDurationTicks(), data);
        tick(r, ctx.level());

        ServerLevel level = ctx.level();
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, caster.getX(), caster.getY() + 1, caster.getZ(), 40, 0.4, 0.6, 0.4, 0.0);
        level.playSound(null, caster.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.6F, 1.8F);
    }

    @Override
    public void tick(ActiveRitual r, ServerLevel level) {
        ServerPlayer p = target(level.getServer(), r);
        if (p == null || !p.isAlive()) return;
        ScaleCompat.apply(p, FACTOR);
        if (r.hasCatalyst("climb")) {
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, BUFF_TICKS, 0, true, false, true));
            p.addEffect(new MobEffectInstance(ModEffects.WALL_CLIMBING.get(), BUFF_TICKS, 0, true, false, true));
        }
    }

    @Override
    public void onEnd(ActiveRitual r, ServerLevel level) {
        ServerPlayer p = target(level.getServer(), r);
        if (p == null) return; // desconectado: se deshace al volver (PlayerStateEvents)
        ScaleCompat.restore(p);
        if (r.hasCatalyst("climb")) {
            p.removeEffect(ModEffects.WALL_CLIMBING.get());
            p.removeEffect(MobEffects.SLOW_FALLING);
        }
        level.playSound(null, p.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.5F, 0.9F);
    }

    @Nullable
    private static ServerPlayer target(MinecraftServer server, ActiveRitual r) {
        return r.data.hasUUID("Target") ? server.getPlayerList().getPlayer(r.data.getUUID("Target")) : null;
    }

    public static List<ActiveRitual> activeFor(MinecraftServer server, UUID player) {
        return RitualSavedData.get(server).matching(r -> r.ritual.equals(ID) && r.data.hasUUID("Target")
                && r.data.getUUID("Target").equals(player));
    }
}
