package io.github.rpgclasses.spin;

import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.server.DamagePipeline;
import io.github.rpgclasses.server.DebugLog;
import io.github.rpgclasses.server.StatEngine;
import io.github.rpgclasses.core.stat.StatMath;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lanzar bolas (adenda del Maestro del Giro, sección 3): clic izquierdo directo, clic derecho curvo, agachado mina (Trampero).
 * Antes de lanzar, un clic derecho puede recoger una mina cercana (Trampero) o redirigir una bola en vuelo (Bola Guiada), y
 * un clic justo cuando llega una bola es una Captura Perfecta. Calcula todo lo que la bola lleva (daño, velocidad,
 * alcance, rebotes, curva, cooldown de retorno) a partir de las ramas, perks, encantamientos, material y Giro.
 */
public final class SpinThrow {
    public static final int DIRECT = 0, CURVED = 1;
    /** Bolas vivas por id de lanzamiento (para limpiar las marcas de vuelo de bolas que ya no existen). */
    private static final Map<Long, SphereEntity> LIVE = io.github.rpgclasses.server.SessionState.track(new HashMap<>());
    private static final AtomicLong IDS = new AtomicLong(System.nanoTime() & 0x7FFFFFFFFFFFL);

    static boolean alive(long id) {
        SphereEntity e = LIVE.get(id);
        return e != null && !e.isRemoved();
    }

    /** Bolas del jugador que existen ahora (sin las de la Espiral Final). */
    static List<SphereEntity> spheresOf(ServerPlayer p) {
        List<SphereEntity> out = new ArrayList<>();
        for (SphereEntity e : LIVE.values()) {
            if (!e.isRemoved() && !e.virtual && e.getOwner() == p) out.add(e);
        }
        return out;
    }

    // ------------------------------------------------------------------ paquetes

    /** Clic con la bandolera en la mano ({@code kind}: 0 directo, 1 curvo). Todo se valida aquí. */
    public static void onThrowPacket(ServerPlayer p, int kind, boolean sneak) {
        if (!p.isAlive() || p.isSpectator() || (kind != DIRECT && kind != CURVED)) return;
        ItemStack band = p.getMainHandItem();
        if (!Bandolier.is(band)) return;
        if (!SpinMaster.canThrow(p)) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.spin.not_spin_master").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        long now = p.level().getGameTime();
        SpinState.State st = SpinState.of(p);
        if (kind == CURVED && pickUpMine(p)) return;
        if (kind == CURVED && redirectGuided(p)) return;
        if (queueCatch(p, kind, sneak)) return;
        if (now - st.lastThrow < Math.round(SpinMaster.t("throw_interval_seconds", 0.25) * 20)) return;
        Bandolier.clearStale(band, SpinThrow::alive, now);

        // Captura Perfecta justo después de llegar: la bola que acaba de volver se relanza al instante.
        SphereMaterial material = Bandolier.current(band);
        long window = Math.max(1, Math.round(SpinMaster.t("catch_window_seconds", 0.25) * 20));
        boolean perfect = st.lastArrivalMaterial != null && now - st.lastArrival <= window && now >= st.nextCatch;
        if (perfect) material = st.lastArrivalMaterial;

        if (material == null) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.spin.empty").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int max = maxSpheres(p);
        if (spheresOf(p).size() >= max) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.spin.limit", max).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        boolean mine = sneak && SpinMaster.has(p, SpinMaster.SPIN_MINE);
        if (mine) {
            int maxMines = (int) Math.round(SpinMaster.mech(p, SpinMaster.SPIN_MINE, "max_mines", 2));
            int mines = 0;
            for (SphereEntity e : spheresOf(p)) if (e.mine) mines++;
            if (mines >= maxMines) {
                p.displayClientMessage(Component.translatable("message.rites_and_paths.spin.mine_limit", maxMines).withStyle(ChatFormatting.GRAY), true);
                return;
            }
        }
        if (perfect) {
            st.nextCatch = now + Math.round(SpinMaster.t("catch_icd_seconds", 1.5) * 20);
            st.lastArrivalMaterial = null;
            Bandolier.makeReady(p, st.lastArrivalId, now);
            perfectBonus(p, st);
        }
        SphereEntity e = launch(p, band, material, kind, mine);
        if (e == null) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.spin.no_ready",
                    Component.translatable("item.rites_and_paths." + material.itemName())).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        st.lastThrow = now;
    }

    /** Tecla de cambiar de bola: pasa al siguiente material de la bandolera de la mano. */
    public static void onCycle(ServerPlayer p) {
        ItemStack band = p.getMainHandItem();
        if (!Bandolier.is(band)) return;
        SphereMaterial m = Bandolier.cycle(band);
        if (m == null) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.spin.empty").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        long now = p.level().getGameTime();
        p.displayClientMessage(Component.translatable("message.rites_and_paths.spin.selected",
                Component.translatable("item.rites_and_paths." + m.itemName()).append(" " + Bandolier.ready(band, m, now) + "/"
                        + Bandolier.total(band, m))).withStyle(ChatFormatting.GOLD), true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    // ------------------------------------------------------------------ lanzar

    /** Bolas que puede tener en vuelo a la vez (las minas cuentan). */
    public static int maxSpheres(ServerPlayer p) {
        int n = (int) Math.round(SpinMaster.t("base_spheres", 1));
        if (SpinMaster.totalLevel(p) >= SpinMaster.t("extra_sphere_level", 15)) n++;
        n += (int) Math.round(SpinMaster.mech(p, SpinMaster.RETURN, "extra_spheres", 0));
        n += (int) Math.round(SpinMaster.mech(p, SpinMaster.DOUBLE_HAND, "extra_spheres", 0));
        n += (int) Math.round(SpinMaster.mech(p, SpinMaster.SWIFT_RETURN, "extra_spheres", 0));
        return Math.max(1, n);
    }

    /** Lanza una bola de {@code material} de la bandolera. Devuelve null si no hay ninguna lista. */
    static SphereEntity launch(ServerPlayer p, ItemStack band, SphereMaterial material, int kind, boolean mine) {
        long now = p.level().getGameTime();
        long id = IDS.incrementAndGet();
        ItemStack copy = Bandolier.take(band, material, now, id);
        if (copy.isEmpty()) return null;
        SphereEntity e = new SphereEntity(SpinRegistry.SPHERE_ENTITY.get(), p.level());
        e.setOwner(p);
        e.throwId = id;
        e.sphere = copy;
        e.setMaterial(material);
        configure(e, p, material, copy, kind, mine);
        SpinState.State st = SpinState.of(p);
        Vec3 look = p.getLookAngle();
        // Ruta de Rebotes fijada: la bola la sigue (sin curva).
        if (st.route != null && now <= st.routeUntil && st.route.size() >= 2) {
            e.route = new ArrayList<>(st.route);
            e.routeIndex = 1;
            e.curved = false;
            st.route = null;
            look = e.route.get(1).subtract(e.route.get(0));
            look = look.lengthSqr() < 1.0E-6 ? p.getLookAngle() : look.normalize();
        }
        Vec3 start = SphereEntity.handPos(p);
        e.setPos(start.x, start.y, start.z);
        e.setDeltaMovement(look.scale(e.speed));
        // El muro de Esferas del lanzador va grabado en la bola (como el de Arco en las flechas).
        double wall = StatEngine.wall(p, Ids.SPHERES);
        DamagePipeline.markProjectile(e, StatMath.wallDamageFactor(wall), true);
        p.level().addFreshEntity(e);
        LIVE.put(id, e);
        p.swing(InteractionHand.MAIN_HAND, true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.7f,
                1.5f + p.getRandom().nextFloat() * 0.3f);
        if (st.spin >= SpinMaster.g("threshold_1", 0.33) || st.golden) {
            ServerLevel level = p.serverLevel();
            for (int i = 0; i < 10; i++) {
                double a = i * 0.7;
                Vec3 q = start.add(look.scale(i * 0.15)).add(Math.cos(a) * 0.25, Math.sin(a) * 0.25, 0);
                level.sendParticles(ParticleTypes.WAX_OFF, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
        }
        if (DebugLog.enabled()) {
            DebugLog.event(p, "giro", "lanza {} ({}{}{}): daño base {} +{}%, velocidad {}, alcance {}, rebotes {}, saltos {}, retorno {} tics",
                    material.id, kind == CURVED ? "curva" : "directa", mine ? ", mina" : "", e.route != null ? ", ruta" : "",
                    String.format("%.2f", e.baseDamage), Math.round(e.damageBonus * 100), String.format("%.2f", e.speed),
                    String.format("%.1f", e.range), e.bounces, e.hopsLeft, e.returnCooldownTicks);
        }
        return e;
    }

    /** Números del lanzamiento. */
    static void configure(SphereEntity e, ServerPlayer p, SphereMaterial m, ItemStack sphere, int kind, boolean mine) {
        SpinState.State st = SpinState.of(p);
        boolean golden = st.golden;
        int density = SpinEnchantments.level(SpinEnchantments.DENSITY, sphere);
        int lightness = SpinEnchantments.level(SpinEnchantments.LIGHTNESS, sphere);
        int magnet = SpinEnchantments.level(SpinEnchantments.MAGNET, sphere);
        int claw = SpinEnchantments.level(SpinEnchantments.CLAW, sphere);
        int elastic = SpinEnchantments.level(SpinEnchantments.ELASTICITY, sphere);
        // Daño.
        e.baseDamage = SpinMaster.t("sword_damage", 6) * SpinMaster.t("iron_fraction", 0.80) * SpinMaster.t("damage_" + m.id, 1.0);
        e.damageBonus = SpinMaster.mech(p, SpinMaster.ROTATION, "damage_bonus", 0) + SpinMaster.mech(p, SpinMaster.HEAVY_SPIN, "damage_bonus", 0)
                + SpinMaster.mech(p, SpinMaster.SWIFT_SPIN, "damage_bonus", 0) + density * SpinEnchantments.DENSITY_DAMAGE
                + lightness * SpinEnchantments.LIGHTNESS_DAMAGE + magnet * SpinEnchantments.MAGNET_DAMAGE;
        // Velocidad.
        double speed = SpinMaster.t("sphere_speed", 1.8) * (m == SphereMaterial.GOLD ? 1 + SpinMaster.t("gold_speed", 0.40) : 1);
        speed *= Math.max(0.3, 1 + SpinMaster.mech(p, SpinMaster.GUIDED_BALL, "speed_change", 0) + claw * SpinEnchantments.CLAW_SPEED
                + (golden ? SpinMaster.number(SpinMaster.GOLDEN_RECTANGLE, "golden_speed", 0.5) : 0));
        e.speed = speed;
        // Alcance (tope de +100 % entre ramas y encantamientos).
        double rangeBonus = SpinMaster.mech(p, SpinMaster.CURVE, "range_bonus", 0) + SpinMaster.mech(p, SpinMaster.TIGHT_CURVE, "range_change", 0)
                + density * SpinEnchantments.DENSITY_RANGE + lightness * SpinEnchantments.LIGHTNESS_RANGE;
        rangeBonus = Math.max(-0.8, Math.min(SpinMaster.t("range_bonus_cap", 1.0), rangeBonus));
        e.range = SpinMaster.t("sphere_range", 20) * (1 + rangeBonus);
        // Rebotes (tope de +5 entre ramas, perks y encantamientos).
        int bonus = (int) Math.round(SpinMaster.mech(p, SpinMaster.REBOUND, "extra_bounces", 0) + SpinMaster.mech(p, SpinMaster.BILLIARDS, "extra_bounces", 0)) + elastic;
        bonus = Math.min(bonus, (int) Math.round(SpinMaster.t("bounce_bonus_cap", 5)));
        int bounces = (int) Math.round(SpinMaster.t("base_bounces", 1)) + bonus
                + (m == SphereMaterial.SLIME ? (int) Math.round(SpinMaster.t("slime_bounces", 3)) : 0)
                + (st.spin >= SpinMaster.g("threshold_1", 0.33) ? (int) Math.round(SpinMaster.g("threshold_1_bounces", 1)) : 0)
                + (golden ? (int) Math.round(SpinMaster.number(SpinMaster.GOLDEN_RECTANGLE, "golden_bounces", 2)) : 0);
        e.dry = SpinMaster.has(p, SpinMaster.DRY_REBOUND);
        if (e.dry) {
            e.hopsLeft = bounces + (int) Math.round(SpinMaster.mech(p, SpinMaster.DRY_REBOUND, "extra_hops", 2));
            e.bounces = 0;
        } else {
            e.bounces = bounces;
        }
        e.hopKeep = SpinMaster.mech(p, SpinMaster.REBOUND, "hop_keep", SpinMaster.t("base_hop_keep", 0.5));
        e.chainFalloff = SpinMaster.mech(p, SpinMaster.BILLIARDS, "chain_falloff", 0);
        e.elasticity = elastic * SpinEnchantments.ELASTICITY_FALLOFF;
        // Curva.
        e.curved = kind == CURVED;
        e.curveRad = Math.toRadians(SpinMaster.t("curve_degrees", 4) * (1 + SpinMaster.mech(p, SpinMaster.CURVE, "curvature", 0))
                * SpinMaster.mech(p, SpinMaster.TIGHT_CURVE, "curve_factor", 1));
        // Retorno.
        double rc = SpinMaster.t("return_cooldown_seconds", 0.6) * 20 * (1 - SpinMaster.mech(p, SpinMaster.RETURN, "return_reduction", 0))
                * (1 + SpinMaster.mech(p, SpinMaster.DOUBLE_HAND, "return_change", 0)) * (1 + magnet * SpinEnchantments.MAGNET_RETURN)
                * (m == SphereMaterial.ENDER_PEARL ? 1 + SpinMaster.t("ender_return_change", 0.5) : 1)
                * (m == SphereMaterial.GOLD ? 1 + SpinMaster.t("gold_return_change", -0.3) : 1);
        e.returnCooldownTicks = golden ? 0 : (int) Math.max(0, Math.round(rc));
        e.swiftReturn = SpinMaster.has(p, SpinMaster.SWIFT_RETURN);
        e.guided = SpinMaster.has(p, SpinMaster.GUIDED_BALL);
        e.clawLevel = claw;
        e.frictionLevel = SpinEnchantments.level(SpinEnchantments.FRICTION, sphere);
        e.mine = mine;
        e.setGolden(golden);
        e.setSpin(st.spin);
    }

    // ------------------------------------------------------------------ antes de lanzar

    /** Trampero: clic derecho a menos de 3 bloques de una mina propia la recoge. */
    private static boolean pickUpMine(ServerPlayer p) {
        double r = SpinMaster.mech(p, SpinMaster.SPIN_MINE, "pickup_range", 3);
        for (SphereEntity e : spheresOf(p)) {
            if (e.state() == SphereEntity.MINE && e.distanceToSqr(p.getEyePosition()) <= (r + 1) * (r + 1)) {
                e.arrive(p);
                p.displayClientMessage(Component.translatable("message.rites_and_paths.spin.mine_picked").withStyle(ChatFormatting.GRAY), true);
                return true;
            }
        }
        return false;
    }

    /** Bola Guiada: clic derecho con una bola volando (aún sin redirigir) la manda hacia donde se mira. */
    private static boolean redirectGuided(ServerPlayer p) {
        if (!SpinMaster.has(p, SpinMaster.GUIDED_BALL)) return false;
        SphereEntity best = null;
        for (SphereEntity e : spheresOf(p)) {
            if (e.state() == SphereEntity.FLYING && e.guided && !e.guidedUsed && !e.mine && (best == null || e.tickCount < best.tickCount)) best = e;
        }
        if (best == null) return false;
        best.guidedUsed = true;
        best.route = null;
        best.curved = false;
        best.setDeltaMovement(p.getLookAngle().scale(Math.max(0.6, best.getDeltaMovement().length())));
        best.level().playSound(null, best.getX(), best.getY(), best.getZ(), SoundEvents.ENDER_EYE_LAUNCH, SoundSource.PLAYERS, 0.6f, 1.6f);
        if (best.level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.WAX_OFF, best.getX(), best.getY(), best.getZ(), 6, 0.1, 0.1, 0.1, 0.1);
        return true;
    }

    /** Una bola que vuelve está a punto de llegar: el clic se guarda y, al llegar, se relanza (Captura Perfecta). */
    private static boolean queueCatch(ServerPlayer p, int kind, boolean sneak) {
        for (SphereEntity e : spheresOf(p)) {
            if (e.state() == SphereEntity.RETURNING && e.catchable && e.queuedRethrow == 0) {
                e.queuedRethrow = kind + 1;
                e.queuedSneak = sneak;
                return true;
            }
        }
        return false;
    }

    /** La bola que vuelve avisa de cuántos tics le quedan: dentro de la ventana, se puede capturar. */
    static void noteArriving(ServerPlayer owner, SphereEntity e, double ticksLeft) {
        e.catchable = ticksLeft <= Math.max(1, SpinMaster.t("catch_window_seconds", 0.25) * 20);
    }

    private static void perfectBonus(ServerPlayer p, SpinState.State st) {
        SpinState.add(p, st, SpinMaster.t("catch_spin", 0.06));
        p.displayClientMessage(Component.translatable("message.rites_and_paths.spin.perfect").withStyle(ChatFormatting.GOLD), true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.6f, 1.8f);
        SpinFxSender.perfect(p);
        SpinFxSender.onomatopoeia(p.serverLevel(), p.getEyePosition().add(p.getLookAngle().scale(0.8)), SpinFxSender.WORD_PASHI);
    }

    // ------------------------------------------------------------------ vuelta

    /** La bola llega a la mano: vuelve a la bandolera (lista tras su cooldown), entrega lo que trae y Captura Perfecta. */
    static void onArrive(ServerPlayer p, SphereEntity e) {
        long now = p.level().getGameTime();
        SpinState.State st = SpinState.of(p);
        int queued = e.queuedRethrow;
        boolean bonus = queued > 0 && now >= st.nextCatch;
        long readyAt = bonus ? now : now + e.returnCooldownTicks;
        Bandolier.giveBack(p, e.throwId, readyAt);
        for (ItemStack s : e.carried) {
            if (!p.getInventory().add(s) && !s.isEmpty()) p.drop(s, false);
        }
        e.carried.clear();
        if (e.state() == SphereEntity.RETURNING) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 1.6f);
        }
        st.lastArrival = now;
        st.lastArrivalId = e.throwId;
        st.lastArrivalMaterial = e.material();
        if (queued > 0) {
            ItemStack band = p.getMainHandItem();
            if (bonus && Bandolier.is(band)) {
                st.nextCatch = now + Math.round(SpinMaster.t("catch_icd_seconds", 1.5) * 20);
                st.lastArrivalMaterial = null;
                perfectBonus(p, st);
                boolean mine = e.queuedSneak && SpinMaster.has(p, SpinMaster.SPIN_MINE);
                if (launch(p, band, e.material(), queued - 1, mine) != null) st.lastThrow = now;
            }
        }
    }

    /** Una bola desaparece sin llegar (chunk descargado, error): vuelve a la bandolera sin esperar. */
    static void onRemoved(SphereEntity e) {
        if (LIVE.get(e.throwId) == e) LIVE.remove(e.throwId);
        if (e.virtual || e.arrived || e.broken) return;
        if (e.getOwner() instanceof ServerPlayer p && !p.isRemoved()) Bandolier.giveBack(p, e.throwId, p.level().getGameTime());
    }

    /** Todas las bolas del jugador vuelven ya (muerte, desconexión, cambio de dimensión, reinicio de clase). */
    public static void recallAll(ServerPlayer p) {
        for (SphereEntity e : new ArrayList<>(LIVE.values())) {
            if (e.getOwner() != p || e.isRemoved()) continue;
            if (!e.virtual) Bandolier.giveBack(p, e.throwId, p.level().getGameTime());
            e.arrived = true;
            e.discard();
        }
    }
}
