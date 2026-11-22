package io.github.rpgclasses.druid;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/**
 * Restos de una bestia recién muerta: lo que queda tumbado unos segundos para que el Druida haga su ritual. Solo
 * aparecen si hay un Druida cerca. No se guardan con el mundo y no se pueden golpear; desaparecen al pasar el margen
 * máximo del ritual. El cliente los dibuja con el modelo de la especie tumbado.
 */
public class BeastRemains extends Entity {
    private static final EntityDataAccessor<String> DATA_TYPE = SynchedEntityData.defineId(BeastRemains.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_YAW = SynchedEntityData.defineId(BeastRemains.class, EntityDataSerializers.FLOAT);

    /** Criatura que murió (cada una solo vale para un ritual por Druida). */
    private UUID creature = new UUID(0, 0);
    private int lifetime = 600;

    public BeastRemains(EntityType<? extends BeastRemains> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static BeastRemains of(Level level, Entity dead, int lifetime) {
        BeastRemains r = new BeastRemains(DruidRegistry.BEAST_REMAINS.get(), level);
        r.moveTo(dead.getX(), dead.getY(), dead.getZ(), dead.getYRot(), 0);
        r.entityData.set(DATA_TYPE, Beasts.id(dead.getType()));
        r.entityData.set(DATA_YAW, dead instanceof net.minecraft.world.entity.LivingEntity le ? le.yBodyRot : dead.getYRot());
        r.creature = dead.getUUID();
        r.lifetime = lifetime;
        return r;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_TYPE, "");
        entityData.define(DATA_YAW, 0f);
    }

    public String species() {
        return entityData.get(DATA_TYPE);
    }

    public float bodyYaw() {
        return entityData.get(DATA_YAW);
    }

    public UUID creature() {
        return creature;
    }

    /** Segundos desde la muerte. */
    public double ageSeconds() {
        return tickCount / 20.0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (tickCount % 8 == 0) {
                level().addParticle(ParticleTypes.SOUL, getX() + (random.nextDouble() - .5) * .8, getY() + .2,
                        getZ() + (random.nextDouble() - .5) * .8, 0, .02, 0);
            }
        } else if (tickCount > lifetime) {
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
