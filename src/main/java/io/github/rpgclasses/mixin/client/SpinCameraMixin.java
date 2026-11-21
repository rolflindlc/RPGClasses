package io.github.rpgclasses.mixin.client;

import io.github.rpgclasses.client.spin.SpinCinematic;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cámara de cine del Rectángulo Dorado (Maestro del Giro): al final de {@code Camera.setup}, si hay un plano de
 * {@link SpinCinematic}, la cámara se coloca en él y pasa a tercera persona (así se dibuja el propio jugador).
 */
@Mixin(Camera.class)
public abstract class SpinCameraMixin {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void rites_and_paths$spinCinematic(BlockGetter level, Entity entity, boolean detachedIn, boolean mirrored, float partialTick,
                                               CallbackInfo ci) {
        SpinCinematic.Shot shot = SpinCinematic.shot(partialTick);
        if (shot == null) return;
        setRotation(shot.yaw(), shot.pitch());
        setPosition(shot.pos().x, shot.pos().y, shot.pos().z);
        detached = true;
    }
}
