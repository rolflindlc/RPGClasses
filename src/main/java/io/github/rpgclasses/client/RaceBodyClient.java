package io.github.rpgclasses.client;

import io.github.rpgclasses.content.race.RaceTraits;
import io.github.rpgclasses.content.race.Races;
import io.github.rpgclasses.registry.ModAttributes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;

/**
 * Movimiento de las razas de la adenda 0.7 que decide el cliente (el movimiento del jugador es suyo):
 * <ul>
 *   <li>Slime: rebote al caer desde 3+ bloques (pierde altura en cada rebote; agachado no rebota). Resorte: bola que rebota en paredes y suelo mientras dura la carga.</li>
 *   <li>Coloso: no nada; se hunde y camina por el fondo, y puede salir saltando de agua poco profunda.</li>
 *   <li>Centauro: salto cargado como el del caballo (mantener saltar y soltar; un toque es un salto normal), con barra.</li>
 * </ul>
 */
public final class RaceBodyClient {
    /** Altura del salto con la carga completa del Centauro, en bloques (igual en todos los Rangos). */
    public static final double CENTAUR_FULL_JUMP = 3.55; // 5,3 − 33 %
    private static final int TAP_TICKS = 3;
    private static final double MIN_BOUNCE = 0.2;

    private static double airVy;
    private static int jumpHeld;
    private static int shallowJumpCooldown;
    // Slime Resorte
    private static int springHeld;
    /** Ticks que le quedan al modo pelota tras el lanzamiento (dura lo que duró la carga; mínimo 2 s). */
    private static int springTicks;
    private static double springWallFactor = 1.05;
    private static double springMaxSpeed;
    private static double springHSpeed;
    /** Velocidad al final del tick anterior: el choque anula la componente contra la que se choca, así que se refleja la de antes. */
    private static Vec3 prevVel = Vec3.ZERO;
    /** Slime: altura más alta de la caída en curso (para exigir una caída mínima antes de rebotar). */
    private static double fallPeakY = Double.NaN;
    // Slime Viscoso
    private static double grip = -1;
    private static boolean stuck;
    private static int stuckEntity = -1;
    private static Vec3 stuckOffset = Vec3.ZERO;
    private static float lastHealth;
    // Centauro Pegaso
    private static long flapReadyAt;
    private static boolean jumpWasDown;

    private RaceBodyClient() {}

    /** Ticks que quedan de refrescar la hitbox y la altura de ojos tras elegir o cambiar de raza. */
    private static int refreshTicks;

    /** Al cambiar la raza (y su tamaño) la cámara se ajusta enseguida, no al agacharse por primera vez. */
    public static void refreshBodySoon() {
        refreshTicks = 20;
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p != null && refreshTicks > 0) {
            refreshTicks--;
            p.refreshDimensions();
        }
        if (p == null || mc.level == null || p.isSpectator() || p.getAbilities().flying || p.isPassenger()) {
            airVy = 0;
            return;
        }
        boolean jump = mc.options.keyJump.isDown() && mc.screen == null;
        springBounce(p);
        sticky(p, jump);
        if (!stuck && springTicks <= 0) bounce(p);
        else if (springTicks > 0) { airVy = 0; fallPeakY = Double.NaN; }
        heavy(p, jump);
        glide(p, jump);
        jumpWasDown = jump;
        lastHealth = p.getHealth();
    }

    // ------------------------------------------------------------------ Slime Resorte

    private static long cooldownLeft(LocalPlayer p, String key) {
        return ClientHandlers.LOCAL_DATA.cooldowns.getOrDefault(key, 0L) - p.level().getGameTime();
    }

    /** Agachado en el suelo se carga; se pierde al dejar de agacharse sin saltar. */
    private static void springCharge(LocalPlayer p, MovementInputUpdateEvent event) {
        double maxSeconds = Races.number(p, "race_spring", "charge_seconds", 0);
        if (maxSeconds <= 0) {
            springHeld = 0;
            return;
        }
        int max = (int) Math.round(maxSeconds * 20);
        boolean sneaking = event.getInput().shiftKeyDown;
        if (sneaking && p.onGround() && !event.getInput().jumping) {
            if (cooldownLeft(p, "rites_and_paths:race_spring") <= 0) springHeld = Math.min(max, springHeld + 1);
            return;
        }
        int min = (int) Math.round(Races.number(p, "race_spring", "min_charge_seconds", 0.5) * 20);
        if (event.getInput().jumping && sneaking && p.onGround() && springHeld >= min) {
            double maxPower = Races.number(p, "race_spring", "power", 1);
            double power = maxPower * (0.3 + 0.7 * springHeld / (double) max);
            // Como el Impulso del tridente: 3 × (1 + nivel) / 4 bloques por tick hacia donde mira.
            Vec3 dir = p.getLookAngle().normalize().scale(3.0 * (1 + power) / 4.0);
            if (p.onGround()) p.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0, 1.2, 0));
            p.push(dir.x, dir.y, dir.z);
            p.startAutoSpinAttack(20);
            p.resetFallDistance();
            springTicks = Math.max(40, springHeld);
            springWallFactor = Races.number(p, "race_spring", "wall_bounce", 1.05);
            springMaxSpeed = dir.length();
            double h = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
            springHSpeed = Math.max(h, 0.4 * springMaxSpeed);
            prevVel = dir;
            io.github.rpgclasses.network.Net.CHANNEL.sendToServer(new io.github.rpgclasses.network.Packets.RaceMotion(
                    io.github.rpgclasses.network.Packets.RaceMotion.SPRING, springHeld, -1));
            event.getInput().jumping = false;
            event.getInput().shiftKeyDown = false;
        }
        springHeld = 0;
    }

    /**
     * Tras un lanzamiento el Resorte es una pelota mientras dura la carga: rebota contra suelo, techo y paredes sin
     * gastarse (los choques no la desactivan; solo agacharse o acabar el tiempo) y mantiene la velocidad horizontal.
     */
    private static void springBounce(LocalPlayer p) {
        Vec3 v = p.getDeltaMovement();
        if (springTicks <= 0) {
            prevVel = v;
            return;
        }
        springTicks--;
        if (p.isShiftKeyDown() || p.isInWaterOrBubble() || p.isInLava()) {
            springTicks = 0;
            prevVel = v;
            return;
        }
        double k = springWallFactor, vx = v.x, vy = v.y, vz = v.z;
        // Se predice el movimiento del próximo tick con la colisión de vanilla: no depende de los indicadores de
        // choque (que el giro de Impulso no siempre deja puestos). Si hay choque se llega hasta el obstáculo y se
        // sale rebotado con el eje que choca invertido.
        Vec3 col = net.minecraft.world.entity.Entity.collideBoundingBox(p, v, p.getBoundingBox(), p.level(), java.util.List.of());
        boolean hx = Math.abs(v.x) > 0.05 && Math.abs(col.x - v.x) > 1.0E-5;
        boolean hz = Math.abs(v.z) > 0.05 && Math.abs(col.z - v.z) > 1.0E-5;
        boolean hy = Math.abs(v.y) > 0.1 && Math.abs(col.y - v.y) > 1.0E-5;
        if (hx || hy || hz) {
            p.move(net.minecraft.world.entity.MoverType.SELF, col);
            if (hx) vx = -v.x * k;
            if (hz) vz = -v.z * k;
            if (hy) vy = -v.y * k;
        } else {
            // Respaldo: el choque ya pasó en este tick (vanilla anula la componente que choca); se refleja la de antes.
            Vec3 pv = prevVel;
            if (p.horizontalCollision) {
                if (Math.abs(pv.x) > 0.05 && Math.abs(v.x) < Math.abs(pv.x) * 0.5) vx = -pv.x * k;
                if (Math.abs(pv.z) > 0.05 && Math.abs(v.z) < Math.abs(pv.z) * 0.5) vz = -pv.z * k;
            }
            if (p.verticalCollision && Math.abs(pv.y) > 0.1 && Math.abs(v.y) < Math.abs(pv.y) * 0.5) vy = -pv.y * k;
        }
        // El rozamiento del aire resta velocidad horizontal cada tick: se repone hasta la velocidad del lanzamiento.
        double hs = Math.sqrt(vx * vx + vz * vz);
        if (hs > 0.01 && hs < springHSpeed) {
            vx *= springHSpeed / hs;
            vz *= springHSpeed / hs;
        }
        double cap = Math.max(springMaxSpeed, 1.5);
        vy = Math.max(-cap, Math.min(cap, vy));
        Vec3 out = new Vec3(vx, vy, vz);
        if (out.distanceToSqr(v) > 1.0E-6) {
            p.setDeltaMovement(out);
            p.hasImpulse = true;
        }
        p.resetFallDistance();
        prevVel = out;
    }

    // ------------------------------------------------------------------ Slime Viscoso

    private static void setStuck(boolean value, int entity) {
        if (stuck == value && stuckEntity == entity) return;
        stuck = value;
        stuckEntity = entity;
        io.github.rpgclasses.network.Net.CHANNEL.sendToServer(new io.github.rpgclasses.network.Packets.RaceMotion(
                value ? io.github.rpgclasses.network.Packets.RaceMotion.STICK : io.github.rpgclasses.network.Packets.RaceMotion.UNSTICK, 0, entity));
    }

    private static void sticky(LocalPlayer p, boolean jump) {
        double gripSeconds = Races.number(p, "race_sticky", "grip_seconds", 0);
        if (gripSeconds <= 0) {
            if (stuck) setStuck(false, -1);
            grip = -1;
            return;
        }
        double max = gripSeconds * 20;
        if (grip < 0) grip = max;
        if (!stuck && p.onGround()) grip = Math.min(max, grip + max / 40);
        // Pared de miel: en el aire, contra una pared se cae despacio.
        double slide = Races.number(p, "race_honey_slide", "slide_speed", 0);
        Vec3 v = p.getDeltaMovement();
        if (!stuck && slide > 0 && !p.onGround() && p.horizontalCollision && v.y < -slide) {
            p.setDeltaMovement(v.x, -slide, v.z);
            p.resetFallDistance();
        }
        boolean sneaking = p.isShiftKeyDown();
        float lost = lastHealth - p.getHealth();
        if (stuck) {
            Entity target = stuckEntity >= 0 ? p.level().getEntity(stuckEntity) : null;
            boolean release = !sneaking || grip <= 0 || lost >= Races.number(p, "race_sticky", "release_damage", 6)
                    || (stuckEntity >= 0 && (target == null || !target.isAlive()));
            if (release) {
                setStuck(false, -1);
                return;
            }
            grip--;
            if (target != null) {
                Vec3 at = target.position().add(stuckOffset);
                p.setDeltaMovement(at.subtract(p.position()));
            } else {
                p.setDeltaMovement(0, jump ? 0.15 : 0, 0);
            }
            p.resetFallDistance();
            return;
        }
        if (!sneaking || grip <= 0 || p.onGround()) return;
        if (p.horizontalCollision) {
            stuckOffset = Vec3.ZERO;
            setStuck(true, -1);
            return;
        }
        for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(0.15), e -> e instanceof net.minecraft.world.entity.LivingEntity && e.isAlive())) {
            stuckOffset = p.position().subtract(e.position());
            setStuck(true, e.getId());
            return;
        }
    }

    // ------------------------------------------------------------------ Centauro Pegaso

    /** Mientras planea (caída lenta oculta del servidor): control en el aire (Rango 2) y aleteo (Rango 3). */
    private static void glide(LocalPlayer p, boolean jump) {
        var slow = p.getEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING);
        if (slow == null || !slow.isAmbient() || p.onGround() || !Races.has(p, "race_glide")) return;
        if (Races.number(p, "race_glide", "air_control", 0) > 0) {
            Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
            float forward = p.input.forwardImpulse;
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(v.x + look.x * 0.02 * forward, v.y, v.z + look.z * 0.02 * forward);
        }
        // Caída lenta de verdad: la de vanilla solo reduce la gravedad y se tarda en frenar desde velocidad de caída.
        Vec3 fall = p.getDeltaMovement();
        if (fall.y < -0.15) p.setDeltaMovement(fall.x, Math.max(-0.15, fall.y * 0.6), fall.z);
        double flap = Races.number(p, "race_glide", "flap_cooldown_seconds", 0);
        long now = p.level().getGameTime();
        if (flap > 0 && jump && !jumpWasDown && now >= flapReadyAt) {
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(v.x, 0.6, v.z);
            p.hasImpulse = true;
            flapReadyAt = now + Math.round(flap * 20);
            p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.4f, false);
        }
    }

    /**
     * Slime: al tocar suelo tras una caída de al menos {@code bounce_min_fall} bloques sale rebotado con la velocidad
     * que traía por el factor de rebote (menor que 1), de modo que pierde altura en cada rebote y deja de rebotar
     * cuando la caída siguiente ya no llega al mínimo.
     */
    private static void bounce(LocalPlayer p) {
        double factor = Races.number(p, "race_bouncy", "bounce", 0);
        if (factor <= 0) {
            airVy = 0;
            fallPeakY = Double.NaN;
            return;
        }
        if (!p.onGround()) {
            airVy = p.getDeltaMovement().y;
            fallPeakY = Double.isNaN(fallPeakY) ? p.getY() : Math.max(fallPeakY, p.getY());
            return;
        }
        double minFall = Races.number(p, "race_bouncy", "bounce_min_fall", 3);
        double drop = Double.isNaN(fallPeakY) ? 0 : fallPeakY - p.getY();
        if (airVy < -MIN_BOUNCE && drop >= minFall && !p.isShiftKeyDown() && !p.isInWaterOrBubble() && !p.isInLava() && !p.isFallFlying()) {
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(v.x, -airVy * factor, v.z);
            p.hasImpulse = true;
        }
        airVy = 0;
        fallPeakY = Double.NaN;
    }

    /** Coloso: en el agua no nada; se hunde aunque mantenga saltar. */
    private static void heavy(LocalPlayer p, boolean jumpDown) {
        if (shallowJumpCooldown > 0) shallowJumpCooldown--;
        if (!Races.has(p, "race_heavy") || !p.isInWater()) return;
        p.setSwimming(false);
        if (p.isSprinting()) p.setSprinting(false);
        Vec3 v = p.getDeltaMovement();
        if (!p.onGround()) {
            p.setDeltaMovement(v.x, v.y - 0.05, v.z);
        } else if (jumpDown && shallowJumpCooldown == 0 && p.getFluidHeight(FluidTags.WATER) < p.getBbHeight() * 0.6) {
            // Agua poco profunda: salto normal desde el fondo para poder salir.
            p.setDeltaMovement(v.x, 0.42 * Math.sqrt(1 + ModAttributes.value(p, ModAttributes.JUMP_BONUS)), v.z);
            p.hasImpulse = true;
            shallowJumpCooldown = 10;
        }
    }

    // ------------------------------------------------------------------ Centauro: salto cargado

    private static int chargeTicks(LocalPlayer p) {
        return Math.max(1, (int) Math.round(Races.number(p, "race_centaur", "jump_charge_seconds", 1) * 20));
    }

    public static float chargeFraction(LocalPlayer p) {
        return jumpHeld <= TAP_TICKS ? 0f : Math.min(1f, (float) jumpHeld / chargeTicks(p));
    }

    /** Velocidad vertical para llegar a {@code height} bloques con la gravedad y el rozamiento de vanilla. */
    static double velocityForHeight(double height) {
        double lo = 0.42, hi = 2.0;
        for (int i = 0; i < 30; i++) {
            double mid = (lo + hi) / 2, y = 0, v = mid;
            while (v > 0) {
                y += v;
                v = (v - 0.08) * 0.98;
            }
            if (y < height) lo = mid; else hi = mid;
        }
        return hi;
    }

    public static void onMovementInput(MovementInputUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(event.getEntity() instanceof LocalPlayer p) || p != mc.player) return;
        springCharge(p, event);
        if (!RaceTraits.isCentaur(p) || p.isPassenger() || p.getAbilities().flying || p.isInWater() || p.isInLava()) {
            jumpHeld = 0;
            return;
        }
        boolean down = event.getInput().jumping;
        if (down && p.onGround()) {
            jumpHeld++;
            event.getInput().jumping = false; // carga en vez de saltar
            return;
        }
        if (!down && jumpHeld > 0) {
            if (jumpHeld <= TAP_TICKS) {
                event.getInput().jumping = true; // un toque: salto normal
            } else if (p.onGround()) {
                float frac = chargeFraction(p);
                double normal = 1.25 * (1 + Math.max(0, ModAttributes.value(p, ModAttributes.JUMP_BONUS)));
                double height = normal + (Math.max(normal, CENTAUR_FULL_JUMP) - normal) * frac;
                Vec3 v = p.getDeltaMovement();
                Vec3 forward = Vec3.directionFromRotation(0, p.getYRot()).scale(0.25 * frac);
                p.setDeltaMovement(v.x + forward.x, velocityForHeight(height), v.z + forward.z);
                p.hasImpulse = true;
            }
            jumpHeld = 0;
        }
    }

    // ------------------------------------------------------------------ Centauro Guerrero: atacar sin frenarse

    private static float rawForward, rawLeft;
    private static boolean rawUp, rawDown, rawLeftKey, rawRightKey;
    private static Vec3 attackVelocity;

    /** ¿El jugador local es un Guerrero a ≥40 % de su velocidad máxima? */
    private static boolean warriorGalloping(LocalPlayer p) {
        return Races.has(p, "race_war_gallop")
                && io.github.rpgclasses.content.race.BodyTraits.clientGallop(p) >= Races.number(p, "race_centaur", "ram_threshold", 0.4);
    }

    /** Antes que los demás (prioridad máxima): lo que el jugador pulsa de verdad. */
    public static void captureInput(MovementInputUpdateEvent event) {
        if (event.getEntity() != Minecraft.getInstance().player) return;
        var in = event.getInput();
        rawForward = in.forwardImpulse;
        rawLeft = in.leftImpulse;
        rawUp = in.up;
        rawDown = in.down;
        rawLeftKey = in.left;
        rawRightKey = in.right;
    }

    /**
     * Después de los demás (prioridad mínima): con Epic Fight un ataque bloquea el movimiento durante la animación;
     * el Guerrero al galope sigue moviéndose. También deshace el frenazo vanilla al golpear corriendo (×0,6 y deja de
     * correr). La Apuesta con la Muerte sí le detiene.
     */
    public static void restoreInput(MovementInputUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(event.getEntity() instanceof LocalPlayer p) || p != mc.player) return;
        if (CoinFlipFx.localFlipping() || p.isPassenger() || !warriorGalloping(p)) {
            attackVelocity = null;
            return;
        }
        var in = event.getInput();
        if (in.forwardImpulse == 0 && in.leftImpulse == 0 && (rawForward != 0 || rawLeft != 0)) {
            in.forwardImpulse = rawForward;
            in.leftImpulse = rawLeft;
            in.up = rawUp;
            in.down = rawDown;
            in.left = rawLeftKey;
            in.right = rawRightKey;
            if (!p.isSprinting() && rawForward > 0) p.setSprinting(true);
        }
        if (attackVelocity != null) {
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(attackVelocity.x, v.y, attackVelocity.z);
            if (!p.isSprinting()) p.setSprinting(true);
            attackVelocity = null;
        }
    }

    /** Golpe del jugador local: se guarda su velocidad para deshacer el frenazo vanilla. */
    public static void onAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity() != mc.player || mc.player == null || !mc.player.isSprinting() || !warriorGalloping(mc.player)) return;
        attackVelocity = mc.player.getDeltaMovement();
    }

    /** Barra de carga en el sitio de la barra de experiencia (como la del caballo). */
    public static void renderHud(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        int x = width / 2 - 91;
        int y = height - 29;
        if (jumpHeld > TAP_TICKS || springHeld > 0) io.github.rpgclasses.client.hud.HudLayout.report("centaur_jump", x, y - 6, 182, 11);
        if (jumpHeld > TAP_TICKS) bar(g, x, y, chargeFraction(mc.player), 0xFFE0C040);
        double maxSpring = Races.number(mc.player, "race_spring", "charge_seconds", 0) * 20;
        if (springHeld > 0 && maxSpring > 0) bar(g, x, y, (float) Math.min(1, springHeld / maxSpring), 0xFF7CDB5A);
        double maxGrip = Races.number(mc.player, "race_sticky", "grip_seconds", 0) * 20;
        if (maxGrip > 0 && grip >= 0 && (stuck || grip < maxGrip)) bar(g, x, y - 6, (float) (grip / maxGrip), 0xFFD8A030);
    }

    private static void bar(GuiGraphics g, int x, int y, float fraction, int color) {
        g.fill(x, y, x + 182, y + 5, 0xFF000000);
        g.fill(x + 1, y + 1, x + 181, y + 4, 0xFF303030);
        g.fill(x + 1, y + 1, x + 1 + (int) (180 * fraction), y + 4, fraction >= 1f ? 0xFF55FF55 : color);
    }
}
