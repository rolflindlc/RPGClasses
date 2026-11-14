package io.github.rpgclasses.gear;

import io.github.rpgclasses.RpgClasses;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Supplier;

/**
 * Tiers de las armaduras de clase (rev. 2.20, «Equipo de Clase»). Los tiers bajos dan 0 o casi 0 de armadura
 * (solo stats ligeros); desde la netherita dan paridad: con la defensa propia de la clase igualan al set normal.
 *
 * <p>Valores por pieza en el orden casco, peto, grebas, botas. Bárbaro: su defensa desarmada (16 / 6 con Piel de
 * Hierro) más el set iguala al set normal del tier. Monje: el set sube su suelo para que con 5 cargas de Piedra
 * iguale al set normal (con 0 queda por debajo y con 10 por encima). Los tiers de dragón son de Ice and Fire:
 * sus recetas solo se cargan si el mod está instalado.
 */
public enum ArmorTier {
    LEATHER("leather", null, false, 5, 15, () -> SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.LEATHER),
            new int[]{0, 0, 0, 0}, 0, new int[]{0, 0, 0, 0}, 0, 0),
    CHAIN("chain", null, false, 15, 12, () -> SoundEvents.ARMOR_EQUIP_CHAIN, () -> Ingredient.of(Items.IRON_INGOT),
            new int[]{0, 0, 0, 0}, 0, new int[]{0, 0, 0, 0}, 0, 0),
    IRON("iron", null, false, 15, 9, () -> SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.of(Items.IRON_INGOT),
            new int[]{0, 0, 0, 0}, 0, new int[]{0, 1, 0, 0}, 0, 0),
    GOLD("gold", null, false, 7, 25, () -> SoundEvents.ARMOR_EQUIP_GOLD, () -> Ingredient.of(Items.GOLD_INGOT),
            new int[]{0, 0, 0, 0}, 0, new int[]{0, 0, 0, 0}, 0, 0),
    DIAMOND("diamond", null, false, 33, 10, () -> SoundEvents.ARMOR_EQUIP_DIAMOND, () -> Ingredient.of(Items.DIAMOND),
            new int[]{0, 1, 0, 0}, 0, new int[]{0, 1, 0, 0}, 0, 0),
    NETHERITE("netherite", null, false, 37, 15, () -> SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(Items.NETHERITE_INGOT),
            new int[]{1, 2, 1, 0}, 1.5f, new int[]{0, 0, 0, 0}, 1.0f, 0.1f),
    DRAGON_FIRE("dragon_fire", "fire", true, 40, 15, () -> SoundEvents.ARMOR_EQUIP_GENERIC, () -> Ingredient.of(Tags.SCALES_FIRE),
            new int[]{2, 3, 3, 1}, 3.0f, new int[]{1, 2, 1, 1}, 2.5f, 0.1f),
    DRAGON_ICE("dragon_ice", "ice", true, 40, 15, () -> SoundEvents.ARMOR_EQUIP_GENERIC, () -> Ingredient.of(Tags.SCALES_ICE),
            new int[]{2, 3, 3, 1}, 3.0f, new int[]{1, 2, 1, 1}, 2.5f, 0.1f),
    DRAGON_LIGHTNING("dragon_lightning", "lightning", true, 40, 15, () -> SoundEvents.ARMOR_EQUIP_GENERIC, () -> Ingredient.of(Tags.SCALES_LIGHTNING),
            new int[]{2, 3, 3, 1}, 3.0f, new int[]{1, 2, 1, 1}, 2.5f, 0.1f),
    DRAGONSTEEL_FIRE("dragonsteel_fire", "fire", true, 60, 20, () -> SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(Tags.DRAGONSTEEL_FIRE),
            new int[]{2, 6, 4, 2}, 3.5f, new int[]{2, 4, 3, 1}, 3.0f, 0.15f),
    DRAGONSTEEL_ICE("dragonsteel_ice", "ice", true, 60, 20, () -> SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(Tags.DRAGONSTEEL_ICE),
            new int[]{2, 6, 4, 2}, 3.5f, new int[]{2, 4, 3, 1}, 3.0f, 0.15f),
    DRAGONSTEEL_LIGHTNING("dragonsteel_lightning", "lightning", true, 60, 20, () -> SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(Tags.DRAGONSTEEL_LIGHTNING),
            new int[]{2, 6, 4, 2}, 3.5f, new int[]{2, 4, 3, 1}, 3.0f, 0.15f);

    public final String id;
    /** Elemento de la variante (fire, ice, lightning) o null. */
    public final String element;
    /** Material de Ice and Fire: recetas condicionadas a que el mod esté instalado. */
    public final boolean iceAndFire;
    final int durabilityMultiplier;
    final int enchantability;
    final Supplier<SoundEvent> sound;
    final Supplier<Ingredient> repair;
    final int[] barbarian;
    final float barbarianToughness;
    final int[] monk;
    final float monkToughness;
    final float knockbackResistance;

    ArmorTier(String id, String element, boolean iceAndFire, int durabilityMultiplier, int enchantability,
              Supplier<SoundEvent> sound, Supplier<Ingredient> repair, int[] barbarian, float barbarianToughness,
              int[] monk, float monkToughness, float knockbackResistance) {
        this.id = id;
        this.element = element;
        this.iceAndFire = iceAndFire;
        this.durabilityMultiplier = durabilityMultiplier;
        this.enchantability = enchantability;
        this.sound = sound;
        this.repair = repair;
        this.barbarian = barbarian;
        this.barbarianToughness = barbarianToughness;
        this.monk = monk;
        this.monkToughness = monkToughness;
        this.knockbackResistance = knockbackResistance;
    }

    public boolean fireResistant() {
        return ordinal() >= NETHERITE.ordinal();
    }

    /** Tier de dragón (escamas) o de dragonsteel. */
    public boolean isDragon() {
        return ordinal() >= DRAGON_FIRE.ordinal() && ordinal() <= DRAGON_LIGHTNING.ordinal();
    }

    public boolean isDragonsteel() {
        return ordinal() >= DRAGONSTEEL_FIRE.ordinal();
    }

    /** Tags de los materiales de Ice and Fire (con entradas opcionales: no fallan sin el mod). */
    public static final class Tags {
        public static final TagKey<Item> SCALES_FIRE = ItemTags.create(RpgClasses.id("iceandfire/scales_fire"));
        public static final TagKey<Item> SCALES_ICE = ItemTags.create(RpgClasses.id("iceandfire/scales_ice"));
        public static final TagKey<Item> SCALES_LIGHTNING = ItemTags.create(RpgClasses.id("iceandfire/scales_lightning"));
        public static final TagKey<Item> DRAGONSTEEL_FIRE = ItemTags.create(RpgClasses.id("iceandfire/dragonsteel_fire"));
        public static final TagKey<Item> DRAGONSTEEL_ICE = ItemTags.create(RpgClasses.id("iceandfire/dragonsteel_ice"));
        public static final TagKey<Item> DRAGONSTEEL_LIGHTNING = ItemTags.create(RpgClasses.id("iceandfire/dragonsteel_lightning"));
        public static final TagKey<Item> DRAGONBONE = ItemTags.create(RpgClasses.id("iceandfire/dragonbone"));

        private Tags() {}
    }
}
