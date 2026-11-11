package io.github.rpgclasses.content;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.registry.ModEffects;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.MechanicService;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rama Castigo del Paladín y sus perks Juicio / Condena (rev. 2.7c). DamagePipeline pide los factores aquí.
 * El daño sagrado es del Paladín: no pasa por el muro de Magia y está en el tag no_proc.
 */
public final class PaladinPerks {
    public static final String SMITE = "smite";
    public static final String JUDGEMENT = "judgement";
    public static final String CONDEMN = "condemn";
    public static final String SHOCKWAVE = "shockwave";
    public static final String SHIELD_COUNTER = "shield_counter";
    public static final ResourceKey<DamageType> SHOCKWAVE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("shockwave"));
    private static final String SHOCKWAVE_COUNTER = "rpgclasses:shockwave_hits";
    private static final String SHIELD_COUNTER_COOLDOWN = "rpgclasses:shield_counter";
    public static final TagKey<EntityType<?>> NETHER_MOBS = TagKey.create(Registries.ENTITY_TYPE, RpgClasses.id("nether_mobs"));
    public static final ResourceKey<DamageType> HOLY_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("holy"));
    private static final String JUDGEMENT_COUNTER = "rpgclasses:judgement_hits";

    /** Paladín -> marca de Condena activa. */
    private record Mark(UUID target, long untilTick) {}
    private static final Map<UUID, Mark> MARKS = new HashMap<>();

    /** Castigo: +daño contra no-muertos y mobs del Nether, y +daño general sin escudo en las manos. */
    public static double smiteBonus(ServerPlayer attacker, LivingEntity target) {
        ActiveMechanic m = MechanicService.find(attacker, SMITE);
        if (m == null) return 0;
        double bonus = 0;
        if (target.getMobType() == MobType.UNDEAD || target.getType().is(NETHER_MOBS)) bonus += m.number("undead_nether_damage", 0);
        if (!isShield(attacker.getMainHandItem()) && !isShield(attacker.getOffhandItem())) bonus += m.number("no_shield_damage", 0);
        return bonus;
    }

    private static boolean isShield(ItemStack stack) {
        return !stack.isEmpty() && stack.canPerformAction(ToolActions.SHIELD_BLOCK);
    }

    /**
     * Juicio: cuenta los golpes cuerpo a cuerpo; el N-ésimo devuelve su bonus (+40 %) y reinicia la cuenta.
     * Devuelve 0 en los demás golpes.
     */
    public static double judgementBonus(ServerPlayer attacker) {
        ActiveMechanic m = MechanicService.find(attacker, JUDGEMENT);
        PlayerClassData data = PlayerClassDataProvider.get(attacker);
        if (m == null || data == null) return 0;
        int every = Math.max(1, (int) Math.round(m.number("every_hits", 6)));
        int hits = data.counters.getOrDefault(JUDGEMENT_COUNTER, 0) + 1;
        if (hits < every) {
            data.counters.put(JUDGEMENT_COUNTER, hits);
            return 0;
        }
        data.counters.remove(JUDGEMENT_COUNTER);
        return m.number("damage_bonus", 0.4);
    }

    /** Juicio: el daño sagrado extra alcanza también a los enemigos a {@code radius} bloques del objetivo. */
    public static void judgementArea(ServerPlayer attacker, LivingEntity target, float holyDamage) {
        double radius = MechanicService.number(attacker, JUDGEMENT, "radius", 2);
        if (holyDamage <= 0) return;
        DamageSource source = new DamageSource(attacker.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(HOLY_DAMAGE), attacker);
        for (LivingEntity e : attacker.level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(radius),
                e -> e != target && e != attacker && e.isAlive() && !e.isAlliedTo(attacker) && e.distanceToSqr(target) <= radius * radius)) {
            if (e instanceof ServerPlayer other && !attacker.canHarmPlayer(other)) continue;
            e.hurt(source, holyDamage);
        }
        if (attacker.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    16, radius / 2, 0.3, radius / 2, 0.02);
        }
        attacker.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    /**
     * Condena: bonus si el objetivo ya estaba marcado por este Paladín. Si no hay marca activa, marca a este.
     * Solo una marca a la vez.
     */
    public static double condemnBonus(ServerPlayer attacker, LivingEntity target) {
        ActiveMechanic m = MechanicService.find(attacker, CONDEMN);
        if (m == null) return 0;
        long now = attacker.level().getGameTime();
        Mark mark = MARKS.get(attacker.getUUID());
        if (mark != null && mark.untilTick() > now) {
            return mark.target().equals(target.getUUID()) ? m.number("damage_bonus", 0.15) : 0;
        }
        int ticks = (int) Math.round(m.number("duration_seconds", 6) * 20);
        MARKS.put(attacker.getUUID(), new Mark(target.getUUID(), now + ticks));
        target.addEffect(new MobEffectInstance(ModEffects.CONDEMNED.get(), ticks, 0), attacker);
        return 0;
    }

    /**
     * Onda de Choque: cada N golpes cuerpo a cuerpo, los enemigos a {@code radius} bloques del Paladín
     * (salvo el objetivo, que ya recibió el golpe) reciben una fracción del daño y salen empujados.
     */
    public static void shockwave(ServerPlayer attacker, LivingEntity target, float hitDamage) {
        ActiveMechanic m = MechanicService.find(attacker, SHOCKWAVE);
        PlayerClassData data = PlayerClassDataProvider.get(attacker);
        if (m == null || data == null) return;
        int every = Math.max(1, (int) Math.round(m.number("every_hits", 5)));
        int hits = data.counters.getOrDefault(SHOCKWAVE_COUNTER, 0) + 1;
        if (hits < every) {
            data.counters.put(SHOCKWAVE_COUNTER, hits);
            return;
        }
        data.counters.remove(SHOCKWAVE_COUNTER);
        double radius = m.number("radius", 3);
        float damage = (float) (hitDamage * m.number("damage_fraction", 0.3));
        DamageSource source = new DamageSource(attacker.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(SHOCKWAVE_DAMAGE), attacker);
        for (LivingEntity e : attacker.level().getEntitiesOfClass(LivingEntity.class, attacker.getBoundingBox().inflate(radius),
                e -> e != attacker && e.isAlive() && !e.isAlliedTo(attacker) && e.distanceToSqr(attacker) <= radius * radius)) {
            if (e instanceof ServerPlayer other && !attacker.canHarmPlayer(other)) continue;
            if (e != target && damage > 0) e.hurt(source, damage);
            pushAway(attacker, e, 0.6);
        }
        if (attacker.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, attacker.getX(), attacker.getY() + 0.5, attacker.getZ(), 8, radius / 2, 0.1, radius / 2, 0);
        }
        attacker.level().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    /** Contragolpe de Escudo: al bloquear un golpe cuerpo a cuerpo, el atacante sale despedido. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onShieldBlock(ShieldBlockEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        ActiveMechanic m = MechanicService.find(player, SHIELD_COUNTER);
        DamageSource source = event.getDamageSource();
        if (m == null || !(source.getDirectEntity() instanceof LivingEntity attacker) || source.getEntity() != attacker) return;
        if (attacker == player || AbilityService.remainingTicks(player, SHIELD_COUNTER_COOLDOWN) > 0) return;
        if (attacker instanceof ServerPlayer other && !player.canHarmPlayer(other)) return;
        // Fuerza aproximada: ~1 de knockback por cada 3 bloques [MEDIR].
        pushAway(player, attacker, m.number("knockback_blocks", 3) / 3.0);
        AbilityService.setCooldown(player, SHIELD_COUNTER_COOLDOWN, (int) Math.round(m.number("icd_seconds", 3) * 20));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 0.6f);
    }

    /** Empuja a {@code target} alejándolo de {@code from}. */
    private static void pushAway(LivingEntity from, LivingEntity target, double strength) {
        double dx = target.getX() - from.getX();
        double dz = target.getZ() - from.getZ();
        if (dx * dx + dz * dz < 1.0E-4) {
            dx = from.getLookAngle().x;
            dz = from.getLookAngle().z;
        }
        // knockback() empuja en sentido contrario al vector dado.
        target.knockback(strength, -dx, -dz);
        target.hurtMarked = true;
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        MARKS.remove(event.getEntity().getUUID());
    }
}
