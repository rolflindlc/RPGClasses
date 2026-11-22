package io.github.rpgclasses.spin;

import com.google.gson.JsonObject;
import io.github.rpgclasses.content.race.Races;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.DebugLog;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.joml.Vector3f;

/**
 * Habilidades activas del Maestro del Giro (adenda, secciones 6 y 7): Ruta de Rebotes (nivel 5), Giro Reverso y Paso de
 * Giro (nivel 15). El Rectángulo Dorado (nivel 30, la ulti) está en {@link GoldenRectangle}.
 */
public final class SpinAbilities {
    private static final DustParticleOptions ROUTE = new DustParticleOptions(new Vector3f(1.0f, 0.84f, 0.3f), 0.9f);

    private SpinAbilities() {}

    // ------------------------------------------------------------------ Ruta de Rebotes

    /**
     * Tiro de billar: traza una ruta que empieza en el enemigo al que apuntas (o el más cercano a la vista) y salta de
     * enemigo en enemigo; la bola elegida la recorre muy rápido, golpeando a cada enemigo intermedio sin clavarse (cada
     * rebote pega más que el anterior) y se clava en el último. Con un solo enemigo, rebota a su alrededor y vuelve a por
     * él. Cada golpe cuenta para el Giro.
     */
    static boolean bounceRoute(ServerPlayer p, JsonObject params) {
        if (GoldenRectangle.active(p)) return false;
        ItemStack band = p.getMainHandItem();
        if (!Bandolier.is(band) || !SpinMaster.canThrow(p)) return false;
        SphereKind m = Bandolier.current(band);
        if (m == null) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.spin.empty").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        long now = p.level().getGameTime();
        Bandolier.clearStale(band, SpinThrow::alive, now);
        List<Object> route = trickRoute(p, params);
        if (route == null) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.spin.no_targets").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        int max = SpinThrow.maxSpheres(p);
        boolean canLaunch = Bandolier.firstReady(band, m, now) != null && SpinThrow.spheresOf(p).size() < max;
        SphereEntity e = canLaunch ? SpinThrow.launchTrick(p, band, m, route, params) : null;
        if (e == null) {
            // Sin bola lista o con todas fuera: la que está en vuelo o volviendo vuelve al instante y sale por la ruta
            // (antes la habilidad no hacía nada si la bola estaba fuera, que es casi siempre en combate).
            e = takeOver(p, route, params);
        }
        if (e == null) {
            if (SpinThrow.spheresOf(p).size() >= max) {
                p.displayClientMessage(Component.translatable("message.rites_and_paths.spin.limit", max).withStyle(ChatFormatting.GRAY), true);
            } else {
                p.displayClientMessage(Component.translatable("message.rites_and_paths.spin.no_ready",
                        m.name()).withStyle(ChatFormatting.GRAY), true);
            }
            return false;
        }
        SpinState.of(p).lastThrow = now;
        // La ruta se ve un momento (la ven todos): una línea dorada de punto en punto.
        ServerLevel level = p.serverLevel();
        Vec3 a = e.position();
        for (Object wp : route) {
            Vec3 b = wp instanceof LivingEntity le ? le.getBoundingBox().getCenter() : (Vec3) wp;
            Vec3 d = b.subtract(a);
            int n = Math.max(1, (int) (d.length() * 2.5));
            for (int k = 0; k <= n; k++) {
                Vec3 q = a.add(d.scale(k / (double) n));
                level.sendParticles(ROUTE, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
            a = b;
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 0.8f, 1.3f);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.4f);
        DebugLog.event(p, "giro", "Ruta de Rebotes: {} puntos", route.size());
        return true;
    }

    /** La bola propia en vuelo o volviendo más cercana al jugador, metida en la ruta. Null si no hay ninguna utilizable. */
    private static SphereEntity takeOver(ServerPlayer p, List<Object> route, JsonObject params) {
        List<SphereEntity> own = new ArrayList<>(SpinThrow.spheresOf(p));
        own.sort(Comparator.comparingDouble(x -> x.distanceToSqr(p)));
        for (SphereEntity e : own) {
            if (e.takeOverForTrick(p, route, AbilityService.number(params, "route_speed", 2.6),
                    AbilityService.number(params, "hit_factor", 0.6), AbilityService.number(params, "per_hit_bonus", 0.15))) {
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.7f, 1.7f);
                return e;
            }
        }
        return null;
    }

    /**
     * Enemigo justo bajo la mira (sin pared delante), aunque no sea hostil: si el jugador apunta a algo a propósito, la
     * ruta empieza en él. Los saltos siguientes solo eligen objetivos automáticos (hostiles o que van a por ti).
     */
    private static LivingEntity crosshairEnemy(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 far = eye.add(p.getLookAngle().scale(range));
        BlockHitResult wall = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 end = wall.getType() == HitResult.Type.MISS ? far : wall.getLocation();
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity x : p.level().getEntities(p, new net.minecraft.world.phys.AABB(eye, end).inflate(1.0),
                y -> y instanceof LivingEntity le && le.isAlive() && !(y instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
            LivingEntity le = (LivingEntity) x;
            if (!io.github.rpgclasses.server.Allies.isEnemy(p, le)) continue;
            java.util.Optional<Vec3> c = le.getBoundingBox().inflate(0.3).clip(eye, end);
            if (c.isEmpty()) continue;
            double d = eye.distanceToSqr(c.get());
            if (d < bestD) {
                bestD = d;
                best = le;
            }
        }
        return best;
    }

    /**
     * La ruta: hasta {@code route_hits} golpes. El primero, el enemigo más cerca de la mira (en un cono) o, si no hay, el más
     * cercano a la vista; luego el enemigo más cercano a la vista desde el anterior que aún no ha recibido golpe (si no
     * queda ninguno, repite a otro). Con un solo enemigo, un punto de rebote a su lado y vuelta a él. Null si no hay nadie.
     */
    static List<Object> trickRoute(ServerPlayer p, JsonObject params) {
        int hits = Math.max(1, (int) Math.round(AbilityService.number(params, "route_hits", 4)));
        double range = AbilityService.number(params, "route_range", 16);
        double hop = AbilityService.number(params, "route_hop", 10);
        double cone = Math.toRadians(AbilityService.number(params, "route_cone_degrees", 70) / 2);
        double bank = AbilityService.number(params, "bank_distance", 3);
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        List<LivingEntity> cands = new ArrayList<>();
        for (LivingEntity e : SphereHits.enemiesAround(p, eye, range, null)) {
            if (SphereHits.visible(p.level(), eye, e.getBoundingBox().getCenter(), p)) cands.add(e);
        }
        LivingEntity aimed = crosshairEnemy(p, range);
        if (aimed != null && !cands.contains(aimed)) cands.add(aimed);
        if (cands.isEmpty()) return null;
        LivingEntity first = aimed;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : first != null ? List.<LivingEntity>of() : cands) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double ang = Math.acos(Mth.clamp(look.dot(to.normalize()), -1, 1));
            if (ang <= cone && ang < best) {
                best = ang;
                first = e;
            }
        }
        if (first == null) first = cands.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(eye))).orElse(null);
        List<Object> out = new ArrayList<>();
        List<LivingEntity> used = new ArrayList<>();
        out.add(first);
        used.add(first);
        LivingEntity cur = first;
        Vec3 prev = eye;
        for (int count = 1; count < hits; count++) {
            Vec3 c = cur.getBoundingBox().getCenter();
            LivingEntity next = nearest(p, cands, cur, c, hop, used, true);
            if (next == null) next = nearest(p, cands, cur, c, hop, used, false);
            if (next == null) {
                // Un solo enemigo: rebota a su lado (o en la pared que haya) y vuelve a por él.
                Vec3 bankPt = bankPoint(p, c, prev, bank, count);
                out.add(bankPt);
                out.add(cur);
                prev = bankPt;
                continue;
            }
            out.add(next);
            if (!used.contains(next)) used.add(next);
            prev = c;
            cur = next;
        }
        return out;
    }

    private static LivingEntity nearest(ServerPlayer p, List<LivingEntity> cands, LivingEntity cur, Vec3 from, double hop,
                                        List<LivingEntity> used, boolean fresh) {
        LivingEntity best = null;
        double bestD = hop * hop;
        for (LivingEntity e : cands) {
            if (e == cur || (fresh && used.contains(e)) || !e.isAlive()) continue;
            Vec3 c = e.getBoundingBox().getCenter();
            double d = c.distanceToSqr(from);
            if (d <= bestD && SphereHits.visible(p.level(), from, c, p)) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** Punto de rebote junto a un enemigo solo: sigue la dirección de llegada girada a un lado y al otro, hasta una pared. */
    private static Vec3 bankPoint(ServerPlayer p, Vec3 center, Vec3 from, double dist, int k) {
        Vec3 in = center.subtract(from).multiply(1, 0, 1);
        if (in.lengthSqr() < 1.0E-4) in = p.getLookAngle().multiply(1, 0, 1);
        if (in.lengthSqr() < 1.0E-4) in = new Vec3(1, 0, 0);
        in = in.normalize();
        double ang = Math.toRadians((k % 2 == 1 ? 1 : -1) * (40 + 25 * ((k - 1) / 2)));
        Vec3 dir = new Vec3(in.x * Math.cos(ang) - in.z * Math.sin(ang), 0.12, in.x * Math.sin(ang) + in.z * Math.cos(ang)).normalize();
        Vec3 end = center.add(dir.scale(dist));
        BlockHitResult hit = p.level().clip(new ClipContext(center, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(dir.scale(0.25));
    }

    // ------------------------------------------------------------------ Giro Reverso

    /** Detona las minas propias o tira de la bola clavada (o del último enemigo golpeado) y lo arrastra hacia el jugador. */
    static boolean reverseSpin(ServerPlayer p, JsonObject params) {
        if (GoldenRectangle.active(p)) return false;
        List<SphereEntity> mines = new ArrayList<>();
        SphereEntity stuck = null;
        for (SphereEntity e : SpinThrow.spheresOf(p)) {
            if (e.state() == SphereEntity.MINE) mines.add(e);
            else if (e.isStuck() && (stuck == null || e.tickCount < stuck.tickCount)) stuck = e;
        }
        if (!mines.isEmpty()) {
            for (SphereEntity e : mines) SphereHits.explodeMine(e, p);
            return true;
        }
        SpinState.State st = SpinState.of(p);
        long now = p.level().getGameTime();
        LivingEntity target = null;
        int claw = 0;
        if (stuck != null) {
            target = stuck.stuck;
            claw = stuck.clawLevel;
        } else if (st.lastHitId >= 0) {
            long window = Math.round(AbilityService.number(params, "stuck_window_seconds", 1.5) * 20) + (long) st.lastHitClaw * SpinEnchantments.CLAW_TICKS;
            Entity e = p.level().getEntity(st.lastHitId);
            if (now - st.lastHitAt <= window && e instanceof LivingEntity le && le.isAlive()) {
                target = le;
                claw = st.lastHitClaw;
            }
        }
        if (target == null) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.spin.nothing_stuck").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        double blocks = AbilityService.number(params, "pull_blocks", 4) + claw * SpinEnchantments.CLAW_PULL;
        Vec3 to = p.position().subtract(target.position());
        double dist = Math.sqrt(to.x * to.x + to.z * to.z);
        if (!Races.isBoss(target) && dist > 1.5) {
            double pull = Math.min(blocks, dist - 1.5);
            Vec3 flat = new Vec3(to.x, 0, to.z).normalize();
            target.setDeltaMovement(flat.scale(pull * 0.42).add(0, 0.3, 0));
            target.hurtMarked = true;
            target.hasImpulse = true;
        }
        if (stuck != null) stuck.lingerTicks = 0;
        ServerLevel level = p.serverLevel();
        SphereHits.line(level, target.getBoundingBox().getCenter(), p.getEyePosition().add(0, -0.3, 0), ParticleTypes.ENCHANTED_HIT);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1.0f, 0.7f);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.6f, 0.6f);
        SpinFxSender.onomatopoeia(level, target.getEyePosition(), SpinFxSender.WORD_GYARU);
        return true;
    }

    // ------------------------------------------------------------------ Paso de Giro

    /** Impulso en espiral hacia donde se mueve (o hacia donde mira); el siguiente impacto da más Giro. */
    static boolean spinStep(ServerPlayer p, JsonObject params) {
        if (GoldenRectangle.active(p)) return false;
        if (p.isPassenger() || p.getAbilities().flying || p.isFallFlying()) return false;
        SpinState.State st = SpinState.of(p);
        long now = p.level().getGameTime();
        // Seguro contra el vuelo: entre dashes hay un bloqueo mínimo (aunque se lleve el Amuleto del Creativo o la tecla se
        // repita al mantenerla) y en el aire solo se puede hacer un dash hasta volver a tocar el suelo.
        boolean grounded = p.onGround() || p.isInWater() || p.onClimbable();
        if (now < st.dashLock || (!grounded && st.dashedSinceGround)) return false;
        Vec3 dir = SpinState.moveDir(p);
        if (dir.lengthSqr() < 1.0E-4) {
            Vec3 look = p.getLookAngle();
            dir = new Vec3(look.x, 0, look.z);
        }
        if (dir.lengthSqr() < 1.0E-4) return false;
        dir = dir.normalize();
        double speed = AbilityService.number(params, "dash_speed", 1.1);
        double lift = AbilityService.number(params, "dash_lift", 0.25);
        // En el aire no gana altura: el impulso es horizontal y solo frena la caída.
        double vy = grounded ? lift : Math.max(p.getDeltaMovement().y, 0.0);
        p.setDeltaMovement(dir.scale(speed).add(0, vy, 0));
        p.hurtMarked = true;
        p.resetFallDistance();
        st.dashLock = now + Math.round(AbilityService.number(params, "dash_lock_seconds", 0.5) * 20);
        st.dashedSinceGround = true;
        st.stepUntil = now + Math.round(AbilityService.number(params, "step_window_seconds", 1.5) * 20);
        st.stepBonus = AbilityService.number(params, "step_spin", 0.06);
        ServerLevel level = p.serverLevel();
        Vec3 side = dir.cross(new Vec3(0, 1, 0)).normalize();
        for (int i = 0; i < 24; i++) {
            double t = i / 24.0;
            double a = t * Math.PI * 4;
            Vec3 q = p.position().add(dir.scale(t * 5)).add(side.scale(Math.cos(a) * 0.6)).add(0, 1 + Math.sin(a) * 0.6, 0);
            level.sendParticles(ParticleTypes.WAX_OFF, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 0.7f, 1.4f);
        return true;
    }
}
