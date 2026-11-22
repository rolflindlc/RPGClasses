package io.github.rpgclasses.client.druid;

import io.github.rpgclasses.client.ClientHandlers;
import io.github.rpgclasses.client.UiStyle;
import io.github.rpgclasses.druid.Beasts;
import io.github.rpgclasses.druid.Bestiary;
import io.github.rpgclasses.druid.Druid;
import io.github.rpgclasses.druid.FormSpec;
import io.github.rpgclasses.druid.WildShape;
import io.github.rpgclasses.network.DruidPackets;
import io.github.rpgclasses.player.PlayerClassData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bestiario del Druida (tecla de Forma Salvaje): las especies que conoce (o empieza a conocer) y sus quimeras. Clic en
 * una que conozca bien: transformarse. Clic derecho en una especie: fijarla (o quitarla) para Rastrear Presas.
 */
public class BestiaryScreen extends Screen {
    private static final int ROW = 34, W = 320;

    private record Entry(FormSpec spec, Beasts.Info info, int points, int needed, boolean known, LivingEntity preview) {}

    private final List<Entry> entries = new ArrayList<>();
    private int scroll;
    private int left, top, bottom;

    public BestiaryScreen() {
        super(Component.translatable("ui.rites_and_paths.bestiary.title"));
    }

    @Override
    protected void init() {
        rebuild();
    }

    void rebuild() {
        clearWidgets();
        entries.clear();
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        PlayerClassData data = ClientHandlers.LOCAL_DATA;
        if (p == null) return;
        left = (width - W) / 2;
        top = Math.max(10, height / 2 - 120);
        bottom = Math.min(height - 10, top + 240);
        List<Entry> known = new ArrayList<>(), learning = new ArrayList<>();
        for (String species : Bestiary.species(data)) {
            Beasts.Info info = Beasts.info(species, true);
            if (info == null || !Beasts.eligible(info.type(), true)) continue;
            int needed = Beasts.requiredPoints(p, info);
            int points = Bestiary.points(data, species);
            Entry e = new Entry(FormSpec.beast(species), info, points, needed, points >= needed, DruidClient.create(info.type(), p));
            (e.known() ? known : learning).add(e);
        }
        entries.addAll(known);
        if (Druid.hasMech(p, Druid.SCULPTOR)) {
            for (FormSpec.Chimera c : Bestiary.chimeras(data)) {
                Beasts.Info info = Beasts.chimera(c, true);
                if (info == null) continue;
                entries.add(new Entry(FormSpec.chimera(c), info, 0, 0, true, DruidClient.create(info.type(), p)));
            }
        }
        entries.addAll(learning);
        if (WildShape.inForm(p)) {
            addRenderableWidget(Button.builder(Component.translatable("ui.rites_and_paths.bestiary.return"), b -> {
                DruidClient.send(DruidPackets.Action.RETURN, new CompoundTag(), 0);
                onClose();
            }).bounds(left + W - 126, top + 4, 120, 16).build());
        }
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private int listTop() {
        return top + 26;
    }

    private int visibleRows() {
        return Math.max(1, (bottom - 18 - listTop()) / ROW);
    }

    private int maxScroll() {
        return Math.max(0, entries.size() - visibleRows());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        UiStyle.panel(g, left, top, left + W, bottom);
        UiStyle.header(g, left, top, left + W, top + 22);
        g.drawString(font, title, left + 8, top + 8, UiStyle.GOLD, false);
        super.render(g, mouseX, mouseY, partialTick);
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null) return;
        long cd = ClientHandlers.LOCAL_DATA.cooldowns.getOrDefault(Druid.WILD_SHAPE, 0L) - p.level().getGameTime();
        if (cd > 0 && !WildShape.inForm(p)) {
            g.drawString(font, Component.translatable("ui.rites_and_paths.bestiary.cooldown", String.format(Locale.ROOT, "%.1f", cd / 20.0)),
                    left + W - 110, top + 8, 0xFFE07070, false);
        }
        if (entries.isEmpty()) {
            List<FormattedCharSequence> lines = font.split(Component.translatable("ui.rites_and_paths.bestiary.empty"), W - 30);
            int y = listTop() + 10;
            for (FormattedCharSequence l : lines) {
                g.drawString(font, l, left + 15, y, 0xFFCCCCCC, false);
                y += 11;
            }
            return;
        }
        String tracked = Bestiary.tracked(ClientHandlers.LOCAL_DATA);
        Entry hovered = null;
        int rows = visibleRows();
        for (int i = 0; i < rows && i + scroll < entries.size(); i++) {
            Entry e = entries.get(i + scroll);
            int y = listTop() + i * ROW;
            boolean hover = mouseX >= left + 6 && mouseX < left + W - 6 && mouseY >= y && mouseY < y + ROW - 2;
            if (hover) hovered = e;
            int bg = !e.known() ? 0x66202020 : hover ? 0x8855703A : 0x66304020;
            g.fill(left + 6, y, left + W - 6, y + ROW - 2, bg);
            if (e.preview() != null) {
                float size = Math.max(e.preview().getBbHeight(), e.preview().getBbWidth());
                int scale = (int) Mth.clamp(22 / Math.max(.3f, size), 4, 30);
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, left + 22, y + ROW - 6, scale,
                        (float) (left + 22 - mouseX), (float) (y + 8 - mouseY), e.preview());
            }
            Component name = e.spec().isChimera() ? Component.translatable("druid.rites_and_paths.chimera", Beasts.name(e.spec().type))
                    : Beasts.name(e.spec().type);
            boolean isTracked = !e.spec().isChimera() && e.spec().type.equals(tracked);
            g.drawString(font, isTracked ? Component.literal("» ").append(name) : name, left + 44, y + 5,
                    e.known() ? 0xFFFFFFFF : 0xFF999999, false);
            Component second;
            if (!e.known()) {
                second = Component.translatable("ui.rites_and_paths.bestiary.knowledge", e.points(), e.needed());
            } else {
                second = Component.translatable("ui.rites_and_paths.bestiary.mana", Beasts.manaCost(p, e.info()))
                        .append("  ").append(Component.translatable("ui.rites_and_paths.bestiary.health", num(e.info().health() * Beasts.healthFactor(p))))
                        .append("  ").append(Component.translatable("ui.rites_and_paths.bestiary.damage", num(e.info().damage() * Beasts.damageFactor(p))));
            }
            g.drawString(font, second, left + 44, y + 17, e.known() ? 0xFFB8E0A0 : 0xFF888888, false);
        }
        if (entries.size() > rows) {
            int track = rows * ROW;
            int knob = Math.max(10, track * rows / entries.size());
            int ky = listTop() + (track - knob) * scroll / Math.max(1, maxScroll());
            g.fill(left + W - 5, ky, left + W - 3, ky + knob, 0xFF888888);
        }
        g.drawCenteredString(font, Component.translatable("ui.rites_and_paths.bestiary.help").withStyle(ChatFormatting.GRAY),
                left + W / 2, bottom - 13, 0xFFAAAAAA);
        if (hovered != null) g.renderComponentTooltip(font, tooltip(p, hovered, tracked), mouseX, mouseY);
    }

    private List<Component> tooltip(Player p, Entry e, String tracked) {
        List<Component> out = new ArrayList<>();
        Beasts.Info info = e.info();
        if (!e.known()) out.add(Component.translatable("ui.rites_and_paths.bestiary.locked").withStyle(ChatFormatting.RED));
        out.add(Component.translatable("ui.rites_and_paths.bestiary.health", num(info.health() * Beasts.healthFactor(p))));
        out.add(Component.translatable("ui.rites_and_paths.bestiary.damage", num(info.damage() * Beasts.damageFactor(p))));
        out.add(Component.translatable("ui.rites_and_paths.bestiary.speed", num(info.speed())));
        out.add(Component.translatable("ui.rites_and_paths.bestiary.mana", Beasts.manaCost(p, info)));
        if (info.ability() != null) {
            out.add(Component.translatable("ui.rites_and_paths.bestiary.ability",
                    Component.translatable("druid.rites_and_paths.ability." + info.ability())).withStyle(ChatFormatting.AQUA));
            out.add(Component.translatable("druid.rites_and_paths.ability." + info.ability() + ".desc").withStyle(ChatFormatting.GRAY));
        } else {
            out.add(Component.translatable("ui.rites_and_paths.bestiary.no_ability").withStyle(ChatFormatting.DARK_GRAY));
        }
        List<String> traits = new ArrayList<>(info.passives());
        if (info.flying()) traits.add(0, "fly");
        if (info.aquatic() && !info.amphibious()) traits.add(0, "swim");
        if (info.amphibious()) traits.add(0, "amphibious");
        if (!traits.isEmpty()) {
            MutableComponent list = Component.empty();
            for (int i = 0; i < traits.size(); i++) {
                if (i > 0) list.append(", ");
                list.append(Component.translatable("druid.rites_and_paths.passive." + traits.get(i)));
            }
            out.add(Component.translatable("ui.rites_and_paths.bestiary.passives", list).withStyle(ChatFormatting.GREEN));
        }
        if (e.spec().isChimera()) {
            FormSpec.Chimera c = e.spec().chimera;
            out.add(Component.translatable("ui.rites_and_paths.bestiary.parts", Beasts.name(c.head()), Beasts.name(c.torso()),
                    Beasts.name(c.legs()), Beasts.name(c.instinct())).withStyle(ChatFormatting.DARK_RED));
        } else {
            out.add(Component.translatable(e.spec().type.equals(tracked) ? "ui.rites_and_paths.bestiary.tracked"
                    : "ui.rites_and_paths.bestiary.track").withStyle(ChatFormatting.DARK_GRAY));
        }
        return out;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int rows = visibleRows();
        for (int i = 0; i < rows && i + scroll < entries.size(); i++) {
            int y = listTop() + i * ROW;
            if (mouseX < left + 6 || mouseX >= left + W - 6 || mouseY < y || mouseY >= y + ROW - 2) continue;
            Entry e = entries.get(i + scroll);
            if (button == 1 && !e.spec().isChimera()) {
                CompoundTag tag = new CompoundTag();
                tag.putString("species", e.spec().type);
                DruidClient.send(DruidPackets.Action.TRACK, tag, 0);
                return true;
            }
            if (button == 0 && e.known()) {
                DruidClient.send(DruidPackets.Action.TRANSFORM, e.spec().save(), 0);
                onClose();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, maxScroll());
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String num(double v) {
        return Math.abs(v - Math.round(v)) < .05 ? Long.toString(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }
}
