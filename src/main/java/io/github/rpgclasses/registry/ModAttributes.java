package io.github.rpgclasses.registry;

import io.github.rpgclasses.RpgClasses;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Atributos propios. Las pasivas de los JSON escriben en ellos y los manejadores de eventos los leen,
 * así que otros mods (o comandos /attribute) también pueden modificarlos.
 */
public final class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, RpgClasses.MODID);

    /** Bonus al multiplicador de crítico (x1,5 + este valor). */
    public static final RegistryObject<Attribute> CRIT_DAMAGE = register("crit_damage", 0, 0, 100);
    /** Puntos de probabilidad de crítico sumados a la base de la clase (perks de Brutalidad). */
    public static final RegistryObject<Attribute> CRIT_CHANCE = register("crit_chance", 0, -1, 1);
    /** Bonus extra al multiplicador de crítico en golpes desde invisibilidad (fase 4). */
    public static final RegistryObject<Attribute> BACKSTAB_CRIT_DAMAGE = register("backstab_crit_damage", 0, 0, 100);
    /** Altura de salto extra, como fracción (0,4 = +40%). */
    public static final RegistryObject<Attribute> JUMP_BONUS = register("jump_bonus", 0, 0, 10);
    /** Daño plano añadido a los golpes con la mano vacía. */
    public static final RegistryObject<Attribute> UNARMED_DAMAGE = register("unarmed_damage", 0, 0, 1024);
    public static final RegistryObject<Attribute> UNARMED_KNOCKBACK = register("unarmed_knockback", 0, 0, 100);
    /** Bonus aditivo de daño con arcos, ballestas y arrojadizas. */
    public static final RegistryObject<Attribute> BOW_DAMAGE = register("bow_damage", 0, 0, 100);
    public static final RegistryObject<Attribute> DRAW_SPEED = register("draw_speed", 0, -1, 100);
    public static final RegistryObject<Attribute> PROJECTILE_VELOCITY = register("projectile_velocity", 0, -0.9, 100);
    public static final RegistryObject<Attribute> ZOOM = register("zoom", 0, 0, 1);
    /** Cambio en la distancia a la que los mobs detectan al jugador (-0,5 = la mitad). */
    public static final RegistryObject<Attribute> MOB_DETECTION = register("mob_detection", 0, -1, 10);
    public static final RegistryObject<Attribute> DEBUFF_RESISTANCE = register("debuff_resistance", 0, 0, 1);
    public static final RegistryObject<Attribute> KNOCKBACK_DEALT = register("knockback_dealt", 0, 0, 100);
    public static final RegistryObject<Attribute> HEALING_RECEIVED = register("healing_received", 0, -1, 100);
    public static final RegistryObject<Attribute> SNEAK_SPEED = register("sneak_speed", 0, 0, 10);
    public static final RegistryObject<Attribute> SPELL_SIZE = register("spell_size", 0, 0, 10);

    private static RegistryObject<Attribute> register(String name, double def, double min, double max) {
        return ATTRIBUTES.register(name,
                () -> new RangedAttribute("attribute." + RpgClasses.MODID + "." + name, def, min, max).setSyncable(true));
    }

    /** Añade todos los atributos del mod a los jugadores. */
    public static void onAttributeModification(EntityAttributeModificationEvent event) {
        for (RegistryObject<Attribute> attribute : ATTRIBUTES.getEntries()) {
            event.add(EntityType.PLAYER, attribute.get());
        }
    }

    /** Valor del atributo, o 0 si la entidad no lo tiene. */
    public static double value(LivingEntity entity, RegistryObject<Attribute> attribute) {
        AttributeInstance instance = entity.getAttribute(attribute.get());
        return instance == null ? 0 : instance.getValue();
    }

    private ModAttributes() {}
}
