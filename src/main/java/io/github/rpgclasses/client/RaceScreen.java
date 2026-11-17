package io.github.rpgclasses.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.definition.AttributeEffect;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.EffectDef;
import io.github.rpgclasses.core.definition.Definitions.RaceDef;
import io.github.rpgclasses.core.definition.Definitions.SubraceDef;
import io.github.rpgclasses.core.definition.RaceStore;
import io.github.rpgclasses.core.progression.ProgressState;
import io.github.rpgclasses.core.stat.StatCalculator;
import io.github.rpgclasses.core.stat.StatMath;
import io.github.rpgclasses.core.value.LevelValue;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets.ClassAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pantalla de razas. SELECT: elección obligatoria y permanente de raza y subraza (antes que la clase).
 * VIEW: consulta. WALL: el Humano elige el muro (o muros) de Adaptable tras elegir clase.
 */
public final class RaceScreen extends Screen {
    public enum Mode { SELECT, VIEW, WALL }

    private static final int LEFT_WIDTH = 112;
    private static final int LINE_HEIGHT = 10;
    private static final Set<String> SKIPPED = Set.of("type", "id", "min_rank", "effects", "condition", "modifiers");

    private final Mode mode;
    private final List<String> raceIds = new ArrayList<>();
    private String selectedRace;
    private String selectedSubrace;
    private List<FormattedCharSequence> lines = List.of();
    private int scroll;
    private int panelLeft, panelTop, panelRight, panelBottom, headerTop;
    private int header = 46;
    private static final int TABS_Y = 28;

    public RaceScreen(Mode mode) {
        super(Component.translatable(mode == Mode.WALL ? "ui.rites_and_paths.race.title_wall"
                : mode == Mode.SELECT ? "ui.rites_and_paths.race.title_select" : "ui.rites_and_paths.race.title_view"));
        this.mode = mode;
    }

    private static ProgressState state() {
        return ClientHandlers.LOCAL_DATA.state;
    }

    /** true si el jugador local es Humano con clase y aún le queda un muro de Adaptable por elegir. */
    public static boolean needsWall() {
        return !eligibleWalls().isEmpty();
    }

    private static List<String> eligibleWalls() {
        ProgressState s = state();
        RaceDef race = RaceStore.CLIENT.race(s.raceId);
        ClassDef classDef = DefinitionStore.CLIENT.classDef(s.classId);
        List<String> out = new ArrayList<>();
        if (race == null || classDef == null || s.humanWalls.size() >= StatCalculator.humanWallSlots(s, race)) return out;
        for (String d : new java.util.TreeSet<>(classDef.walls().keySet())) {
            if (classDef.walls().get(d) > 0 && !s.humanWalls.contains(d)) out.add(d);
        }
        return out;
    }

    /** El servidor ha enviado datos nuevos. */
    public void onDataChanged() {
        if (minecraft == null || minecraft.screen != this) return;
        if ((mode == Mode.SELECT && state().raceId != null) || (mode == Mode.WALL && !needsWall())) {
            minecraft.setScreen(null);
            return;
        }
        rebuildWidgets();
    }

    @Override
    protected void init() {
        Portraits.clearCache();
        panelLeft = LEFT_WIDTH + 14;
        header = height >= 330 ? 46 : 24;
        headerTop = TABS_Y + 26;
        panelTop = headerTop + header;
        panelRight = width - 10;
        panelBottom = height - 36;
        int footerY = height - 28;
        int center = (panelLeft + panelRight) / 2;

        if (mode == Mode.WALL) {
            initWall();
            return;
        }
        raceIds.clear();
        raceIds.addAll(RaceStore.CLIENT.ids());
        if (selectedRace == null || !raceIds.contains(selectedRace)) {
            String own = state().raceId;
            selectedRace = own != null && raceIds.contains(own) ? own : (raceIds.isEmpty() ? null : raceIds.get(0));
            selectedSubrace = own != null && own.equals(selectedRace) ? state().subraceId : null;
        }
        RaceDef race = RaceStore.CLIENT.race(selectedRace);
        if (race != null && race.subrace(selectedSubrace) == null) selectedSubrace = race.subraces().get(0).id();

        // Rejilla de razas: tarjetas con retrato (si no caben, solo el retrato y el nombre en la ayuda).
        int cols = 2, gap = 2;
        int cellW = (LEFT_WIDTH - 4 - gap) / cols;
        int rows = Math.max(1, (raceIds.size() + cols - 1) / cols);
        int cellH = Math.max(26, Math.min(52, (height - 8 - TABS_Y) / rows - gap));
        for (int i = 0; i < raceIds.size(); i++) {
            String id = raceIds.get(i);
            RaceDef def = RaceStore.CLIENT.race(id);
            PortraitButton b = new PortraitButton(8 + (i % cols) * (cellW + gap), TABS_Y + (i / cols) * (cellH + gap), cellW, cellH,
                    Component.translatable(Ids.langKey("race", id, "name")), Portraits.raceTex(id), def == null ? null : def.icon(),
                    PortraitButton.Layout.VERTICAL, cellH >= 48, btn -> {
                        selectedRace = id;
                        selectedSubrace = null;
                        scroll = 0;
                        rebuildWidgets();
                    });
            b.active = !id.equals(selectedRace);
            addRenderableWidget(b);
        }
        // Pestañas de subraza con retrato pequeño.
        if (race != null) {
            int tabWidth = (panelRight - panelLeft) / race.subraces().size();
            for (int i = 0; i < race.subraces().size(); i++) {
                SubraceDef sub = race.subraces().get(i);
                PortraitButton b = new PortraitButton(panelLeft + i * tabWidth, TABS_Y, tabWidth - 2, 22, subraceName(race, sub),
                        Portraits.subraceTex(race.id(), sub.id()), sub.icon(), PortraitButton.Layout.HORIZONTAL, true, btn -> {
                            selectedSubrace = sub.id();
                            scroll = 0;
                            rebuildWidgets();
                        });
                b.active = !sub.id().equals(selectedSubrace);
                addRenderableWidget(b);
            }
        }
        SubraceDef sub = race == null ? null : race.subrace(selectedSubrace);
        if (mode == Mode.SELECT && race != null && sub != null) {
            Component name = Component.translatable(Ids.langKey("race", race.id(), "name")).append(" · ").append(subraceName(race, sub));
            Button choose = Button.builder(Component.translatable("ui.rites_and_paths.choose", name), btn -> confirm(race, sub, name))
                    .bounds(center - 110, footerY, 220, 20).build();
            choose.active = sub.available();
            addRenderableWidget(choose);
        } else if (mode == Mode.VIEW) {
            addRenderableWidget(Button.builder(Component.translatable("ui.rites_and_paths.close"), btn -> onClose())
                    .bounds(center - 50, footerY, 100, 20).build());
        }
        rebuildLines(race, sub);
    }

    private void initWall() {
        ProgressState s = state();
        ClassDef classDef = DefinitionStore.CLIENT.classDef(s.classId);
        RaceDef race = RaceStore.CLIENT.race(s.raceId);
        int y = 70;
        for (String d : eligibleWalls()) {
            double wall = classDef == null ? 0 : StatMath.wallPenalty(classDef.walls().getOrDefault(d, 0.0), 30);
            // Reducción que tendría este muro si se elige ahora (como principal o como segundo).
            ProgressState preview = new ProgressState();
            preview.copyFrom(s);
            preview.humanWalls.add(d);
            double reduction = StatCalculator.humanWallReduction(preview, race, d);
            Component label = Component.translatable("ui.rites_and_paths.race.wall_option",
                    Component.translatable(Ids.langKey("discipline", d, "name")),
                    Math.round(wall * 100), ClassPages.format(wall * (1 - Math.min(1, reduction)), "%", false));
            addRenderableWidget(Button.builder(label, btn -> Net.CHANNEL.sendToServer(new ClassAction(ClassAction.Action.CHOOSE_WALL, d)))
                    .bounds(width / 2 - 130, y, 260, 20).build());
            y += 24;
        }
        List<Component> text = List.of(Component.translatable(s.humanWalls.isEmpty()
                ? "ui.rites_and_paths.race.wall_text" : "ui.rites_and_paths.race.wall_text_second"));
        lines = new ArrayList<>();
        for (Component c : text) lines.addAll(font.split(c, Math.min(width - 40, 360)));
    }

    private void confirm(RaceDef race, SubraceDef sub, Component name) {
        if (minecraft == null) return;
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) Net.CHANNEL.sendToServer(new ClassAction(ClassAction.Action.CHOOSE_RACE, race.id() + "/" + sub.id()));
            minecraft.setScreen(yes ? null : this);
        }, Component.translatable("ui.rites_and_paths.confirm_title", name), Component.translatable("ui.rites_and_paths.race.confirm_text")));
    }

    // --- Texto ---

    private static MutableComponent subraceName(RaceDef race, SubraceDef sub) {
        return Component.translatable(Ids.langKey("subrace", race.id() + "." + sub.id(), "name"));
    }

    private void rebuildLines(RaceDef race, SubraceDef sub) {
        List<Component> out = new ArrayList<>();
        if (race != null) {
            ProgressState s = state();
            boolean own = race.id().equals(s.raceId);
            out.add(Component.translatable(Ids.langKey("race", race.id(), "name")).withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE));
            if (own) {
                out.add(Component.translatable("ui.rites_and_paths.race.own", subraceName(race, race.subrace(s.subraceId) == null ? race.subraces().get(0) : race.subrace(s.subraceId)),
                        s.raceRank).withStyle(ChatFormatting.GREEN));
            }
            out.add(Component.translatable(Ids.langKey("race", race.id(), "desc")));
            out.add(Component.empty());
            out.add(Component.translatable("ui.rites_and_paths.race.traits").withStyle(ChatFormatting.YELLOW));
            for (EffectDef e : race.effects()) effect(e, out);
            if (sub != null) {
                out.add(Component.empty());
                out.add(subraceName(race, sub).withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE));
                if (!sub.available()) out.add(Component.translatable("ui.rites_and_paths.race.unavailable").withStyle(ChatFormatting.RED));
                out.add(Component.translatable(Ids.langKey("subrace", race.id() + "." + sub.id(), "desc")));
                out.add(Component.empty());
                out.add(Component.translatable("ui.rites_and_paths.race.by_rank").withStyle(ChatFormatting.GRAY));
                for (EffectDef e : sub.effects()) effect(e, out);
            }
        }
        List<FormattedCharSequence> split = new ArrayList<>();
        int w = panelRight - panelLeft - 12;
        for (Component c : out) {
            if (c.getString().isEmpty()) split.add(FormattedCharSequence.EMPTY);
            else split.addAll(font.split(c, w));
        }
        lines = split;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    /** Un rasgo con sus datos exactos; lo que cambia con el rango se muestra como R0 / R1 / R2 / R3. */
    private static void effect(EffectDef e, List<Component> out) {
        if (Ids.EFFECT_ATTRIBUTE.equals(e.type())) {
            AttributeEffect parsed;
            try {
                parsed = AttributeEffect.parse(e.params(), StatCalculator.RACE_RANKS);
            } catch (RuntimeException ex) {
                return;
            }
            for (AttributeEffect.Modifier m : parsed.modifiers()) {
                if (m.attribute().equals(Ids.NS + ":leaf_walker")) continue; // bandera interna
                out.add(Component.literal("  ").append(Component.translatable("ui.rites_and_paths.data.fixed",
                        ClassPages.attributeName(m.attribute()), ranks(m.value(), ClassPages.isPercent(m) ? "%" : "", true))));
            }
            return;
        }
        JsonObject p = e.params();
        String id = p.get("id").getAsString();
        MutableComponent name = Component.translatable(Ids.langKey("mechanic", id, "name")).withStyle(ChatFormatting.WHITE);
        if (p.has("min_rank")) name.append(Component.translatable("ui.rites_and_paths.race.from_rank", p.get("min_rank").getAsInt()).withStyle(ChatFormatting.GRAY));
        out.add(Component.literal("  ").append(name));
        out.add(Component.literal("    ").append(Component.translatable(Ids.langKey("mechanic", id, "desc"))).withStyle(ChatFormatting.GRAY));
        Map<String, String> units = ClassPages.units();
        for (Map.Entry<String, JsonElement> entry : p.entrySet()) {
            String key = entry.getKey();
            if (SKIPPED.contains(key)) continue;
            Component label = Component.translatableWithFallback("param.rites_and_paths." + key, key);
            String unit = units.getOrDefault(key, "");
            JsonElement v = entry.getValue();
            String text = null;
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) text = ClassPages.format(v.getAsDouble(), unit, false);
            else if (v.isJsonObject()) {
                try {
                    text = ranks(LevelValue.fromJson(v.getAsJsonObject(), StatCalculator.RACE_RANKS), unit, false);
                } catch (RuntimeException ignored) {
                    // no es un valor por rango
                }
            }
            if (text != null) out.add(Component.literal("    ").append(Component.translatable("ui.rites_and_paths.data.fixed", label, text)).withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    private static String ranks(LevelValue value, String unit, boolean signed) {
        boolean constant = true;
        for (int i = 2; i <= StatCalculator.RACE_RANKS; i++) constant &= value.at(i) == value.at(1);
        if (constant) return ClassPages.format(value.at(1), unit, signed);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= StatCalculator.RACE_RANKS; i++) {
            if (i > 1) sb.append(" / ");
            sb.append("R").append(i - 1).append(" ").append(ClassPages.format(value.at(i), unit, signed));
        }
        return sb.toString();
    }

    // --- Dibujo y entrada ---

    /** Cabecera: retrato y nombre de la raza a la izquierda; de la subraza a la derecha. */
    private void renderHeader(GuiGraphics g) {
        UiStyle.header(g, panelLeft, headerTop, panelRight, headerTop + header);
        RaceDef race = RaceStore.CLIENT.race(selectedRace);
        if (race == null) return;
        SubraceDef sub = race.subrace(selectedSubrace);
        boolean big = header >= 46;
        int size = big ? Portraits.TEX : 18;
        int x = panelLeft + 6, y = headerTop + (big ? 5 : 3);
        Portraits.draw(g, Portraits.raceTex(race.id()), race.icon(), x, y, size);
        Component raceName = Component.translatable(Ids.langKey("race", race.id(), "name"));
        if (big) UiStyle.scaledText(g, font, raceName, x + 44, y + 4, 1.5f, 0xFFFFE08A);
        else g.drawString(font, raceName, x + 24, y + 5, 0xFFFFE08A);
        if (big && race.id().equals(state().raceId)) {
            g.drawString(font, Component.translatable("ui.rites_and_paths.race.header_rank", state().raceRank), x + 44, y + 22, 0x55FF55);
        }
        if (sub != null) {
            int sx = panelRight - 6 - size;
            Portraits.draw(g, Portraits.subraceTex(race.id(), sub.id()), sub.icon(), sx, y, size);
            Component name = subraceName(race, sub);
            g.drawString(font, name, sx - 6 - font.width(name), y + (big ? 8 : 5), sub.available() ? 0x55FFFF : 0x888888);
            if (big) {
                Component label = Component.translatable("ui.rites_and_paths.race.header_subrace");
                g.drawString(font, label, sx - 6 - font.width(label), y + 20, 0x888888);
            }
        }
    }

    private int visibleLines() {
        return Math.max(1, (panelBottom - panelTop - 8) / LINE_HEIGHT);
    }

    private int maxScroll() {
        return Math.max(0, lines.size() - visibleLines());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta) * 3));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        if (mode == Mode.WALL) {
            int y = 34;
            for (FormattedCharSequence line : lines) {
                g.drawCenteredString(font, line, width / 2, y, 0xDDDDDD);
                y += LINE_HEIGHT;
            }
        } else {
            UiStyle.panel(g, panelLeft, headerTop, panelRight, panelBottom);
            renderHeader(g);
            int y = panelTop + 4;
            for (int i = scroll; i < Math.min(lines.size(), scroll + visibleLines()); i++) {
                g.drawString(font, lines.get(i), panelLeft + 6, y, 0xE0E0E0);
                y += LINE_HEIGHT;
            }
            if (mode == Mode.SELECT) {
                g.drawCenteredString(font, Component.translatable("ui.rites_and_paths.race.permanent").withStyle(ChatFormatting.GOLD),
                        (panelLeft + panelRight) / 2, 19, 0xFFFFFF);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return mode == Mode.VIEW;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
