package io.github.rpgclasses.difficulty;

import io.github.rpgclasses.config.ServerConfig;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import net.minecraft.Util;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Amenaza T de una zona: media del nivel de clase (0-40) de los jugadores válidos cercanos.
 * Es una función pura de la posición y de los jugadores conectados; no guarda estado.
 */
public final class ThreatService {
    public static final double MAX_THREAT = 40.0;

    /** @param threat media de niveles (0 si no hay jugadores válidos); @param players jugadores que cuentan. */
    public record Result(double threat, int players) {
        public static final Result NONE = new Result(0.0, 0);
    }

    private ThreatService() {}

    /** Un jugador cuenta si tiene clase, no está en creativo ni espectador y no lleva demasiado inactivo. */
    public static boolean counts(ServerPlayer player, long nowMillis) {
        if (player.isCreative() || player.isSpectator()) return false;
        int idleMinutes = ServerConfig.DIFFICULTY_IDLE_MINUTES.get();
        if (idleMinutes > 0 && nowMillis - player.getLastActionTime() > idleMinutes * 60_000L) return false;
        PlayerClassData data = PlayerClassDataProvider.get(player);
        return data != null && data.state.classId != null;
    }

    public static Result compute(ServerLevel level, Vec3 pos) {
        double radius = ServerConfig.DIFFICULTY_RADIUS.get();
        double radiusSq = radius * radius;
        long now = Util.getMillis();
        int count = 0;
        double sum = 0;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) > radiusSq || !counts(player, now)) continue;
            PlayerClassData data = PlayerClassDataProvider.get(player);
            sum += data.state.totalLevel();
            count++;
        }
        if (count == 0) return Result.NONE;
        return new Result(Math.max(0.0, Math.min(MAX_THREAT, sum / count)), count);
    }
}
