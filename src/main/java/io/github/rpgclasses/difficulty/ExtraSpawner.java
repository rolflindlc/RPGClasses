package io.github.rpgclasses.difficulty;

import io.github.rpgclasses.config.ServerConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;

/** Mobs extra con el mismo T que el original, con presupuesto por tick y tope de mobs hostiles cercanos. */
final class ExtraSpawner {
    private static final int MAX_PER_ORIGIN = 3;
    private static final int PLACEMENT_ATTEMPTS = 4;

    private ExtraSpawner() {}

    /** Mobs extra esperados por cada mob original (partes decimales por sorteo). */
    static double expected(double threat, int players) {
        double perPlayer = Math.min(ServerConfig.DIFFICULTY_SPAWN_PLAYER_CAP.get(),
                ServerConfig.DIFFICULTY_SPAWN_PER_PLAYER.get() * Math.max(0, players - 1));
        return (ServerConfig.DIFFICULTY_SPAWN_PER_T.get() * threat + perPlayer) * DifficultySettings.factor();
    }

    /** @return cuántos mobs extra se han creado, para que quien llama lleve la cuenta del presupuesto por tick. */
    static int spawn(ServerLevel level, Mob origin, ThreatService.Result zone, int budget) {
        if (budget <= 0) return 0;
        RandomSource random = origin.getRandom();
        double expected = expected(zone.threat(), zone.players());
        int wanted = (int) expected;
        if (random.nextDouble() < expected - wanted) wanted++;
        wanted = Math.min(wanted, Math.min(MAX_PER_ORIGIN, budget));
        if (wanted <= 0) return 0;

        // Solo se consulta el entorno si de verdad hay que crear algo.
        int nearby = level.getEntitiesOfClass(Monster.class, origin.getBoundingBox().inflate(32), Entity::isAlive).size();
        if (nearby >= ServerConfig.DIFFICULTY_LOCAL_MOB_CAP.get()) return 0;

        int created = 0;
        for (int i = 0; i < wanted; i++) {
            Entity entity = origin.getType().create(level);
            if (!(entity instanceof Mob extra)) continue;
            if (!place(level, origin, extra, random)) {
                extra.discard();
                continue;
            }
            // Con el evento de Forge, como una aparición natural: otros mods que ajustan o vetan apariciones también ven
            // a los extras. Nuestro propio FinalizeSpawn los marca, pero onJoin los salta porque ya llevan su T.
            net.minecraftforge.event.ForgeEventFactory.onFinalizeSpawn(extra, level, level.getCurrentDifficultyAt(extra.blockPosition()),
                    MobSpawnType.NATURAL, null, null);
            if (extra.isSpawnCancelled()) {
                extra.discard();
                continue;
            }
            DifficultyEvents.markAndApply(extra, zone);
            level.addFreshEntityWithPassengers(extra);
            created++;
        }
        return created;
    }

    /** Cerca del original, sin colisión, sobre suelo y fuera de las zonas protegidas. */
    private static boolean place(ServerLevel level, Mob origin, Mob extra, RandomSource random) {
        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
            double x = origin.getX() + (random.nextDouble() - 0.5) * 6.0;
            double z = origin.getZ() + (random.nextDouble() - 0.5) * 6.0;
            extra.moveTo(x, origin.getY(), z, random.nextFloat() * 360.0f, 0.0f);
            if (!level.noCollision(extra) || level.getBlockState(extra.blockPosition().below()).isAir()) continue;
            if (ExemptionRules.isInSafeZone(level, extra.position())) continue;
            return true;
        }
        return false;
    }
}
