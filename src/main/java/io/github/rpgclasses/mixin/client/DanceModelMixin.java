package io.github.rpgclasses.mixin.client;

import io.github.rpgclasses.client.bard.DanceFx;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Baile Forzado (Bardo): brazos doblados y torso de lado a lado en los modelos humanoides que están bailando. */
@Mixin(HumanoidModel.class)
public abstract class DanceModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"), require = 0)
    private void rites_and_paths$dance(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                  float netHeadYaw, float headPitch, CallbackInfo ci) {
        DanceFx.pose(entity, ageInTicks, (HumanoidModel<?>) (Object) this);
    }
}
