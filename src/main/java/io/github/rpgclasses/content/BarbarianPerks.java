package io.github.rpgclasses.content;

import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.registry.ModEffects;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.MechanicService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ramas extra y perks del Bárbaro (rev. 2.7c): Curtido (+ Carnívoro / Aguante), perks de Furia y
 * los de Estampida (la parte de Embestida está en CombatAbilities). Lo que toca al daño lo aplica DamagePipeline.
 */
public final class BarbarianPerks {
    public static final String HARDENED = "hardened";
    public static final String CARNIVORE = "carnivore";
    public static final String ENDURANCE = "endurance";
    public static final String FURY_PERK = "fury_perk";
    public static final String CHARGE_UPGRADE = "charge_upgrade";
    public static final String UNSTOPPABLE_CHARGE = "unstoppable_charge";
    public static final String CRUSHING_CHARGE = "crushing_charge";
    private static final String ENDURANCE_COOLDOWN = "rpgclasses:endurance";

    /** Agotamiento (hambre) del tic anterior, para descontar una parte del que se acumula. */
    private static final Map<UUID, Float> LAST_EXHAUSTION = new HashMap<>();

    /** Curtido: reducción del daño de caída, fuego y explosiones que recibe. */
    public static double hazardFactor(ServerPlayer victim, DamageSource source) {
        if (!source.is(DamageTypeTags.IS_FALL) && !source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypeTags.IS_EXPLOSION)) return 1;
        return 1 - MechanicService.number(victim, HARDENED, "hazard_reduction", 0);
    }

    /** Perks de Furia: bonus de daño (positivo o negativo) por la vida perdida, sumado al de la pasiva. */
    public static double furyPerkBonus(ServerPlayer player) {
        ActiveMechanic m = MechanicService.find(player, FURY_PERK);
        return m == null ? 0 : m.number("damage_per_missing_pct", 0) * missingPct(player);
    }

    /** Aguante: reducción del daño recibido mientras dura. */
    public static double enduranceFactor(ServerPlayer victim) {
        if (!victim.hasEffect(ModEffects.ENDURANCE.get())) return 1;
        return 1 - MechanicService.number(victim, ENDURANCE, "damage_reduction", 0.2);
    }

    private static double missingPct(ServerPlayer player) {
        return Math.max(0, Math.min(1, 1 - player.getHealth() / player.getMaxHealth())) * 100;
    }

    /** Furia Templada: +0,5 % de curación por cada 1 % de vida perdida, de todas las fuentes. */
    @SubscribeEvent
    public void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        ActiveMechanic m = MechanicService.find(player, FURY_PERK);
        if (m == null) return;
        double bonus = m.number("healing_per_missing_pct", 0) * missingPct(player);
        if (bonus != 0) event.setAmount((float) Math.max(0, event.getAmount() * (1 + bonus)));
    }

    /** Aguante: al bajar del umbral de vida (y sobrevivir), reducción de daño durante unos segundos. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || event.getAmount() <= 0) return;
        ActiveMechanic m = MechanicService.find(player, ENDURANCE);
        if (m == null) return;
        float threshold = (float) (player.getMaxHealth() * m.number("health_threshold", 0.3));
        float after = player.getHealth() - event.getAmount();
        if (player.getHealth() < threshold || after >= threshold || after <= 0) return;
        if (AbilityService.remainingTicks(player, ENDURANCE_COOLDOWN) > 0) return;
        player.addEffect(new MobEffectInstance(ModEffects.ENDURANCE.get(), (int) Math.round(m.number("duration_seconds", 5) * 20), 0));
        AbilityService.setCooldown(player, ENDURANCE_COOLDOWN, (int) Math.round(m.number("icd_seconds", 30) * 20));
    }

    /** Carnívoro (rev. 2.7f): solo se puede comer carne. */
    @SubscribeEvent
    public void onStartEating(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        FoodProperties food = event.getItem().getFoodProperties(player);
        if (food == null || food.isMeat() || !MechanicService.has(player, CARNIVORE)) return;
        event.setCanceled(true);
        player.stopUsingItem();
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.rpgclasses.carnivore"), true);
    }

    /** Carnívoro: la carne da más saturación. */
    @SubscribeEvent
    public void onEat(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        ActiveMechanic m = MechanicService.find(player, CARNIVORE);
        if (m == null) return;
        FoodProperties food = event.getItem().getFoodProperties(player);
        if (food == null) return;
        float saturation = food.getNutrition() * food.getSaturationModifier() * 2;
        CompoundTag tag = foodTag(player.getFoodData());
        int level = tag.getInt("foodLevel");
        float currentSaturation = tag.getFloat("foodSaturationLevel");
        if (food.isMeat()) {
            float extra = (float) (saturation * m.number("meat_saturation", 1.0));
            tag.putFloat("foodSaturationLevel", Math.min(level, currentSaturation + extra));
        } else {
            double change = m.number("other_food", -0.3); // negativo
            int newLevel = Math.max(0, level + (int) Math.round(food.getNutrition() * change));
            tag.putInt("foodLevel", newLevel);
            tag.putFloat("foodSaturationLevel", (float) Math.max(0, Math.min(newLevel, currentSaturation + saturation * change)));
        }
        player.getFoodData().readAdditionalSaveData(tag);
    }

    /** Curtido: se descuenta una fracción del agotamiento (hambre) que el jugador acumula cada tic. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        double reduction = MechanicService.number(player, HARDENED, "hunger_reduction", 0);
        if (reduction <= 0) {
            LAST_EXHAUSTION.remove(player.getUUID());
            return;
        }
        CompoundTag tag = foodTag(player.getFoodData());
        float now = tag.getFloat("foodExhaustionLevel");
        Float last = LAST_EXHAUSTION.get(player.getUUID());
        if (last != null) {
            // Al pasar de 4 vanilla resta 4 y gasta saturación/comida: se recupera lo acumulado ese tic.
            float gained = now >= last ? now - last : now + 4 - last;
            if (gained > 0) {
                now = (float) Math.max(0, now - gained * Math.min(1, reduction));
                tag.putFloat("foodExhaustionLevel", now);
                player.getFoodData().readAdditionalSaveData(tag);
            }
        }
        LAST_EXHAUSTION.put(player.getUUID(), now);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_EXHAUSTION.remove(event.getEntity().getUUID());
    }

    /** FoodData no tiene setters de servidor para saturación y agotamiento: se edita por NBT. */
    private static CompoundTag foodTag(FoodData food) {
        CompoundTag tag = new CompoundTag();
        food.addAdditionalSaveData(tag);
        return tag;
    }
}
