package io.github.rpgclasses.bard;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Partitura (pergamino de conjuro con textura de partitura). Lleva un conjuro de la escuela musical y su nivel; solo
 * puede guardarse en un instrumento (atril) y sube de nivel con tinta en la mesa de trabajo ({@link SheetMusicUpgradeRecipe}).
 */
public class SheetMusicItem extends Item {
    public SheetMusicItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public Component getName(ItemStack stack) {
        MusicSpellInfo spell = Sheets.spellOf(stack);
        if (spell == null) return super.getName(stack);
        return Component.translatable("item.rites_and_paths.sheet_music.named", Component.translatable(spell.nameKey()));
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        MusicSpellInfo spell = Sheets.spellOf(stack);
        if (spell == null) return Rarity.COMMON;
        return spell.combo >= 30 ? Rarity.EPIC : spell.combo >= 20 ? Rarity.RARE : spell.combo >= 10 ? Rarity.UNCOMMON : Rarity.COMMON;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        MusicSpellInfo spell = Sheets.spellOf(stack);
        return spell != null && Sheets.levelOf(stack) >= spell.maxLevel;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        MusicSpellInfo spell = Sheets.spellOf(stack);
        if (spell == null) {
            tooltip.add(Component.translatable("tooltip.rites_and_paths.sheet_music.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.rites_and_paths.sheet_music.level", Sheets.levelOf(stack), spell.maxLevel)
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.rites_and_paths.sheet_music.combo", spell.combo).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(spell.nameKey() + ".guide").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.rites_and_paths.sheet_music.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
