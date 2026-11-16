package io.github.ironrituals.compat.jei;

import io.github.ironrituals.IronRituals;
import io.github.ironrituals.compat.IssCompat;
import io.github.ironrituals.item.RitualTomeItem;
import io.github.ironrituals.registry.ModItems;
import io.github.ironrituals.ritual.recipe.RitualComponent;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class RitualCategory implements IRecipeCategory<RitualJeiEntry> {
    public static final RecipeType<RitualJeiEntry> TYPE = RecipeType.create(IronRituals.MOD_ID, "ritual", RitualJeiEntry.class);
    private static final int W = 160, H = 76;

    private final IDrawable background;
    private final IDrawable icon;

    public RitualCategory(IGuiHelper gui) {
        this.background = gui.createBlankDrawable(W, H);
        this.icon = gui.createDrawableItemStack(new ItemStack(ModItems.RITUAL_CORE.get()));
    }

    @Override
    public RecipeType<RitualJeiEntry> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.ironrituals.ritual");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RitualJeiEntry entry, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.CATALYST, 2, 2).addItemStack(RitualTomeItem.create(entry.recipe().ritual()));
        List<RitualComponent> comps = entry.tier().components();
        for (int i = 0; i < comps.size(); i++) {
            RitualComponent c = comps.get(i);
            int x = 2 + (i % 8) * 19, y = 38 + (i / 8) * 19;
            List<ItemStack> stacks = new ArrayList<>();
            for (ItemStack s : c.displayStacks()) stacks.add(s.copyWithCount(c.count()));
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.INPUT, x, y).addItemStacks(stacks);
            if (c.isSoul()) {
                Component soul = c.soul().describe().copy().withStyle(ChatFormatting.RED);
                slot.addTooltipCallback((view, tooltip) -> tooltip.add(soul));
            }
        }
    }

    @Override
    public void draw(RitualJeiEntry entry, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, RitualTomeItem.ritualName(RitualTomeItem.create(entry.recipe().ritual())), 22, 2, 0xFF5A2D82, false);
        g.drawString(font, Component.translatable("jei.ironrituals.details", entry.tier().level(), Math.round(entry.tier().mana()),
                IssCompat.schoolName(entry.recipe().school())), 22, 13, 0xFF404040, false);
        if (!entry.recipe().catalysts().isEmpty()) {
            g.drawString(font, Component.translatable("jei.ironrituals.secrets"), 2, 26, 0xFF808080, false);
        }
    }
}
