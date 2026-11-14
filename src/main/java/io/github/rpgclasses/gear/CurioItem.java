package io.github.rpgclasses.gear;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Objeto de un curio del catálogo. Se equipa en la ranura de Curios que indica su tag (curios:&lt;ranura&gt;). */
public class CurioItem extends Item {
    public final Curio curio;

    public CurioItem(Curio curio, Properties properties) {
        super(properties.stacksTo(1));
        this.curio = curio;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return curio.rarity == Curio.Rarity.RELIC;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        ChatFormatting color = switch (curio.rarity) {
            case COMMON -> ChatFormatting.WHITE;
            case RARE -> ChatFormatting.AQUA;
            case EPIC -> ChatFormatting.LIGHT_PURPLE;
            case RELIC -> ChatFormatting.GOLD;
        };
        tooltip.add(Component.translatable("curio.rpgclasses.rarity." + curio.rarity.name().toLowerCase(java.util.Locale.ROOT)).withStyle(color));
        if (curio.kind == Curio.Kind.CLASS) {
            tooltip.add(Component.translatable("curio.rpgclasses.requires_class",
                    Component.translatable("class.rpgclasses." + curio.requirement + ".name")).withStyle(ChatFormatting.BLUE));
        } else if (curio.kind == Curio.Kind.RACE) {
            tooltip.add(Component.translatable("curio.rpgclasses.requires_race",
                    Component.translatable("race.rpgclasses." + curio.requirement + ".name")).withStyle(ChatFormatting.GREEN));
        }
        tooltip.add(Component.translatable("curio.rpgclasses." + curio.id + ".desc").withStyle(ChatFormatting.GRAY));
        if (curio.rarity == Curio.Rarity.RELIC) tooltip.add(Component.translatable("curio.rpgclasses.relic_rule").withStyle(ChatFormatting.DARK_GRAY));
    }
}
