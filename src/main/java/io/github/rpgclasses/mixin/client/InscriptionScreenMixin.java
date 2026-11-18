package io.github.rpgclasses.mixin.client;

import io.github.rpgclasses.bard.InscriptionTable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pantalla de la mesa de inscripción de Iron's (cliente): reconoce un instrumento como libro y una partitura como
 * pergamino para activar el botón, y manda el clic de inscribir. La inscripción la hace el servidor
 * ({@code InscriptionMenuMixin}).
 */
// @Pseudo: la clase es de Iron's (no está en el jar de API con el que se compila) y puede no existir.
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableScreen", remap = false)
public abstract class InscriptionScreenMixin {
    @Shadow
    private boolean isDirty;

    private static AbstractContainerMenu rites_and_paths$menu() {
        var player = Minecraft.getInstance().player;
        return player == null ? null : player.containerMenu;
    }

    @Inject(method = "isSpellBookSlotted()Z", at = @At("RETURN"), cancellable = true, require = 0)
    private void rites_and_paths$book(CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerMenu menu = rites_and_paths$menu();
        if (!cir.getReturnValue() && menu != null && menu.slots.size() > InscriptionTable.SCROLL_SLOT
                && InscriptionTable.isInstrument(menu.slots.get(InscriptionTable.BOOK_SLOT).getItem())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isScrollSlotted()Z", at = @At("RETURN"), cancellable = true, require = 0)
    private void rites_and_paths$scroll(CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerMenu menu = rites_and_paths$menu();
        if (!cir.getReturnValue() && menu != null && menu.slots.size() > InscriptionTable.SCROLL_SLOT
                && InscriptionTable.isSheet(menu.slots.get(InscriptionTable.SCROLL_SLOT).getItem())) {
            cir.setReturnValue(true);
        }
    }

    /** Con un instrumento y una partitura, Iron's no haría nada (exige SpellBook y Scroll): mandamos el clic nosotros. */
    @Inject(method = "onInscription()V", at = @At("HEAD"), cancellable = true, require = 0)
    private void rites_and_paths$inscribe(CallbackInfo ci) {
        AbstractContainerMenu menu = rites_and_paths$menu();
        Minecraft mc = Minecraft.getInstance();
        if (menu == null || mc.gameMode == null || menu.slots.size() <= InscriptionTable.SCROLL_SLOT) return;
        if (!InscriptionTable.isInstrument(menu.slots.get(InscriptionTable.BOOK_SLOT).getItem())
                || !InscriptionTable.isSheet(menu.slots.get(InscriptionTable.SCROLL_SLOT).getItem())) return;
        isDirty = true;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1f));
        mc.gameMode.handleInventoryButtonClick(menu.containerId, -1);
        ci.cancel();
    }
}
