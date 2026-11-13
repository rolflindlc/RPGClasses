package io.github.rpgclasses.content;

import io.github.rpgclasses.server.Allies;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Marca del Vacío (Elementalista, detonación Eldritch, rev. 2.16). El objetivo queda marcado unos segundos: guarda
 * una parte de todo el daño que recibe (de cualquiera) y al terminar —o al morir— implosiona: atrae a los enemigos
 * cercanos y les suelta el daño guardado como estallido sónico (ignora la armadura). Contra jefes, reducido.
 */
public final class VoidMark {
    private static final class Mark {
        UUID owner;
        LivingEntity target;
        ServerLevel level;
        Vec3 lastPos;
        long expires;
        double stored, storeFraction, radius, pull, bossFactor, areaFraction;
    }

    private static final Map<UUID, Mark> MARKS = new HashMap<>();
    private static boolean imploding;

    /** Instancia solo para el bus de eventos. */
    public VoidMark() {}

    public static void mark(ServerPlayer owner, LivingEntity target, double seconds, double storeFraction, double radius,
                            double pull, double bossFactor, double areaFraction, double initial) {
        if (!(target.level() instanceof ServerLevel level)) return;
        Mark m = MARKS.computeIfAbsent(target.getUUID(), k -> new Mark());
        m.owner = owner.getUUID();
        m.target = target;
        m.level = level;
        m.lastPos = target.position();
        m.expires = level.getGameTime() + Math.round(seconds * 20);
        m.storeFraction = storeFraction;
        m.radius = radius;
        m.pull = pull;
        m.bossFactor = bossFactor;
        m.areaFraction = areaFraction;
        m.stored += initial;
        level.sendParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                25, 0.4, 0.6, 0.4, 0.05);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.8f, 1.4f);
    }

    /** Guarda parte del daño final que recibe el marcado (de cualquier fuente salvo la propia implosión). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDamage(LivingDamageEvent event) {
        if (imploding || MARKS.isEmpty()) return;
        Mark m = MARKS.get(event.getEntity().getUUID());
        if (m != null && event.getAmount() > 0) m.stored += event.getAmount() * m.storeFraction;
    }

    @SubscribeEvent
    public void onVoidTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || MARKS.isEmpty()) return;
        Iterator<Mark> it = MARKS.values().iterator();
        while (it.hasNext()) {
            Mark m = it.next();
            long now = m.level.getGameTime();
            boolean alive = m.target.isAlive() && !m.target.isRemoved();
            if (alive) m.lastPos = m.target.position();
            if (!alive || now >= m.expires) {
                it.remove();
                implode(m);
            } else {
                ring(m, now);
            }
        }
    }

    /** Anillo de almas girando alrededor del marcado y pulsos de tinta oscura. */
    private static void ring(Mark m, long now) {
        LivingEntity t = m.target;
        double r = t.getBbWidth() / 2 + 0.6, y = t.getY() + t.getBbHeight() * 0.5;
        double a0 = now * 0.35;
        for (int i = 0; i < 3; i++) {
            double a = a0 + i * (Math.PI * 2 / 3);
            m.level.sendParticles(ParticleTypes.SCULK_SOUL, t.getX() + Math.cos(a) * r, y + Math.sin(a * 2) * 0.3,
                    t.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        if (now % 10 == 0) {
            m.level.sendParticles(ParticleTypes.SQUID_INK, t.getX(), y, t.getZ(), 8, r * 0.6, 0.3, r * 0.6, 0.02);
            m.level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, t.getX(), t.getY() + 0.1, t.getZ(), 6, r, 0, r, 0.01);
            m.level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.7f, 1.2f);
        }
    }

    private static void implode(Mark m) {
        ServerLevel level = m.level;
        Vec3 c = m.lastPos.add(0, m.target.getBbHeight() / 2, 0);
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(m.owner);
        // Partículas que se hunden hacia el centro y el estallido.
        for (int i = 0; i < 40; i++) {
            double a = level.random.nextDouble() * Math.PI * 2, d = m.radius * (0.6 + level.random.nextDouble() * 0.4);
            double dx = Math.cos(a) * d, dz = Math.sin(a) * d, dy = (level.random.nextDouble() - 0.5) * 1.5;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x + dx, c.y + dy, c.z + dz, 0, -dx, -dy, -dz, 0.12);
            level.sendParticles(ParticleTypes.SCULK_SOUL, c.x + dx, c.y + dy, c.z + dz, 0, -dx, -dy, -dz, 0.08);
        }
        level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, c.x, c.y, c.z, 30, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2f, 1.0f);
        if (owner == null || m.stored <= 0) return;

        imploding = true;
        try {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(m.radius),
                    e -> e.isAlive() && Allies.isEnemy(owner, e) && e.position().distanceToSqr(c) <= m.radius * m.radius)) {
                Vec3 pull = c.subtract(e.position()).multiply(1, 0, 1);
                if (pull.lengthSqr() > 0.01 && !e.getType().is(Tags.EntityTypes.BOSSES)) {
                    pull = pull.normalize().scale(m.pull);
                    e.push(pull.x, 0.15, pull.z);
                    e.hurtMarked = true;
                }
                double dmg = m.stored * (e == m.target ? 1 : m.areaFraction);
                if (e.getType().is(Tags.EntityTypes.BOSSES)) dmg *= m.bossFactor;
                e.invulnerableTime = 0;
                e.hurt(level.damageSources().sonicBoom(owner), (float) dmg);
            }
        } finally {
            imploding = false;
        }
    }
}
