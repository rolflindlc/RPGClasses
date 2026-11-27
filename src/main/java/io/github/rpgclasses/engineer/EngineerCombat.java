package io.github.rpgclasses.engineer;

import io.github.rpgclasses.compat.ntgl.NtglCompat;
import io.github.rpgclasses.content.CombatAbilities;
import io.github.rpgclasses.server.AbilityService;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Daño del Ingeniero. El pipeline de clases llama a {@link #firearmFactor} y {@link #afterFirearmHit} en su rama de armas
 * de fuego (después del muro de Pólvora); aquí además: el Culatazo, el daño recibido (Modo Asedio, Duelista, Racha
 * Frágil), la muerte del retado y el botín de lo que matan las torretas con su dueño desconectado.
 */
public final class EngineerCombat {
    private static final ResourceLocation HAMMER = new ResourceLocation("cgs", "hammer");

    /** Multiplicador del Ingeniero sobre un impacto de arma de fuego suyo (no de sus torretas: van sin ramas). */
    public static double firearmFactor(ServerPlayer attacker, LivingEntity target, DamageSource source) {
        if (!Engineer.is(attacker) || source.getDirectEntity() instanceof TurretEntity) return 1;
        Engineer.State s = Engineer.state(attacker);
        long now = attacker.level().getGameTime();
        double f = 1;
        if (now < s.perfectUntil) f *= 1 + Engineer.a(Engineer.ACTIVE_RELOAD, "perfect_damage", .15);
        if (s.streak > 0) {
            double per = Engineer.has(attacker, Engineer.FRAGILE) ? Engineer.mech(attacker, Engineer.FRAGILE, "per_stack", .07)
                    : Engineer.has(attacker, Engineer.STEADY) ? Engineer.mech(attacker, Engineer.STEADY, "per_stack", .04)
                    : Engineer.mech(attacker, Engineer.STREAK, "per_stack", 0);
            f *= 1 + per * s.streak;
        }
        boolean explosion = source.is(DamageTypeTags.IS_EXPLOSION);
        if (!explosion && attacker.distanceTo(target) > Engineer.mech(attacker, Engineer.MARKSMAN, "far_distance", 15)) {
            f *= 1 + Engineer.mech(attacker, Engineer.MARKSMAN, "far_bonus", 0);
        }
        if (!explosion) {
            boolean head = Hitscan.HEAD.get() || NtglCompat.currentHeadshot(source);
            if (Engineer.has(attacker, Engineer.HEADHUNTER)) {
                f *= head ? Engineer.mech(attacker, Engineer.HEADHUNTER, "headshot", 1.75) / Hitscan.HEADSHOT
                        : 1 + Engineer.mech(attacker, Engineer.HEADHUNTER, "body", -.15);
            } else if (Engineer.has(attacker, Engineer.SUPPRESSION) && head) {
                f *= Engineer.mech(attacker, Engineer.SUPPRESSION, "headshot", 1.25) / Hitscan.HEADSHOT;
            }
        }
        if (Engineer.sub(attacker, Engineer.GUNSLINGER)) {
            if (s.dueled == null || !alive(attacker, s.dueled)) s.dueled = target.getUUID();
            if (target.getUUID().equals(s.dueled)) f *= 1 + Engineer.mech(attacker, Engineer.DUELIST, "damage", 0);
        }
        if (explosion && now < s.siegeUntil) f *= 1 + Engineer.a(Engineer.SIEGE, "area_damage", .25);
        return f;
    }

    private static boolean alive(ServerPlayer p, UUID id) {
        Entity e = p.serverLevel().getEntity(id);
        return e instanceof LivingEntity l && l.isAlive() && l.distanceTo(p) < 96;
    }

    /** Efectos al impactar: bala especial, Supresión y Fuego Supresor. */
    public static void afterFirearmHit(ServerPlayer attacker, LivingEntity target, DamageSource source) {
        if (!Engineer.is(attacker) || source.getDirectEntity() instanceof TurretEntity || source.is(DamageTypeTags.IS_EXPLOSION)) return;
        int special = Hitscan.SPECIAL.get();
        Entity projectile = NtglCompat.currentProjectile(source);
        if (special < 0 && projectile != null && projectile.getPersistentData().contains(ImprovisedAmmo.TAG)) {
            special = projectile.getPersistentData().getInt(ImprovisedAmmo.TAG);
            // Los perdigones de un mismo disparo llevan la misma marca: el efecto, una vez por perdigón (no se acumula).
        }
        ImprovisedAmmo.apply(attacker, target, special);
        boolean head = Hitscan.HEAD.get() || NtglCompat.currentHeadshot(source);
        if (!head && Engineer.has(attacker, Engineer.SUPPRESSION)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    (int) Math.round(Engineer.mech(attacker, Engineer.SUPPRESSION, "slow_seconds", 1) * 20),
                    (int) Engineer.mech(attacker, Engineer.SUPPRESSION, "slow_level", 0)));
        }
        Engineer.State s = Engineer.state(attacker);
        if (attacker.level().getGameTime() < s.suppressUntil) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    (int) Math.round(Engineer.a(Engineer.SUPPRESSING_FIRE, "slow_seconds", 2) * 20),
                    (int) Engineer.a(Engineer.SUPPRESSING_FIRE, "slow_level", 0)));
        }
    }

    /** Muro cuerpo a cuerpo: el martillo neumático de Gunsmithing no tiene muro para el Ingeniero (como la guitarra-hacha). */
    public static double meleeWall(ServerPlayer attacker, ItemStack weapon, double wallFactor) {
        if (Engineer.is(attacker) && HAMMER.equals(ForgeRegistries.ITEMS.getKey(weapon.getItem()))) return 1;
        return wallFactor;
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        // Culatazo: golpe cuerpo a cuerpo con un arma de fuego.
        if (source.getEntity() instanceof ServerPlayer p && source.getDirectEntity() == p && Engineer.is(p)
                && NtglCompat.isFirearm(p.getMainHandItem()) && !NtglCompat.isFirearmDamage(source)
                && AbilityService.has(p, Engineer.RIFLE_BUTT) && AbilityService.remainingTicks(p, Engineer.RIFLE_BUTT) <= 0) {
            var params = Engineer.ability(Engineer.RIFLE_BUTT);
            Vec3 push = target.position().subtract(p.position()).multiply(1, 0, 1);
            if (push.lengthSqr() > 1e-4) {
                target.knockback(AbilityService.number(params, "knockback", 1.6), -push.x, -push.z);
            }
            CombatAbilities.stun(target, (int) AbilityService.number(params, "stun_ticks", 10));
            AbilityService.setCooldown(p, Engineer.RIFLE_BUTT, AbilityService.ticks(params, "cooldown_seconds", 12));
        }
        // Daño que recibe el Ingeniero.
        if (target instanceof ServerPlayer p && Engineer.is(p)) {
            Engineer.State s = Engineer.state(p);
            long now = p.level().getGameTime();
            if (now < s.siegeUntil) event.setAmount(event.getAmount() * (float) (1 - Engineer.a(Engineer.SIEGE, "damage_reduction", .30)));
            Entity attacker = source.getEntity();
            if (attacker != null && attacker.getUUID().equals(s.dueled) && Engineer.sub(p, Engineer.GUNSLINGER)) {
                event.setAmount(event.getAmount() * (float) (1 - Engineer.mech(p, Engineer.DUELIST, "reduction", .10)));
            }
            if (event.getAmount() > 0 && s.streak > 0 && Engineer.has(p, Engineer.FRAGILE)) {
                s.streak = Math.max(0, s.streak - (int) Engineer.mech(p, Engineer.FRAGILE, "hit_loss", 2));
            }
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || !Engineer.sub(p, Engineer.GUNSLINGER)) return;
        Engineer.State s = Engineer.state(p);
        if (event.getEntity().getUUID().equals(s.dueled)) {
            s.dueled = null;
            s.duelKillUntil = p.level().getGameTime() + Math.round(Engineer.mech(p, Engineer.DUELIST, "kill_speed_seconds", 3) * 20);
        }
    }

    /** Torreta con el dueño desconectado: lo que mata no suelta objetos ni experiencia (sin granjas AFK). */
    private static boolean offlineTurretKill(LivingEntity dead, DamageSource source) {
        DamageSource s = source != null ? source : dead.getLastDamageSource();
        return s != null && s.getDirectEntity() instanceof TurretEntity turret && turret.ownerOffline();
    }

    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        if (offlineTurretKill(event.getEntity(), event.getSource())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onExperience(LivingExperienceDropEvent event) {
        if (offlineTurretKill(event.getEntity(), null)) event.setCanceled(true);
    }
}
