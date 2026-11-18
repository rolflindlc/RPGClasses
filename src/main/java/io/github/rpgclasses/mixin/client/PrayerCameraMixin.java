package io.github.rpgclasses.mixin.client;

import io.github.rpgclasses.client.cleric.ClericClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Plegaria (Intercesor): mientras reza, la cámara se coloca en el ángel que orbita al aliado. La rotación sigue siendo
 * la del jugador (el ratón), así que se mira al aliado desde detrás; la cámara queda «separada» para que el cuerpo
 * arrodillado se dibuje si entra en el plano.
 */
@Mixin(Camera.class)
public abstract class PrayerCameraMixin {
    @Shadow private boolean detached;

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Inject(method = "setup", at = @At("TAIL"), require = 0)
    private void rites_and_paths$prayerCamera(BlockGetter level, Entity entity, boolean detachedIn, boolean mirror, float partialTick, CallbackInfo ci) {
        Vec3 pos = ClericClient.prayerCamera((Camera) (Object) this, partialTick);
        if (pos == null) return;
        this.detached = true;
        this.setPosition(pos);
    }
}
