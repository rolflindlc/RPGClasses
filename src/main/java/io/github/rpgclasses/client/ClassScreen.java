package io.github.rpgclasses.client;

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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pantalla de clases. En modo selección (al entrar sin clase) no se puede cerrar hasta elegir;
 * en modo libro (tecla) consulta cualquier clase y, en la propia, permite gastar puntos, elegir
 * subclase y reiniciar. Todas las acciones se envían al servidor, que es quien las valida.
 */
public final class ClassScreen extends Screen {
    private static final int LINE_HEIGHT = 10;
    private static final int LEFT_WIDTH = 112;
    private static final int ROW = 24;

    private final boolean selecting;
    private final List<String> classIds = new ArrayList<>();
    private String selectedClass;
    private ClassPages.Tab tab;

    private List<FormattedCharSequence> lines = List.of();
    private int scroll;
    private int panelLeft, panelTop, panelRight, panelBottom;
    private long lastXp = -1;

    public ClassScreen(boolean selecting) {
        super(Component.translatable(selecting ? "ui.rpgclasses.title_select" : "ui.rpgclasses.title_book"));
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

    /** La pestaña de progreso solo existe para la clase propia y fuera del modo selección. */
    private boolean progressAvailable() {
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
        if (tab == ClassPages.Tab.PROGRESS && !progressAvailable()) tab = ClassPages.Tab.SUMMARY;

        panelLeft = LEFT_WIDTH + 14;
        panelTop = 54;
        panelRight = width - 10;
        panelBottom = height - 36;

        // Columna de clases.
        int y = 30;
        for (String id : classIds) {
            Button b = Button.builder(Component.translatable(Ids.langKey("class", id, "name")), btn -> select(id))
                    .bounds(30, y, LEFT_WIDTH - 22, 20).build();
            b.active = !id.equals(selectedClass);
            addRenderableWidget(b);
            y += ROW;
        }

        // Pestañas.
        List<ClassPages.Tab> tabs = new ArrayList<>();
        for (ClassPages.Tab t : ClassPages.Tab.values()) {
            if (t != ClassPages.Tab.PROGRESS || progressAvailable()) tabs.add(t);
        }
        int tabWidth = (panelRight - panelLeft) / tabs.size();
        for (int i = 0; i < tabs.size(); i++) {
            ClassPages.Tab t = tabs.get(i);
            Button b = Button.builder(Component.translatable("ui.rpgclasses.tab." + t.name().toLowerCase(Locale.ROOT)), btn -> {
                tab = t;
                scroll = 0;
                rebuildWidgets();
            }).bounds(panelLeft + i * tabWidth, 30, tabWidth - 2, 20).build();
            b.active = t != tab;
            addRenderableWidget(b);
        }

        // Pie.
        int footerY = height - 28;
        int center = (panelLeft + panelRight) / 2;
        if (selecting && selectedClass != null) {
            Component name = Component.translatable(Ids.langKey("class", selectedClass, "name"));
            addRenderableWidget(Button.builder(Component.translatable("ui.rpgclasses.choose", name), btn -> confirmChoice())
                    .bounds(center - 90, footerY, 180, 20).build());
        } else if (tab == ClassPages.Tab.PROGRESS) {
            int w = Math.min(120, (panelRight - panelLeft - 8) / 3);
            Button resetSub = Button.builder(Component.translatable("ui.rpgclasses.respec_subclass"),
                    btn -> confirm("ui.rpgclasses.respec_subclass", ClassAction.Action.RESPEC_SUBCLASS, "")).bounds(center - w * 3 / 2 - 4, footerY, w, 20).build();
            resetSub.active = state().subclassId != null;
            addRenderableWidget(resetSub);
            addRenderableWidget(Button.builder(Component.translatable("ui.rpgclasses.respec_class"),
                    btn -> confirm("ui.rpgclasses.respec_class", ClassAction.Action.RESPEC_CLASS, "")).bounds(center - w / 2, footerY, w, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("ui.rpgclasses.close"), btn -> onClose())
                    .bounds(center + w / 2 + 4, footerY, w, 20).build());
        } else if (!selecting) {
            addRenderableWidget(Button.builder(Component.translatable("ui.rpgclasses.close"), btn -> onClose())
                    .bounds(center - 50, footerY, 100, 20).build());
        }

        if (tab == ClassPages.Tab.PROGRESS) {
            lines = List.of();
            initProgress();
        } else {
            rebuildLines();
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

    private int branchRowY(int index) {
        return panelTop + 22 + index * ROW;
    }

    private int subclassRowY(int branchCount) {
        return branchRowY(branchCount) + 16;
    }

    private void initProgress() {
        ClassDef def = DefinitionStore.CLIENT.classDef(selectedClass);
        if (def == null) return;
        ProgressState s = state();
        boolean canPay = !ProgressionRules.isMaxed(s) && availableXp() >= ProgressionRules.nextCost(s, costMultiplier());

        for (int i = 0; i < def.branches().size(); i++) {
            BranchDef b = def.branches().get(i);
            Button plus = Button.builder(Component.literal("+"), btn -> send(ClassAction.Action.SPEND_BRANCH, b.id()))
                    .bounds(panelRight - 34, branchRowY(i), 24, 20).build();
            plus.active = canPay && s.pointsIn(b.id()) < b.maxPoints();
            addRenderableWidget(plus);
        }

        int y = subclassRowY(def.branches().size());
        if (s.subclassId != null) {
            Button plus = Button.builder(Component.literal("+"), btn -> send(ClassAction.Action.SPEND_SUBCLASS, ""))
                    .bounds(panelRight - 34, y, 24, 20).build();
            plus.active = canPay && s.subclassLevel < ProgressionRules.subclassCap(s.basePoints());
            addRenderableWidget(plus);
        } else if (s.basePoints() >= ProgressionRules.SUBCLASS_UNLOCK) {
            int count = def.subclasses().size();
            int w = (panelRight - panelLeft - 16) / Math.max(1, count);
            for (int i = 0; i < count; i++) {
                String subId = def.subclasses().get(i);
                Component name = Component.translatable(Ids.langKey("subclass", subId, "name"));
                addRenderableWidget(Button.builder(name, btn -> confirmNamed("ui.rpgclasses.confirm_subclass", name,
                                ClassAction.Action.CHOOSE_SUBCLASS, subId))
                        .bounds(panelLeft + 8 + i * w, y + 12, w - 4, 20).build());
            }
        }
    }

    private void renderProgress(GuiGraphics g) {
        ClassDef def = DefinitionStore.CLIENT.classDef(selectedClass);
        if (def == null) return;
        ProgressState s = state();
        int x = panelLeft + 8;
        Component cost = ProgressionRules.isMaxed(s)
                ? Component.translatable("msg.rpgclasses.max_level")
                : Component.translatable("msg.rpgclasses.next_cost", ProgressionRules.nextCost(s, costMultiplier()));
        g.drawString(font, Component.translatable("ui.rpgclasses.progress_header", s.totalLevel(), availableXp(), cost), x, panelTop + 7, 0xFFFF55);

        for (int i = 0; i < def.branches().size(); i++) {
            BranchDef b = def.branches().get(i);
            int y = branchRowY(i);
            Component name = Component.translatable(Ids.langKey("branch", def.id() + "." + b.id(), "name"));
            g.drawString(font, name, x, y + 6, 0xFFAA00);
            int points = s.pointsIn(b.id());
            // Barra de puntos de la rama.
            int barLeft = x + 90;
            int barRight = panelRight - 78;
            if (barRight > barLeft + 20) {
                g.fill(barLeft, y + 7, barRight, y + 13, 0xFF333333);
                g.fill(barLeft, y + 7, barLeft + (barRight - barLeft) * points / b.maxPoints(), y + 13, 0xFFFFAA00);
            }
            g.drawString(font, points + "/" + b.maxPoints(), panelRight - 72, y + 6, 0xFFFFFF);
        }

        int y = subclassRowY(def.branches().size());
        if (s.subclassId != null) {
            int cap = ProgressionRules.subclassCap(s.basePoints());
            g.drawString(font, Component.translatable(Ids.langKey("subclass", s.subclassId, "name")), x, y + 6, 0x55FFFF);
            g.drawString(font, Component.translatable("ui.rpgclasses.subclass_level", s.subclassLevel, cap), x + 130, y + 6, 0xFFFFFF);
        } else if (s.basePoints() >= ProgressionRules.SUBCLASS_UNLOCK) {
            g.drawString(font, Component.translatable("ui.rpgclasses.subclass_pick"), x, y, 0x55FFFF);
        } else {
            g.drawString(font, Component.translatable("ui.rpgclasses.subclass_locked", ProgressionRules.SUBCLASS_UNLOCK, s.basePoints()), x, y + 6, 0xAAAAAA);
        }
    }

    // --- Acciones ---

    private void send(ClassAction.Action action, String arg) {
        Net.CHANNEL.sendToServer(new ClassAction(action, arg));
    }

    private void confirm(String titleKey, ClassAction.Action action, String arg) {
        confirmWith(Component.translatable(titleKey), Component.translatable("ui.rpgclasses.respec_text", lossPercent()), action, arg);
    }

    private void confirmNamed(String titleKey, Component name, ClassAction.Action action, String arg) {
        confirmWith(Component.translatable(titleKey, name), Component.translatable("ui.rpgclasses.confirm_subclass_text"), action, arg);
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
        scroll = 0;
        rebuildWidgets();
    }

    private void confirmChoice() {
        String id = selectedClass;
        if (id == null) return;
        Component name = Component.translatable(Ids.langKey("class", id, "name"));
        confirmWith(Component.translatable("ui.rpgclasses.confirm_title", name), Component.translatable("ui.rpgclasses.confirm_text"),
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
            g.drawCenteredString(font, Component.translatable("ui.rpgclasses.must_choose"), width / 2, 19, 0xAAAAAA);
        }

        // Iconos de las clases.
        int y = 32;
        for (String id : classIds) {
            ClassDef def = DefinitionStore.CLIENT.classDef(id);
            if (def != null) {
                ResourceLocation iconId = ResourceLocation.tryParse(def.icon());
                Item item = iconId == null ? null : ForgeRegistries.ITEMS.getValue(iconId);
                if (item != null) g.renderItem(new ItemStack(item), 10, y);
            }
            y += ROW;
        }

        g.fill(panelLeft, panelTop, panelRight, panelBottom, 0x90000000);
        if (tab == ClassPages.Tab.PROGRESS) {
            renderProgress(g);
        } else {
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
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (delta * LINE_HEIGHT * 3)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // En modo selección, Esc abre el menú de pausa (para poder salir del mundo) en lugar de cerrar.
        if (selecting && keyCode == GLFW.GLFW_KEY_ESCAPE && minecraft != null) {
            minecraft.setScreen(new PauseScreen(true));
            return true;
        }
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
