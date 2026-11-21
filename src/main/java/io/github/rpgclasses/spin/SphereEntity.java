package io.github.rpgclasses.spin;

import io.github.rpgclasses.content.race.Races;
import io.github.rpgclasses.server.Allies;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Una bola en vuelo (adenda del Maestro del Giro, secciones 3, 4, 7 y 8). Estados:
 * <ul>
 *   <li><b>Vuela:</b> guiada hacia donde apunta el lanzador (clic izquierdo), curvándose hacia donde mira (clic derecho,
 *       que se puede cargar) o recorriendo la Ruta de Rebotes (de enemigo en enemigo). Mientras le queden rebotes, cada
 *       rebote en un bloque la manda hacia donde mira la cámara del lanzador (si eso la mete en la pared, rebota
 *       normal); al llegar a su alcance vuelve.</li>
 *   <li><b>Gira:</b> clavada en un enemigo unos 2 s (el doble con la carga completa): daño en pulsos que se saltan la
 *       invulnerabilidad, con una micro-pausa al principio. Con Garra sigue clavada un rato más. Después, si le quedan
 *       rebotes, sale hacia donde mira la cámara del lanzador (él apunta al siguiente enemigo); si no, vuelve.</li>
 *   <li><b>Vuelve:</b> atraviesa los bloques hacia la mano del lanzador (la de perla de Ender lo lleva a él al impacto).</li>
 *   <li><b>Mina:</b> clavada en un bloque (Trampero de Giro), explota cuando pasa un enemigo cerca.</li>
 * </ul>
 * La bola del Rectángulo Dorado es «de guion» ({@link #scripted}): la mueve {@link GoldenRectangle} y aquí no hace nada.
 * La bola nunca sale de la bandolera ({@link Bandolier}): esta entidad solo lleva una copia para leer sus encantamientos.
 * No se guarda con el mundo; si desaparece, la marca de vuelo de la bandolera se limpia sola.
 */
public class SphereEntity extends Projectile {
    public static final byte FLYING = 0, SPINNING = 1, RETURNING = 2, MINE = 3;
    private static final int MAX_FLIGHT_TICKS = 200, MAX_RETURN_TICKS = 160;

    private static final EntityDataAccessor<Byte> DATA_MATERIAL = SynchedEntityData.defineId(SphereEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(SphereEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_SPIN = SynchedEntityData.defineId(SphereEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_GOLDEN = SynchedEntityData.defineId(SphereEntity.class, EntityDataSerializers.BOOLEAN);

    // --- Lanzamiento (lo rellena SpinThrow) ---
    long throwId;
    /** Bola sin objeto detrás (la del Rectángulo Dorado): no toca la bandolera ni cuenta para fallar. */
    boolean virtual;
    /** La mueve el guion del Rectángulo Dorado: su tick no hace nada. */
    boolean scripted;
    /** Clic izquierdo: va hacia donde apunta el lanzador (hasta el primer rebote o salto). */
    boolean aimed;
    boolean curved, mine, golden, dry, guided, guidedUsed, swiftReturn;
    ItemStack sphere = ItemStack.EMPTY;
    double baseDamage, damageBonus, speed, range, hopKeep, chainFalloff, elasticity, curveRad, aimRad;
    int bounces, hopsLeft, clawLevel, frictionLevel, returnCooldownTicks;
    /** Carga del clic derecho (0-1) y lo que multiplica: tiempo clavada y daño total del impacto. */
    double charge, spinTimeFactor = 1, chargeDamage = 1;
    /**
     * Ruta de Rebotes: enemigos (LivingEntity) y puntos de rebote (Vec3) por orden. Los enemigos intermedios reciben un
     * impacto de rebote al pasar; en el último se clava.
     */
    List<Object> trick;
    int trickIndex, trickHits;
    double trickHitFactor, trickBonus;

    // --- Vuelo ---
    double traveled;
    int blockBounces, hops, flightTicks, returnTicks, age;
    final Set<Integer> hitIds = new HashSet<>();
    boolean hitEnemy;
    Vec3 lastImpact;
    int blocksBroken;
    final List<ItemStack> carried = new ArrayList<>();
    /** Palancas y botones ya accionados en este lanzamiento (bola de redstone). */
    final Set<BlockPos> toggled = new HashSet<>();
    /** La bola se ha roto (sin durabilidad): estalla al terminar el giro en vez de volver. */
    boolean broken;
    /** Ya ha vuelto a la mano (para no devolverla dos veces al quitarse). */
    boolean arrived;
    /** Captura Perfecta pedida mientras volvía: 0 = no, 1 = directo, 2 = curvo. */
    int queuedRethrow;
    boolean queuedSneak;
    /** Le queda menos que la ventana de Captura Perfecta para llegar. */
    boolean catchable;

    // --- Giro sobre un objetivo ---
    LivingEntity stuck;
    Vec3 stuckOffset = Vec3.ZERO;
    int spinTick, spinTicks = 40, lingerTicks, pulseTicks = 5, hitStopTicks = 3;
    /** Daño de cada pulso del giro. */
    float damagePerTick;
    /** Crítico decidido para todos los tics del impacto actual (lo lee DamagePipeline). */
    Boolean critDecided;
    /** Golpe del impacto actual con sus multiplicadores, para el empuje del final del giro. */
    double impactKnockback;
    /** Potencia del efecto del material en el impacto actual (×1,5 con Giro II). */
    double materialFactor = 1.0;

    // --- Mina ---
    long mineUntil;

    public SphereEntity(EntityType<? extends SphereEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_MATERIAL, (byte) 0);
        entityData.define(DATA_STATE, FLYING);
        entityData.define(DATA_SPIN, 0f);
        entityData.define(DATA_GOLDEN, false);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    /** El agua no la desvía (la trayectoria la decide el código, también la Ruta de Rebotes). */
    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    public SphereMaterial material() {
        return SphereMaterial.byIndex(entityData.get(DATA_MATERIAL));
    }

    void setMaterial(SphereMaterial m) {
        entityData.set(DATA_MATERIAL, (byte) m.ordinal());
    }

    public byte state() {
        return entityData.get(DATA_STATE);
    }

    void setState(byte s) {
        entityData.set(DATA_STATE, s);
    }

    /** Giro del lanzador (0-1) cuando se lanzó o impactó: el anillo gira más rápido cuanto más alto. */
    public float spin() {
        return entityData.get(DATA_SPIN);
    }

    void setSpin(float s) {
        entityData.set(DATA_SPIN, Mth.clamp(s, 0f, 1f));
    }

    public boolean golden() {
        return entityData.get(DATA_GOLDEN);
    }

    void setGolden(boolean g) {
        golden = g;
        entityData.set(DATA_GOLDEN, g);
    }

    public Boolean critDecided() {
        return critDecided;
    }

    public LivingEntity stuckTarget() {
        return state() == SPINNING ? stuck : null;
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        age++;
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (!(getOwner() instanceof ServerPlayer owner) || !owner.isAlive() || owner.isRemoved() || owner.level() != level()) {
            discard();
            return;
        }
        if (scripted) {
            // La mueve GoldenRectangle; si el guion se perdió, desaparece sola.
            if (age > 400 || !GoldenRectangle.owns(owner, this)) discard();
            return;
        }
        switch (state()) {
            case FLYING -> tickFlying(owner);
            case SPINNING -> tickSpinning(owner);
            case RETURNING -> tickReturning(owner);
            case MINE -> tickMine(owner);
            default -> discard();
        }
    }

    // ------------------------------------------------------------------ vuelo

    private void tickFlying(ServerPlayer owner) {
        if (++flightTicks > MAX_FLIGHT_TICKS) {
            startReturn(owner);
            return;
        }
        if (trick != null) {
            tickTrick(owner);
            return;
        }
        Vec3 vel = getDeltaMovement();
        if (vel.lengthSqr() < 1.0E-6) vel = owner.getLookAngle().scale(Math.max(0.5, speed));
        Vec3 start = position();
        if (aimed && aimRad > 0) {
            // Clic izquierdo: se guía hacia el punto al que apunta la cámara.
            vel = steer(vel, aimPoint(owner, false).subtract(start), aimRad);
        } else if (curved && curveRad > 0) {
            vel = steer(vel, owner.getLookAngle(), curveRad);
        }
        Vec3 end = start.add(vel);

        BlockHitResult block = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean wall = block.getType() != HitResult.Type.MISS;
        Vec3 segEnd = wall ? block.getLocation() : end;

        // Enemigos en el tramo (el más cercano).
        LivingEntity target = findTarget(owner, start, segEnd);
        if (target != null) {
            Vec3 at = hitPoint(target, start, segEnd);
            setPos(at.x, at.y, at.z);
            traveled += start.distanceTo(at);
            SphereHits.startSpin(this, owner, target, at);
            return;
        }
        if (material() == SphereMaterial.REDSTONE) SphereHits.pressComponents(this, owner, start, segEnd);

        if (wall) {
            onBlockImpact(owner, block, vel, start);
            return;
        }
        traveled += start.distanceTo(end);
        setPos(end.x, end.y, end.z);
        setDeltaMovement(vel);
        checkInsideBlocks();
        if (traveled >= range) startReturn(owner);
    }

    /**
     * Punto al que apunta el lanzador: el primer bloque en su línea de vista (o 64 bloques por delante). Con
     * {@code enemies}, si antes hay un enemigo bajo la mira que esta bola aún no ha golpeado, su centro (para los rebotes;
     * la guía de cada tic solo mira bloques, que es más barato).
     */
    private Vec3 aimPoint(ServerPlayer owner, boolean enemies) {
        Vec3 eye = owner.getEyePosition();
        Vec3 far = eye.add(owner.getLookAngle().scale(64));
        BlockHitResult hit = level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 end = hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
        if (!enemies) return end;
        LivingEntity best = null;
        double bestD = eye.distanceToSqr(end);
        for (Entity e : level().getEntities(owner, new AABB(eye, end).inflate(1.0), x -> x instanceof LivingEntity && x.isAlive()
                && !(x instanceof ArmorStand) && !hitIds.contains(x.getId()))) {
            LivingEntity le = (LivingEntity) e;
            if (!Allies.isEnemy(owner, le)) continue;
            Optional<Vec3> c = le.getBoundingBox().inflate(0.3).clip(eye, end);
            if (c.isEmpty()) continue;
            double d = eye.distanceToSqr(c.get());
            if (d < bestD) {
                bestD = d;
                best = le;
            }
        }
        return best != null ? best.getBoundingBox().getCenter() : end;
    }

    // ------------------------------------------------------------------ Ruta de Rebotes

    /**
     * Recorre la ruta: vuela recta (sin chocar con bloques: la ruta se trazó con línea de vista) hacia el siguiente punto.
     * Al pasar por un enemigo intermedio le da un impacto de rebote; en el último enemigo se clava como siempre.
     */
    private void tickTrick(ServerPlayer owner) {
        while (trickIndex < trick.size()) {
            Object wp = trick.get(trickIndex);
            if (!(wp instanceof LivingEntity le)) break;
            boolean gone = !le.isAlive() || le.isRemoved() || le.level() != level();
            Vec3 c = le.getBoundingBox().getCenter();
            if (!gone && (position().distanceToSqr(c) < 4 || SphereHits.visible(level(), position(), c, this))) break;
            trickIndex++; // muerto o tras una pared: se lo salta
        }
        if (trickIndex >= trick.size()) {
            trick = null;
            startReturn(owner);
            return;
        }
        Object wp = trick.get(trickIndex);
        Vec3 start = position();
        Vec3 goal = wp instanceof LivingEntity le ? le.getBoundingBox().getCenter() : (Vec3) wp;
        Vec3 to = goal.subtract(start);
        double d = to.length();
        Vec3 end = d <= speed ? goal : start.add(to.scale(speed / d));
        if (wp instanceof LivingEntity le) {
            AABB box = le.getBoundingBox().inflate(0.3);
            Optional<Vec3> clip = box.contains(start) ? Optional.of(start) : box.clip(start, end);
            if (clip.isPresent() || d <= speed) {
                Vec3 at = clip.orElse(goal);
                traveled += start.distanceTo(at);
                setPos(at.x, at.y, at.z);
                if (lastTrickEnemy()) {
                    // Último enemigo de la ruta: se clava.
                    trick = null;
                    setDeltaMovement(to.lengthSqr() < 1.0E-6 ? getDeltaMovement() : to.normalize().scale(speed));
                    SphereHits.startSpin(this, owner, le, at);
                    return;
                }
                SphereHits.ricochet(this, owner, le, at);
                trickIndex++;
                if (broken) {
                    finishSpin(owner, true);
                    return;
                }
                aimAtNext();
                return;
            }
        }
        if (to.lengthSqr() > 1.0E-6) setDeltaMovement(to.normalize().scale(speed));
        traveled += start.distanceTo(end);
        setPos(end.x, end.y, end.z);
        if (!(wp instanceof LivingEntity) && d <= speed) {
            // Punto de rebote (banda): cambia de dirección hacia el siguiente.
            trickIndex++;
            bounceFx(goal);
            aimAtNext();
        }
    }

    private boolean lastTrickEnemy() {
        for (int i = trickIndex + 1; i < trick.size(); i++) if (trick.get(i) instanceof LivingEntity) return false;
        return true;
    }

    private void aimAtNext() {
        if (trick == null || trickIndex >= trick.size()) return;
        Object next = trick.get(trickIndex);
        Vec3 goal = next instanceof LivingEntity le ? le.getBoundingBox().getCenter() : (Vec3) next;
        Vec3 dir = goal.subtract(position());
        if (dir.lengthSqr() > 1.0E-6) setDeltaMovement(dir.normalize().scale(speed));
    }

    private void onBlockImpact(ServerPlayer owner, BlockHitResult hit, Vec3 vel, Vec3 start) {
        Vec3 at = hit.getLocation();
        traveled += start.distanceTo(at);
        lastImpact = at;
        BlockPos pos = hit.getBlockPos();
        BlockState state = level().getBlockState(pos);
        // Como una flecha: dianas, campanas...
        state.onProjectileHit(level(), state, hit, this);
        Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        if (mine) {
            SphereHits.becomeMine(this, owner, at.add(n.scale(0.12)));
            return;
        }
        boolean broke = SphereHits.blockEffects(this, owner, hit);
        if (broke) {
            // La estalactita rompe el bloque y sigue su camino.
            setPos(at.x, at.y, at.z);
            setDeltaMovement(vel);
            return;
        }
        if (bounces > 0 && !dry) {
            bounces--;
            blockBounces++;
            double keep = material() == SphereMaterial.SLIME ? SpinMaster.t("slime_speed_keep", 0.98) : SpinMaster.t("bounce_speed_keep", 0.85);
            double spd = Math.max(0.6, vel.length() * keep);
            // Sale hacia donde mira la cámara del lanzador; si eso la mete en la pared (mira a la propia pared), rebota normal.
            Vec3 aim = aimPoint(owner, true).subtract(at);
            Vec3 out;
            if (aim.lengthSqr() > 0.25 && aim.normalize().dot(n) > 0.15) {
                out = aim.normalize().scale(spd);
            } else {
                Vec3 reflected = vel.subtract(n.scale(2 * vel.dot(n)));
                out = reflected.lengthSqr() < 1.0E-6 ? n.scale(spd) : reflected.normalize().scale(spd);
            }
            curved = false;
            Vec3 p = at.add(n.scale(0.06));
            setPos(p.x, p.y, p.z);
            setDeltaMovement(out);
            bounceFx(at);
            return;
        }
        setPos(at.x, at.y, at.z);
        startReturn(owner);
    }

    private void bounceFx(Vec3 at) {
        level().playSound(null, at.x, at.y, at.z, material() == SphereMaterial.SLIME ? SoundEvents.SLIME_JUMP_SMALL : SoundEvents.CHAIN_HIT,
                SoundSource.PLAYERS, 0.5f, 1.3f + random.nextFloat() * 0.3f);
        if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.05, 0.05, 0.05, 0.15);
    }

    /** El enemigo más cercano que cruza el tramo (sin aliados, sin el lanzador y sin repetir objetivo en el mismo lanzamiento). */
    private LivingEntity findTarget(ServerPlayer owner, Vec3 a, Vec3 b) {
        AABB box = new AABB(a, b).inflate(0.8);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : level().getEntities(this, box, x -> x instanceof LivingEntity && x.isAlive() && x != owner)) {
            LivingEntity le = (LivingEntity) e;
            if (le instanceof ArmorStand || hitIds.contains(le.getId()) || !Allies.isEnemy(owner, le)) continue;
            AABB eb = le.getBoundingBox().inflate(0.3);
            double d;
            if (eb.contains(a)) {
                d = 0;
            } else {
                Optional<Vec3> hit = eb.clip(a, b);
                if (hit.isEmpty()) continue;
                d = a.distanceToSqr(hit.get());
            }
            if (d < bestD) {
                bestD = d;
                best = le;
            }
        }
        return best;
    }

    private static Vec3 hitPoint(LivingEntity target, Vec3 a, Vec3 b) {
        AABB eb = target.getBoundingBox().inflate(0.3);
        if (eb.contains(a)) return a;
        return eb.clip(a, b).orElse(target.getBoundingBox().getCenter());
    }

    // ------------------------------------------------------------------ giro sobre el objetivo

    private void tickSpinning(ServerPlayer owner) {
        LivingEntity t = stuck;
        if (t == null || !t.isAlive() || t.isRemoved() || t.level() != level()) {
            finishSpin(owner, true);
            return;
        }
        Vec3 p = t.position().add(stuckOffset);
        setPos(p.x, p.y, p.z);
        spinTick++;
        if (spinTick < spinTicks) {
            // Micro-pausa: al principio el objetivo se queda quieto (los jefes no); luego la bola sigue girando clavada.
            if (spinTick < hitStopTicks && !Races.isBoss(t)) {
                Vec3 v = t.getDeltaMovement();
                t.setDeltaMovement(0, Math.min(0, v.y), 0);
                t.hurtMarked = true;
            }
            if (spinTick % Math.max(1, pulseTicks) == 0) SphereHits.dealTick(this, owner, t);
            return;
        }
        if (lingerTicks > 0) {
            lingerTicks--;
            if (level() instanceof ServerLevel sl && lingerTicks % 4 == 0) {
                sl.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.05);
            }
            return;
        }
        finishSpin(owner, false);
    }

    /** Fin del giro: empuje del material y, si quedan rebotes, salta a otro enemigo; si no, vuelve. */
    void finishSpin(ServerPlayer owner, boolean targetGone) {
        LivingEntity t = stuck;
        if (t != null && !targetGone && impactKnockback > 0 && !Races.isBoss(t)) {
            Vec3 away = t.position().subtract(owner.position()).multiply(1, 0, 1);
            if (away.lengthSqr() > 1.0E-4) {
                t.knockback(impactKnockback, -away.x, -away.z);
                t.hurtMarked = true;
            }
        }
        stuck = null;
        critDecided = null;
        if (broken) {
            // Sin durabilidad: se rompe en el sitio.
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM,
                        sphere.isEmpty() ? new ItemStack(SpinRegistry.sphere(material())) : sphere), getX(), getY(), getZ(), 12, 0.1, 0.1, 0.1, 0.1);
            }
            arrived = true;
            discard();
            return;
        }
        if (canHop()) {
            // Rebote en el enemigo: sale hacia donde mira la cámara del lanzador (él apunta al siguiente).
            Vec3 dir = aimPoint(owner, true).subtract(position());
            if (dir.lengthSqr() > 1.0) {
                useHop();
                hops++;
                setDeltaMovement(dir.normalize().scale(Math.max(speed, 1.2)));
                curved = false;
                // Cada rebote en un enemigo tiene al menos su propio tramo de alcance.
                traveled = Math.min(traveled, Math.max(0, range - SpinMaster.t("hop_range", 8)));
                setState(FLYING);
                bounceFx(position());
                return;
            }
        }
        startReturn(owner);
    }

    boolean canHop() {
        return dry ? hopsLeft > 0 : bounces > 0;
    }

    private void useHop() {
        if (dry) hopsLeft--;
        else bounces--;
    }

    // ------------------------------------------------------------------ vuelta

    /** Empieza a volver. Si no impactó a ningún enemigo, es un fallo (pierde Giro). */
    void startReturn(ServerPlayer owner) {
        if (state() == RETURNING) return;
        stuck = null;
        critDecided = null;
        if (virtual) {
            discard();
            return;
        }
        if (!hitEnemy && !mine) SpinState.onMiss(owner);
        setState(RETURNING);
        returnTicks = 0;
        // Perla de Ender: en vez de volver la bola, el lanzador va al punto de impacto.
        if (material() == SphereMaterial.ENDER_PEARL && lastImpact != null) {
            SphereHits.enderTeleport(this, owner, lastImpact);
            arrive(owner);
            return;
        }
        // Retorno Veloz: si impactó, vuelve a la mano al instante.
        if (swiftReturn && hitEnemy) arrive(owner);
    }

    private void tickReturning(ServerPlayer owner) {
        Vec3 hand = handPos(owner);
        Vec3 to = hand.subtract(position());
        double dist = to.length();
        double base = SpinMaster.t("return_speed", 1.5) * (material() == SphereMaterial.GOLD ? 1.4 : 1.0);
        double v = Math.min(4.0, base * (1 + 0.08 * returnTicks));
        if (dist <= v + 0.6 || ++returnTicks > MAX_RETURN_TICKS) {
            arrive(owner);
            return;
        }
        Vec3 step = to.scale(v / dist);
        setDeltaMovement(step);
        Vec3 p = position().add(step);
        setPos(p.x, p.y, p.z);
        // Ventana de Captura Perfecta: le queda poco para llegar.
        SpinThrow.noteArriving(owner, this, dist / v);
    }

    /** Llega a la mano: se desmarca en la bandolera, entrega lo que trae y, con Captura Perfecta, se relanza. */
    void arrive(ServerPlayer owner) {
        if (isRemoved() || arrived) return;
        arrived = true;
        if (!virtual && !broken) SpinThrow.onArrive(owner, this);
        discard();
    }

    static Vec3 handPos(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
        return p.getEyePosition().add(0, -0.35, 0).add(right.scale(0.3)).add(look.scale(0.25));
    }

    // ------------------------------------------------------------------ mina

    private void tickMine(ServerPlayer owner) {
        if (level().getGameTime() >= mineUntil) {
            startReturn(owner);
            return;
        }
        if (age % 2 == 0) SphereHits.checkMine(this, owner);
    }

    // ------------------------------------------------------------------ cliente

    private void clientTick() {
        SphereMaterial m = material();
        byte st = state();
        if (st == MINE) {
            if (age % 10 == 0) level().addParticle(new DustParticleOptions(new Vector3f(m.red(), m.green(), m.blue()), 0.6f),
                    getX(), getY() + 0.15, getZ(), 0, 0.02, 0);
            return;
        }
        boolean gold = golden() || spin() >= 0.33f;
        Vec3 v = getDeltaMovement();
        if (st == SPINNING) {
            // Clavada y girando: chispas que salen despedidas (doradas con el Giro I o en la ulti).
            if (age % 2 == 0) level().addParticle(ParticleTypes.CRIT, getX(), getY(), getZ(), (random.nextDouble() - 0.5) * 0.4,
                    random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.4);
            if (gold && age % 3 == 0) {
                double a = age * 0.8;
                level().addParticle(ParticleTypes.WAX_OFF, getX() + Math.cos(a) * 0.3, getY() + 0.05, getZ() + Math.sin(a) * 0.3, 0, 0.02, 0);
            }
            return;
        }
        // Estela: color del material, o dorada con Giro I o con el Giro Dorado (con espirales).
        DustParticleOptions dust = gold ? new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.25f), 0.9f)
                : new DustParticleOptions(new Vector3f(m.red(), m.green(), m.blue()), 0.7f);
        level().addParticle(dust, getX() - v.x * 0.5, getY() - v.y * 0.5, getZ() - v.z * 0.5, 0, 0, 0);
        if (gold && v.lengthSqr() > 1.0E-4) {
            Vec3 dir = v.normalize();
            Vec3 side = dir.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
            side = side.normalize();
            Vec3 up = side.cross(dir).normalize();
            double ang = age * 0.9;
            Vec3 off = side.scale(Math.cos(ang) * 0.35).add(up.scale(Math.sin(ang) * 0.35));
            level().addParticle(ParticleTypes.WAX_OFF, getX() + off.x, getY() + off.y, getZ() + off.z, 0, 0, 0);
        }
        if (m == SphereMaterial.NETHERITE && age % 3 == 0) level().addParticle(ParticleTypes.SMALL_FLAME, getX(), getY(), getZ(), 0, 0, 0);
        if (m == SphereMaterial.REDSTONE && age % 3 == 0) level().addParticle(DustParticleOptions.REDSTONE, getX(), getY(), getZ(), 0, 0, 0);
    }

    // ------------------------------------------------------------------ utilidades

    /** Gira {@code v} hacia {@code want} como mucho {@code maxRad} radianes, conservando su módulo. */
    static Vec3 steer(Vec3 v, Vec3 want, double maxRad) {
        double speed = v.length();
        if (speed < 1.0E-6 || want.lengthSqr() < 1.0E-6) return v;
        Vec3 a = v.scale(1 / speed);
        Vec3 b = want.normalize();
        double dot = Mth.clamp(a.dot(b), -1.0, 1.0);
        double ang = Math.acos(dot);
        if (ang < 1.0E-4) return v;
        double sin = Math.sin(ang);
        if (sin < 1.0E-4) return v;
        double t = Math.min(1, maxRad / ang);
        Vec3 r = a.scale(Math.sin((1 - t) * ang) / sin).add(b.scale(Math.sin(t * ang) / sin));
        return r.normalize().scale(speed);
    }

    /** Para Giro Reverso: la bola sigue clavada (girando o en el tiempo extra de Garra). */
    public boolean isStuck() {
        return state() == SPINNING && stuck != null;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) SpinThrow.onRemoved(this);
        super.remove(reason);
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return false;
    }

    public Player ownerPlayer() {
        return getOwner() instanceof Player p ? p : null;
    }

    static Direction faceOf(BlockHitResult hit) {
        return hit.getDirection();
    }
}
