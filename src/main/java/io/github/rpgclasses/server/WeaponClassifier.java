package io.github.rpgclasses.server;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.core.Ids;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.common.ToolActions;

/**
 * Decide la disciplina de un objeto. Orden de prioridad:
 * 1) tag de objeto rpgclasses:discipline/&lt;nombre&gt; (decisión del creador del pack),
 * 2) categoría de Epic Fight (fase 4, a través de su puente),
 * 3) tipo vanilla,
 * 4) sin clasificar: cuenta como arma ligera.
 */
public final class WeaponClassifier {
    public static final TagKey<Item> TAG_HEAVY = ItemTags.create(RpgClasses.id("discipline/heavy"));
    public static final TagKey<Item> TAG_LIGHT = ItemTags.create(RpgClasses.id("discipline/light"));
    public static final TagKey<Item> TAG_BOW = ItemTags.create(RpgClasses.id("discipline/bow"));
    public static final TagKey<Item> TAG_SHIELD = ItemTags.create(RpgClasses.id("discipline/shield"));

    /** @param discipline id de disciplina, o null si es la mano vacía (desarmado). */
    public record Classification(String discipline, String rule) {}

    public static Classification classify(ItemStack stack) {
        if (stack.isEmpty()) return new Classification(null, "mano vacía (desarmado)");
        if (stack.is(TAG_HEAVY)) return new Classification(Ids.HEAVY, "tag discipline/heavy");
        if (stack.is(TAG_LIGHT)) return new Classification(Ids.LIGHT, "tag discipline/light");
        if (stack.is(TAG_BOW)) return new Classification(Ids.BOW, "tag discipline/bow");
        if (stack.is(TAG_SHIELD)) return new Classification(Ids.SHIELD, "tag discipline/shield");

        Item item = stack.getItem();
        if (item instanceof AxeItem) return new Classification(Ids.HEAVY, "tipo vanilla: hacha");
        if (item instanceof SwordItem) return new Classification(Ids.LIGHT, "tipo vanilla: espada");
        if (item instanceof TridentItem) return new Classification(Ids.LIGHT, "tipo vanilla: tridente (cuerpo a cuerpo)");
        if (item instanceof ProjectileWeaponItem) return new Classification(Ids.BOW, "tipo vanilla: arco o ballesta");
        if (stack.canPerformAction(ToolActions.SHIELD_BLOCK)) return new Classification(Ids.SHIELD, "bloquea como escudo");
        return new Classification(Ids.LIGHT, "sin clasificar: cuenta como ligera");
    }

    private WeaponClassifier() {}
}
