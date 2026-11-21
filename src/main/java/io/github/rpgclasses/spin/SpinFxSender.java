package io.github.rpgclasses.spin;

import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * Efectos de cliente del Maestro del Giro, mandados desde el servidor: temblor (lanzador y golpeados), onomatopeyas
 * flotantes (las ven todos los cercanos), destello de los golpeados por la ulti y de la Captura Perfecta. La cámara de la
 * ulti va aparte (Packets.SpinCinematic). Todo se dibuja como capas de interfaz o geometría: funciona con shaders.
 */
public final class SpinFxSender {
    /** Tipos (el 2 era la cinemática antigua de la Espiral Final; no se reutiliza). */
    public static final int SHAKE = 0, WORD = 1, CINEMATIC_HIT = 3, PERFECT = 4;
    /** Onomatopeyas (índices de SpinClient.WORDS). */
    public static final int WORD_GYARU = 0, WORD_BAKYUN = 1, WORD_DOGOON = 2, WORD_GOGOGO = 3, WORD_PASHI = 4;
    private static final double WORD_RANGE = 48;

    private SpinFxSender() {}

    public static void shake(ServerPlayer player, float strength) {
        Net.toPlayer(player, new Packets.SpinFx(SHAKE, 0, 0, 0, strength));
    }

    public static void onomatopoeia(ServerLevel level, Vec3 at, int word) {
        Net.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(at.x, at.y, at.z, WORD_RANGE, level.dimension())),
                new Packets.SpinFx(WORD, at.x, at.y, at.z, word));
    }

    public static void cinematicHit(ServerPlayer hit, int ticks) {
        Net.toPlayer(hit, new Packets.SpinFx(CINEMATIC_HIT, 0, 0, 0, ticks));
    }

    public static void perfect(ServerPlayer player) {
        Net.toPlayer(player, new Packets.SpinFx(PERFECT, 0, 0, 0, 1));
    }
}
