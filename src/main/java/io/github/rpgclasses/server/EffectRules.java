package io.github.rpgclasses.server;

import io.github.rpgclasses.core.Ids;
import com.google.gson.JsonElement;
import io.github.rpgclasses.content.BannerAura;
import io.github.rpgclasses.content.MonkSubclasses;
import io.github.rpgclasses.content.BarbarianSubclasses;
import io.github.rpgclasses.content.PaladinSubclasses;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.registry.ModAttributes;
import io.github.rpgclasses.registry.ModEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Efectos negativos que recibe un jugador con clase:
 * <ul>
 *   <li>Rama Voluntad (Paladín): {@code debuff_resistance} reduce su duración (−50 % con 10 puntos).</li>
 *   <li>Perks de Voluntad: inmunidad a una lista de efectos (y a la quemadura en "contra el Daño");
 *       el resto dura un +50 % (se suma a la reducción de la rama: la duración neta queda igual).</li>
 * </ul>
 * La duración no se puede cambiar sobre la instancia, así que se rechaza y se vuelve a aplicar una copia escalada.
 */
public final class EffectRules {
    public static final String IMMUNITY = "effect_immunity";
    private static boolean reapplying;

    /** Efectos que otros mods registran como neutrales pero son negativos (data/rites_and_paths/tags/mob_effect/negative.json). */
    public static final TagKey<MobEffect> NEGATIVE_TAG = TagKey.create(Registries.MOB_EFFECT, new ResourceLocation(Ids.NS, "negative"));

    /** Efecto negativo: dañino o en el tag {@code rites_and_paths:negative}; nunca las marcas de estado del propio mod. */
    public static boolean isNegative(MobEffect effect) {
        if (effect instanceof ModEffects.StateEffect) return false;
        return effect.getCategory() == MobEffectCategory.HARMFUL
                || BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect).is(NEGATIVE_TAG);
    }

    @SubscribeEvent
    public void onApplicable(MobEffectEvent.Applicable event) {
        if (reapplying) return;
        // Mascotas e invocaciones aliadas: solo el Estandarte del Paladín acorta sus efectos negativos.
        if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player) && !event.getEntity().level().isClientSide()) {
            petBanner(event);
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        MobEffectInstance instance = event.getEffectInstance();
        MobEffect effect = instance.getEffect();
        // Las marcas del propio mod (Condena...) no son debuffs normales: su duración la controla quien las pone.
        if (!isNegative(effect)) return;

        // Baluarte Incorruptible: inmune a cualquier efecto negativo nuevo mientras dura.
        if (PaladinSubclasses.incorruptible(player)) {
            event.setResult(Event.Result.DENY);
            return;
        }
        ActiveMechanic immunity = MechanicService.find(player, IMMUNITY);
        if (immunity != null && isListed(immunity, effect)) {
            event.setResult(Event.Result.DENY);
            return;
        }
        // Manifiesto de Piedra (Guardián Trimundial): inmune a Veneno, Wither y Hambre.
        if (MonkSubclasses.stoneImmune(player, effect)) {
            event.setResult(Event.Result.DENY);
            return;
        }
        // Sangre Resistente (Corruptor de Plagas): con el 100 % es inmunidad.
        double blood = BarbarianSubclasses.resistantBlood(player, effect);
        if (blood >= 1 - 1.0E-6) {
            event.setResult(Event.Result.DENY);
            return;
        }
        if (instance.isInfiniteDuration() || instance.getDuration() <= 1) return;
        double factor = 1 - ModAttributes.value(player, ModAttributes.DEBUFF_RESISTANCE) - blood
                + (immunity == null ? 0 : immunity.number("other_duration", 0))
                + BannerAura.debuffDurationChange(player);
        if (Math.abs(factor - 1) < 1.0E-6) return;
        int duration = Math.max(1, (int) Math.round(instance.getDuration() * Math.max(0, factor)));
        event.setResult(Event.Result.DENY);
        reapplying = true;
        try {
            player.addEffect(new MobEffectInstance(effect, duration, instance.getAmplifier(), instance.isAmbient(),
                    instance.isVisible(), instance.showIcon()));
        } finally {
            reapplying = false;
        }
    }

    private static void petBanner(MobEffectEvent.Applicable event) {
        net.minecraft.world.entity.LivingEntity pet = event.getEntity();
        double change = BannerAura.debuffDurationChange(pet);
        MobEffectInstance instance = event.getEffectInstance();
        if (change == 0 || !isNegative(instance.getEffect()) || instance.isInfiniteDuration() || instance.getDuration() <= 1) return;
        int duration = Math.max(1, (int) Math.round(instance.getDuration() * Math.max(0, 1 + change)));
        event.setResult(Event.Result.DENY);
        reapplying = true;
        try {
            pet.addEffect(new MobEffectInstance(instance.getEffect(), duration, instance.getAmplifier(), instance.isAmbient(),
                    instance.isVisible(), instance.showIcon()));
        } finally {
            reapplying = false;
        }
    }

    /**
     * Sustituye un efecto por otra instancia (p. ej. con menos duración) sin que vuelvan a aplicarse las reglas de
     * duración de esta clase (Voluntad, Estandarte...). Lo usa la Purificación del Clérigo.
     */
    public static void replace(net.minecraft.world.entity.LivingEntity entity, MobEffectInstance instance) {
        boolean before = reapplying;
        reapplying = true;
        try {
            entity.removeEffect(instance.getEffect());
            if (instance.getDuration() > 0) entity.addEffect(instance);
        } finally {
            reapplying = before;
        }
    }

    /** true mientras se reaplica un efecto desde aquí (otras reglas de duración no deben tocarlo). */
    public static boolean reapplying() {
        return reapplying;
    }

    /** Voluntad contra el Daño: inmune a la quemadura (seguir ardiendo); el fuego y la lava directos sí dañan. */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        if (!event.getSource().is(DamageTypes.ON_FIRE)) return;
        if (MechanicService.number(player, IMMUNITY, "fire", 0) > 0) {
            event.setCanceled(true);
            player.clearFire();
        }
    }

    private static boolean isListed(ActiveMechanic immunity, MobEffect effect) {
        if (!immunity.params().has("effects")) return false;
        ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(effect);
        if (id == null) return false;
        for (JsonElement e : immunity.params().getAsJsonArray("effects")) {
            if (id.toString().equals(e.getAsString())) return true;
        }
        return false;
    }
}
