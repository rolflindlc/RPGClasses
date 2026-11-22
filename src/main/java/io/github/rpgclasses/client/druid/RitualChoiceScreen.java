package io.github.rpgclasses.client.druid;

import io.github.rpgclasses.client.UiStyle;
import io.github.rpgclasses.druid.Beasts;
import io.github.rpgclasses.druid.FormSpec;
import io.github.rpgclasses.network.DruidPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Escultor, tras un ritual: el punto de conocimiento o una parte (cabeza, torso, patas o instinto) de la especie. Las
 * partes solo se ofrecen si ya conoce bien la especie. Cerrar sin elegir es tomar el conocimiento.
 */
public class RitualChoiceScreen extends Screen {
    private final String species;
    private final boolean parts;
    private boolean answered;

    public RitualChoiceScreen(String species, boolean parts) {
        super(Component.translatable("ui.rites_and_paths.ritual_choice.title", Beasts.name(species)));
        this.species = species;
        this.parts = parts;
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 - 40;
        addRenderableWidget(Button.builder(Component.translatable("ui.rites_and_paths.ritual_choice.knowledge"), b -> choose(0))
                .bounds(cx - 100, y, 200, 20).build());
        if (!parts) return;
        for (int i = 0; i < 4; i++) {
            int choice = i + 1;
            Component label = Component.translatable("item.rites_and_paths.beast_" + FormSpec.Chimera.PARTS[i] + ".named", Beasts.name(species));
            addRenderableWidget(Button.builder(label, b -> choose(choice)).bounds(cx - 100, y + 24 + i * 22, 200, 20).build());
        }
    }

    private void choose(int choice) {
        answered = true;
        DruidClient.send(DruidPackets.Action.RITUAL_CHOICE, new CompoundTag(), choice);
        onClose();
    }

    @Override
    public void onClose() {
        if (!answered) {
            answered = true;
            DruidClient.send(DruidPackets.Action.RITUAL_CHOICE, new CompoundTag(), 0);
        }
        super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int cx = width / 2, y = height / 2 - 40;
        UiStyle.panel(g, cx - 112, y - 30, cx + 112, y + (parts ? 24 + 4 * 22 + 8 : 30));
        g.drawCenteredString(font, title, cx, y - 18, UiStyle.GOLD);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
