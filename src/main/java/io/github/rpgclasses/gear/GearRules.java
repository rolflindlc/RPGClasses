package io.github.rpgclasses.gear;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.registry.ModAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

/** Reglas compartidas del equipo de clase: exención de la regla desarmada y daño de las armas marciales. */
public final class GearRules {
    /** Armas marciales (nunchaku, sai, bo y las que añada un pack): para el Monje cuentan como desarmado. */
    public static final TagKey<Item> MARTIAL = ItemTags.create(RpgClasses.id("martial_weapons"));
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Puente con Curios (lo pone el compat si el mod está): primeras nudilleras en las ranuras de Curios, o vacío. */
    public static Function<Player, ItemStack> curioKnuckles = p -> ItemStack.EMPTY;

    /** La pieza no cuenta como armadura para las pasivas desarmadas de su clase (tag class_armor/&lt;clase&gt;). */
    public static boolean exempt(ServerPlayer player, ItemStack stack) {
        GearClass c = GearClass.of(player);
        return c != null && stack.is(c.armorTag);
    }

    public static boolean isMartial(ItemStack stack) {
        return !stack.isEmpty() && stack.is(MARTIAL);
    }

    /** Número de piezas equipadas de un tier y una clase de armadura. */
    public static int pieces(Player player, GearClass c, ArmorTier tier) {
        int n = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (player.getItemBySlot(slot).getItem() instanceof ClassArmorItem a && a.gearClass == c && a.tier == tier) n++;
        }
        return n;
    }

    /** Tier del set completo (4 piezas del mismo tier y clase), o null. */
    public static ArmorTier fullSet(Player player, GearClass c) {
        ArmorTier found = null;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!(player.getItemBySlot(slot).getItem() instanceof ClassArmorItem a) || a.gearClass != c) return null;
            if (found == null) found = a.tier;
            else if (found != a.tier) return null;
        }
        return found;
    }

    /** Nudilleras que lleva el jugador: ranura de Curios o, si no, la mano secundaria. Null si no lleva. */
    public static KnucklesItem knuckles(Player player) {
        ItemStack curio = curioKnuckles.apply(player);
        if (curio.getItem() instanceof KnucklesItem k) return k;
        if (player.getOffhandItem().getItem() instanceof KnucklesItem k) return k;
        return null;
    }

    /** Daño plano de las nudilleras en un golpe con la mano vacía: el Monje suma el bonus de tier; el resto, un daño modesto. */
    public static double knucklesBonus(ServerPlayer player, boolean monk) {
        KnucklesItem k = knuckles(player);
        if (k == null) return 0;
        return monk ? k.tier.monkBonus : Math.max(0, (k.tier.swordDamage - 1) * 0.6);
    }

    /**
     * Factor de daño de un arma marcial para el Monje: su golpe pasa a valer el daño desarmado (1 + desarmado + bonus
     * de tier) por el factor de golpe del arma (nunchaku 0,7; sai 1; bo 1,35). Rev. 2.21: sin compensar la velocidad
     * de ataque, porque con Epic Fight el ritmo lo marcan las animaciones (la compensación daba ×3 con el nunchaku).
     */
    public static double monkMartialFactor(ServerPlayer player, MartialWeaponItem weapon) {
        double weaponBase = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (weaponBase <= 0) return 1;
        double monkBase = 1 + ModAttributes.value(player, ModAttributes.UNARMED_DAMAGE) + weapon.tier.monkBonus;
        return monkBase * weapon.kind.damageFactor / weaponBase;
    }

    /** Cargas de Fuego por golpe: 1, también con armas marciales (rev. 2.21). */
    public static double chargesPerHit(ServerPlayer player) {
        return 1;
    }

    private GearRules() {}
}
