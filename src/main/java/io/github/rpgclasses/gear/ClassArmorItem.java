package io.github.rpgclasses.gear;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.registry.ModAttributes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Pieza de armadura de Bárbaro o Monje. Cualquier clase puede llevarla y recibe su armadura y sus stats ligeros;
 * el efecto de set completo (4 piezas del mismo tier) solo funciona para su clase ({@link SetEffects}).
 * Para su clase no desactiva las pasivas desarmadas ni borra las cargas del Monje ({@link GearRules#exempt}).
 */
public class ClassArmorItem extends ArmorItem {
    public final GearClass gearClass;
    public final ArmorTier tier;
    private Multimap<Attribute, AttributeModifier> modifiers;

    public ClassArmorItem(GearClass gearClass, ArmorTier tier, Type type, Properties properties) {
        super(new ClassArmorMaterial(gearClass, tier), type, properties);
        this.gearClass = gearClass;
        this.tier = tier;
    }

    /** Stat ligero por pieza (con 4 piezas, el total): pequeño, pensado como sabor de los tiers bajos. */
    private record Light(Supplier<Attribute> attribute, AttributeModifier.Operation op, double amount) {}

    private Light light() {
        boolean barb = gearClass == GearClass.BARBARIAN;
        return switch (tier) {
            case LEATHER -> new Light(() -> Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.MULTIPLY_BASE, 0.0075);
            case CHAIN, IRON -> barb || tier == ArmorTier.CHAIN
                    ? new Light(() -> Attributes.KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADDITION, 0.0125) : null;
            case GOLD -> new Light(() -> Attributes.LUCK, AttributeModifier.Operation.ADDITION, 0.25);
            case DIAMOND -> new Light(ModAttributes.CRIT_CHANCE, AttributeModifier.Operation.ADDITION, barb ? 0.0075 : 0.005);
            default -> null;
        };
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        Multimap<Attribute, AttributeModifier> base = super.getDefaultAttributeModifiers(slot);
        if (slot != getEquipmentSlot()) return base;
        if (modifiers == null) {
            ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
            b.putAll(base);
            Light l = light();
            if (l != null && l.attribute().get() != null) {
                UUID id = UUID.nameUUIDFromBytes((RpgClasses.MODID + ":gear_light:" + slot.getName()).getBytes(StandardCharsets.UTF_8));
                b.put(l.attribute().get(), new AttributeModifier(id, "Equipo de clase", l.amount(), l.op()));
            }
            modifiers = b.build();
        }
        return modifiers;
    }

    @Override
    public boolean makesPiglinsNeutral(ItemStack stack, LivingEntity wearer) {
        return tier == ArmorTier.GOLD;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        String set = SetEffects.setKey(gearClass, tier);
        tooltip.add(Component.translatable("gear.rpgclasses.set_header",
                Component.translatable("class.rpgclasses." + gearClass.id + ".name")).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("gear.rpgclasses.set." + set + ".name").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("gear.rpgclasses.set." + set + ".desc").withStyle(ChatFormatting.GRAY));
        if (tier.element != null) {
            tooltip.add(Component.translatable("gear.rpgclasses.element." + tier.element).withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("gear.rpgclasses.exempt." + gearClass.id).withStyle(ChatFormatting.DARK_GREEN));
    }
}
