package io.github.rpgclasses.mixin;

import io.github.rpgclasses.content.race.CraftTraits;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Elfo Arcano: encantar en la mesa cuesta menos experiencia (se devuelve una parte). */
@Mixin(Player.class)
public abstract class PlayerEnchantMixin {
    @Inject(method = "onEnchantmentPerformed(Lnet/minecraft/world/item/ItemStack;I)V", at = @At("TAIL"))
    private void rites_and_paths$arcaneRefund(ItemStack stack, int levels, CallbackInfo ci) {
        CraftTraits.onEnchanted((Player) (Object) this, stack, levels);
    }
}
