package io.github.rpgclasses.client.spin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.rpgclasses.config.ClientConfig;
import io.github.rpgclasses.network.Packets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static io.github.rpgclasses.spin.GoldenRectangle.DRAG_BACK;
import static io.github.rpgclasses.spin.GoldenRectangle.END;
import static io.github.rpgclasses.spin.GoldenRectangle.ESCAPE;
import static io.github.rpgclasses.spin.GoldenRectangle.FINAL;
import static io.github.rpgclasses.spin.GoldenRectangle.FINAL_HIT;
import static io.github.rpgclasses.spin.GoldenRectangle.IMPACT;
import static io.github.rpgclasses.spin.GoldenRectangle.SCAN_SHOT;
import static io.github.rpgclasses.spin.GoldenRectangle.THROW;

/**
 * Cámara de cine del Rectángulo Dorado (solo para el que la lanza), con el guion de {@link io.github.rpgclasses.spin.GoldenRectangle}.
 * Planos con cortes secos, como viñetas:
 * <ol>
 *   <li>Perfil del lanzador, bajo, acercándose (0-13); por encima del hombro hacia el objetivo, con el rectángulo áureo y
 *       su espiral dibujados encima (13-26).</li>
 *   <li>Detrás de la bola, siguiéndola hasta el objetivo, con líneas de velocidad (26-40).</li>
 *   <li>Girando alrededor del agujero mientras la rotación lo retiene (40-96), algo más lejos durante el escape.</li>
 *   <li>Contrapicado a ras de suelo para el golpe final, con destello y temblor (96-114).</li>
 * </ol>
 * La posición la pone {@code mixin.client.SpinCameraMixin} (al final de Camera.setup, en tercera persona); el balanceo y
 * el zoom, los eventos de cámara de SpinClient. Mientras dura no hay HUD, mano, contorno de bloque ni movimiento. La
 * cámara no atraviesa bloques. Con la cinemática desactivada en la configuración, solo se bloquea el control.
 */
public final class SpinCinematic {
    public record Shot(Vec3 pos, float yaw, float pitch, float roll, float fov) {}

    private static boolean active;
    private static int tick, targetId = -1, sphereId = -1;
    private static Vec3 lastTarget;
    private static double orbitStart = Double.NaN;
    private static float lockYaw, lockPitch;
    private static Shot current;

    private SpinCinematic() {}

    /** Hay un Rectángulo Dorado en curso: sin moverse ni actuar. */
    public static boolean locked() {
        return active;
    }

    /** Además, la cámara de cine está activa (se puede quitar en la configuración del cliente). */
    public static boolean camera() {
        return active && ClientConfig.spinCinematic();
    }

    public static void onPacket(Packets.SpinCinematic msg) {
        Minecraft mc = Minecraft.getInstance();
        if (msg.tick() < 0) {
            stop();
            return;
        }
        if (!active) {
            lastTarget = null;
            orbitStart = Double.NaN;
            if (mc.player != null) {
                lockYaw = mc.player.getYRot();
                lockPitch = mc.player.getXRot();
            }
        }
        active = true;
        tick = msg.tick();
        targetId = msg.targetId();
        sphereId = msg.sphereId();
    }

    public static void stop() {
        active = false;
        current = null;
        targetId = sphereId = -1;
    }

    static void clientTick() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || ++tick > END + 40) {
            stop();
            return;
        }
        // La vista del jugador no se mueve (el servidor ya lo giró hacia el objetivo).
        LocalPlayer p = mc.player;
        p.setYRot(lockYaw);
        p.setXRot(lockPitch);
        p.yRotO = lockYaw;
        p.xRotO = lockPitch;
    }

    static void lockInput(Input input) {
        input.leftImpulse = 0;
        input.forwardImpulse = 0;
        input.up = input.down = input.left = input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    // ------------------------------------------------------------------ planos

    /** Plano de este fotograma (null: cámara normal). Lo llama el mixin de la cámara. */
    public static Shot shot(float pt) {
        current = null;
        if (!camera()) return null;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null) return null;
        float t = tick + pt;
        if (t >= END) return null;
        Entity target = targetId >= 0 ? mc.level.getEntity(targetId) : null;
        Entity sphere = sphereId >= 0 ? mc.level.getEntity(sphereId) : null;
        Vec3 eye = me.getEyePosition(pt);
        Vec3 tc = target != null ? center(target, pt) : lastTarget != null ? lastTarget : eye.add(me.getLookAngle().scale(5));
        lastTarget = tc;
        Vec3 anchor = sphere != null && t >= IMPACT ? center(sphere, pt) : tc;
        Vec3 flat = tc.subtract(eye).multiply(1, 0, 1);
        if (flat.lengthSqr() < 1.0E-4) flat = me.getLookAngle().multiply(1, 0, 1);
        if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(0, 0, 1);
        flat = flat.normalize();
        Vec3 right = flat.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 pos, look;
        float roll = 0, fov = 1;
        if (t < SCAN_SHOT) {
            float e = ease(t / SCAN_SHOT);
            pos = eye.add(right.scale(2.8 - 0.9 * e)).add(flat.scale(0.5)).add(0, -0.3 + 0.15 * e, 0);
            look = eye.add(flat.scale(0.9)).add(0, -0.15, 0);
            fov = 0.9f;
        } else if (t < THROW) {
            float e = ease((t - SCAN_SHOT) / (THROW - SCAN_SHOT));
            pos = eye.add(flat.scale(-(2.4 - 0.7 * e))).add(right.scale(0.8)).add(0, 0.35, 0);
            look = tc;
            fov = 0.85f - 0.12f * e;
        } else if (t < IMPACT) {
            if (sphere != null) {
                Vec3 sp = center(sphere, pt);
                Vec3 dir = tc.subtract(sp);
                dir = dir.lengthSqr() < 1.0E-4 ? flat : dir.normalize();
                pos = sp.subtract(dir.scale(2.3)).add(0, 0.45, 0).add(right.scale(0.3));
            } else {
                pos = eye.add(flat.scale(-1.7)).add(right.scale(0.8)).add(0, 0.35, 0);
            }
            look = tc;
            fov = 1.12f;
        } else if (t < FINAL) {
            if (Double.isNaN(orbitStart)) {
                Vec3 rel = eye.subtract(anchor);
                orbitStart = Math.atan2(rel.z, rel.x) + 0.7;
            }
            double a = orbitStart + (t - IMPACT) * 0.055;
            double rad = Math.max(3.6, (target != null ? target.getBbWidth() : 0.6) * 2.5 + 2.2);
            if (t >= ESCAPE && t < DRAG_BACK + 4) rad *= 1.35;
            pos = anchor.add(Math.cos(a) * rad, 0.8 + 0.25 * Math.sin(t * 0.08), Math.sin(a) * rad);
            look = anchor;
            roll = 3f * Mth.sin(t * 0.12f);
            fov = 0.92f;
        } else {
            Vec3 rel = eye.subtract(anchor).multiply(1, 0, 1);
            rel = rel.lengthSqr() < 1.0E-4 ? flat.reverse() : rel.normalize();
            double c = Math.cos(Math.toRadians(70)), s = Math.sin(Math.toRadians(70));
            Vec3 side = new Vec3(rel.x * c - rel.z * s, 0, rel.x * s + rel.z * c);
            double feet = anchor.y - (target != null ? target.getBbHeight() / 2 : 0.9);
            float f = (t - FINAL) / (END - FINAL);
            double d = 3.2 - 0.6 * f;
            pos = new Vec3(anchor.x + side.x * d, feet + 0.35, anchor.z + side.z * d);
            look = anchor.add(0, 0.3, 0);
            fov = t >= FINAL_HIT && t < FINAL_HIT + 5 ? 0.8f : 0.95f;
        }
        // Que no se meta en los bloques.
        BlockHitResult hit = mc.level.clip(new ClipContext(look, pos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, me));
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 back = look.subtract(pos);
            pos = hit.getLocation().add(back.lengthSqr() < 1.0E-6 ? Vec3.ZERO : back.normalize().scale(0.25));
        }
        Vec3 d = look.subtract(pos);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) (-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG));
        current = new Shot(pos, yaw, pitch, roll, fov);
        return current;
    }

    static float roll() {
        return current == null ? 0 : current.roll();
    }

    static float fov() {
        return current == null ? 1 : current.fov();
    }

    private static Vec3 center(Entity e, float pt) {
        return e.getPosition(pt).add(0, e.getBbHeight() / 2, 0);
    }

    private static float ease(float f) {
        f = Mth.clamp(f, 0f, 1f);
        return f * f * (3 - 2 * f);
    }

    // ------------------------------------------------------------------ capas de interfaz

    /** Bandas, tinte dorado, cortes, rectángulo áureo, líneas de velocidad y destellos (debajo del resto del HUD). */
    static void renderOverlay(GuiGraphics g, float pt, int width, int height) {
        if (!camera()) return;
        float t = tick + pt;
        float k = Mth.clamp(Math.min(t / 6f, (END - t) / 8f), 0f, 1f);
        if (k <= 0) return;
        int bar = (int) (height * 0.12f * k);
        g.fill(0, 0, width, bar, 0xF5000000);
        g.fill(0, height - bar, width, height, 0xF5000000);
        g.fill(0, bar, width, height - bar, ((int) (0x22 * k) << 24) | 0xF2C230);
        if (t < THROW) {
            float in = Mth.clamp((t - 2) / 6f, 0f, 1f), out = Mth.clamp((THROW - t) / 4f, 0f, 1f);
            float size = height * (t < SCAN_SHOT ? 0.30f : 0.55f) * (0.85f + 0.15f * ease((t % SCAN_SHOT) / SCAN_SHOT));
            goldenRectangle(g, width / 2f, height / 2f, size, t * 0.9f, (int) (0xD0 * in * out));
        }
        if (t >= THROW && t < IMPACT + 2) speedLines(g, width, height, t, 0x60);
        if (t >= ESCAPE && t < DRAG_BACK) {
            float f = 1 - (t - ESCAPE) / (DRAG_BACK - ESCAPE);
            g.fill(0, bar, width, height - bar, ((int) (0x40 * f) << 24) | 0x7A3CFF);
        }
        if (t >= FINAL_HIT && t < FINAL_HIT + 10) speedLines(g, width, height, t, (int) (0x90 * (1 - (t - FINAL_HIT) / 10f)));
        // Cortes de plano: un destello blanco breve.
        for (int cut : new int[] {SCAN_SHOT, THROW, IMPACT, FINAL}) {
            if (t >= cut && t < cut + 3) g.fill(0, 0, width, height, ((int) (0x90 * (1 - (t - cut) / 3f)) << 24) | 0xFFFFFF);
        }
        if (t >= ESCAPE && t < ESCAPE + 5) g.fill(0, 0, width, height, ((int) (0x80 * (1 - (t - ESCAPE) / 5f)) << 24) | 0xC9B0FF);
        if (t >= FINAL_HIT && t < FINAL_HIT + 6) g.fill(0, 0, width, height, ((int) (0xD0 * (1 - (t - FINAL_HIT) / 6f)) << 24) | 0xFFF4C0);
    }

    /** Rectángulo áureo subdividido en cuadrados con su espiral, girando poco a poco. */
    private static void goldenRectangle(GuiGraphics g, float cx, float cy, float h, float angle, int alpha) {
        if (alpha <= 4) return;
        int line = (alpha << 24) | 0xF2C230;
        int dot = (Math.min(255, alpha + 40) << 24) | 0xFFE9A0;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        float w = h * 1.618f;
        float x = -w / 2, y = -h / 2;
        outline(g, x, y, w, h, line);
        for (int i = 0; i < 9 && w > 2 && h > 2; i++) {
            float s;
            float ccx, ccy, a0;
            switch (i % 4) {
                case 0 -> { // cuadrado a la izquierda
                    s = h;
                    outline(g, x, y, s, s, line);
                    ccx = x + s; ccy = y + s; a0 = 180;
                    x += s; w -= s;
                }
                case 1 -> { // arriba
                    s = w;
                    outline(g, x, y, s, s, line);
                    ccx = x; ccy = y + s; a0 = 270;
                    y += s; h -= s;
                }
                case 2 -> { // a la derecha
                    s = h;
                    outline(g, x + w - s, y, s, s, line);
                    ccx = x + w - s; ccy = y; a0 = 0;
                    w -= s;
                }
                default -> { // abajo
                    s = w;
                    outline(g, x, y + h - s, s, s, line);
                    ccx = x + s; ccy = y + h - s; a0 = 90;
                    h -= s;
                }
            }
            int n = Math.max(4, (int) (s / 3));
            for (int j = 0; j <= n; j++) {
                double a = Math.toRadians(a0 + 90.0 * j / n);
                int px = Math.round((float) (ccx + Math.cos(a) * s)), py = Math.round((float) (ccy + Math.sin(a) * s));
                g.fill(px - 1, py - 1, px + 1, py + 1, dot);
            }
        }
        pose.popPose();
    }

    private static void outline(GuiGraphics g, float x, float y, float w, float h, int color) {
        int x0 = Math.round(x), y0 = Math.round(y), x1 = Math.round(x + w), y1 = Math.round(y + h);
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }

    /** Líneas de velocidad desde los bordes hacia el centro (cambian cada tic). */
    private static void speedLines(GuiGraphics g, int width, int height, float t, int alpha) {
        if (alpha <= 4) return;
        PoseStack pose = g.pose();
        float diag = (float) Math.sqrt(width * width + height * height) / 2;
        int seed = (int) t * 7919;
        for (int i = 0; i < 28; i++) {
            int hsh = (seed + i * 104729) * 0x9E3779B1;
            float ang = (hsh >>> 8 & 0xFFFF) / 65535f * 360f;
            float r0 = diag * (0.42f + ((hsh >>> 4) & 0xFF) / 255f * 0.25f);
            pose.pushPose();
            pose.translate(width / 2f, height / 2f, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(ang));
            g.fill(Math.round(r0), 0, Math.round(diag + 4), 1 + (i % 3 == 0 ? 1 : 0), (alpha << 24) | 0xFFFFFF);
            pose.popPose();
        }
    }
}
