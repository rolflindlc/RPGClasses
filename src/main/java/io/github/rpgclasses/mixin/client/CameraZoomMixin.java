package io.github.rpgclasses.mixin.client;

import io.github.rpgclasses.content.race.RaceTraits;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Cámara en tercera persona de los jugadores grandes (Coloso ×2, Titán ×4): la distancia vanilla (4 bloques) se
 * multiplica por el tamaño, para que la cámara no quede dentro del modelo. Solo agranda, nunca acerca.
 */
@Mixin(Camera.class)
public abstract class CameraZoomMixin {
    @ModifyArg(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(D)D"), require = 0)
    private double rites_and_paths$scaleZoom(double distance) {
        Entity e = ((Camera) (Object) this).getEntity();
        if (e instanceof Player p) {
            float s = RaceTraits.scale(p);
            if (s > 1f) return distance * s;
        }
        return distance;
    }
}
