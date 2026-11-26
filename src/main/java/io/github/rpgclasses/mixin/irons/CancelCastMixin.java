package io.github.rpgclasses.mixin.irons;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Maestro del Giro, giro muscular: quien lanza un conjuro a la fuerza (bastón golpeado por una bola sobrecargada) no lo
 * puede cancelar hasta que acabe. Toda cancelación de Iron's pasa por {@code ServerboundCancelCast.cancelCast}. Con
 * {@code require = 0}: si Iron's cambia, solo se pierde ese bloqueo.
 */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.network.ServerboundCancelCast", remap = false)
public abstract class CancelCastMixin {
    @Inject(method = "cancelCast(Lnet/minecraft/server/level/ServerPlayer;Z)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void rites_and_paths$forcedCast(ServerPlayer player, boolean triggerCooldown, CallbackInfo ci) {
        if (io.github.rpgclasses.spin.MuscleSpin.blocksCancel(player)) ci.cancel();
    }
}
