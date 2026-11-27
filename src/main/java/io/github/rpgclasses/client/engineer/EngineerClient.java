package io.github.rpgclasses.client.engineer;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.rpgclasses.client.ClassKeyContext;
import io.github.rpgclasses.client.ClientHandlers;
import io.github.rpgclasses.client.hud.HudLayout;
import io.github.rpgclasses.compat.ntgl.NtglCompat;
import io.github.rpgclasses.engineer.EngineerHooks;
import io.github.rpgclasses.engineer.EngineerPackets;
import io.github.rpgclasses.engineer.EngineerRegistry;
import io.github.rpgclasses.network.Net;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

/**
 * Cliente del Ingeniero.
 * <ul>
 *   <li><b>Recarga activa:</b> barra bajo la mira con la zona buena y la perfecta; al volver a pulsar la tecla de recargar
 *       de NTGL se manda dónde estaba el cursor (el servidor lo comprueba con su propio reloj).</li>
 *   <li><b>HUD:</b> racha, bala especial cargada y la siguiente; cargas del Artificiero.</li>
 *   <li><b>Teclas</b> de torreta, mina y trinchera (solo hacen algo siendo Artificiero) y el clic izquierdo con el arma
 *       vacía del perk Arma Arrojadiza.</li>
 *   <li>Brillo de los marcados por Ojo Muerto y dibujo de torretas, minas y armas lanzadas.</li>
 * </ul>
 */
public final class EngineerClient {
    public static final KeyMapping TURRET = key("engineer_turret", GLFW.GLFW_KEY_G);
    public static final KeyMapping MINE = key("engineer_mine", GLFW.GLFW_KEY_Y);
    public static final KeyMapping TRENCH = key("engineer_trench", GLFW.GLFW_KEY_U);
    private static final String[] SPECIALS = {"fire", "stun", "frost", "poison", "wither", "levitation", "pull"};
    private static final int[] SPECIAL_COLORS = {0xFFFF8A3D, 0xFFFFE066, 0xFF9FE3FF, 0xFF8BD45A, 0xFF7A6A6A, 0xFFE0B3FF, 0xFFB070FF};

    private static EngineerPackets.Sync sync;
    // Barra de recarga activa (tics de juego del cliente).
    private static int barTotal;
    private static long barStart;
    private static float perfectStart, perfectEnd, goodStart, goodEnd;
    private static boolean pressed;
    private static int resultZone = -1;
    private static long resultAt;
    // Ojo Muerto.
    private static final Set<Integer> MARKS = new HashSet<>();
    private static long marksUntil;
    private static long clientTicks;

    private EngineerClient() {
    }

    private static KeyMapping key(String name, int glfw) {
        return new KeyMapping("key.rites_and_paths." + name, ClassKeyContext.ENGINEER, InputConstants.Type.KEYSYM, glfw,
                "key.categories.rites_and_paths");
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.register(TURRET);
            e.register(MINE);
            e.register(TRENCH);
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerEntityRenderer(EngineerRegistry.TURRET.get(), TurretRenderer::new);
            e.registerEntityRenderer(EngineerRegistry.MINE.get(), MineRenderer::new);
            e.registerEntityRenderer(EngineerRegistry.THROWN_FIREARM.get(), ctx -> new ThrownItemRenderer<>(ctx, 1.0f, true));
        });
        modBus.addListener((RegisterGuiOverlaysEvent e) -> {
            e.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "engineer_reload", HudLayout.wrap("engineer_reload", EngineerClient::renderBar));
            e.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "engineer_ammo", HudLayout.wrap("engineer_ammo", EngineerClient::renderAmmo));
            e.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "artificer_charges",
                    HudLayout.wrap("artificer_charges", EngineerClient::renderCharges));
        });
        EngineerHooks.client = e -> e == Minecraft.getInstance().player && active() ? sync.factors() : null;
        EngineerHooks.clientAlly = ClientHandlers::isAlly;
        MinecraftForge.EVENT_BUS.addListener(EngineerClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(EngineerClient::onKey);
        MinecraftForge.EVENT_BUS.addListener(EngineerClient::onMouse);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, EngineerClient::onAttack);
    }

    private static boolean active() {
        return sync != null && sync.engineer();
    }

    // ------------------------------------------------------------------ paquetes

    public static void onSync(EngineerPackets.Sync msg) {
        sync = msg;
        if (!msg.engineer()) {
            barTotal = 0;
            MARKS.clear();
        }
    }

    public static void onBar(EngineerPackets.Bar msg) {
        barTotal = msg.total();
        barStart = clientTicks;
        perfectStart = msg.perfectStart();
        perfectEnd = msg.perfectEnd();
        goodStart = msg.goodStart();
        goodEnd = msg.goodEnd();
        pressed = false;
    }

    public static void onResult(EngineerPackets.Result msg) {
        resultZone = msg.zone();
        resultAt = clientTicks;
        pressed = true;
    }

    public static void onMarks(EngineerPackets.Marks msg) {
        MARKS.clear();
        MARKS.addAll(msg.ids());
        marksUntil = clientTicks + msg.ticks();
    }

    /** Ojo Muerto: los marcados brillan solo para el Pistolero (HealerGlowMixin). */
    public static boolean glows(Entity entity) {
        return clientTicks < marksUntil && MARKS.contains(entity.getId());
    }

    // ------------------------------------------------------------------ entrada

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        clientTicks++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            sync = null;
            barTotal = 0;
            return;
        }
        if (barTotal > 0 && clientTicks - barStart > barTotal + 10) barTotal = 0;
        if (!active() || mc.screen != null) return;
        while (TURRET.consumeClick()) Net.CHANNEL.sendToServer(new EngineerPackets.Gadget(0));
        while (MINE.consumeClick()) Net.CHANNEL.sendToServer(new EngineerPackets.Gadget(1));
        while (TRENCH.consumeClick()) Net.CHANNEL.sendToServer(new EngineerPackets.Gadget(2));
    }

    private static KeyMapping reloadKey;
    private static boolean reloadKeyLooked;

    /** La tecla de recargar de NTGL (por reflexión: NTGL es opcional). */
    private static KeyMapping reloadKey() {
        if (!reloadKeyLooked) {
            reloadKeyLooked = true;
            try {
                reloadKey = (KeyMapping) Class.forName("com.nukateam.ntgl.client.input.NtglKeyBinds").getField("KEY_RELOAD").get(null);
            } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
                reloadKey = null;
            }
        }
        return reloadKey;
    }

    private static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        KeyMapping key = reloadKey();
        if (key != null && key.matches(event.getKey(), event.getScanCode())) pressReload();
    }

    private static void onMouse(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        KeyMapping key = reloadKey();
        if (key != null && key.matchesMouse(event.getButton())) pressReload();
    }

    private static void pressReload() {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.screen != null || barTotal <= 0 || pressed) return;
        // Recién empezada la barra, la misma pulsación que la ha abierto no cuenta.
        if (clientTicks - barStart < 2) return;
        pressed = true;
        Net.CHANNEL.sendToServer(new EngineerPackets.Press(fraction(mc.getFrameTime())));
    }

    private static float fraction(float partial) {
        return barTotal <= 0 ? 0 : (clientTicks - barStart + partial) / barTotal;
    }

    /** Arma Arrojadiza: clic izquierdo con el arma de fuego vacía. */
    private static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack() || !active() || (sync.flags() & EngineerPackets.Sync.THROWING) == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !NtglCompat.isFirearm(mc.player.getMainHandItem())) return;
        if (NtglCompat.ammo(mc.player.getMainHandItem(), mc.player) != 0) return;
        event.setCanceled(true);
        event.setSwingHand(true);
        Net.CHANNEL.sendToServer(new EngineerPackets.Throw());
    }

    // ------------------------------------------------------------------ HUD

    private static void renderBar(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.options.hideGui) return;
        int w = 100, x = width / 2 - w / 2, y = height / 2 + 14;
        boolean showBar = barTotal > 0 && !pressed;
        boolean showResult = resultZone >= 0 && clientTicks - resultAt < 25;
        if (!showBar && !showResult) return;
        if (showBar) {
            g.fill(x - 1, y - 1, x + w + 1, y + 6, 0xC0000000);
            g.fill(x, y, x + w, y + 5, 0xFF3A2A22);
            g.fill(x + (int) (w * goodStart), y, x + (int) (w * goodEnd), y + 5, 0xFFC9A13B);
            g.fill(x + (int) (w * perfectStart), y, x + Math.max((int) (w * perfectStart) + 1, (int) (w * perfectEnd)), y + 5, 0xFF62D66A);
            int cx = x + (int) (w * Mth.clamp(fraction(partial), 0, 1));
            g.fill(cx - 1, y - 2, cx + 1, y + 7, 0xFFFFFFFF);
        }
        if (showResult) {
            String key = resultZone == 0 ? "perfect" : resultZone == 1 ? "good" : "jam";
            int color = resultZone == 0 ? 0xFF62D66A : resultZone == 1 ? 0xFFE8C75A : 0xFFE05050;
            g.drawCenteredString(mc.font, Component.translatable("hud.rites_and_paths.engineer." + key), width / 2, y + 9, color);
        }
        HudLayout.report("engineer_reload", x - 1, y - 2, w + 2, 20);
    }

    private static void renderAmmo(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.options.hideGui) return;
        Font font = mc.font;
        int x = width / 2 + 96, y = height - 12;
        int lines = 0, maxW = 0;
        if (sync.streakMax() > 0) {
            Component c = Component.translatable("hud.rites_and_paths.engineer.streak", sync.streak() + "/" + sync.streakMax());
            g.drawString(font, c, x, y - 10 * lines, sync.streak() >= sync.streakMax() ? 0xFFFFD25A : 0xFFE6E6E6);
            maxW = Math.max(maxW, font.width(c));
            lines++;
        }
        if (sync.loaded() >= 0 && sync.loaded() < SPECIALS.length) {
            Component c = Component.translatable("hud.rites_and_paths.engineer.loaded",
                    Component.translatable("special.rites_and_paths." + SPECIALS[sync.loaded()]), sync.loadedLeft());
            g.drawString(font, c, x, y - 10 * lines, SPECIAL_COLORS[sync.loaded()]);
            maxW = Math.max(maxW, font.width(c));
            lines++;
        }
        if (sync.next() >= 0 && sync.next() < SPECIALS.length) {
            Component c = Component.translatable("hud.rites_and_paths.engineer.next",
                    Component.translatable("special.rites_and_paths." + SPECIALS[sync.next()]));
            g.drawString(font, c, x, y - 10 * lines, 0xFFB0B0B0);
            maxW = Math.max(maxW, font.width(c));
            lines++;
        }
        if (sync.perfect() > 0) {
            Component c = Component.translatable("hud.rites_and_paths.engineer.perfect_buff", sync.perfect());
            g.drawString(font, c, x, y - 10 * lines, 0xFF62D66A);
            maxW = Math.max(maxW, font.width(c));
            lines++;
        }
        if (lines > 0) HudLayout.report("engineer_ammo", x, y - 10 * (lines - 1), maxW, 10 * lines);
    }

    private static void renderCharges(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.options.hideGui || sync.maxCharges() <= 0) return;
        int size = 7, gap = 2, n = sync.maxCharges();
        int w = n * (size + gap) - gap;
        int x = width / 2 - 96 - w, y = height - 11;
        for (int i = 0; i < n; i++) {
            int bx = x + i * (size + gap);
            g.fill(bx, y, bx + size, y + size, 0xFF000000);
            g.fill(bx + 1, y + 1, bx + size - 1, y + size - 1, i < sync.charges() ? 0xFFE0A040 : 0xFF4A4038);
        }
        if (sync.nextCharge() > 0) {
            String t = sync.nextCharge() + "s";
            g.drawString(mc.font, t, x - mc.font.width(t) - 2, y - 1, 0xFFB0B0B0);
        }
        Component label = Component.translatable("hud.rites_and_paths.engineer.charges", sync.charges(), n);
        g.drawString(mc.font, label, x + w - mc.font.width(label), y - 10, 0xFFE6E6E6);
        HudLayout.report("artificer_charges", x - 20, y - 10, w + 20, 18);
    }
}
