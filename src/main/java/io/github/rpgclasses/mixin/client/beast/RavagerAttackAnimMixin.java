package io.github.rpgclasses.mixin.client.beast;

import io.github.rpgclasses.client.druid.BeastAttackTicker;
import net.minecraft.world.entity.monster.Ravager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Descuenta la animación de ataque del cuerpo de bestia del Druida (ver {@link BeastAttackTicker}). */
@Mixin(Ravager.class)
public abstract class RavagerAttackAnimMixin implements BeastAttackTicker {
    @Shadow
    private int attackTick;

    @Override
    public void rites_and_paths$tickAttackAnim() {
        if (this.attackTick > 0) this.attackTick--;
    }
}
