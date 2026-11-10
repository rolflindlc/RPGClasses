package io.github.rpgclasses.client;

import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
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

/**
 * Pantalla de clases. En modo selección (al entrar sin clase) no se puede cerrar hasta elegir;
 * en modo libro (tecla) solo consulta. A la izquierda, las clases; a la derecha, cuatro pestañas
 * con descripción, ramas y habilidades, subclases y datos exactos.
 */
public final class ClassScreen extends Screen {
    private static final int LINE_HEIGHT = 10;
    private static final int LEFT_WIDTH = 112;

    private final boolean selecting;
    private final List<String> classIds = new ArrayList<>();
    private String selectedClass;
    private ClassPages.Tab tab = ClassPages.Tab.SUMMARY;

    private List<FormattedCharSequence> lines = List.of();
    private int scroll;
    private int panelLeft, panelTop, panelRight, panelBottom;

    public ClassScreen(boolean selecting) {
        super(Component.translatable(selecting ? "ui.rpgclasses.title_select" : "ui.rpgclasses.title_book"));
        this.selecting = selecting;
    }

    public boolean isSelecting() {
        return selecting;
    }

    @Override
    protected void init() {
        classIds.clear();
        classIds.addAll(DefinitionStore.CLIENT.classIds());
        if (selectedClass == null || !classIds.contains(selectedClass)) {
            String own = ClientHandlers.LOCAL_DATA.state.classId;
            selectedClass = own != null && classIds.contains(own) ? own : (classIds.isEmpty() ? null : classIds.get(0));
        }

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
            y += 24;
        }

        // Pestañas.
        ClassPages.Tab[] tabs = ClassPages.Tab.values();
        int tabWidth = (panelRight - panelLeft) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            ClassPages.Tab t = tabs[i];
            Button b = Button.builder(Component.translatable("ui.rpgclasses.tab." + t.name().toLowerCase(java.util.Locale.ROOT)), btn -> {
                tab = t;
                scroll = 0;
                rebuildWidgets();
            }).bounds(panelLeft + i * tabWidth, 30, tabWidth - 2, 20).build();
            b.active = t != tab;
            addRenderableWidget(b);
        }

        // Pie.
        int footerY = height - 28;
        if (selecting && selectedClass != null) {
            Component name = Component.translatable(Ids.langKey("class", selectedClass, "name"));
            addRenderableWidget(Button.builder(Component.translatable("ui.rpgclasses.choose", name), btn -> confirmChoice())
                    .bounds((panelLeft + panelRight) / 2 - 90, footerY, 180, 20).build());
        } else if (!selecting) {
            addRenderableWidget(Button.builder(Component.translatable("ui.rpgclasses.close"), btn -> onClose())
                    .bounds((panelLeft + panelRight) / 2 - 50, footerY, 100, 20).build());
        }

        rebuildLines();
    }

    private void select(String id) {
        selectedClass = id;
        scroll = 0;
        rebuildWidgets();
    }

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

    private void confirmChoice() {
        String id = selectedClass;
        if (id == null || minecraft == null) return;
        Component name = Component.translatable(Ids.langKey("class", id, "name"));
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) Net.CHANNEL.sendToServer(new ClassAction(ClassAction.Action.CHOOSE_CLASS, id));
            // Se vuelve a esta pantalla; se cerrará sola cuando el servidor confirme la clase.
            minecraft.setScreen(this);
        }, Component.translatable("ui.rpgclasses.confirm_title", name), Component.translatable("ui.rpgclasses.confirm_text")));
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
            y += 24;
        }

        // Panel de texto con recorte y barra de desplazamiento.
        g.fill(panelLeft, panelTop, panelRight, panelBottom, 0x90000000);
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
