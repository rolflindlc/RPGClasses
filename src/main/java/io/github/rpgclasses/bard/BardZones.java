package io.github.rpgclasses.bard;

import io.github.rpgclasses.content.CombatAbilities;
import io.github.rpgclasses.content.race.Races;
import io.github.rpgclasses.server.Allies;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Efectos de la escuela musical que duran en el tiempo y no son un efecto de poción: el Coro Fantasma (tres coristas
 * que distraen y dañan), la Barrera de Armonía (cúpula que absorbe proyectiles y reduce el daño a los aliados), el
 * Canto de Sirena (atrae a los enemigos), el sueño de la Nana (se rompe al recibir daño) y la protección de la
 * Canción de Cuna Eterna (el primer golpe mortal no mata). Sin dependencias de Iron's.
 */
public final class BardZones {
    private enum Type { CHOIR, BARRIER, SIREN }

    private static final class Zone {
        Type type;
        UUID owner;
        ResourceKey<Level> dim;
        Vec3 center = Vec3.ZERO;
        Vec3[] singers = new Vec3[0];
        long until;
        double radius, value;
    }

    private static final List<Zone> ZONES = new ArrayList<>();
    /** Criaturas dormidas: hasta qué tick del servidor. */
    private static final Map<UUID, Long> SLEEP = new HashMap<>();
    /** Protección de la Canción de Cuna Eterna: {tick de fin, fracción de vida}. */
    private static final Map<UUID, double[]> WARDS = new HashMap<>();

    // ------------------------------------------------------------------ crear

    /** Coro Fantasma: tres coristas en círculo alrededor del Bardo. */
    public static void choir(ServerPlayer owner, int ticks, double radius, double damagePerSecond) {
        Zone z = zone(Type.CHOIR, owner, ticks, radius, damagePerSecond);
        z.center = owner.position();
        z.singers = new Vec3[3];
        double start = owner.getRandom().nextDouble() * Math.PI * 2;
        for (int i = 0; i < 3; i++) {
            double a = start + Math.PI * 2 * i / 3;
            z.singers[i] = z.center.add(Math.cos(a) * 3, 0, Math.sin(a) * 3);
        }
    }

    /** Barrera de Armonía: cúpula fija donde se lanza. */
    public static void barrier(ServerPlayer owner, int ticks, double radius, double reduction) {
        zone(Type.BARRIER, owner, ticks, radius, reduction).center = owner.position();
    }

    /** Canto de Sirena: los enemigos del radio son atraídos hacia el Bardo mientras dura. */
    public static void siren(ServerPlayer owner, int ticks, double radius, double strength) {
        zone(Type.SIREN, owner, ticks, radius, strength);
    }

    private static Zone zone(Type type, ServerPlayer owner, int ticks, double radius, double value) {
        Zone z = new Zone();
        z.type = type;
        z.owner = owner.getUUID();
        z.dim = owner.level().dimension();
        z.until = owner.getServer().getTickCount() + ticks;
        z.radius = radius;
        z.value = value;
        ZONES.add(z);
        return z;
    }

    /** Nana: la criatura se duerme (aturdida) hasta que reciba daño o pase el tiempo. */
    public static void sleep(LivingEntity target, int ticks) {
        if (ticks <= 0 || target.getServer() == null) return;
        if (target instanceof Mob mob) mob.setTarget(null);
        CombatAbilities.stun(target, ticks);
        SLEEP.put(target.getUUID(), (long) target.getServer().getTickCount() + ticks);
    }

    /** Canción de Cuna Eterna: el primer golpe mortal de este aliado no lo mata. */
    public static void ward(LivingEntity ally, int ticks, double healthFraction) {
        if (ticks <= 0 || ally.getServer() == null) return;
        WARDS.put(ally.getUUID(), new double[] {ally.getServer().getTickCount() + ticks, healthFraction});
    }

    public static boolean warded(LivingEntity e) {
        double[] w = WARDS.get(e.getUUID());
        return w != null && e.getServer() != null && w[0] >= e.getServer().getTickCount();
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (now % 2 != 0) return;
        if (!SLEEP.isEmpty()) SLEEP.values().removeIf(until -> until < now);
        if (!WARDS.isEmpty()) WARDS.values().removeIf(w -> w[0] < now);
        for (Iterator<Zone> it = ZONES.iterator(); it.hasNext(); ) {
            Zone z = it.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(z.owner);
            ServerLevel level = server.getLevel(z.dim);
            if (owner == null || level == null || !owner.isAlive() || now >= z.until) {
                it.remove();
                continue;
            }
            switch (z.type) {
                case CHOIR -> choirTick(level, owner, z, now);
                case BARRIER -> barrierTick(level, owner, z, now);
                case SIREN -> sirenTick(level, owner, z);
            }
        }
    }

    private static void choirTick(ServerLevel level, ServerPlayer owner, Zone z, long now) {
        if (now % 10 != 0) return;
        for (Vec3 c : z.singers) {
            level.sendParticles(ParticleTypes.SOUL, c.x, c.y + 0.9, c.z, 2, 0.15, 0.35, 0.15, 0.01);
            level.sendParticles(ParticleTypes.NOTE, c.x, c.y + 2.0, c.z, 1, 0.25, 0.1, 0.25, 1);
        }
        boolean hit = now % 20 == 0;
        AABB box = new AABB(z.center, z.center).inflate(z.radius + 4);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!Allies.isEnemy(owner, e)) continue;
            Vec3 near = z.singers[0];
            for (Vec3 c : z.singers) if (c.distanceToSqr(e.position()) < near.distanceToSqr(e.position())) near = c;
            double d = near.distanceTo(e.position());
            if (d > z.radius) continue;
            // Distraen: los mobs dejan de ir a por el Bardo y sus aliados y se acercan a cantar con ellos.
            if (e instanceof Mob mob && !Races.isBoss(e)) {
                LivingEntity t = mob.getTarget();
                if (t != null && Allies.isAlly(owner, t)) mob.setTarget(null);
                mob.getNavigation().moveTo(near.x, near.y, near.z, 1.0);
            }
            if (hit && d <= 3.5) e.hurt(music(owner), (float) z.value);
        }
    }

    private static void barrierTick(ServerLevel level, ServerPlayer owner, Zone z, long now) {
        Vec3 c = z.center;
        double r = z.radius;
        if (now % 10 == 0) {
            for (int i = 0; i < 24; i++) {
                double a = Math.PI * 2 * i / 24;
                level.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(a) * r, c.y + 0.1 + (i % 4) * 0.7, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            level.sendParticles(ParticleTypes.NOTE, c.x, c.y + 2.5, c.z, 1, r * 0.4, 0.2, r * 0.4, 1);
        }
        for (Projectile p : level.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(r, r, r))) {
            if (p.position().distanceToSqr(c) > r * r) continue;
            Entity shooter = p.getOwner();
            if (shooter != null && (shooter == owner || Allies.isAlly(owner, shooter))) continue;
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, p.getX(), p.getY(), p.getZ(), 4, 0.1, 0.1, 0.1, 0.1);
            p.discard();
        }
    }

    private static void sirenTick(ServerLevel level, ServerPlayer owner, Zone z) {
        Vec3 c = owner.position();
        double r = z.radius;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(r))) {
            if (e.distanceToSqr(owner) > r * r || !Allies.isEnemy(owner, e)) continue;
            double dx = c.x - e.getX(), dz = c.z - e.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 2.0) {
                e.setDeltaMovement(e.getDeltaMovement().multiply(0.3, 1, 0.3));
                continue;
            }
            double s = z.value * (Races.isBoss(e) ? 0.4 : 1.0);
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x * 0.4 + dx / len * s, v.y, v.z * 0.4 + dz / len * s);
            e.hurtMarked = true;
            if (e instanceof Mob mob) mob.getNavigation().stop();
            if (e.tickCount % 6 == 0) level.sendParticles(ParticleTypes.NOTE, e.getX(), e.getY() + e.getBbHeight() + 0.2, e.getZ(), 1, 0.2, 0.1, 0.2, 1);
        }
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || victim.getServer() == null) return;
        // Un dormido se despierta al recibir daño.
        Long until = SLEEP.remove(victim.getUUID());
        if (until != null && until >= victim.getServer().getTickCount()) {
            victim.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            victim.removeEffect(MobEffects.WEAKNESS);
        }
        if (ZONES.isEmpty()) return;
        double best = 0;
        for (Zone z : ZONES) {
            if (z.type != Type.BARRIER || z.dim != victim.level().dimension()) continue;
            if (victim.position().distanceToSqr(z.center) > z.radius * z.radius) continue;
            if (Allies.isAllyOf(victim.getServer(), z.owner, victim)) best = Math.max(best, z.value);
        }
        if (best > 0) event.setAmount((float) (event.getAmount() * (1 - Math.min(0.9, best))));
    }

    /** Canción de Cuna Eterna: el golpe mortal deja al aliado con parte de su vida. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || e.getServer() == null || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        double[] w = WARDS.remove(e.getUUID());
        if (w == null || w[0] < e.getServer().getTickCount()) return;
        event.setCanceled(true);
        e.setHealth(Math.max(1f, (float) (e.getMaxHealth() * w[1])));
        for (MobEffectInstance inst : new ArrayList<>(e.getActiveEffects())) {
            if (inst.getEffect().getCategory() == MobEffectCategory.HARMFUL) e.removeEffect(inst.getEffect());
        }
        e.invulnerableTime = 30;
        if (e.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, e.getX(), e.getY() + 1, e.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
            level.sendParticles(ParticleTypes.NOTE, e.getX(), e.getY() + 2, e.getZ(), 8, 0.4, 0.3, 0.4, 1);
        }
        e.level().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.5f, 0.6f);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SLEEP.remove(event.getEntity().getUUID());
        WARDS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onStopped(ServerStoppedEvent event) {
        ZONES.clear();
        SLEEP.clear();
        WARDS.clear();
    }

    private static DamageSource music(ServerPlayer p) {
        return new DamageSource(p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(CuttingNoteEntity.MUSIC_MAGIC), p);
    }
}
