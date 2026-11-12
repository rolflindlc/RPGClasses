package io.github.rpgclasses.mixin;

import io.github.rpgclasses.server.Allies;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sistema de aliados con Iron's Spells: sus conjuros de apoyo (curar, Prisa, etc.) eligen objetivo con
 * {@code Utils.shouldHealEntity}, que sin equipos cura a cualquier jugador. Entre entidades con dueño jugador,
 * ahora solo alcanzan a quien el lanzador tiene como aliado (y a sus mascotas e invocaciones).
 * Sin Iron's instalado, el mixin no se aplica.
 */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.api.util.Utils", remap = false)
public abstract class IronsHealTargetMixin {
    @Inject(method = "shouldHealEntity(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void rpgclasses$allies(Entity healer, Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (healer == null || target == null || healer == target) return;
        Boolean allied = Allies.alliedOverride(healer, target);
        if (allied != null) cir.setReturnValue(allied);
    }
}
