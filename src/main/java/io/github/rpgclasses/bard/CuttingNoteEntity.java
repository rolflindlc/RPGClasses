package io.github.rpgclasses.bard;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.server.Allies;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Nota Cortante: proyectil recto sin gravedad que hace daño sónico (escuela musical) a los enemigos que cruza. Atraviesa
 * tantos enemigos como diga el nivel del conjuro y desaparece al chocar con un bloque o al llegar a su alcance.
 * Se dibuja como una nota musical ({@link ItemSupplier}).
 */
public class CuttingNoteEntity extends Projectile implements ItemSupplier {
    public static final ResourceKey<DamageType> MUSIC_MAGIC = ResourceKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("music_magic"));
    private static final double SPEED = 2.0;

    private float damage;
    private int pierce;
    private double range = 20;
    private Vec3 origin;
    private final Set<Integer> hit = new HashSet<>();

    public CuttingNoteEntity(EntityType<? extends CuttingNoteEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static CuttingNoteEntity shoot(ServerPlayer owner, float damage, int pierce, double range) {
        CuttingNoteEntity e = new CuttingNoteEntity(BardEntities.CUTTING_NOTE.get(), owner.level());
        e.setOwner(owner);
        e.damage = damage;
        e.pierce = Math.max(0, pierce);
        e.range = range;
        Vec3 eye = owner.getEyePosition();
        Vec3 look = owner.getLookAngle();
        e.setPos(eye.x + look.x * 0.5, eye.y - 0.1 + look.y * 0.5, eye.z + look.z * 0.5);
        e.origin = e.position();
        e.setDeltaMovement(look.scale(SPEED));
        owner.level().addFreshEntity(e);
        return e;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 start = position();
        Vec3 motion = getDeltaMovement();
        Vec3 end = start.add(motion);
        if (level().isClientSide) {
            level().addParticle(ParticleTypes.NOTE, getX(), getY(), getZ(), (tickCount % 24) / 24.0, 0, 0);
            setPos(end);
            return;
        }
        if (origin == null || tickCount > 40) {
            discard();
            return;
        }
        BlockHitResult block = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean wall = block.getType() != HitResult.Type.MISS;
        if (wall) end = block.getLocation();
        Entity owner = getOwner();
        AABB box = getBoundingBox().expandTowards(motion).inflate(0.4);
        for (Entity e : level().getEntities(this, box, x -> x instanceof LivingEntity && x.isAlive())) {
            if (hit.contains(e.getId()) || e == owner || !(owner instanceof ServerPlayer p) || !Allies.isEnemy(p, (LivingEntity) e)) continue;
            AABB eb = e.getBoundingBox().inflate(0.3);
            if (!eb.contains(start) && eb.clip(start, end).isEmpty()) continue;
            hit.add(e.getId());
            e.hurt(source(p), damage);
            ((ServerLevel) level()).sendParticles(ParticleTypes.NOTE, e.getX(), e.getY() + e.getBbHeight() * 0.7, e.getZ(), 4, 0.3, 0.3, 0.3, 1);
            if (hit.size() > pierce) {
                discard();
                return;
            }
        }
        if (wall || end.distanceToSqr(origin) > range * range) {
            level().playSound(null, end.x, end.y, end.z, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
            discard();
            return;
        }
        setPos(end);
    }

    private DamageSource source(ServerPlayer owner) {
        return new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(MUSIC_MAGIC), this, owner);
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(BardItems.MUSIC_NOTE.get());
    }
}
