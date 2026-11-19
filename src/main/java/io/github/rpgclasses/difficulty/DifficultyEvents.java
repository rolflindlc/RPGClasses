package io.github.rpgclasses.difficulty;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.config.ServerConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.EnumSet;
import java.util.Set;

/**
 * Dificultad por zona (adenda de dificultad por zona). Todo el módulo se apaga con la opción
 * {@code difficulty.enabled} del archivo de configuración del servidor.
 * <p>
 * Flujo: {@link MobSpawnEvent.FinalizeSpawn} anota el tipo de aparición del mob; al entrar en el mundo
 * ({@link EntityJoinLevelEvent}) se comprueban las exenciones, se calcula T una sola vez, se guarda en el NBT
 * del mob y se aplican las palancas (equipo, stats y mobs extra). Los mobs cargados del disco no se tocan.
 */
public final class DifficultyEvents {
    /** Presente = el mob ya fue procesado (su valor es T). Se guarda con el mob. */
    public static final String KEY_THREAT = RpgClasses.MODID + "_threat";
    /** Tipo de aparición anotado entre FinalizeSpawn y la entrada al mundo; se borra al procesarlo. */
    private static final String KEY_SPAWN = RpgClasses.MODID + "_spawn_type";

    /** Apariciones que reciben dificultad. Quedan fuera las de generación del mundo, huevos, comandos e invocaciones (incluidos los esbirros de jefes). */
    private static final Set<MobSpawnType> SCALED = EnumSet.of(MobSpawnType.NATURAL, MobSpawnType.SPAWNER,
            MobSpawnType.PATROL, MobSpawnType.REINFORCEMENT, MobSpawnType.STRUCTURE, MobSpawnType.EVENT);

    private static final int RESPAWN_REFRESH_TICKS = 100;

    private int extrasThisTick;

    @SubscribeEvent
    public void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!ServerConfig.DIFFICULTY_ENABLED.get() || !SCALED.contains(event.getSpawnType())) return;
        event.getEntity().getPersistentData().putString(KEY_SPAWN, event.getSpawnType().name());
    }

    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Mob mob)) return;
        CompoundTag data = mob.getPersistentData();
        if (!data.contains(KEY_SPAWN)) return;
        String spawnType = data.getString(KEY_SPAWN);
        data.remove(KEY_SPAWN);
        if (!ServerConfig.DIFFICULTY_ENABLED.get() || data.contains(KEY_THREAT)) return;

        if (ExemptionRules.check(mob, level, mob.position()) != ExemptionRules.Reason.NONE) {
            data.putFloat(KEY_THREAT, 0.0f);
            return;
        }
        ThreatService.Result zone = ThreatService.compute(level, mob.position());
        data.putFloat(KEY_THREAT, (float) zone.threat());
        if (zone.threat() < ServerConfig.DIFFICULTY_MIN_THREAT.get()) return;

        applyLevers(mob, zone);
        // Los mobs extra solo acompañan a las apariciones naturales (no a spawners ni a incursiones).
        if (ServerConfig.DIFFICULTY_SPAWN.get() && MobSpawnType.NATURAL.name().equals(spawnType)) {
            extrasThisTick += ExtraSpawner.spawn(level, mob, zone, ServerConfig.DIFFICULTY_EXTRA_PER_TICK.get() - extrasThisTick);
        }
    }

    /** Marca un mob extra como procesado y le aplica las palancas con el T del original. */
    static void markAndApply(Mob mob, ThreatService.Result zone) {
        mob.getPersistentData().putFloat(KEY_THREAT, (float) zone.threat());
        applyLevers(mob, zone);
    }

    private static void applyLevers(Mob mob, ThreatService.Result zone) {
        if (ServerConfig.DIFFICULTY_GEAR.get()) GearRoller.roll(mob, zone);
        if (ServerConfig.DIFFICULTY_STATS.get()) ThreatStats.apply(mob, zone.threat());
        if (ServerConfig.DIFFICULTY_TRAITS.get()) EliteTraits.roll(mob, zone.threat());
    }

    /** Recompensa: Saqueo efectivo extra al matar, con un jugador, mobs que recibieron dificultad. */
    @SubscribeEvent
    public void onLooting(LootingLevelEvent event) {
        if (!ServerConfig.DIFFICULTY_ENABLED.get() || !ServerConfig.DIFFICULTY_LOOT.get()) return;
        LivingEntity victim = event.getEntity();
        if (!victim.getPersistentData().contains(KEY_THREAT)) return;
        if (event.getDamageSource() == null || !(event.getDamageSource().getEntity() instanceof Player)) return;
        int bonus = Math.min(ServerConfig.DIFFICULTY_LOOT_MAX.get(),
                (int) (victim.getPersistentData().getFloat(KEY_THREAT) / ServerConfig.DIFFICULTY_LOOT_STEP.get()));
        if (bonus > 0) event.setLootingLevel(event.getLootingLevel() + bonus);
    }

    /** Rasgos de élite al golpear: fuego, lentitud y debilidad, y vampirismo (sobre el daño final). */
    @SubscribeEvent
    public void onDamage(LivingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Mob attacker) || !attacker.getPersistentData().contains(EliteTraits.KEY_TRAITS)) return;
        LivingEntity target = event.getEntity();
        if (event.getAmount() <= 0 || target == attacker) return;
        if (EliteTraits.has(attacker, EliteTraits.Trait.IGNEOUS)) target.setSecondsOnFire(5);
        if (EliteTraits.has(attacker, EliteTraits.Trait.CRIPPLING)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), attacker);
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), attacker);
        }
        if (EliteTraits.has(attacker, EliteTraits.Trait.VAMPIRIC)) attacker.heal(event.getAmount() * 0.3f);
    }

    /** Rasgo explosivo: al morir explota sin romper bloques. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || !dead.getPersistentData().contains(EliteTraits.KEY_TRAITS)) return;
        if (EliteTraits.has(dead, EliteTraits.Trait.VOLATILE)) {
            level.explode(dead, dead.getX(), dead.getY() + dead.getBbHeight() / 2, dead.getZ(), 2.5f, Level.ExplosionInteraction.NONE);
        }
    }

    /** Señal visible de los mobs de élite: una llama de alma cada segundo. */
    @SubscribeEvent
    public void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.tickCount % 20 != 0 || !(entity.level() instanceof ServerLevel level) || !(entity instanceof Mob)
                || !entity.getPersistentData().contains(EliteTraits.KEY_TRAITS)) return;
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(),
                4, entity.getBbWidth() / 2, entity.getBbHeight() / 3, entity.getBbWidth() / 2, 0.01);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) RespawnIndex.get(player.server).update(player);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        extrasThisTick = 0;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % RESPAWN_REFRESH_TICKS != 0 || !ServerConfig.DIFFICULTY_ENABLED.get()) return;
        RespawnIndex index = RespawnIndex.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) index.update(player);
    }
}
