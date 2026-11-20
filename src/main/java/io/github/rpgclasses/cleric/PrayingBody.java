package io.github.rpgclasses.cleric;

import io.github.rpgclasses.content.DecoyEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Cuerpo del Clérigo mientras reza (Plegaria del Intercesor): una copia agachada con su aspecto, su armadura y lo que
 * lleva en las manos, en el sitio donde empezó a rezar. El jugador real va como una cabeza junto al aliado.
 * <ul>
 *   <li>No se mueve, no se empuja y no recibe empuje.</li>
 *   <li>Los golpes que recibe se pasan al jugador ({@link Prayer#hurtBody}); el propio cuerpo no pierde vida. Los mobs
 *       que iban a por el jugador van a por él.</li>
 *   <li>Desaparece al acabar la Plegaria o si su dueño se va. No se guarda en el mundo.</li>
 * </ul>
 */
public class PrayingBody extends DecoyEntity {
    /** Inclinación de la cabeza (mira al suelo, rezando). */
    private static final float HEAD_PITCH = 25f;

    public PrayingBody(EntityType<? extends PrayingBody> type, Level level) {
        super(type, level);
        setPose(Pose.CROUCHING);
    }

    @Override
    protected void registerGoals() {
        // Quieto: sin objetivos de IA.
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(0.6f, 1.5f).scale(getScale());
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return 1.27f * getScale();
    }

    @Override
    public void tick() {
        super.tick();
        // Siempre mirando adonde miraba el Clérigo al empezar, con la cabeza gacha.
        float yaw = getYRot();
        yBodyRot = yBodyRotO = yaw;
        yHeadRot = yHeadRotO = yaw;
        setXRot(HEAD_PITCH);
        xRotO = HEAD_PITCH;
        if (getPose() != Pose.CROUCHING) setPose(Pose.CROUCHING);
    }

    @Override
    protected boolean shouldVanish(ServerPlayer owner) {
        return owner == null || !Prayer.isBodyOf(owner, this);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) return false;
        // /kill, el vacío...: el cuerpo desaparece y la Plegaria se corta (el jugador vuelve a su sitio).
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            vanish();
            return true;
        }
        ServerPlayer owner = getOwnerUuid().map(id -> level().getServer().getPlayerList().getPlayer(id)).orElse(null);
        if (owner == null || source.getEntity() == owner) return false;
        boolean hit = Prayer.hurtBody(owner, source, amount);
        if (hit) level().broadcastDamageEvent(this, source);
        return hit;
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // El cuerpo no se mueve.
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
        // Ni empuja ni lo empujan.
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }
}
