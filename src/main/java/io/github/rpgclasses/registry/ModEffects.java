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
        /** El nombre NO empieza por "rpgclasses:" a propósito: StatEngine no debe borrarlo al recalcular. */
        private static final String NAME = "rpgclasses_ability:berserker_rage";

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
