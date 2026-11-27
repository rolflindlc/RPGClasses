package io.github.rpgclasses.engineer;

import io.github.rpgclasses.compat.ntgl.NtglCompat;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Torreta del Artificiero (adenda del Ingeniero, 4.3): lleva el arma de fuego que el jugador tenía en la mano y dispara
 * sola (rayos de {@link Hitscan}) con el 70 % de las estadísticas del arma, sin las ramas del dueño. Munición: del
 * inventario del dueño si está cerca y vivo; si ha muerto, su reserva (el cargador del arma); si está desconectado,
 * gratis, pero lo que mata no suelta botín ({@link EngineerCombat}). Se rompe de un golpe y el arma vuelve al dueño.
 */
public class TurretEntity extends LivingEntity implements OwnableEntity {
    private static final EntityDataAccessor<ItemStack> GUN = SynchedEntityData.defineId(TurretEntity.class, EntityDataSerializers.ITEM_STACK);
    private UUID owner;
    private long nextShot;
    private boolean broken;

    public TurretEntity(EntityType<? extends TurretEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 1).add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(GUN, ItemStack.EMPTY);
    }

    void setup(ServerPlayer player, ItemStack gun) {
        owner = player.getUUID();
        entityData.set(GUN, gun);
    }

    public ItemStack gun() {
        return entityData.get(GUN);
    }

    @Override
    public UUID getOwnerUUID() {
        return owner;
    }

    /** Dueño desconectado (o en otra dimensión). */
    boolean ownerOffline() {
        return owner != null && level().getServer() != null && level().getServer().getPlayerList().getPlayer(owner) == null;
    }

    private ServerPlayer ownerPlayer() {
        return owner == null || level().getServer() == null ? null : level().getServer().getPlayerList().getPlayer(owner);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || broken) return;
        ItemStack gun = gun();
        if (!NtglCompat.isFirearm(gun)) {
            breakTurret();
            return;
        }
        long now = level().getGameTime();
        if (now < nextShot) return;
        ServerLevel level = (ServerLevel) level();
        double range = Engineer.a(Engineer.TURRET, "range", 24);
        ResourceLocation ammoId = NtglCompat.currentAmmoId(gun, this);
        NtglCompat.Ballistics ballistics = NtglCompat.ballistics(gun, this, ammoId, range);
        double reach = ballistics.range();
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(reach),
                e -> e.isAlive() && EngineerAbilities.hostile(level, owner, e) && hasLineOfSight(e) && distanceTo(e) <= reach);
        if (targets.isEmpty()) return;
        LivingEntity target = targets.stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        Vec3 from = getEyePosition();
        Vec3 dir = new Vec3(target.getX(), target.getY(0.6), target.getZ()).subtract(from);
        setYRot((float) (Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90));
        setYHeadRot(getYRot());
        yBodyRot = getYRot();
        // Munición: si no hay balas en el cargador, se rellena del inventario del dueño (cerca y vivo).
        ServerPlayer player = ownerPlayer();
        boolean free = ownerOffline();
        int ammo = Math.max(0, NtglCompat.ammo(gun, this));
        if (ammo <= 0 && !free) {
            if (player != null && player.isAlive() && player.distanceTo(this) <= Engineer.a(Engineer.TURRET, "ammo_range", 32)) {
                int max = NtglCompat.maxAmmo(gun, this);
                ammo = ActiveReload.take(player, ammoId, max);
                if (ammo > 0) {
                    NtglCompat.setAmmo(gun, this, ammo);
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.6f, 1.4f);
                }
            }
            if (ammo <= 0) {
                nextShot = now + 20;
                return;
            }
        }
        float damage = NtglCompat.shotDamage(gun, this, ammoId) * (float) Engineer.a(Engineer.TURRET, "stats_factor", .7);
        double spread = NtglCompat.spread(gun, this);
        Hitscan.shoot(level, this, player, from, Hitscan.spread(dir, spread, getRandom()), reach + 1, damage,
                ballistics.fades(), false, -1, e -> e == target || EngineerAbilities.hostile(level, owner, (LivingEntity) e));
        if (!free) NtglCompat.setAmmo(gun, this, ammo - 1);
        entityData.set(GUN, gun.copy());
        nextShot = now + NtglCompat.rate(gun, this);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || broken) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            breakTurret();
            return true;
        }
        // Solo la rompe un golpe de alguien (o una explosión): ni caídas ni el entorno. Los aliados del dueño no.
        if (source.getEntity() == null && !source.is(DamageTypeTags.IS_EXPLOSION)) return false;
        if (source.getEntity() != null && io.github.rpgclasses.server.Allies.isAlly(this, source.getEntity())) return false;
        breakTurret();
        return true;
    }

    /** Se rompe: el arma vuelve al dueño (o se guarda hasta que vuelva) y su carga empieza a recargarse. */
    void breakTurret() {
        if (broken) return;
        broken = true;
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1f, 0.8f);
        Artificer.returnGun(this, gun(), true);
        discard();
    }

    /** Recogida con la mano vacía: el arma vuelve y la carga se recupera al instante. */
    void recall() {
        if (broken) return;
        broken = true;
        Artificer.returnGun(this, gun(), false);
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.put("Gun", gun().save(new CompoundTag()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) owner = tag.getUUID("Owner");
        entityData.set(GUN, ItemStack.of(tag.getCompound("Gun")));
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !broken;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return true;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return List.of();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? gun() : ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    /** Las torretas no cuentan como víctima de un jugador (no dan experiencia). */
    @Override
    public boolean isAlliedTo(net.minecraft.world.entity.Entity other) {
        return other instanceof Player p && p.getUUID().equals(owner) || super.isAlliedTo(other);
    }
}
