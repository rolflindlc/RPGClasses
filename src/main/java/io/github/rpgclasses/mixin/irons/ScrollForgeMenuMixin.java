package io.github.rpgclasses.mixin.irons;

import io.github.rpgclasses.compat.irons.music.MusicForge;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Forja de pergaminos de Iron's (cliente y servidor): cuando el pergamino que va a salir es de un conjuro musical, la
 * ranura de resultado recibe la partitura. Se cambia la variable justo después de que Iron's rellene el pergamino
 * ({@code createScrollContainer}), así que el resto del método (comparar con lo que ya hay y colocarlo) sigue igual y
 * no hay bucles. Sin @Shadow y con {@code require = 0}: si Iron's cambia, la forja sigue dando pergaminos y el juego
 * arranca.
 */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.gui.scroll_forge.ScrollForgeMenu", remap = false)
public abstract class ScrollForgeMenuMixin {
    @ModifyVariable(method = "setupResultSlot(Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;)V",
            at = @At(value = "INVOKE", shift = At.Shift.AFTER,
                    target = "Lio/redspace/ironsspellbooks/api/spells/ISpellContainer;createScrollContainer(Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;ILnet/minecraft/world/item/ItemStack;)Lio/redspace/ironsspellbooks/api/spells/ISpellContainer;"),
            index = 5, remap = false, require = 0)
    private ItemStack rites_and_paths$sheetInsteadOfScroll(ItemStack scroll) {
        return MusicForge.toSheet(scroll);
    }
}
