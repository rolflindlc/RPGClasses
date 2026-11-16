package io.github.rpgclasses.content.race;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Slime pequeño del Slime Enjambre (adenda 0.7, 3.1). Sigue a su dueño a saltos, se lanza contra los enemigos cercanos
 * y les hace daño por contacto (lo calcula {@link SlimeForms#touch}). Lleva 1 corazón (o lo que toque al repartir la
 * vida). No es un mob hostil (los gólems no lo atacan), no suelta nada y no se guarda: al recargar el mundo desaparece.
 */
public class SwarmSlime extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(SwarmSlime.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final double JUMP = 0.42;

    // Aplastamiento al botar (solo visual, como el slime vanilla).
    public float targetSquish, squish, oSquish;
    private boolean wasOnGround;
    private int jumpDelay;
    private double airVy;
    private LivingEntity target;
    private int retarget;
    /** Enfriamiento del contacto por objetivo (1 s por slime). */
    final Map<UUID, Long> touched = new HashMap<>();

    public SwarmSlime(EntityType<? extends SwarmSlime> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 2.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(OWNER, Optional.empty());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    public void setOwner(UUID owner) {
        entityData.set(OWNER, Optional.ofNullable(owner));
    }

    public Optional<UUID> getOwnerUuid() {
        return entityData.get(OWNER);
    }

    private ServerPlayer owner() {
        if (level().getServer() == null) return null;
        return getOwnerUuid().map(id -> level().getServer().getPlayerList().getPlayer(id)).orElse(null);
    }

    @Override
    public void tick() {
        squish += (targetSquish - squish) * 0.5f;
        oSquish = squish;
        double vy = getDeltaMovement().y;
        super.tick();
        if (onGround() && !wasOnGround) {
            targetSquish = -0.5f;
            if (level().isClientSide) {
                for (int i = 0; i < 4; i++) {
                    float a = random.nextFloat() * (float) (Math.PI * 2);
                    float r = random.nextFloat() * 0.25f + 0.25f;
                    level().addParticle(ParticleTypes.ITEM_SLIME, getX() + Mth.sin(a) * r, getY(), getZ() + Mth.cos(a) * r, 0, 0, 0);
                }
            }
        } else if (!onGround() && wasOnGround) {
            targetSquish = 1.0f;
        }
        wasOnGround = onGround();
        targetSquish *= 0.6f;
        if (level().isClientSide) return;
        // Conserva el rebote de la raza: al caer desde alto, bota.
        if (onGround() && airVy < -0.45) setDeltaMovement(getDeltaMovement().x, -airVy * 0.8, getDeltaMovement().z);
        airVy = onGround() ? 0 : vy;
        ServerPlayer owner = owner();
        if (owner == null || !SlimeForms.isSwarming(owner)) {
            vanish();
            return;
        }
        SlimeForms.touch(owner, this, getBoundingBox().inflate(0.08), touched);
    }

    /** Movimiento: salta hacia el enemigo más cercano o, si no hay, hacia su dueño. */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerPlayer owner = owner();
        if (owner == null) return;
        double leash = Races.number(owner, "race_swarm", "leash", 32);
        if (owner.level() != level()) {
            vanish();
            return;
        }
        if (distanceToSqr(owner) > leash * leash) {
            // Correa: vuelve solo junto a su dueño.
            moveTo(owner.getX() + random.nextGaussian() * 0.8, owner.getY(), owner.getZ() + random.nextGaussian() * 0.8, getYRot(), 0);
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (--retarget <= 0 || target != null && !target.isAlive()) {
            target = SlimeForms.swarmTarget(owner, this);
            retarget = 10;
        }
        Vec3 goal = target != null ? target.position() : owner.position();
        double dx = goal.x - getX(), dz = goal.z - getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 0.01) {
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            setYRot(Mth.rotLerp(0.5f, getYRot(), yaw));
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        }
        if (!onGround()) return;
        if (jumpDelay > 0) {
            jumpDelay--;
            return;
        }
        boolean chase = target != null;
        if (!chase && dist < 2.5) return;
        double speed = (chase ? 0.32 : Math.min(0.6, 0.22 + dist * 0.04)) * (1 + Races.number(owner, "race_swarm", "slime_speed", 0));
        double f = Math.min(1, dist / 2 + 0.3);
        setDeltaMovement(dx / Math.max(dist, 1e-3) * speed * f, JUMP, dz / Math.max(dist, 1e-3) * speed * f);
        hasImpulse = true;
        jumpDelay = chase ? 3 + random.nextInt(5) : (dist > 8 ? 2 : 6 + random.nextInt(8));
        playSound(SoundEvents.SLIME_JUMP_SMALL, 0.35f, (random.nextFloat() - random.nextFloat()) * 0.2f + 1.0f);
    }

    /** No empuja a su dueño (el slime que controla). */
    @Override
    protected void doPush(Entity other) {
        if (getOwnerUuid().filter(id -> id.equals(other.getUUID())).isPresent()) return;
        super.doPush(other);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Su dueño no puede romper sus propios slimes.
        Entity attacker = source.getEntity();
        if (attacker != null && getOwnerUuid().filter(id -> id.equals(attacker.getUUID())).isPresent()) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SLIME_BALL)), getX(), getY() + 0.25, getZ(),
                    10, 0.2, 0.2, 0.2, 0.05);
            playSound(SoundEvents.SLIME_DEATH_SMALL, 0.8f, 1.0f);
        }
        discard();
    }

    /** Se reabsorbe en su dueño (o desaparece si este ya no está). */
    public void vanish() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 0.25, getZ(), 6, 0.2, 0.2, 0.2, 0.02);
        }
        discard();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SLIME_HURT_SMALL;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SLIME_DEATH_SMALL;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }
}
