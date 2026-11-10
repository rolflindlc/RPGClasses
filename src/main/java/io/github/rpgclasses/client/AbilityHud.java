package io.github.rpgclasses.client;

import io.github.rpgclasses.content.MonkAbilities;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.ability.UnlockResolver;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.progression.ProgressState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.List;
import java.util.Locale;

/** Lista de habilidades activas con su tecla y cooldown, en la esquina inferior derecha. */
public final class AbilityHud {
    private static final int ROW_HEIGHT = 12;

    /** Habilidades activas del jugador local, en orden de ranura. La misma regla que usa el servidor. */
    public static List<String> localActives() {
        ProgressState s = ClientHandlers.LOCAL_DATA.state;
        ClassDef classDef = DefinitionStore.CLIENT.classDef(s.classId);
        return UnlockResolver.actives(
                UnlockResolver.unlocked(s, classDef, DefinitionStore.CLIENT.subclassDef(s.subclassId)),
                DefinitionStore.CLIENT::abilityDef);
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.level == null) return;
        List<String> actives = localActives();
        if (actives.isEmpty()) return;

        long now = mc.level.getGameTime();
        int count = Math.min(actives.size(), ClientSetup.ABILITY_KEYS.length);
        int y = screenHeight - 4 - count * ROW_HEIGHT;
        for (int i = 0; i < count; i++) {
            String id = actives.get(i);
            KeyMapping key = ClientSetup.ABILITY_KEYS[i];
            long remaining = ClientHandlers.LOCAL_DATA.cooldowns.getOrDefault(id, 0L) - now;
            Component name = Component.translatable(Ids.langKey("ability", id, "name"));
            String extra = "";
            if (id.equals(MonkAbilities.STANCES)) {
                int stance = ClientHandlers.LOCAL_DATA.counters.getOrDefault(MonkAbilities.STANCE_COUNTER, 0);
                if (stance >= 0 && stance < MonkAbilities.STANCE_NAMES.length) {
                    extra = ": " + Component.translatable("stance.rpgclasses." + MonkAbilities.STANCE_NAMES[stance]).getString();
                }
            }
            String text = "[" + key.getTranslatedKeyMessage().getString() + "] " + name.getString() + extra
                    + (remaining > 0 ? String.format(Locale.ROOT, "  %.0f s", Math.ceil(remaining / 20.0)) : "");
            int width = mc.font.width(text);
            int x = screenWidth - width - 6;
            g.fill(x - 3, y - 2, screenWidth - 3, y + ROW_HEIGHT - 3, 0x80000000);
            g.drawString(mc.font, text, x, y, remaining > 0 ? 0xFF8888 : 0xFFFFFF);
            y += ROW_HEIGHT;
        }
    }

    private AbilityHud() {}
}
