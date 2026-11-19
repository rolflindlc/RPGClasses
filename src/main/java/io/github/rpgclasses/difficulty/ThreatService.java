package io.github.rpgclasses.difficulty;

import io.github.rpgclasses.config.ServerConfig;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.registry.ModEffects;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;

/**
 * Amenaza T de una zona. En el modo PLAYERS es la media del nivel de clase (0-40) de los jugadores válidos cercanos (los modos
 * DISTANCE y MIXED añaden la distancia al spawn), con las pociones de
 * Tranquilidad y de Desafío aplicadas a la parte de cada jugador. Sin pociones nunca pasa de 40; con Desafío puede
 * llegar al tope configurable ({@code maxThreat}, por defecto 80).
 * Es una función pura de la posición y de los jugadores conectados; no guarda estado.
 */
public final class ThreatService {
    /** Nivel de clase máximo (30 base + 10 subclase) y, por tanto, la T máxima sin pociones. */
    public static final double CLASS_MAX_LEVEL = 40.0;

    /**
     * @param threat     T final: media de niveles con las pociones aplicadas (0 si no hay jugadores válidos);
     * @param players    jugadores que cuentan;
     * @param baseThreat media de niveles sin pociones.
     */
    public record Result(double threat, int players, double baseThreat) {
        public static final Result NONE = new Result(0.0, 0, 0.0);
    }

    private ThreatService() {}

    public static double maxThreat() {
        return ServerConfig.DIFFICULTY_MAX_THREAT.get();
    }

    /** Un jugador cuenta si tiene clase, no está en creativo ni espectador y no lleva demasiado inactivo. */
    public static boolean counts(ServerPlayer player, long nowMillis) {
        if (player.isCreative() || player.isSpectator()) return false;
        int idleMinutes = ServerConfig.DIFFICULTY_IDLE_MINUTES.get();
        if (idleMinutes > 0 && nowMillis - player.getLastActionTime() > idleMinutes * 60_000L) return false;
        PlayerClassData data = PlayerClassDataProvider.get(player);
        return data != null && data.state.classId != null;
    }

    /**
     * T según el modo de {@link DifficultySettings}: nivel de los jugadores (PLAYERS), distancia al spawn (DISTANCE) o la
     * media ponderada de ambos (MIXED). Las pociones de cada jugador cuentan en los tres modos (en los de distancia, con
     * la media de las pociones de los jugadores cercanos).
     */
    public static Result compute(ServerLevel level, Vec3 pos) {
        double radius = ServerConfig.DIFFICULTY_RADIUS.get();
        double radiusSq = radius * radius;
        long now = Util.getMillis();
        int count = 0;
        double sum = 0;
        double sumBase = 0;
        double sumFactor = 0;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) > radiusSq || !counts(player, now)) continue;
            PlayerClassData data = PlayerClassDataProvider.get(player);
            int classLevel = data.state.totalLevel();
            double factor = potionFactor(player);
            sumBase += classLevel;
            sum += classLevel * factor;
            sumFactor += factor;
            count++;
        }
        DifficultySettings.Mode mode = DifficultySettings.mode();
        if (mode == DifficultySettings.Mode.PLAYERS) {
            if (count == 0) return Result.NONE;
            return new Result(clamp(sum / count), count, clamp(sumBase / count));
        }
        double distance = distanceThreat(level, pos);
        double meanFactor = count == 0 ? 1.0 : sumFactor / count;
        if (mode == DifficultySettings.Mode.DISTANCE) {
            return new Result(clamp(distance * meanFactor), count, clamp(distance));
        }
        double weight = ServerConfig.DIFFICULTY_MIXED_PLAYER_WEIGHT.get();
        double playersPotion = count == 0 ? 0.0 : sum / count;
        double playersBase = count == 0 ? 0.0 : sumBase / count;
        return new Result(clamp(weight * playersPotion + (1.0 - weight) * distance * meanFactor), count,
                clamp(weight * playersBase + (1.0 - weight) * distance));
    }

    /** T por distancia horizontal al spawn del mundo (Overworld), con las coordenadas de cada dimensión a escala (Nether x8). */
    public static double distanceThreat(ServerLevel level, Vec3 pos) {
        double scale = level.dimensionType().coordinateScale();
        BlockPos spawn = level.getServer().overworld().getSharedSpawnPos();
        double dx = pos.x * scale - spawn.getX();
        double dz = pos.z * scale - spawn.getZ();
        double blocks = Math.sqrt(dx * dx + dz * dz);
        double t = (blocks - ServerConfig.DIFFICULTY_DISTANCE_START.get()) / ServerConfig.DIFFICULTY_DISTANCE_PER_T.get();
        return Math.max(0.0, Math.min(ServerConfig.DIFFICULTY_DISTANCE_MAX.get(), t));
    }

    /**
     * Pociones del jugador: cada nivel de Tranquilidad resta {@code tranquilityStep} (20 %) y cada nivel de
     * Desafío suma {@code challengeStep} (25 %) a su nivel de clase. Actúa sobre la parte de ese jugador en la
     * media, así que en grupo un solo bebedor no cambia por sí solo toda la zona.
     */
    static double potionFactor(ServerPlayer player) {
        MobEffectInstance calm = player.getEffect(ModEffects.TRANQUILITY.get());
        MobEffectInstance challenge = player.getEffect(ModEffects.CHALLENGE.get());
        int calmLevels = calm == null ? 0 : calm.getAmplifier() + 1;
        int challengeLevels = challenge == null ? 0 : challenge.getAmplifier() + 1;
        return Math.max(0.0, 1.0 + ServerConfig.DIFFICULTY_CHALLENGE_STEP.get() * challengeLevels
                - ServerConfig.DIFFICULTY_TRANQUILITY_STEP.get() * calmLevels);
    }

    private static double clamp(double t) {
        return Math.max(0.0, Math.min(maxThreat(), t));
    }
}
