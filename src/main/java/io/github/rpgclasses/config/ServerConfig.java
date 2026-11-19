package io.github.rpgclasses.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** Configuración de servidor (se sincroniza sola a los clientes). El balance NO va aquí: va en el datapack. */
public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue XP_COST_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue OUT_OF_COMBAT_SECONDS;
    public static final ForgeConfigSpec.DoubleValue RESPEC_XP_LOSS;
    public static final ForgeConfigSpec.BooleanValue PROTECT_UNCLASSED;
    public static final ForgeConfigSpec.DoubleValue BASE_CRIT_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLED_CLASSES;
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOG;
    public static final ForgeConfigSpec.BooleanValue ALLY_FRIENDLY_FIRE;
    public static final ForgeConfigSpec.BooleanValue ALLY_SCOREBOARD_TEAMS;
    public static final ForgeConfigSpec.IntValue MAX_ALLIES;

    // Dificultad por zona (adenda de dificultad por zona). Todo es [MEDIR].
    public static final ForgeConfigSpec.BooleanValue DIFFICULTY_ENABLED;
    public static final ForgeConfigSpec.BooleanValue DIFFICULTY_GEAR;
    public static final ForgeConfigSpec.BooleanValue DIFFICULTY_STATS;
    public static final ForgeConfigSpec.BooleanValue DIFFICULTY_SPAWN;
    public static final ForgeConfigSpec.BooleanValue DIFFICULTY_LOOT;
    public static final ForgeConfigSpec.IntValue DIFFICULTY_RADIUS;
    public static final ForgeConfigSpec.IntValue DIFFICULTY_SAFE_RADIUS;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_HEALTH_EXEMPT;
    public static final ForgeConfigSpec.IntValue DIFFICULTY_IDLE_MINUTES;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_MIN_THREAT;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_HEALTH_PER_T;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_DAMAGE_PER_T;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_EQUIP_BASE;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_EQUIP_PER_T;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_EQUIP_MAX;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_NETHERITE_MAX;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_ENCHANT_START;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_ENCHANT_MAX_CHANCE;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_SPAWN_PER_T;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_SPAWN_PER_PLAYER;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_SPAWN_PLAYER_CAP;
    public static final ForgeConfigSpec.IntValue DIFFICULTY_EXTRA_PER_TICK;
    public static final ForgeConfigSpec.IntValue DIFFICULTY_LOCAL_MOB_CAP;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_LOOT_STEP;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_POTION_STEP;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("progression");
        XP_COST_MULTIPLIER = b
                .comment("Multiplicador del coste en XP de cada nivel de clase (coste = (80 + 6n) x multiplicador).")
                .defineInRange("xpCostMultiplier", 1.0, 0.0, 100.0);
        OUT_OF_COMBAT_SECONDS = b
                .comment("Segundos sin dar ni recibir daño para considerarse fuera de combate (lo exige el respec).")
                .defineInRange("outOfCombatSeconds", 10, 0, 600);
        RESPEC_XP_LOSS = b
                .comment("Fracción de la XP gastada que se pierde en cada reinicio de clase o subclase (0.33 = se devuelve el 67%).")
                .defineInRange("respecXpLoss", 0.33, 0.0, 1.0);
        PROTECT_UNCLASSED = b
                .comment("Si es true, un jugador que aún no ha elegido clase no recibe daño.")
                .define("protectUnclassed", true);
        DISABLED_CLASSES = b
                .comment("Clases que no se pueden elegir en este servidor, p. ej. [\"rites_and_paths:mage\"].")
                .defineListAllowEmpty(List.of("disabledClasses"), () -> List.<String>of(), o -> o instanceof String);
        b.pop();
        b.push("combat");
        BASE_CRIT_CHANCE = b
                .comment("Probabilidad base de crítico para todas las clases [MEDIR]. Cada clase suma su crit_chance_bonus.")
                .defineInRange("baseCritChance", 0.30, 0.0, 1.0);
        b.pop();
        b.push("allies");
        ALLY_FRIENDLY_FIRE = b
                .comment("Si es false, no puedes dañar a quien tienes como aliado (ni tus mascotas ni tus invocaciones).",
                        "Si es true, el daño directo entre aliados funciona como en vanilla; las áreas y efectos del mod siguen sin afectarles.")
                .define("friendlyFire", false);
        ALLY_SCOREBOARD_TEAMS = b
                .comment("Si es true, los jugadores de un mismo equipo de scoreboard (/team) cuentan además como aliados entre sí.")
                .define("scoreboardTeams", true);
        MAX_ALLIES = b
                .comment("Número máximo de aliados que puede marcar cada jugador.")
                .defineInRange("maxAllies", 32, 0, 1000);
        b.pop();
        b.push("difficulty");
        DIFFICULTY_ENABLED = b
                .comment("Interruptor general de la dificultad por zona: los mobs hostiles mejoran su equipo, su vida, su daño y su spawn",
                        "según el nivel de clase medio de los jugadores cercanos (amenaza T, de 0 a 40). Con false el sistema no hace nada.",
                        "Los mobs que ya aparecieron conservan lo que recibieron.")
                .define("enabled", true);
        DIFFICULTY_GEAR = b.comment("Palanca de equipo: armaduras y armas variadas según T.").define("scaleGear", true);
        DIFFICULTY_STATS = b.comment("Palanca de stats: vida máxima y daño de ataque según T.").define("scaleStats", true);
        DIFFICULTY_SPAWN = b.comment("Palanca de spawn: grupos de mobs más numerosos según T y según los jugadores de la zona.").define("scaleSpawn", true);
        DIFFICULTY_LOOT = b.comment("Recompensa: Saqueo efectivo extra al matar mobs escalados (la XP no cambia).").define("scaleLoot", true);
        DIFFICULTY_RADIUS = b
                .comment("Radio (bloques) en el que se buscan los jugadores que cuentan para calcular T.")
                .defineInRange("zoneRadius", 96, 16, 512);
        DIFFICULTY_SAFE_RADIUS = b
                .comment("Radio (bloques, solo en horizontal) alrededor del punto de reaparición de cada jugador (cama, ancla o spawn del mundo)",
                        "en el que los mobs NO reciben dificultad. 0 = sin zonas protegidas.")
                .defineInRange("safeRadius", 96, 0, 512);
        DIFFICULTY_HEALTH_EXEMPT = b
                .comment("Los mobs con más vida máxima base que esto (jefes y mobs grandes) no reciben dificultad.")
                .defineInRange("exemptAboveHealth", 80.0, 1.0, 100000.0);
        DIFFICULTY_IDLE_MINUTES = b
                .comment("Minutos sin actividad tras los que un jugador deja de contar para T. 0 = siempre cuenta.")
                .defineInRange("idleMinutes", 5, 0, 120);
        DIFFICULTY_MIN_THREAT = b
                .comment("Por debajo de esta amenaza T todo es vanilla.")
                .defineInRange("minThreat", 10.0, 0.0, 40.0);
        DIFFICULTY_HEALTH_PER_T = b
                .comment("Vida máxima del mob: x(1 + valor * T).")
                .defineInRange("healthPerThreat", 0.02, 0.0, 1.0);
        DIFFICULTY_DAMAGE_PER_T = b
                .comment("Daño de ataque del mob: x(1 + valor * T).")
                .defineInRange("damagePerThreat", 0.015, 0.0, 1.0);
        DIFFICULTY_EQUIP_BASE = b
                .comment("Probabilidad base de que una ranura de equipo reciba una pieza: base + porT * T, con tope en equipMax.")
                .defineInRange("equipBase", 0.05, 0.0, 1.0);
        DIFFICULTY_EQUIP_PER_T = b.defineInRange("equipPerThreat", 0.02, 0.0, 1.0);
        DIFFICULTY_EQUIP_MAX = b.defineInRange("equipMax", 0.9, 0.0, 1.0);
        DIFFICULTY_NETHERITE_MAX = b
                .comment("Tope de la probabilidad de netherita por pieza (solo aparece con T = 40; la netherita añadida por el sistema no se suelta).")
                .defineInRange("netheriteMaxChance", 0.20, 0.0, 1.0);
        DIFFICULTY_ENCHANT_START = b
                .comment("Amenaza T a partir de la cual el equipo puede salir encantado.")
                .defineInRange("enchantStartThreat", 20.0, 0.0, 40.0);
        DIFFICULTY_ENCHANT_MAX_CHANCE = b
                .comment("Probabilidad de pieza encantada con T = 40 (sube linealmente desde enchantStartThreat). El nivel es 10 + T/2.")
                .defineInRange("enchantMaxChance", 0.5, 0.0, 1.0);
        DIFFICULTY_SPAWN_PER_T = b
                .comment("Mobs extra por cada mob que aparece de forma natural: valor * T (por ejemplo 0,8 con T = 40).")
                .defineInRange("spawnPerThreat", 0.02, 0.0, 1.0);
        DIFFICULTY_SPAWN_PER_PLAYER = b
                .comment("Mobs extra por cada jugador adicional en la zona.")
                .defineInRange("spawnPerExtraPlayer", 0.10, 0.0, 1.0);
        DIFFICULTY_SPAWN_PLAYER_CAP = b
                .comment("Máximo de mobs extra por mob aportado por el número de jugadores.")
                .defineInRange("spawnPlayerCap", 0.5, 0.0, 5.0);
        DIFFICULTY_EXTRA_PER_TICK = b
                .comment("Presupuesto de rendimiento: máximo de mobs extra que se crean por tick en todo el servidor.")
                .defineInRange("maxExtraSpawnsPerTick", 6, 0, 1000);
        DIFFICULTY_LOCAL_MOB_CAP = b
                .comment("No se crean mobs extra si ya hay tantos mobs hostiles en 32 bloques alrededor.")
                .defineInRange("localMobCap", 80, 1, 10000);
        DIFFICULTY_LOOT_STEP = b
                .comment("Un nivel de Saqueo efectivo por cada tantos puntos de T (con 15: 0 a 2).")
                .defineInRange("lootingStep", 15.0, 1.0, 40.0);
        DIFFICULTY_POTION_STEP = b
                .comment("Pociones de Tranquilidad y de Desafío: cada nivel del efecto resta o suma esta fracción al nivel de clase del jugador al calcular T (0,2 = 20 % por nivel).")
                .defineInRange("potionStep", 0.20, 0.0, 1.0);
        b.pop();
        b.push("debug");
        DEBUG_LOG = b
                .comment("Registro de depuración en logs/rites_and_paths-debug.log: habilidades, pasivas, efectos, daño y curas.",
                        "Los errores se registran siempre. También se cambia en partida con /clases debug log on|off.")
                .define("debugLog", true);
        b.pop();
        SPEC = b.build();
    }

    private ServerConfig() {}
}
