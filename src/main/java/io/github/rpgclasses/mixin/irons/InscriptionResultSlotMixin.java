package io.github.rpgclasses.mixin.irons;

import io.github.rpgclasses.bard.InscriptionTable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mesa de inscripción de Iron's: ranura de resultado (clase anónima n.º 5 del menú). Con un instrumento en la mesa,
 * coger la partitura la quita del instrumento ({@code InscriptionMenuMixin}); Iron's quitaría el conjuro de su
 * contenedor, que en un instrumento se reescribe a partir de las partituras.
 */
// @Pseudo: la clase es de Iron's (no está en el jar de API con el que se compila) y puede no existir.
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu$5", remap = false)
public abstract class InscriptionResultSlotMixin {
    @Inject(method = {"m_142406_(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
            "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V"},
            at = @At("HEAD"), cancellable = true, require = 0)
    private void rites_and_paths$takeSheet(Player player, ItemStack taken, CallbackInfo ci) {
        if (player != null && player.containerMenu instanceof InscriptionTable.Menu menu && menu.rites_and_paths$onResultTaken(player)) {
            ((Slot) (Object) this).setChanged();
            ci.cancel();
        }
    }
}
