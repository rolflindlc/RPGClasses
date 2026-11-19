package io.github.rpgclasses.registry;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.server.AbilityService;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.UUID;

/**
 * Estados de las habilidades como efectos de poción: dan icono, caducidad y sincronización gratis.
 * Se declaran sin objeto curativo para que un cubo de leche no los quite.
 */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, RpgClasses.MODID);

    /** Baluarte: reducción de todo el daño recibido (el porcentaje lo aplica DamagePipeline). */
    public static final RegistryObject<MobEffect> BULWARK =
            EFFECTS.register("bulwark", () -> new StateEffect(MobEffectCategory.BENEFICIAL, 0xF0D25A));
    /** Baluarte de Embestida: carrera forzada (cliente) y daño de contacto (content.PaladinSubclasses). */
    public static final RegistryObject<MobEffect> CHARGING_BULWARK =
            EFFECTS.register("charging_bulwark", () -> new StateEffect(MobEffectCategory.BENEFICIAL, 0xB4643C));
    /** Última Furia Inmortal (Berserker Suicida): solo icono; la lógica está en content.BarbarianSubclasses. */
    public static final RegistryObject<MobEffect> IMMORTAL_FURY =
            EFFECTS.register("immortal_fury", () -> new StateEffect(MobEffectCategory.BENEFICIAL, 0x8C0A0A));
    /** Plaga (Corruptor de Plagas): el daño periódico lo hace content.BarbarianSubclasses; la leche la cura. */
    /** Corrosión (Dracónido Ácido): −5 % de armadura y dureza por nivel del efecto. */
    public static final RegistryObject<MobEffect> CORROSION = EFFECTS.register("corrosion", () ->
            new CurableEffect(MobEffectCategory.HARMFUL, 0x9ACD32)
                    .addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR, "4b1c9a2e-7d3f-4a51-8e6b-0c2d9f1a7e31", -0.05,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL)
                    .addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS, "4b1c9a2e-7d3f-4a51-8e6b-0c2d9f1a7e32", -0.05,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
    public static final RegistryObject<MobEffect> PLAGUE =
            EFFECTS.register("plague", () -> new CurableEffect(MobEffectCategory.HARMFUL, 0x6B7A2A));
    /** Reducción de daño de Provocación. */
    public static final RegistryObject<MobEffect> TAUNT_GUARD =
            EFFECTS.register("taunt_guard", () -> new StateEffect(MobEffectCategory.BENEFICIAL, 0x78AAF0));
    /** Furia Berserker: velocidad de ataque aquí; daño infligido y recibido en DamagePipeline. */
    public static final RegistryObject<MobEffect> BERSERKER_RAGE =
            EFFECTS.register("berserker_rage", BerserkerEffect::new);

    /** Toxina del Pícaro: el daño periódico lo hace {@code content.Toxins}; la leche la cura. */
    public static final RegistryObject<MobEffect> TOXIN =
            EFFECTS.register("toxin", () -> new CurableEffect(MobEffectCategory.HARMFUL, 0x5AA02D));
    /** Aguante (perk de Curtido): reducción del daño recibido. */
    public static final RegistryObject<MobEffect> ENDURANCE =
            EFFECTS.register("endurance", () -> new StateEffect(MobEffectCategory.BENEFICIAL, 0x8C6E50));
    /** Condena (perk de Castigo): el objetivo recibe más daño del Paladín que lo marcó. */
    public static final RegistryObject<MobEffect> CONDEMNED =
            EFFECTS.register("condemned", () -> new StateEffect(MobEffectCategory.HARMFUL, 0xFFF0A0));

    /** Tranquilidad (poción): resta un 20 % por nivel al nivel de clase del jugador al calcular la amenaza T. Lo lee difficulty.ThreatService. */
    public static final RegistryObject<MobEffect> TRANQUILITY =
            EFFECTS.register("tranquility", () -> new CurableEffect(MobEffectCategory.BENEFICIAL, 0x7FC8E8));
    /** Desafío (poción): suma un 20 % por nivel al nivel de clase del jugador al calcular la amenaza T. Lo lee difficulty.ThreatService. */
    public static final RegistryObject<MobEffect> CHALLENGE =
            EFFECTS.register("challenge", () -> new CurableEffect(MobEffectCategory.HARMFUL, 0xD2492B));

    /** Icono del aura de Estandarte (los valores los aplica content.BannerAura). */
    public static final RegistryObject<MobEffect> BANNER =
            EFFECTS.register("banner", () -> new StateEffect(MobEffectCategory.BENEFICIAL, 0xE6C35A));

    /** Marca de Cazador (perk de Alcance): el marcado recibe más daño de todas las fuentes. */
    public static final RegistryObject<MobEffect> HUNTERS_MARK =
            EFFECTS.register("hunters_mark", () -> new StateEffect(MobEffectCategory.HARMFUL, 0xC83C28));
    /** Retirada (rama extra del Ranger): +25 % de velocidad de movimiento. */
    public static final RegistryObject<MobEffect> RETREAT =
            EFFECTS.register("retreat", () -> new StateEffect(MobEffectCategory.BENEFICIAL, 0x7CC8A0)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, "3c9b2f4e-81d7-4a5b-9f0e-2d6c1a7b8e43", 0.25,
                            AttributeModifier.Operation.MULTIPLY_TOTAL));
    /** Paso Arcano (Mago, Fluidez): +30 % de velocidad tras lanzar un conjuro. */
    public static final RegistryObject<MobEffect> ARCANE_STEP =
            EFFECTS.register("arcane_step", () -> new StateEffect(MobEffectCategory.BENEFICIAL, 0x9F7FE0)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, "b51e7d2a-4c3f-4e8a-9a61-0f2d7c9e5a18", 0.30,
                            AttributeModifier.Operation.MULTIPLY_TOTAL));

    /** Tumbado (Milagro del Clérigo): solo icono; la lógica está en cleric.Miracle. */
    public static final RegistryObject<MobEffect> DOWNED =
            EFFECTS.register("downed", () -> new StateEffect(MobEffectCategory.NEUTRAL, 0xE8D9A0));
    /** Forma Angelical (Ángel): solo icono; el vuelo lo controla cleric.ClericSubclasses. */
    public static final RegistryObject<MobEffect> ANGELIC_FORM =
            EFFECTS.register("angelic_form", () -> new StateEffect(MobEffectCategory.BENEFICIAL, 0xFFF4C2));

    /** Efecto normal (curable con leche) sin lógica propia. */
    public static class CurableEffect extends MobEffect {
        public CurableEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }

    public static class StateEffect extends MobEffect {
        public StateEffect(MobEffectCategory category, int color) {
            super(category, color);
        }

        @Override
        public List<ItemStack> getCurativeItems() {
            return List.of();
        }
    }

    private static final class BerserkerEffect extends StateEffect {
        private static final UUID ATTACK_SPEED_ID = UUID.fromString("5b0f5b1e-6d0c-4d38-9d5c-7f6a0c2b9e11");
        /** El nombre NO empieza por "rites_and_paths:" a propósito: StatEngine no debe borrarlo al recalcular. */
        private static final String NAME = "rites_and_paths_ability:berserker_rage";

        BerserkerEffect() {
            super(MobEffectCategory.BENEFICIAL, 0xDC3C32);
        }

        @Override
        public void addAttributeModifiers(LivingEntity entity, AttributeMap attributes, int amplifier) {
            super.addAttributeModifiers(entity, attributes, amplifier);
            AttributeInstance speed = attributes.getInstance(Attributes.ATTACK_SPEED);
            if (speed == null) return;
            double bonus = AbilityService.number(AbilityService.params(RpgClasses.MODID + ":berserker_rage"), "attack_speed", 0.3);
            speed.removeModifier(ATTACK_SPEED_ID);
            speed.addTransientModifier(new AttributeModifier(ATTACK_SPEED_ID, NAME, bonus, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }

        @Override
        public void removeAttributeModifiers(LivingEntity entity, AttributeMap attributes, int amplifier) {
            super.removeAttributeModifiers(entity, attributes, amplifier);
            AttributeInstance speed = attributes.getInstance(Attributes.ATTACK_SPEED);
            if (speed != null) speed.removeModifier(ATTACK_SPEED_ID);
        }
    }

    private ModEffects() {}
}
