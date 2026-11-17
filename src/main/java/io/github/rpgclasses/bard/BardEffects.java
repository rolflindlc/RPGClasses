package io.github.rpgclasses.bard;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.registry.ModEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.UUID;

/**
 * Efectos de las canciones del Bardo. La potencia va en el amplificador: amplificador + 1 = puntos porcentuales
 * (Himno de Guerra +12 % → amplificador 11), así que vanilla se queda con el más fuerte cuando dos Bardos tocan la
 * misma canción (las auras no se apilan: cuenta la más alta). Balada de Reposo: décimas de vida por segundo.
 */
public final class BardEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, RpgClasses.MODID);

    private static final ResourceLocation SPEED = new ResourceLocation("minecraft", "generic.movement_speed");
    private static final ResourceLocation ATTACK_SPEED = new ResourceLocation("minecraft", "generic.attack_speed");
    private static final ResourceLocation MANA_REGEN = new ResourceLocation("irons_spellbooks", "mana_regen");

    /** Marcha: +1 % de velocidad y +0,5 % de velocidad de ataque por punto (Velocidad I y Prisa I con 20). */
    public static final RegistryObject<MobEffect> MARCH = EFFECTS.register("march", () -> new PercentEffect(MobEffectCategory.BENEFICIAL, 0x7CD3F5, "march",
            new Mod(SPEED, UUID.fromString("3d1c9b8e-55a2-4f0e-8d7b-1a2b3c4d5e01"), 0.01, AttributeModifier.Operation.MULTIPLY_TOTAL),
            new Mod(ATTACK_SPEED, UUID.fromString("3d1c9b8e-55a2-4f0e-8d7b-1a2b3c4d5e02"), 0.005, AttributeModifier.Operation.MULTIPLY_TOTAL)));
    public static final RegistryObject<MobEffect> WAR_HYMN = EFFECTS.register("war_hymn", () -> new ModEffects.StateEffect(MobEffectCategory.BENEFICIAL, 0xE04A3A));
    public static final RegistryObject<MobEffect> REST_BALLAD = EFFECTS.register("rest_ballad", BalladEffect::new);
    public static final RegistryObject<MobEffect> IRON_CHORALE = EFFECTS.register("iron_chorale", () -> new ModEffects.StateEffect(MobEffectCategory.BENEFICIAL, 0xA7AEB8));
    /** Cadencia de Maná: +1 % de regeneración de maná de Iron's por punto (sin Iron's no hace nada). */
    public static final RegistryObject<MobEffect> MANA_CADENCE = EFFECTS.register("mana_cadence", () -> new PercentEffect(MobEffectCategory.BENEFICIAL, 0x4E7BFF, "mana_cadence",
            new Mod(MANA_REGEN, UUID.fromString("3d1c9b8e-55a2-4f0e-8d7b-1a2b3c4d5e03"), 0.01, AttributeModifier.Operation.MULTIPLY_BASE)));
    public static final RegistryObject<MobEffect> REQUIEM_FERVOR = EFFECTS.register("requiem_fervor", () -> new ModEffects.StateEffect(MobEffectCategory.BENEFICIAL, 0x6A2C8E));
    /** Paso Compartido: +1 % de velocidad por punto. */
    public static final RegistryObject<MobEffect> SHARED_STEP = EFFECTS.register("shared_step", () -> new PercentEffect(MobEffectCategory.BENEFICIAL, 0xF2D16B, "shared_step",
            new Mod(SPEED, UUID.fromString("3d1c9b8e-55a2-4f0e-8d7b-1a2b3c4d5e04"), 0.01, AttributeModifier.Operation.MULTIPLY_TOTAL)));
    public static final RegistryObject<MobEffect> SCALE_WARD_FIRE = EFFECTS.register("scale_ward_fire", () -> new ModEffects.StateEffect(MobEffectCategory.BENEFICIAL, 0xD9502B));
    public static final RegistryObject<MobEffect> SCALE_WARD_ICE = EFFECTS.register("scale_ward_ice", () -> new ModEffects.StateEffect(MobEffectCategory.BENEFICIAL, 0x86C5F0));
    public static final RegistryObject<MobEffect> SCALE_WARD_LIGHTNING = EFFECTS.register("scale_ward_lightning", () -> new ModEffects.StateEffect(MobEffectCategory.BENEFICIAL, 0x9B6BE8));
    /** Baile Forzado: sin atacar, lanzar conjuros ni usar objetos; quieto en el sitio. */
    public static final RegistryObject<MobEffect> FORCED_DANCE = EFFECTS.register("forced_dance", () -> new ModEffects.StateEffect(MobEffectCategory.HARMFUL, 0xFF6FD8));
    /** Silencio (Disonancia): sin conjuros. */
    public static final RegistryObject<MobEffect> SILENCED = EFFECTS.register("silenced", () -> new ModEffects.StateEffect(MobEffectCategory.HARMFUL, 0x5A5A6E));
    /** Hechizado (Encantador): no ataca al Bardo ni a sus aliados y pelea con otros mobs. */
    public static final RegistryObject<MobEffect> CHARMED = EFFECTS.register("charmed", () -> new ModEffects.StateEffect(MobEffectCategory.HARMFUL, 0xE58BD4));

    private BardEffects() {}

    /** Puntos porcentuales del efecto (amplificador + 1), o 0 si no lo tiene. */
    public static int points(LivingEntity e, RegistryObject<MobEffect> effect) {
        MobEffectInstance i = e.getEffect(effect.get());
        return i == null ? 0 : i.getAmplifier() + 1;
    }

    /**
     * Aplica un efecto de aura: dura {@code refresh} ticks más la persistencia de Reverberación. Con Estela la
     * persistencia va con menos potencia: se añade antes una instancia débil y larga, que vanilla guarda oculta debajo
     * de la fuerte y corta y que toma el relevo al caducar esta.
     */
    public static void give(LivingEntity target, MobEffect effect, int points, int refresh, int linger, double lingerPotency, Entity source) {
        if (points <= 0) return;
        int amp = Math.min(254, points - 1);
        if (linger > 0 && lingerPotency < 1) {
            int weak = Math.max(0, (int) Math.round(points * lingerPotency) - 1);
            target.addEffect(new MobEffectInstance(effect, refresh + linger, weak, true, true, true), source);
            target.addEffect(new MobEffectInstance(effect, refresh, amp, true, true, true), source);
        } else {
            target.addEffect(new MobEffectInstance(effect, refresh + Math.max(0, linger), amp, true, true, true), source);
        }
    }

    record Mod(ResourceLocation attribute, UUID id, double perPoint, AttributeModifier.Operation operation) {}

    /** Efecto con modificadores de atributo proporcionales al amplificador. Los atributos se buscan al aplicarlo (Iron's es opcional). */
    static final class PercentEffect extends ModEffects.StateEffect {
        private final List<Mod> mods;
        private final String name;

        PercentEffect(MobEffectCategory category, int color, String name, Mod... mods) {
            super(category, color);
            this.mods = List.of(mods);
            // No empieza por "rpgclasses:": StatEngine no debe borrarlo al recalcular.
            this.name = "rpgclasses_effect:" + name;
        }

        @Override
        public void addAttributeModifiers(LivingEntity entity, AttributeMap attributes, int amplifier) {
            super.addAttributeModifiers(entity, attributes, amplifier);
            for (Mod m : mods) {
                AttributeInstance inst = instance(attributes, m);
                if (inst == null) continue;
                inst.removeModifier(m.id());
                inst.addTransientModifier(new AttributeModifier(m.id(), name, m.perPoint() * (amplifier + 1), m.operation()));
            }
        }

        @Override
        public void removeAttributeModifiers(LivingEntity entity, AttributeMap attributes, int amplifier) {
            super.removeAttributeModifiers(entity, attributes, amplifier);
            for (Mod m : mods) {
                AttributeInstance inst = instance(attributes, m);
                if (inst != null) inst.removeModifier(m.id());
            }
        }

        private static AttributeInstance instance(AttributeMap attributes, Mod m) {
            Attribute a = ForgeRegistries.ATTRIBUTES.getValue(m.attribute());
            return a == null ? null : attributes.getInstance(a);
        }
    }

    /** Balada de Reposo: cada segundo cura una décima de vida por punto. */
    static final class BalladEffect extends ModEffects.StateEffect {
        BalladEffect() {
            super(MobEffectCategory.BENEFICIAL, 0x7FE08A);
        }

        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            return duration % 20 == 0;
        }

        @Override
        public void applyEffectTick(LivingEntity entity, int amplifier) {
            if (entity.getHealth() < entity.getMaxHealth()) entity.heal(0.1f * (amplifier + 1));
        }
    }
}
