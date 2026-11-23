package io.github.rpgclasses.mixin.client.beast;

import io.github.rpgclasses.client.druid.BeastAttackTicker;
import net.minecraft.world.entity.animal.IronGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Descuenta la animación de ataque del cuerpo de bestia del Druida (ver {@link BeastAttackTicker}). */
@Mixin(IronGolem.class)
public abstract class IronGolemAttackAnimMixin implements BeastAttackTicker {
    @Shadow
    private int attackAnimationTick;

    @Override
    public void rites_and_paths$tickAttackAnim() {
        if (this.attackAnimationTick > 0) this.attackAnimationTick--;
    }
}
