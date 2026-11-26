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

    /** Caja de las filas dibujadas en este fotograma (para el editor del HUD). */
    private static int boxLeft, boxTop, boxRight, boxBottom;

    private static void fillRow(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        boxLeft = Math.min(boxLeft, x1);
        boxTop = Math.min(boxTop, y1);
        boxRight = Math.max(boxRight, x2);
        boxBottom = Math.max(boxBottom, y2);
        g.fill(x1, y1, x2, y2, color);
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.level == null) return;
        boxLeft = Integer.MAX_VALUE;
        boxTop = Integer.MAX_VALUE;
        boxRight = Integer.MIN_VALUE;
        boxBottom = Integer.MIN_VALUE;
        try {
            renderRows(g, mc, partialTick, screenWidth, screenHeight);
        } finally {
            if (boxLeft < boxRight) io.github.rpgclasses.client.hud.HudLayout.report("abilities", boxLeft, boxTop, boxRight - boxLeft, boxBottom - boxTop);
        }
    }

    private static void renderRows(GuiGraphics g, Minecraft mc, float partialTick, int screenWidth, int screenHeight) {
        List<String> actives = localActives();
        int classRows = Math.min(actives.size(), ClientSetup.ABILITY_KEYS.length);
        int freeRows = renderFreeCasts(g, mc, screenWidth, screenHeight - 4 - classRows * ROW_HEIGHT);
        renderRacial(g, mc, screenWidth, screenHeight - 4 - (classRows + freeRows) * ROW_HEIGHT);
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
                    extra = ": " + Component.translatable("stance.rites_and_paths." + MonkAbilities.STANCE_NAMES[stance]).getString();
                }
            }
            // Mártir: con la vida a ≤15 % el Sacrificio pasa a ser el Sacrificio final (fila roja y palpitante).
            boolean martyrdom = id.equals(io.github.rpgclasses.cleric.Cleric.SACRIFICE)
                    && io.github.rpgclasses.client.cleric.ClericClient.sacrificeFinal();
            if (martyrdom) name = Component.translatable("hud.rites_and_paths.sacrifice_final");
            String text = "[" + key.getTranslatedKeyMessage().getString() + "] " + name.getString() + extra
                    + (remaining > 0 ? String.format(Locale.ROOT, "  %.0f s", Math.ceil(remaining / 20.0)) : "");
            int width = mc.font.width(text);
            int x = screenWidth - width - 6;
            if (martyrdom) {
                float pulse = 0.5f + 0.5f * net.minecraft.util.Mth.sin((now + partialTick) * 0.3f);
                int alpha = 0x60 + (int) (0x80 * pulse);
                fillRow(g, x - 3, y - 2, screenWidth - 3, y + ROW_HEIGHT - 3, (alpha << 24) | 0x8A0A0A);
                g.drawString(mc.font, text, x, y, 0xFFFF4040);
                y += ROW_HEIGHT;
                continue;
            }
            fillRow(g, x - 3, y - 2, screenWidth - 3, y + ROW_HEIGHT - 3, 0x80000000);
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
            String text = Component.translatable("hud.rites_and_paths.free_cast", slot, spellName).getString();
            int dots = max * 7;
            int width = mc.font.width(text) + 6 + dots;
            int x = screenWidth - width - 6;
            fillRow(g, x - 3, y - 2, screenWidth - 3, y + ROW_HEIGHT - 3, 0x80000000);
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

    /** Habilidades raciales con tecla: mecánica y ranura (0 = V, 1 = B, 2 = agachado + V, 3 = agachado + B). */
    private static final String[][] RACIAL_KEYS = {
            {"race_warcry", "0"}, {"race_stone_sense", "0"}, {"race_smoke_cloud", "0"}, {"race_snare", "0"},
            {"race_nether_compass", "0"},
            {"race_portable_furnace", "0"}, {"race_ethereal", "0"}, {"race_breath", "0"}, {"race_grow", "0"},
            {"race_swarm", "0"}, {"race_belt", "0"},
            // Sin tecla propia, pero con enfriamiento (solo se muestran mientras se enfrían).
            {"race_spring", "-1"}, {"race_mitosis", "-1"}};

    /** Habilidades raciales: una fila por cada una (encima de las de clase), con su tecla y su enfriamiento. */
    private static void renderRacial(GuiGraphics g, Minecraft mc, int screenWidth, int bottom) {
        long now = mc.level.getGameTime();
        int y = bottom - ROW_HEIGHT;
        for (int i = RACIAL_KEYS.length - 1; i >= 0; i--) {
            String key = Ids.NS + ":" + RACIAL_KEYS[i][0];
            if (!ClientSetup.hasLocalMechanic(key)) continue;
            int slot = Integer.parseInt(RACIAL_KEYS[i][1]);
            long remaining = ClientHandlers.LOCAL_DATA.cooldowns.getOrDefault(key, 0L) - now;
            if (slot < 0 && remaining <= 0) continue;
            String keyText = slot < 0 ? "" : "[" + (slot >= 2 ? "⇧" : "")
                    + ClientSetup.RACE_KEYS[slot % 2].getTranslatedKeyMessage().getString() + "] ";
            String text = keyText + Component.translatable(Ids.langKey("mechanic", key, "name")).getString()
                    + (remaining > 0 ? String.format(Locale.ROOT, "  %.0f s", Math.ceil(remaining / 20.0)) : "");
            int x = screenWidth - mc.font.width(text) - 6;
            fillRow(g, x - 3, y - 2, screenWidth - 3, y + ROW_HEIGHT - 3, 0x80000000);
            g.drawString(mc.font, text, x, y, remaining > 0 ? 0xFF8888 : 0xFFE6C35A);
            y -= ROW_HEIGHT;
        }
    }

    /** Barra de Sigilo del Pícaro, sobre la barra de objetos. */
    public static void renderStealthBar(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.level == null) return;
        ProgressState s = ClientHandlers.LOCAL_DATA.state;
        ClassDef classDef = DefinitionStore.CLIENT.classDef(s.classId);
        if (classDef == null || !UnlockResolver.unlocked(s, classDef, DefinitionStore.CLIENT.subclassDef(s.subclassId))
                .contains(RogueAbilities.STEALTH)) return;
        int bar = ClientHandlers.LOCAL_DATA.counters.getOrDefault(RogueAbilities.BAR_COUNTER, 1000);
        int state = ClientHandlers.LOCAL_DATA.counters.getOrDefault(RogueAbilities.STATE_COUNTER, 0);
        int w = 80, h = 5;
        // Encima de todo lo que ya hay a los lados de la barra de objetos (vida, armadura, comida…).
        int top = io.github.rpgclasses.client.hud.HudLayout.stackCenter(gui, screenHeight, 16);
        int x = screenWidth / 2 - w / 2, y = top + 10;
        io.github.rpgclasses.client.hud.HudLayout.report("stealth", x - 1, top, w + 2, 16);
        int color = state == RogueAbilities.STATE_LOCKED ? 0xFFC03030 : state == RogueAbilities.STATE_ACTIVE ? 0xFF5090E0 : 0xFF8080A0;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xA0000000);
        g.fill(x, y, x + Math.round(w * Math.max(0, Math.min(1000, bar)) / 1000f), y + h, color);
        g.drawCenteredString(mc.font, Component.translatable("hud.rites_and_paths.stealth"), screenWidth / 2, y - 10, 0xFFFFFF);
    }

    private AbilityHud() {}
}
