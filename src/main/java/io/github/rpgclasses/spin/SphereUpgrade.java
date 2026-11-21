package io.github.rpgclasses.spin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.Tags;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Mejoras de bola: se ponen en la mesa de trabajo a una bola de cualquier tier (hierro, diamante, netherita) con unas
 * cuantas unidades del material ({@link SphereUpgradeRecipe}) y le dan su efecto. El tier pone el daño base y su propio
 * efecto; la mejora multiplica el daño y añade el suyo. Se guarda en el NBT de la bola (una sola mejora: otra la
 * sustituye). Los números de cada efecto están en {@code sphere_throw} (tools/spin.py).
 */
public enum SphereUpgrade {
    SLIME("slime", 0x7CCB6A, 4, () -> Ingredient.of(Tags.Items.SLIMEBALLS)),
    COPPER("copper", 0xD9824F, 4, () -> Ingredient.of(Tags.Items.INGOTS_COPPER)),
    AMETHYST("amethyst", 0xA77BDB, 4, () -> Ingredient.of(Tags.Items.GEMS_AMETHYST)),
    ENDER_PEARL("ender_pearl", 0x2E9B83, 2, () -> Ingredient.of(Tags.Items.ENDER_PEARLS)),
    GOLD("gold", 0xF2D14A, 4, () -> Ingredient.of(Tags.Items.INGOTS_GOLD)),
    REDSTONE("redstone", 0xD0281E, 4, () -> Ingredient.of(Tags.Items.DUSTS_REDSTONE)),
    DRIPSTONE("dripstone", 0x9E8066, 4, () -> Ingredient.of(Items.POINTED_DRIPSTONE)),
    /** Barrena: menos daño por tick, pero se queda mucho más tiempo clavada y en total hace más. */
    FLINT("flint", 0x55524E, 4, () -> Ingredient.of(Items.FLINT)),
    /** Mazazo: todo el daño de un solo golpe, sin quedarse clavada; pesada (más lenta y tarda más en volver a estar lista). */
    OBSIDIAN("obsidian", 0x2A1E3D, 4, () -> Ingredient.of(Tags.Items.OBSIDIAN));

    public static final String TAG = "rites_and_paths.sphere_upgrade";

    public final String id;
    /** Color (RGB) del anillo, la estela y el HUD. */
    public final int color;
    /** Unidades del material que pide la receta. */
    public final int count;
    private final Supplier<Ingredient> ingredient;

    SphereUpgrade(String id, int color, int count, Supplier<Ingredient> ingredient) {
        this.id = id;
        this.color = color;
        this.count = count;
        this.ingredient = ingredient;
    }

    public Ingredient ingredient() {
        return ingredient.get();
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

    public static SphereUpgrade byId(String id) {
        if (id == null || id.isEmpty()) return null;
        for (SphereUpgrade u : values()) if (u.id.equals(id.toLowerCase(Locale.ROOT))) return u;
        return null;
    }

    /** 0 = sin mejora; 1.. = mejora (para sincronizar la entidad en un byte). */
    public static SphereUpgrade byIndex(int i) {
        SphereUpgrade[] all = values();
        return i >= 1 && i <= all.length ? all[i - 1] : null;
    }

    public static int index(SphereUpgrade u) {
        return u == null ? 0 : u.ordinal() + 1;
    }

    /** La mejora de una bola (null si no tiene). */
    public static SphereUpgrade of(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? null : byId(tag.getString(TAG));
    }

    /** Pone (o quita, con null) la mejora en la bola. */
    public static void set(ItemStack stack, SphereUpgrade u) {
        if (u == null) {
            if (stack.getTag() != null) stack.getTag().remove(TAG);
        } else {
            stack.getOrCreateTag().putString(TAG, u.id);
        }
    }
}
