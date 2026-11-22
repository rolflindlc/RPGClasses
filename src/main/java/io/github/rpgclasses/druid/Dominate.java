package io.github.rpgclasses.druid;

import io.github.rpgclasses.server.Allies;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Dominar Animal (adenda, 5): el animal obedece al Druida unos minutos como una mascota (cuenta como aliado suyo en
 * todos los efectos del mod). Le sigue, se teletransporta si se queda muy atrás y, si sabe atacar, ataca a quien
 * golpea al Druida o a quien el Druida golpea. Nunca ataca al Druida ni a sus aliados. El dueño y el final se guardan
 * en la entidad, así que sobrevive a que se descargue (al volver se le ponen de nuevo los objetivos si no ha acabado).
 */
public final class Dominate {
    private static final String OWNER = "rites_and_paths:dominated_by";
    private static final String UNTIL = "rites_and_paths:dominated_until";
    private static final Map<Mob, List<Goal>> GOALS = new WeakHashMap<>();

    static void apply(Mob mob, ServerPlayer owner, int ticks) {
        CompoundTag tag = mob.getPersistentData();
        tag.putUUID(OWNER, owner.getUUID());
        tag.putLong(UNTIL, mob.level().getGameTime() + ticks);
        mob.setTarget(null);
        attach(mob);
    }

    /** Dueño (jugador) de un animal dominado y aún dentro de su tiempo, o null. Lo usa {@link Allies#ownerOf}. */
    public static UUID ownerOf(Entity e) {
        if (!(e instanceof Mob mob) || e.level().isClientSide) return null;
        CompoundTag tag = mob.getPersistentData();
        if (!tag.hasUUID(OWNER)) return null;
        if (mob.level().getGameTime() > tag.getLong(UNTIL)) return null;
        return tag.getUUID(OWNER);
    }

    private static void attach(Mob mob) {
        detach(mob);
        List<Goal> goals = new ArrayList<>();
        goals.add(new FollowGoal(mob));
        mob.goalSelector.addGoal(1, goals.get(0));
        if (mob instanceof PathfinderMob pm && mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            Goal attack = new MeleeAttackGoal(pm, 1.2, true);
            mob.goalSelector.addGoal(0, attack);
            goals.add(attack);
        }
        Goal defend = new DefendGoal(mob);
        mob.targetSelector.addGoal(0, defend);
        goals.add(defend);
        GOALS.put(mob, goals);
    }

    private static void detach(Mob mob) {
        List<Goal> goals = GOALS.remove(mob);
        if (goals == null) return;
        for (Goal g : goals) {
            mob.goalSelector.removeGoal(g);
            mob.targetSelector.removeGoal(g);
        }
    }

    /** Se acabó el tiempo: quita las marcas y los objetivos, y avisa al dueño. */
    private static void release(Mob mob) {
        CompoundTag tag = mob.getPersistentData();
        UUID owner = tag.hasUUID(OWNER) ? tag.getUUID(OWNER) : null;
        tag.remove(OWNER);
        tag.remove(UNTIL);
        detach(mob);
        mob.setTarget(null);
        MinecraftServer server = mob.getServer();
        ServerPlayer p = owner == null || server == null ? null : server.getPlayerList().getPlayer(owner);
        if (p != null) p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.dominate_end", mob.getDisplayName()), true);
    }

    private static ServerPlayer owner(Mob mob) {
        UUID id = ownerOf(mob);
        MinecraftServer server = mob.getServer();
        return id == null || server == null ? null : server.getPlayerList().getPlayer(id);
    }

    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) return;
        CompoundTag tag = mob.getPersistentData();
        if (!tag.hasUUID(OWNER)) return;
        if (ownerOf(mob) == null) {
            tag.remove(OWNER);
            tag.remove(UNTIL);
        } else {
            attach(mob);
        }
    }

    /** Un animal dominado no ataca a su dueño ni a los aliados de su dueño. */
    @SubscribeEvent
    public void onTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || event.getNewTarget() == null) return;
        ServerPlayer owner = owner(mob);
        if (owner != null && Allies.isAlly(owner, event.getNewTarget())) event.setCanceled(true);
    }

    /** Sigue al dueño; se teletransporta si se queda muy atrás. Termina (y suelta al animal) al acabar el tiempo. */
    private static final class FollowGoal extends Goal {
        private final Mob mob;
        private ServerPlayer owner;
        private int recalc;

        FollowGoal(Mob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (mob.getPersistentData().hasUUID(OWNER) && ownerOf(mob) == null) {
                release(mob);
                return false;
            }
            owner = owner(mob);
            if (owner == null || owner.isSpectator() || owner.level() != mob.level()) return false;
            if (mob.getTarget() != null && mob.getTarget().isAlive()) return false;
            return mob.distanceToSqr(owner) > follow() * follow();
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null && owner.isAlive() && owner.level() == mob.level() && ownerOf(mob) != null
                    && (mob.getTarget() == null || !mob.getTarget().isAlive()) && mob.distanceToSqr(owner) > 4;
        }

        @Override
        public void stop() {
            owner = null;
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (owner == null) return;
            mob.getLookControl().setLookAt(owner, 10f, mob.getMaxHeadXRot());
            if (--recalc > 0) return;
            recalc = 10;
            double tp = Druid.number(Druid.DOMINATE_BEAST, "teleport_distance", 24);
            if (mob.distanceToSqr(owner) > tp * tp) {
                mob.moveTo(owner.getX(), owner.getY(), owner.getZ(), mob.getYRot(), mob.getXRot());
                mob.getNavigation().stop();
                if (mob.level() instanceof ServerLevel sl) {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, mob.getX(), mob.getY() + .5, mob.getZ(), 10, .3, .3, .3, .1);
                }
                return;
            }
            mob.getNavigation().moveTo(owner, 1.2);
        }

        private double follow() {
            return Druid.number(Druid.DOMINATE_BEAST, "follow_distance", 6);
        }
    }

    /** Ataca a quien golpea a su dueño, o a quien el dueño golpea (si es enemigo del dueño). */
    private static final class DefendGoal extends Goal {
        private final Mob mob;

        DefendGoal(Mob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            ServerPlayer owner = owner(mob);
            if (owner == null) return false;
            LivingEntity t = owner.getLastHurtByMob();
            if (t == null || !t.isAlive() || !Allies.isEnemy(owner, t) || t == mob) t = owner.getLastHurtMob();
            if (t == null || !t.isAlive() || t == mob || !Allies.isEnemy(owner, t)) return false;
            if (t == mob.getTarget()) return false;
            mob.setTarget(t);
            return false;
        }
    }
}
