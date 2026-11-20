package io.github.rpgclasses.mixin.irons;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clérigo: marca el final de {@code AbstractSpell.castSpell} de Iron's (servidor). Dentro de ese método Iron's avisa
 * con {@code SpellOnCastEvent} y ejecuta el conjuro ({@code onCast}); al terminar:
 * <ul>
 *   <li>los efectos que se apliquen ya no son de ese conjuro (Bendición, {@code ClericBuffs});</li>
 *   <li>el muro de Magia vuelve tras un conjuro exento de él ({@code SpellExemptions}, flechas mágicas del Ranger).</li>
 * </ul>
 * Con {@code require = 0}: si Iron's cambia, las dos cosas se resuelven al final del tick.
 */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.api.spells.AbstractSpell", remap = false)
public abstract class CastSpellMixin {
    @Inject(method = "castSpell(Lnet/minecraft/world/level/Level;ILnet/minecraft/server/level/ServerPlayer;Lio/redspace/ironsspellbooks/api/spells/CastSource;Z)V",
            at = @At("RETURN"), remap = false, require = 0)
    private void rites_and_paths$castEnd(CallbackInfo ci) {
        io.github.rpgclasses.cleric.ClericBuffs.castEnd();
        io.github.rpgclasses.compat.irons.SpellExemptions.end();
    }
}
