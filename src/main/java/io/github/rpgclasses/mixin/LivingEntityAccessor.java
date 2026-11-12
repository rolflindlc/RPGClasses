package io.github.rpgclasses.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Goblin Jinete de Lobo: saber si el jinete pulsa saltar. */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("jumping")
    boolean rpgclasses$isJumping();
}
