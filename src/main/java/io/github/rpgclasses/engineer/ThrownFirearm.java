package io.github.rpgclasses.engineer;

import io.github.rpgclasses.content.CombatAbilities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Arma de fuego lanzada (perk Arma Arrojadiza): golpe de culata y aturdimiento al primer enemigo. Es solo el proyectil
 * que se ve: el arma de verdad vuelve sola al inventario del dueño ({@link Holster}).
 */
public class ThrownFirearm extends ThrowableItemProjectile {
    private float damage = 4;
    private int stunTicks = 10;

    public ThrownFirearm(EntityType<? extends ThrownFirearm> type, Level level) {
        super(type, level);
    }

    ThrownFirearm(LivingEntity owner, ItemStack gun, float damage, int stunTicks) {
        super(EngineerRegistry.THROWN_FIREARM.get(), owner, owner.level());
        setItem(gun.copyWithCount(1));
        this.damage = damage;
        this.stunTicks = stunTicks;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.IRON_INGOT;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide || !(result.getEntity() instanceof LivingEntity target) || target == getOwner()) return;
        target.hurt(damageSources().thrown(this, getOwner()), damage);
        CombatAbilities.stun(target, stunTicks);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > 80) discard();
    }
}
