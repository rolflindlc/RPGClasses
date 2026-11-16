package io.github.ironrituals.ritual.effect;

import io.github.ironrituals.registry.ModItems;
import io.github.ironrituals.ritual.RitualContext;
import io.github.ironrituals.ritual.RitualEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Ritual de Custodia (plan, 4.F.29): crea {@code seals} Sellos de Custodia (4 por defecto) sobre el núcleo. */
public class CustodyEffect implements RitualEffect {

    @Override
    public void apply(RitualContext ctx) {
        ServerLevel level = ctx.level();
        BlockPos pos = ctx.core();
        ItemEntity e = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                new ItemStack(ModItems.CUSTODY_SEAL.get(), Math.max(1, ctx.paramInt("seals", 4))));
        e.setDeltaMovement(0, 0.2, 0);
        e.setDefaultPickUpDelay();
        level.addFreshEntity(e);
        level.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5, 25, 0.3, 0.3, 0.3, 0.5);
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
    }
}
