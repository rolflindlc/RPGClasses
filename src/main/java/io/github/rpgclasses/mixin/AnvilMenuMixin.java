package io.github.rpgclasses.mixin;

import io.github.rpgclasses.content.race.CraftTraits;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Enano Herrero y Elfo Arcano: el yunque marca lo reparado (Herrero) y cuesta menos niveles. */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
    @Shadow @Final private DataSlot cost;

    @Inject(method = "createResult()V", at = @At("RETURN"))
    private void rpgclasses$raceAnvil(CallbackInfo ci) {
        AnvilMenu self = (AnvilMenu) (Object) this;
        ItemStack result = self.getSlot(2).getItem();
        int before = cost.get();
        int after = CraftTraits.onAnvil(self, result, before);
        if (after != before) cost.set(after);
    }
}
