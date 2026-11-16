package io.github.ironrituals.client;

import io.github.ironrituals.compat.IssCompat;
import io.github.ironrituals.core.CircleGeometry;
import io.github.ironrituals.item.RitualTomeItem;
import io.github.ironrituals.ritual.recipe.RitualCatalyst;
import io.github.ironrituals.ritual.recipe.RitualComponent;
import io.github.ironrituals.ritual.recipe.RitualRecipe;
import io.github.ironrituals.ritual.recipe.RitualTier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI de lectura del grimorio (plan, 3.D): lore, pista enigmática, diagrama del círculo y componentes por nivel.
 * Los catalizadores solo aparecen como pistas secundarias: hay que descubrirlos.
 */
public class RitualTomeScreen extends Screen {
    private static final int W = 300, H = 200;
    private static final int BG = 0xF0170C1F, BORDER = 0xFF8A55C8, INNER = 0xFF2A1638;
    private static final int TEXT = 0xFFE6D9F2, MUTED = 0xFF9C8AAE, HINT = 0xFFC79BFF;

    @Nullable private final ResourceLocation ritualId;
    @Nullable private RitualRecipe recipe;
    private int selected;
    private int left, top;

    public RitualTomeScreen(ItemStack tome) {
        super(RitualTomeItem.ritualName(tome));
        this.ritualId = RitualTomeItem.ritualId(tome);
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        if (minecraft != null && minecraft.level != null) recipe = RitualRecipe.find(minecraft.level, ritualId);
        if (recipe != null) {
            List<RitualTier> tiers = recipe.tiers();
            selected = Math.min(selected, tiers.size() - 1);
            if (tiers.size() > 1) {
                for (int i = 0; i < tiers.size(); i++) {
                    final int idx = i;
                    addRenderableWidget(Button.builder(Component.literal(roman(tiers.get(i).level())), b -> selected = idx)
                            .bounds(left + 168 + i * 24, top + 30, 22, 14).build());
                }
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + W - 54, top + H - 20, 48, 14).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(left - 1, top - 1, left + W + 1, top + H + 1, BORDER);
        g.fill(left, top, left + W, top + H, BG);
        g.fill(left + 160, top + 26, left + W - 6, top + H - 24, INNER);

        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.BOLD), left + W / 2, top + 7, TEXT);

        String id = ritualId == null ? "unknown" : ritualId.getNamespace() + "." + ritualId.getPath();
        int y = top + 22;
        if (recipe != null) {
            Component meta = Component.translatable("gui.ironrituals.tome.meta", IssCompat.schoolName(recipe.school()),
                    roman(recipe.minCircle()));
            g.drawString(font, meta, left + 8, y, MUTED, false);
            y += 14;
        }
        y = drawWrapped(g, Component.translatable("ritual." + id + ".lore"), left + 8, y, 148, TEXT) + 6;
        y = drawWrapped(g, Component.translatable("ritual." + id + ".hint").withStyle(ChatFormatting.ITALIC), left + 8, y, 148, HINT) + 8;

        if (recipe == null) {
            drawWrapped(g, Component.translatable("gui.ironrituals.tome.blank"), left + 168, top + 34, 120, MUTED);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        if (!recipe.catalysts().isEmpty()) {
            g.drawString(font, Component.translatable("gui.ironrituals.tome.secrets").withStyle(ChatFormatting.UNDERLINE), left + 8, y, MUTED, false);
            y += 11;
            for (RitualCatalyst c : recipe.catalysts()) {
                if (y > top + H - 30) break;
                y = drawWrapped(g, Component.literal("• ").append(Component.translatable("ritual." + id + ".catalyst." + c.id())),
                        left + 8, y, 148, HINT) + 3;
            }
        }

        RitualTier tier = recipe.tiers().get(selected);
        g.drawString(font, Component.translatable("gui.ironrituals.tome.level", roman(tier.level()), Math.round(tier.mana())),
                left + 168, top + 48, TEXT, false);
        drawCircle(g, left + 168 + 60, top + 92, tier.level());
        drawComponents(g, tier, left + 166, top + 136, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Diagrama del círculo: el núcleo en el centro y las velas de cada anillo. */
    private void drawCircle(GuiGraphics g, int cx, int cy, int level) {
        int cell = 3;
        int r = CircleGeometry.ringRadius(level);
        g.fill(cx - r * cell - 2, cy - r * cell - 2, cx + r * cell + 3, cy + r * cell + 3, 0x40FFFFFF);
        for (int l = 1; l <= level; l++) {
            int color = l == 1 ? 0xFFE7C6FF : l == 2 ? 0xFFB88CF0 : 0xFF8F5CD6;
            for (int[] o : CircleGeometry.ringOffsets(CircleGeometry.ringRadius(l))) {
                int x = cx + o[0] * cell, z = cy + o[1] * cell;
                g.fill(x - 1, z - 1, x + 2, z + 2, color);
            }
        }
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFD36B);
    }

    private void drawComponents(GuiGraphics g, RitualTier tier, int x0, int y0, int mouseX, int mouseY) {
        int perRow = 6;
        List<Component> tooltip = null;
        ItemStack hovered = ItemStack.EMPTY;
        List<RitualComponent> comps = tier.components();
        for (int i = 0; i < comps.size(); i++) {
            RitualComponent c = comps.get(i);
            int x = x0 + (i % perRow) * 20, y = y0 + (i / perRow) * 20;
            ItemStack[] options = c.displayStacks();
            if (options.length == 0) continue;
            ItemStack shown = options[(int) ((System.currentTimeMillis() / 1000) % options.length)].copyWithCount(1);
            g.renderItem(shown, x, y);
            g.renderItemDecorations(font, shown, x, y, c.count() > 1 ? String.valueOf(c.count()) : null);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                if (c.isSoul()) {
                    tooltip = new ArrayList<>(List.of(shown.getHoverName(), c.soul().describe().copy().withStyle(ChatFormatting.RED)));
                } else {
                    hovered = shown;
                }
            }
        }
        if (tooltip != null) g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        else if (!hovered.isEmpty()) g.renderTooltip(font, hovered, mouseX, mouseY);
    }

    private int drawWrapped(GuiGraphics g, Component text, int x, int y, int width, int color) {
        for (FormattedCharSequence line : font.split(text, width)) {
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(n);
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
