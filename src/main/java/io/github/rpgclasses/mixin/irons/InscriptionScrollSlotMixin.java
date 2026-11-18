package io.github.rpgclasses.mixin.irons;

import io.github.rpgclasses.bard.InscriptionTable;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mesa de inscripción de Iron's: la ranura del pergamino (clase anónima n.º 4 del menú) también admite partituras. */
// @Pseudo: la clase es de Iron's (no está en el jar de API con el que se compila) y puede no existir.
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu$4", remap = false)
public abstract class InscriptionScrollSlotMixin {
    @Inject(method = {"m_5857_(Lnet/minecraft/world/item/ItemStack;)Z", "mayPlace(Lnet/minecraft/world/item/ItemStack;)Z"},
            at = @At("HEAD"), cancellable = true, require = 0)
    private void rites_and_paths$sheets(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (InscriptionTable.isSheet(stack)) cir.setReturnValue(true);
    }
}
