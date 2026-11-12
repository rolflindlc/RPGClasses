package io.github.rpgclasses.server;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.content.MonkAbilities;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.SubclassDef;
import io.github.rpgclasses.core.progression.ProgressState;
import io.github.rpgclasses.core.stat.StatCalculator;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
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
        MechanicService.invalidate(player);
        if (data == null) return;
        ProgressState s = data.state;
        ClassDef classDef = DefinitionStore.SERVER.classDef(s.classId);
        SubclassDef subDef = classDef == null ? null : DefinitionStore.SERVER.subclassDef(s.subclassId);

        removeAll(player);
        io.github.rpgclasses.core.definition.Definitions.RaceDef race = race(s);

        // 1. Pasivas de atributo de ramas, subclase y raza (con topes y suelos globales ya aplicados).
        StatCalculator.Context ctx = new StatCalculator.Context(isWearingArmor(player), MonkAbilities.stanceFactors(player));
        for (Map.Entry<StatCalculator.Key, Double> e : StatCalculator.attributeTotals(s, classDef, subDef, race, ctx).entrySet()) {
            apply(player, new ResourceLocation(e.getKey().attribute()), "stat", operation(e.getKey().operation()), e.getValue());
        }
        if (classDef == null) {
            clampHealth(player);
            player.refreshDimensions();
            return;
        }

        // 2. Armadura de las piezas equipadas: Coraza la aumenta y el muro de Armadura la reduce.
        //    Es aditivo sobre lo que aportan las piezas, así que no toca la defensa desarmada ni las pociones.
        double[] cuirass = StatCalculator.equippedArmorBonus(s, classDef);
        double armorWall = StatCalculator.wall(s, classDef, subDef, race, Ids.ARMOR);
        double pieceArmor = equippedTotal(player, Attributes.ARMOR);
        double pieceToughness = equippedTotal(player, Attributes.ARMOR_TOUGHNESS);
        applyTo(player, Attributes.ARMOR, "equipped", AttributeModifier.Operation.ADDITION, pieceArmor * (cuirass[0] - armorWall));
        applyTo(player, Attributes.ARMOR_TOUGHNESS, "equipped", AttributeModifier.Operation.ADDITION, pieceToughness * (cuirass[1] - armorWall));

        // 3. Muro de Magia: multiplicador total sobre el poder de hechizo de Iron's (si está instalado).
        double magicWall = StatCalculator.wall(s, classDef, subDef, race, Ids.MAGIC);
        Attribute spellPower = ForgeRegistries.ATTRIBUTES.getValue(IRONS_SPELL_POWER);
        if (spellPower != null) {
            applyTo(player, spellPower, "wall", AttributeModifier.Operation.MULTIPLY_TOTAL, -magicWall);
        }
        // 4. Regla 14: muros fijos por escuela (sagrada −100 % salvo Paladín) y escuelas exentas del muro de
        //    Magia. Iron's multiplica poder general × poder de escuela, así que la exención compensa el muro
        //    general con ×1/(1−muro) sobre el poder de la escuela.
        for (Map.Entry<String, Double> e : classDef.schoolWalls().entrySet()) {
            apply(player, schoolPowerAttribute(e.getKey()), "school_wall", AttributeModifier.Operation.MULTIPLY_TOTAL, -e.getValue());
        }
        if (magicWall > 0 && magicWall < 1) {
            for (String school : classDef.magicWallExemptSchools()) {
                apply(player, schoolPowerAttribute(school), "wall_exempt", AttributeModifier.Operation.MULTIPLY_TOTAL,
                        magicWall / (1 - magicWall));
            }
        }

        // 5. Fluidez (Mago): con la rama llena, lanzar conjuros no ralentiza. Iron's mueve al jugador a
        //    0,2 + casting_movespeed − 1 mientras lanza; +0,8 lo deja a velocidad normal.
        ActiveMechanic noSlow = MechanicService.find(player, "cast_no_slow");
        if (noSlow != null && noSlow.level() >= noSlow.number("min_points", 10)) {
            apply(player, new ResourceLocation("irons_spellbooks", "casting_movespeed"), "cast_no_slow",
                    AttributeModifier.Operation.ADDITION, 0.8);
        }

        clampHealth(player);
        player.refreshDimensions(); // tamaño racial
    }

    /** Raza del jugador según el datapack (null si no tiene o ya no existe). */
    public static io.github.rpgclasses.core.definition.Definitions.RaceDef race(ProgressState s) {
        return io.github.rpgclasses.core.definition.RaceStore.SERVER.race(s.raceId);
    }

    /** Muro efectivo (0..1) del jugador en una disciplina; 0 si no tiene clase. */
    public static double wall(ServerPlayer player, String discipline) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return 0;
        ClassDef classDef = DefinitionStore.SERVER.classDef(data.state.classId);
        if (classDef == null) return 0;
        return StatCalculator.wall(data.state, classDef, DefinitionStore.SERVER.subclassDef(data.state.subclassId), race(data.state), discipline);
    }

    /** Muro fijo (0..1) del jugador en una escuela de Iron's ("irons_spellbooks:holy"); 0 si no tiene clase. */
    public static double schoolWall(ServerPlayer player, String schoolId) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return 0;
        ClassDef classDef = DefinitionStore.SERVER.classDef(data.state.classId);
        return classDef == null ? 0 : classDef.schoolWalls().getOrDefault(schoolId, 0.0);
    }

    /** "irons_spellbooks:holy" → "irons_spellbooks:holy_spell_power" (convención de Iron's para escuelas). */
    private static ResourceLocation schoolPowerAttribute(String schoolId) {
        ResourceLocation school = new ResourceLocation(schoolId);
        return new ResourceLocation(school.getNamespace(), school.getPath() + "_spell_power");
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
