package io.github.rpgclasses.mixin;

import io.github.rpgclasses.content.race.CraftTraits;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Infernal Nativo (Rango 2): probabilidad de que el trueque con un piglin salga doble. */
@Mixin(PiglinAi.class)
public abstract class PiglinBarterMixin {
    @Inject(method = "getBarterResponseItems(Lnet/minecraft/world/entity/monster/piglin/Piglin;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true)
    private static void rites_and_paths$barter(Piglin piglin, CallbackInfoReturnable<List<ItemStack>> cir) {
        List<ItemStack> out = CraftTraits.onBarter(piglin, cir.getReturnValue());
        if (out != cir.getReturnValue()) cir.setReturnValue(out);
    }
}
