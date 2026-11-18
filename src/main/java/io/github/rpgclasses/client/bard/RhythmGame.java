package io.github.rpgclasses.client.bard;

import com.google.gson.JsonObject;
import io.github.rpgclasses.bard.Bard;
import io.github.rpgclasses.bard.BardState;
import io.github.rpgclasses.bard.Instrument;
import io.github.rpgclasses.client.ClientSetup;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.definition.Definitions.AbilityDef;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Minijuego de ritmo del Bardo (adenda del Bardo, sección 2), en el cliente: genera frases de notas, juzga las
 * pulsaciones de los cuatro carriles (clic izquierdo, clic derecho, Shift y espacio, que siguen haciendo su acción) y
 * manda cada resultado al servidor, que lleva el combo. La guía se dibuja junto a la mira: cada nota viene hacia el
 * centro desde el lado de su carril (izquierda: clic izquierdo; derecha: clic derecho; arriba: espacio; abajo: Shift).
 *
 * <p>Ritmo: solo es fallo no pulsar a tiempo el carril pedido; pulsar otros carriles no penaliza el combo, pero sí
 * rompe la Armonía. El tempo sube con el combo (hasta el tope) y lo cambian Tempo Presto y Tempo Largo; las notas de
 * clic izquierdo nunca llegan más rápido que el ciclo de ataque del arma.
 *
 * <p>Las notas llegan en ráfagas (una nota por pulso) separadas por descansos, y tres ráfagas forman un ciclo que el
 * servidor cuenta como una frase para la Armonía: {@link #BURST_NOTES} y {@link #BURST_REST_MS}.
 */
public final class RhythmGame {
    private static final int[] LANE_COLOR = {0xFFE0533D, 0xFF4E8FE8, 0xFFF2C94C, 0xFF6FD07A};
    private static final int LANE_DIST = 14, TRAVEL = 46;

    private static final class Note {
        final long time;
        final int lane;
        boolean judged;

        Note(long time, int lane) {
            this.time = time;
            this.lane = lane;
        }
    }

    /** Ráfaga de notas ya generada: tiempo de la primera y la última nota y duración del pulso (ms). */
    private record Burst(long start, long end, double beat) {}

    private record Flash(int lane, int color, long until) {}

    private record Popup(Component text, int color, long until) {}

    // Estado que manda el servidor.
    private static int combo, stage, crescendoTicks;

    // Partitura en curso.
    private static final List<Note> NOTES = new ArrayList<>();
    private static final List<Flash> FLASHES = new ArrayList<>();
    private static Popup popup;
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final RandomSource RNG = RandomSource.create();
    private static boolean active;
    private static double beatMs = 600;
    /** Cuándo empieza la siguiente ráfaga; cuándo acaba la última de un ciclo (0 = ya contado). */
    private static long nextBurst, closeAt;
    private static int burstIndex;
    private static long lastBeatSound;
    private static long lastAttackNote, lastUseNote, lastJumpNote;

    /** Patrón de un ciclo: notas seguidas [mín, máx] de cada ráfaga y descanso [mín, máx] (ms) hasta la siguiente. */
    private static final int[][] BURST_NOTES = {{3, 4}, {3, 6}, {6, 10}};
    private static final int[][] BURST_REST_MS = {{1000, 1000}, {1000, 1000}, {1500, 4000}};

    private RhythmGame() {}

    // ------------------------------------------------------------------ servidor

    public static void onSync(Packets.BardSync msg) {
        combo = msg.combo();
        stage = msg.stage();
        crescendoTicks = msg.crescendoTicks();
        if (!msg.bard()) stop();
    }

    public static void reset() {
        stop();
        combo = 0;
        stage = 0;
        crescendoTicks = 0;
    }

    private static void stop() {
        active = false;
        NOTES.clear();
        BURSTS.clear();
        FLASHES.clear();
        popup = null;
    }

    // ------------------------------------------------------------------ parámetros

    private static JsonObject params() {
        AbilityDef def = DefinitionStore.CLIENT.abilityDef(Bard.RHYTHM);
        return def == null ? new JsonObject() : def.params();
    }

    private static double p(JsonObject o, String key, double fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsDouble() : fallback;
    }

    private static double mech(String id, String key, double fallback) {
        ActiveMechanic m = ClientSetup.localMechanic("rites_and_paths:" + id);
        return m == null ? fallback : m.number(key, fallback);
    }

    private static boolean hasMech(String id) {
        return ClientSetup.localMechanic("rites_and_paths:" + id) != null;
    }

    /** Tempo: sube con el combo (ya no con la Armonía); Tempo Largo también recorta esa subida. */
    private static double bpm() {
        JsonObject r = params();
        double perCombo = p(r, "tempo_per_combo", 0.015);
        double base = p(r, "base_bpm", 100) * (1 + mech(Bard.TEMPO_PRESTO, "tempo_bonus", 0) + mech(Bard.TEMPO_LARGO, "tempo_bonus", 0));
        double comboFactor = 1 + perCombo * combo * (hasMech(Bard.TEMPO_LARGO) ? 1 + mech(Bard.TEMPO_LARGO, "tempo_bonus", -0.2) : 1);
        return Math.max(40, Math.min(p(r, "max_bpm", 180), base * comboFactor));
    }

    /** Ventana de acierto (ms) a cada lado de la nota, sin pasar del 45 % del pulso. */
    private static double window() {
        double w = p(params(), "window_ms", 110) * (1 + mech(Bard.TIMING_WINDOW, "window_bonus", 0) + mech(Bard.TEMPO_LARGO, "window_bonus", 0));
        return Math.min(w, beatMs * 0.45);
    }

    private static double perfectWindow() {
        return Math.min(window() * 0.6, p(params(), "perfect_ms", 50) * (1 + mech(Bard.TIMING_WINDOW, "window_bonus", 0)));
    }

    // ------------------------------------------------------------------ tick

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (crescendoTicks > 0) crescendoTicks--;
        boolean should = player != null && player.isAlive() && !player.isSpectator() && Bard.isBard(player)
                && Bard.instrumentHand(player) != null && mc.screen == null && !mc.isPaused() && !DanceFx.localDancing();
        long now = Util.getMillis();
        if (!should) {
            if (active) stop();
            return;
        }
        if (!active) {
            active = true;
            NOTES.clear();
            BURSTS.clear();
            beatMs = 60000.0 / bpm();
            nextBurst = now + (long) (beatMs * 2); // cuenta de entrada
            closeAt = 0;
            burstIndex = 0;
            lastAttackNote = lastUseNote = lastJumpNote = Long.MIN_VALUE / 2;
        }
        // Fallos: notas que ya han pasado sin pulsarse.
        double w = window();
        for (Note n : NOTES) {
            if (!n.judged && now > n.time + w) {
                n.judged = true;
                send(BardState.MISS, n.lane);
                flash(n.lane, 0xFFFF3030, now);
                popup = new Popup(Component.translatable("hud.rites_and_paths.bard.miss"), 0xFFFF5050, now + 450);
            }
        }
        // Al acabar la tercera ráfaga se cierra el ciclo (la frase de la Armonía).
        if (closeAt > 0 && now > closeAt + w) {
            send(BardState.PHRASE, 0);
            closeAt = 0;
        }
        BURSTS.removeIf(b -> now > b.end() + 2000);
        if (now >= nextBurst - beatMs * 2) newBurst(Math.max(now + (long) beatMs, nextBurst), player);
        NOTES.removeIf(n -> n.judged && now > n.time + 1000);
        FLASHES.removeIf(f -> f.until() < now);
    }

    /** Genera una ráfaga (una nota por pulso) con el tempo de ahora y decide cuándo empieza la siguiente. */
    private static void newBurst(long start, LocalPlayer player) {
        beatMs = 60000.0 / bpm();
        int[] size = BURST_NOTES[burstIndex];
        int count = size[0] + RNG.nextInt(size[1] - size[0] + 1);
        double speed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        double attackPeriod = 1000.0 / Math.max(0.25, speed);
        long last = start;
        for (int i = 0; i < count; i++) {
            long t = start + Math.round(i * beatMs);
            int lane = pickLane();
            if (lane == Bard.LANE_ATTACK && t - lastAttackNote < attackPeriod * 0.95) lane = Bard.LANE_SNEAK;
            if (lane == Bard.LANE_USE && t - lastUseNote < Math.max(1500, beatMs * 4)) lane = Bard.LANE_SNEAK;
            if (lane == Bard.LANE_JUMP && t - lastJumpNote < beatMs * 2) lane = Bard.LANE_SNEAK;
            switch (lane) {
                case Bard.LANE_ATTACK -> lastAttackNote = t;
                case Bard.LANE_USE -> lastUseNote = t;
                case Bard.LANE_JUMP -> lastJumpNote = t;
                default -> { }
            }
            NOTES.add(new Note(t, lane));
            last = t;
        }
        BURSTS.add(new Burst(start, last, beatMs));
        int[] rest = BURST_REST_MS[burstIndex];
        long restMs = rest[0] + (rest[1] > rest[0] ? RNG.nextInt(rest[1] - rest[0] + 1) : 0);
        nextBurst = last + Math.max(restMs, Math.round(beatMs * 1.5));
        if (burstIndex == BURST_NOTES.length - 1) closeAt = last;
        burstIndex = (burstIndex + 1) % BURST_NOTES.length;
    }

    private static int pickLane() {
        double r = RNG.nextDouble();
        if (r < 0.40) return Bard.LANE_ATTACK;
        if (r < 0.62) return Bard.LANE_SNEAK;
        if (r < 0.85) return Bard.LANE_JUMP;
        return Bard.LANE_USE;
    }

    // ------------------------------------------------------------------ entrada

    public static void onMouse(InputEvent.MouseButton.Pre event) {
        if (!active || event.getAction() != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null) return;
        var o = Minecraft.getInstance().options;
        int b = event.getButton();
        if (o.keyAttack.matchesMouse(b)) press(Bard.LANE_ATTACK);
        else if (o.keyUse.matchesMouse(b)) press(Bard.LANE_USE);
        else if (o.keyShift.matchesMouse(b)) press(Bard.LANE_SNEAK);
        else if (o.keyJump.matchesMouse(b)) press(Bard.LANE_JUMP);
    }

    public static void onKey(InputEvent.Key event) {
        if (!active || event.getAction() != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null) return;
        var o = Minecraft.getInstance().options;
        int k = event.getKey(), s = event.getScanCode();
        if (o.keyAttack.matches(k, s)) press(Bard.LANE_ATTACK);
        else if (o.keyUse.matches(k, s)) press(Bard.LANE_USE);
        else if (o.keyShift.matches(k, s)) press(Bard.LANE_SNEAK);
        else if (o.keyJump.matches(k, s)) press(Bard.LANE_JUMP);
    }

    private static void press(int lane) {
        long now = Util.getMillis();
        double w = window();
        Note best = null;
        for (Note n : NOTES) {
            if (n.judged || n.lane != lane || Math.abs(n.time - now) > w) continue;
            if (best == null || Math.abs(n.time - now) < Math.abs(best.time - now)) best = n;
        }
        if (best == null) {
            // Pulsación de más: no penaliza el combo, pero rompe la Armonía. En los descansos entre ráfagas no cuenta.
            boolean inBurst = false;
            for (Burst b : BURSTS) if (now >= b.start() - w && now <= b.end() + w) inBurst = true;
            if (inBurst) send(BardState.EXTRA, lane);
            return;
        }
        best.judged = true;
        boolean perfect = Math.abs(best.time - now) <= perfectWindow();
        send(perfect ? BardState.PERFECT : BardState.HIT, lane);
        flash(lane, perfect ? 0xFFFFFFFF : LANE_COLOR[lane], now);
        popup = perfect ? new Popup(Component.translatable("hud.rites_and_paths.bard.perfect"), 0xFFFFE680, now + 350)
                : new Popup(Component.translatable("hud.rites_and_paths.bard.good"), 0xFFB0E0FF, now + 300);
        playNote(lane);
    }

    private static void send(int type, int lane) {
        Net.CHANNEL.sendToServer(new Packets.BardNote(type, lane));
    }

    private static void flash(int lane, int color, long now) {
        FLASHES.add(new Flash(lane, color, now + 160));
    }

    private static void playNote(int lane) {
        Minecraft mc = Minecraft.getInstance();
        Instrument i = mc.player == null ? null : Bard.instrument(mc.player);
        SoundEvent sound = i == null ? SoundEvents.NOTE_BLOCK_HARP.value() : i.sound.getSoundEvent().value();
        float pitch = (float) Math.pow(2.0, ((lane * 4 + Math.min(12, combo / 3)) - 12) / 12.0);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.6f));
    }

    // ------------------------------------------------------------------ HUD

    public static void renderHud(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        long now = Util.getMillis();
        int cx = width / 2, cy = height / 2;
        double lead = beatMs * 2;

        // Metrónomo suave en cada pulso de una ráfaga.
        for (Burst b : BURSTS) {
            if (now < b.start() || now > b.end() + 60) continue;
            long beat = (long) Math.floor((now - b.start()) / b.beat());
            long beatTime = b.start() + Math.round(beat * b.beat());
            if (beatTime > lastBeatSound && now - beatTime < 60) {
                lastBeatSound = beatTime;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 1.6f, 0.12f));
            }
        }

        // Dianas de los carriles.
        for (int lane = 0; lane < 4; lane++) {
            int[] t = target(cx, cy, lane, 0);
            int color = LANE_COLOR[lane] & 0x80FFFFFF;
            for (Flash f : FLASHES) if (f.lane() == lane) color = f.color();
            frame(g, t[0] - 4, t[1] - 4, t[0] + 4, t[1] + 4, color);
        }
        // Notas que se acercan.
        for (Note n : NOTES) {
            if (n.judged) continue;
            double progress = (n.time - now) / lead;
            if (progress > 1 || progress < -0.3) continue;
            int[] pos = target(cx, cy, n.lane, Math.max(0, progress));
            int alpha = progress < 0 ? 0x60 : 0xFF;
            diamond(g, pos[0], pos[1], (LANE_COLOR[n.lane] & 0x00FFFFFF) | (alpha << 24));
        }
        // Combo, Armonía y Crescendo, debajo de la mira.
        var font = mc.font;
        Component c = Component.translatable("hud.rites_and_paths.bard.combo", combo);
        g.drawString(font, c, cx - font.width(c) / 2, cy + 26, combo >= 30 ? 0xFFFFD34A : combo >= 20 ? 0xFFE7A0FF : combo >= 10 ? 0xFF8FD8FF : 0xFFE0E0E0, true);
        if (stage > 0) {
            for (int i = 0; i < 5; i++) {
                int x = cx - 12 + i * 5;
                g.fill(x, cy + 37, x + 3, cy + 40, i < stage ? 0xFF7FF0C8 : 0x50FFFFFF);
            }
        }
        if (crescendoTicks > 0) {
            Component cres = Component.translatable("hud.rites_and_paths.bard.crescendo");
            g.drawString(font, cres, cx - font.width(cres) / 2, cy + 43, 0xFFFFB347, true);
        }
        if (popup != null && popup.until() > now) {
            g.drawString(font, popup.text(), cx - font.width(popup.text()) / 2, cy - 30, popup.color(), true);
        }
    }

    /** Posición de una nota: {@code progress} 0 = en la diana, 1 = al principio del recorrido. */
    private static int[] target(int cx, int cy, int lane, double progress) {
        int d = LANE_DIST + (int) Math.round(progress * TRAVEL);
        return switch (lane) {
            case Bard.LANE_ATTACK -> new int[] {cx - d, cy};
            case Bard.LANE_USE -> new int[] {cx + d, cy};
            case Bard.LANE_JUMP -> new int[] {cx, cy - d};
            default -> new int[] {cx, cy + d};
        };
    }

    private static void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }

    private static void diamond(GuiGraphics g, int x, int y, int color) {
        for (int i = -3; i <= 3; i++) {
            int half = 3 - Math.abs(i);
            g.fill(x - half, y + i, x + half + 1, y + i + 1, color);
        }
    }
}
