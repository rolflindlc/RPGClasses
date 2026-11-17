package io.github.rpgclasses.mixin.client;

import io.github.rpgclasses.client.CentaurRender;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Centauro: mientras se dibuja su torso humano (sin piernas), las grebas y las botas tampoco se dibujan en las piernas. */
@Mixin(HumanoidArmorLayer.class)
public abstract class ArmorLegsMixin {
    @Inject(method = "setPartVisibility", at = @At("TAIL"), require = 0)
    private void rites_and_paths$centaurLegs(HumanoidModel<?> model, EquipmentSlot slot, CallbackInfo ci) {
        if (!CentaurRender.hidingLegs()) return;
        model.leftLeg.visible = false;
        model.rightLeg.visible = false;
    }
}
