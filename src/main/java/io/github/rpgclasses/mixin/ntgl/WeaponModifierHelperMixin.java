package io.github.rpgclasses.mixin.ntgl;

import com.nukateam.ntgl.common.data.WeaponData;
import io.github.rpgclasses.engineer.EngineerHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Engineer addendum: fire rate, spread, reload time and draw time of NTGL guns, adjusted by the Engineer's branches,
 * subclasses and active abilities (see {@link EngineerHooks}). Only applied when NTGL is installed (NtglMixinPlugin);
 * {@link WeaponData} is a compile-time stub of NTGL's class.
 */
@Mixin(targets = "com.nukateam.ntgl.common.util.util.WeaponModifierHelper", remap = false)
public abstract class WeaponModifierHelperMixin {
    @Inject(method = "getRate", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void rites_and_paths$rate(WeaponData data, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(EngineerHooks.rate(data, cir.getReturnValue()));
    }

    @Inject(method = "getSpread", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void rites_and_paths$spread(WeaponData data, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(EngineerHooks.spread(data, cir.getReturnValue()));
    }

    @Inject(method = "getReloadTime", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void rites_and_paths$reload(WeaponData data, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(EngineerHooks.reloadTime(data, cir.getReturnValue()));
    }

    @Inject(method = "getEquipTime", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void rites_and_paths$equip(WeaponData data, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(EngineerHooks.equipTime(data, cir.getReturnValue()));
    }
}
