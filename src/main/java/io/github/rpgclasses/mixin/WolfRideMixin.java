package io.github.rpgclasses.mixin;

import io.github.rpgclasses.content.race.Races;
import io.github.rpgclasses.content.race.WolfRider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Goblin Jinete de Lobo: el dueño montado controla a su lobo como a un caballo (el cliente del jinete mueve al
 * lobo y el servidor lo acepta porque el jinete es el pasajero que controla). Solo cuando el pasajero es el dueño;
 * quién puede montar lo decide el servidor en {@link WolfRider}.
 */
@Mixin(Wolf.class)
public abstract class WolfRideMixin extends TamableAnimal {
    protected WolfRideMixin(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
    }

    @Override
    public LivingEntity getControllingPassenger() {
        Player rider = WolfRider.rider((Wolf) (Object) this);
        return rider != null ? rider : super.getControllingPassenger();
    }

    @Override
    protected void tickRidden(Player rider, Vec3 input) {
        super.tickRidden(rider, input);
        setYRot(rider.getYRot());
        yRotO = getYRot();
        setXRot(rider.getXRot() * 0.5f);
        setRot(getYRot(), getXRot());
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        if (((LivingEntityAccessor) rider).rpgclasses$isJumping() && onGround()) jumpFromGround();
    }

    /** Salto del lobo montado: el de vanilla es muy corto. Solo cambia cuando hay jinete. */
    @Override
    public float getJumpPower() {
        float base = super.getJumpPower();
        Player rider = WolfRider.rider((Wolf) (Object) this);
        return rider == null ? base : base * (float) Races.number(rider, "race_wolf_mount", "jump_factor", 1.0);
    }

    @Override
    protected Vec3 getRiddenInput(Player rider, Vec3 input) {
        float strafe = rider.xxa * 0.5f;
        float forward = rider.zza;
        if (forward <= 0) forward *= 0.25f;
        return new Vec3(strafe, 0, forward);
    }

    /** Asiento sobre el lomo (vanilla lo pondría a 3/4 de la altura, metido en el lobo). */
    @Override
    public double getPassengersRidingOffset() {
        return getBbHeight() * 0.95;
    }

    @Override
    protected float getRiddenSpeed(Player rider) {
        return WolfRider.riddenSpeed((Wolf) (Object) this, rider);
    }
}
