package io.github.rpgclasses.mixin;

import io.github.rpgclasses.content.race.CraftTraits;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enano Herrero: marca lo que fabrica. {@code Item.onCraftedBy} se llama con el objeto que recibe el jugador,
 * también al sacarlo con mayúsculas (donde el evento de Forge ya recibe la pila vacía).
 */
@Mixin(Item.class)
public abstract class ItemCraftedMixin {
    @Inject(method = "onCraftedBy(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("HEAD"))
    private void rites_and_paths$forged(ItemStack stack, Level level, Player player, CallbackInfo ci) {
        if (level != null && !level.isClientSide()) CraftTraits.onCrafted(player, stack);
    }
}
