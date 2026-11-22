package io.github.rpgclasses.druid;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Parte de bestia del Escultor (cabeza, torso, patas o instinto) arrancada de unos restos con el ritual. Guarda la
 * especie en {@code species}. Va en su hueco del Recipiente de Carne.
 */
public class BeastPartItem extends Item {
    public static final String SPECIES = "species";
    /** 0 cabeza (ataque), 1 torso (vida y tamaño), 2 patas (movimiento), 3 instinto (habilidad). */
    public final int part;

    public BeastPartItem(int part, Properties properties) {
        super(properties);
        this.part = part;
    }

    public static String species(ItemStack stack) {
        return stack.getTag() == null ? "" : stack.getTag().getString(SPECIES);
    }

    @Override
    public Component getName(ItemStack stack) {
        String species = species(stack);
        if (species.isEmpty()) return super.getName(stack);
        return Component.translatable(getDescriptionId() + ".named", Beasts.name(species));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.rites_and_paths.beast_part.slot",
                Component.translatable("druid.rites_and_paths.part.slot." + FormSpec.Chimera.PARTS[part])).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.rites_and_paths.beast_part.sculptor").withStyle(ChatFormatting.DARK_GRAY));
    }
}
