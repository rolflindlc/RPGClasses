package io.github.rpgclasses.difficulty;

import io.github.rpgclasses.config.ServerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** Diagnóstico de la dificultad en el punto donde está quien ejecuta /clases dificultad (texto de administración, sin traducir). */
public final class DifficultyInfo {
    private DifficultyInfo() {}

    public static int show(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        ThreatService.Result zone = ThreatService.compute(level, pos);
        boolean safe = ExemptionRules.isInSafeZone(level, pos);
        double t = zone.threat();
        send(source, "Dificultad por zona: " + (ServerConfig.DIFFICULTY_ENABLED.get() ? "activada" : "DESACTIVADA (difficulty.enabled)"));
        send(source, String.format(Locale.ROOT, "Amenaza T aquí: %.1f (cuentan %d jugadores en %d bloques)", t, zone.players(), ServerConfig.DIFFICULTY_RADIUS.get()));
        if (Math.abs(zone.baseThreat() - t) > 0.05) {
            send(source, String.format(Locale.ROOT, "Sin pociones de Tranquilidad ni de Desafío sería T = %.1f.", zone.baseThreat()));
        }
        send(source, "Zona protegida (punto de reaparición a menos de " + ServerConfig.DIFFICULTY_SAFE_RADIUS.get() + " bloques): " + (safe ? "sí, los mobs aquí no reciben dificultad" : "no"));
        send(source, "Los mobs con más de " + ServerConfig.DIFFICULTY_HEALTH_EXEMPT.get() + " de vida base y los del tag difficulty_exempt quedan siempre fuera.");
        if (t < ServerConfig.DIFFICULTY_MIN_THREAT.get()) {
            send(source, String.format(Locale.ROOT, "T por debajo de %.0f: todo vanilla.", ServerConfig.DIFFICULTY_MIN_THREAT.get()));
            return 1;
        }
        double[] w = GearRoller.weightsFor(t);
        double total = 0;
        for (double v : w) total += v;
        double slot = Math.min(ServerConfig.DIFFICULTY_EQUIP_MAX.get(),
                ServerConfig.DIFFICULTY_EQUIP_BASE.get() + ServerConfig.DIFFICULTY_EQUIP_PER_T.get() * t);
        send(source, String.format(Locale.ROOT, "Equipo: %.0f %% por ranura, netherita %.1f %% de las piezas. Vida x%.2f, daño x%.2f.",
                slot * 100, total > 0 ? w[5] / total * 100 : 0.0,
                1 + ServerConfig.DIFFICULTY_HEALTH_PER_T.get() * t, 1 + ServerConfig.DIFFICULTY_DAMAGE_PER_T.get() * t));
        send(source, String.format(Locale.ROOT, "Mobs extra: +%.2f por mob natural. Saqueo efectivo: +%d.",
                ExtraSpawner.expected(t, zone.players()),
                Math.min(ServerConfig.DIFFICULTY_LOOT_MAX.get(), (int) (t / ServerConfig.DIFFICULTY_LOOT_STEP.get()))));
        double full = GearRoller.fullNetheriteChance(t);
        if (full > 0) send(source, String.format(Locale.ROOT, "Set completo de netherita encantada: %.1f %% de los mobs con armadura.", full * 100));
        int traits = EliteTraits.traitCount(t);
        if (traits > 0 && ServerConfig.DIFFICULTY_TRAITS.get()) {
            send(source, String.format(Locale.ROOT, "Mobs de élite: %.0f %% de los mobs, con %d rasgo(s) cada uno (regeneración, fuego, vampirismo, lentitud, explosión).",
                    EliteTraits.eliteChance(t) * 100, traits));
        }
        return 1;
    }

    private static void send(CommandSourceStack source, String line) {
        source.sendSuccess(() -> Component.literal(line), false);
    }
}
