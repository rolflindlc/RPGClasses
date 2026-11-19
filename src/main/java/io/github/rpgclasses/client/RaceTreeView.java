package io.github.rpgclasses.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.rpgclasses.client.tree.IconRef;
import io.github.rpgclasses.client.tree.McCanvas;
import io.github.rpgclasses.client.tree.RaceTreeLayout;
import io.github.rpgclasses.client.tree.RaceTreeLayout.Node;
import io.github.rpgclasses.client.tree.RaceTreePainter;
import io.github.rpgclasses.client.tree.TreeCamera;
import io.github.rpgclasses.client.tree.TreeLayout;
import io.github.rpgclasses.client.tree.TreeStatus.Status;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.definition.AttributeEffect;
import io.github.rpgclasses.core.definition.Definitions.EffectDef;
import io.github.rpgclasses.core.definition.Definitions.ObjectiveDef;
import io.github.rpgclasses.core.definition.Definitions.RaceDef;
import io.github.rpgclasses.core.definition.Definitions.RiteDef;
import io.github.rpgclasses.core.definition.Definitions.SubraceDef;
import io.github.rpgclasses.core.definition.RaceStore;
import io.github.rpgclasses.core.progression.ProgressState;
import io.github.rpgclasses.core.progression.ProgressionRules;
import io.github.rpgclasses.core.rite.RiteProgress;
import io.github.rpgclasses.core.stat.StatCalculator;
import io.github.rpgclasses.core.value.LevelValue;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Árbol de progreso de la raza del jugador, con la misma estética que el árbol de habilidades de clase:
 * el núcleo (la raza), la subraza, los tres ritos que suben el Rango y, en cada rango, las habilidades nuevas.
 * Es solo de consulta: se arrastra, se hace zoom y las ayudas muestran los ritos (con su progreso) y los rasgos.
 */
public final class RaceTreeView implements RaceTreePainter.Info {
    private static final double DRAG_THRESHOLD = 4;
    /** Cámara de cada subraza, para volver a donde estabas al reabrir el libro. */
    private static final Map<String, float[]> CAMERAS = new HashMap<>();
    private static final int OK = 0xFFB8F28C, TEXT = 0xFFE8E4F0;

    private final TreeCamera cam = new TreeCamera();
    private final RaceTreePainter painter = new RaceTreePainter();
    private final McCanvas canvas = new McCanvas();

    private RaceTreeLayout layout;
    private RaceDef race;
    private SubraceDef sub;
    private long lastNanos;
    private float time, hintTime;
    private Node hover;
    private double pressX, pressY;
    private int pressButton = -1;
    private boolean dragging;

    private static ProgressState state() {
        return ClientHandlers.LOCAL_DATA.state;
    }

    /** true si el jugador tiene una raza y una subraza conocidas por el cliente. */
    public static boolean available() {
        ProgressState s = state();
        RaceDef r = s.raceId == null ? null : RaceStore.CLIENT.race(s.raceId);
        return r != null && r.subrace(s.subraceId) != null;
    }

    /** Prepara la vista; si la raza no cambia, se conservan la cámara y las animaciones. */
    public void setup(int x0, int y0, int x1, int y1) {
        cam.setView(x0, y0, x1, y1);
        ProgressState s = state();
        RaceDef r = s.raceId == null ? null : RaceStore.CLIENT.race(s.raceId);
        SubraceDef sd = r == null ? null : r.subrace(s.subraceId);
        if (r == race && sd == sub && (layout != null || sd == null)) return;
        saveCamera();
        race = r;
        sub = sd;
        hover = null;
        if (r == null || sd == null) {
            layout = null;
            return;
        }
        layout = build(r, sd);
        float[] saved = CAMERAS.get(key());
        if (saved != null) cam.set(saved[0], saved[1], saved[2]);
        else cam.home(layout, false);
        cam.clamp(layout);
    }

    private String key() {
        return race.id() + "/" + sub.id();
    }

    /** Al cerrar la pantalla: recuerda la cámara. */
    public void saveCamera() {
        if (race != null && sub != null && layout != null) CAMERAS.put(key(), new float[]{cam.x, cam.y, cam.zoom});
    }

    // --- Construcción ---

    private RaceTreeLayout build(RaceDef r, SubraceDef sd) {
        int color = TreeLayout.BRANCH_COLORS[Math.floorMod(r.id().hashCode(), TreeLayout.BRANCH_COLORS.length)];
        RaceTreeLayout l = new RaceTreeLayout(color, byRank(r.effects()), byRank(sd.effects()), sd.rites(),
                e -> Ids.EFFECT_ATTRIBUTE.equals(e.type()));
        l.core.label = I18n.get(Ids.langKey("race", r.id(), "name"));
        l.core.icon = IconRef.racePortrait(r.id(), r.icon());
        l.subKey.label = subraceName(r, sd);
        l.subKey.icon = IconRef.subracePortrait(r.id(), sd.id(), sd.icon());
        for (Node n : l.rites) {
            n.label = n.rite == null ? "" : I18n.get(riteKey(n.rite));
            n.icon = IconRef.item(riteIcon(n.rite));
        }
        for (Node g : l.gems) g.label = gemName(g);
        return l;
    }

    /** Efectos repartidos por el Rango en que se ganan (las mecánicas con min_rank; el resto, en el Rango 0). */
    private static List<List<EffectDef>> byRank(List<EffectDef> effects) {
        List<List<EffectDef>> out = new ArrayList<>();
        for (int i = 0; i <= ProgressionRules.RACE_MAX_RANK; i++) out.add(new ArrayList<>());
        for (EffectDef e : effects) {
            if (Ids.EFFECT_ATTRIBUTE.equals(e.type())) {
                List<Component> lines = new ArrayList<>();
                RaceScreen.attributeLines(e, lines);
                if (lines.isEmpty()) continue; // solo bandera interna: no hay nada que enseñar
            }
            out.get(rankOf(e)).add(e);
        }
        return out;
    }

    private static int rankOf(EffectDef e) {
        if (!Ids.EFFECT_MECHANIC.equals(e.type()) || !e.params().has("min_rank")) return 0;
        return Math.max(0, Math.min(ProgressionRules.RACE_MAX_RANK, e.params().get("min_rank").getAsInt()));
    }

    private static String subraceName(RaceDef r, SubraceDef sd) {
        return I18n.get(Ids.langKey("subrace", r.id() + "." + sd.id(), "name"));
    }

    private static String riteKey(RiteDef rite) {
        return "rite." + io.github.rpgclasses.RpgClasses.MODID + "." + rite.id();
    }

    private static String riteIcon(RiteDef rite) {
        if (rite == null) return "minecraft:book";
        return switch (rite.kind()) {
            case "feat" -> "minecraft:iron_sword";
            case "craft" -> "minecraft:anvil";
            case "ritual" -> "minecraft:nether_star";
            default -> "minecraft:book";
        };
    }

    private static String gemName(Node g) {
        if (g.gemType == RaceTreeLayout.GemType.ATTRIBUTES) return I18n.get("ui.rites_and_paths.race.tree.attributes");
        return I18n.get(Ids.langKey("mechanic", g.effects.get(0).params().get("id").getAsString(), "name"));
    }

    // --- Estado de cada nodo ---

    @Override
    public int rank() {
        return Math.max(0, Math.min(ProgressionRules.RACE_MAX_RANK, state().raceRank));
    }

    @Override
    public Status status(Node n) {
        int rank = rank();
        return switch (n.kind) {
            case CORE, SUB_KEY -> Status.OWNED;
            case RITE -> rank >= n.rank ? Status.OWNED : rank == n.rank - 1 ? Status.AVAILABLE : Status.FUTURE;
            case GEM -> rank >= n.rank ? Status.OWNED : Status.FUTURE;
        };
    }

    @Override
    public float progress(Node n) {
        if (n.rite == null || n.rite.objectives().isEmpty()) return 0;
        float sum = 0;
        for (int i = 0; i < n.rite.objectives().size(); i++) sum += objectiveFraction(n.rite.objectives().get(i), i);
        return sum / n.rite.objectives().size();
    }

    private static float objectiveFraction(ObjectiveDef o, int index) {
        int target = Math.max(1, RiteProgress.target(o));
        return Math.min(1f, RiteProgress.value(o, index, state().riteProgress) / (float) target);
    }

    // --- Dibujo ---

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0 : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        time += dt;
        hintTime += dt;
        if (layout == null) return;
        cam.update(dt, layout);
        hover = dragging || !cam.contains(mouseX, mouseY) ? null : nodeAt(mouseX, mouseY);
        painter.update(dt, hover);
        g.enableScissor(cam.viewX0, cam.viewY0, cam.viewX1, cam.viewY1);
        canvas.begin(g, font);
        painter.paint(canvas, layout, this, cam, time, hover, hud(), hintAlpha());
        canvas.end();
        g.disableScissor();
    }

    private float hintAlpha() {
        return hintTime < 7 ? 1 : Math.max(0, 1 - (hintTime - 7) / 1.5f);
    }

    private RaceTreePainter.Hud hud() {
        int rank = rank();
        String title = I18n.get(Ids.langKey("race", race.id(), "name"));
        String rankText = I18n.get("ui.rites_and_paths.race.tree.rank", rank, ProgressionRules.RACE_MAX_RANK);
        List<RaceTreePainter.Line> lines = new ArrayList<>();
        String riteTitle;
        RiteDef rite = sub.riteFor(rank);
        if (rank >= ProgressionRules.RACE_MAX_RANK || rite == null) {
            riteTitle = I18n.get("ui.rites_and_paths.race.tree.maxed");
        } else {
            riteTitle = I18n.get("msg.rites_and_paths.rite.header", rank + 1, I18n.get(riteKey(rite)));
            for (int i = 0; i < rite.objectives().size(); i++) lines.add(objectiveLine(rite, i, true));
        }
        return new RaceTreePainter.Hud(title, subraceName(race, sub), rankText, riteTitle, lines, I18n.get("ui.rites_and_paths.race.tree.hint"));
    }

    /** "✔ Golpes recibidos 100/100" o "• Enemigos derrotados 12/30" para el progreso real del rito en curso. */
    private RaceTreePainter.Line objectiveLine(RiteDef rite, int i, boolean withProgress) {
        ObjectiveDef o = rite.objectives().get(i);
        int target = RiteProgress.target(o);
        int value = withProgress ? Math.min(RiteProgress.value(o, i, state().riteProgress), target) : 0;
        boolean done = withProgress && value >= target;
        String text = I18n.get(done ? "msg.rites_and_paths.rite.done_mark" : "msg.rites_and_paths.rite.todo_mark") + " "
                + I18n.get(riteKey(rite) + ".o" + i) + " " + value + "/" + target;
        return new RaceTreePainter.Line(text, done ? OK : TEXT);
    }

    private Node nodeAt(double mx, double my) {
        Node best = null;
        double bestD = Double.MAX_VALUE;
        for (Node n : layout.nodes) {
            double dx = cam.sx(n.x) - mx, dy = cam.sy(n.y) - my;
            double d = Math.sqrt(dx * dx + dy * dy);
            double hit = Math.max(n.r, 6) * cam.zoom + 2.5;
            if (d <= hit && d < bestD) {
                best = n;
                bestD = d;
            }
        }
        return best;
    }

    // --- Ayudas ---

    public void renderTooltip(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (layout == null || dragging || hover == null) return;
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component c : tooltip(hover)) {
            if (c.getString().isEmpty()) out.add(FormattedCharSequence.EMPTY);
            else out.addAll(font.split(c, 240));
        }
        g.renderTooltip(font, out, mouseX, mouseY);
    }

    private static Style color(int argb) {
        return Style.EMPTY.withColor(TextColor.fromRgb(argb & 0xFFFFFF));
    }

    private List<Component> tooltip(Node n) {
        List<Component> out = new ArrayList<>();
        int rank = rank();
        switch (n.kind) {
            case CORE -> {
                out.add(Component.translatable(Ids.langKey("race", race.id(), "name")).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                out.add(Component.translatable("ui.rites_and_paths.race.tree.rank", rank, ProgressionRules.RACE_MAX_RANK).withStyle(ChatFormatting.GREEN));
                out.add(Component.translatable(Ids.langKey("race", race.id(), "desc")).withStyle(ChatFormatting.GRAY));
            }
            case SUB_KEY -> {
                out.add(Component.literal(subraceName(race, sub)).withStyle(color(n.color)).withStyle(ChatFormatting.BOLD));
                out.add(Component.translatable(Ids.langKey("subrace", race.id() + "." + sub.id(), "desc")).withStyle(ChatFormatting.GRAY));
            }
            case RITE -> riteTooltip(n, rank, out);
            case GEM -> gemTooltip(n, rank, out);
        }
        return out;
    }

    private void riteTooltip(Node n, int rank, List<Component> out) {
        RiteDef rite = n.rite;
        Status st = status(n);
        if (rite == null) {
            out.add(Component.translatable("msg.rites_and_paths.rite.header", n.rank, "?").withStyle(color(n.color)).withStyle(ChatFormatting.BOLD));
            return;
        }
        out.add(Component.translatable("msg.rites_and_paths.rite.header", n.rank, Component.translatable(riteKey(rite))).withStyle(color(n.color)).withStyle(ChatFormatting.BOLD));
        out.add(Component.translatable(riteKey(rite) + ".desc").withStyle(ChatFormatting.GRAY));
        out.add(Component.empty());
        for (int i = 0; i < rite.objectives().size(); i++) {
            ObjectiveDef o = rite.objectives().get(i);
            int target = RiteProgress.target(o);
            int value = st == Status.OWNED ? target : st == Status.AVAILABLE ? Math.min(RiteProgress.value(o, i, state().riteProgress), target) : 0;
            boolean done = value >= target;
            out.add(Component.translatable("msg.rites_and_paths.rite.objective",
                    Component.translatable(done ? "msg.rites_and_paths.rite.done_mark" : "msg.rites_and_paths.rite.todo_mark"),
                    Component.translatable(riteKey(rite) + ".o" + i), value, target).withStyle(done ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        }
        out.add(Component.empty());
        switch (st) {
            case OWNED -> out.add(Component.translatable("ui.rites_and_paths.race.tree.rite_done").withStyle(ChatFormatting.GREEN));
            case AVAILABLE -> {
                out.add(Component.translatable("ui.rites_and_paths.race.tree.rite_current").withStyle(ChatFormatting.YELLOW));
                out.add(Component.translatable("msg.rites_and_paths.rite.hint").withStyle(ChatFormatting.DARK_GRAY));
            }
            default -> out.add(Component.translatable("ui.rites_and_paths.race.tree.rite_locked").withStyle(ChatFormatting.RED));
        }
        // Qué se gana con este rango.
        List<String> unlocks = new ArrayList<>();
        for (Node g : layout.gems) if (g.rank == n.rank && g.gemType == RaceTreeLayout.GemType.MECHANIC) unlocks.add(g.label);
        if (!unlocks.isEmpty()) {
            out.add(Component.empty());
            out.add(Component.translatable("ui.rites_and_paths.race.tree.unlocks", String.join(", ", unlocks)).withStyle(ChatFormatting.AQUA));
        }
        List<Component> upgrades = upgrades(n.rank);
        if (!upgrades.isEmpty()) {
            out.add(Component.translatable("ui.rites_and_paths.race.tree.upgrades").withStyle(ChatFormatting.AQUA));
            out.addAll(upgrades);
        }
    }

    private void gemTooltip(Node g, int rank, List<Component> out) {
        Status st = status(g);
        MutableComponent title = Component.literal(g.label).withStyle(color(g.trunk ? TreeLayout.GEM_COLOR : g.color)).withStyle(ChatFormatting.BOLD);
        out.add(title);
        for (EffectDef e : g.effects) {
            if (Ids.EFFECT_ATTRIBUTE.equals(e.type())) {
                RaceScreen.attributeLines(e, out);
            } else {
                JsonObject p = e.params();
                String id = p.get("id").getAsString();
                out.add(Component.translatable(Ids.langKey("mechanic", id, "desc")).withStyle(ChatFormatting.GRAY));
                RaceScreen.paramLines(p, out);
            }
        }
        out.add(Component.empty());
        if (st == Status.OWNED) out.add(Component.translatable("ui.rites_and_paths.tree.unlocked").withStyle(ChatFormatting.GREEN));
        else out.add(Component.translatable("ui.rites_and_paths.race.tree.gem_locked", g.rank).withStyle(ChatFormatting.GRAY));
    }

    /** Lo que mejora al llegar al Rango {@code rank}: valores por Rango de la raza y la subraza que cambian respecto al anterior. */
    private List<Component> upgrades(int rank) {
        List<Component> out = new ArrayList<>();
        List<EffectDef> all = new ArrayList<>(race.effects());
        all.addAll(sub.effects());
        for (EffectDef e : all) {
            if (Ids.EFFECT_ATTRIBUTE.equals(e.type())) {
                AttributeEffect parsed;
                try {
                    parsed = AttributeEffect.parse(e.params(), StatCalculator.RACE_RANKS);
                } catch (RuntimeException ex) {
                    continue;
                }
                for (AttributeEffect.Modifier m : parsed.modifiers()) {
                    if (m.attribute().equals(Ids.NS + ":leaf_walker")) continue;
                    upgradeLine(out, ClassPages.attributeName(m.attribute()), m.value(), rank, ClassPages.isPercent(m) ? "%" : "", true);
                }
                continue;
            }
            JsonObject p = e.params();
            if (rankOf(e) >= rank) continue; // la mecánica aún no existía: se ve como habilidad nueva
            for (Map.Entry<String, JsonElement> entry : p.entrySet()) {
                if (!entry.getValue().isJsonObject() || "min_rank".equals(entry.getKey()) || "effects".equals(entry.getKey())) continue;
                LevelValue value;
                try {
                    value = LevelValue.fromJson(entry.getValue().getAsJsonObject(), StatCalculator.RACE_RANKS);
                } catch (RuntimeException ignored) {
                    continue;
                }
                Component label = Component.translatableWithFallback("param.rites_and_paths." + entry.getKey(), entry.getKey());
                upgradeLine(out, label, value, rank, ClassPages.units().getOrDefault(entry.getKey(), ""), false);
            }
        }
        return out;
    }

    private static void upgradeLine(List<Component> out, Component label, LevelValue value, int rank, String unit, boolean signed) {
        double before = value.at(rank), after = value.at(rank + 1); // nivel = Rango + 1
        if (Math.abs(before - after) < 1e-9) return;
        out.add(Component.literal("  ").append(label.copy().withStyle(ChatFormatting.GRAY)).append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(ClassPages.format(before, unit, signed) + " → ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(ClassPages.format(after, unit, signed)).withStyle(ChatFormatting.GREEN)));
    }

    // --- Ratón y teclado ---

    public boolean contains(double mx, double my) {
        return layout != null && cam.contains(mx, my);
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!contains(mx, my)) return false;
        hintTime = Math.max(hintTime, 7);
        pressX = mx;
        pressY = my;
        pressButton = button;
        dragging = false;
        return true;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (pressButton < 0) return false;
        if (!dragging && Math.hypot(mx - pressX, my - pressY) > DRAG_THRESHOLD) dragging = true;
        if (dragging) cam.pan(dx, dy, layout);
        return true;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (pressButton < 0) return false;
        boolean wasClick = !dragging && button == pressButton;
        pressButton = -1;
        dragging = false;
        if (wasClick && layout != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            Node n = nodeAt(pressX, pressY);
            if (n != null) {
                cam.glideTo(n.x, n.y, -1);
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f));
            }
        }
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!contains(mx, my)) return false;
        cam.zoomAt(mx, my, (float) Math.pow(1.15, delta));
        return true;
    }

    public boolean keyPressed(int key) {
        if (layout == null) return false;
        float step = 40;
        switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> cam.pan(step, 0, layout);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> cam.pan(-step, 0, layout);
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> cam.pan(0, step, layout);
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> cam.pan(0, -step, layout);
            case GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_SPACE -> cam.home(layout, true);
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> cam.zoomAt(cam.centerScreenX(), cam.centerScreenY(), 1.15f);
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> cam.zoomAt(cam.centerScreenX(), cam.centerScreenY(), 1 / 1.15f);
            default -> {
                return false;
            }
        }
        return true;
    }
}
