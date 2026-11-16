package io.github.rpgclasses.content;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * Clon del Señuelo: atrae a los enemigos y huye de ellos. Desaparece en humo cuando su dueño deja de
 * estar en sigilo, si el dueño se va, o pasado un tiempo máximo.
 */
public class DecoyEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(DecoyEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    /** Escala del dueño (tamaño racial: Orco, Enano, Goblin) para que el clon mida lo mismo que él. */
    private static final EntityDataAccessor<Float> SCALE =
            SynchedEntityData.defineId(DecoyEntity.class, EntityDataSerializers.FLOAT);
    private static final int MAX_LIFETIME_TICKS = 20 * 60;
    /** Cada cuántos ticks da un salto mientras huye (como un jugador corriendo y saltando). */
    private static final int HOP_PERIOD_TICKS = 11;
    private int hopCooldown;

    public DecoyEntity(EntityType<? extends DecoyEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(OWNER, Optional.empty());
        entityData.define(SCALE, 1.0f);
    }

    /** Mismo tamaño que el jugador: 0,6 × 1,8 por la escala de su raza. */
    public void setScale(float scale) {
        entityData.set(SCALE, scale);
        refreshDimensions();
    }

    public float getScale() {
        return entityData.get(SCALE);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SCALE.equals(key)) refreshDimensions();
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return super.getDimensions(pose).scale(getScale());
    }

    @Override
    protected float getStandingEyeHeight(net.minecraft.world.entity.Pose pose, net.minecraft.world.entity.EntityDimensions dimensions) {
        return 1.62f * getScale();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Monster.class, 14.0f, 1.2, 1.6));
        goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1.0));
    }

    public void setOwner(UUID owner) {
        entityData.set(OWNER, Optional.ofNullable(owner));
    }

    public Optional<UUID> getOwnerUuid() {
        return entityData.get(OWNER);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        hop();
        ServerPlayer owner = getOwnerUuid().map(id -> level().getServer().getPlayerList().getPlayer(id)).orElse(null);
        if (shouldVanish(owner)) vanish();
    }

    /** El Señuelo desaparece si su dueño se va, deja el sigilo o pasa el tiempo máximo (las subclases lo cambian). */
    protected boolean shouldVanish(ServerPlayer owner) {
        return owner == null || tickCount > MAX_LIFETIME_TICKS || !RogueAbilities.isStealthed(owner);
    }

    /** Enemigos de los que huye (los mobs hostiles; las subclases añaden más). */
    protected boolean fleesFrom(net.minecraft.world.entity.LivingEntity e) {
        return e instanceof Monster && e.isAlive();
    }

    /**
     * Huye saltando como un jugador: mientras hay un enemigo cerca y el clon avanza, corre ("sprint") y da un salto
     * cada pocos ticks; si no, anda con normalidad. El salto sobre desniveles lo hace además el control de movimiento.
     */
    private void hop() {
        boolean fleeing = !level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, getBoundingBox().inflate(14.0),
                e -> e != this && fleesFrom(e)).isEmpty();
        boolean moving = getDeltaMovement().horizontalDistanceSqr() > 0.0004;
        setSprinting(fleeing && moving);
        if (hopCooldown > 0) hopCooldown--;
        if (fleeing && moving && onGround() && hopCooldown == 0) {
            getJumpControl().jump();
            hopCooldown = HOP_PERIOD_TICKS;
        }
    }

    /** Desaparece en una nube de humo. */
    public void vanish() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.POOF, getX(), getY() + 1.0, getZ(), 20, 0.3, 0.5, 0.3, 0.02);
        }
        discard();
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.POOF, getX(), getY() + 1.0, getZ(), 20, 0.3, 0.5, 0.3, 0.02);
        }
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // El dueño no puede romper su propio clon.
        if (source.getEntity() != null && getOwnerUuid().filter(id -> id.equals(source.getEntity().getUUID())).isPresent()) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    /** Un clon no se guarda: al recargar el mundo desaparece. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
