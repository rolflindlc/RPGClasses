package io.github.rpgclasses.gear;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Supplier;

/**
 * Tiers de las armas marciales (nudilleras, nunchaku, sai y bo).
 *
 * <p>{@code swordDamage} es el daño total de la espada del tier (incluye el 1 base del jugador): fija el daño de las
 * armas para cualquier clase. {@code monkBonus} es el bonus plano sobre el daño desarmado del Monje: la diferencia
 * entre la espada del tier y la de diamante, porque el Monje ya está calibrado contra una espada de diamante.
 * Los valores de Ice and Fire (hueso de dragón y dragonsteel) son objetivos hasta leer su config [LEER].
 */
public enum WeaponTier {
    WOOD("wood", null, false, 4, 0, 59, 15, () -> Ingredient.of(ItemTags.PLANKS)),
    STONE("stone", null, false, 5, 0, 131, 5, () -> Ingredient.of(ItemTags.STONE_TOOL_MATERIALS)),
    IRON("iron", null, false, 6, 0, 250, 14, () -> Ingredient.of(Items.IRON_INGOT)),
    GOLD("gold", null, false, 4, 0, 32, 22, () -> Ingredient.of(Items.GOLD_INGOT)),
    DIAMOND("diamond", null, false, 7, 0, 1561, 10, () -> Ingredient.of(Items.DIAMOND)),
    NETHERITE("netherite", null, false, 8, 1, 2031, 15, () -> Ingredient.of(Items.NETHERITE_INGOT)),
    DRAGONBONE("dragonbone", null, true, 9, 2, 1660, 22, () -> Ingredient.of(ArmorTier.Tags.DRAGONBONE)),
    DRAGONSTEEL_FIRE("dragonsteel_fire", "fire", true, 12, 5, 8000, 10, () -> Ingredient.of(ArmorTier.Tags.DRAGONSTEEL_FIRE)),
    DRAGONSTEEL_ICE("dragonsteel_ice", "ice", true, 12, 5, 8000, 10, () -> Ingredient.of(ArmorTier.Tags.DRAGONSTEEL_ICE)),
    DRAGONSTEEL_LIGHTNING("dragonsteel_lightning", "lightning", true, 12, 5, 8000, 10, () -> Ingredient.of(ArmorTier.Tags.DRAGONSTEEL_LIGHTNING));

    public final String id;
    public final String element;
    public final boolean iceAndFire;
    public final double swordDamage;
    public final double monkBonus;
    public final int durability;
    public final int enchantability;
    final Supplier<Ingredient> repair;

    WeaponTier(String id, String element, boolean iceAndFire, double swordDamage, double monkBonus, int durability,
               int enchantability, Supplier<Ingredient> repair) {
        this.id = id;
        this.element = element;
        this.iceAndFire = iceAndFire;
        this.swordDamage = swordDamage;
        this.monkBonus = monkBonus;
        this.durability = durability;
        this.enchantability = enchantability;
        this.repair = repair;
    }

    public boolean fireResistant() {
        return ordinal() >= NETHERITE.ordinal();
    }

    /** 0 hasta diamante, 1 netherita, 2 hueso de dragón, 3 dragonsteel: nivel de los efectos de late game. */
    public int lateTier() {
        if (ordinal() >= DRAGONSTEEL_FIRE.ordinal()) return 3;
        if (this == DRAGONBONE) return 2;
        if (this == NETHERITE) return 1;
        return 0;
    }
}
