package io.github.rpgclasses.mixin;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Infernal Fundidor: hornos cercanos más rápidos. */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface FurnaceAccessor {
    @Accessor("litTime")
    int rpgclasses$getLitTime();

    @Accessor("cookingProgress")
    int rpgclasses$getCookingProgress();

    @Accessor("cookingProgress")
    void rpgclasses$setCookingProgress(int value);

    @Accessor("cookingTotalTime")
    int rpgclasses$getCookingTotalTime();
}
