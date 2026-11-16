package io.github.rpgclasses.mixin;

import io.github.rpgclasses.content.race.BodyTraits;
import io.github.rpgclasses.content.race.RaceTraits;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Pasajeros sobre un jugador (adenda de razas 0.7): el Centauro y el Coloso Porteador llevan 1 o 2 aliados.
 * Vanilla deja un solo pasajero por entidad y lo sienta en el centro; aquí se admiten los del Rango y se colocan
 * sobre el lomo del Centauro (uno detrás de otro) o sobre los hombros del Coloso (uno a cada lado).
 */
@Mixin(Player.class)
public abstract class PlayerPassengerMixin extends LivingEntity {
    protected PlayerPassengerMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        int max = BodyTraits.maxPassengers((Player) (Object) this);
        return max > 0 ? this.getPassengers().size() < max : super.canAddPassenger(passenger);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!this.hasPassenger(passenger)) return;
        Player self = (Player) (Object) this;
        int index = this.getPassengers().indexOf(passenger);
        double y = this.getY() + this.getPassengersRidingOffset() + passenger.getMyRidingOffset();
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw); // hacia delante
        double ox = 0, oz = 0;
        if (RaceTraits.isCentaur(self)) {
            double back = 0.45 + 0.45 * index;   // sobre el lomo, detrás del torso
            ox = -fx * back;
            oz = -fz * back;
        } else if (this.getPassengers().size() > 1) {
            double side = (index == 0 ? 1 : -1) * 0.25 * this.getBbWidth(); // un pasajero en cada hombro
            ox = -fz * side;
            oz = fx * side;
        }
        move.accept(passenger, this.getX() + ox, y, this.getZ() + oz);
    }
}
