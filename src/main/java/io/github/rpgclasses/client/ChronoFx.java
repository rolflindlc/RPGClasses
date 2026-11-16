package io.github.rpgclasses.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.rpgclasses.network.Packets;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Cliente del Cronomante (Parada Temporal).
 * <ul>
 *   <li><b>Esfera:</b> se dibuja como una cáscara gris translúcida, sin postprocesado, para que funcione también con
 *       shaders (Iris/Oculus). Desde fuera se dibuja su cara exterior (lo de dentro se ve gris) y desde dentro su cara
 *       interior (lo de fuera se ve gris).</li>
 *   <li><b>Jugador paralizado:</b> pantalla gris (capa translúcida bajo el HUD), tercera persona forzada, tictac rápido y
 *       sin poder moverse ni usar nada. Hay un seguro propio: el efecto acaba solo por tiempo aunque se pierda el paquete
 *       de fin, y también al desconectarse, reaparecer o cambiar de mundo.</li>
 * </ul>
 */
public final class ChronoFx {
    private static final float GREY = 0.55f, SHELL_ALPHA = 0.38f;
    private static final int LAT = 20, LON = 32, FADE_TICKS = 3;
    /** Pantalla del paralizado: gris translúcido (ARGB). */
    private static final int FROZEN_OVERLAY = 0x99808286;
    /** Ticks extra del seguro del cliente por si no llega el paquete de fin. */
    private static final int SAFETY_TICKS = 20;

    private record Sphere(Vec3 center, float radius, long start, long end) {}

    private static final Map<Integer, Sphere> SPHERES = new HashMap<>();

    /** Paralizado: ticks de cliente restantes (0 = libre) y la cámara que tenía antes. */
    private static int frozenTicks;
    private static int tickCounter;
    private static CameraType previousCamera;
    private static ClientLevel frozenLevel;

    private ChronoFx() {}

    public static void register(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.addListener(ChronoFx::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener(ChronoFx::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ChronoFx::onMovementInput);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ChronoFx::onInteractionKey);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> reset());
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.Clone event) -> endFrozen());
        modBus.addListener((RegisterGuiOverlaysEvent event) -> event.registerBelowAll("time_frozen", ChronoFx::renderOverlay));
    }

    // ------------------------------------------------------------------ paquetes

    public static void onSphere(Packets.TimeSphere msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (msg.ticks() <= 0) {
            SPHERES.remove(msg.id());
            return;
        }
        long now = mc.level.getGameTime();
        SPHERES.put(msg.id(), new Sphere(new Vec3(msg.x(), msg.y(), msg.z()), msg.radius(), now, now + msg.ticks()));
    }

    public static void onFrozen(int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (ticks <= 0) {
            endFrozen();
            return;
        }
        if (frozenTicks <= 0) {
            previousCamera = mc.options.getCameraType();
            tickCounter = 0;
        }
        frozenTicks = ticks + SAFETY_TICKS;
        frozenLevel = mc.level;
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    public static boolean frozen() {
        return frozenTicks > 0;
    }

    private static void endFrozen() {
        if (frozenTicks <= 0) return;
        frozenTicks = 0;
        frozenLevel = null;
        Minecraft mc = Minecraft.getInstance();
        if (previousCamera != null) mc.options.setCameraType(previousCamera);
        previousCamera = null;
    }

    private static void reset() {
        SPHERES.clear();
        endFrozen();
    }

    // ------------------------------------------------------------------ tick, entrada y sonido

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            reset();
            return;
        }
        if (!SPHERES.isEmpty()) {
            long now = mc.level.getGameTime();
            SPHERES.values().removeIf(s -> now >= s.end() || now < s.start() - 40);
        }
        if (frozenTicks <= 0) return;
        if (mc.level != frozenLevel || mc.player == null || !mc.player.isAlive()) {
            endFrozen();
            return;
        }
        frozenTicks--;
        if (frozenTicks <= 0) {
            endFrozen();
            return;
        }
        // Tercera persona mientras dure: si pulsa F5 se vuelve a poner.
        if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        // Tictac muy rápido (unos 8 por segundo: dos golpes cada 5 ticks).
        int phase = tickCounter++ % 5;
        if (phase == 0 || phase == 2) {
            var p = mc.player;
            mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.MASTER,
                    0.7f, phase == 0 ? 1.9f : 1.45f, false);
        }
    }

    private static void onMovementInput(MovementInputUpdateEvent event) {
        if (frozenTicks <= 0) return;
        var in = event.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    private static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (frozenTicks <= 0) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    // ------------------------------------------------------------------ dibujo

    private static void renderOverlay(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        if (frozenTicks > 0) g.fill(0, 0, width, height, FROZEN_OVERLAY);
    }

    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SPHERES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        float now = mc.level.getGameTime() + event.getPartialTick();
        PoseStack pose = event.getPoseStack();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        for (Sphere s : SPHERES.values()) {
            float fade = Mth.clamp(Math.min(now - s.start(), s.end() - now) / FADE_TICKS, 0f, 1f);
            if (fade <= 0) continue;
            boolean inside = cam.distanceToSqr(s.center()) < (double) s.radius() * s.radius();
            pose.pushPose();
            pose.translate(s.center().x - cam.x, s.center().y - cam.y, s.center().z - cam.z);
            drawShell(pose.last().pose(), s.radius(), inside, SHELL_ALPHA * fade);
            pose.popPose();
        }
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /**
     * Cáscara esférica de quads. Con el culling activo solo se ve una cara: el orden A-B-C-D (θ hacia abajo, φ hacia
     * +z) mira hacia dentro, así que desde dentro se usa tal cual y desde fuera al revés.
     */
    private static void drawShell(Matrix4f m, float r, boolean inside, float alpha) {
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < LAT; i++) {
            float t0 = Mth.PI * i / LAT, t1 = Mth.PI * (i + 1) / LAT;
            for (int j = 0; j < LON; j++) {
                float p0 = Mth.TWO_PI * j / LON, p1 = Mth.TWO_PI * (j + 1) / LON;
                if (inside) {
                    vertex(bb, m, r, t0, p0, alpha);
                    vertex(bb, m, r, t1, p0, alpha);
                    vertex(bb, m, r, t1, p1, alpha);
                    vertex(bb, m, r, t0, p1, alpha);
                } else {
                    vertex(bb, m, r, t0, p0, alpha);
                    vertex(bb, m, r, t0, p1, alpha);
                    vertex(bb, m, r, t1, p1, alpha);
                    vertex(bb, m, r, t1, p0, alpha);
                }
            }
        }
        BufferUploader.drawWithShader(bb.end());
    }

    private static void vertex(BufferBuilder bb, Matrix4f m, float r, float theta, float phi, float alpha) {
        float sin = Mth.sin(theta);
        bb.vertex(m, r * sin * Mth.cos(phi), r * Mth.cos(theta), r * sin * Mth.sin(phi))
                .color(GREY, GREY, GREY + 0.03f, alpha).endVertex();
    }
}
