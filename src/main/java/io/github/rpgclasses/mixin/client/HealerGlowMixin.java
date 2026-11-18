package io.github.rpgclasses.mixin.client;

import io.github.rpgclasses.client.cleric.ClericClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ojo del Sanador (Clérigo): los aliados cercanos con poca vida (o tumbados) brillan, solo para el Clérigo. */
@Mixin(Minecraft.class)
public abstract class HealerGlowMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true, require = 0)
    private void rites_and_paths$healerGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (ClericClient.glows(entity)) cir.setReturnValue(true);
    }
}
