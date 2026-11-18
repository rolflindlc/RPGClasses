package io.github.rpgclasses.client.tree;

import io.github.rpgclasses.client.ClassPages;
import io.github.rpgclasses.client.tree.TreeLayout.Kind;
import io.github.rpgclasses.client.tree.TreeLayout.Node;
import io.github.rpgclasses.client.tree.TreePlan.Step;
import io.github.rpgclasses.client.tree.TreeStatus.Status;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.definition.Definitions.AbilityDef;
import io.github.rpgclasses.core.definition.Definitions.BranchDef;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.MilestoneDef;
import io.github.rpgclasses.core.definition.Definitions.PerkDef;
import io.github.rpgclasses.core.definition.Definitions.SubclassDef;
import io.github.rpgclasses.core.progression.ProgressState;
import io.github.rpgclasses.core.progression.ProgressionRules;
import io.github.rpgclasses.core.progression.XpMath;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Árbol de habilidades interactivo de la pantalla de clases: se arrastra con el ratón, se acerca con la rueda
 * y los clics forman un plan que solo se aplica al confirmarlo. El dibujo lo hace {@link TreePainter}; esta
 * clase pone Minecraft (ratón, teclado, sonidos, textos y ayudas) y no decide nada: el servidor valida el plan.
 */
public final class SkillTreeView {
    /** Píxeles que hay que mover el ratón con el botón pulsado para que sea arrastre y no clic. */
    private static final double DRAG_THRESHOLD = 4;
    /** Cámara de cada clase, para volver a donde estabas al reabrir el libro. */
    private static final Map<String, float[]> CAMERAS = new HashMap<>();

    private final TreeCamera cam = new TreeCamera();
    private final TreePainter painter = new TreePainter();
    private final TreeFx fx = new TreeFx();
    private final McCanvas canvas = new McCanvas();
    private final TreePlan plan = new TreePlan();

    private TreeLayout layout;
    private ClassDef def;
    private boolean editable;
    private LongSupplier xp = () -> 0;
    private double multiplier = 1;

    private long lastNanos;
    private float time;
    private float hintTime;
    private Node hover;
    private double pressX, pressY;
    private int pressButton = -1;
    private boolean dragging, minimapDrag;
    /** Último estado real visto, para lanzar los destellos cuando el servidor confirma algo. */
    private ProgressState seen;

    public TreePlan plan() {
        return plan;
    }

    public boolean editable() {
        return editable;
    }

    public ClassDef def() {
        return def;
    }

    /**
     * Prepara la vista para una clase. Se llama en cada {@code init} de la pantalla: si la clase no cambia,
     * se conservan el plan, la cámara y las animaciones.
     */
    public void setup(ClassDef def, boolean editable, int x0, int y0, int x1, int y1, LongSupplier xp, double multiplier) {
        this.xp = xp;
        this.multiplier = multiplier;
        boolean changed = this.def == null || def == null || this.def != def || this.editable != editable || layout == null;
        cam.setView(x0, y0, x1, y1);
        if (!changed) {
            plan.prune(context());
            return;
        }
        if (this.def != null && layout != null) CAMERAS.put(this.def.id(), new float[]{cam.x, cam.y, cam.zoom});
        this.def = def;
        this.editable = editable;
        plan.clear();
        fx.clear();
        hover = null;
        seen = null;
        if (def == null) {
            layout = null;
            return;
        }
        List<SubclassDef> subs = new ArrayList<>();
        for (String s : def.subclasses()) subs.add(DefinitionStore.CLIENT.subclassDef(s));
        layout = new TreeLayout(def, subs);
        label(layout);
        float[] saved = CAMERAS.get(def.id());
        if (saved != null) cam.set(saved[0], saved[1], saved[2]);
        else cam.home(layout, false);
        cam.clamp(layout);
    }

    /** Al cerrar la pantalla: recuerda la cámara. */
    public void saveCamera() {
        if (def != null && layout != null) CAMERAS.put(def.id(), new float[]{cam.x, cam.y, cam.zoom});
    }

    private void label(TreeLayout l) {
        for (Node n : l.nodes) {
            switch (n.kind) {
                case KEYSTONE -> n.label = ClassPages.branchTitle(def, def.branches().get(n.index)).getString();
                case PERK -> {
                    BranchDef b = def.branches().get(n.index);
                    n.label = ClassPages.perkName(def, b, b.perks().get(n.level)).getString();
                }
                case SUB_KEY -> n.label = I18n.get(Ids.langKey("subclass", def.subclasses().get(n.index), "name"));
                default -> { }
            }
        }
    }

    private ProgressState real() {
        return io.github.rpgclasses.client.ClientHandlers.LOCAL_DATA.state;
    }

    public TreePlan.Context context() {
        return new TreePlan.Context(real(), def, DefinitionStore.CLIENT::subclassDef, xp.getAsLong(), multiplier);
    }

    public TreePlan.Simulation simulate() {
        return plan.simulate(context());
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

        TreePlan.Context ctx = editable ? context() : null;
        TreePlan.Simulation sim = editable ? plan.simulate(ctx) : null;
        TreeStatus status = new TreeStatus(layout, ctx, sim, editable);
        if (editable) detectChanges(status);

        hover = dragging || !cam.contains(mouseX, mouseY) || overHud(mouseX, mouseY) ? null : nodeAt(mouseX, mouseY);
        painter.update(dt, hover);
        Set<Integer> lockPreview = lockPreview(hover, status);

        g.enableScissor(cam.viewX0, cam.viewY0, cam.viewX1, cam.viewY1);
        canvas.begin(g, font);
        painter.paint(canvas, layout, status, editable ? real() : null, cam, fx, time, hover, lockPreview, hud(status), hintAlpha());
        canvas.end();
        g.disableScissor();
    }

    private float hintAlpha() {
        return hintTime < 7 ? 1 : Math.max(0, 1 - (hintTime - 7) / 1.5f);
    }

    private TreePainter.Hud hud(TreeStatus status) {
        String title = I18n.get(Ids.langKey("class", def.id(), "name"));
        int totalMax = ProgressionRules.BASE_MAX + ProgressionRules.SUBCLASS_MAX;
        String hint = I18n.get(editable ? "ui.rites_and_paths.tree.hint" : "ui.rites_and_paths.tree.hint_preview");
        String subHub = I18n.get("ui.rites_and_paths.tree.subclass");
        if (!editable) {
            return new TreePainter.Hud(title, "", null, null, null, "", hint, I18n.get("ui.rites_and_paths.tree.preview"),
                    I18n.get("ui.rites_and_paths.tree.bridge_preview", ProgressionRules.SUBCLASS_UNLOCK), subHub);
        }
        ProgressState real = real();
        ProgressState s = status.sim.state;
        String level = I18n.get("ui.rites_and_paths.tree.level", real.totalLevel(), totalMax);
        String planned = s.totalLevel() > real.totalLevel() ? I18n.get("ui.rites_and_paths.tree.level_planned", s.totalLevel()) : null;
        String xpLine = ProgressionRules.isMaxed(s)
                ? I18n.get("ui.rites_and_paths.tree.xp_max", status.sim.xpLeft)
                : I18n.get("ui.rites_and_paths.tree.xp", status.sim.xpLeft, ProgressionRules.nextCost(s, multiplier));
        String planLine = plan.isEmpty() ? null : I18n.get("ui.rites_and_paths.tree.plan", status.sim.valid.size(), status.sim.cost);
        String bridge = real.basePoints() < ProgressionRules.SUBCLASS_UNLOCK
                ? I18n.get("ui.rites_and_paths.tree.bridge", Math.min(ProgressionRules.SUBCLASS_UNLOCK, s.basePoints()), ProgressionRules.SUBCLASS_UNLOCK)
                : null;
        return new TreePainter.Hud(title, level, planned, xpLine, planLine, I18n.get("ui.rites_and_paths.tree.branches"), hint, null, bridge, subHub);
    }

    /** Destellos cuando el estado real cambia (el servidor ha confirmado compras). */
    private void detectChanges(TreeStatus status) {
        ProgressState real = real();
        if (seen == null || !java.util.Objects.equals(seen.classId, real.classId)) {
            seen = copy(real);
            return;
        }
        float t = time;
        int delay = 0;
        for (int k = 0; k < def.branches().size(); k++) {
            BranchDef b = def.branches().get(k);
            int before = seen.pointsIn(b.id()), after = real.pointsIn(b.id());
            for (int i = before + 1; i <= after && i <= layout.points.get(k).size(); i++) {
                Node n = layout.points.get(k).get(i - 1);
                fx.add(TreeFx.Type.GAIN, n.x, n.y, n.r, n.color, t + 0.06f * delay++);
            }
            if (before == 0 && after > 0) {
                Node key = layout.keystones.get(k);
                fx.add(TreeFx.Type.GAIN, key.x, key.y, key.r, key.color, t);
            }
            String perk = real.perkIn(b.id());
            if (perk != null && !perk.equals(seen.perkIn(b.id()))) {
                for (Node n : layout.perks.get(k)) {
                    if (b.perks().get(n.level).id().equals(perk)) fx.add(TreeFx.Type.GAIN, n.x, n.y, n.r + 3, TreeLayout.PERK_COLOR, t + 0.06f * delay++);
                }
            }
            if (!ProgressionRules.isBranchLocked(seen, b.id()) && ProgressionRules.isBranchLocked(real, b.id())) {
                Node key = layout.keystones.get(k);
                fx.add(TreeFx.Type.LOCK, key.x, key.y, key.r, key.color, t + 0.2f);
            }
        }
        if (real.subclassId != null && !real.subclassId.equals(seen.subclassId)) {
            int j = def.subclasses().indexOf(real.subclassId);
            if (j >= 0) {
                Node key = layout.subKeys.get(j);
                fx.add(TreeFx.Type.GAIN, key.x, key.y, key.r, key.color, t);
                for (int o = 0; o < layout.subKeys.size(); o++) {
                    if (o != j) fx.add(TreeFx.Type.LOCK, layout.subKeys.get(o).x, layout.subKeys.get(o).y, layout.subKeys.get(o).r, 0, t + 0.2f);
                }
            }
        }
        if (real.subclassId != null) {
            int j = def.subclasses().indexOf(real.subclassId);
            int before = real.subclassId.equals(seen.subclassId) ? seen.subclassLevel : 0;
            if (j >= 0) {
                for (int i = before + 1; i <= real.subclassLevel && i <= layout.subPoints.get(j).size(); i++) {
                    Node n = layout.subPoints.get(j).get(i - 1);
                    fx.add(TreeFx.Type.GAIN, n.x, n.y, n.r, n.color, t + 0.06f * delay++);
                }
            }
        }
        if (seen.basePoints() < ProgressionRules.SUBCLASS_UNLOCK && real.basePoints() >= ProgressionRules.SUBCLASS_UNLOCK && layout.subHub != null) {
            fx.add(TreeFx.Type.GAIN, layout.subHub.x, layout.subHub.y, layout.subHub.r, 0xFF4DE1C1, t + 0.3f);
        }
        if (!sameProgress(seen, real)) seen = copy(real);
    }

    private static boolean sameProgress(ProgressState a, ProgressState b) {
        return a.branchPoints.equals(b.branchPoints) && a.perks.equals(b.perks)
                && java.util.Objects.equals(a.subclassId, b.subclassId) && a.subclassLevel == b.subclassLevel;
    }

    private static ProgressState copy(ProgressState s) {
        ProgressState c = new ProgressState();
        c.copyFrom(s);
        return c;
    }

    /** Ramas que se bloquearían al comprar el nodo bajo el ratón (para teñirlas). */
    private Set<Integer> lockPreview(Node n, TreeStatus status) {
        Set<Integer> out = new HashSet<>();
        if (n == null || !editable || status.sim == null) return out;
        if (n.kind != Kind.POINT && n.kind != Kind.KEYSTONE) return out;
        ProgressState s = status.sim.state;
        BranchDef b = def.branches().get(n.index);
        if (s.pointsIn(b.id()) > 0 || s.usedBranches() != ProgressionRules.BRANCHES_USABLE - 1) return out;
        Status st = status.of(n);
        if (st == Status.LOCKED || st == Status.WILL_LOCK) return out;
        for (int k = 0; k < def.branches().size(); k++) {
            if (k != n.index && s.pointsIn(def.branches().get(k).id()) == 0) out.add(k);
        }
        return out;
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

    private boolean overHud(double mx, double my) {
        return inside(painter.minimap, mx, my) || inside(painter.home, mx, my);
    }

    private static boolean inside(float[] r, double x, double y) {
        return r[2] > r[0] && x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
    }

    // --- Ayudas ---

    public void renderTooltip(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (layout == null || dragging) return;
        List<Component> lines;
        if (inside(painter.home, mouseX, mouseY)) {
            lines = List.of(Component.translatable("ui.rites_and_paths.tree.home"));
        } else if (inside(painter.minimap, mouseX, mouseY)) {
            lines = List.of(Component.translatable("ui.rites_and_paths.tree.minimap"));
        } else if (hover != null) {
            TreePlan.Context ctx = editable ? context() : null;
            TreePlan.Simulation sim = editable ? plan.simulate(ctx) : null;
            lines = tooltip(hover, new TreeStatus(layout, ctx, sim, editable), sim);
        } else {
            return;
        }
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component c : lines) {
            if (c.getString().isEmpty()) out.add(FormattedCharSequence.EMPTY);
            else out.addAll(font.split(c, 230));
        }
        g.renderTooltip(font, out, mouseX, mouseY);
    }

    private static Style color(int argb) {
        return Style.EMPTY.withColor(TextColor.fromRgb(argb & 0xFFFFFF));
    }

    private List<Component> tooltip(Node n, TreeStatus status, TreePlan.Simulation sim) {
        List<Component> out = new ArrayList<>();
        Status st = status.of(n);
        ProgressState real = editable ? real() : null;
        ProgressState s = sim != null ? sim.state : null;
        switch (n.kind) {
            case CORE -> {
                out.add(Component.translatable(Ids.langKey("class", def.id(), "name")).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                if (editable) {
                    out.add(Component.translatable("ui.rites_and_paths.tree.core", real.totalLevel(), ProgressionRules.BASE_MAX + ProgressionRules.SUBCLASS_MAX,
                            real.basePoints(), ProgressionRules.BASE_MAX, real.subclassLevel, ProgressionRules.SUBCLASS_MAX).withStyle(ChatFormatting.GRAY));
                } else if (!def.roles().isEmpty()) {
                    out.add(Component.translatable("ui.rites_and_paths.roles", String.join(", ", def.roles())).withStyle(ChatFormatting.GRAY));
                }
                out.add(Component.translatable("ui.rites_and_paths.tree.core_note").withStyle(ChatFormatting.DARK_GRAY));
            }
            case KEYSTONE, POINT -> {
                BranchDef b = def.branches().get(n.index);
                if (n.kind == Kind.KEYSTONE) {
                    out.add(ClassPages.branchTitle(def, b).withStyle(color(n.color)).withStyle(ChatFormatting.BOLD));
                    out.add(ClassPages.branchDesc(def, b).copy().withStyle(ChatFormatting.GRAY));
                    if (!b.perks().isEmpty()) {
                        List<String> names = new ArrayList<>();
                        for (PerkDef p : b.perks()) names.add(ClassPages.perkName(def, b, p).getString());
                        out.add(Component.translatable("ui.rites_and_paths.tree.perks_at_end", String.join(" / ", names)).withStyle(ChatFormatting.LIGHT_PURPLE));
                    }
                    out.add(Component.translatable("ui.rites_and_paths.tree.at_max").withStyle(ChatFormatting.DARK_GRAY));
                    out.addAll(ClassPages.levelLines(b.effects(), b.maxPoints(), b.maxPoints(), false));
                } else {
                    out.add(Component.translatable("ui.rites_and_paths.tree.point", ClassPages.branchTitle(def, b), n.level, b.maxPoints())
                            .withStyle(color(n.color)).withStyle(ChatFormatting.BOLD));
                    out.addAll(ClassPages.levelLines(b.effects(), b.maxPoints(), n.level, true));
                }
                if (editable) {
                    int target = n.kind == Kind.KEYSTONE ? s.pointsIn(b.id()) + 1 : n.level;
                    statusLines(out, st, s.pointsIn(b.id()), target, b.maxPoints(), sim, false);
                    Set<Integer> locks = lockPreview(n, status);
                    if (!locks.isEmpty()) {
                        List<String> names = new ArrayList<>();
                        for (int k : locks) names.add(ClassPages.branchTitle(def, def.branches().get(k)).getString());
                        out.add(Component.translatable("ui.rites_and_paths.tree.locks_others", String.join(", ", names)).withStyle(ChatFormatting.RED));
                    }
                }
            }
            case PERK -> {
                BranchDef b = def.branches().get(n.index);
                PerkDef p = b.perks().get(n.level);
                out.addAll(ClassPages.perkLines(def, b, p));
                out.set(0, ClassPages.perkName(def, b, p).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
                if (editable) {
                    out.add(Component.empty());
                    String key = switch (st) {
                        case OWNED -> "ui.rites_and_paths.tree.perk_owned";
                        case PLANNED -> "ui.rites_and_paths.tree.planned";
                        case AVAILABLE -> "ui.rites_and_paths.tree.perk_pick";
                        case DISCARDED -> "ui.rites_and_paths.tree.perk_other";
                        case LOCKED -> "ui.rites_and_paths.branch_locked";
                        case WILL_LOCK -> "ui.rites_and_paths.tree.will_lock";
                        default -> "ui.rites_and_paths.tree.perk_fill";
                    };
                    out.add(Component.translatable(key).withStyle(statusColor(st)));
                    if (st == Status.AVAILABLE || st == Status.PLANNED) {
                        out.add(Component.translatable("ui.rites_and_paths.confirm_perk_text").withStyle(ChatFormatting.DARK_GRAY));
                    }
                }
            }
            case GEM -> {
                for (MilestoneDef m : n.milestones) ability(out, m);
                if (editable) {
                    if (st == Status.OWNED) out.add(Component.translatable("ui.rites_and_paths.tree.unlocked").withStyle(ChatFormatting.GREEN));
                    else if (st == Status.PLANNED) out.add(Component.translatable("ui.rites_and_paths.tree.unlock_plan").withStyle(ChatFormatting.LIGHT_PURPLE));
                    else out.add(Component.translatable("ui.rites_and_paths.tree.levels_left", n.level - s.totalLevel()).withStyle(ChatFormatting.GRAY));
                }
            }
            case SUB_HUB -> {
                out.add(Component.translatable("ui.rites_and_paths.tree.subclass").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
                out.add(Component.translatable("ui.rites_and_paths.subclass_note").withStyle(ChatFormatting.GRAY));
                if (editable) {
                    if (s.basePoints() < ProgressionRules.SUBCLASS_UNLOCK) {
                        out.add(Component.translatable("ui.rites_and_paths.subclass_locked", ProgressionRules.SUBCLASS_UNLOCK, real.basePoints()).withStyle(ChatFormatting.YELLOW));
                    } else {
                        out.add(Component.translatable("ui.rites_and_paths.tree.sub_hub", s.subclassId == null ? 0 : s.subclassLevel, ProgressionRules.SUBCLASS_MAX,
                                ProgressionRules.subclassCap(s.basePoints())).withStyle(ChatFormatting.GRAY));
                    }
                }
            }
            case SUB_KEY -> {
                String subId = def.subclasses().get(n.index);
                SubclassDef sd = DefinitionStore.CLIENT.subclassDef(subId);
                out.add(Component.translatable(Ids.langKey("subclass", subId, "name")).withStyle(color(n.color)).withStyle(ChatFormatting.BOLD));
                out.add(Component.translatable(Ids.langKey("subclass", subId, "desc")).withStyle(ChatFormatting.GRAY));
                if (sd != null) {
                    for (MilestoneDef m : sd.milestones()) {
                        out.add(Component.literal("◆ ").withStyle(ChatFormatting.AQUA)
                                .append(Component.translatable(Ids.langKey("ability", m.grants(), "name")).withStyle(ChatFormatting.WHITE))
                                .append(Component.literal(" (").append(Component.translatable(m.subclassLevel()
                                        ? "ui.rites_and_paths.unlock_sub" : "ui.rites_and_paths.unlock_total", m.level())).append(")").withStyle(ChatFormatting.DARK_GRAY)));
                    }
                }
                if (editable) {
                    String key = switch (st) {
                        case OWNED -> "ui.rites_and_paths.tree.sub_owned";
                        case PLANNED -> "ui.rites_and_paths.tree.planned";
                        case AVAILABLE -> "ui.rites_and_paths.tree.sub_pick";
                        case LOCKED -> "ui.rites_and_paths.tree.sub_other";
                        case WILL_LOCK -> "ui.rites_and_paths.tree.sub_other_plan";
                        default -> null;
                    };
                    if (key != null) out.add(Component.translatable(key).withStyle(statusColor(st)));
                    else out.add(Component.translatable("ui.rites_and_paths.subclass_locked", ProgressionRules.SUBCLASS_UNLOCK, real.basePoints()).withStyle(ChatFormatting.YELLOW));
                    if (st == Status.AVAILABLE || st == Status.PLANNED) {
                        out.add(Component.translatable("ui.rites_and_paths.confirm_subclass_text").withStyle(ChatFormatting.DARK_GRAY));
                    }
                }
            }
            case SUB_POINT -> {
                String subId = def.subclasses().get(n.index);
                SubclassDef sd = DefinitionStore.CLIENT.subclassDef(subId);
                out.add(Component.translatable("ui.rites_and_paths.tree.sub_level", Component.translatable(Ids.langKey("subclass", subId, "name")),
                        n.level, ProgressionRules.SUBCLASS_MAX).withStyle(color(n.color)).withStyle(ChatFormatting.BOLD));
                if (sd != null) out.addAll(ClassPages.levelLines(sd.effects(), ProgressionRules.SUBCLASS_MAX, n.level, true));
                for (MilestoneDef m : n.milestones) ability(out, m);
                if (editable) {
                    if (st == Status.CAPPED) {
                        out.add(Component.translatable("ui.rites_and_paths.tree.sub_capped", TreeStatus.baseNeededForSubclassLevel(n.level)).withStyle(ChatFormatting.YELLOW));
                    } else if (st == Status.LOCKED) {
                        out.add(Component.translatable("ui.rites_and_paths.tree.sub_other").withStyle(ChatFormatting.RED));
                    } else if (st == Status.WILL_LOCK) {
                        out.add(Component.translatable("ui.rites_and_paths.tree.sub_other_plan").withStyle(ChatFormatting.RED));
                    } else if (s.subclassId == null) {
                        out.add(Component.translatable(s.basePoints() >= ProgressionRules.SUBCLASS_UNLOCK ? "ui.rites_and_paths.tree.sub_first"
                                : "ui.rites_and_paths.tree.sub_sealed").withStyle(ChatFormatting.GRAY));
                    } else {
                        statusLines(out, st, s.subclassLevel, n.level, ProgressionRules.SUBCLASS_MAX, sim, true);
                    }
                }
            }
        }
        return out;
    }

    private void ability(List<Component> out, MilestoneDef m) {
        AbilityDef ability = DefinitionStore.CLIENT.abilityDef(m.grants());
        boolean passive = ability != null && ability.params().has("passive") && ability.params().get("passive").getAsBoolean();
        MutableComponent name = Component.translatable(Ids.langKey("ability", m.grants(), "name")).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        if (passive) name.append(Component.literal(" (").append(Component.translatable("ui.rites_and_paths.passive")).append(")").withStyle(ChatFormatting.GRAY));
        out.add(name);
        out.add(Component.translatable(Ids.langKey("ability", m.grants(), "desc")).withStyle(ChatFormatting.GRAY));
        out.add(Component.translatable(m.subclassLevel() ? "ui.rites_and_paths.tree.milestone_sub" : "ui.rites_and_paths.tree.milestone", m.level())
                .withStyle(ChatFormatting.DARK_AQUA));
        if (m.upgrades() != null) {
            out.add(Component.translatable("ui.rites_and_paths.upgrades", Component.translatable(Ids.langKey("ability", m.upgrades(), "name"))).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** Coste y qué pasa al hacer clic, para un punto de rama o un nivel de subclase. */
    private void statusLines(List<Component> out, Status st, int simPoints, int target, int max, TreePlan.Simulation sim, boolean subclass) {
        out.add(Component.empty());
        switch (st) {
            case OWNED -> out.add(Component.translatable("ui.rites_and_paths.tree.owned").withStyle(ChatFormatting.GREEN));
            case PLANNED -> out.add(Component.translatable("ui.rites_and_paths.tree.planned").withStyle(ChatFormatting.LIGHT_PURPLE));
            case LOCKED -> out.add(Component.translatable(subclass ? "ui.rites_and_paths.tree.sub_other" : "ui.rites_and_paths.branch_locked").withStyle(ChatFormatting.RED));
            case WILL_LOCK -> out.add(Component.translatable("ui.rites_and_paths.tree.will_lock").withStyle(ChatFormatting.RED));
            default -> {
                int needed = Math.max(1, Math.min(target, max) - simPoints);
                int total = sim.state.totalLevel();
                long cost = XpMath.totalCost(total, total + needed, multiplier);
                if (ProgressionRules.isMaxed(sim.state) || total + needed > ProgressionRules.BASE_MAX + ProgressionRules.SUBCLASS_MAX) {
                    out.add(Component.translatable("msg.rites_and_paths.max_level").withStyle(ChatFormatting.GRAY));
                } else if (needed == 1) {
                    out.add(Component.translatable("ui.rites_and_paths.tree.cost", cost).withStyle(ChatFormatting.YELLOW));
                    if (sim.xpLeft >= cost) out.add(Component.translatable("ui.rites_and_paths.tree.click_add").withStyle(ChatFormatting.GRAY));
                    else out.add(Component.translatable("ui.rites_and_paths.tree.need_xp", cost - sim.xpLeft).withStyle(ChatFormatting.RED));
                } else {
                    out.add(Component.translatable("ui.rites_and_paths.tree.click_until", needed, cost).withStyle(ChatFormatting.GRAY));
                    if (sim.xpLeft < cost) out.add(Component.translatable("ui.rites_and_paths.tree.need_xp", cost - sim.xpLeft).withStyle(ChatFormatting.RED));
                }
            }
        }
    }

    private static ChatFormatting statusColor(Status st) {
        return switch (st) {
            case OWNED -> ChatFormatting.GREEN;
            case PLANNED -> ChatFormatting.LIGHT_PURPLE;
            case AVAILABLE -> ChatFormatting.YELLOW;
            case LOCKED, WILL_LOCK -> ChatFormatting.RED;
            default -> ChatFormatting.GRAY;
        };
    }

    // --- Ratón y teclado ---

    public boolean contains(double mx, double my) {
        return layout != null && cam.contains(mx, my);
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!contains(mx, my)) return false;
        hintTime = Math.max(hintTime, 7);
        if (inside(painter.home, mx, my)) {
            cam.home(layout, true);
            click(1.1f);
            return true;
        }
        if (inside(painter.minimap, mx, my)) {
            minimapDrag = true;
            pressButton = button;
            minimapJump(mx, my);
            return true;
        }
        pressX = mx;
        pressY = my;
        pressButton = button;
        dragging = false;
        return true;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (pressButton < 0) return false;
        if (minimapDrag) {
            minimapJump(mx, my);
            return true;
        }
        if (!dragging && Math.hypot(mx - pressX, my - pressY) > DRAG_THRESHOLD) dragging = true;
        if (dragging) cam.pan(dx, dy, layout);
        return true;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (pressButton < 0) return false;
        boolean wasClick = !dragging && !minimapDrag && button == pressButton;
        pressButton = -1;
        dragging = false;
        minimapDrag = false;
        if (wasClick && layout != null) {
            Node n = nodeAt(pressX, pressY);
            if (n != null) onNodeClick(n, button);
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

    private void minimapJump(double mx, double my) {
        float scale = painter.minimapMap[0];
        if (scale <= 0) return;
        float tx = (float) (mx - painter.minimapMap[1]) / scale, ty = (float) (my - painter.minimapMap[2]) / scale;
        cam.set(tx, ty, cam.zoom);
        cam.clamp(layout);
    }

    private void onNodeClick(Node n, int button) {
        boolean right = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        if (n.kind == Kind.CORE || n.kind == Kind.SUB_HUB || n.kind == Kind.GEM || !editable) {
            if (!right) {
                cam.glideTo(n.x, n.y, -1);
                click(1.1f);
            }
            return;
        }
        TreePlan.Context ctx = context();
        List<Step> before = plan.steps();
        TreePlan.Simulation sim = plan.simulate(ctx);
        ProgressState s = sim.state;
        switch (n.kind) {
            case KEYSTONE -> {
                String id = def.branches().get(n.index).id();
                plan.setBranchTarget(ctx, id, s.pointsIn(id) + (right ? -1 : 1));
            }
            case POINT -> {
                String id = def.branches().get(n.index).id();
                plan.setBranchTarget(ctx, id, right ? n.level - 1 : n.level);
            }
            case PERK -> {
                BranchDef b = def.branches().get(n.index);
                String perkId = b.perks().get(n.level).id();
                if (right) {
                    if (perkId.equals(s.perkIn(b.id())) && !perkId.equals(ctx.real().perkIn(b.id()))) plan.removePerk(ctx, b.id());
                } else {
                    plan.choosePerk(ctx, b.id(), perkId);
                }
            }
            case SUB_KEY -> {
                String subId = def.subclasses().get(n.index);
                if (right) {
                    if (subId.equals(s.subclassId) && ctx.real().subclassId == null) plan.removeSubclassChoice(ctx);
                } else {
                    plan.chooseSubclass(ctx, subId);
                }
            }
            case SUB_POINT -> {
                String subId = def.subclasses().get(n.index);
                if (s.subclassId == null && !right) plan.chooseSubclass(ctx, subId);
                if (subId.equals(plan.simulate(ctx).state.subclassId)) plan.setSubclassTarget(ctx, right ? n.level - 1 : n.level);
            }
            default -> { }
        }
        List<Step> after = plan.steps();
        if (after.equals(before)) {
            if (!right) deny();
            return;
        }
        boolean added = after.size() >= before.size() && !right;
        fx.add(TreeFx.Type.PLAN, n.x, n.y, n.r, n.color, time);
        click(added ? 1.25f : 0.85f);
    }

    private static void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    private static void deny() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 0.6f));
    }
}
