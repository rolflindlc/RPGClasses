package io.github.rpgclasses.content;

import io.github.rpgclasses.registry.ModEffects;
import io.github.rpgclasses.server.MechanicService;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

/**
 * Efectos visuales de las habilidades de clase y de algunos rasgos raciales (solo partículas del servidor, que ven
 * todos los jugadores cercanos). Lo que dura en el tiempo se dibuja aquí en cada tic; lo instantáneo se llama desde
 * cada habilidad con los métodos estáticos.
 */
public final class ClassFx {
    public static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.85f, 0.08f, 0.05f), 1.2f);
    public static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.55f, 0.0f, 0.05f), 1.0f);
    public static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(0.95f, 0.78f, 0.25f), 1.0f);
    public static final DustParticleOptions TOXIC = new DustParticleOptions(new Vector3f(0.35f, 0.85f, 0.15f), 1.0f);
    public static final DustParticleOptions SHIELD = new DustParticleOptions(new Vector3f(0.75f, 0.85f, 1.0f), 1.1f);

    /** Instancia solo para el bus de eventos. */
    public ClassFx() {}

    // ------------------------------------------------------------------ utilidades

    public static void ring(Entity center, ParticleOptions particle, double radius, double dy, int count) {
        if (!(center.level() instanceof ServerLevel level)) return;
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count;
            level.sendParticles(particle, center.getX() + Math.cos(a) * radius, center.getY() + dy,
                    center.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    public static void burst(Entity at, ParticleOptions particle, int count, double speed) {
        if (!(at.level() instanceof ServerLevel level)) return;
        level.sendParticles(particle, at.getX(), at.getY() + at.getBbHeight() * 0.6, at.getZ(), count,
                at.getBbWidth() * 0.5, at.getBbHeight() * 0.35, at.getBbWidth() * 0.5, speed);
    }

    /** Partículas en una línea entre dos entidades (robo de vida). */
    public static void line(Entity from, Entity to, ParticleOptions particle, int steps) {
        if (!(from.level() instanceof ServerLevel level)) return;
        double fx = from.getX(), fy = from.getY() + from.getBbHeight() * 0.6, fz = from.getZ();
        double tx = to.getX(), ty = to.getY() + to.getBbHeight() * 0.6, tz = to.getZ();
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            level.sendParticles(particle, fx + (tx - fx) * t, fy + (ty - fy) * t, fz + (tz - fz) * t, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    // ------------------------------------------------------------------ efectos que duran

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        if (p.isInvisible() || p.isSpectator()) return;
        long t = p.server.getTickCount() + p.getId();

        // Baluarte (y Baluarte de Embestida): escudo de partículas que gira alrededor del Paladín.
        if ((p.hasEffect(ModEffects.BULWARK.get()) || p.hasEffect(ModEffects.CHARGING_BULWARK.get())) && t % 3 == 0) {
            double a = (t % 60) / 60.0 * Math.PI * 2;
            ServerLevel level = p.serverLevel();
            for (int k = 0; k < 4; k++) {
                double b = a + k * Math.PI / 2;
                level.sendParticles(SHIELD, p.getX() + Math.cos(b) * 0.9, p.getY() + 0.3 + (t % 20) / 20.0 * 1.5,
                        p.getZ() + Math.sin(b) * 0.9, 1, 0, 0, 0, 0);
            }
        }
        // Furia Berserker: aura roja y vapor.
        if (p.hasEffect(ModEffects.BERSERKER_RAGE.get()) && t % 2 == 0) {
            p.serverLevel().sendParticles(RED, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.35, 0.6, 0.35, 0);
            if (t % 10 == 0) p.serverLevel().sendParticles(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + 2.1, p.getZ(), 1, 0.2, 0, 0.2, 0);
        }
        // Estandarte: anillo dorado en el borde del aura cada segundo, y una chispa en quien la recibe.
        if (t % 20 == 0) {
            var aura = MechanicService.find(p, BannerAura.AURA);
            if (aura != null) ring(p, GOLD, aura.number("radius", 8), 0.15, 40);
        }
        if (t % 15 == 0 && p.hasEffect(ModEffects.BANNER.get())) {
            p.serverLevel().sendParticles(GOLD, p.getX(), p.getY() + 2.0, p.getZ(), 2, 0.2, 0.1, 0.2, 0);
        }
    }
}
