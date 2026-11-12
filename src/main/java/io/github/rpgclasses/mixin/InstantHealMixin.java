package io.github.rpgclasses.mixin;

import io.github.rpgclasses.content.race.Races;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No-muerto, Cuerpo Sin Vida: el efecto de Curación instantánea no le cura, venga de donde venga. */
@Mixin(MobEffect.class)
public abstract class InstantHealMixin {
    @Inject(method = "applyInstantenousEffect", at = @At("HEAD"), cancellable = true)
    private void rpgclasses$noInstantHeal(Entity source, Entity indirectSource, LivingEntity target, int amplifier, double health, CallbackInfo ci) {
        if ((Object) this == MobEffects.HEAL && target instanceof ServerPlayer player && Races.has(player, "race_lifeless")) ci.cancel();
    }

    @Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true)
    private void rpgclasses$noHealTick(LivingEntity target, int amplifier, CallbackInfo ci) {
        if ((Object) this == MobEffects.HEAL && target instanceof ServerPlayer player && Races.has(player, "race_lifeless")) ci.cancel();
    }
}
