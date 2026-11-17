package io.github.rpgclasses.bard;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.content.CombatAbilities;
import io.github.rpgclasses.content.race.Races;
import io.github.rpgclasses.server.Allies;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Piano de Cola: una sombra grande aparece bajo el objetivo, lo sigue mientras se encoge y se queda fija el último
 * 0,8 s (ventana para esquivar); entonces cae un piano que golpea en área (2 bloques + 0,5 por nivel), aturde 1 s a
 * los no jefes y no rompe bloques ni daña a los aliados. Las fases se sincronizan con la edad ({@link #AGE}).
 */
public class GrandPianoEntity extends Entity {
    public static final ResourceKey<DamageType> PIANO = ResourceKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("grand_piano"));
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(GrandPianoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(GrandPianoEntity.class, EntityDataSerializers.FLOAT);
    /** Fase de sombra (ticks); los últimos {@link #FIXED} no sigue al objetivo. */
    public static final int SHADOW = 36, FIXED = 16, FALL = 6, LINGER = 14;

    private UUID owner, target;
    private float damage;
    private double radius = 2;
    private int stunTicks = 20;
    private int delay;

    public GrandPianoEntity(EntityType<? extends GrandPianoEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static void drop(ServerPlayer owner, LivingEntity target, float damage, double radius, int stunTicks, int delay) {
        GrandPianoEntity e = new GrandPianoEntity(BardEntities.GRAND_PIANO.get(), owner.level());
        e.owner = owner.getUUID();
        e.target = target.getUUID();
        e.damage = damage;
        e.radius = radius;
        e.stunTicks = stunTicks;
        e.delay = Math.max(0, delay);
        e.entityData.set(SIZE, (float) radius);
        Vec3 g = ground(owner.level(), target.position());
        e.setPos(g.x, g.y, g.z);
        owner.level().addFreshEntity(e);
    }

    private static Vec3 ground(Level level, Vec3 pos) {
        BlockHitResult hit = level.clip(new ClipContext(pos.add(0, 0.5, 0), pos.add(0, -12, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        return hit.getType() == HitResult.Type.MISS ? pos : hit.getLocation();
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(AGE, 0);
        entityData.define(SIZE, 2f);
    }

    /** Edad efectiva (ticks desde que empieza la sombra; negativa durante el retraso del segundo piano). */
    public int age() {
        return entityData.get(AGE);
    }

    public float size() {
        return entityData.get(SIZE);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        int age = tickCount - delay;
        entityData.set(AGE, age);
        if (age < 0) return;
        ServerLevel level = (ServerLevel) level();
        if (age < SHADOW - FIXED && target != null) {
            Entity t = level.getEntity(target);
            if (t != null && t.isAlive()) {
                Vec3 g = ground(level, t.position());
                setPos(g.x, g.y, g.z);
            }
        }
        if (age == SHADOW) {
            level.playSound(null, getX(), getY() + 8, getZ(), SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 1.5f, 0.5f);
        }
        if (age == SHADOW + FALL) impact(level);
        if (age > SHADOW + FALL + LINGER) discard();
    }

    private void impact(ServerLevel level) {
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.4f, 0.6f);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 2f, 0.5f);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.2f, 0.6f);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLACK_CONCRETE.defaultBlockState()), getX(), getY() + 0.3, getZ(), 40, radius / 2, 0.3, radius / 2, 0.2);
        level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 12, radius / 2, 0.1, radius / 2, 0.05);
        level.sendParticles(ParticleTypes.NOTE, getX(), getY() + 1, getZ(), 10, radius / 2, 0.5, radius / 2, 1);
        ServerPlayer p = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (p == null) return;
        DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(PIANO), this, p);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius, 2, radius))) {
            double dx = e.getX() - getX(), dz = e.getZ() - getZ();
            if (dx * dx + dz * dz > radius * radius || !Allies.isEnemy(p, e)) continue;
            e.hurt(source, damage);
            if (stunTicks > 0 && !Races.isBoss(e)) CombatAbilities.stun(e, stunTicks);
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
