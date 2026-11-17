package io.github.rpgclasses.mixin;

import io.github.rpgclasses.content.race.CraftTraits;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Enano Herrero: los objetos con la marca de Forja Enana tienen más durabilidad máxima. */
@Mixin(ItemStack.class)
public abstract class ItemDurabilityMixin {
    @Inject(method = "getMaxDamage()I", at = @At("RETURN"), cancellable = true)
    private void rites_and_paths$forged(CallbackInfoReturnable<Integer> cir) {
        int base = cir.getReturnValueI();
        int boosted = CraftTraits.maxDamage((ItemStack) (Object) this, base);
        if (boosted != base) cir.setReturnValue(boosted);
    }
}
