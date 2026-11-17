package io.github.rpgclasses.mixin;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Infernal Fundidor: hornos cercanos más rápidos. */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface FurnaceAccessor {
    @Accessor("litTime")
    int rites_and_paths$getLitTime();

    @Accessor("cookingProgress")
    int rites_and_paths$getCookingProgress();

    @Accessor("cookingProgress")
    void rites_and_paths$setCookingProgress(int value);

    @Accessor("cookingTotalTime")
    int rites_and_paths$getCookingTotalTime();
}
