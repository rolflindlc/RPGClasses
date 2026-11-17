package io.github.rpgclasses.bard;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.content.race.Races;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.Allies;
import io.github.rpgclasses.server.MechanicService;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Combate del Bardo: golpe a compás, Combo Marcial y Golpe Resonante (Cantor de Guerra), exención de la guitarra-hacha,
 * los efectos de daño de las canciones, el control (Baile Forzado, Silencio, Hechizado) y el botín de los jefes.
 */
public final class BardCombat {
    private static final TagKey<DamageType> FIRE = TagKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("element/fire"));
    private static final TagKey<DamageType> ICE = TagKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("element/ice"));
    private static final TagKey<DamageType> LIGHTNING = TagKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("element/lightning"));
    private static final TagKey<EntityType<?>> SUN_LUTE = TagKey.create(Registries.ENTITY_TYPE, RpgClasses.id("bard/sun_lute"));
    private static final TagKey<EntityType<?>> ABYSSAL_REED = TagKey.create(Registries.ENTITY_TYPE, RpgClasses.id("bard/abyssal_reed"));
    private static final TagKey<EntityType<?>> COLOSSUS_DRUMSKIN = TagKey.create(Registries.ENTITY_TYPE, RpgClasses.id("bard/colossus_drumskin"));

    /** Golpe Resonante pendiente del golpe en curso (lo marca {@link #meleeBonus} y lo suelta {@link #afterMelee}). */
    private static final Set<UUID> WAVE = new java.util.HashSet<>();
    /** Quién bailó o silenció a cada criatura (para Hechizar): entidad → Bardo. */
    private static final Map<UUID, UUID> CONTROLLER = new HashMap<>();
    /** Quién hechizó a cada mob: mob → Bardo. */
    private static final Map<UUID, UUID> CHARMER = new HashMap<>();

    // ------------------------------------------------------------------ golpes (llamado desde DamagePipeline)

    /**
     * Multiplicador del muro para un golpe cuerpo a cuerpo de un Bardo: la guitarra-hacha no tiene muro, y el Combo
     * Marcial (Cantor de Guerra) suma +1 % por acierto con armas ligeras y pesadas, sumado al muro.
     */
    public static double wallFactor(ServerPlayer attacker, ItemStack weapon, String discipline, double factor) {
        if (!Bard.isBard(attacker)) return factor;
        if (weapon.getItem() instanceof GuitarAxeItem) factor = 1.0;
        ActiveMechanic martial = MechanicService.find(attacker, Bard.MARTIAL_COMBO);
        if (martial != null && (Ids.LIGHT.equals(discipline) || Ids.HEAVY.equals(discipline))) {
            factor += Math.min(martial.number("cap", 0.3), BardState.combo(attacker) * martial.number("per_hit", 0.01));
        }
        return factor;
    }

    /** Golpe a compás (+15 %) y Furia Rítmica (+10 % con Armonía 3 o más). 1 si no hay nada. */
    public static double meleeBonus(ServerPlayer attacker) {
        if (!Bard.isBard(attacker)) return 1;
        double f = 1;
        if (BardState.consumeOnBeat(attacker)) {
            f *= 1 + Bard.number(Bard.RHYTHM, "beat_bonus", 0.15);
            if (BardState.consumeResonant(attacker)) WAVE.add(attacker.getUUID());
        }
        if (AbilityService.has(attacker, Bard.RHYTHMIC_FURY)
                && BardState.stage(attacker) >= (int) Bard.number(Bard.RHYTHMIC_FURY, "min_stage", 3)) {
            f *= 1 + Bard.number(Bard.RHYTHMIC_FURY, "damage_bonus", 0.10);
        }
        return f;
    }

    /** Golpe Resonante: onda sónica alrededor del objetivo con parte del golpe (a los demás enemigos). */
    public static void afterMelee(ServerPlayer attacker, LivingEntity target, float damage) {
        if (!WAVE.remove(attacker.getUUID())) return;
        ActiveMechanic m = MechanicService.find(attacker, Bard.RESONANT_STRIKE);
        if (m == null) return;
        double r = m.number("radius", 3);
        float dmg = (float) (damage * m.number("damage_fraction", 0.3));
        ServerLevel level = attacker.serverLevel();
        level.sendParticles(ParticleTypes.SONIC_BOOM, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(),
                net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, 0.8f);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(r))) {
            if (e == target || e.distanceToSqr(target) > r * r || !Allies.isEnemy(attacker, e)) continue;
            e.hurt(BardAuras.sonic(attacker), dmg);
        }
    }

    // ------------------------------------------------------------------ efectos de daño de las canciones

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        float amount = event.getAmount();
        DamageSource source = event.getSource();
        if (amount <= 0 || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (source.getEntity() instanceof LivingEntity attacker) {
            int bonus = BardEffects.points(attacker, BardEffects.WAR_HYMN) + BardEffects.points(attacker, BardEffects.REQUIEM_FERVOR);
            if (bonus > 0) amount *= 1 + bonus / 100f;
        }
        LivingEntity victim = event.getEntity();
        int chorale = Math.min(60, BardEffects.points(victim, BardEffects.IRON_CHORALE));
        if (chorale > 0) amount *= 1 - chorale / 100f;
        amount *= ward(victim, source, FIRE, BardEffects.SCALE_WARD_FIRE)
                * ward(victim, source, ICE, BardEffects.SCALE_WARD_ICE)
                * ward(victim, source, LIGHTNING, BardEffects.SCALE_WARD_LIGHTNING);
        if (amount != event.getAmount()) event.setAmount(amount);
    }

    private static float ward(LivingEntity victim, DamageSource source, TagKey<DamageType> tag, RegistryObject<MobEffect> effect) {
        if (!source.is(tag)) return 1;
        int pts = Math.min(80, BardEffects.points(victim, effect));
        return 1 - pts / 100f;
    }

    // ------------------------------------------------------------------ control

    public static boolean dancing(LivingEntity e) {
        return e.hasEffect(BardEffects.FORCED_DANCE.get());
    }

    public static boolean silenced(LivingEntity e) {
        return e.hasEffect(BardEffects.SILENCED.get());
    }

    /** Pone a bailar a una criatura (y avisa a los clientes para la animación). */
    public static void dance(ServerPlayer bard, LivingEntity target, int ticks) {
        if (ticks <= 0) return;
        target.addEffect(new MobEffectInstance(BardEffects.FORCED_DANCE.get(), ticks, 0, false, true, true), bard);
        if (target instanceof Mob mob) mob.getNavigation().stop();
        CONTROLLER.put(target.getUUID(), bard.getUUID());
        Net.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target), new Packets.DanceSync(target.getId(), ticks));
    }

    /** Silencio de la Disonancia. */
    public static void silence(ServerPlayer bard, LivingEntity target, int ticks) {
        if (ticks <= 0) return;
        target.addEffect(new MobEffectInstance(BardEffects.SILENCED.get(), ticks, 0, false, true, true), bard);
        CONTROLLER.put(target.getUUID(), bard.getUUID());
    }

    /** Duración del control según Voz Hipnótica (Encantador). */
    public static int controlTicks(ServerPlayer bard, double seconds) {
        return (int) Math.round(seconds * 20 * (1 + Bard.mech(bard, Bard.HYPNOTIC_VOICE, "control_duration", 0)));
    }

    /** Los bailarines no atacan; los hechizados no atacan al Bardo ni a sus aliados. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(LivingAttackEvent event) {
        Entity src = event.getSource().getEntity();
        if (!(src instanceof LivingEntity attacker) || src == event.getEntity()) return;
        if (dancing(attacker)) {
            event.setCanceled(true);
            return;
        }
        UUID charmer = CHARMER.get(attacker.getUUID());
        if (charmer != null && attacker.hasEffect(BardEffects.CHARMED.get()) && attacker.getServer() != null
                && Allies.isAllyOf(attacker.getServer(), charmer, event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlayerAttack(AttackEntityEvent event) {
        if (dancing(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (dancing(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (dancing(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onBreak(PlayerInteractEvent.LeftClickBlock event) {
        if (dancing(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity mob = event.getEntity();
        UUID charmer = CHARMER.get(mob.getUUID());
        if (charmer == null || !mob.hasEffect(BardEffects.CHARMED.get()) || event.getNewTarget() == null || mob.getServer() == null) return;
        if (Allies.isAllyOf(mob.getServer(), charmer, event.getNewTarget())) event.setCanceled(true);
    }

    /** Hechizar (Encantador, hito 5): al terminar el baile o la Disonancia, los mobs normales quedan hechizados. */
    @SubscribeEvent
    public void onEffectExpired(MobEffectEvent.Expired event) {
        MobEffectInstance inst = event.getEffectInstance();
        LivingEntity e = event.getEntity();
        if (inst == null || e.level().isClientSide || !(e instanceof Mob mob)) return;
        if (inst.getEffect() != BardEffects.FORCED_DANCE.get() && inst.getEffect() != BardEffects.SILENCED.get()) return;
        UUID bardId = CONTROLLER.remove(e.getUUID());
        MinecraftServer server = e.getServer();
        ServerPlayer bard = bardId == null || server == null ? null : server.getPlayerList().getPlayer(bardId);
        if (bard == null || Races.isBoss(mob) || !AbilityService.has(bard, Bard.BEWITCH)) return;
        int ticks = AbilityService.ticks(AbilityService.params(Bard.BEWITCH), "duration_seconds", 4);
        mob.addEffect(new MobEffectInstance(BardEffects.CHARMED.get(), ticks, 0, false, true, true), bard);
        CHARMER.put(mob.getUUID(), bard.getUUID());
        if (mob.getTarget() != null && Allies.isAlly(bard, mob.getTarget())) mob.setTarget(null);
        ((ServerLevel) mob.level()).sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 3, 0.3, 0.2, 0.3, 0);
    }

    /** Bailarines quietos en su sitio; hechizados buscando otro mob al que pegar. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        boolean retarget = server.getTickCount() % 10 == 0;
        if (CHARMER.isEmpty() && CONTROLLER.isEmpty()) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (UUID id : java.util.List.copyOf(CONTROLLER.keySet())) {
                Entity e = level.getEntity(id);
                if (e instanceof Mob mob && dancing(mob)) {
                    mob.getNavigation().stop();
                    mob.setDeltaMovement(0, Math.min(0, mob.getDeltaMovement().y), 0);
                }
            }
            if (!retarget) continue;
            for (UUID id : java.util.List.copyOf(CHARMER.keySet())) {
                Entity e = level.getEntity(id);
                if (!(e instanceof Mob mob)) continue;
                if (!mob.isAlive() || !mob.hasEffect(BardEffects.CHARMED.get())) {
                    CHARMER.remove(id);
                    continue;
                }
                UUID charmer = CHARMER.get(id);
                LivingEntity t = mob.getTarget();
                if (t != null && t.isAlive() && !Allies.isAllyOf(server, charmer, t)) continue;
                LivingEntity best = null;
                double bestD = 144;
                for (LivingEntity o : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(12))) {
                    if (o == mob || !(o instanceof Enemy) || !o.isAlive() || Allies.isAllyOf(server, charmer, o)) continue;
                    double d = o.distanceToSqr(mob);
                    if (d < bestD) {
                        bestD = d;
                        best = o;
                    }
                }
                mob.setTarget(best);
            }
        }
        CONTROLLER.keySet().removeIf(id -> {
            for (ServerLevel level : server.getAllLevels()) {
                Entity e = level.getEntity(id);
                if (e instanceof LivingEntity le && (dancing(le) || silenced(le))) return false;
            }
            return server.getTickCount() % 100 == 0;
        });
    }

    // ------------------------------------------------------------------ botín de jefes

    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || !(event.getSource().getEntity() instanceof Player)) return;
        drop(event, e, SUN_LUTE, () -> BardItems.INSTRUMENTS.get(Instrument.ETERNAL_SUN_LUTE).get());
        drop(event, e, ABYSSAL_REED, BardItems.ABYSSAL_REED);
        drop(event, e, COLOSSUS_DRUMSKIN, BardItems.COLOSSUS_DRUMSKIN);
    }

    private static void drop(LivingDropsEvent event, LivingEntity e, TagKey<EntityType<?>> tag, Supplier<Item> item) {
        if (!e.getType().is(tag)) return;
        event.getDrops().add(new ItemEntity(e.level(), e.getX(), e.getY() + 0.5, e.getZ(), new ItemStack(item.get())));
    }
}
