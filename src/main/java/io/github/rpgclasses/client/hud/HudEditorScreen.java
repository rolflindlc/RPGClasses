package io.github.rpgclasses.client.hud;

import io.github.rpgclasses.config.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Editor del HUD: cada elemento del mod sale como una caja con su nombre. Se arrastra con el clic izquierdo, el derecho
 * lo oculta o lo vuelve a enseñar, las flechas lo mueven de 1 en 1 (con Mayús de 10 en 10) y R lo devuelve a su sitio.
 * Los elementos que ahora no se dibujan (no eres Monje, no estás cargando…) salen en gris en su sitio aproximado.
 * Al cerrar se guarda en rites_and_paths-client.toml.
 */
public final class HudEditorScreen extends Screen {
    private String selected;
    private String dragging;
    private double grabX, grabY;

    public HudEditorScreen() {
        super(Component.translatable("hud_editor.rites_and_paths.title"));
    }

    @Override
    protected void init() {
        int y = 32;
        addRenderableWidget(Button.builder(Component.translatable("hud_editor.rites_and_paths.reset_all"), b -> {
            for (String id : ClientConfig.HUD_IDS) {
                HudLayout.move(id, 0, 0);
                HudLayout.setHidden(id, false);
            }
        }).bounds(width / 2 - 102, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 + 2, y, 100, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static Component name(String id) {
        return Component.translatable("hud_editor.rites_and_paths.element." + id);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x30000000);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("hud_editor.rites_and_paths.help"), width / 2, 20, 0xC0C0C0);
        String hover = at(mouseX, mouseY);
        for (String id : ClientConfig.HUD_IDS) {
            HudLayout.Box b = HudLayout.placed(id, width, height);
            boolean hidden = HudLayout.hidden(id);
            boolean live = HudLayout.drawnRecently(id);
            int color = id.equals(selected) ? 0xFFFFD84A : hidden ? 0xFFE05050 : live ? 0xFFFFFFFF : 0xFF8A8A8A;
            if (!live || hidden) g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), hidden ? 0x40E05050 : 0x40808080);
            if (id.equals(hover)) g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), 0x30FFFFFF);
            HudLayout.drawBox(g, b, color);
            Component label = hidden ? Component.translatable("hud_editor.rites_and_paths.hidden", name(id)) : name(id);
            int ly = b.y() - 10 < 56 ? b.y() + b.h() + 2 : b.y() - 10;
            g.drawString(font, label, Math.max(2, Math.min(width - font.width(label) - 2, b.x())), ly, color);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Elemento bajo el ratón (el más pequeño si hay varios encima). */
    private String at(double x, double y) {
        String best = null;
        int bestArea = Integer.MAX_VALUE;
        for (String id : ClientConfig.HUD_IDS) {
            HudLayout.Box b = HudLayout.placed(id, width, height);
            if (b.contains(x, y) && b.w() * b.h() < bestArea) {
                best = id;
                bestArea = b.w() * b.h();
            }
        }
        return best;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        String id = at(x, y);
        if (id == null) {
            selected = null;
            return false;
        }
        selected = id;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            HudLayout.setHidden(id, !HudLayout.hidden(id));
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = id;
            grabX = x - HudLayout.dx(id);
            grabY = y - HudLayout.dy(id);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        if (dragging != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            HudLayout.move(dragging, (int) Math.round(x - grabX), (int) Math.round(y - grabY));
            return true;
        }
        return super.mouseDragged(x, y, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        dragging = null;
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (selected != null) {
            int step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1;
            int dx = HudLayout.dx(selected), dy = HudLayout.dy(selected);
            switch (key) {
                case GLFW.GLFW_KEY_LEFT -> { HudLayout.move(selected, dx - step, dy); return true; }
                case GLFW.GLFW_KEY_RIGHT -> { HudLayout.move(selected, dx + step, dy); return true; }
                case GLFW.GLFW_KEY_UP -> { HudLayout.move(selected, dx, dy - step); return true; }
                case GLFW.GLFW_KEY_DOWN -> { HudLayout.move(selected, dx, dy + step); return true; }
                case GLFW.GLFW_KEY_R -> { HudLayout.move(selected, 0, 0); return true; }
                default -> { }
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void onClose() {
        HudLayout.save();
        super.onClose();
    }
}
