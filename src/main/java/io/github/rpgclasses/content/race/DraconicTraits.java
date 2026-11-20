package io.github.rpgclasses.content.race;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.content.Schools;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.server.DebugLog;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dracónido (adenda 0.7): resistencia a su elemento, neutralidad con los dragones de su elemento (Ice and Fire),
 * inmunidades de las subrazas Venenoso y Ácido y el aire largo del Dracónido de Agua. El aliento va aparte.
 */
public final class DraconicTraits {
    private static final Map<UUID, Set<UUID>> PROVOKED = io.github.rpgclasses.server.SessionState.track(new HashMap<>()); // dragón -> jugadores que lo provocaron
    private static final Map<UUID, Integer> LAST_AIR = io.github.rpgclasses.server.SessionState.track(new HashMap<>());

    private static TagKey<DamageType> elementTag(String element) {
        return TagKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("element/" + element));
    }

    private static TagKey<EntityType<?>> dragonTag(String element) {
        return TagKey.create(Registries.ENTITY_TYPE, RpgClasses.id("dragons/" + element));
    }

    /** ¿Es este daño del elemento? Etiqueta del datapack + reglas vanilla + escuelas de Iron's / T.O Magic 'n Extras. */
    public static boolean isElement(DamageSource source, String element, LivingEntity victim) {
        if (source.is(elementTag(element))) return true;
        Schools.School school = source.typeHolder().unwrapKey().map(k -> Schools.ofDamageType(k.location())).orElse(null);
        return switch (element) {
            case "fire" -> source.is(DamageTypeTags.IS_FIRE) || school == Schools.School.FIRE;
            case "ice" -> source.is(DamageTypeTags.IS_FREEZING) || school == Schools.School.ICE;
            case "lightning" -> source.is(DamageTypeTags.IS_LIGHTNING) || school == Schools.School.LIGHTNING;
            // El efecto Veneno hace daño mágico genérico: se reconoce por tenerlo activo.
            case "poison" -> victim.hasEffect(MobEffects.POISON) && source.getEntity() == null && source.getDirectEntity() == null
                    && source.typeHolder().unwrapKey().map(k -> k.location().equals(new ResourceLocation("minecraft", "magic"))).orElse(false);
            case "water" -> source.is(DamageTypeTags.IS_DROWNING) || school == Schools.School.AQUA;
            default -> false;
        };
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        ActiveMechanic resist = Races.find(p, "race_element_resist");
        if (resist == null) return;
        String element = resist.text("element", "");
        if (element.isEmpty() || !isElement(event.getSource(), element, p)) return;
        double reduction = Math.min(resist.number("cap", 0.8), resist.number("reduction", 0));
        event.setAmount((float) (event.getAmount() * (1 - Math.max(0, reduction))));
    }

    // ------------------------------------------------------------------ dragones de su elemento

    private static String kinElement(Player p) {
        ActiveMechanic kin = Races.find(p, "race_dragon_kin");
        return kin == null ? null : kin.text("element", null);
    }

    private static boolean isWildDragonOf(LivingEntity e, String element) {
        if (!e.getType().is(dragonTag(element))) return false;
        return !(e instanceof TamableAnimal t && t.isTame());
    }

    private static void provokeNear(ServerPlayer p, String element, double radius) {
        for (LivingEntity d : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(radius), e -> isWildDragonOf(e, element))) {
            PROVOKED.computeIfAbsent(d.getUUID(), k -> new HashSet<>()).add(p.getUUID());
        }
    }

    /** Los dragones salvajes de su elemento no lo eligen como objetivo salvo que los haya provocado. */
    @SubscribeEvent
    public void onTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        String element = kinElement(p);
        if (element == null || !isWildDragonOf(event.getEntity(), element)) return;
        Set<UUID> who = PROVOKED.get(event.getEntity().getUUID());
        if (who != null && who.contains(p.getUUID())) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onDragonHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p)) return;
        String element = kinElement(p);
        if (element != null && isWildDragonOf(event.getEntity(), element)) {
            PROVOKED.computeIfAbsent(event.getEntity().getUUID(), k -> new HashSet<>()).add(p.getUUID());
        }
    }

    /** Abrir un contenedor a 32 bloques de un dragón de su elemento lo provoca. */
    @SubscribeEvent
    public void onOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        String element = kinElement(p);
        if (element == null || event.getContainer() == p.inventoryMenu) return;
        provokeNear(p, element, Races.number(p, "race_dragon_kin", "radius", 32));
    }

    /** Romper montones de tesoro (Ice and Fire) también. */
    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer p)) return;
        String element = kinElement(p);
        if (element == null) return;
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(event.getState().getBlock());
        if (id != null && "iceandfire".equals(id.getNamespace()) && id.getPath().contains("pile")) {
            provokeNear(p, element, Races.number(p, "race_dragon_kin", "radius", 32));
        }
    }

    // ------------------------------------------------------------------ inmunidades de subraza

    @SubscribeEvent
    public void onEffect(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player p)) return;
        var effect = event.getEffectInstance().getEffect();
        // Venenoso (Rango 2): inmune al Veneno.
        if (effect == MobEffects.POISON && Races.has(p, "race_poison_immune")) {
            event.setResult(Event.Result.DENY);
            return;
        }
        // Ácido: inmune al Hambre y a los efectos negativos de la comida (pez globo, ojo de araña…).
        if (Races.has(p, "race_acid_stomach") && (effect == MobEffects.HUNGER
                || (effect.getCategory() == MobEffectCategory.HARMFUL && p.isUsingItem() && p.getUseItem().isEdible()))) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** Dracónido de Agua: el aire dura más (×3). Se devuelve parte de lo que se gasta cada tick. */
    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        double factor = Races.number(p, "race_deep_lungs", "air_factor", 1);
        if (factor <= 1) {
            LAST_AIR.remove(p.getUUID());
            return;
        }
        int air = p.getAirSupply();
        Integer last = LAST_AIR.get(p.getUUID());
        if (last != null && air < last && air >= 0) {
            // Se gasta 1 de aire por tick: con factor 3 solo se pierde 1 de cada 3 ticks.
            if (p.tickCount % Math.round(factor) != 0) {
                air = last;
                p.setAirSupply(air);
            }
        }
        LAST_AIR.put(p.getUUID(), air);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_AIR.remove(event.getEntity().getUUID());
        for (Set<UUID> s : PROVOKED.values()) s.remove(event.getEntity().getUUID());
        PROVOKED.values().removeIf(Set::isEmpty);
    }

    /** Un dragón que muere o desaparece deja de estar provocado. */
    @SubscribeEvent
    public void onLeave(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Mob m && !event.getLevel().isClientSide()) PROVOKED.remove(m.getUUID());
    }

    static void log(ServerPlayer p, String msg) {
        DebugLog.event(p, "raza", msg);
    }
}
