package io.github.rpgclasses.druid;

import com.google.gson.JsonObject;
import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.content.race.Races;
import io.github.rpgclasses.network.DruidPackets;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.Allies;
import io.github.rpgclasses.server.SessionState;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Activas del Druida (adenda, 5): Rastrear Presas (nivel 5), Dominar Animal (nivel 15) y Enredaderas (nivel 30).
 * Las dos primeras se pueden usar transformado; las Enredaderas, no.
 */
public final class DruidActives {
    public static final ResourceKey<DamageType> VINES_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("druid_vines"));

    private static final class Vines {
        final ServerLevel level;
        final Vec3 center;
        final UUID owner;
        final long until;
        final double radius;
        final Map<Integer, Vec3> lastPos = new HashMap<>();

        Vines(ServerLevel level, Vec3 center, UUID owner, long until, double radius) {
            this.level = level;
            this.center = center;
            this.owner = owner;
            this.until = until;
            this.radius = radius;
        }
    }

    private static final List<Vines> VINES = SessionState.track(new ArrayList<>());

    // ------------------------------------------------------------------ Rastrear Presas

    static boolean trackPrey(ServerPlayer p, JsonObject params) {
        PlayerClassData data = Druid.data(p);
        String fixed = data == null ? "" : Bestiary.tracked(data);
        boolean useFixed = !fixed.isEmpty() && Beasts.type(fixed) != null;
        double r = useFixed ? AbilityService.number(params, "fixed_radius", 96) : AbilityService.number(params, "radius", 48);
        AABB box = p.getBoundingBox().inflate(r);
        List<LivingEntity> found = p.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive()
                && e.distanceToSqr(p) <= r * r && (useFixed ? Beasts.id(e.getType()).equals(fixed) : Beasts.eligible(e)));
        if (found.isEmpty()) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.track_none"), true);
            return true;
        }
        int[] ids = found.stream().mapToInt(LivingEntity::getId).limit(256).toArray();
        Net.toPlayer(p, new DruidPackets.Glow(ids, AbilityService.ticks(params, "duration_seconds", 10)));
        p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.track_found", ids.length), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.FOX_SNIFF, SoundSource.PLAYERS, 1f, 1f);
        return true;
    }

    /** Bestiario: fija (o quita) la especie que rastrea. */
    static void setTracked(ServerPlayer p, String species) {
        PlayerClassData data = Druid.data(p);
        if (data == null || !Druid.isDruid(p)) return;
        boolean clear = species == null || species.isEmpty() || species.equals(Bestiary.tracked(data));
        Bestiary.setTracked(data, clear ? "" : species);
        p.displayClientMessage(clear ? Component.translatable("message.rites_and_paths.druid.track_cleared")
                : Component.translatable("message.rites_and_paths.druid.track_fixed", Beasts.name(species)), true);
        Net.syncProgress(p);
    }

    // ------------------------------------------------------------------ Dominar Animal

    static boolean dominate(ServerPlayer p, JsonObject params) {
        double range = AbilityService.number(params, "range", 12);
        LivingEntity target = lookCreature(p, range);
        if (!(target instanceof Mob mob) || Races.isBoss(target) || !Beasts.eligible(target)) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.dominate_no_target"), true);
            return false;
        }
        if (!Beasts.known(p, Beasts.id(target.getType()))) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.dominate_unknown"), true);
            return false;
        }
        int ticks = AbilityService.ticks(params, "duration_seconds", 180);
        Dominate.apply(mob, p, ticks);
        p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.dominate_done", target.getDisplayName(),
                ticks / 20), true);
        ((ServerLevel) p.level()).sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + target.getBbHeight() + .3, target.getZ(),
                6, .4, .2, .4, 0);
        p.level().playSound(null, target.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, .8f, 1.4f);
        return true;
    }

    /** Animal al que mira (cualquier criatura viva). */
    private static LivingEntity lookCreature(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        LivingEntity best = null;
        double bestDot = Math.cos(Math.toRadians(8));
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range))) {
            if (e == p || !e.isAlive() || e instanceof Player || !p.hasLineOfSight(e)) continue;
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            if (to.length() > range) continue;
            double dot = to.normalize().dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = e;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ Enredaderas

    static boolean vines(ServerPlayer p, JsonObject params) {
        if (WildShape.inForm(p)) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.vines_in_form"), true);
            return false;
        }
        ServerLevel level = p.serverLevel();
        VINES.add(new Vines(level, p.position(), p.getUUID(), level.getGameTime() + AbilityService.ticks(params, "duration_seconds", 20),
                AbilityService.number(params, "radius", 5)));
        level.playSound(null, p.blockPosition(), SoundEvents.CAVE_VINES_PLACE, SoundSource.PLAYERS, 1.5f, .7f);
        level.playSound(null, p.blockPosition(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.5f, .6f);
        return true;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || VINES.isEmpty()) return;
        MinecraftServer server = event.getServer();
        JsonObject params = Druid.params(Druid.ENTANGLING_VINES, false);
        double heal = AbilityService.number(params, "heal_per_second", .5);
        double damage = AbilityService.number(params, "damage", 1.5);
        int slow = Math.max(0, (int) AbilityService.number(params, "slow_level", 1) - 1);
        int interval = Math.max(1, AbilityService.ticks(params, "damage_interval_seconds", .5));
        Iterator<Vines> it = VINES.iterator();
        while (it.hasNext()) {
            Vines v = it.next();
            long now = v.level.getGameTime();
            if (now > v.until) {
                it.remove();
                continue;
            }
            if (now % interval != 0) continue;
            ServerPlayer owner = server.getPlayerList().getPlayer(v.owner);
            // Partículas: enredaderas por el suelo del círculo.
            for (int i = 0; i < 10; i++) {
                double ang = v.level.random.nextDouble() * Math.PI * 2, d = Math.sqrt(v.level.random.nextDouble()) * v.radius;
                v.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.VINE.defaultBlockState()),
                        v.center.x + Math.cos(ang) * d, v.center.y + .1, v.center.z + Math.sin(ang) * d, 2, .1, .05, .1, 0);
            }
            v.level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, v.center.x, v.center.y + .3, v.center.z, 4, v.radius / 2, .1, v.radius / 2, 0);
            AABB box = new AABB(v.center, v.center).inflate(v.radius, 1.5, v.radius);
            for (LivingEntity e : v.level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive()
                    && e.position().subtract(v.center).horizontalDistanceSqr() <= v.radius * v.radius)) {
                if (owner != null && Allies.isAlly(owner, e)) {
                    if (e.getHealth() < e.getMaxHealth()) e.heal((float) (heal * interval / 20.0));
                    continue;
                }
                if (owner == null || !Allies.isEnemy(owner, e)) continue;
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, interval + 10, slow, false, false, true));
                Vec3 last = v.lastPos.put(e.getId(), e.position());
                if (last != null && last.distanceToSqr(e.position()) > 1.0E-4) {
                    DamageSource source = new DamageSource(v.level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                            .getHolderOrThrow(VINES_DAMAGE), owner);
                    e.hurt(source, (float) damage);
                }
            }
        }
    }
}
