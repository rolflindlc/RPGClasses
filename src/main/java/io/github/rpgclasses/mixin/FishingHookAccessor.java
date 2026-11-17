package io.github.rpgclasses.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Tritón Pescador: acortar la espera hasta que pica un pez. */
@Mixin(FishingHook.class)
public interface FishingHookAccessor {
    @Accessor("timeUntilLured")
    int rites_and_paths$getTimeUntilLured();

    @Accessor("timeUntilLured")
    void rites_and_paths$setTimeUntilLured(int value);
}
