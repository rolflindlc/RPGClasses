package io.github.rpgclasses.client;

import io.github.rpgclasses.content.race.RaceTraits;
import io.github.rpgclasses.content.race.Races;
import io.github.rpgclasses.registry.ModAttributes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;

/**
 * Movimiento de las razas de la adenda 0.7 que decide el cliente (el movimiento del jugador es suyo):
 * <ul>
 *   <li>Slime: rebote al caer sobre cualquier bloque (agachado no rebota).</li>
 *   <li>Coloso: no nada; se hunde y camina por el fondo, y puede salir saltando de agua poco profunda.</li>
 *   <li>Centauro: salto cargado como el del caballo (mantener saltar y soltar; un toque es un salto normal), con barra.</li>
 * </ul>
 */
public final class RaceBodyClient {
    /** Altura del salto con la carga completa del Centauro, en bloques (igual en todos los Rangos). */
    public static final double CENTAUR_FULL_JUMP = 5.3;
    private static final int TAP_TICKS = 3;
    private static final double MIN_BOUNCE = 0.2;

    private static double airVy;
    private static int jumpHeld;
    private static int shallowJumpCooldown;

    private RaceBodyClient() {}

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || p.isSpectator() || p.getAbilities().flying || p.isPassenger()) {
            airVy = 0;
            return;
        }
        bounce(p);
        heavy(p, mc.options.keyJump.isDown() && mc.screen == null);
    }

    /** Slime: al tocar suelo tras caer, sale rebotado con la velocidad que traía (como un bloque de slime). */
    private static void bounce(LocalPlayer p) {
        double factor = Races.number(p, "race_bouncy", "bounce", 0);
        if (factor <= 0) {
            airVy = 0;
            return;
        }
        if (!p.onGround()) {
            airVy = p.getDeltaMovement().y;
            return;
        }
        if (airVy < -MIN_BOUNCE && !p.isShiftKeyDown() && !p.isInWaterOrBubble() && !p.isInLava() && !p.isFallFlying()) {
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(v.x, -airVy * factor, v.z);
            p.hasImpulse = true;
        }
        airVy = 0;
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

    /** Barra de carga en el sitio de la barra de experiencia (como la del caballo). */
    public static void renderHud(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || jumpHeld <= TAP_TICKS) return;
        float fraction = chargeFraction(mc.player);
        int x = width / 2 - 91;
        int y = height - 29;
        g.fill(x, y, x + 182, y + 5, 0xFF000000);
        g.fill(x + 1, y + 1, x + 181, y + 4, 0xFF303030);
        g.fill(x + 1, y + 1, x + 1 + (int) (180 * fraction), y + 4, fraction >= 1f ? 0xFF55FF55 : 0xFFE0C040);
    }
}
