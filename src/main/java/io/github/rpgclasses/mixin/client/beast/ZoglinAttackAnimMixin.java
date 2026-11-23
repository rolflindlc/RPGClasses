package io.github.rpgclasses.mixin.client.beast;

import io.github.rpgclasses.client.druid.BeastAttackTicker;
import net.minecraft.world.entity.monster.Zoglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Descuenta la animación de ataque del cuerpo de bestia del Druida (ver {@link BeastAttackTicker}). */
@Mixin(Zoglin.class)
public abstract class ZoglinAttackAnimMixin implements BeastAttackTicker {
    @Shadow
    private int attackAnimationRemainingTicks;

    @Override
    public void rites_and_paths$tickAttackAnim() {
        if (this.attackAnimationRemainingTicks > 0) this.attackAnimationRemainingTicks--;
    }
}
