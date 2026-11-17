package io.github.rpgclasses.mixin;

import io.github.rpgclasses.server.Allies;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sistema de aliados: {@code Entity.isAlliedTo} sigue la lista de aliados cuando las dos entidades tienen dueño
 * jugador (jugadores, mascotas, invocaciones). Así lo respetan vanilla (barrido, mascotas, objetivos de mobs),
 * Iron's (fuego amigo de conjuros, invocaciones) y Epic Fight. Si alguna no tiene dueño jugador, no se toca nada.
 */
@Mixin(Entity.class)
public abstract class AllyEntityMixin {
    @Inject(method = "isAlliedTo(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void rites_and_paths$allies(Entity other, CallbackInfoReturnable<Boolean> cir) {
        Boolean allied = Allies.alliedOverride((Entity) (Object) this, other);
        if (allied != null) cir.setReturnValue(allied);
    }
}
