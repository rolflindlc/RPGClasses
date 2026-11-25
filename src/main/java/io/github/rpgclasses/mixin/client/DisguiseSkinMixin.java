package io.github.rpgclasses.mixin.client;

import io.github.rpgclasses.client.rogue.DisguiseClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cambiarrostros: disfrazado de otro jugador se usa su skin y su modelo (normal o delgado), sin capa. */
@Mixin(AbstractClientPlayer.class)
public abstract class DisguiseSkinMixin {
    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true, require = 0)
    private void rites_and_paths$disguiseSkin(CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation skin = DisguiseClient.skin((AbstractClientPlayer) (Object) this);
        if (skin != null) cir.setReturnValue(skin);
    }

    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true, require = 0)
    private void rites_and_paths$disguiseModel(CallbackInfoReturnable<String> cir) {
        String model = DisguiseClient.model((AbstractClientPlayer) (Object) this);
        if (model != null) cir.setReturnValue(model);
    }

    @Inject(method = "getCloakTextureLocation", at = @At("HEAD"), cancellable = true, require = 0)
    private void rites_and_paths$disguiseCape(CallbackInfoReturnable<ResourceLocation> cir) {
        if (DisguiseClient.hidesCape((AbstractClientPlayer) (Object) this)) cir.setReturnValue(null);
    }
}
