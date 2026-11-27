package io.github.rpgclasses.engineer;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Mina del Artificiero (adenda del Ingeniero, 4.3): se lanza, cae al suelo y, al segundo, se arma y se vuelve invisible
 * para los enemigos (el cliente solo la dibuja a su dueño y a sus aliados). Explota al pisarla un enemigo, sin romper
 * bloques.
 */
public class MineEntity extends Entity implements OwnableEntity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(MineEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> ARMED = SynchedEntityData.defineId(MineEntity.class, EntityDataSerializers.BOOLEAN);
    private float damage = 7;
    private double blastRadius = 2;
    private int armTicks = 20;

    public MineEntity(EntityType<? extends MineEntity> type, Level level) {
        super(type, level);
    }

    void setup(ServerPlayer owner, float damage, double blastRadius, int armTicks) {
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        this.damage = damage;
        this.blastRadius = blastRadius;
        this.armTicks = armTicks;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(OWNER, Optional.empty());
        entityData.define(ARMED, false);
    }

    @Override
    public UUID getOwnerUUID() {
        return entityData.get(OWNER).orElse(null);
    }

    public boolean armed() {
        return entityData.get(ARMED);
    }

    @Override
    public void tick() {
        super.tick();
        if (!isNoGravity()) setDeltaMovement(getDeltaMovement().add(0, -0.06, 0));
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(onGround() ? Vec3.ZERO : getDeltaMovement().scale(0.98));
        if (level().isClientSide) return;
        if (!armed() && onGround() && tickCount >= armTicks) entityData.set(ARMED, true);
        if (!armed()) return;
        ServerLevel level = (ServerLevel) level();
        UUID owner = getOwnerUUID();
        boolean triggered = !level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.4, 0.6, 0.4),
                e -> EngineerAbilities.hostile(level, owner, e)).isEmpty();
        if (triggered) {
            EngineerAbilities.blast(level, owner, position(), blastRadius, damage, this);
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) entityData.set(OWNER, Optional.of(tag.getUUID("Owner")));
        damage = tag.getFloat("Damage");
        blastRadius = tag.getDouble("Radius");
        entityData.set(ARMED, tag.getBoolean("Armed"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID owner = getOwnerUUID();
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putFloat("Damage", damage);
        tag.putDouble("Radius", blastRadius);
        tag.putBoolean("Armed", armed());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }

    /** Para quien no puede verla (el cliente lo consulta al dibujar). */
    public boolean visibleTo(Entity viewer, java.util.function.Predicate<UUID> isAlly) {
        UUID owner = getOwnerUUID();
        return !armed() || owner == null || viewer.getUUID().equals(owner) || isAlly.test(owner);
    }
}
