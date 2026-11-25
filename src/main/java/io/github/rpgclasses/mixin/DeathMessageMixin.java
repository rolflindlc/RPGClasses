package io.github.rpgclasses.mixin;

import io.github.rpgclasses.rogue.FaceChanger;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.CombatTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cambiarrostros: mientras se calcula un mensaje de muerte, los jugadores disfrazados se nombran por su disfraz (ver
 * PlayerDisplayNameMixin). El mensaje se calcula dentro de un try/finally para que el modo nunca se quede puesto.
 */
@Mixin(CombatTracker.class)
public abstract class DeathMessageMixin {
    @Inject(method = "getDeathMessage", at = @At("HEAD"), cancellable = true)
    private void rites_and_paths$disguisedNames(CallbackInfoReturnable<Component> cir) {
        if (FaceChanger.inDeathMessage()) return; // segunda pasada: el cálculo original
        FaceChanger.beginDeathMessage();
        try {
            cir.setReturnValue(((CombatTracker) (Object) this).getDeathMessage());
        } finally {
            FaceChanger.endDeathMessage();
        }
    }
}
