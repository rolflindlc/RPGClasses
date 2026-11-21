package io.github.rpgclasses.spin;

import com.google.gson.JsonObject;
import io.github.rpgclasses.content.race.Races;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.Allies;
import io.github.rpgclasses.server.DamagePipeline;
import io.github.rpgclasses.server.DebugLog;
import io.github.rpgclasses.server.SessionState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rectángulo Dorado (ulti del Maestro del Giro, nivel 30): una secuencia automática con cámara de cine, inspirada en la
 * escena en que la rotación infinita derrota a D4C. Con el Giro al 100 %, al pulsarla:
 * <ol>
 *   <li><b>Escaneo</b> (0-26): el tiempo se detiene a tu alrededor (los mobs cercanos, salvo jefes, se quedan quietos y no
 *       atacan) y el rectángulo áureo se dibuja sobre el objetivo: el enemigo al que apuntas o el más cercano a la vista.</li>
 *   <li><b>Lanzamiento</b> (26-40): lanzas solo una bola dorada que vuela en espiral hasta él.</li>
 *   <li><b>Rotación infinita</b> (40-96): la bola se clava y lo retiene en el punto de impacto girando sobre sí mismo, con
 *       daño cada pocos tics. A mitad intenta escapar (desaparece y aparece a unos bloques), pero la rotación le sigue y lo
 *       arrastra de vuelta al agujero. Los jefes no se mueven ni escapan: la bola gira clavada en ellos.</li>
 *   <li><b>Golpe final</b> (96-114): un último golpe crítico lo hunde contra el suelo y la rotación se propaga a los
 *       enemigos cercanos.</li>
 * </ol>
 * Mientras dura, no te puedes mover ni actuar y no recibes daño (salvo el vacío). El cliente del que la lanza mueve la
 * cámara con el mismo guion ({@code client.spin.SpinCinematic}). Si el objetivo muere antes, se salta al golpe final.
 */
public final class GoldenRectangle {
    // Guion, en tics desde que se pulsa (lo usa también la cámara del cliente).
    public static final int SCAN_SHOT = 13, THROW = 26, IMPACT = 40, ESCAPE = 62, DRAG_BACK = 74, FINAL = 96, FINAL_HIT = 99, END = 114;
    private static final int PULSE_TICKS = 4;
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.25f), 1.1f);

    private static final class Run {
        final ServerPlayer caster;
        final ServerLevel level;
        final JsonObject params;
        final Map<Mob, Vec3> frozen = new HashMap<>();
        LivingEntity target;
        SphereEntity sphere;
        int tick;
        Vec3 casterPos, anchor, lastCenter, flightFrom, escapePos;
        boolean boss, escaped;
        double ref;
        SphereKind look = new SphereKind(SphereMaterial.IRON, SphereUpgrade.GOLD);

        Run(ServerPlayer caster, JsonObject params) {
            this.caster = caster;
            this.level = caster.serverLevel();
            this.params = params;
        }
    }

    private static final Map<UUID, Run> RUNS = SessionState.track(new HashMap<>());

    GoldenRectangle() {}

    /** ¿Está en su Rectángulo Dorado? (sin moverse ni actuar). */
    public static boolean active(Player p) {
        return p != null && RUNS.containsKey(p.getUUID());
    }

    static boolean owns(ServerPlayer owner, SphereEntity sphere) {
        Run r = RUNS.get(owner.getUUID());
        return r != null && r.sphere == sphere;
    }

    // ------------------------------------------------------------------ inicio

    /** La habilidad: con el Giro al 100 % y un enemigo a la vista, empieza la secuencia (y el cooldown). */
    static boolean start(ServerPlayer p, JsonObject params) {
        if (active(p) || p.isSpectator()) return false;
        SpinState.State st = SpinState.of(p);
        if (st.spin < 0.999f) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.spin.need_full").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        LivingEntity target = pickTarget(p, AbilityService.number(params, "target_range", 24));
        if (target == null) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.spin.no_targets").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        Run r = new Run(p, params);
        r.target = target;
        r.boss = Races.isBoss(target);
        r.casterPos = p.position();
        r.lastCenter = target.getBoundingBox().getCenter();
        double bonus = SpinMaster.mech(p, SpinMaster.HEAVY_SPIN, "damage_bonus", 0) + SpinMaster.mech(p, SpinMaster.SWIFT_SPIN, "damage_bonus", 0);
        // Rotación da ticks extra: aquí, el mismo daño extra en cada pulso.
        r.ref = SpinMaster.t("sword_damage", 6) * Math.max(0.1, 1 + bonus) * (1 + SpinMaster.mech(p, SpinMaster.ROTATION, "extra_ticks", 0));
        ItemStack band = p.getMainHandItem();
        if (Bandolier.is(band) && Bandolier.current(band) != null) r.look = Bandolier.current(band);
        // El tiempo se detiene: mobs cercanos (no jefes ni aliados) quietos durante el escaneo y el lanzamiento.
        double fr = AbilityService.number(params, "freeze_radius", 20);
        for (Mob m : r.level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(fr),
                m -> m.isAlive() && !Races.isBoss(m) && !Allies.isAlly(p, m) && m.distanceToSqr(p) <= fr * fr)) {
            r.frozen.put(m, m.position());
        }
        st.spin = 0;
        p.lookAt(EntityAnchorArgument.Anchor.EYES, r.lastCenter);
        p.addEffect(new MobEffectInstance(SpinRegistry.GOLDEN_SPIN.get(), END + 10, 0, false, false, true));
        r.level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.6f);
        r.level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 0.8f);
        p.displayClientMessage(Component.translatable("message.rites_and_paths.spin.golden").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        SpinFxSender.onomatopoeia(r.level, p.getEyePosition().add(0, 0.7, 0), SpinFxSender.WORD_GOGOGO);
        RUNS.put(p.getUUID(), r);
        send(r);
        DebugLog.event(p, "giro", "Rectángulo Dorado sobre {} (jefe {}), {} mobs quietos", DebugLog.describe(target), r.boss, r.frozen.size());
        return true;
    }

    /** El enemigo más cerca de la mira (en 45°); si no hay, el más cercano a la vista. */
    private static LivingEntity pickTarget(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        LivingEntity aimed = null, near = null;
        double bestAng = Math.toRadians(45), bestD = Double.MAX_VALUE;
        for (LivingEntity e : SphereHits.enemiesAround(p, eye, range, null)) {
            Vec3 c = e.getBoundingBox().getCenter();
            if (!SphereHits.visible(p.level(), eye, c, p)) continue;
            double ang = Math.acos(Mth.clamp(look.dot(c.subtract(eye).normalize()), -1, 1));
            if (ang < bestAng) {
                bestAng = ang;
                aimed = e;
            }
            double d = c.distanceToSqr(eye);
            if (d < bestD) {
                bestD = d;
                near = e;
            }
        }
        return aimed != null ? aimed : near;
    }

    // ------------------------------------------------------------------ guion

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || RUNS.isEmpty()) return;
        for (Run r : new ArrayList<>(RUNS.values())) DebugLog.guard(r.caster, "Rectángulo Dorado", () -> tick(r));
    }

    private static void tick(Run r) {
        ServerPlayer p = r.caster;
        if (!p.isAlive() || p.isRemoved() || p.level() != r.level) {
            finish(r);
            return;
        }
        r.tick++;
        holdCaster(r);
        if (r.tick < IMPACT) holdFrozen(r);
        LivingEntity t = r.target;
        boolean alive = t != null && t.isAlive() && !t.isRemoved() && t.level() == r.level;
        if (alive) r.lastCenter = t.getBoundingBox().getCenter();

        if (r.tick < IMPACT && !alive) {
            // Lo mataron antes del impacto: busca otro; si no hay, golpe final en su sitio.
            LivingEntity next = pickTarget(p, AbilityService.number(r.params, "target_range", 24));
            if (next != null) {
                r.target = t = next;
                r.boss = Races.isBoss(next);
                r.lastCenter = next.getBoundingBox().getCenter();
                alive = true;
                send(r);
            } else {
                jumpToFinal(r);
                return;
            }
        }

        if (r.tick < THROW) scan(r, t);
        else if (r.tick == THROW) throwSphere(r);
        else if (r.tick < IMPACT) fly(r);
        else if (r.tick == IMPACT) impact(r, alive);
        else if (r.tick < FINAL) spin(r, alive);
        else if (r.tick == FINAL) windUp(r);
        else if (r.tick == FINAL_HIT) finalHit(r, alive);
        else if (r.tick >= END) finish(r);
        if (r.sphere != null && r.tick > FINAL_HIT && !r.sphere.isRemoved()) {
            // Tras el golpe final la bola sube girando y se deshace en polvo dorado.
            Vec3 q = r.sphere.position().add(0, 0.08, 0);
            r.sphere.setPos(q.x, q.y, q.z);
            if (r.tick % 2 == 0) r.level.sendParticles(ParticleTypes.WAX_OFF, q.x, q.y, q.z, 2, 0.1, 0.1, 0.1, 0.05);
        }
    }

    /** Escaneo: espiral dorada que sube por el lanzador y rectángulo áureo alrededor del objetivo. */
    private static void scan(Run r, LivingEntity t) {
        ServerPlayer p = r.caster;
        double a = r.tick * 0.55;
        double rad = 0.9 - r.tick * 0.02;
        r.level.sendParticles(ParticleTypes.WAX_OFF, p.getX() + Math.cos(a) * rad, p.getY() + 0.1 + r.tick * 0.08, p.getZ() + Math.sin(a) * rad, 1, 0, 0, 0, 0);
        r.level.sendParticles(ParticleTypes.WAX_OFF, p.getX() - Math.cos(a) * rad, p.getY() + 0.1 + r.tick * 0.08, p.getZ() - Math.sin(a) * rad, 1, 0, 0, 0, 0);
        if (r.tick == SCAN_SHOT && t != null) {
            goldenFrame(r.level, t, p);
            r.level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 1.0f, 0.9f);
            r.level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 1.2f);
        }
        if (r.tick > SCAN_SHOT && r.tick % 4 == 0 && t != null) goldenFrame(r.level, t, p);
    }

    /** Rectángulo de proporción áurea (de cara al lanzador) alrededor del objetivo. */
    private static void goldenFrame(ServerLevel level, LivingEntity t, ServerPlayer from) {
        Vec3 c = t.getBoundingBox().getCenter();
        Vec3 to = c.subtract(from.getEyePosition()).multiply(1, 0, 1);
        if (to.lengthSqr() < 1.0E-4) to = new Vec3(1, 0, 0);
        Vec3 side = to.normalize().cross(new Vec3(0, 1, 0)).normalize();
        double h = Math.max(1.2, t.getBbHeight() * 1.25);
        double w = h * 1.618;
        for (int i = 0; i <= 16; i++) {
            double f = i / 16.0;
            for (Vec3 q : new Vec3[] {
                    c.add(side.scale((f - 0.5) * w)).add(0, h / 2, 0), c.add(side.scale((f - 0.5) * w)).add(0, -h / 2, 0),
                    c.add(side.scale(-w / 2)).add(0, (f - 0.5) * h, 0), c.add(side.scale(w / 2)).add(0, (f - 0.5) * h, 0)}) {
                level.sendParticles(GOLD, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
        }
    }

    private static void throwSphere(Run r) {
        ServerPlayer p = r.caster;
        SphereEntity e = new SphereEntity(SpinRegistry.SPHERE_ENTITY.get(), r.level);
        e.setOwner(p);
        e.virtual = true;
        e.scripted = true;
        e.setKind(r.look);
        e.setGolden(true);
        e.setSpin(1f);
        Vec3 hand = SphereEntity.handPos(p);
        e.setPos(hand.x, hand.y, hand.z);
        e.setDeltaMovement(r.lastCenter.subtract(hand).normalize().scale(1.5));
        DamagePipeline.markProjectile(e, 1.0, true);
        r.level.addFreshEntity(e);
        r.sphere = e;
        r.flightFrom = hand;
        p.swing(InteractionHand.MAIN_HAND, true);
        r.level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0f, 0.8f);
        r.level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 0.7f, 1.3f);
        send(r);
    }

    /** Vuelo en espiral cerrándose hasta el objetivo (llega justo en el tic del impacto). */
    private static void fly(Run r) {
        if (r.sphere == null) return;
        double f = (r.tick - THROW) / (double) (IMPACT - THROW);
        double ease = f * f * (3 - 2 * f);
        Vec3 dir = r.lastCenter.subtract(r.flightFrom);
        Vec3 base = r.flightFrom.add(dir.scale(ease));
        Vec3 n = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 side = n.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(n).normalize();
        double ang = f * Math.PI * 5;
        double rad = 0.6 * (1 - f);
        Vec3 q = base.add(side.scale(Math.cos(ang) * rad)).add(up.scale(Math.sin(ang) * rad));
        r.sphere.setDeltaMovement(q.subtract(r.sphere.position()));
        r.sphere.setPos(q.x, q.y, q.z);
        r.level.sendParticles(GOLD, q.x, q.y, q.z, 2, 0.05, 0.05, 0.05, 0);
    }

    private static void impact(Run r, boolean alive) {
        if (!alive) {
            jumpToFinal(r);
            return;
        }
        LivingEntity t = r.target;
        r.anchor = t.getBoundingBox().getCenter();
        r.frozen.clear();
        if (r.sphere != null) {
            r.sphere.setState(SphereEntity.SPINNING);
            // Para la armadura: cada pulso cuenta como parte de un golpe entero (SphereHits.armorFactor).
            r.sphere.pulseHits = (FINAL - IMPACT) / PULSE_TICKS + 1;
            r.sphere.setDeltaMovement(Vec3.ZERO);
            r.sphere.setPos(r.anchor.x, r.anchor.y, r.anchor.z);
        }
        r.level.playSound(null, r.anchor.x, r.anchor.y, r.anchor.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 0.6f);
        r.level.playSound(null, r.anchor.x, r.anchor.y, r.anchor.z, SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
        SpinFxSender.onomatopoeia(r.level, r.anchor.add(0, 0.6, 0), SpinFxSender.WORD_GYARU);
        SpinFxSender.shake(r.caster, 0.6f);
        if (t instanceof ServerPlayer hit) SpinFxSender.cinematicHit(hit, 16);
        pulse(r, t);
    }

    /** Rotación infinita: lo retiene girando en el agujero, con daño cada pocos tics (y el intento de escape). */
    private static void spin(Run r, boolean alive) {
        if (!alive) {
            jumpToFinal(r);
            return;
        }
        LivingEntity t = r.target;
        if (r.boss) {
            r.anchor = t.getBoundingBox().getCenter();
        } else {
            boolean nod = t instanceof Mob;
            if (nod && r.tick == ESCAPE) escape(r, t);
            if (r.escaped && r.tick >= ESCAPE && r.tick < DRAG_BACK) {
                double f = (r.tick - ESCAPE) / (double) (DRAG_BACK - ESCAPE);
                double ease = f * f * f;
                holdAt(t, r.escapePos.lerp(r.anchor, ease));
                SphereHits.line(r.level, t.getBoundingBox().getCenter(), r.anchor, ParticleTypes.WAX_OFF);
            } else {
                holdAt(t, r.anchor);
            }
            if (r.tick == DRAG_BACK && r.escaped) {
                r.level.playSound(null, r.anchor.x, r.anchor.y, r.anchor.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 0.7f);
                SpinFxSender.onomatopoeia(r.level, r.anchor.add(0, 0.8, 0), SpinFxSender.WORD_GOGOGO);
                SpinFxSender.shake(r.caster, 0.4f);
            }
            if (!(t instanceof Player)) {
                // Gira sobre sí mismo, cada vez más rápido.
                float step = 18f + 30f * (r.tick - IMPACT) / (float) (FINAL - IMPACT);
                t.setYRot(t.getYRot() + step);
                t.yBodyRot = t.getYRot();
                t.setYHeadRot(t.getYRot());
            }
        }
        if (r.sphere != null) r.sphere.setPos(r.anchor.x, r.anchor.y, r.anchor.z);
        // Hélice dorada alrededor del agujero.
        double a = r.tick * 0.7;
        double h = ((r.tick - IMPACT) % 20) / 20.0 * 2.2 - 1.1;
        double rad = Math.max(0.6, t.getBbWidth() * 0.9);
        for (int k = 0; k < 2; k++) {
            double b = a + k * Math.PI;
            r.level.sendParticles(ParticleTypes.WAX_OFF, r.anchor.x + Math.cos(b) * rad, r.anchor.y + h, r.anchor.z + Math.sin(b) * rad, 1, 0, 0, 0, 0);
        }
        if ((r.tick - IMPACT) % PULSE_TICKS == 0) pulse(r, t);
        if ((r.tick - IMPACT) % 10 == 0) {
            r.level.playSound(null, r.anchor.x, r.anchor.y, r.anchor.z, SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.5f,
                    0.8f + 0.8f * (r.tick - IMPACT) / (float) (FINAL - IMPACT));
        }
    }

    /** El objetivo «escapa» a unos bloques (destello de portal); la rotación lo devolverá al agujero. */
    private static void escape(Run r, LivingEntity t) {
        double start = r.level.random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < 8; i++) {
            double ang = start + i * Math.PI / 4;
            Vec3 off = new Vec3(Math.cos(ang) * 3.5, 0, Math.sin(ang) * 3.5);
            AABB box = t.getBoundingBox().move(off);
            Vec3 c = box.getCenter();
            if (!r.level.noCollision(t, box) || !SphereHits.visible(r.level, r.anchor, c, t)) continue;
            Vec3 from = t.getBoundingBox().getCenter();
            r.escapePos = c;
            r.escaped = true;
            holdAt(t, c);
            r.level.sendParticles(ParticleTypes.PORTAL, from.x, from.y, from.z, 40, 0.3, 0.6, 0.3, 0.4);
            r.level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 30, 0.3, 0.6, 0.3, 0.1);
            r.level.playSound(null, c.x, c.y, c.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 0.5f);
            return;
        }
    }

    private static void windUp(Run r) {
        Vec3 c = r.anchor != null ? r.anchor : r.lastCenter;
        double y = c.y - (r.target != null ? r.target.getBbHeight() / 2 : 0.9) + 0.1;
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI * 2 / 32;
            r.level.sendParticles(ParticleTypes.WAX_OFF, c.x + Math.cos(a) * 2.2, y, c.z + Math.sin(a) * 2.2, 1, 0, 0, 0, 0);
        }
        r.level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
    }

    /** Golpe final (crítico): lo hunde contra el suelo y la rotación salta a los enemigos cercanos. */
    private static void finalHit(Run r, boolean alive) {
        Vec3 c = r.anchor != null ? r.anchor : r.lastCenter;
        ServerPlayer p = r.caster;
        LivingEntity t = r.target;
        if (r.sphere != null) r.sphere.pulseHits = 1;
        if (alive && r.sphere != null) {
            r.sphere.critDecided = true;
            t.invulnerableTime = 0;
            t.hurt(SpinMaster.sphereSource(r.level, r.sphere, p), (float) (AbilityService.number(r.params, "final_damage", 3.0) * r.ref));
            r.sphere.critDecided = null;
            if (!r.boss) {
                t.setDeltaMovement(0, -1.4, 0);
                t.hurtMarked = true;
            }
            if (t instanceof ServerPlayer hit) {
                SpinFxSender.cinematicHit(hit, 20);
                SpinFxSender.shake(hit, 0.8f);
            }
        }
        // Se hunde: polvo del suelo en espiral.
        BlockPos below = BlockPos.containing(c.x, c.y - (t != null ? t.getBbHeight() / 2 : 0.9) - 0.2, c.z);
        BlockState ground = r.level.getBlockState(below);
        if (!ground.isAir()) {
            for (int i = 0; i < 40; i++) {
                double a = i * 0.5;
                double rad = 0.3 + i * 0.05;
                r.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), c.x + Math.cos(a) * rad, below.getY() + 1.05,
                        c.z + Math.sin(a) * rad, 2, 0.05, 0.1, 0.05, 0.15);
            }
        }
        r.level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 3, 0.4, 0.3, 0.4, 0);
        r.level.sendParticles(ParticleTypes.WAX_OFF, c.x, c.y, c.z, 40, 0.6, 0.6, 0.6, 0.6);
        r.level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 0.7f);
        r.level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0f, 0.5f);
        SpinFxSender.onomatopoeia(r.level, c.add(0, 1.0, 0), SpinFxSender.WORD_DOGOON);
        SpinFxSender.shake(p, 1.0f);
        // La rotación se propaga.
        int n = (int) Math.round(AbilityService.number(r.params, "spread_targets", 6));
        double radius = AbilityService.number(r.params, "spread_radius", 12);
        float dmg = (float) (AbilityService.number(r.params, "spread_damage", 1.5) * r.ref);
        List<LivingEntity> near = SphereHits.enemiesAround(p, c, radius, t);
        near.sort((x, y) -> Double.compare(x.distanceToSqr(c), y.distanceToSqr(c)));
        int done = 0;
        for (LivingEntity e : near) {
            if (done >= n) break;
            Vec3 ec = e.getBoundingBox().getCenter();
            if (!SphereHits.visible(r.level, c, ec, p)) continue;
            if (r.sphere != null) {
                r.sphere.critDecided = false;
                e.invulnerableTime = 0;
                e.hurt(SpinMaster.sphereSource(r.level, r.sphere, p), dmg);
                r.sphere.critDecided = null;
            }
            SphereHits.line(r.level, c, ec, ParticleTypes.WAX_OFF);
            r.level.sendParticles(ParticleTypes.CRIT, ec.x, ec.y, ec.z, 8, 0.2, 0.2, 0.2, 0.3);
            done++;
        }
        if (r.sphere != null) {
            r.sphere.setState(SphereEntity.FLYING);
            r.sphere.setDeltaMovement(new Vec3(0, 0.08, 0));
        }
        DebugLog.event(p, "giro", "Rectángulo Dorado: golpe final (vivo {}), rotación a {} enemigos", alive, done);
    }

    private static void pulse(Run r, LivingEntity t) {
        if (r.sphere == null || t == null || !t.isAlive()) return;
        r.sphere.critDecided = false;
        t.invulnerableTime = 0;
        t.hurt(SpinMaster.sphereSource(r.level, r.sphere, r.caster), (float) (AbilityService.number(r.params, "pulse_damage", 0.4) * r.ref));
        r.sphere.critDecided = null;
        Vec3 c = r.sphere.position();
        r.level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 4, 0.15, 0.15, 0.15, 0.3);
        SpinFxSender.shake(r.caster, 0.12f);
    }

    private static void jumpToFinal(Run r) {
        if (r.anchor == null) r.anchor = r.lastCenter;
        if (r.sphere == null) {
            r.tick = THROW;
            throwSphere(r);
        }
        if (r.sphere != null) {
            r.sphere.setState(SphereEntity.SPINNING);
            r.sphere.setPos(r.anchor.x, r.anchor.y, r.anchor.z);
        }
        r.frozen.clear();
        r.tick = FINAL;
        windUp(r);
        send(r);
    }

    // ------------------------------------------------------------------ retener

    private static void holdCaster(Run r) {
        ServerPlayer p = r.caster;
        if (p.isPassenger()) return;
        Vec3 d = p.position().subtract(r.casterPos);
        if (d.x * d.x + d.z * d.z > 0.36) {
            p.connection.teleport(r.casterPos.x, p.getY(), r.casterPos.z, p.getYRot(), p.getXRot());
        }
    }

    private static void holdFrozen(Run r) {
        for (Map.Entry<Mob, Vec3> e : r.frozen.entrySet()) {
            Mob m = e.getKey();
            if (!m.isAlive() || m.level() != r.level) continue;
            Vec3 at = e.getValue();
            m.setPos(at.x, at.y, at.z);
            m.setDeltaMovement(Vec3.ZERO);
            m.getNavigation().stop();
            if (m instanceof Creeper c) c.setSwellDir(-1);
        }
    }

    /** Pone el centro de {@code t} en {@code center} (los jugadores, con un teletransporte solo si se han ido). */
    private static void holdAt(LivingEntity t, Vec3 center) {
        Vec3 pos = center.subtract(0, t.getBbHeight() / 2, 0);
        if (t instanceof ServerPlayer sp) {
            if (sp.position().distanceToSqr(pos) > 0.09) sp.connection.teleport(pos.x, pos.y, pos.z, sp.getYRot(), sp.getXRot());
            return;
        }
        t.setPos(pos.x, pos.y, pos.z);
        t.setDeltaMovement(Vec3.ZERO);
        if (t instanceof Mob m) m.getNavigation().stop();
    }

    // ------------------------------------------------------------------ fin

    private static void finish(Run r) {
        RUNS.remove(r.caster.getUUID());
        if (r.sphere != null && !r.sphere.isRemoved()) {
            Vec3 q = r.sphere.position();
            r.level.sendParticles(ParticleTypes.WAX_OFF, q.x, q.y, q.z, 12, 0.2, 0.2, 0.2, 0.2);
            r.sphere.discard();
        }
        r.caster.removeEffect(SpinRegistry.GOLDEN_SPIN.get());
        Net.toPlayer(r.caster, new Packets.SpinCinematic(-1, -1, -1));
    }

    /** Corta el Rectángulo Dorado en curso (muerte, desconexión, cambio de dimensión, reinicio). */
    public static void abort(ServerPlayer p) {
        Run r = RUNS.get(p.getUUID());
        if (r != null) finish(r);
    }

    private static void send(Run r) {
        Net.toPlayer(r.caster, new Packets.SpinCinematic(r.target == null ? -1 : r.target.getId(),
                r.sphere == null ? -1 : r.sphere.getId(), r.tick));
    }

    // ------------------------------------------------------------------ eventos

    /** Mientras dura, el lanzador no recibe daño (salvo el vacío) y los mobs detenidos no hacen daño. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(LivingAttackEvent event) {
        if (RUNS.isEmpty()) return;
        if (event.getEntity() instanceof ServerPlayer sp && RUNS.containsKey(sp.getUUID())
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
            return;
        }
        Entity src = event.getSource().getEntity();
        if (src instanceof Mob m) {
            for (Run r : RUNS.values()) {
                if (r.tick < IMPACT && r.frozen.containsKey(m)) {
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPlayerAttack(AttackEntityEvent event) {
        if (active(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (active(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (active(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (active(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (active(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer)) abort(p);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) abort(p);
    }

    @SubscribeEvent
    public void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) abort(p);
    }
}
