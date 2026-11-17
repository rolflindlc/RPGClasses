package io.github.rpgclasses.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Razas grandes (Centauro, Coloso, Titán): el empujón de vanilla que saca al jugador de dentro de los bloques
 * ({@code moveTowardsClosestSpace}) comprueba esquinas a 0,35 veces su ancho y cajas del ancho entero, así que con una
 * hitbox de más de 0,6 detecta paredes situadas fuera de ella y lo empuja sin parar: el jugador queda paralizado
 * junto a las paredes, sin poder saltar ni subir escalones. Con esas hitbox ya no se aplica (la colisión normal
 * impide entrar en los bloques).
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerPushMixin {
    @Inject(method = "moveTowardsClosestSpace(DD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void rites_and_paths$noWallPush(double x, double z, CallbackInfo ci) {
        if (((LocalPlayer) (Object) this).getBbWidth() > 0.8f) ci.cancel();
    }
}
