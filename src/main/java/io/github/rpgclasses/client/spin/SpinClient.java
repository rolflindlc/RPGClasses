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
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
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
 *   <li><b>Entrada:</b> con la bandolera en la mano, el clic izquierdo lanza guiado y el derecho, al soltarlo, curvo con la
 *       carga que haya acumulado (no atacan ni usan; con el derecho sobre puertas, cofres, palancas, aldeanos... se sigue
 *       usando lo apuntado). Tecla para cambiar de bola. Durante el Rectángulo Dorado no se puede hacer nada.</li>
 *   <li><b>HUD:</b> barra de Giro con marcas en el 33 y el 66 %, la carga del clic derecho, la bola elegida y sus bolas
 *       listas. El aviso del Rectángulo Dorado solo sale con la ulti desbloqueada (nivel 30).</li>
 *   <li><b>Efectos:</b> temblor en los impactos, onomatopeyas flotantes, destello de la Captura Perfecta y de los golpeados
 *       por la ulti, y la cámara de cine del Rectángulo Dorado ({@link SpinCinematic}). Todo son capas de interfaz,
 *       geometría o la propia cámara: funciona con shaders.</li>
 * </ul>
 */
public final class SpinClient {
    public static final KeyMapping CYCLE = new KeyMapping("key.rites_and_paths.cycle_sphere", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G, "key.categories.rites_and_paths");
    /** Onomatopeyas (índices de SpinFxSender.WORD_*): no se traducen, son sonidos. */
    static final String[] WORDS = {"GYARURURU!", "BAKYUN!", "DOGOOON!", "GOGOGOGO", "PASHI!"};
    private static final int[] WORD_COLORS = {0xFFE8B83A, 0xFFFF5A3C, 0xFFFFD84A, 0xFFB070FF, 0xFFFFFFFF};

    private static float spin;
    private static boolean active;
    private static long lastSend;
    // Carga del clic derecho (desde que se pulsó; la mide también el servidor).
    private static boolean charging, chargeFull;
    private static long chargeStart;

    // Efectos.
    private static float trauma;
    private static int hitFlashTicks, hitFlashTotal;
    private static int perfectTicks;

    private record Word(Vec3 pos, String text, int color, int life, int[] age) {}

    private static final List<Word> FLOATING = new ArrayList<>();

    private SpinClient() {}

    public static float spin() {
        return active ? spin : 0;
    }

    /** La bola de la mano se ve dorada durante el Rectángulo Dorado. */
    public static boolean golden() {
        return active && SpinCinematic.locked();
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
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, SpinClient::onOverlay);
        MinecraftForge.EVENT_BUS.addListener((RenderHandEvent event) -> {
            if (SpinCinematic.camera()) event.setCanceled(true);
        });
        MinecraftForge.EVENT_BUS.addListener((RenderHighlightEvent.Block event) -> {
            if (SpinCinematic.camera()) event.setCanceled(true);
        });
        MinecraftForge.EVENT_BUS.addListener((MovementInputUpdateEvent event) -> {
            if (SpinCinematic.locked()) SpinCinematic.lockInput(event.getInput());
        });
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> reset());
    }

    private static void reset() {
        spin = 0;
        active = false;
        charging = false;
        trauma = 0;
        hitFlashTicks = perfectTicks = 0;
        SpinCinematic.stop();
        FLOATING.clear();
    }

    // ------------------------------------------------------------------ paquetes

    public static void onSync(Packets.SpinSync msg) {
        spin = Mth.clamp(msg.spin(), 0f, 1f);
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

    /**
     * Clic de ratón: con la bandolera en la mano, lanzar en vez de atacar o usar (antes de que lo vean vanilla y Epic Fight).
     * El izquierdo lanza al pulsar; el derecho empieza a cargar al pulsar y lanza al soltar.
     */
    private static void onMouse(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (mc.screen != null || p == null) return;
        if (SpinCinematic.locked()) {
            event.setCanceled(true);
            return;
        }
        var o = mc.options;
        if (event.getAction() == GLFW.GLFW_RELEASE) {
            if (charging && o.keyUse.matchesMouse(event.getButton())) {
                release(mc);
                event.setCanceled(true);
            }
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS || p.isSpectator() || !holding(p) || !localSpinMaster()) return;
        if (o.keyAttack.matchesMouse(event.getButton())) {
            send(mc, 0);
            event.setCanceled(true);
        } else if (o.keyUse.matchesMouse(event.getButton())) {
            if (useTargetsSomething(mc)) return;
            send(mc, 2);
            charging = true;
            chargeFull = false;
            chargeStart = mc.level == null ? 0 : mc.level.getGameTime();
            event.setCanceled(true);
        }
    }

    /** Suelta el clic derecho: lanza con la carga que lleve. */
    private static void release(Minecraft mc) {
        charging = false;
        if (mc.player != null && holding(mc.player)) {
            Net.CHANNEL.sendToServer(new Packets.SphereThrow(1, mc.player.isShiftKeyDown()));
        }
    }

    /** 0-1: cuánto lleva cargado el clic derecho (el servidor hace la misma cuenta). */
    private static float charge(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (!charging || mc.level == null) return 0;
        double full = Math.max(1, SpinMaster.anySide(SpinMaster.SPHERE_THROW, "full_charge_seconds", 1.0) * 20);
        return (float) Mth.clamp((mc.level.getGameTime() - chargeStart + pt) / full, 0, 1);
    }

    /** Ataque o uso con teclas del teclado (el ratón ya lo ha cogido onMouse). */
    private static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p != null && SpinCinematic.locked()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
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

    /** Pulsaciones (0 izquierdo, 1 derecho con teclado, sin carga; 2 empezar a cargar). */
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
        if (hitFlashTicks > 0) hitFlashTicks--;
        if (perfectTicks > 0) perfectTicks--;
        SpinCinematic.clientTick();
        if (charging) {
            // Se soltó fuera (con una pantalla abierta, por ejemplo) o ya no lleva la bandolera.
            LocalPlayer p = mc.player;
            var key = mc.options.keyUse.getKey();
            boolean down = key.getType() == InputConstants.Type.MOUSE
                    && GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), key.getValue()) == GLFW.GLFW_PRESS;
            if (p == null || !holding(p) || SpinCinematic.locked()) charging = false;
            else if (!down) release(mc);
            else if (!chargeFull && charge(0) >= 1f) {
                chargeFull = true;
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.CROSSBOW_LOADING_END, 1.4f, 0.6f));
            }
        }
        Iterator<Word> it = FLOATING.iterator();
        while (it.hasNext()) {
            Word w = it.next();
            if (++w.age()[0] >= w.life()) it.remove();
        }
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
        if (SpinCinematic.camera()) event.setRoll(event.getRoll() + SpinCinematic.roll());
    }

    private static void onFov(ViewportEvent.ComputeFov event) {
        double f = 1;
        if (SpinCinematic.camera()) f *= SpinCinematic.fov();
        if (hitFlashTicks > 0) f *= 1 - 0.11 * hitFlashTicks / (float) hitFlashTotal;
        if (f != 1) event.setFOV(event.getFOV() * f);
    }

    /** Durante la cinemática solo se ven sus capas (y el chat). */
    private static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!SpinCinematic.camera()) return;
        var id = event.getOverlay().id();
        if (id.getNamespace().equals(io.github.rpgclasses.RpgClasses.MODID) && id.getPath().equals("spin_cinematic")) return;
        if (id.equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CHAT_PANEL.id())) return;
        event.setCanceled(true);
    }

    // ------------------------------------------------------------------ HUD

    /** Cinemática del Rectángulo Dorado (lanzador), destello dorado (golpeados) y de la Captura Perfecta: debajo del resto del HUD. */
    private static void renderCinematic(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        SpinCinematic.renderOverlay(g, partialTick, width, height);
        if (hitFlashTicks > 0) {
            float f = hitFlashTicks / (float) hitFlashTotal;
            g.fill(0, 0, width, height, ((int) (0x70 * f) << 24) | 0xFFD84A);
        }
        if (perfectTicks > 0) {
            int a = (int) (0x50 * perfectTicks / 6f);
            g.fill(0, 0, width, height, (a << 24) | 0xFFFFFF);
        }
    }

    /**
     * Barra de Giro (con marcas en el 33 y el 66 %), la carga del clic derecho y la bola elegida, sobre la barra de objetos.
     * Con el Giro al 100 %, el aviso del Rectángulo Dorado solo sale si ya está desbloqueado (y sin enfriamiento).
     */
    private static void renderHud(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.level == null || !localSpinMaster()) return;
        Font font = mc.font;
        int w = 100, h = 5;
        int x = width / 2 - w / 2, y = height - 62;
        long now = mc.level.getGameTime();
        java.util.List<String> actives = io.github.rpgclasses.client.AbilityHud.localActives();
        int slot = actives.indexOf(SpinMaster.GOLDEN_RECTANGLE);
        boolean unlocked = slot >= 0 && slot < io.github.rpgclasses.client.ClientSetup.ABILITY_KEYS.length;
        long cooldown = unlocked ? io.github.rpgclasses.client.ClientHandlers.LOCAL_DATA.cooldowns.getOrDefault(SpinMaster.GOLDEN_RECTANGLE, 0L) - now : 0;
        boolean full = spin >= 0.999f;
        boolean ready = full && unlocked && cooldown <= 0;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xA0000000);
        int fill = Math.round(w * spin);
        float pulse = 0.5f + 0.5f * Mth.sin((now + partialTick) * 0.35f);
        int color = ready ? lerp(0xFFE8B83A, 0xFFFFF2B0, pulse) : spin >= 0.66f ? 0xFFE8B83A : spin >= 0.33f ? 0xFFC99A3A : 0xFF9A8456;
        g.fill(x, y, x + fill, y + h, color);
        for (float mark : new float[] {0.33f, 0.66f}) {
            int mx = x + Math.round(w * mark);
            g.fill(mx, y - 2, mx + 1, y + h + 2, 0xFFFFFFFF);
        }
        // Carga del clic derecho: una línea fina bajo la barra.
        float c = charge(partialTick);
        if (c > 0) {
            int cw = Math.round(w * c);
            g.fill(x, y + h + 2, x + cw, y + h + 4, c >= 1f ? lerp(0xFFFFD84A, 0xFFFFFFFF, pulse) : 0xFFE0E0E0);
        }
        String label;
        if (ready) {
            label = Component.translatable("hud.rites_and_paths.spin.ready",
                    io.github.rpgclasses.client.ClientSetup.ABILITY_KEYS[slot].getTranslatedKeyMessage()).getString();
        } else {
            label = Component.translatable("hud.rites_and_paths.spin").getString() + " " + Math.round(spin * 100) + "%";
            if (full && unlocked) label += String.format(Locale.ROOT, "  (%.0f s)", Math.ceil(cooldown / 20.0));
        }
        g.drawCenteredString(font, label, width / 2, y - 10, ready ? 0xFFFFD84A : 0xFFFFFFFF);
        ItemStack band = mc.player.getMainHandItem();
        if (Bandolier.is(band)) {
            SphereMaterial m = Bandolier.current(band);
            if (m != null) {
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
