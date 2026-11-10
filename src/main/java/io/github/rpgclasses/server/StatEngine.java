package io.github.rpgclasses.server;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.SubclassDef;
import io.github.rpgclasses.core.progression.ProgressState;
import io.github.rpgclasses.core.stat.StatCalculator;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Traduce el progreso del jugador a modificadores de atributo. {@link #recompute} es idempotente:
 * borra todos los modificadores del mod y los vuelve a aplicar desde cero. Son transitorios
 * (no se guardan), así que no pueden duplicarse ni quedar huérfanos.
 */
public final class StatEngine {
    /** Prefijo del nombre de todos los modificadores del mod; se usa para encontrarlos y borrarlos. */
    public static final String MODIFIER_PREFIX = RpgClasses.MODID + ":";
    private static final EquipmentSlot[] ARMOR_SLOTS =
            {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final ResourceLocation IRONS_SPELL_POWER = new ResourceLocation("irons_spellbooks", "spell_power");
    private static final Set<String> WARNED_ATTRIBUTES = new HashSet<>();

    /** Recalcula las estadísticas y envía el progreso al cliente. Llamar tras cualquier cambio de progreso. */
    public static void refresh(ServerPlayer player) {
        recompute(player);
        Net.syncPlayer(player);
    }

    public static void recompute(ServerPlayer player) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return;
        ProgressState s = data.state;
        ClassDef classDef = DefinitionStore.SERVER.classDef(s.classId);
        SubclassDef subDef = classDef == null ? null : DefinitionStore.SERVER.subclassDef(s.subclassId);

        removeAll(player);
        if (classDef == null) {
            clampHealth(player);
            return;
        }

        // 1. Pasivas de atributo de ramas y subclase (con topes globales ya aplicados).
        StatCalculator.Context ctx = new StatCalculator.Context(isWearingArmor(player));
        for (Map.Entry<StatCalculator.Key, Double> e : StatCalculator.attributeTotals(s, classDef, subDef, ctx).entrySet()) {
            apply(player, new ResourceLocation(e.getKey().attribute()), "stat", operation(e.getKey().operation()), e.getValue());
        }

        // 2. Armadura de las piezas equipadas: Coraza la aumenta y el muro de Armadura la reduce.
        //    Es aditivo sobre lo que aportan las piezas, así que no toca la defensa desarmada ni las pociones.
        double[] cuirass = StatCalculator.equippedArmorBonus(s, classDef);
        double armorWall = StatCalculator.wall(s, classDef, subDef, Ids.ARMOR);
        double pieceArmor = equippedTotal(player, Attributes.ARMOR);
        double pieceToughness = equippedTotal(player, Attributes.ARMOR_TOUGHNESS);
        applyTo(player, Attributes.ARMOR, "equipped", AttributeModifier.Operation.ADDITION, pieceArmor * (cuirass[0] - armorWall));
        applyTo(player, Attributes.ARMOR_TOUGHNESS, "equipped", AttributeModifier.Operation.ADDITION, pieceToughness * (cuirass[1] - armorWall));

        // 3. Muro de Magia: multiplicador total sobre el poder de hechizo de Iron's (si está instalado).
        double magicWall = StatCalculator.wall(s, classDef, subDef, Ids.MAGIC);
        Attribute spellPower = ForgeRegistries.ATTRIBUTES.getValue(IRONS_SPELL_POWER);
        if (spellPower != null) {
            applyTo(player, spellPower, "wall", AttributeModifier.Operation.MULTIPLY_TOTAL, -magicWall);
        }

        clampHealth(player);
    }

    /** "Sin armadura" = ninguna pieza en las ranuras de armadura que aporte armadura (élitros o calabazas no cuentan). */
    public static boolean isWearingArmor(ServerPlayer player) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.getAttributeModifiers(slot).containsKey(Attributes.ARMOR)) return true;
        }
        return false;
    }

    /** Lista legible de los modificadores del mod activos, para /clases debug stats. */
    public static List<String> describe(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<Attribute>, Attribute> e : ForgeRegistries.ATTRIBUTES.getEntries()) {
            AttributeInstance instance = player.getAttribute(e.getValue());
            if (instance == null) continue;
            for (AttributeModifier m : instance.getModifiers()) {
                if (m.getName().startsWith(MODIFIER_PREFIX)) {
                    out.add(e.getKey().location() + " " + m.getOperation().name().toLowerCase(java.util.Locale.ROOT)
                            + " " + String.format(java.util.Locale.ROOT, "%+.3f", m.getAmount())
                            + " -> " + String.format(java.util.Locale.ROOT, "%.3f", instance.getValue()));
                }
            }
        }
        return out;
    }

    private static void removeAll(ServerPlayer player) {
        for (Attribute attribute : ForgeRegistries.ATTRIBUTES.getValues()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) continue;
            for (AttributeModifier m : List.copyOf(instance.getModifiers())) {
                if (m.getName().startsWith(MODIFIER_PREFIX)) instance.removeModifier(m.getId());
            }
        }
    }

    private static void apply(ServerPlayer player, ResourceLocation attributeId, String source,
                              AttributeModifier.Operation op, double amount) {
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(attributeId);
        if (attribute == null) {
            // Normal si el mod que lo aporta no está instalado (p. ej. Iron's Spells): se avisa una sola vez.
            if (WARNED_ATTRIBUTES.add(attributeId.toString())) {
                RpgClasses.LOGGER.warn("[rpgclasses] Atributo '{}' no registrado; se ignoran sus modificadores", attributeId);
            }
            return;
        }
        applyTo(player, attribute, source, op, amount);
    }

    private static void applyTo(ServerPlayer player, Attribute attribute, String source,
                                AttributeModifier.Operation op, double amount) {
        if (amount == 0 || !Double.isFinite(amount)) return;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        String name = MODIFIER_PREFIX + source + ":" + op.name();
        UUID id = UUID.nameUUIDFromBytes((name + "|" + ForgeRegistries.ATTRIBUTES.getKey(attribute)).getBytes(StandardCharsets.UTF_8));
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }

    private static AttributeModifier.Operation operation(String name) {
        return switch (name) {
            case "multiply_base" -> AttributeModifier.Operation.MULTIPLY_BASE;
            case "multiply_total" -> AttributeModifier.Operation.MULTIPLY_TOTAL;
            default -> AttributeModifier.Operation.ADDITION;
        };
    }

    private static double equippedTotal(ServerPlayer player, Attribute attribute) {
        double total = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            for (AttributeModifier m : stack.getAttributeModifiers(slot).get(attribute)) {
                if (m.getOperation() == AttributeModifier.Operation.ADDITION) total += m.getAmount();
            }
        }
        return total;
    }

    private static void clampHealth(ServerPlayer player) {
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    private StatEngine() {}
}
