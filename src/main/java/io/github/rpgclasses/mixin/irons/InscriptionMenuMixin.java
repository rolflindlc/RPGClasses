package io.github.rpgclasses.mixin.irons;

import io.github.rpgclasses.bard.InscriptionTable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mesa de inscripción de Iron's: el botón de inscribir (id negativo) con un instrumento y una partitura la hace
 * {@link InscriptionTable}; con un libro y un pergamino sigue el camino de Iron's.
 */
@Mixin(targets = "io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu", remap = false)
public abstract class InscriptionMenuMixin {
    @Inject(method = {"m_6366_(Lnet/minecraft/world/entity/player/Player;I)Z", "clickMenuButton(Lnet/minecraft/world/entity/player/Player;I)Z"},
            at = @At("HEAD"), cancellable = true, require = 0)
    private void rites_and_paths$inscribeSheet(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (id >= 0) return;
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        if (self.slots.size() <= InscriptionTable.SCROLL_SLOT) return;
        Slot book = self.slots.get(InscriptionTable.BOOK_SLOT);
        Slot sheet = self.slots.get(InscriptionTable.SCROLL_SLOT);
        if (!InscriptionTable.isInstrument(book.getItem()) || !InscriptionTable.isSheet(sheet.getItem())) return;
        if (player instanceof ServerPlayer sp) InscriptionTable.inscribe(sp, book, sheet);
        cir.setReturnValue(true);
    }
}
