package io.github.rpgclasses.mixin.irons;

import io.github.rpgclasses.bard.InscriptionTable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mesa de inscripción de Iron's con un instrumento en la ranura del libro:
 * <ul>
 *   <li>Inscribir: el botón {@link InscriptionTable#INSCRIBE_BUTTON} con una partitura lo hace {@link InscriptionTable}.</li>
 *   <li>Sacar: al elegir un hueco, su partitura sale en la ranura de resultado (Iron's solo lo hace con libros); al
 *       cogerla se quita del instrumento ({@code InscriptionResultSlotMixin} → {@link #rites_and_paths$onResultTaken}).</li>
 * </ul>
 * Con un libro y un pergamino todo sigue el camino de Iron's. No se usan @Shadow: si Iron's cambia un nombre, la
 * inyección afectada no se aplica ({@code require = 0}) en vez de impedir que arranque el juego.
 */
// @Pseudo: la clase es de Iron's (no está en el jar de API con el que se compila) y puede no existir.
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu", remap = false)
public abstract class InscriptionMenuMixin implements InscriptionTable.Menu {
    /** Hueco elegido + 1 (0 = ninguno), copia del {@code selectedSpellIndex} privado de Iron's. */
    @Unique
    private int rites_and_paths$selected;

    @Unique
    private AbstractContainerMenu rites_and_paths$self() {
        return (AbstractContainerMenu) (Object) this;
    }

    @Unique
    private Slot rites_and_paths$slot(int index) {
        AbstractContainerMenu self = rites_and_paths$self();
        return self.slots.size() > InscriptionTable.RESULT_SLOT ? self.slots.get(index) : null;
    }

    @Unique
    private boolean rites_and_paths$hasInstrument() {
        Slot book = rites_and_paths$slot(InscriptionTable.BOOK_SLOT);
        return book != null && InscriptionTable.isInstrument(book.getItem());
    }

    /** Pone en la ranura de resultado la partitura del hueco elegido. Solo el servidor: el cliente la recibe sincronizada. */
    @Unique
    private void rites_and_paths$updateResult() {
        if (EffectiveSide.get().isClient()) return;
        Slot book = rites_and_paths$slot(InscriptionTable.BOOK_SLOT);
        Slot out = rites_and_paths$slot(InscriptionTable.RESULT_SLOT);
        if (book == null || out == null) return;
        ItemStack result = InscriptionTable.extractPreview(book.getItem(), rites_and_paths$selected - 1);
        if (!ItemStack.matches(result, out.getItem())) out.set(result);
    }

    @Inject(method = {"m_6366_(Lnet/minecraft/world/entity/player/Player;I)Z", "clickMenuButton(Lnet/minecraft/world/entity/player/Player;I)Z"},
            at = @At("HEAD"), cancellable = true, require = 0)
    private void rites_and_paths$buttons(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (id == InscriptionTable.INSCRIBE_BUTTON) {
            Slot book = rites_and_paths$slot(InscriptionTable.BOOK_SLOT);
            Slot sheet = rites_and_paths$slot(InscriptionTable.SCROLL_SLOT);
            if (book != null && sheet != null && player instanceof ServerPlayer sp
                    && InscriptionTable.isInstrument(book.getItem()) && InscriptionTable.isSheet(sheet.getItem())) {
                InscriptionTable.inscribe(sp, book, sheet);
            }
            cir.setReturnValue(true);
        } else if (id < 0 && rites_and_paths$hasInstrument()) {
            // -1 de Iron's con un instrumento: solo «sin selección» (nunca inscribe).
            rites_and_paths$selected = 0;
            rites_and_paths$updateResult();
            cir.setReturnValue(true);
        }
        // id >= 0: Iron's llama a setSelectedSpell, que copiamos abajo, y luego a setupResultSlot.
    }

    @Inject(method = "setSelectedSpell(I)V", at = @At("HEAD"), require = 0)
    private void rites_and_paths$copySelection(int index, CallbackInfo ci) {
        rites_and_paths$selected = Math.max(0, index + 1);
    }

    /** Iron's solo prepara el pergamino a sacar si hay un libro; con un instrumento lo hacemos nosotros. */
    @Inject(method = "setupResultSlot()V", at = @At("HEAD"), cancellable = true, require = 0)
    private void rites_and_paths$instrumentResult(CallbackInfo ci) {
        if (!rites_and_paths$hasInstrument()) return;
        rites_and_paths$updateResult();
        ci.cancel();
    }

    @Override
    public boolean rites_and_paths$onResultTaken(Player player) {
        if (!rites_and_paths$hasInstrument()) return false;
        int index = rites_and_paths$selected - 1;
        // Antes de tocar el instrumento: al cambiarlo, Iron's recalcula el resultado y no debe salir la siguiente.
        rites_and_paths$selected = 0;
        if (player instanceof ServerPlayer sp) InscriptionTable.extract(sp, rites_and_paths$slot(InscriptionTable.BOOK_SLOT), index);
        rites_and_paths$updateResult();
        return true;
    }
}
