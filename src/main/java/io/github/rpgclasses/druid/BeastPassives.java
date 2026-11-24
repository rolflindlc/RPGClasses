package io.github.rpgclasses.druid;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Rasgos pasivos de forma añadidos con las bestias de otros mods:
 * <ul>
 *   <li><b>glide</b> (planear): en el aire caes despacio y conservas el impulso; agáchate para dejar de planear. Sin daño
 *       de caída.</li>
 *   <li><b>dodge</b> (esquiva): probabilidad de esquivar los proyectiles ({@code dodge_chance}, 25 %).</li>
 *   <li><b>lava_swim</b> (nadar en lava): inmune al fuego y te mueves por la lava como por el agua (el movimiento lo hace
 *       el cliente, en DruidClient).</li>
 *   <li><b>thorns</b> (púas): quien te golpea cuerpo a cuerpo recibe daño ({@code thorns_damage}, 2).</li>
 *   <li><b>snow_walk</b> (pies de nieve): andas sobre la nieve en polvo (mixin) y no te congelas.</li>
 *   <li><b>rooted</b> (arraigado): la forma no se mueve ni salta (plantas y bestias de emboscada).</li>
 * </ul>
 */
public final class BeastPassives {
    static boolean has(Player p, String passive) {
        Beasts.Info info = WildShape.info(p);
        return info != null && info.hasPassive(passive);
    }

    /** Cada tic de la forma (servidor). */
    static void tick(ServerPlayer p, Beasts.Info info) {
        if (info.hasPassive("glide") && !p.onGround() && !p.isInWater() && !p.isShiftKeyDown() && p.getDeltaMovement().y < 0) {
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0, true, false));
        }
        if (info.hasPassive("snow_walk") && p.getTicksFrozen() > 0) p.setTicksFrozen(0);
    }

    /** Arraigado: sin salto (el salto lo calcula el cliente, así que se corta en los dos lados). */
    @SubscribeEvent
    public void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof Player p && has(p, "rooted")) {
            Vec3 m = p.getDeltaMovement();
            p.setDeltaMovement(m.x, Math.min(0, m.y), m.z);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !has(p, "dodge")) return;
        DamageSource src = event.getSource();
        if (src.is(DamageTypeTags.IS_PROJECTILE)
                && p.getRandom().nextDouble() < Druid.number(Druid.WILD_SHAPE, "dodge_chance", .25)) {
            event.setCanceled(true);
            p.level().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_NODAMAGE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1.4f);
        }
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !has(p, "thorns")) return;
        DamageSource src = event.getSource();
        if (src.getEntity() instanceof LivingEntity attacker && src.getDirectEntity() == attacker && attacker != p
                && !src.is(DamageTypeTags.IS_PROJECTILE)) {
            attacker.hurt(p.damageSources().thorns(p), (float) Druid.number(Druid.WILD_SHAPE, "thorns_damage", 2));
        }
    }
}
