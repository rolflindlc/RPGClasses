package io.github.rpgclasses.gear;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import io.github.rpgclasses.RpgClasses;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Nunchaku, sai o bo de un tier. Para cualquier clase es un arma (ligera a efectos de muros) con el daño de la espada
 * del tier por el factor del arma. Para el Monje cuenta como desarmado ({@link GearRules#monkMartialFactor}).
 * El sai se mantiene con el clic derecho para la guardia y el parry ({@link GearEvents}).
 */
public class MartialWeaponItem extends Item {
    private static final UUID REACH_ID = UUID.nameUUIDFromBytes((RpgClasses.MODID + ":martial_reach").getBytes(StandardCharsets.UTF_8));

    public final WeaponKind kind;
    public final WeaponTier tier;
    private Multimap<Attribute, AttributeModifier> modifiers;

    public MartialWeaponItem(WeaponKind kind, WeaponTier tier, Properties properties) {
        super(properties);
        this.kind = kind;
        this.tier = tier;
    }

    /** Daño total del arma para cualquier clase (incluye el 1 base del jugador). */
    public double totalDamage() {
        return tier.swordDamage * kind.damageFactor;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot != EquipmentSlot.MAINHAND) return super.getDefaultAttributeModifiers(slot);
        if (modifiers == null) {
            ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
            b.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Arma marcial",
                    totalDamage() - 1, AttributeModifier.Operation.ADDITION));
            double speed = kind.attackSpeed() + lightSpeed();
            b.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Arma marcial",
                    speed - WeaponKind.FIST_SPEED, AttributeModifier.Operation.ADDITION));
            double reach = kind.reach + lightReach();
            if (reach != 0) {
                b.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(REACH_ID, "Arma marcial", reach, AttributeModifier.Operation.ADDITION));
            }
            modifiers = b.build();
        }
        return modifiers;
    }

    /** Stat ligero del tier: madera, nunchaku un poco más rápido. */
    private double lightSpeed() {
        return kind == WeaponKind.NUNCHAKU && tier == WeaponTier.WOOD ? 0.1 : 0;
    }

    /** Stat ligero del tier: madera y diamante, bo con +0,25 bloques de alcance. */
    private double lightReach() {
        return kind == WeaponKind.BO && (tier == WeaponTier.WOOD || tier == WeaponTier.DIAMOND) ? 0.25 : 0;
    }

    /** Ventana de parry del sai en ticks (0,25 s, +1 tick en madera, hierro y diamante). */
    public int parryWindowTicks() {
        return 5 + (tier == WeaponTier.WOOD || tier == WeaponTier.IRON || tier == WeaponTier.DIAMOND ? 1 : 0)
                + (tier.lateTier() >= 1 ? 1 : 0);
    }

    /** Reducción de daño de la guardia del sai fuera de la ventana de parry. */
    public double guardReduction() {
        return 0.30 + (tier == WeaponTier.STONE || tier == WeaponTier.DIAMOND ? 0.05 : 0);
    }

    /** Ángulo total del barrido del bo, en grados. */
    public double sweepDegrees() {
        return tier == WeaponTier.IRON || tier.lateTier() >= 1 ? 160 : 150;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return tier.enchantability;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return enchantment.category == EnchantmentCategory.WEAPON || enchantment.category == EnchantmentCategory.BREAKABLE
                || super.canApplyAtEnchantingTable(stack, enchantment);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return tier.repair.get().test(repair);
    }

    // --- Guardia del sai ---

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return kind == WeaponKind.SAI ? UseAnim.BLOCK : UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return kind == WeaponKind.SAI ? 72000 : 0;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (kind != WeaponKind.SAI) return InteractionResultHolder.pass(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("gear.rpgclasses.weapon." + kind.id + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gear.rpgclasses.weapon.monk").withStyle(ChatFormatting.DARK_GREEN));
        if (tier.lateTier() > 0) {
            tooltip.add(Component.translatable("gear.rpgclasses.weapon." + kind.id + ".late" + tier.lateTier()).withStyle(ChatFormatting.YELLOW));
        }
        if (tier.element != null) {
            tooltip.add(Component.translatable("gear.rpgclasses.weapon.element." + tier.element).withStyle(ChatFormatting.AQUA));
        }
    }
}
