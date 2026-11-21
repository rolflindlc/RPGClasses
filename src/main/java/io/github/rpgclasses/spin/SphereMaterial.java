package io.github.rpgclasses.spin;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.Tags;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Materiales de las bolas (adenda del Maestro del Giro, sección 3). Aquí solo lo que es propio del objeto (durabilidad
 * como las herramientas del mismo material, encantabilidad, reparación y color); el daño y los números de cada efecto
 * se leen de la habilidad {@code sphere_throw} (tools/spin.py), como el resto del balance.
 */
public enum SphereMaterial {
    IRON("iron", 250, 14, () -> Ingredient.of(Tags.Items.INGOTS_IRON), 0xD9D9D9, false),
    DIAMOND("diamond", 1561, 10, () -> Ingredient.of(Tags.Items.GEMS_DIAMOND), 0x5CE1D5, false),
    NETHERITE("netherite", 2031, 15, () -> Ingredient.of(Tags.Items.INGOTS_NETHERITE), 0x5A4A4E, true),
    SLIME("slime", 200, 12, () -> Ingredient.of(Tags.Items.SLIMEBALLS), 0x7CCB6A, false),
    COPPER("copper", 190, 13, () -> Ingredient.of(Tags.Items.INGOTS_COPPER), 0xD9824F, false),
    AMETHYST("amethyst", 300, 16, () -> Ingredient.of(Tags.Items.GEMS_AMETHYST), 0xA77BDB, false),
    ENDER_PEARL("ender_pearl", 150, 15, () -> Ingredient.of(Tags.Items.ENDER_PEARLS), 0x2E9B83, false),
    GOLD("gold", 32, 22, () -> Ingredient.of(Tags.Items.INGOTS_GOLD), 0xF2D14A, false),
    REDSTONE("redstone", 180, 12, () -> Ingredient.of(Tags.Items.DUSTS_REDSTONE), 0xD0281E, false),
    DRIPSTONE("dripstone", 160, 8, () -> Ingredient.of(Items.POINTED_DRIPSTONE), 0x9E8066, false);

    public final String id;
    public final int durability;
    public final int enchantability;
    private final Supplier<Ingredient> repair;
    /** Color (RGB) de partículas, estela y HUD. */
    public final int color;
    /** Resiste el fuego y la lava (netherita). */
    public final boolean fireResistant;

    SphereMaterial(String id, int durability, int enchantability, Supplier<Ingredient> repair, int color, boolean fireResistant) {
        this.id = id;
        this.durability = durability;
        this.enchantability = enchantability;
        this.repair = repair;
        this.color = color;
        this.fireResistant = fireResistant;
    }

    public Ingredient repairIngredient() {
        return repair.get();
    }

    /** Nombre del objeto: {@code <id>_sphere}. */
    public String itemName() {
        return id + "_sphere";
    }

    public float red() {
        return ((color >> 16) & 0xFF) / 255f;
    }

    public float green() {
        return ((color >> 8) & 0xFF) / 255f;
    }

    public float blue() {
        return (color & 0xFF) / 255f;
    }

    public static SphereMaterial byId(String id) {
        if (id == null) return null;
        for (SphereMaterial m : values()) if (m.id.equals(id.toLowerCase(Locale.ROOT))) return m;
        return null;
    }

    public static SphereMaterial byIndex(int i) {
        SphereMaterial[] all = values();
        return i >= 0 && i < all.length ? all[i] : IRON;
    }
}
