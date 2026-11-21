package io.github.rpgclasses.spin;

import com.google.gson.JsonObject;
import io.github.rpgclasses.content.race.Races;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.DebugLog;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

/**
 * Habilidades activas del Maestro del Giro (adenda, secciones 6 y 7): Ruta de Rebotes (nivel 5), Giro Reverso y Paso de
 * Giro (nivel 15) y Rectángulo Dorado (nivel 30, la ulti: Giro Dorado y Espiral Final).
 */
public final class SpinAbilities {
    private SpinAbilities() {}

    // ------------------------------------------------------------------ Ruta de Rebotes

    /** Calcula la ruta de rebotes de la bola elegida hacia donde se mira, la enseña un momento y la fija para el siguiente lanzamiento. */
    static boolean bounceRoute(ServerPlayer p, JsonObject params) {
        ItemStack band = p.getMainHandItem();
        if (!Bandolier.is(band) || !SpinMaster.canThrow(p)) return false;
        SphereMaterial m = Bandolier.current(band);
        if (m == null) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.spin.empty").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        // Mismos números que el lanzamiento real (alcance y rebotes), con una bola de prueba que no se añade al mundo.
        SphereEntity probe = new SphereEntity(SpinRegistry.SPHERE_ENTITY.get(), p.level());
        ItemStack copy = ItemStack.EMPTY;
        Bandolier.Entry e = Bandolier.firstReady(band, m, p.level().getGameTime());
        if (e != null) copy = e.stack;
        SpinThrow.configure(probe, p, m, copy, SpinThrow.DIRECT, false);
        int max = (int) Math.round(AbilityService.number(params, "route_bounces_max", 8));
        List<Vec3> route = route(p, p.getEyePosition(), p.getLookAngle(), probe.range, Math.min(max, probe.dry ? 0 : probe.bounces));
        SpinState.State st = SpinState.of(p);
        long now = p.level().getGameTime();
        int show = (int) Math.round(AbilityService.number(params, "preview_seconds", 1.5) * 20);
        st.route = route;
        st.routeShowUntil = now + show;
        st.routeUntil = now + show * 2L;
        p.displayClientMessage(Component.translatable("message.rites_and_paths.spin.route").withStyle(ChatFormatting.GOLD), true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 0.8f, 1.3f);
        return true;
    }

    /** Reflexiones de un rayo desde {@code eye} hacia {@code look}: los puntos donde rebota y el final. */
    static List<Vec3> route(ServerPlayer p, Vec3 eye, Vec3 look, double range, int bounces) {
        List<Vec3> pts = new ArrayList<>();
        Vec3 pos = eye;
        Vec3 dir = look.normalize();
        double left = range;
        pts.add(pos);
        for (int i = 0; i <= bounces && left > 0.1; i++) {
            Vec3 end = pos.add(dir.scale(left));
            BlockHitResult h = p.level().clip(new ClipContext(pos, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (h.getType() == HitResult.Type.MISS) {
                pts.add(end);
                break;
            }
            Vec3 at = h.getLocation();
            left -= pos.distanceTo(at);
            pts.add(at);
            if (i == bounces) break;
            Vec3 n = Vec3.atLowerCornerOf(h.getDirection().getNormal());
            dir = dir.subtract(n.scale(2 * dir.dot(n))).normalize();
            pos = at.add(n.scale(0.06));
        }
        return pts;
    }

    // ------------------------------------------------------------------ Giro Reverso

    /** Detona las minas propias o tira de la bola clavada (o del último enemigo golpeado) y lo arrastra hacia el jugador. */
    static boolean reverseSpin(ServerPlayer p, JsonObject params) {
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
        if (p.isPassenger() || p.getAbilities().flying || p.isFallFlying()) return false;
        Vec3 dir = SpinState.moveDir(p);
        if (dir.lengthSqr() < 1.0E-4) {
            Vec3 look = p.getLookAngle();
            dir = new Vec3(look.x, 0, look.z);
        }
        if (dir.lengthSqr() < 1.0E-4) return false;
        dir = dir.normalize();
        double speed = AbilityService.number(params, "dash_speed", 1.1);
        double lift = AbilityService.number(params, "dash_lift", 0.25);
        p.setDeltaMovement(dir.scale(speed).add(0, lift, 0));
        p.hurtMarked = true;
        p.resetFallDistance();
        SpinState.State st = SpinState.of(p);
        st.stepUntil = p.level().getGameTime() + Math.round(AbilityService.number(params, "step_window_seconds", 1.5) * 20);
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

    // ------------------------------------------------------------------ Rectángulo Dorado

    /**
     * Con el Giro al 100 %: Giro Dorado. Pulsando otra vez durante el Giro Dorado, Espiral Final ya. El cooldown se pone al
     * lanzar la Espiral Final (por eso devuelve false: AbilityService no lo pone al activar).
     */
    static boolean goldenRectangle(ServerPlayer p, JsonObject params) {
        SpinState.State st = SpinState.of(p);
        if (st.golden) {
            finalSpiral(p, st);
            return false;
        }
        if (st.spin < 0.999f) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.spin.need_full").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        int ticks = (int) Math.round(AbilityService.number(params, "golden_seconds", 8) * 20);
        st.spin = 0;
        st.golden = true;
        st.goldenUntil = p.level().getGameTime() + ticks;
        st.spiral = 0;
        p.addEffect(new MobEffectInstance(SpinRegistry.GOLDEN_SPIN.get(), ticks, 0, false, true, true));
        ServerLevel level = p.serverLevel();
        for (int i = 0; i < 40; i++) {
            double a = i * 0.45;
            double r = 0.3 + i * 0.04;
            level.sendParticles(ParticleTypes.WAX_OFF, p.getX() + Math.cos(a) * r, p.getY() + 0.2 + i * 0.05, p.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.6f);
        p.displayClientMessage(Component.translatable("message.rites_and_paths.spin.golden").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        SpinFxSender.onomatopoeia(level, p.getEyePosition().add(0, 0.6, 0), SpinFxSender.WORD_GOGOGO);
        DebugLog.event(p, "giro", "Rectángulo Dorado: Giro Dorado {} tics", ticks);
        return false;
    }

    /** Espiral Final: una bola por objetivo orbita al jugador y se lanza contra él, con daño según las cargas acumuladas. */
    static void finalSpiral(ServerPlayer p, SpinState.State st) {
        st.golden = false;
        p.removeEffect(SpinRegistry.GOLDEN_SPIN.get());
        JsonObject params = SpinMaster.params(SpinMaster.GOLDEN_RECTANGLE);
        AbilityService.setCooldown(p, SpinMaster.GOLDEN_RECTANGLE, AbilityService.ticks(params, "cooldown_seconds", 120));
        Net.syncPlayer(p);
        int charges = st.spiral;
        st.spiral = 0;
        double radius = AbilityService.number(params, "spiral_radius", 16);
        int maxTargets = (int) Math.round(AbilityService.number(params, "spiral_targets", 8));
        List<LivingEntity> targets = SphereHits.enemiesAround(p, p.position(), radius, null);
        targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        if (targets.size() > maxTargets) targets = new ArrayList<>(targets.subList(0, maxTargets));
        double ironDamage = SpinMaster.t("sword_damage", 6) * SpinMaster.t("iron_fraction", 0.80);
        float damage = (float) (charges * AbilityService.number(params, "spiral_damage", 0.25) * ironDamage);
        int orbit = (int) Math.round(AbilityService.number(params, "orbit_seconds", 1.5) * 20);
        ItemStack band = p.getMainHandItem();
        SphereMaterial look = Bandolier.is(band) && Bandolier.current(band) != null ? Bandolier.current(band) : SphereMaterial.GOLD;
        int n = Math.max(targets.size(), 4);
        for (int i = 0; i < n; i++) {
            SphereEntity e = new SphereEntity(SpinRegistry.SPHERE_ENTITY.get(), p.level());
            e.setOwner(p);
            e.virtual = true;
            e.setMaterial(look);
            e.setGolden(true);
            e.setSpin(1f);
            e.orbitIndex = i;
            e.orbitTotal = n;
            e.orbitTicks = orbit;
            e.orbitTarget = i < targets.size() ? targets.get(i) : null;
            e.fixedDamage = Math.max(0.5f, damage);
            e.speed = SpinMaster.t("sphere_speed", 1.8) * 1.5;
            e.range = 48;
            Vec3 start = p.position().add(0, 1, 0);
            e.setPos(start.x, start.y, start.z);
            e.setState(SphereEntity.ORBIT);
            io.github.rpgclasses.server.DamagePipeline.markProjectile(e, 1.0, true);
            p.level().addFreshEntity(e);
        }
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7f, 1.5f);
        p.displayClientMessage(Component.translatable("message.rites_and_paths.spin.spiral", charges).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        SpinFxSender.cinematic(p, orbit + 24);
        SpinFxSender.onomatopoeia(level, p.getEyePosition().add(0, 0.8, 0), SpinFxSender.WORD_GOGOGO);
        DebugLog.event(p, "giro", "Espiral Final: {} cargas, {} objetivos, {} de daño", charges, targets.size(), damage);
    }
}
