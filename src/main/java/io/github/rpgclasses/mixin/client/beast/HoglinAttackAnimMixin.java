package io.github.rpgclasses.mixin.client.beast;

import io.github.rpgclasses.client.druid.BeastAttackTicker;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Descuenta la animación de ataque del cuerpo de bestia del Druida (ver {@link BeastAttackTicker}). */
@Mixin(Hoglin.class)
public abstract class HoglinAttackAnimMixin implements BeastAttackTicker {
    @Shadow
    private int attackAnimationRemainingTicks;

    @Override
    public void rites_and_paths$tickAttackAnim() {
        if (this.attackAnimationRemainingTicks > 0) this.attackAnimationRemainingTicks--;
    }
}
