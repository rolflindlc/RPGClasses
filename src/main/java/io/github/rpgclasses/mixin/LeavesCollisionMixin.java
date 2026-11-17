package io.github.rpgclasses.mixin;

import io.github.rpgclasses.registry.ModAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Elfo, Paso entre Hojas: las hojas no tienen colisión para quien tenga el atributo {@code rites_and_paths:leaf_walker}. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class LeavesCollisionMixin {
    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("HEAD"), cancellable = true)
    private void rites_and_paths$leavesPassThrough(BlockGetter level, BlockPos pos, CollisionContext context,
                                              CallbackInfoReturnable<VoxelShape> cir) {
        if (context instanceof EntityCollisionContext entityContext && entityContext.getEntity() instanceof Player player
                && ((BlockBehaviour.BlockStateBase) (Object) this).is(BlockTags.LEAVES)
                && player.isAddedToWorld() && ModAttributes.value(player, ModAttributes.LEAF_WALKER) > 0) {
            cir.setReturnValue(Shapes.empty());
        }
    }
}
