package io.github.rpgclasses.client.spin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.rpgclasses.client.ClientHandlers;
import io.github.rpgclasses.config.ClientConfig;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import io.github.rpgclasses.spin.Bandolier;
import io.github.rpgclasses.spin.SphereEntity;
import io.github.rpgclasses.spin.SphereMaterial;
import io.github.rpgclasses.spin.SpinFxSender;
import io.github.rpgclasses.spin.SpinMaster;
import io.github.rpgclasses.spin.SpinRegistry;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Cliente del Maestro del Giro (adenda, secciones 3, 4, 7 y 10).
 * <ul>
 *   <li><b>Entrada:</b> con la bandolera en la mano, el clic izquierdo lanza directo y el derecho curvo (no atacan ni usan;
 *       con el derecho sobre puertas, cofres, palancas, aldeanos... se sigue usando lo apuntado). Tecla para cambiar de bola.</li>
 *   <li><b>HUD:</b> barra de Giro con marcas en el 33 y el 66 %, la bola elegida y sus bolas listas, y el Giro Dorado.</li>
 *   <li><b>Efectos:</b> temblor en los impactos, onomatopeyas flotantes, destello de la Captura Perfecta y cámara
 *       cinemática de la Espiral Final (bandas, zoom y tercera persona) para el lanzador y los golpeados. Todo son capas
 *       de interfaz o geometría: funciona con shaders.</li>
 * </ul>
 */
public final class SpinClient {
    public static final KeyMapping CYCLE = new KeyMapping("key.rites_and_paths.cycle_sphere", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G, "key.categories.rites_and_paths");
    /** Onomatopeyas (índices de SpinFxSender.WORD_*): no se traducen, son sonidos. */
    static final String[] WORDS = {"GYARURURU!", "BAKYUN!", "DOGOOON!", "GOGOGOGO", "PASHI!"};
    private static final int[] WORD_COLORS = {0xFFE8B83A, 0xFFFF5A3C, 0xFFFFD84A, 0xFFB070FF, 0xFFFFFFFF};

    private static float spin;
    private static int goldenTicks, spiral, maxSpiral;
    private static boolean active;
    private static long lastSend;

    // Efectos.
    private static float trauma;
    private static int cinematicTicks, cinematicTotal;
    private static int hitFlashTicks, hitFlashTotal;
    private static int perfectTicks;
    private static CameraType previousCamera;

    private record Word(Vec3 pos, String text, int color, int life, int[] age) {}

    private static final List<Word> FLOATING = new ArrayList<>();

    private SpinClient() {}

    public static float spin() {
        return active ? spin : 0;
    }

    public static boolean golden() {
        return active && goldenTicks > 0;
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(CYCLE));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerEntityRenderer(SpinRegistry.SPHERE_ENTITY.get(), SphereRenderer::new));
        modBus.addListener((ModelEvent.RegisterAdditional event) -> event.register(SpinItemRenderer.BANDOLIER_ICON));
        modBus.addListener((RegisterGuiOverlaysEvent event) -> {
            event.registerBelowAll("spin_cinematic", SpinClient::renderCinematic);
            event.registerAbove(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.id(), "spin_bar", SpinClient::renderHud);
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, SpinClient::onMouse);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, SpinClient::onInteractionKey);
        MinecraftForge.EVENT_BUS.addListener(SpinClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(SpinClient::onCamera);
        MinecraftForge.EVENT_BUS.addListener(SpinClient::onFov);
        MinecraftForge.EVENT_BUS.addListener(SpinClient::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> reset());
    }

    private static void reset() {
        spin = 0;
        goldenTicks = spiral = 0;
        active = false;
        trauma = 0;
        hitFlashTicks = perfectTicks = 0;
        endCinematic();
        FLOATING.clear();
    }

    // ------------------------------------------------------------------ paquetes

    public static void onSync(Packets.SpinSync msg) {
        spin = Mth.clamp(msg.spin(), 0f, 1f);
        goldenTicks = msg.goldenTicks();
        spiral = msg.spiral();
        maxSpiral = msg.maxSpiral();
        active = msg.active();
    }

    public static void onFx(Packets.SpinFx msg) {
        Minecraft mc = Minecraft.getInstance();
        switch (msg.kind()) {
            case SpinFxSender.SHAKE -> trauma = Mth.clamp(trauma + msg.value() * (float) ClientConfig.spinShake(), 0f, 1f);
            case SpinFxSender.WORD -> {
                int i = Mth.clamp((int) msg.value(), 0, WORDS.length - 1);
                if (FLOATING.size() < 24) FLOATING.add(new Word(new Vec3(msg.x(), msg.y(), msg.z()), WORDS[i], WORD_COLORS[i], 30, new int[1]));
            }
            case SpinFxSender.CINEMATIC -> {
                if (!ClientConfig.spinCinematic()) return;
                if (cinematicTicks <= 0) previousCamera = mc.options.getCameraType();
                cinematicTicks = cinematicTotal = Math.max(1, (int) msg.value());
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            case SpinFxSender.CINEMATIC_HIT -> {
                hitFlashTicks = hitFlashTotal = Math.max(1, (int) msg.value());
                trauma = Mth.clamp(trauma + 0.6f * (float) ClientConfig.spinShake(), 0f, 1f);
            }
            case SpinFxSender.PERFECT -> perfectTicks = 6;
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ entrada

    private static boolean localSpinMaster() {
        return SpinMaster.CLASS.equals(ClientHandlers.LOCAL_DATA.state.classId);
    }

    private static boolean holding(LocalPlayer p) {
        return Bandolier.is(p.getMainHandItem());
    }

    /** Clic de ratón: con la bandolera en la mano, lanzar en vez de atacar o usar (antes de que lo vean vanilla y Epic Fight). */
    private static void onMouse(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (mc.screen != null || p == null || p.isSpectator() || !holding(p) || !localSpinMaster()) return;
        var o = mc.options;
        if (o.keyAttack.matchesMouse(event.getButton())) {
            send(mc, 0);
            event.setCanceled(true);
        } else if (o.keyUse.matchesMouse(event.getButton())) {
            if (useTargetsSomething(mc)) return;
            send(mc, 1);
            event.setCanceled(true);
        }
    }

    /** Ataque o uso con teclas del teclado (el ratón ya lo ha cogido onMouse). */
    private static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || event.getHand() != InteractionHand.MAIN_HAND || !holding(p) || !localSpinMaster()) return;
        if (event.isAttack()) {
            send(mc, 0);
        } else if (event.isUseItem()) {
            if (useTargetsSomething(mc)) return;
            send(mc, 1);
        } else {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    private static void send(Minecraft mc, int kind) {
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        if (now - lastSend < 2 && now >= lastSend) return;
        lastSend = now;
        Net.CHANNEL.sendToServer(new Packets.SphereThrow(kind, mc.player.isShiftKeyDown()));
    }

    /**
     * El clic derecho apunta a algo que se usa (puertas, contenedores, palancas, aldeanos, monturas...) o hay una mina
     * propia cerca (se recoge con el clic: eso sí va al servidor).
     */
    private static boolean useTargetsSomething(Minecraft mc) {
        LocalPlayer p = mc.player;
        AABB near = p.getBoundingBox().inflate(3.5);
        if (!p.level().getEntitiesOfClass(SphereEntity.class, near, s -> s.state() == SphereEntity.MINE && s.getOwner() == p).isEmpty()) {
            return false;
        }
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) {
            BlockState state = p.level().getBlockState(b.getBlockPos());
            if (p.isShiftKeyDown()) return false;
            return state.hasBlockEntity() || state.getMenuProvider(p.level(), b.getBlockPos()) != null
                    || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES)
                    || state.is(BlockTags.BUTTONS) || state.is(BlockTags.BEDS) || state.getBlock() instanceof LeverBlock;
        }
        if (hit instanceof EntityHitResult eh) {
            Entity e = eh.getEntity();
            return e instanceof AbstractVillager || e instanceof AbstractHorse || e instanceof Boat || e instanceof AbstractMinecart
                    || e instanceof ItemFrame || e instanceof ArmorStand;
        }
        return false;
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (CYCLE.consumeClick()) {
            if (mc.screen == null && mc.player != null && holding(mc.player)) Net.CHANNEL.sendToServer(new Packets.SphereCycle());
        }
        if (trauma > 0) trauma = Math.max(0f, trauma - 0.06f);
        if (goldenTicks > 0) goldenTicks--;
        if (hitFlashTicks > 0) hitFlashTicks--;
        if (perfectTicks > 0) perfectTicks--;
        if (cinematicTicks > 0) {
            cinematicTicks--;
            if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK && cinematicTicks > 0) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            if (cinematicTicks == 0) endCinematic();
        }
        Iterator<Word> it = FLOATING.iterator();
        while (it.hasNext()) {
            Word w = it.next();
            if (++w.age()[0] >= w.life()) it.remove();
        }
    }

    private static void endCinematic() {
        cinematicTicks = 0;
        Minecraft mc = Minecraft.getInstance();
        if (previousCamera != null && mc.options != null) mc.options.setCameraType(previousCamera);
        previousCamera = null;
    }

    // ------------------------------------------------------------------ cámara

    private static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = (float) event.getPartialTick();
        float t = (mc.level.getGameTime() + pt) * 1.9f;
        if (trauma > 0) {
            float s = trauma * trauma;
            event.setPitch(event.getPitch() + s * 3.0f * (Mth.sin(t) * 0.6f + Mth.sin(t * 2.7f) * 0.4f));
            event.setYaw(event.getYaw() + s * 2.2f * (Mth.sin(t + 13) * 0.6f + Mth.sin(t * 3.1f) * 0.4f));
            event.setRoll(event.getRoll() + s * 2.5f * Mth.sin(t * 1.7f + 5));
        }
        if (cinematicTicks > 0) {
            // Giro lento de la cámara alrededor de la espiral.
            float k = cinematic(pt);
            event.setRoll(event.getRoll() + k * 4f * Mth.sin((mc.level.getGameTime() + pt) * 0.2f));
        }
    }

    private static void onFov(ViewportEvent.ComputeFov event) {
        if (cinematicTicks <= 0 && hitFlashTicks <= 0) return;
        float k = Math.max(cinematic((float) event.getPartialTick()), hitFlashTicks > 0 ? 0.5f * hitFlashTicks / (float) hitFlashTotal : 0f);
        event.setFOV(event.getFOV() * (1 - 0.22 * k));
    }

    /** 0-1: entra rápido y sale suave. */
    private static float cinematic(float pt) {
        if (cinematicTicks <= 0 || cinematicTotal <= 0) return 0;
        float left = Math.max(0, cinematicTicks - pt);
        float done = cinematicTotal - left;
        return Mth.clamp(Math.min(done / 5f, left / 8f), 0f, 1f);
    }

    // ------------------------------------------------------------------ HUD

    /** Bandas de cine (lanzador) y destello dorado (golpeados) y de la Captura Perfecta: debajo del resto del HUD. */
    private static void renderCinematic(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        float k = cinematic(partialTick);
        if (k > 0) {
            int bar = (int) (height * 0.11f * k);
            g.fill(0, 0, width, bar, 0xF0000000);
            g.fill(0, height - bar, width, height, 0xF0000000);
            int a = (int) (0x30 * k);
            g.fill(0, bar, width, height - bar, (a << 24) | 0xF2C230);
        }
        if (hitFlashTicks > 0) {
            float f = hitFlashTicks / (float) hitFlashTotal;
            g.fill(0, 0, width, height, ((int) (0x70 * f) << 24) | 0xFFD84A);
        }
        if (perfectTicks > 0) {
            int a = (int) (0x50 * perfectTicks / 6f);
            g.fill(0, 0, width, height, (a << 24) | 0xFFFFFF);
        }
    }

    /** Barra de Giro (con marcas en el 33 y el 66 %) y la bola elegida, sobre la barra de objetos. */
    private static void renderHud(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.level == null || !localSpinMaster()) return;
        Font font = mc.font;
        int w = 100, h = 5;
        int x = width / 2 - w / 2, y = height - 62;
        boolean full = spin >= 0.999f;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xA0000000);
        int fill = Math.round(w * (goldenTicks > 0 ? 1f : spin));
        float pulse = 0.5f + 0.5f * Mth.sin((mc.level.getGameTime() + partialTick) * 0.35f);
        int color = goldenTicks > 0 ? 0xFFFFD84A : full ? lerp(0xFFE8B83A, 0xFFFFF2B0, pulse) : spin >= 0.66f ? 0xFFE8B83A : spin >= 0.33f ? 0xFFC99A3A : 0xFF9A8456;
        g.fill(x, y, x + fill, y + h, color);
        for (float mark : new float[] {0.33f, 0.66f}) {
            int mx = x + Math.round(w * mark);
            g.fill(mx, y - 2, mx + 1, y + h + 2, 0xFFFFFFFF);
        }
        String label;
        if (goldenTicks > 0) {
            label = Component.translatable("hud.rites_and_paths.spin.golden", spiral).getString()
                    + String.format(Locale.ROOT, "  %.0f s", Math.ceil(goldenTicks / 20.0));
        } else if (full) {
            label = Component.translatable("hud.rites_and_paths.spin.ready",
                    io.github.rpgclasses.client.ClientSetup.ABILITY_KEYS[3].getTranslatedKeyMessage()).getString();
        } else {
            label = Component.translatable("hud.rites_and_paths.spin").getString() + " " + Math.round(spin * 100) + "%";
        }
        g.drawCenteredString(font, label, width / 2, y - 10, goldenTicks > 0 || full ? 0xFFFFD84A : 0xFFFFFFFF);
        ItemStack band = mc.player.getMainHandItem();
        if (Bandolier.is(band)) {
            SphereMaterial m = Bandolier.current(band);
            if (m != null) {
                long now = mc.level.getGameTime();
                String s = Component.translatable("hud.rites_and_paths.sphere",
                        Component.translatable("item.rites_and_paths." + m.itemName()), Bandolier.ready(band, m, now), Bandolier.total(band, m)).getString();
                g.drawString(font, s, x + w + 6, y - 2, 0xFF000000 | m.color);
            }
        }
    }

    private static int lerp(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int gg = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bb = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return 0xFF000000 | (r << 16) | (gg << 8) | bb;
    }

    // ------------------------------------------------------------------ onomatopeyas

    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || FLOATING.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        float pt = event.getPartialTick();
        for (Word w : FLOATING) {
            float age = w.age()[0] + pt;
            float life = w.life();
            float scale = 0.035f * (age < 4 ? 0.6f + 0.4f * age / 4f : 1f) * (1.2f - 0.2f * age / life);
            float alpha = Mth.clamp((life - age) / 8f, 0f, 1f);
            if (alpha <= 0.02f) continue;
            int color = ((int) (alpha * 255) << 24) | (w.color() & 0xFFFFFF);
            pose.pushPose();
            pose.translate(w.pos().x - cam.x, w.pos().y - cam.y + age * 0.03, w.pos().z - cam.z);
            pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            pose.scale(-scale, -scale, scale);
            float tilt = (float) Math.toRadians(-8 + (w.text().hashCode() & 7) * 2);
            pose.mulPose(com.mojang.math.Axis.ZP.rotation(tilt));
            float tx = -font.width(w.text()) / 2f;
            font.drawInBatch(w.text(), tx, 0, color, true, pose.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        buffers.endBatch();
    }
}
