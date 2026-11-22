package io.github.rpgclasses.client.druid;

import io.github.rpgclasses.client.ClientHandlers;
import io.github.rpgclasses.client.UiStyle;
import io.github.rpgclasses.druid.Beasts;
import io.github.rpgclasses.druid.Bestiary;
import io.github.rpgclasses.druid.FormSpec;
import io.github.rpgclasses.network.DruidPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Escultor con el máximo de quimeras: cuál sustituye la nueva (o no crearla). */
public class ChimeraReplaceScreen extends Screen {
    private boolean answered;
    private int rows;

    public ChimeraReplaceScreen() {
        super(Component.translatable("ui.rites_and_paths.chimera_replace.title"));
    }

    @Override
    protected void init() {
        List<FormSpec.Chimera> list = Bestiary.chimeras(ClientHandlers.LOCAL_DATA);
        rows = list.size() + 1;
        int cx = width / 2, y = height / 2 - rows * 11;
        for (int i = 0; i < list.size(); i++) {
            int index = i;
            FormSpec.Chimera c = list.get(i);
            Button b = Button.builder(Component.translatable("druid.rites_and_paths.chimera", Beasts.name(c.torso())), x -> choose(index))
                    .bounds(cx - 110, y + i * 22, 220, 20).build();
            b.setTooltip(Tooltip.create(Component.translatable("ui.rites_and_paths.bestiary.parts", Beasts.name(c.head()),
                    Beasts.name(c.torso()), Beasts.name(c.legs()), Beasts.name(c.instinct()))));
            addRenderableWidget(b);
        }
        addRenderableWidget(Button.builder(Component.translatable("ui.rites_and_paths.chimera_replace.cancel"), x -> choose(-1))
                .bounds(cx - 110, y + list.size() * 22 + 6, 220, 20).build());
    }

    private void choose(int index) {
        answered = true;
        DruidClient.send(DruidPackets.Action.CHIMERA_REPLACE, new CompoundTag(), index);
        onClose();
    }

    @Override
    public void onClose() {
        if (!answered) {
            answered = true;
            DruidClient.send(DruidPackets.Action.CHIMERA_REPLACE, new CompoundTag(), -1);
        }
        super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int cx = width / 2, y = height / 2 - rows * 11;
        UiStyle.panel(g, cx - 122, y - 30, cx + 122, y + rows * 22 + 12);
        g.drawCenteredString(font, title, cx, y - 18, UiStyle.GOLD);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
