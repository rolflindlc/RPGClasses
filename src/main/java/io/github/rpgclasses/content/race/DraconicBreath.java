package io.github.rpgclasses.content.race;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.stat.StatCalculator;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.core.stat.StatMath;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.registry.ModEffects;
import io.github.rpgclasses.server.Allies;
import io.github.rpgclasses.server.DebugLog;
import io.github.rpgclasses.server.StatEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToDoubleBiFunction;

/**
 * Aliento del Dracónido (adenda 0.7, sección 5): cono canalizado de su elemento con la tecla racial 1.
 * Gasta maná de Iron's mientras dura (si Iron's está instalado) y sufre el muro de Magia de la clase como un conjuro.
 * No es un conjuro de Iron's, así que no cuenta para las cargas del Elementalista ni para la Magia Caótica.
 * Volver a pulsar la tecla lo corta antes de tiempo.
 */
public final class DraconicBreath {
    /** Medio ángulo del cono (grados). */
    private static final double HALF_ANGLE = 30;
    private static final int PULSE_TICKS = 10;
    /** Maná por segundo de canalización (sustituible por el datapack con "mana_per_second"). */
    private static final double MANA_PER_SECOND = 10;

    /** Maná: (jugador, coste) → true si lo ha pagado. Sin Iron's siempre se paga (lo instala IronsCompat). */
    public static ToDoubleBiFunction<ServerPlayer, Double> manaBridge = (p, cost) -> 1;

    private record Channel(String element, long end) {}
    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();

    private DraconicBreath() {}

    public static boolean isChanneling(ServerPlayer p) {
        return CHANNELS.containsKey(p.getUUID());
    }

    public static void stop(ServerPlayer p) {
        if (CHANNELS.remove(p.getUUID()) != null) DebugLog.event(p, "raza", "aliento cortado");
    }

    /** Elemento del aliento según la subraza (mecánica breath_<elemento>). */
    static String element(ServerPlayer p) {
        for (String e : new String[]{"fire", "ice", "lightning", "poison", "acid", "water"}) {
            if (Races.has(p, "race_breath_" + e)) return e;
        }
        return null;
    }

    /** Lo llama RaceAbilities al pulsar la tecla racial 1 (ranura 0). */
    static boolean start(ServerPlayer p, ActiveMechanic m) {
        String element = element(p);
        if (element == null) return false;
        double cost = m.number("mana_per_second", MANA_PER_SECOND) * PULSE_TICKS / 20.0;
        if (manaBridge.applyAsDouble(p, cost) <= 0) {
            p.displayClientMessage(Component.translatable("message.rpgclasses.breath_no_mana"), true);
            return false;
        }
        long end = p.level().getGameTime() + Math.round(m.number("channel_seconds", 2) * 20);
        CHANNELS.put(p.getUUID(), new Channel(element, end));
        return true;
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        Channel ch = CHANNELS.get(p.getUUID());
        if (ch == null) return;
        long now = p.level().getGameTime();
        ActiveMechanic m = Races.find(p, "race_breath");
        if (m == null || !p.isAlive() || now >= ch.end()) {
            CHANNELS.remove(p.getUUID());
            return;
        }
        DebugLog.guard(p, "aliento", () -> tick(p, m, ch, now));
    }

    private static void tick(ServerPlayer p, ActiveMechanic m, Channel ch, long now) {
        ServerLevel level = p.serverLevel();
        double range = m.number("range", 4);
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        particles(level, ch.element(), eye, look, range);
        if ((ch.end() - now) % PULSE_TICKS != 0) return;
        // Cada medio segundo: paga el maná del siguiente medio segundo (si no puede, se corta) y golpea el cono.
        double cost = m.number("mana_per_second", MANA_PER_SECOND) * PULSE_TICKS / 20.0;
        if (manaBridge.applyAsDouble(p, cost) <= 0) {
            CHANNELS.remove(p.getUUID());
            p.displayClientMessage(Component.translatable("message.rpgclasses.breath_no_mana"), true);
            return;
        }
        level.playSound(null, p.blockPosition(), sound(ch.element()), SoundSource.PLAYERS, 0.8f, 1.0f);
        float damage = (float) (m.number("damage_per_second", 3) * PULSE_TICKS / 20.0 * magicFactor(p));
        Set<LivingEntity> hit = new HashSet<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range + 1),
                e -> e != p && e.isAlive() && inCone(eye, look, e, range))) {
            if (!Allies.isEnemy(p, e)) continue;
            hit.add(e);
            strike(p, e, ch.element(), damage);
        }
        if ("lightning".equals(ch.element())) chain(p, hit, damage);
        if ("water".equals(ch.element())) douse(level, eye, look, range);
    }

    /** Multiplicador del muro de Magia de la clase (el aliento cuenta como magia). */
    private static double magicFactor(ServerPlayer p) {
        PlayerClassData data = PlayerClassDataProvider.get(p);
        if (data == null || data.state.classId == null) return 1;
        var classDef = DefinitionStore.SERVER.classDef(data.state.classId);
        if (classDef == null) return 1;
        var subDef = DefinitionStore.SERVER.subclassDef(data.state.subclassId);
        double wall = StatCalculator.wall(data.state, classDef, subDef, StatEngine.race(data.state), Ids.MAGIC);
        return StatMath.wallDamageFactor(wall);
    }

    private static boolean inCone(Vec3 eye, Vec3 look, LivingEntity e, double range) {
        Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
        double dist = to.length();
        if (dist > range + e.getBbWidth() / 2 || dist < 1e-3) return dist < 1e-3;
        double cos = to.normalize().dot(look);
        return cos >= Math.cos(Math.toRadians(HALF_ANGLE)) || dist < 1.2;
    }

    private static DamageSource source(ServerPlayer p, String element) {
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("breath_" + element));
        var holder = p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(key);
        return holder.map(h -> new DamageSource(h, p, p)).orElseGet(() -> p.damageSources().playerAttack(p));
    }

    private static void strike(ServerPlayer p, LivingEntity e, String element, float damage) {
        boolean boss = Races.isBoss(e);
        boolean ownBoss = Rites.isOwnBoss(p, e);
        String credit = "breath";
        switch (element) {
            case "fire" -> e.setSecondsOnFire((int) Math.round(Races.number(p, "race_breath_fire", "burn_seconds", 3)));
            case "ice" -> {
                int amp = (int) Races.number(p, "race_breath_ice", "amplifier", 0);
                int ticks = (int) Math.round(Races.number(p, "race_breath_ice", "slow_seconds", 3) * 20);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, boss ? ticks / 2 : ticks, boss ? 0 : amp), p);
            }
            case "poison" -> {
                int amp = (int) Races.number(p, "race_breath_poison", "amplifier", 0);
                int ticks = (int) Math.round(Races.number(p, "race_breath_poison", "venom_seconds", 4) * 20);
                if (boss) ticks /= 2;
                if (e.getMobType() == MobType.UNDEAD) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 0), p);
                } else {
                    boolean had = e.hasEffect(MobEffects.POISON);
                    e.addEffect(new MobEffectInstance(MobEffects.POISON, ticks, amp), p);
                    credit = "breath_poison";
                    if (!had && ownBoss) Rites.count(p, "breath_poisoned", 1);
                }
            }
            case "acid" -> {
                double pct = Races.number(p, "race_breath_acid", "corrosion", 0.1) * (boss ? 0.5 : 1);
                int ticks = (int) Math.round(Races.number(p, "race_breath_acid", "corrosion_seconds", 5) * 20);
                // Corrosión: −5 % de armadura y dureza por nivel del efecto (no se acumula: se renueva).
                int amp = Math.max(0, (int) Math.round(pct / 0.05) - 1);
                boolean had = e.hasEffect(ModEffects.CORROSION.get());
                e.addEffect(new MobEffectInstance(ModEffects.CORROSION.get(), ticks, amp), p);
                if (e.getArmorValue() > 0) credit = "breath_acid";
                if (!had && ownBoss) Rites.count(p, "breath_corroded", 1);
            }
            case "water" -> {
                if (e.isOnFire()) e.clearFire();
                Vec3 away = e.position().subtract(p.position());
                double push = Races.number(p, "race_breath_water", "breath_push", 1.0) * 0.4 * (boss ? 0.5 : 1);
                if (away.horizontalDistanceSqr() > 1e-6) e.knockback(push, -away.x, -away.z);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), p);
            }
            default -> {}
        }
        String src = credit;
        Rites.dealing(src, () -> e.hurt(source(p, element), damage));
        if (!"breath".equals(credit)) Rites.credit(p, e, credit);
    }

    /** Eléctrico: el rayo salta a enemigos cercanos que no estén ya en el cono. */
    private static void chain(ServerPlayer p, Set<LivingEntity> hit, float damage) {
        int chains = (int) Races.number(p, "race_breath_lightning", "chains", 0);
        if (chains <= 0 || hit.isEmpty()) return;
        float jump = (float) (damage * Races.number(p, "race_breath_lightning", "chain_factor", 0.5));
        Set<LivingEntity> done = new HashSet<>(hit);
        List<LivingEntity> from = new ArrayList<>(hit);
        for (LivingEntity origin : from) {
            int left = chains;
            for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, origin.getBoundingBox().inflate(4),
                    e -> e != p && e.isAlive() && !done.contains(e))) {
                if (left <= 0) break;
                if (!Allies.isEnemy(p, e)) continue;
                done.add(e);
                left--;
                Rites.dealing("breath", () -> e.hurt(source(p, "lightning"), jump));
                ((ServerLevel) p.level()).sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(),
                        12, 0.3, 0.4, 0.3, 0.2);
            }
        }
    }

    /** Agua: apaga el fuego del mundo dentro del cono. */
    private static void douse(ServerLevel level, Vec3 eye, Vec3 look, double range) {
        for (double d = 1; d <= range; d += 1) {
            BlockPos pos = BlockPos.containing(eye.add(look.scale(d)));
            for (BlockPos q : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
                if (level.getBlockState(q).is(BlockTags.FIRE)) level.removeBlock(q, false);
            }
        }
    }

    private static void particles(ServerLevel level, String element, Vec3 eye, Vec3 look, double range) {
        ParticleOptions particle = switch (element) {
            case "fire" -> ParticleTypes.FLAME;
            case "ice" -> ParticleTypes.SNOWFLAKE;
            case "lightning" -> ParticleTypes.ELECTRIC_SPARK;
            case "poison" -> new DustParticleOptions(new Vector3f(0.35f, 0.75f, 0.2f), 1.2f);
            case "acid" -> new DustParticleOptions(new Vector3f(0.75f, 0.9f, 0.1f), 1.2f);
            default -> ParticleTypes.SPLASH;
        };
        for (int i = 0; i < 6; i++) {
            double d = 0.6 + level.random.nextDouble() * range;
            double spread = Math.tan(Math.toRadians(HALF_ANGLE)) * d * 0.8;
            Vec3 at = eye.add(look.scale(d)).add((level.random.nextDouble() - 0.5) * spread, (level.random.nextDouble() - 0.5) * spread,
                    (level.random.nextDouble() - 0.5) * spread);
            level.sendParticles(particle, at.x, at.y - 0.2, at.z, 1, 0, 0, 0, 0.01);
        }
    }

    private static SoundEvent sound(String element) {
        return switch (element) {
            case "fire" -> SoundEvents.FIRECHARGE_USE;
            case "ice" -> SoundEvents.POWDER_SNOW_STEP;
            case "lightning" -> SoundEvents.BEE_LOOP_AGGRESSIVE;
            case "water" -> SoundEvents.GENERIC_SPLASH;
            default -> SoundEvents.SLIME_SQUISH;
        };
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) CHANNELS.remove(p.getUUID());
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CHANNELS.remove(event.getEntity().getUUID());
    }
}
