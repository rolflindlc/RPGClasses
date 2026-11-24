package io.github.rpgclasses.client;

import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.progression.ProgressState;
import io.github.rpgclasses.core.progression.SavedBuild;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets.ClassAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Builds guardadas (3 huecos). Guardar copia la clase, ramas, perks y subclase actuales. Cargar reinicia la clase con la
 * pérdida normal y vuelve a gastar los puntos siguiendo la build hasta donde lleguen (fuera de combate).
 */
public final class BuildsScreen extends Screen {
    private final Screen parent;
    private int lastHash;

    public BuildsScreen(Screen parent) {
        super(Component.translatable("ui.rites_and_paths.builds.title"));
        this.parent = parent;
    }

    private static ProgressState state() {
        return ClientHandlers.LOCAL_DATA.state;
    }

    @Override
    protected void init() {
        ProgressState s = state();
        lastHash = hash(s);
        int top = height / 2 - 50;
        for (int i = 0; i < SavedBuild.SLOTS; i++) {
            int slot = i, y = top + i * 30;
            SavedBuild b = s.builds[i];
            Button save = Button.builder(Component.translatable("ui.rites_and_paths.builds.save"), btn -> {
                if (b == null) send(ClassAction.Action.SAVE_BUILD, slot);
                else confirm("ui.rites_and_paths.builds.overwrite", ClassAction.Action.SAVE_BUILD, slot);
            }).bounds(width / 2 + 40, y, 70, 20).build();
            save.active = s.classId != null;
            addRenderableWidget(save);
            Button load = Button.builder(Component.translatable("ui.rites_and_paths.builds.load"),
                    btn -> confirm("ui.rites_and_paths.builds.load_confirm", ClassAction.Action.LOAD_BUILD, slot))
                    .bounds(width / 2 + 114, y, 70, 20).build();
            load.active = b != null;
            addRenderableWidget(load);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), btn -> onClose())
                .bounds(width / 2 - 50, top + SavedBuild.SLOTS * 30 + 14, 100, 20).build());
    }

    private void send(ClassAction.Action action, int slot) {
        Net.CHANNEL.sendToServer(new ClassAction(action, String.valueOf(slot)));
    }

    private void confirm(String key, ClassAction.Action action, int slot) {
        if (minecraft == null) return;
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) send(action, slot);
            minecraft.setScreen(this);
        }, Component.translatable(key + ".title", slot + 1), Component.translatable(key + ".text")));
    }

    /** Los datos llegan del servidor: al cambiar, se rehacen los botones. */
    @Override
    public void tick() {
        if (hash(state()) != lastHash) rebuildWidgets();
    }

    private static int hash(ProgressState s) {
        return java.util.Arrays.hashCode(s.builds) * 31 + java.util.Objects.hashCode(s.classId);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, height / 2 - 80, 0xFFFFFF);
        ProgressState s = state();
        int top = height / 2 - 50;
        for (int i = 0; i < SavedBuild.SLOTS; i++) {
            int y = top + i * 30 + 6;
            g.drawString(font, describe(i, s.builds[i]), width / 2 - 190, y, 0xFFFFFF);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    private static Component describe(int slot, SavedBuild b) {
        MutableComponent c = Component.translatable("ui.rites_and_paths.builds.slot", slot + 1).withStyle(ChatFormatting.GOLD);
        if (b == null) return c.append(" ").append(Component.translatable("ui.rites_and_paths.builds.empty").withStyle(ChatFormatting.DARK_GRAY));
        c.append(" ").append(Component.translatable(Ids.langKey("class", b.classId(), "name")).withStyle(ChatFormatting.WHITE));
        if (b.subclassId() != null) {
            c.append(Component.literal(" / ").withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable(Ids.langKey("subclass", b.subclassId(), "name")).withStyle(ChatFormatting.AQUA));
        }
        return c.append(Component.translatable("ui.rites_and_paths.builds.points", b.totalPoints()).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
