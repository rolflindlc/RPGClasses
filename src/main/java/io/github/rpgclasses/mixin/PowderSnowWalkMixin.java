package io.github.rpgclasses.mixin;

import io.github.rpgclasses.druid.WildShape;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forma Salvaje con «pies de nieve» (zorro de las nieves, Frostmaw...): anda sobre la nieve en polvo. */
@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowWalkMixin {
    @Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true, require = 0)
    private static void rites_and_paths$beastSnowWalk(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player p) {
            var info = WildShape.info(p);
            if (info != null && info.hasPassive("snow_walk")) cir.setReturnValue(true);
        }
    }
}
