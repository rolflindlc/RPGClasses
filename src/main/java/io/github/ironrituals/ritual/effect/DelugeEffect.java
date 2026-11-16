package io.github.ironrituals.ritual.effect;

import io.github.ironrituals.ritual.RitualContext;
import io.github.ironrituals.ritual.RitualEffect;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

/**
 * Ritual del Diluvio (plan, 4.A.3): lluvia durante la duración del nivel (≈ 10 min, escala con el poder de Rayo);
 * con el catalizador {@code storm} (Botella de Rayo), tormenta. Solo en dimensiones con clima.
 */
public class DelugeEffect implements RitualEffect {

    @Nullable
    @Override
    public Component checkStart(RitualContext ctx) {
        ServerLevel level = ctx.level();
        if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling() || level.dimensionType().ultraWarm()) {
            return Component.translatable("ritual.ironrituals.deluge.no_weather");
        }
        return null;
    }

    @Override
    public void apply(RitualContext ctx) {
        int ticks = (int) Math.max(20 * 60, ctx.scaledDurationTicks());
        boolean storm = ctx.hasCatalyst("storm");
        ctx.level().setWeatherParameters(0, ticks, true, storm);
        ctx.level().playSound(null, ctx.core(), storm ? SoundEvents.LIGHTNING_BOLT_THUNDER : SoundEvents.WEATHER_RAIN_ABOVE,
                SoundSource.WEATHER, 2.0F, 0.9F);
    }
}
