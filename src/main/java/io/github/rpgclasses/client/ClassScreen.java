package io.github.rpgclasses.client;

import io.github.rpgclasses.client.tree.SkillTreeView;
import io.github.rpgclasses.client.tree.TreePlan;
import io.github.rpgclasses.config.ServerConfig;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.definition.Definitions.BranchDef;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.progression.ProgressState;
import io.github.rpgclasses.core.progression.ProgressionRules;
import io.github.rpgclasses.core.progression.XpMath;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets.ClassAction;
import io.github.rpgclasses.network.Packets.ClassPlan;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pantalla de clases. En modo selección (al entrar sin clase) no se puede cerrar hasta elegir;
 * en modo libro (tecla) consulta cualquier clase. La pestaña "Árbol" muestra el árbol de habilidades
 * ({@link SkillTreeView}): de cualquier clase como vista previa y, en la propia, para planear puntos,
 * perks y subclase y confirmarlos de una vez; en su ayuda de cada nodo está toda la información de ramas, perks,
 * habilidades y subclases. La pestaña "Raza" muestra el progreso de la raza propia ({@link RaceTreeView}): sus
 * ritos y las habilidades que da cada Rango. Todas las acciones se envían al servidor, que las valida.
 */
public final class ClassScreen extends Screen {
    private static final int LINE_HEIGHT = 10;
    private static final int LEFT_WIDTH = 112;
    /** Cabecera del panel (retrato y nombre) entre las pestañas y el contenido; compacta en pantallas bajas. */
    private int header = 46;
    private static final int TABS_Y = 28;

    private final boolean selecting;
    private final List<String> classIds = new ArrayList<>();
    private String selectedClass;
    private ClassPages.Tab tab;

    private List<FormattedCharSequence> lines = List.of();
    private int scroll;
    private int panelLeft, panelTop, panelRight, panelBottom, headerTop;
    private long lastXp = -1;
    private final SkillTreeView tree = new SkillTreeView();
    /** Árbol de progreso de la raza propia (pestaña Raza). */
    private final RaceTreeView raceTree = new RaceTreeView();
    /** Plan con el que se construyeron los botones del pie (si cambia, se reconstruyen). */
    private List<TreePlan.Step> footerPlan = List.of();

    public ClassScreen(boolean selecting) {
        super(Component.translatable(selecting ? "ui.rites_and_paths.title_select" : "ui.rites_and_paths.title_book"));
        this.selecting = selecting;
        this.tab = selecting ? ClassPages.Tab.SUMMARY : ClassPages.Tab.PROGRESS;
    }

    public boolean isSelecting() {
        return selecting;
    }

    /** El servidor ha enviado datos nuevos: se reconstruye la pantalla con ellos. */
    public void onDataChanged() {
        if (minecraft != null && minecraft.screen == this) rebuildWidgets();
    }

    private ProgressState state() {
        return ClientHandlers.LOCAL_DATA.state;
    }

    /** El árbol se puede editar solo en la clase propia y fuera del modo selección; las demás son vista previa. */
    private boolean treeEditable() {
        return !selecting && selectedClass != null && selectedClass.equals(state().classId);
    }

    /** La XP llega del servidor en un paquete aparte: al cambiar, se actualizan los botones de compra. */
    @Override
    public void tick() {
        long xp = availableXp();
        if (xp != lastXp) {
            lastXp = xp;
            if (tab == ClassPages.Tab.PROGRESS) rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        lastXp = availableXp();
        classIds.clear();
        classIds.addAll(DefinitionStore.CLIENT.classIds());
        if (selectedClass == null || !classIds.contains(selectedClass)) {
            String own = state().classId;
            selectedClass = own != null && classIds.contains(own) ? own : (classIds.isEmpty() ? null : classIds.get(0));
        }

        Portraits.clearCache();
        // Sin raza (o con una que el cliente no conoce) no hay árbol de raza que enseñar.
        boolean hasRace = RaceTreeView.available();
        if (tab == ClassPages.Tab.RACE && !hasRace) tab = selecting ? ClassPages.Tab.SUMMARY : ClassPages.Tab.PROGRESS;
        panelLeft = LEFT_WIDTH + 14;
        header = height >= 330 ? 46 : 24;
        headerTop = TABS_Y + 24;
        panelTop = headerTop + header;
        panelRight = width - 10;
        panelBottom = height - 36;

        // Rejilla de clases: tarjetas con retrato.
        int gridBottom = selecting ? height - 8 : height - 56;
        addPortraitGrid(classIds, gridBottom);

        // Raza propia (permanente): se consulta en su pantalla.
        if (!selecting && !io.github.rpgclasses.core.definition.RaceStore.CLIENT.ids().isEmpty()) {
            addRenderableWidget(Button.builder(Component.translatable("ui.rites_and_paths.race.button"),
                    btn -> minecraft.setScreen(new RaceScreen(RaceScreen.Mode.VIEW))).bounds(8, height - 28, LEFT_WIDTH - 4, 20).build());
        }
        // Aliados (tecla J): quién cuenta como aliado; el resto son enemigos.
        if (!selecting) {
            addRenderableWidget(Button.builder(Component.translatable("ui.rites_and_paths.ally.button"),
                    btn -> minecraft.setScreen(new AllyScreen(this))).bounds(8, height - 50, LEFT_WIDTH - 4, 20).build());
        }

        // Pestañas.
        List<ClassPages.Tab> tabs = new ArrayList<>(List.of(ClassPages.Tab.values()));
        if (!hasRace) tabs.remove(ClassPages.Tab.RACE);
        int tabWidth = (panelRight - panelLeft) / tabs.size();
        for (int i = 0; i < tabs.size(); i++) {
            ClassPages.Tab t = tabs.get(i);
            Button b = Button.builder(Component.translatable("ui.rites_and_paths.tab." + t.name().toLowerCase(Locale.ROOT)), btn -> {
                tab = t;
                scroll = 0;
                rebuildWidgets();
            }).bounds(panelLeft + i * tabWidth, TABS_Y, tabWidth - 2, 20).build();
            b.active = t != tab;
            addRenderableWidget(b);
        }

        // Pie.
        int footerY = height - 28;
        int center = (panelLeft + panelRight) / 2;
        if (selecting && selectedClass != null) {
            Component name = Component.translatable(Ids.langKey("class", selectedClass, "name"));
            addRenderableWidget(Button.builder(Component.translatable("ui.rites_and_paths.choose", name), btn -> confirmChoice())
                    .bounds(center - 90, footerY, 180, 20).build());
        } else if (tab == ClassPages.Tab.PROGRESS && treeEditable()) {
            int w = Math.min(120, (panelRight - panelLeft - 8) / 3);
            ClassDef def = DefinitionStore.CLIENT.classDef(selectedClass);
            tree.setup(def, true, panelLeft + 3, headerTop + 3, panelRight - 3, panelBottom - 3, this::availableXp, costMultiplier());
            footerPlan = tree.plan().steps();
            if (!tree.plan().isEmpty()) {
                // Plan pendiente: descartarlo o confirmarlo (con aviso si bloquea ramas o elige perk o subclase).
                TreePlan.Simulation sim = tree.simulate();
                addRenderableWidget(Button.builder(Component.translatable("ui.rites_and_paths.tree.plan_discard"), btn -> {
                    tree.plan().clear();
                    rebuildWidgets();
                }).bounds(center - w * 3 / 2 - 4, footerY, w, 20).build());
                Button ok = Button.builder(Component.translatable("ui.rites_and_paths.tree.plan_confirm", sim.cost), btn -> confirmPlan())
                        .bounds(center - w / 2, footerY, w, 20).build();
                ok.active = !sim.valid.isEmpty();
                addRenderableWidget(ok);
            } else {
                Button resetSub = Button.builder(Component.translatable("ui.rites_and_paths.respec_subclass"),
                        btn -> confirm("ui.rites_and_paths.respec_subclass", ClassAction.Action.RESPEC_SUBCLASS, "")).bounds(center - w * 3 / 2 - 4, footerY, w, 20).build();
                resetSub.active = state().subclassId != null;
                addRenderableWidget(resetSub);
                addRenderableWidget(Button.builder(Component.translatable("ui.rites_and_paths.respec_class"),
                        btn -> confirm("ui.rites_and_paths.respec_class", ClassAction.Action.RESPEC_CLASS, "")).bounds(center - w / 2, footerY, w, 20).build());
            }
            addRenderableWidget(Button.builder(Component.translatable("ui.rites_and_paths.close"), btn -> onClose())
                    .bounds(center + w / 2 + 4, footerY, w, 20).build());
        } else if (!selecting) {
            addRenderableWidget(Button.builder(Component.translatable("ui.rites_and_paths.close"), btn -> onClose())
                    .bounds(center - 50, footerY, 100, 20).build());
        }

        if (tab == ClassPages.Tab.RACE) {
            lines = List.of();
            raceTree.setup(panelLeft + 3, headerTop + 3, panelRight - 3, panelBottom - 3);
        } else if (tab == ClassPages.Tab.PROGRESS) {
            lines = List.of();
            if (!treeEditable()) {
                tree.setup(DefinitionStore.CLIENT.classDef(selectedClass), false, panelLeft + 3, headerTop + 3, panelRight - 3, panelBottom - 3,
                        this::availableXp, costMultiplier());
            }
        } else {
            rebuildLines();
        }
    }

    /** Dos columnas de tarjetas; si no caben, se encogen y dejan solo el retrato (el nombre va en la ayuda). */
    private void addPortraitGrid(List<String> ids, int bottom) {
        int cols = 2, gap = 2, top = TABS_Y;
        int cellW = (LEFT_WIDTH - 4 - gap) / cols;
        int rows = Math.max(1, (ids.size() + cols - 1) / cols);
        int cellH = Math.max(26, Math.min(52, (bottom - top) / rows - gap));
        boolean names = cellH >= 48;
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            ClassDef def = DefinitionStore.CLIENT.classDef(id);
            PortraitButton b = new PortraitButton(8 + (i % cols) * (cellW + gap), top + (i / cols) * (cellH + gap), cellW, cellH,
                    Component.translatable(Ids.langKey("class", id, "name")), Portraits.classTex(id), def == null ? null : def.icon(),
                    PortraitButton.Layout.VERTICAL, names, btn -> select(id));
            b.active = !id.equals(selectedClass);
            addRenderableWidget(b);
        }
    }

    /** Cabecera del panel: retrato y nombre de la clase; si es la tuya y tienes subclase, también la subclase. */
    private void renderHeader(GuiGraphics g) {
        UiStyle.header(g, panelLeft, headerTop, panelRight, headerTop + header);
        if (selectedClass == null) return;
        ClassDef def = DefinitionStore.CLIENT.classDef(selectedClass);
        boolean big = header >= 46;
        int size = big ? Portraits.TEX : 18;
        int x = panelLeft + 6, y = headerTop + (big ? 5 : 3);
        Portraits.draw(g, Portraits.classTex(selectedClass), def == null ? null : def.icon(), x, y, size);
        Component className = Component.translatable(Ids.langKey("class", selectedClass, "name"));
        if (big) UiStyle.scaledText(g, font, className, x + 44, y + 4, 1.5f, 0xFFFFE08A);
        else g.drawString(font, className, x + 24, y + 5, 0xFFFFE08A);
        if (def != null && !def.roles().isEmpty()) {
            Component roles = Component.translatable("ui.rites_and_paths.roles", String.join(", ", def.roles()));
            if (big) g.drawString(font, roles, x + 44, y + 22, 0xAAAAAA);
            else g.drawString(font, roles, x + 30 + font.width(className), y + 5, 0xAAAAAA);
        }
        String sub = state().subclassId;
        if (!selecting && sub != null && selectedClass.equals(state().classId)) {
            var subDef = DefinitionStore.CLIENT.subclassDef(sub);
            Component name = Component.translatable(Ids.langKey("subclass", sub, "name"));
            int sx = panelRight - 6 - size;
            Portraits.draw(g, Portraits.subclassTex(sub), subDef == null ? null : subDef.icon(), sx, y, size);
            g.drawString(font, name, sx - 6 - font.width(name), y + (big ? 8 : 5), 0x55FFFF);
            if (big) {
                Component label = Component.translatable("ui.rites_and_paths.header_subclass");
                g.drawString(font, label, sx - 6 - font.width(label), y + 20, 0x888888);
            }
        }
    }

    // --- Pestaña de progreso ---

    private long availableXp() {
        return minecraft == null || minecraft.player == null ? 0
                : XpMath.totalPoints(minecraft.player.experienceLevel, minecraft.player.experienceProgress);
    }

    private double costMultiplier() {
        try {
            return ServerConfig.XP_COST_MULTIPLIER.get();
        } catch (IllegalStateException notLoaded) {
            return 1.0;
        }
    }

    // --- Plan del árbol ---

    /** Envía el plan; si bloquea ramas, elige perks o subclase, primero pide confirmación con el resumen. */
    private void confirmPlan() {
        if (minecraft == null) return;
        ClassDef def = DefinitionStore.CLIENT.classDef(selectedClass);
        TreePlan.Simulation sim = tree.simulate();
        if (def == null || sim.valid.isEmpty()) {
            tree.plan().clear();
            rebuildWidgets();
            return;
        }
        List<ClassAction> actions = new ArrayList<>();
        List<String> perks = new ArrayList<>();
        String subclass = null;
        for (TreePlan.Step st : sim.valid) {
            switch (st.type()) {
                case BRANCH -> actions.add(new ClassAction(ClassAction.Action.SPEND_BRANCH, st.branch()));
                case PERK -> {
                    actions.add(new ClassAction(ClassAction.Action.CHOOSE_PERK, ClassAction.perkArg(st.branch(), st.value())));
                    BranchDef b = def.branch(st.branch());
                    if (b != null && b.perk(st.value()) != null) perks.add(ClassPages.perkName(def, b, b.perk(st.value())).getString());
                }
                case CHOOSE_SUBCLASS -> {
                    actions.add(new ClassAction(ClassAction.Action.CHOOSE_SUBCLASS, st.value()));
                    subclass = Component.translatable(Ids.langKey("subclass", st.value(), "name")).getString();
                }
                case SUBCLASS -> actions.add(new ClassAction(ClassAction.Action.SPEND_SUBCLASS, ""));
            }
        }
        List<String> locks = new ArrayList<>();
        for (BranchDef b : def.branches()) {
            if (!ProgressionRules.isBranchLocked(state(), b.id()) && ProgressionRules.isBranchLocked(sim.state, b.id())) {
                locks.add(ClassPages.branchTitle(def, b).getString());
            }
        }
        Runnable send = () -> {
            Net.CHANNEL.sendToServer(new ClassPlan(List.copyOf(actions)));
            tree.plan().clear();
        };
        if (locks.isEmpty() && perks.isEmpty() && subclass == null) {
            send.run();
            rebuildWidgets();
            return;
        }
        MutableComponent text = Component.translatable("ui.rites_and_paths.tree.confirm_points", sim.points, sim.cost);
        if (!locks.isEmpty()) text.append("\n").append(Component.translatable("ui.rites_and_paths.tree.confirm_locks", String.join(", ", locks)));
        if (!perks.isEmpty()) text.append("\n").append(Component.translatable("ui.rites_and_paths.tree.confirm_perks", String.join(", ", perks)));
        if (subclass != null) text.append("\n").append(Component.translatable("ui.rites_and_paths.tree.confirm_subclass", subclass));
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) send.run();
            minecraft.setScreen(this);
        }, Component.translatable("ui.rites_and_paths.tree.confirm_title"), text));
    }

    /** Tras un clic en el árbol: si el plan ha cambiado, los botones del pie (confirmar, coste) también. */
    private void refreshPlanButtons() {
        if (tab == ClassPages.Tab.PROGRESS && treeEditable() && !tree.plan().steps().equals(footerPlan)) rebuildWidgets();
    }

    // --- Acciones ---

    private void send(ClassAction.Action action, String arg) {
        Net.CHANNEL.sendToServer(new ClassAction(action, arg));
    }

    private void confirm(String titleKey, ClassAction.Action action, String arg) {
        confirmWith(Component.translatable(titleKey), Component.translatable("ui.rites_and_paths.respec_text", lossPercent()), action, arg);
    }

    private void confirmWith(Component title, Component text, ClassAction.Action action, String arg) {
        if (minecraft == null) return;
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) send(action, arg);
            // Al reiniciar la clase el servidor pedirá elegir de nuevo: ClientSetup abrirá la selección.
            minecraft.setScreen(yes && action == ClassAction.Action.RESPEC_CLASS ? null : this);
        }, title, text));
    }

    private String lossPercent() {
        try {
            return Math.round(ServerConfig.RESPEC_XP_LOSS.get() * 100) + "%";
        } catch (IllegalStateException notLoaded) {
            return "33%";
        }
    }

    private void select(String id) {
        selectedClass = id;
        if (tab == ClassPages.Tab.RACE) tab = ClassPages.Tab.PROGRESS; // la raza es la tuya: elegir una clase vuelve a su árbol
        scroll = 0;
        rebuildWidgets();
    }

    private void confirmChoice() {
        String id = selectedClass;
        if (id == null) return;
        Component name = Component.translatable(Ids.langKey("class", id, "name"));
        confirmWith(Component.translatable("ui.rites_and_paths.confirm_title", name), Component.translatable("ui.rites_and_paths.confirm_text"),
                ClassAction.Action.CHOOSE_CLASS, id);
    }

    // --- Texto de las demás pestañas ---

    private void rebuildLines() {
        ClassDef def = DefinitionStore.CLIENT.classDef(selectedClass);
        List<FormattedCharSequence> out = new ArrayList<>();
        if (def != null) {
            int wrapWidth = Math.max(60, panelRight - panelLeft - 16);
            for (Component c : ClassPages.build(def, tab)) {
                if (c.getString().isEmpty()) {
                    out.add(FormattedCharSequence.EMPTY);
                } else {
                    out.addAll(font.split(c, wrapWidth));
                }
            }
        }
        lines = out;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private int maxScroll() {
        return Math.max(0, lines.size() * LINE_HEIGHT - (panelBottom - panelTop - 8));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        if (selecting) {
            g.drawCenteredString(font, Component.translatable("ui.rites_and_paths.must_choose"), (panelLeft + panelRight) / 2, 18, 0xAAAAAA);
        }

        UiStyle.panel(g, panelLeft, headerTop, panelRight, panelBottom);
        if (tab == ClassPages.Tab.PROGRESS) {
            tree.render(g, font, mouseX, mouseY);
        } else if (tab == ClassPages.Tab.RACE) {
            raceTree.render(g, font, mouseX, mouseY);
        } else {
            renderHeader(g);
            g.enableScissor(panelLeft, panelTop + 4, panelRight, panelBottom - 4);
            int textY = panelTop + 4 - scroll;
            for (FormattedCharSequence line : lines) {
                if (textY + LINE_HEIGHT > panelTop && textY < panelBottom) {
                    g.drawString(font, line, panelLeft + 6, textY, 0xE0E0E0);
                }
                textY += LINE_HEIGHT;
            }
            g.disableScissor();
            int max = maxScroll();
            if (max > 0) {
                int trackHeight = panelBottom - panelTop;
                int barHeight = Math.max(12, trackHeight * trackHeight / (trackHeight + max));
                int barY = panelTop + (trackHeight - barHeight) * scroll / max;
                g.fill(panelRight - 4, barY, panelRight - 1, barY + barHeight, 0xFFAAAAAA);
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
        if (tab == ClassPages.Tab.PROGRESS) tree.renderTooltip(g, font, mouseX, mouseY);
        else if (tab == ClassPages.Tab.RACE) raceTree.renderTooltip(g, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == ClassPages.Tab.PROGRESS && tree.mouseClicked(mouseX, mouseY, button)) return true;
        if (tab == ClassPages.Tab.RACE && raceTree.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (tab == ClassPages.Tab.PROGRESS && tree.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        if (tab == ClassPages.Tab.RACE && raceTree.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (tab == ClassPages.Tab.PROGRESS && tree.mouseReleased(mouseX, mouseY, button)) {
            refreshPlanButtons();
            return true;
        }
        if (tab == ClassPages.Tab.RACE && raceTree.mouseReleased(mouseX, mouseY, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == ClassPages.Tab.PROGRESS) return tree.mouseScrolled(mouseX, mouseY, delta);
        if (tab == ClassPages.Tab.RACE) return raceTree.mouseScrolled(mouseX, mouseY, delta);
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (delta * LINE_HEIGHT * 3)));
        return true;
    }

    @Override
    public void removed() {
        tree.saveCamera();
        raceTree.saveCamera();
        super.removed();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // En modo selección, Esc abre el menú de pausa (para poder salir del mundo) en lugar de cerrar.
        if (selecting && keyCode == GLFW.GLFW_KEY_ESCAPE && minecraft != null) {
            minecraft.setScreen(new PauseScreen(true));
            return true;
        }
        if (tab == ClassPages.Tab.PROGRESS && tree.keyPressed(keyCode)) return true;
        if (tab == ClassPages.Tab.RACE && raceTree.keyPressed(keyCode)) return true;
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN || keyCode == GLFW.GLFW_KEY_DOWN) {
            return mouseScrolled(0, 0, keyCode == GLFW.GLFW_KEY_DOWN ? -1 : -4);
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_UP) {
            return mouseScrolled(0, 0, keyCode == GLFW.GLFW_KEY_UP ? 1 : 4);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !selecting;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
