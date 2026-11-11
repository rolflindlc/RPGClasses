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
    private static final int MAX_LIFETIME_TICKS = 20 * 60;

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
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Monster.class, 14.0f, 1.1, 1.5));
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
        ServerPlayer owner = getOwnerUuid().map(id -> level().getServer().getPlayerList().getPlayer(id)).orElse(null);
        if (owner == null || tickCount > MAX_LIFETIME_TICKS || !RogueAbilities.isStealthed(owner)) vanish();
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
