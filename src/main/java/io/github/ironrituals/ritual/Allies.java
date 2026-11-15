package io.github.ironrituals.ritual;

import io.github.ironrituals.config.ServerConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Relación de aliados del mod (plan, 2.4). Siempre se mira desde el propietario del ritual o del núcleo.
 * <ul>
 *   <li>El propietario es aliado de sí mismo.</li>
 *   <li>Si el propietario está conectado se usa {@link Entity#isAlliedTo(Entity)}: cubre los equipos vanilla y
 *       cualquier mod que lo amplíe (el mod de Razas y Clases lo hace con su sistema de aliados, sin que este
 *       mod dependa de él).</li>
 *   <li>Si está desconectado, cuenta el equipo vanilla guardado en el scoreboard.</li>
 *   <li>Las mascotas cuentan como su dueño.</li>
 * </ul>
 * FTB Teams se añadirá como integración opcional en una fase posterior.
 */
public final class Allies {
    private Allies() {}

    /** ¿Considera el propietario {@code owner} aliada a la entidad {@code other}? */
    public static boolean isAlly(MinecraftServer server, @Nullable UUID owner, String ownerName, Entity other) {
        if (owner == null) return true;
        UUID otherId = effectiveOwner(other);
        if (owner.equals(otherId)) return true;

        ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(owner);
        if (ownerPlayer != null) {
            if (ownerPlayer.isAlliedTo(other)) return true;
            if (otherId != null && !otherId.equals(other.getUUID())) {
                ServerPlayer otherOwner = server.getPlayerList().getPlayer(otherId);
                if (otherOwner != null && ownerPlayer.isAlliedTo(otherOwner)) return true;
            }
        }

        if (ServerConfig.SCOREBOARD_TEAMS_ARE_ALLIES.get() && !ownerName.isEmpty()) {
            PlayerTeam ownerTeam = server.getScoreboard().getPlayersTeam(ownerName);
            if (ownerTeam != null) {
                Entity teamSubject = other;
                if (otherId != null && !otherId.equals(other.getUUID())) {
                    ServerPlayer otherOwner = server.getPlayerList().getPlayer(otherId);
                    if (otherOwner != null) teamSubject = otherOwner;
                }
                if (ownerTeam.equals(teamSubject.getTeam())) return true;
            }
        }
        return false;
    }

    /** Acceso al núcleo u otro elemento con propietario: propietario, aliados u operadores (según config). */
    public static boolean canAccess(Player player, @Nullable UUID owner, String ownerName) {
        if (owner == null || player.getServer() == null) return true;
        if (ServerConfig.OPS_BYPASS.get() && player.hasPermissions(2)) return true;
        return isAlly(player.getServer(), owner, ownerName, player);
    }

    /** UUID del jugador que "representa" a la entidad: él mismo si es jugador, su dueño si es mascota. */
    @Nullable
    public static UUID effectiveOwner(Entity e) {
        if (e instanceof Player p) return p.getUUID();
        if (e instanceof OwnableEntity own && own.getOwnerUUID() != null) return own.getOwnerUUID();
        return e.getUUID();
    }
}
