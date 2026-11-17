package io.github.rpgclasses.gear;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Nudilleras: se llevan en la ranura de manos de Curios (o en la mano secundaria si Curios no está) y mejoran los
 * golpes con la mano vacía. Para el Monje suman el bonus de tier a su daño desarmado; para el resto, un daño plano
 * modesto ({@link GearRules#knucklesBonus}).
 */
public class KnucklesItem extends Item {
    public final WeaponTier tier;

    public KnucklesItem(WeaponTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("gear.rites_and_paths.weapon.knuckles.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gear.rites_and_paths.weapon.monk").withStyle(ChatFormatting.DARK_GREEN));
        if (tier.lateTier() > 0) {
            tooltip.add(Component.translatable("gear.rites_and_paths.weapon.knuckles.late" + tier.lateTier()).withStyle(ChatFormatting.YELLOW));
        }
        if (tier.element != null) {
            tooltip.add(Component.translatable("gear.rites_and_paths.weapon.element." + tier.element).withStyle(ChatFormatting.AQUA));
        }
    }
}
