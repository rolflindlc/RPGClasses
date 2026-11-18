package io.github.rpgclasses.mixin.irons;

import io.github.rpgclasses.bard.Bard;
import io.github.rpgclasses.bard.BardItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rueda de conjuros de Iron's (cliente y servidor): un Bardo con un instrumento en la mano principal o secundaria solo
 * ve los conjuros del instrumento; no cuentan los libros, las armaduras ni los curios. Los conjuros de raza se quitan
 * en {@code RacialSpells.onSpellSelection}.
 */
@Mixin(targets = "io.redspace.ironsspellbooks.api.magic.SpellSelectionManager", remap = false)
public abstract class SpellSelectionMixin {
    @Shadow @Final private Player player;

    @Unique
    private boolean rites_and_paths$instrumentOnly() {
        return player != null && Bard.isBard(player) && Bard.instrumentHand(player) != null;
    }

    @Inject(method = "initItem(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void rites_and_paths$onlyInstrument(ItemStack stack, String slot, CallbackInfo ci) {
        if (rites_and_paths$instrumentOnly() && BardItems.instrumentOf(stack) == null) ci.cancel();
    }

    @Inject(method = "initCurioItems(Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void rites_and_paths$noCurios(Player p, CallbackInfo ci) {
        if (rites_and_paths$instrumentOnly()) ci.cancel();
    }
}
