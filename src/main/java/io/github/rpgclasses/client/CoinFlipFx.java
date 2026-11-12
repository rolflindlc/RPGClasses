package io.github.rpgclasses.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.network.Packets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Apuesta con la Muerte: la moneda (anj / calavera) sobre la cabeza del Bárbaro.
 * <ul>
 *   <li>Fase de lanzamiento: la moneda sube y baja girando y cae en la cara que decidió el servidor.</li>
 *   <li>Fase de resultado: la cara ganadora, en grande, sobre la cabeza; se desvanece al final.</li>
 * </ul>
 * Los demás jugadores la ven en el mundo; el propio jugador también en tercera persona, y en primera persona
 * en la parte alta de la pantalla. Mientras gira, el jugador local no puede moverse, atacar ni usar nada.
 */
public final class CoinFlipFx {
    public static final ResourceLocation ANKH = RpgClasses.id("textures/gui/coin_ankh.png");
    public static final ResourceLocation SKULL = RpgClasses.id("textures/gui/coin_skull.png");

    private record Anim(boolean win, long start, int flip, int show) {
        long end() {
            return start + flip + show;
        }
    }

    private static final Map<Integer, Anim> ACTIVE = new HashMap<>();

    private CoinFlipFx() {}

    public static void start(Packets.CoinFlip msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ACTIVE.put(msg.entityId(), new Anim(msg.win(), mc.level.getGameTime(), msg.flipTicks(), msg.showTicks()));
    }

    public static void reset() {
        ACTIVE.clear();
    }

    /** ¿El jugador local tiene la moneda en el aire? (no puede hacer nada). */
    public static boolean localFlipping() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        Anim a = ACTIVE.get(mc.player.getId());
        return a != null && mc.level.getGameTime() < a.start + a.flip;
    }

    // ------------------------------------------------------------------ Estado de la animación

    /** Fotograma: cara visible, escala vertical (giro), altura extra (0..1), tamaño relativo y opacidad. */
    private record Frame(ResourceLocation face, float squash, float lift, float size, float alpha) {}

    private static Frame frame(Anim a, float time) {
        float t = time - a.start;
        if (t < a.flip) {
            float p = Mth.clamp(t / a.flip, 0, 1);
            float eased = 1 - (1 - p) * (1 - p) * (1 - p);
            // Empieza enseñando el anj: un número par de medias vueltas acaba en el anj, uno impar en la calavera.
            int halfTurns = a.win ? 10 : 11;
            double angle = Math.PI * halfTurns * eased;
            float cos = (float) Math.cos(angle);
            ResourceLocation face = cos >= 0 ? ANKH : SKULL;
            float squash = Math.max(0.08f, Math.abs(cos));
            float lift = 4 * p * (1 - p); // parábola: sube y vuelve a caer
            return new Frame(face, squash, lift, 1f, 1f);
        }
        float s = t - a.flip;
        float pop = s < 4 ? 1.0f + 0.6f * (s / 4f) : 1.6f - 0.15f * Math.min(1f, (s - 4) / 4f);
        float alpha = s > a.show - 6 ? Math.max(0, (a.show - s) / 6f) : 1f;
        return new Frame(a.win ? ANKH : SKULL, 1f, 0f, pop, alpha);
    }

    // ------------------------------------------------------------------ Mundo

    /** Sobre la cabeza de cualquier jugador con la moneda activa (en tercera persona también el propio). */
    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        Anim a = ACTIVE.get(player.getId());
        if (a == null) return;
        Minecraft mc = Minecraft.getInstance();
        float time = player.level().getGameTime() + event.getPartialTick();
        if (time >= a.end()) return;
        Frame f = frame(a, time);

        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(0, player.getBbHeight() + 0.85 + f.lift() * 1.1, 0);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float half = 0.3f * f.size();
        Matrix4f pose = ps.last().pose();
        // Como las etiquetas de nombre: tipo "text" (sin sombreado, translúcido) y a doble cara.
        VertexConsumer vc = event.getMultiBufferSource().getBuffer(RenderType.text(f.face()));
        int light = 0xF000F0; // brilla aunque sea de noche
        int alpha = Math.round(255 * f.alpha());
        float h = half * f.squash();
        // La derecha de la pantalla es -x en este espacio: u=0 a la izquierda (x=+half).
        vertex(vc, pose, half, -h, 0, 1, light, alpha);
        vertex(vc, pose, -half, -h, 1, 1, light, alpha);
        vertex(vc, pose, -half, h, 1, 0, light, alpha);
        vertex(vc, pose, half, h, 0, 0, light, alpha);
        vertex(vc, pose, half, h, 0, 0, light, alpha);
        vertex(vc, pose, -half, h, 1, 0, light, alpha);
        vertex(vc, pose, -half, -h, 1, 1, light, alpha);
        vertex(vc, pose, half, -h, 0, 1, light, alpha);
        ps.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, float x, float y, float u, float v, int light, int alpha) {
        vc.vertex(pose, x, y, 0).color(255, 255, 255, alpha).uv(u, v).uv2(light).endVertex();
    }

    // ------------------------------------------------------------------ Pantalla (primera persona)

    public static void renderHud(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || !mc.options.getCameraType().isFirstPerson()) return;
        Anim a = ACTIVE.get(mc.player.getId());
        if (a == null) return;
        float time = mc.level.getGameTime() + partialTick;
        if (time >= a.end()) return;
        Frame f = frame(a, time);
        float size = 40 * f.size();
        float cx = width / 2f;
        float cy = height * 0.26f - f.lift() * 36;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(size / 64f, size / 64f * f.squash(), 1);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1, 1, 1, f.alpha());
        g.blit(f.face(), -32, -32, 0, 0, 64, 64, 64, 64);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ Bloqueo y limpieza

    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!localFlipping()) return;
        var in = event.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!localFlipping()) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        long now = mc.level.getGameTime();
        ACTIVE.values().removeIf(a -> now >= a.end());
    }
}
