package io.github.rpgclasses.gear;

import io.github.rpgclasses.RpgClasses;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

/** Material de una armadura de clase: la textura es {@code textures/models/armor/<clase>_<tier>_layer_N.png}. */
public final class ClassArmorMaterial implements ArmorMaterial {
    /** Durabilidad base por pieza (como vanilla), en el orden de ArmorItem.Type: casco, peto, grebas y botas. */
    private static final int[] BASE_DURABILITY = {11, 16, 15, 13};

    private final GearClass gearClass;
    private final ArmorTier tier;

    public ClassArmorMaterial(GearClass gearClass, ArmorTier tier) {
        this.gearClass = gearClass;
        this.tier = tier;
    }

    private static int index(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 0;
            case CHESTPLATE -> 1;
            case LEGGINGS -> 2;
            case BOOTS -> 3;
        };
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return BASE_DURABILITY[index(type)] * tier.durabilityMultiplier;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return (gearClass == GearClass.BARBARIAN ? tier.barbarian : tier.monk)[index(type)];
    }

    @Override
    public int getEnchantmentValue() {
        return tier.enchantability;
    }

    @Override
    public SoundEvent getEquipSound() {
        return tier.sound.get();
    }

    @Override
    public Ingredient getRepairIngredient() {
        return tier.repair.get();
    }

    @Override
    public String getName() {
        return RpgClasses.MODID + ":" + gearClass.id + "_" + tier.id;
    }

    @Override
    public float getToughness() {
        return gearClass == GearClass.BARBARIAN ? tier.barbarianToughness : tier.monkToughness;
    }

    @Override
    public float getKnockbackResistance() {
        return gearClass == GearClass.BARBARIAN ? tier.knockbackResistance : tier.knockbackResistance * 0.5f;
    }
}
