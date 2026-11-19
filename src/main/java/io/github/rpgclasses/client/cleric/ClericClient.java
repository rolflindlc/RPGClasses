package io.github.rpgclasses.client.cleric;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.rpgclasses.cleric.Cleric;
import io.github.rpgclasses.client.AbilityHud;
import io.github.rpgclasses.client.ClientHandlers;
import io.github.rpgclasses.client.ClientSetup;
import io.github.rpgclasses.core.ability.UnlockResolver;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import io.github.rpgclasses.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente del Clérigo.
 * <ul>
 *   <li><b>Ojo del Sanador:</b> vida de los aliados (jugadores y mascotas) a 32 bloques sobre sus cabezas (se ve a través de las paredes) y brillo
 *       (contorno) de los que bajan del 40 %. Los tumbados muestran su cuenta atrás. Solo lo ve el Clérigo.</li>
 *   <li><b>Tumbado (Milagro):</b> el jugador se dibuja acostado (postura de dormir sin cama), no puede moverse ni
 *       actuar, y su pantalla muestra la cuenta atrás.</li>
 *   <li><b>Santuario:</b> esfera dorada translúcida, sin postprocesado (funciona con shaders), que sigue a su ancla.
 *       Al soltar la tecla de la ranura del Santuario se avisa al servidor.</li>
 *   <li><b>Plegaria:</b> la cámara pasa al ángel que orbita al aliado ({@code mixin.client.PrayerCameraMixin}); el ratón
 *       la gira y el cuerpo queda quieto y agachado. La lista de aliados es {@link PrayerScreen}.</li>
 *   <li><b>Ángel:</b> sin correr en el aire durante la Forma Angelical; caída lenta con Gracia Alada (salto pulsado).</li>
 * </ul>
 */
public final class ClericClient {
    private static final int SAFETY_TICKS = 20;
    /** Tumbados: id de entidad → tick (del cliente) en que acaba. */
    private static final Map<Integer, Long> DOWNED = new HashMap<>();

    private record Sphere(int anchorId, float radius, long start, int expandTicks, long end) {}

    private static final Map<Integer, Sphere> SPHERES = new HashMap<>();
    private static int prayerTarget = -1;
    private static float prayerOrbit = 3;
    private static boolean healerEye;
    private static final boolean[] KEY_DOWN = new boolean[3];
    /** Jugador que se está dibujando acostado y su pose real. */
    private static Player posed;
    private static Pose savedPose;

    private ClericClient() {}

    public static void register(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.addListener(ClericClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ClericClient::onMovementInput);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ClericClient::onInteractionKey);
        MinecraftForge.EVENT_BUS.addListener(ClericClient::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener(ClericClient::onRenderLiving);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, ClericClient::onRenderPlayerPre);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ClericClient::onRenderPlayerPost);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> reset());
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.Clone event) -> {
            prayerTarget = -1;
            DOWNED.clear();
        });
        modBus.addListener((RegisterGuiOverlaysEvent event) -> event.registerAboveAll("cleric_downed", ClericClient::renderHud));
        // Rezando, la cámara está en el ángel: ni la mano en primera persona ni el contorno del bloque al que mira el cuerpo.
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RenderHandEvent event) -> {
            if (prayerTarget >= 0) event.setCanceled(true);
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RenderHighlightEvent.Block event) -> {
            if (prayerTarget >= 0) event.setCanceled(true);
        });
    }

    private static void reset() {
        DOWNED.clear();
        SPHERES.clear();
        prayerTarget = -1;
        healerEye = false;
    }

    // ------------------------------------------------------------------ paquetes

    public static void onDowned(Packets.ClericDowned msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (msg.ticks() <= 0) DOWNED.remove(msg.entityId());
        else DOWNED.put(msg.entityId(), mc.level.getGameTime() + msg.ticks());
    }

    public static void onSanctuary(Packets.SanctuarySync msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (msg.ticks() <= 0) {
            SPHERES.remove(msg.id());
            return;
        }
        long now = mc.level.getGameTime();
        Sphere old = SPHERES.get(msg.id());
        long start = old != null ? old.start() : now;
        SPHERES.put(msg.id(), new Sphere(msg.anchorId(), msg.radius(), start, Math.max(1, msg.expandTicks()), now + msg.ticks() + SAFETY_TICKS));
    }

    public static void onPrayerState(Packets.PrayerState msg) {
        prayerTarget = msg.targetId();
        prayerOrbit = msg.orbit() <= 0 ? 3 : msg.orbit();
        Minecraft mc = Minecraft.getInstance();
        if (prayerTarget < 0 && mc.screen instanceof PrayerScreen) mc.setScreen(null);
    }

    public static void onPrayerList(Packets.PrayerList msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null || mc.screen instanceof PrayerScreen) mc.setScreen(new PrayerScreen(msg));
    }

    // ------------------------------------------------------------------ estado

    public static boolean downed(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (e == null || mc.level == null) return false;
        Long end = DOWNED.get(e.getId());
        return end != null && mc.level.getGameTime() < end;
    }

    private static int downedSeconds(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        Long end = DOWNED.get(e.getId());
        return end == null || mc.level == null ? 0 : (int) Math.ceil(Math.max(0, end - mc.level.getGameTime()) / 20.0);
    }

    public static boolean praying() {
        return prayerTarget >= 0;
    }

    /** Contorno brillante (Ojo del Sanador): aliado a 32 bloques por debajo del 40 % de vida. */
    public static boolean glows(Entity e) {
        if (!healerEye || !(e instanceof LivingEntity p) || p == Minecraft.getInstance().player) return false;
        if (!ClientHandlers.isAllyEntity(p)) return false;
        Minecraft mc = Minecraft.getInstance();
        double range = number(Cleric.HEALER_EYE, "range", 32);
        if (mc.player == null || p.distanceToSqr(mc.player) > range * range) return false;
        return downed(p) || p.getHealth() < p.getMaxHealth() * number(Cleric.HEALER_EYE, "low_health", 0.4);
    }

    private static double number(String ability, String key, double fallback) {
        var def = DefinitionStore.CLIENT.abilityDef(ability);
        return def == null || !def.params().has(key) ? fallback : def.params().get(key).getAsDouble();
    }

    private static boolean localHas(String ability) {
        var s = ClientHandlers.LOCAL_DATA.state;
        var c = DefinitionStore.CLIENT.classDef(s.classId);
        return c != null && UnlockResolver.unlocked(s, c, DefinitionStore.CLIENT.subclassDef(s.subclassId)).contains(ability);
    }

    // ------------------------------------------------------------------ cámara de la Plegaria (mixin)

    /** Coloca la cámara en el ángel: detrás del aliado según la mirada, sin atravesar bloques. Devuelve false si no reza. */
    public static Vec3 prayerCamera(Camera camera, float partialTick) {
        if (prayerTarget < 0) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        Entity target = mc.level.getEntity(prayerTarget);
        if (target == null) return null;
        Vec3 center = target.getEyePosition(partialTick);
        Vec3 look = Vec3.directionFromRotation(camera.getXRot(), camera.getYRot());
        Vec3 pos = center.subtract(look.scale(prayerOrbit));
        BlockHitResult hit = mc.level.clip(new ClipContext(center, pos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.MISS) pos = hit.getLocation().add(look.scale(0.25));
        return pos;
    }

    // ------------------------------------------------------------------ tick y entrada

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }
        long now = mc.level.getGameTime();
        DOWNED.values().removeIf(end -> now >= end + SAFETY_TICKS);
        SPHERES.values().removeIf(s -> now >= s.end());
        healerEye = localHas(Cleric.HEALER_EYE);

        // Santuario: avisar al soltar su tecla.
        List<String> actives = AbilityHud.localActives();
        for (int i = 0; i < KEY_DOWN.length && i < ClientSetup.ABILITY_KEYS.length; i++) {
            boolean down = ClientSetup.ABILITY_KEYS[i].isDown();
            if (KEY_DOWN[i] && !down && i < actives.size() && Cleric.SANCTUARY.equals(actives.get(i))) {
                Net.CHANNEL.sendToServer(new Packets.AbilityRelease(i));
            }
            KEY_DOWN[i] = down;
        }

        // Forma Angelical: vuela a paso de caminar (sin correr en el aire).
        Player p = mc.player;
        if (p.hasEffect(ModEffects.ANGELIC_FORM.get()) && p.getAbilities().flying && p.isSprinting()) p.setSprinting(false);
        // Gracia Alada: caída lenta manteniendo el salto desde el nivel indicado.
        var grace = ClientSetup.localMechanic(io.github.rpgclasses.core.Ids.NS + ":" + Cleric.WINGED_GRACE);
        if (grace != null && grace.level() >= grace.number("slow_fall_level", 5) && mc.screen == null) {
            io.github.rpgclasses.content.MovementPerks.featherFallTick(p, mc.options.keyJump.isDown());
        }
    }

    private static void onMovementInput(MovementInputUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        boolean down = downed(event.getEntity());
        if (!down && prayerTarget < 0) return;
        var in = event.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = !down; // rezando: agachado
    }

    private static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (downed(mc.player) || (prayerTarget >= 0 && (event.isAttack() || event.isPickBlock()))) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    // ------------------------------------------------------------------ dibujo: tumbados

    private static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (event.isCanceled() || !downed(event.getEntity())) return;
        Player p = event.getEntity();
        if (posed != null && posed != p) restorePose();
        if (p.getPose() == Pose.SLEEPING) return;
        posed = p;
        savedPose = p.getPose();
        p.setPose(Pose.SLEEPING);
    }

    private static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (posed == event.getEntity()) restorePose();
    }

    private static void restorePose() {
        if (posed != null && savedPose != null) posed.setPose(savedPose);
        posed = null;
        savedPose = null;
    }

    // ------------------------------------------------------------------ dibujo: Ojo del Sanador

    private static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        if (!healerEye) return;
        LivingEntity target = event.getEntity();
        Minecraft mc = Minecraft.getInstance();
        if (target == mc.player || mc.player == null || target.isInvisible() || !ClientHandlers.isAllyEntity(target)) return;
        double range = number(Cleric.HEALER_EYE, "range", 32);
        if (target.distanceToSqr(mc.player) > range * range) return;
        MutableComponent line;
        if (downed(target)) {
            int secs = downedSeconds(target);
            boolean blink = (mc.level.getGameTime() / 5) % 2 == 0;
            line = Component.literal("✝ " + secs + " s").withStyle(blink ? ChatFormatting.GOLD : ChatFormatting.YELLOW);
        } else {
            float frac = Mth.clamp(target.getHealth() / Math.max(1, target.getMaxHealth()), 0, 1);
            int filled = Math.round(frac * 20);
            ChatFormatting color = frac < number(Cleric.HEALER_EYE, "low_health", 0.4) ? ChatFormatting.RED
                    : frac < 0.7f ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
            line = Component.literal("|".repeat(filled)).withStyle(color)
                    .append(Component.literal("|".repeat(20 - filled)).withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(" " + Math.round(target.getHealth()) + "/" + Math.round(target.getMaxHealth())).withStyle(ChatFormatting.WHITE));
        }
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(0, target.getBbHeight() + 0.8, 0);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(-0.025f, -0.025f, 0.025f);
        Matrix4f m = ps.last().pose();
        Font font = mc.font;
        float x = -font.width(line) / 2f;
        int bg = (int) (mc.options.getBackgroundOpacity(0.25f) * 255.0f) << 24;
        font.drawInBatch(line, x, 0, 0x20FFFFFF, false, m, event.getMultiBufferSource(), Font.DisplayMode.SEE_THROUGH, bg, event.getPackedLight());
        font.drawInBatch(line, x, 0, 0xFFFFFFFF, false, m, event.getMultiBufferSource(), Font.DisplayMode.NORMAL, 0, event.getPackedLight());
        ps.popPose();
    }

    // ------------------------------------------------------------------ dibujo: Santuario

    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SPHERES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        float now = mc.level.getGameTime() + partial;
        PoseStack pose = event.getPoseStack();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        for (Sphere s : SPHERES.values()) {
            Entity anchor = mc.level.getEntity(s.anchorId());
            if (anchor == null) continue;
            float grow = Mth.clamp((now - s.start()) / s.expandTicks(), 0.05f, 1f);
            float fade = Mth.clamp((s.end() - SAFETY_TICKS - now) / 4f, 0f, 1f);
            if (fade <= 0) continue;
            float r = s.radius() * grow;
            Vec3 c = anchor.getPosition(partial).add(0, anchor.getBbHeight() / 2, 0);
            boolean inside = cam.distanceToSqr(c) < (double) r * r;
            float pulse = 0.85f + 0.15f * Mth.sin(now * 0.25f);
            pose.pushPose();
            pose.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
            drawShell(pose.last().pose(), r, inside, 0.22f * fade * pulse);
            pose.popPose();
        }
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static final int LAT = 16, LON = 28;

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
        // Dorado, más claro hacia arriba.
        float up = 0.5f + 0.5f * Mth.cos(theta);
        bb.vertex(m, r * sin * Mth.cos(phi), r * Mth.cos(theta), r * sin * Mth.sin(phi))
                .color(1.0f, 0.82f + 0.12f * up, 0.35f + 0.4f * up, alpha).endVertex();
    }

    // ------------------------------------------------------------------ HUD

    private static void renderHud(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (downed(mc.player)) {
            float t = (mc.level.getGameTime() + partialTick) * 0.15f;
            int alpha = (int) (60 + 30 * Mth.sin(t));
            g.fill(0, 0, width, height, (alpha << 24) | 0x302010);
            Component text = Component.translatable("hud.rites_and_paths.downed", downedSeconds(mc.player));
            g.drawCenteredString(mc.font, text, width / 2, height / 2 - 30, 0xFFE6C35A);
        }
    }

    /** Fila del Sacrificio en el HUD de habilidades: roja y palpitante cuando pagar el coste te mataría. */
    public static boolean sacrificeFinal() {
        Player p = Minecraft.getInstance().player;
        if (p == null) return false;
        return p.getHealth() <= p.getMaxHealth() * number(Cleric.SACRIFICE, "cost_fraction", 0.15);
    }

    /** Ancho de la esfera más cercana (depuración). */
    public static int spheres() {
        return SPHERES.size();
    }

    static LivingEntity entity(int id) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getEntity(id) instanceof LivingEntity l ? l : null;
    }
}
