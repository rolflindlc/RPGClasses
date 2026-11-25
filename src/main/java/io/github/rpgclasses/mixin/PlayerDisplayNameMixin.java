package io.github.rpgclasses.mixin;

import io.github.rpgclasses.rogue.FaceChanger;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cambiarrostros: en los mensajes de muerte y de kill aparece el nombre del disfraz (el chat normal no cambia). */
@Mixin(Player.class)
public abstract class PlayerDisplayNameMixin {
    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void rites_and_paths$disguiseName(CallbackInfoReturnable<Component> cir) {
        Component name = FaceChanger.deathMessageName((Player) (Object) this);
        if (name != null) cir.setReturnValue(name);
    }
}
