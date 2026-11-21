package io.github.rpgclasses.spin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Tipo de bola: tier + mejora (o ninguna). Es lo que elige la tecla de cambiar de bola y lo que se ve en el HUD:
 * en la bandolera, las bolas del mismo tier y la misma mejora son intercambiables.
 */
public record SphereKind(SphereMaterial tier, SphereUpgrade upgrade) {
    public static final SphereKind IRON = new SphereKind(SphereMaterial.IRON, null);

    public static SphereKind of(ItemStack stack) {
        return stack.getItem() instanceof SphereItem s ? new SphereKind(s.material, SphereUpgrade.of(stack)) : null;
    }

    /** {@code iron}, {@code diamond+copper}... */
    public String id() {
        return upgrade == null ? tier.id : tier.id + "+" + upgrade.id;
    }

    public static SphereKind byId(String id) {
        if (id == null || id.isEmpty()) return null;
        int plus = id.indexOf('+');
        SphereMaterial t = SphereMaterial.byId(plus < 0 ? id : id.substring(0, plus));
        if (t == null) return null;
        return new SphereKind(t, plus < 0 ? null : SphereUpgrade.byId(id.substring(plus + 1)));
    }

    /** Orden para la tecla de cambiar de bola: por tier y luego por mejora. */
    public int order() {
        return tier.ordinal() * 32 + SphereUpgrade.index(upgrade);
    }

    /** "Bola de diamante" o "Bola de diamante con cobre". */
    public Component name() {
        Component base = Component.translatable("item.rites_and_paths." + tier.itemName());
        return upgrade == null ? base
                : Component.translatable("item.rites_and_paths.sphere_upgraded", base, Component.translatable("upgrade.rites_and_paths." + upgrade.id));
    }

    public int color() {
        return upgrade != null ? upgrade.color : tier.color;
    }

    public float red() {
        return ((color() >> 16) & 0xFF) / 255f;
    }

    public float green() {
        return ((color() >> 8) & 0xFF) / 255f;
    }

    public float blue() {
        return (color() & 0xFF) / 255f;
    }
}
