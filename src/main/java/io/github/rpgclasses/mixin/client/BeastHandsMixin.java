package io.github.rpgclasses.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.rpgclasses.druid.WildShape;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forma Salvaje (Druida): transformado no se dibujan las manos ni los objetos en primera persona. Se corta en la raíz
 * (antes de que Forge lance {@code RenderHandEvent}), así tampoco aparecen los brazos que dibujan otros mods como Epic
 * Fight desde ese evento.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class BeastHandsMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true, require = 0)
    private void rites_and_paths$hideBeastHands(float partial, PoseStack pose, MultiBufferSource.BufferSource buffers,
                                                 LocalPlayer player, int light, CallbackInfo ci) {
        if (WildShape.inForm(player)) ci.cancel();
    }
}
