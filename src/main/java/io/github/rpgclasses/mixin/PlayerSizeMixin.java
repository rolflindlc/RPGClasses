package io.github.rpgclasses.mixin;

import io.github.rpgclasses.content.race.RaceTraits;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tamaño racial (Orco, Enano, Goblin) en <b>todas</b> las poses del jugador.
 * <p>
 * Forge solo deja pasar el evento {@code EntityEvent.Size} para la pose actual; las comprobaciones de Minecraft
 * (¿cabe de pie?, ¿puede agacharse o nadar?) usan {@code getDimensions(pose)} sin escalar. Con el tamaño solo en el
 * evento, un Goblin de pie medía 1,8 bloques al decidir la pose y se ponía en la pose de nado (tumbado) al pasar
 * por huecos de 1 bloque, y un Orco chocaba con paredes que su hitbox ya no respetaba. Escalando aquí las
 * dimensiones de cada pose, la hitbox que ve el motor coincide con la que se dibuja. El evento solo ajusta la
 * altura de los ojos, en {@link RaceTraits#onSize}.
 */
@Mixin(Player.class)
public abstract class PlayerSizeMixin {
    @Inject(method = "getDimensions(Lnet/minecraft/world/entity/Pose;)Lnet/minecraft/world/entity/EntityDimensions;",
            at = @At("RETURN"), cancellable = true)
    private void rites_and_paths$scaleDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        Player self = (Player) (Object) this;
        // Centauro (adenda 0.7): hitbox de caballo en todas las poses salvo dormido.
        if (pose != Pose.SLEEPING && RaceTraits.isCentaur(self)) {
            cir.setReturnValue(EntityDimensions.scalable(RaceTraits.CENTAUR_WIDTH, RaceTraits.centaurHeight(pose)));
            return;
        }
        float s = RaceTraits.scale(self);
        if (s != 1f) cir.setReturnValue(cir.getReturnValue().scale(s));
    }
}
