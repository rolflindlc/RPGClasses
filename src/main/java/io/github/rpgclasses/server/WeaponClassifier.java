package io.github.rpgclasses.server;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.core.Ids;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.common.ToolActions;

/**
 * Decide la disciplina de un objeto. Orden de prioridad:
 * 1) tag de objeto rites_and_paths:discipline/&lt;nombre&gt; (decisión del creador del pack),
 * 2) categoría de Epic Fight (fase 4, a través de su puente),
 * 3) arma de fuego de NTGL (Create: Gunsmithing; adenda del Ingeniero) y tipo vanilla,
 * 4) sin clasificar: si aporta daño de ataque cuenta como arma ligera; si no (una bola de nieve,
 *    una armadura de caballo), no es un arma: ni muro ni bonus de desarmado.
 */
public final class WeaponClassifier {
    public static final TagKey<Item> TAG_HEAVY = ItemTags.create(RpgClasses.id("discipline/heavy"));
    public static final TagKey<Item> TAG_LIGHT = ItemTags.create(RpgClasses.id("discipline/light"));
    public static final TagKey<Item> TAG_BOW = ItemTags.create(RpgClasses.id("discipline/bow"));
    public static final TagKey<Item> TAG_SHIELD = ItemTags.create(RpgClasses.id("discipline/shield"));
    public static final TagKey<Item> TAG_FIREARM = ItemTags.create(RpgClasses.id("discipline/firearm"));

    /**
     * @param discipline id de disciplina, o null si no es un arma.
     * @param unarmed true solo con la mano vacía: es lo único que recibe los bonus de desarmado.
     */
    /**
     * @param martial arma marcial (tag rites_and_paths:martial_weapons): ligera para todas las clases, desarmado para el Monje.
     */
    public record Classification(String discipline, String rule, boolean unarmed, boolean martial) {
        Classification(String discipline, String rule) {
            this(discipline, rule, false, false);
        }

        Classification(String discipline, String rule, boolean unarmed) {
            this(discipline, rule, unarmed, false);
        }
    }

    public static Classification classify(ItemStack stack) {
        if (stack.isEmpty()) return new Classification(null, "mano vacía (desarmado)", true);
        if (stack.is(io.github.rpgclasses.gear.GearRules.MARTIAL)) {
            return new Classification(Ids.LIGHT, "arma marcial (desarmado para el Monje)", false, true);
        }
        if (stack.is(TAG_HEAVY)) return new Classification(Ids.HEAVY, "tag discipline/heavy");
        if (stack.is(TAG_LIGHT)) return new Classification(Ids.LIGHT, "tag discipline/light");
        if (stack.is(TAG_BOW)) return new Classification(Ids.BOW, "tag discipline/bow");
        if (stack.is(TAG_SHIELD)) return new Classification(Ids.SHIELD, "tag discipline/shield");
        if (stack.is(TAG_FIREARM)) return new Classification(Ids.FIREARM, "tag discipline/firearm");
        // Adenda del Ingeniero: toda arma de NTGL es Pólvora (también sus golpes cuerpo a cuerpo: bayoneta, culata).
        // El martillo neumático de Gunsmithing va en discipline/heavy, que se mira antes.
        if (io.github.rpgclasses.compat.ntgl.NtglCompat.isFirearm(stack)) {
            return new Classification(Ids.FIREARM, "arma de fuego de NTGL");
        }

        Item item = stack.getItem();
        if (item instanceof AxeItem) return new Classification(Ids.HEAVY, "tipo vanilla: hacha");
        if (item instanceof SwordItem) return new Classification(Ids.LIGHT, "tipo vanilla: espada");
        if (item instanceof TridentItem) return new Classification(Ids.LIGHT, "tipo vanilla: tridente (cuerpo a cuerpo)");
        if (item instanceof ProjectileWeaponItem) return new Classification(Ids.BOW, "tipo vanilla: arco o ballesta");
        if (stack.canPerformAction(ToolActions.SHIELD_BLOCK)) return new Classification(Ids.SHIELD, "bloquea como escudo");
        if (stack.getAttributeModifiers(EquipmentSlot.MAINHAND).containsKey(Attributes.ATTACK_DAMAGE)) {
            return new Classification(Ids.LIGHT, "sin clasificar con daño de ataque: cuenta como ligera");
        }
        return new Classification(null, "no es un arma: sin muro y sin bonus de desarmado");
    }

    private WeaponClassifier() {}
}
