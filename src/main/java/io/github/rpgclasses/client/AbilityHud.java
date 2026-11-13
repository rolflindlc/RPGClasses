package io.github.rpgclasses.client;

import io.github.rpgclasses.content.MonkAbilities;
import io.github.rpgclasses.content.RogueAbilities;
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
        renderStealthBar(g, mc, screenWidth, screenHeight);
        List<String> actives = localActives();
        int freeRows = renderFreeCasts(g, mc, screenWidth, screenHeight - 4 - Math.min(actives.size(), ClientSetup.ABILITY_KEYS.length) * ROW_HEIGHT);
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

    /**
     * Conjuros Gratuitos del Mago: una fila por ranura desbloqueada y con conjuro fijado, encima de la lista de
     * habilidades: puntos llenos = cargas disponibles. Devuelve las filas dibujadas.
     */
    private static int renderFreeCasts(GuiGraphics g, Minecraft mc, int screenWidth, int bottom) {
        ProgressState s = ClientHandlers.LOCAL_DATA.state;
        ClassDef classDef = DefinitionStore.CLIENT.classDef(s.classId);
        if (classDef == null) return 0;
        java.util.Set<String> unlocked = UnlockResolver.unlocked(s, classDef, DefinitionStore.CLIENT.subclassDef(s.subclassId));
        int rows = 0;
        int y = bottom - ROW_HEIGHT;
        for (int slot = 3; slot >= 1; slot--) {
            String id = Ids.NS + ":free_cast_slot_" + slot;
            String spell = ClientHandlers.LOCAL_DATA.texts.get("free_cast_spell_" + slot);
            if (!unlocked.contains(id) || spell == null) continue;
            var def = DefinitionStore.CLIENT.abilityDef(id);
            int max = def == null || !def.params().has("charges") ? slot : def.params().get("charges").getAsInt();
            int left = Math.max(0, max - ClientHandlers.LOCAL_DATA.counters.getOrDefault("free_cast_used_" + slot, 0));
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(spell);
            String spellName = rl == null ? spell : Component.translatable("spell." + rl.getNamespace() + "." + rl.getPath()).getString();
            String text = Component.translatable("hud.rpgclasses.free_cast", slot, spellName).getString();
            int dots = max * 7;
            int width = mc.font.width(text) + 6 + dots;
            int x = screenWidth - width - 6;
            g.fill(x - 3, y - 2, screenWidth - 3, y + ROW_HEIGHT - 3, 0x80000000);
            g.drawString(mc.font, text, x, y, left > 0 ? 0xFFB070FF : 0xFF888888);
            int dx = x + mc.font.width(text) + 6;
            for (int i = 0; i < max; i++) {
                int c = i < left ? 0xFFB070FF : 0xFF404040;
                g.fill(dx + i * 7, y + 1, dx + i * 7 + 5, y + 6, 0xFF000000);
                g.fill(dx + i * 7 + 1, y + 2, dx + i * 7 + 4, y + 5, c);
            }
            y -= ROW_HEIGHT;
            rows++;
        }
        return rows;
    }

    /** Barra de Sigilo del Pícaro, sobre la barra de objetos. */
    private static void renderStealthBar(GuiGraphics g, Minecraft mc, int screenWidth, int screenHeight) {
        ProgressState s = ClientHandlers.LOCAL_DATA.state;
        ClassDef classDef = DefinitionStore.CLIENT.classDef(s.classId);
        if (classDef == null || !UnlockResolver.unlocked(s, classDef, DefinitionStore.CLIENT.subclassDef(s.subclassId))
                .contains(RogueAbilities.STEALTH)) return;
        int bar = ClientHandlers.LOCAL_DATA.counters.getOrDefault(RogueAbilities.BAR_COUNTER, 1000);
        int state = ClientHandlers.LOCAL_DATA.counters.getOrDefault(RogueAbilities.STATE_COUNTER, 0);
        int w = 80, h = 5;
        int x = screenWidth / 2 - w / 2, y = screenHeight - 62;
        int color = state == RogueAbilities.STATE_LOCKED ? 0xFFC03030 : state == RogueAbilities.STATE_ACTIVE ? 0xFF5090E0 : 0xFF8080A0;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xA0000000);
        g.fill(x, y, x + Math.round(w * Math.max(0, Math.min(1000, bar)) / 1000f), y + h, color);
        g.drawCenteredString(mc.font, Component.translatable("hud.rpgclasses.stealth"), screenWidth / 2, y - 10, 0xFFFFFF);
    }

    private AbilityHud() {}
}
