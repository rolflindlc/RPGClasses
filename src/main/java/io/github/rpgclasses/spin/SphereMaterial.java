package io.github.rpgclasses.spin;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.Tags;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Tiers de las bolas (adenda del Maestro del Giro, sección 3): hierro, diamante y netherita. Son los únicos objetos de bola;
 * los materiales especiales (slime, cobre, amatista...) son mejoras que se le ponen a una bola de cualquier tier
 * ({@link SphereUpgrade}). Aquí solo lo que es propio del objeto (durabilidad como las herramientas del mismo material,
 * encantabilidad, reparación y color); el daño y los efectos se leen de la habilidad {@code sphere_throw} (tools/spin.py).
 */
public enum SphereMaterial {
    IRON("iron", 250, 14, () -> Ingredient.of(Tags.Items.INGOTS_IRON), 0xD9D9D9, false),
    DIAMOND("diamond", 1561, 10, () -> Ingredient.of(Tags.Items.GEMS_DIAMOND), 0x5CE1D5, false),
    NETHERITE("netherite", 2031, 15, () -> Ingredient.of(Tags.Items.INGOTS_NETHERITE), 0x5A4A4E, true);

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
