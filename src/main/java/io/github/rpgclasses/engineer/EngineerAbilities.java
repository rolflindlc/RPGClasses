package io.github.rpgclasses.engineer;

import com.google.gson.JsonObject;
import io.github.rpgclasses.compat.ntgl.NtglCompat;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.Allies;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Activas del Ingeniero: Fuego Supresor y Último Cargador (tronco), Abanico y Ojo Muerto (Pistolero), Bombardeo y Modo
 * Asedio (Artillero). Las del Artificiero tienen teclas propias ({@link Artificer}). El Culatazo es automático
 * ({@link EngineerCombat}).
 */
final class EngineerAbilities {
    private static final ResourceLocation REVOLVER = new ResourceLocation("cgs", "revolver");
    private static final ResourceLocation SMALL_ROCKET = new ResourceLocation("cgs", "rocket_small");

    private EngineerAbilities() {
    }

    static void register() {
        AbilityService.register(Engineer.SUPPRESSING_FIRE, (p, params) -> {
            if (!Engineer.is(p)) return false;
            Engineer.state(p).suppressUntil = p.level().getGameTime() + AbilityService.ticks(params, "duration_seconds", 6);
            sound(p, SoundEvents.BLAZE_SHOOT, 1.2f);
            return true;
        });
        AbilityService.register(Engineer.LAST_MAGAZINE, (p, params) -> {
            if (!Engineer.is(p)) return false;
            Engineer.state(p).lastMagUntil = p.level().getGameTime() + AbilityService.ticks(params, "duration_seconds", 8);
            sound(p, SoundEvents.ARMOR_EQUIP_IRON, 0.8f);
            return true;
        });
        AbilityService.register(Engineer.FAN, EngineerAbilities::fan);
        AbilityService.register(Engineer.DEAD_EYE, EngineerAbilities::deadEye);
        AbilityService.register(Engineer.BOMBARDMENT, EngineerAbilities::bombard);
        AbilityService.register(Engineer.SIEGE, (p, params) -> {
            if (!Engineer.is(p)) return false;
            Engineer.state(p).siegeUntil = p.level().getGameTime() + AbilityService.ticks(params, "duration_seconds", 10);
            sound(p, SoundEvents.ANVIL_PLACE, 0.7f);
            return true;
        });
    }

    private static void sound(ServerPlayer p, net.minecraft.sounds.SoundEvent sound, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, 1f, pitch);
    }

    private static void fail(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable("message.rites_and_paths.engineer." + key), true);
    }

    // ------------------------------------------------------------------ Pistolero

    private static boolean fan(ServerPlayer p, JsonObject params) {
        ItemStack gun = p.getMainHandItem();
        if (!REVOLVER.equals(ForgeRegistries.ITEMS.getKey(gun.getItem()))) {
            fail(p, "no_revolver");
            return false;
        }
        int ammo = NtglCompat.ammo(gun, p);
        if (ammo <= 0) {
            fail(p, "empty");
            return false;
        }
        Engineer.State s = Engineer.state(p);
        s.fanLeft = ammo;
        s.fanNext = p.level().getGameTime();
        return true;
    }

    private static boolean deadEye(ServerPlayer p, JsonObject params) {
        if (!Engineer.light(p.getMainHandItem())) {
            fail(p, "no_light_firearm");
            return false;
        }
        Engineer.State s = Engineer.state(p);
        s.marks.clear();
        s.deadEyeUntil = p.level().getGameTime() + AbilityService.ticks(params, "duration_seconds", 3);
        sound(p, SoundEvents.BELL_BLOCK, 1.6f);
        return true;
    }

    /** Daño de una bala del arma de la mano con su munición actual. */
    private static float bulletDamage(ServerPlayer p, ItemStack gun) {
        return NtglCompat.shotDamage(gun, p, NtglCompat.currentAmmoId(gun, p));
    }

    // ------------------------------------------------------------------ Artillero

    private static boolean bombard(ServerPlayer p, JsonObject params) {
        double range = AbilityService.number(params, "range", 30);
        Vec3 eye = p.getEyePosition();
        BlockHitResult hit = p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 center = hit.getType() == HitResult.Type.MISS ? eye.add(p.getLookAngle().scale(range)) : hit.getLocation();
        if (!pay(p, (int) AbilityService.number(params, "powder_cost", 3))) {
            fail(p, "no_powder");
            return false;
        }
        Engineer.State s = Engineer.state(p);
        long impact = p.level().getGameTime() + AbilityService.ticks(params, "delay_seconds", 1.5);
        int shells = (int) AbilityService.number(params, "shells", 6);
        double radius = AbilityService.number(params, "radius", 4);
        for (int i = 0; i < shells; i++) {
            double ang = p.getRandom().nextDouble() * Math.PI * 2, dist = Math.sqrt(p.getRandom().nextDouble()) * radius;
            double x = center.x + Math.cos(ang) * dist, z = center.z + Math.sin(ang) * dist;
            BlockPos ground = p.level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    BlockPos.containing(x, center.y, z));
            double y = Math.min(center.y + 1, ground.getY());
            s.shells.add(new double[] {x, y, z, impact + i * 2});
        }
        sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.6f);
        return true;
    }

    /** Un cohete pequeño o, si no hay, la pólvora indicada (en creativo, gratis). */
    private static boolean pay(ServerPlayer p, int powder) {
        if (p.getAbilities().instabuild) return true;
        Item rocket = ForgeRegistries.ITEMS.getValue(SMALL_ROCKET);
        var inv = p.getInventory();
        if (rocket != null && rocket != Items.AIR) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (inv.getItem(i).is(rocket)) {
                    inv.getItem(i).shrink(1);
                    return true;
                }
            }
        }
        if (inv.countItem(Items.GUNPOWDER) < powder) return false;
        int left = powder;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(Items.GUNPOWDER)) {
                int n = Math.min(left, st.getCount());
                st.shrink(n);
                left -= n;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ tic

    static void tick(ServerPlayer p, Engineer.State s, long now) {
        ServerLevel level = p.serverLevel();
        // Abanico: el tambor se vacía en medio segundo.
        if (s.fanLeft > 0 && now >= s.fanNext) {
            ItemStack gun = p.getMainHandItem();
            int ammo = NtglCompat.ammo(gun, p);
            if (!REVOLVER.equals(ForgeRegistries.ITEMS.getKey(gun.getItem())) || ammo <= 0) {
                s.fanLeft = 0;
            } else {
                JsonObject params = Engineer.ability(Engineer.FAN);
                float damage = bulletDamage(p, gun) * (float) AbilityService.number(params, "damage_factor", .7);
                double spread = NtglCompat.spread(gun, p) * AbilityService.number(params, "spread_factor", 3) + 2;
                Hitscan.shoot(level, p, p, p.getEyePosition(), Hitscan.spread(p.getLookAngle(), spread, p.getRandom()), 64,
                        damage, false, false, -1, e -> e != p && !Allies.isAlly(p, e));
                NtglCompat.setAmmo(gun, p, ammo - 1);
                s.fanLeft--;
                int interval = Math.max(1, (int) Math.round(AbilityService.ticks(params, "duration_seconds", .5) / 6.0));
                s.fanNext = now + interval;
                if (s.fanLeft == 0 || ammo - 1 <= 0) {
                    s.fanLeft = 0;
                    NtglCompat.startReload(p, InteractionHand.MAIN_HAND);
                }
            }
        }
        // Ojo Muerto: marcar al pasar la mira; al terminar, una bala a la cabeza de cada marcado.
        if (s.deadEyeUntil > 0) {
            JsonObject params = Engineer.ability(Engineer.DEAD_EYE);
            if (now < s.deadEyeUntil) {
                int max = (int) AbilityService.number(params, "max_targets", 6);
                if (s.marks.size() < max) {
                    LivingEntity looked = looked(p, AbilityService.number(params, "range", 64));
                    if (looked != null && s.marks.add(looked.getId())) {
                        EngineerPackets.marks(p, s.marks, (int) (s.deadEyeUntil - now) + 10);
                        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.8f, 1.8f);
                    }
                }
            } else {
                ItemStack gun = p.getMainHandItem();
                float damage = bulletDamage(p, gun);
                for (int id : s.marks) {
                    int ammo = NtglCompat.ammo(gun, p);
                    if (ammo <= 0 && !p.getAbilities().instabuild) break;
                    Entity e = level.getEntity(id);
                    if (!(e instanceof LivingEntity target) || !target.isAlive() || !p.hasLineOfSight(target)) continue;
                    Vec3 from = p.getEyePosition();
                    Vec3 dir = new Vec3(target.getX(), target.getEyeY(), target.getZ()).subtract(from);
                    Hitscan.shoot(level, p, p, from, dir, dir.length() + 2, damage, false, true, -1, x -> x == target);
                    if (ammo > 0) NtglCompat.setAmmo(gun, p, ammo - 1);
                }
                s.marks.clear();
                s.deadEyeUntil = 0;
                EngineerPackets.marks(p, s.marks, 0);
            }
        }
        // Bombardeo: estela de los obuses y explosión al llegar.
        if (!s.shells.isEmpty()) {
            JsonObject params = Engineer.ability(Engineer.BOMBARDMENT);
            Iterator<double[]> it = s.shells.iterator();
            List<double[]> landed = new ArrayList<>();
            while (it.hasNext()) {
                double[] sh = it.next();
                long left = (long) sh[3] - now;
                if (left <= 0) {
                    landed.add(sh);
                    it.remove();
                } else {
                    level.sendParticles(ParticleTypes.FLAME, sh[0], sh[1] + left * 0.6, sh[2], 2, 0.05, 0.1, 0.05, 0.01);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, sh[0], sh[1] + left * 0.6 + 0.5, sh[2], 1, 0.05, 0.1, 0.05, 0.01);
                }
            }
            for (double[] sh : landed) {
                blast(level, p.getUUID(), new Vec3(sh[0], sh[1], sh[2]), AbilityService.number(params, "blast_radius", 2),
                        (float) AbilityService.number(params, "damage", 8), null);
            }
        }
    }

    /** Explosión sin romper bloques que solo hiere a los enemigos del dueño (obuses y minas). */
    static void blast(ServerLevel level, java.util.UUID ownerId, Vec3 at, double radius, float damage, Entity direct) {
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 2, 0.3, 0.2, 0.3, 0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2f, 1.1f);
        ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
        var box = new net.minecraft.world.phys.AABB(at, at).inflate(radius);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator())) {
            if (!hostile(level, ownerId, e)) continue;
            double d = e.position().distanceTo(at);
            if (d > radius + e.getBbWidth() / 2) continue;
            e.invulnerableTime = 0;
            Entity cause = direct != null ? direct : owner;
            e.hurt(Hitscan.source(level, Hitscan.BLAST, cause, owner), damage * (float) Math.max(.4, 1 - d / (radius + 1)));
        }
    }

    /**
     * Objetivo de torretas, minas y obuses: enemigo del dueño (sistema de aliados) y además monstruo, jugador o criatura
     * que ataca al dueño o a un aliado (no los animales tranquilos).
     */
    static boolean hostile(ServerLevel level, java.util.UUID ownerId, LivingEntity e) {
        if (e instanceof TurretEntity || ownerId == null || !Allies.isEnemyOf(level.getServer(), ownerId, e)) return false;
        if (e instanceof net.minecraft.world.entity.monster.Enemy || e instanceof net.minecraft.world.entity.player.Player) return true;
        return e instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null
                && Allies.isAllyOf(level.getServer(), ownerId, mob.getTarget());
    }

    /** Enemigo bajo la mira (para Ojo Muerto). */
    private static LivingEntity looked(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        BlockHitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p.level(), p, eye, end,
                new net.minecraft.world.phys.AABB(eye, end).inflate(1),
                e -> e instanceof LivingEntity l && l.isAlive() && e != p && Allies.isEnemy(p, l));
        return hit != null && hit.getEntity() instanceof LivingEntity l ? l : null;
    }
}
