package io.github.rpgclasses.spin;

import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.DebugLog;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Estado del Maestro del Giro (adenda, secciones 4 y 7): la barra de Giro (0-1) con su cadena de impactos, el Giro Dorado
 * del Rectángulo Dorado y sus cargas de espiral, la velocidad del jugador (Jinete), la Ruta de Rebotes fijada y las marcas
 * de Punto Débil (Escáner). Vive en memoria: se pierde al morir o al desconectarse.
 */
public final class SpinState {
    /** Estado de un jugador. */
    public static final class State {
        public float spin;
        long lastImpact = -10_000;
        int chain;
        long stepUntil;
        double stepBonus;
        boolean golden;
        long goldenUntil;
        int spiral;
        long nextCatch;
        long lastArrival = -10_000;
        long lastArrivalId;
        SphereMaterial lastArrivalMaterial;
        int steadyTarget = -1, steadyCount;
        long lastThrow = -10_000;
        // Velocidad (bloques por tic, horizontal) y dirección de movimiento.
        Vec3 lastPos;
        double speed;
        Vec3 moveDir = Vec3.ZERO;
        // Último enemigo golpeado (Giro Reverso).
        int lastHitId = -1;
        long lastHitAt = -10_000;
        int lastHitClaw;
        // Ruta de Rebotes.
        List<Vec3> route;
        long routeUntil, routeShowUntil;
        // Punto Débil: objetivos marcados (id → fin).
        final Map<Integer, Long> marks = new HashMap<>();
        // Sincronización.
        float sentSpin = -1;
        int sentGolden = -1, sentSpiral = -1;
    }

    private static final Map<UUID, State> STATES = io.github.rpgclasses.server.SessionState.track(new HashMap<>());
    private static final DustParticleOptions ROUTE = new DustParticleOptions(new Vector3f(1.0f, 0.84f, 0.3f), 0.8f);
    private static final DustParticleOptions MARK = new DustParticleOptions(new Vector3f(1.0f, 0.2f, 0.25f), 1.2f);

    public static State of(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    public static float spin(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s == null ? 0 : s.spin;
    }

    public static boolean golden(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.golden;
    }

    // ------------------------------------------------------------------ Giro

    /** Un impacto en un enemigo: sube el Giro (con la cadena), carga la espiral del Giro Dorado y se apunta para Giro Reverso. */
    static void onImpact(ServerPlayer p, LivingEntity target, int clawLevel) {
        State s = of(p);
        long now = p.level().getGameTime();
        double chainTicks = SpinMaster.g("chain_seconds", 3) * 20;
        s.chain = now - s.lastImpact <= chainTicks ? s.chain + 1 : 0;
        s.lastImpact = now;
        double chainMult = Math.min(SpinMaster.g("chain_max", 2.0), Math.pow(1 + SpinMaster.g("chain_step", 0.25), s.chain));
        double rate = 1 + SpinMaster.mech(p, SpinMaster.ROTATION, "spin_rate", 0) + SpinMaster.mech(p, SpinMaster.HEAVY_SPIN, "spin_rate", 0)
                + SpinMaster.mech(p, SpinMaster.SWIFT_SPIN, "spin_rate", 0);
        double gain = SpinMaster.g("spin_gain", 0.04) * chainMult * Math.max(0, rate);
        if (now <= s.stepUntil) {
            gain += s.stepBonus;
            s.stepUntil = 0;
        }
        if (!s.golden) add(p, s, gain);
        else s.spiral = Math.min((int) Math.round(SpinMaster.number(SpinMaster.GOLDEN_RECTANGLE, "max_spiral", 20)), s.spiral + 1);
        s.lastHitId = target.getId();
        s.lastHitAt = now;
        s.lastHitClaw = clawLevel;
    }

    /** Una bola vuelve sin haber impactado a ningún enemigo: se pierde la mitad del Giro. */
    static void onMiss(ServerPlayer p) {
        State s = of(p);
        if (s.golden || s.spin <= 0) return;
        float before = s.spin;
        s.spin = (float) Math.max(0, s.spin * (1 - SpinMaster.g("miss_loss", 0.5)));
        s.chain = 0;
        DebugLog.event(p, "giro", "fallo: Giro {} → {}", before, s.spin);
    }

    static void add(ServerPlayer p, State s, double amount) {
        s.spin = (float) Math.max(0, Math.min(1, s.spin + amount));
    }

    // ------------------------------------------------------------------ velocidad (Jinete) y movimiento

    /** Velocidad horizontal actual frente a la máxima (a pie: carrera con salto; montado: la de la montura). */
    public static double speedFraction(ServerPlayer p) {
        State s = of(p);
        Entity mover = p.getVehicle() != null ? p.getVehicle() : p;
        double attr = 0.1;
        if (mover instanceof LivingEntity le && le.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            attr = le.getAttributeValue(Attributes.MOVEMENT_SPEED);
        }
        double max;
        if (mover == p) {
            if (p.isSprinting()) attr /= 1.3; // el modificador de correr ya está en el atributo
            max = attr * 2.16 * 1.3 * 1.26;
        } else {
            max = attr * 2.15;
        }
        return max <= 1.0E-4 ? 0 : s.speed / max;
    }

    static Vec3 moveDir(ServerPlayer p) {
        State s = of(p);
        return s.speed > 0.03 ? s.moveDir : Vec3.ZERO;
    }

    // ------------------------------------------------------------------ Punto Débil

    static void markWeak(ServerPlayer p, LivingEntity target) {
        var m = SpinMaster.mech(p, SpinMaster.WEAK_POINT);
        long until = p.level().getGameTime() + Math.round((m == null ? 6 : m.number("duration_seconds", 6)) * 20);
        of(p).marks.merge(target.getId(), until, Math::max);
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p instanceof FakePlayer) continue;
            if (!SpinMaster.isSpinMaster(p)) {
                if (STATES.containsKey(p.getUUID())) {
                    STATES.remove(p.getUUID());
                    Net.toPlayer(p, new Packets.SpinSync(0, 0, 0, 0, false));
                }
                continue;
            }
            DebugLog.guard(p, "Maestro del Giro (tick)", () -> tick(p));
        }
    }

    private static void tick(ServerPlayer p) {
        State s = of(p);
        long now = p.level().getGameTime();
        // Velocidad (media suave) y dirección de movimiento.
        Vec3 pos = p.getVehicle() != null ? p.getVehicle().position() : p.position();
        if (s.lastPos != null) {
            Vec3 d = pos.subtract(s.lastPos).multiply(1, 0, 1);
            double len = d.length();
            if (len < 4) {
                s.speed = s.speed * 0.5 + len * 0.5;
                if (len > 0.02) s.moveDir = d.scale(1 / len);
            }
        }
        s.lastPos = pos;
        // Bajada fuera de combate.
        double delay = SpinMaster.g("decay_delay_seconds", 5) * 20;
        if (!s.golden && s.spin > 0 && now - s.lastImpact > delay) {
            s.spin = (float) Math.max(0, s.spin - SpinMaster.g("decay_per_second", 0.05) / 20.0);
        }
        // Fin del Giro Dorado: Espiral Final.
        if (s.golden && (now >= s.goldenUntil || !p.hasEffect(SpinRegistry.GOLDEN_SPIN.get()))) {
            SpinAbilities.finalSpiral(p, s);
        }
        // Ruta de Rebotes: se dibuja solo para él.
        if (s.route != null && now <= s.routeShowUntil && now % 3 == 0) {
            for (int i = 0; i + 1 < s.route.size(); i++) {
                Vec3 a = s.route.get(i), b = s.route.get(i + 1);
                Vec3 d = b.subtract(a);
                int n = Math.max(1, (int) (d.length() * 2));
                for (int k = 0; k <= n; k++) {
                    Vec3 q = a.add(d.scale(k / (double) n));
                    p.serverLevel().sendParticles(p, ROUTE, true, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                }
                p.serverLevel().sendParticles(p, ParticleTypes.WAX_OFF, true, b.x, b.y, b.z, 3, 0.05, 0.05, 0.05, 0.02);
            }
        }
        if (s.route != null && now > s.routeUntil) s.route = null;
        // Punto Débil: marca visible para el Escáner (y sus aliados con Punto Débil Compartido).
        if (!s.marks.isEmpty() && now % 8 == 0) showMarks(p, s, now);
        sync(p, s, now);
    }

    private static void showMarks(ServerPlayer p, State s, long now) {
        boolean shared = AbilityService.has(p, SpinMaster.SHARED_WEAK_POINT);
        double range = SpinMaster.number(SpinMaster.SHARED_WEAK_POINT, "visible_range", 32);
        Iterator<Map.Entry<Integer, Long>> it = s.marks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> e = it.next();
            Entity t = p.level().getEntity(e.getKey());
            if (e.getValue() <= now || !(t instanceof LivingEntity le) || !le.isAlive()) {
                it.remove();
                continue;
            }
            // La marca real vive en el objetivo; si un Escáner sin el nivel 5 la renovó otro, manda la del objetivo.
            double x = le.getX(), y = le.getY() + le.getBbHeight() * 0.75, z = le.getZ();
            List<ServerPlayer> viewers = new ArrayList<>();
            viewers.add(p);
            if (shared) {
                for (ServerPlayer o : p.serverLevel().players()) {
                    if (o != p && o.distanceToSqr(le) <= range * range && io.github.rpgclasses.server.Allies.isAlly(p, o)) viewers.add(o);
                }
            }
            for (ServerPlayer v : viewers) {
                v.serverLevel().sendParticles(v, MARK, true, x, y, z, 2, 0.15, 0.15, 0.15, 0);
                v.serverLevel().sendParticles(v, ParticleTypes.CRIT, true, x, y, z, 1, 0.1, 0.1, 0.1, 0);
            }
        }
    }

    private static void sync(ServerPlayer p, State s, long now) {
        int goldenTicks = s.golden ? (int) Math.max(0, s.goldenUntil - now) : 0;
        boolean changed = Math.abs(s.spin - s.sentSpin) >= 0.004f || (s.golden ? 1 : 0) != (s.sentGolden > 0 ? 1 : 0)
                || s.spiral != s.sentSpiral || (s.golden && now % 10 == 0);
        if (!changed) return;
        s.sentSpin = s.spin;
        s.sentGolden = goldenTicks;
        s.sentSpiral = s.spiral;
        Net.toPlayer(p, new Packets.SpinSync(s.spin, goldenTicks, s.spiral,
                (int) Math.round(SpinMaster.number(SpinMaster.GOLDEN_RECTANGLE, "max_spiral", 20)), true));
    }

    // ------------------------------------------------------------------ fin

    /** Muere o se reinicia: Giro a 0, sin Giro Dorado y las bolas en vuelo vuelven a la bandolera. */
    public static void reset(ServerPlayer p) {
        STATES.remove(p.getUUID());
        p.removeEffect(SpinRegistry.GOLDEN_SPIN.get());
        SpinThrow.recallAll(p);
        Net.toPlayer(p, new Packets.SpinSync(0, 0, 0, 0, SpinMaster.isSpinMaster(p)));
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer) && STATES.containsKey(p.getUUID())) reset(p);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            SpinThrow.recallAll(p);
            STATES.remove(p.getUUID());
        }
    }

    @SubscribeEvent
    public void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) SpinThrow.recallAll(p);
    }

    static boolean isOwner(Player p, Entity e) {
        return e instanceof SphereEntity s && s.getOwner() == p;
    }
}
