package io.github.rpgclasses.content;

import com.google.gson.JsonArray;
import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.registry.ModEffects;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.MechanicService;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Subclases del Bárbaro (rev. 2.7d):
 * <ul>
 *   <li>Indestructible — Apuesta con la Muerte ({@code death_gamble}): moneda; 75 % la primera vez y luego 50 %; se restablece al morir.</li>
 *   <li>Berserker Suicida — Última Furia Inmortal ({@code last_immortal_fury}): 7–17 s inmortal para matar al agresor.</li>
 *   <li>Corruptor de Plagas — Sangre Resistente (en EffectRules) y Golpe Aflictivo ({@code afflicting_strike}): Plaga con contagio.</li>
 * </ul>
 * La supervivencia va en LivingDamageEvent (después de armadura y absorción), antes de que vanilla mire el Tótem.
 */
public final class BarbarianSubclasses {
    public static final String DEATH_GAMBLE = "death_gamble";
    public static final String LAST_FURY = "last_immortal_fury";
    public static final String AFFLICTING_STRIKE = "afflicting_strike";
    public static final String RESISTANT_BLOOD = "resistant_blood";
    private static final String GAMBLE_COUNTER = "death_gamble_uses";
    private static final String FURY_PENDING = "last_fury_pending";
    private static final String FURY_COOLDOWN = "rites_and_paths:last_immortal_fury";

    public static final ResourceKey<DamageType> PLAGUE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("plague"));
    private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE,
            new net.minecraft.resources.ResourceLocation("forge", "bosses"));
    private static final String PLAGUE_TAG = "rites_and_paths.plague";

    /** Última Furia en curso: agresor que hay que matar y tick en que termina. */
    private record Fury(UUID target, long endTick) {}
    private static final Map<UUID, Fury> FURY = io.github.rpgclasses.server.SessionState.track(new HashMap<>());

    // --- Supervivencia ---

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || event.getAmount() < player.getHealth()) return;
        float survive = Math.max(0, player.getHealth() - 1);

        if (FURY.containsKey(player.getUUID())) { // inmortal durante la Última Furia
            event.setAmount(survive);
            return;
        }
        if (tryFury(player, source)) {
            event.setAmount(survive);
            return;
        }
        if (tryGamble(player, source)) event.setAmount(survive);
    }

    /** Apuesta con la Muerte: lanza la moneda (ver {@link DeathGamble}). Mientras gira, el Bárbaro queda a 1 HP. */
    private static boolean tryGamble(ServerPlayer player, DamageSource source) {
        ActiveMechanic m = MechanicService.find(player, DEATH_GAMBLE);
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (m == null || data == null || DeathGamble.resolving(player) || DeathGamble.flipping(player)) return false;
        DeathGamble.start(player, source, m, data, GAMBLE_COUNTER);
        return true;
    }

    private static boolean tryFury(ServerPlayer player, DamageSource source) {
        ActiveMechanic m = MechanicService.find(player, LAST_FURY);
        if (m == null || AbilityService.remainingTicks(player, FURY_COOLDOWN) > 0) return false;
        // Hace falta un agresor que se pueda matar (no el entorno, no uno ya muerto, no un inmune).
        if (!(source.getEntity() instanceof LivingEntity aggressor) || aggressor == player || !aggressor.isAlive()
                || aggressor.isInvulnerable() || (aggressor instanceof Player p && (p.isCreative() || p.isSpectator()))) return false;
        double min = m.number("min_seconds", 7), max = m.number("max_seconds", 17);
        int ticks = (int) Math.round((min + player.getRandom().nextDouble() * (max - min)) * 20);
        FURY.put(player.getUUID(), new Fury(aggressor.getUUID(), player.level().getGameTime() + ticks));
        player.addEffect(new MobEffectInstance(ModEffects.IMMORTAL_FURY.get(), ticks, 0));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 2));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 2));
        AbilityService.setCooldown(player, FURY_COOLDOWN, (int) Math.round(m.number("cooldown_seconds", 300) * 20));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6f, 1.4f);
        player.displayClientMessage(Component.translatable("message.rites_and_paths.last_fury", aggressor.getDisplayName()), true);
        if (aggressor instanceof net.minecraft.world.entity.Mob mob) mob.setGlowingTag(true);
        return true;
    }

    /** Fin de la Última Furia sin haber matado al agresor: el Bárbaro muere. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Fury fury = FURY.get(player.getUUID());
        if (fury == null || player.level().getGameTime() < fury.endTick()) return;
        endFury(player, fury);
        if (player.isAlive()) player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
    }

    private static void endFury(ServerPlayer player, Fury fury) {
        FURY.remove(player.getUUID());
        player.removeEffect(ModEffects.IMMORTAL_FURY.get());
        if (player.level() instanceof ServerLevel level && level.getEntity(fury.target()) instanceof net.minecraft.world.entity.Mob mob) {
            mob.setGlowingTag(false);
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) return;

        // Muerte real del jugador: la Apuesta con la Muerte vuelve al 75 %.
        if (dead instanceof ServerPlayer player) {
            Fury fury = FURY.get(player.getUUID());
            if (fury != null && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                event.setCanceled(true); // seguro: ninguna otra vía lo mata durante la Furia
                player.setHealth(1);
                return;
            }
            if (fury != null) endFury(player, fury);
            PlayerClassData data = PlayerClassDataProvider.get(player);
            if (data != null) data.counters.remove(GAMBLE_COUNTER);
        }

        // Última Furia: el Bárbaro ha matado a su agresor.
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            Fury fury = FURY.get(killer.getUUID());
            if (fury != null && fury.target().equals(dead.getUUID())) {
                endFury(killer, fury);
                killer.removeEffect(MobEffects.DAMAGE_BOOST);
                killer.removeEffect(MobEffects.MOVEMENT_SPEED);
                killer.setHealth(1);
                ActiveMechanic m = MechanicService.find(killer, LAST_FURY);
                int weakness = (int) Math.round((m == null ? 10 : m.number("weakness_seconds", 10)) * 20);
                killer.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, weakness, 2));
            }
        }

        // Plaga: contagio al enemigo más cercano con la duración restante.
        MobEffectInstance plague = dead.getEffect(ModEffects.PLAGUE.get());
        CompoundTag dose = dead.getPersistentData().getCompound(PLAGUE_TAG);
        if (plague == null || !dose.hasUUID("owner") || !(dead.level() instanceof ServerLevel level)) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(dose.getUUID("owner"));
        if (owner == null) return;
        double radius = dose.getDouble("radius");
        int remaining = plague.getDuration();
        if (remaining <= 1 || radius <= 0) return;
        level.getEntitiesOfClass(LivingEntity.class, dead.getBoundingBox().inflate(radius),
                        e -> e != dead && CombatAbilities.canHit(owner, e) && !e.hasEffect(ModEffects.PLAGUE.get())
                                && e.distanceToSqr(dead) <= radius * radius)
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(dead)))
                .ifPresent(next -> applyPlague(owner, next, remaining, dose));
    }

    // --- Plaga ---

    /** Golpe Aflictivo: llamado por DamagePipeline tras un golpe cuerpo a cuerpo. No se apila ni se renueva. */
    public static void onMeleeHit(ServerPlayer attacker, LivingEntity target) {
        ActiveMechanic m = MechanicService.find(attacker, AFFLICTING_STRIKE);
        if (m == null) return;
        if (!target.isAlive() || target.hasEffect(ModEffects.PLAGUE.get())) {
            io.github.rpgclasses.server.DebugLog.event(attacker, "Plaga", "no se aplica a {}: {}", io.github.rpgclasses.server.DebugLog.describe(target),
                    target.isAlive() ? "ya tiene Plaga" : "ha muerto con el golpe");
            return;
        }
        CompoundTag dose = new CompoundTag();
        double perSecond = m.number("damage_per_second", 2) * (target.getType().is(BOSSES) ? m.number("boss_factor", 0.5) : 1);
        dose.putFloat("damage", (float) (perSecond / 2)); // un tic cada 10 ticks
        dose.putDouble("radius", m.number("contagion_radius", 5));
        applyPlague(attacker, target, (int) Math.round(m.number("duration_seconds", 6) * 20), dose);
    }

    private static void applyPlague(ServerPlayer owner, LivingEntity target, int ticks, CompoundTag base) {
        if (!target.addEffect(new MobEffectInstance(ModEffects.PLAGUE.get(), ticks, 0), owner)) {
            io.github.rpgclasses.server.DebugLog.event(owner, "Plaga", "{} no acepta el efecto", target);
            return;
        }
        io.github.rpgclasses.server.DebugLog.event(owner, "Plaga", "{} infecta a {} ({} ticks, {} por tic)",
                owner.getGameProfile().getName(), target, ticks, base.getFloat("damage"));
        CompoundTag dose = base.copy();
        dose.putUUID("owner", owner.getUUID());
        dose.putLong("next", target.level().getGameTime() + 10);
        target.getPersistentData().put(PLAGUE_TAG, dose);
    }

    @SubscribeEvent
    public void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        CompoundTag data = entity.getPersistentData();
        if (!entity.hasEffect(ModEffects.PLAGUE.get())) {
            if (data.contains(PLAGUE_TAG)) data.remove(PLAGUE_TAG);
            return;
        }
        if (!data.contains(PLAGUE_TAG)) return;
        CompoundTag dose = data.getCompound(PLAGUE_TAG);
        long now = entity.level().getGameTime();
        if (now < dose.getLong("next")) return;
        dose.putLong("next", now + 10);
        Entity owner = dose.hasUUID("owner") && entity.level() instanceof ServerLevel level
                ? level.getServer().getPlayerList().getPlayer(dose.getUUID("owner")) : null;
        if (owner instanceof ServerPlayer p) entity.setLastHurtByPlayer(p);
        int invulnerable = entity.invulnerableTime;
        entity.invulnerableTime = 0;
        if (entity.level() instanceof ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SNEEZE, entity.getX(), entity.getY() + entity.getBbHeight() * 0.7,
                    entity.getZ(), 4, 0.3, 0.3, 0.3, 0.01);
        }
        entity.hurt(new DamageSource(entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(PLAGUE_DAMAGE)), dose.getFloat("damage"));
        entity.invulnerableTime = invulnerable;
    }

    /** Sangre Resistente: fracción de duración que quita a los efectos de su lista (1 = inmune). */
    public static double resistantBlood(ServerPlayer player, net.minecraft.world.effect.MobEffect effect) {
        ActiveMechanic m = MechanicService.find(player, RESISTANT_BLOOD);
        if (m == null || !m.params().has("effects")) return 0;
        var id = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(effect);
        if (id == null) return 0;
        for (var e : m.params().getAsJsonArray("effects")) {
            if (id.toString().equals(e.getAsString())) return m.number("duration_reduction", 0);
        }
        return 0;
    }

    /** Salir durante la Furia no sirve para huir: al volver, el Bárbaro muere. */
    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (FURY.remove(event.getEntity().getUUID()) == null || !(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data != null) data.counters.put(FURY_PENDING, 1);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null || data.counters.remove(FURY_PENDING) == null) return;
        player.removeEffect(ModEffects.IMMORTAL_FURY.get());
        if (player.isAlive()) player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
    }
}
