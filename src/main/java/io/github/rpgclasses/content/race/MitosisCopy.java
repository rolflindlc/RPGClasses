package io.github.rpgclasses.content.race;

import io.github.rpgclasses.content.DecoyEntity;
import io.github.rpgclasses.server.Allies;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.EnumSet;

/**
 * Copia del Slime Mitótico (adenda 0.7, 3.3): el clon del jugador (con su aspecto, como el Señuelo) que no controla.
 * Huye de las criaturas hostiles y de los jugadores enemigos y, si no hay peligro, se acerca a su dueño. No lleva
 * inventario. Si muere, el jugador sigue con la otra; agachado junto a ella durante unos segundos se fusionan.
 */
public class MitosisCopy extends DecoyEntity {
    public MitosisCopy(EntityType<? extends MitosisCopy> type, Level level) {
        super(type, level);
    }

    private ServerPlayer owner() {
        if (level().getServer() == null) return null;
        return getOwnerUuid().map(id -> level().getServer().getPlayerList().getPlayer(id)).orElse(null);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, LivingEntity.class, 12.0f, 1.2, 1.6, this::fleesFrom));
        goalSelector.addGoal(2, new FollowOwnerGoal());
    }

    /** Huye de los mobs hostiles, de los que atacan a su dueño y de los jugadores enemigos. */
    @Override
    protected boolean fleesFrom(LivingEntity e) {
        if (!e.isAlive() || e == this) return false;
        ServerPlayer owner = owner();
        if (owner == null || e == owner) return false;
        if (e instanceof Player) return Allies.isEnemy(owner, e) && !Allies.isAlly(owner, e);
        return e instanceof Enemy || e instanceof Mob m && m.getTarget() == owner;
    }

    /** Desaparece si su dueño se va o ya no es su copia (se fusionaron, murió o se acabó la división). */
    @Override
    protected boolean shouldVanish(ServerPlayer owner) {
        return owner == null || !SlimeForms.isCopyOf(owner, this);
    }

    /** Si no hay peligro, se acerca a su dueño (sin pegarse a él). */
    private final class FollowOwnerGoal extends Goal {
        FollowOwnerGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            ServerPlayer owner = owner();
            return owner != null && owner.level() == level() && distanceToSqr(owner) > 9;
        }

        @Override
        public void tick() {
            ServerPlayer owner = owner();
            if (owner == null) return;
            if (getNavigation().isDone() || tickCount % 10 == 0) getNavigation().moveTo(owner, distanceToSqr(owner) > 64 ? 1.5 : 1.1);
        }

        @Override
        public boolean canContinueToUse() {
            ServerPlayer owner = owner();
            return owner != null && owner.level() == level() && distanceToSqr(owner) > 4;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }
}
