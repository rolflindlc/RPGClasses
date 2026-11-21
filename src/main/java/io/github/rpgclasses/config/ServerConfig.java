package io.github.rpgclasses.config;

import io.github.rpgclasses.difficulty.EliteTraits.Trait;
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

    // Maestro del Giro (adenda del Maestro del Giro): bolas que tocan el mundo.
    public static final ForgeConfigSpec.BooleanValue SPHERE_REDSTONE;
    public static final ForgeConfigSpec.BooleanValue SPHERE_STALACTITE_BREAK;
    public static final ForgeConfigSpec.BooleanValue SPHERE_FRICTION_IGNITE;

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
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_TRANQUILITY_STEP;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_CHALLENGE_STEP;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_MAX_THREAT;
    public static final ForgeConfigSpec.IntValue DIFFICULTY_LOOT_MAX;
    public static final ForgeConfigSpec.IntValue DIFFICULTY_ENCHANT_MAX_LEVEL;
    public static final ForgeConfigSpec.EnumValue<io.github.rpgclasses.difficulty.DifficultySettings.Mode> DIFFICULTY_MODE;
    public static final ForgeConfigSpec.EnumValue<io.github.rpgclasses.difficulty.DifficultySettings.Intensity> DIFFICULTY_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_DISTANCE_START;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_DISTANCE_PER_T;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_DISTANCE_MAX;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_MIXED_PLAYER_WEIGHT;
    public static final ForgeConfigSpec.BooleanValue DIFFICULTY_TRAITS;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_FULL_START;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_FULL_REF_THREAT;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_FULL_REF_CHANCE;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_ELITE_START;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_ELITE_BASE;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_ELITE_PER_T;
    public static final ForgeConfigSpec.DoubleValue DIFFICULTY_ELITE_TRAIT_STEP;
    public static final ForgeConfigSpec.IntValue DIFFICULTY_ELITE_MAX_TRAITS;
    public static final ForgeConfigSpec.BooleanValue DIFFICULTY_TRAIT_PARTICLES;
    /** Por rasgo: activado y amenaza T mínima a partir de la cual puede salir (difficulty.traits.<id>). */
    public static final java.util.Map<io.github.rpgclasses.difficulty.EliteTraits.Trait, ForgeConfigSpec.BooleanValue> TRAIT_ENABLED =
            new java.util.EnumMap<>(io.github.rpgclasses.difficulty.EliteTraits.Trait.class);
    public static final java.util.Map<io.github.rpgclasses.difficulty.EliteTraits.Trait, ForgeConfigSpec.DoubleValue> TRAIT_UNLOCK =
            new java.util.EnumMap<>(io.github.rpgclasses.difficulty.EliteTraits.Trait.class);
    public static final ForgeConfigSpec.DoubleValue TRAIT_SWIFT_SPEED;
    public static final ForgeConfigSpec.IntValue TRAIT_POISON_SECONDS;
    public static final ForgeConfigSpec.IntValue TRAIT_IGNEOUS_SECONDS;
    public static final ForgeConfigSpec.DoubleValue TRAIT_VAMPIRIC_HEAL;
    public static final ForgeConfigSpec.IntValue TRAIT_CRIPPLING_SECONDS;
    public static final ForgeConfigSpec.DoubleValue TRAIT_VOLATILE_POWER;
    public static final ForgeConfigSpec.DoubleValue TRAIT_TELEPORT_CHANCE;
    public static final ForgeConfigSpec.DoubleValue TRAIT_TELEPORT_MIN_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue TRAIT_TELEPORT_MAX_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue TRAIT_DEFLECT_SPEED;
    public static final ForgeConfigSpec.DoubleValue TRAIT_COLOSSUS_SCALE;
    public static final ForgeConfigSpec.DoubleValue TRAIT_COLOSSUS_HEALTH;
    public static final ForgeConfigSpec.DoubleValue TRAIT_COLOSSUS_SLOW;
    public static final ForgeConfigSpec.IntValue TRAIT_COLOSSUS_SPAWNS;

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
        b.comment("Maestro del Giro: lo que sus bolas pueden hacer en el mundo. Siempre respetan las protecciones de terreno.").push("sphere");
        b.push("redstone");
        SPHERE_REDSTONE = b
                .comment("La bola de redstone activa botones, palancas y dianas y hace que el bloque golpeado dé señal un instante.")
                .define("enabled", true);
        b.pop();
        b.push("stalactite");
        SPHERE_STALACTITE_BREAK = b
                .comment("La bola de estalactita rompe bloques blandos (tag rites_and_paths:stalactite_breakable) y te trae lo que sueltan.")
                .define("breakBlocks", true);
        b.pop();
        b.push("friction");
        SPHERE_FRICTION_IGNITE = b
                .comment("El encantamiento Fricción prende fuego a los bloques inflamables que golpea la bola (también respeta mobGriefing).")
                .define("igniteBlocks", true);
        b.pop();
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
        DIFFICULTY_TRANQUILITY_STEP = b
                .comment("Poción de Tranquilidad: cada nivel del efecto resta esta fracción al nivel de clase del jugador al calcular T (0,2 = 20 % por nivel).")
                .defineInRange("tranquilityStep", 0.20, 0.0, 1.0);
        DIFFICULTY_CHALLENGE_STEP = b
                .comment("Poción de Desafío: cada nivel del efecto suma esta fracción al nivel de clase del jugador al calcular T (0,25 = 25 % por nivel).")
                .defineInRange("challengeStep", 0.25, 0.0, 5.0);
        DIFFICULTY_MAX_THREAT = b
                .comment("Tope de la amenaza T. Los jugadores llegan a 40 como mucho; por encima de 40 solo se llega con pociones de Desafío",
                        "(con la poción de nivel IV y todos los jugadores en nivel 40, T = 80).")
                .defineInRange("maxThreat", 80.0, 40.0, 1000.0);
        DIFFICULTY_LOOT_MAX = b
                .comment("Tope del Saqueo efectivo extra por la dificultad.")
                .defineInRange("lootingMaxBonus", 3, 0, 20);
        DIFFICULTY_ENCHANT_MAX_LEVEL = b
                .comment("Tope del nivel de encantamiento del equipo de los mobs (el nivel es 10 + T/2).")
                .defineInRange("enchantMaxLevel", 40, 1, 100);
        DIFFICULTY_MODE = b
                .comment("De qué depende la amenaza T: PLAYERS (nivel de clase de los jugadores cercanos), DISTANCE (solo la distancia al spawn del mundo)",
                        "o MIXED (media ponderada de los dos). Se cambia en la partida con /clases dificultad ajustes.")
                .defineEnum("mode", io.github.rpgclasses.difficulty.DifficultySettings.Mode.PLAYERS);
        DIFFICULTY_INTENSITY = b
                .comment("Cuánto pesan los incrementos de dificultad del mod: OFF los desactiva, EASY los divide por dos, NORMAL los deja como están planeados",
                        "y HARD los duplica (vida, daño, equipo, mobs extra, élites y set de netherita). Se cambia en la partida con /clases dificultad ajustes.")
                .defineEnum("intensity", io.github.rpgclasses.difficulty.DifficultySettings.Intensity.NORMAL);
        DIFFICULTY_DISTANCE_START = b
                .comment("Modos DISTANCE y MIXED: distancia al spawn del mundo (bloques) hasta la cual T es 0.")
                .defineInRange("distanceStart", 500.0, 0.0, 1.0E7);
        DIFFICULTY_DISTANCE_PER_T = b
                .comment("Modos DISTANCE y MIXED: bloques que hay que alejarse del spawn por cada punto de T (con 100: T = 10 a 1.500 bloques, 40 a 4.500).",
                        "En el Nether la distancia cuenta por 8, como las coordenadas.")
                .defineInRange("distancePerThreat", 100.0, 1.0, 1.0E6);
        DIFFICULTY_DISTANCE_MAX = b
                .comment("Tope de la T por distancia (40, como el nivel máximo de clase). Por encima solo se llega con pociones de Desafío.")
                .defineInRange("distanceMaxThreat", 40.0, 0.0, 1000.0);
        DIFFICULTY_MIXED_PLAYER_WEIGHT = b
                .comment("Modo MIXED: peso del nivel de los jugadores en la media (0,5 = mitad y mitad; el resto es la distancia).")
                .defineInRange("mixedPlayerWeight", 0.5, 0.0, 1.0);
        DIFFICULTY_TRAITS = b
                .comment("Palanca de rasgos de élite: efectos especiales de los mobs a partir de eliteStartThreat.")
                .define("scaleTraits", true);
        DIFFICULTY_FULL_START = b
                .comment("Amenaza T a partir de la cual un mob con armadura puede salir con el set completo de netherita, todo encantado.")
                .defineInRange("fullNetheriteStartThreat", 40.0, 0.0, 1000.0);
        DIFFICULTY_FULL_REF_THREAT = b
                .comment("Punto de referencia del set completo: con esta T (por defecto 80)...")
                .defineInRange("fullNetheriteReferenceThreat", 80.0, 1.0, 1000.0);
        DIFFICULTY_FULL_REF_CHANCE = b
                .comment("...la probabilidad de set completo de netherita encantada es esta (por defecto 0,30). Crece linealmente desde fullNetheriteStartThreat.")
                .defineInRange("fullNetheriteReferenceChance", 0.30, 0.0, 1.0);
        DIFFICULTY_ELITE_START = b
                .comment("Amenaza T a partir de la cual los mobs pueden ser de élite y llevar rasgos especiales. Por debajo, 0 %.")
                .defineInRange("eliteStartThreat", 10.0, 0.0, 1000.0);
        DIFFICULTY_ELITE_BASE = b
                .comment("Probabilidad de ser de élite con T = eliteStartThreat.")
                .defineInRange("eliteBaseChance", 0.05, 0.0, 1.0);
        DIFFICULTY_ELITE_PER_T = b
                .comment("Probabilidad de ser de élite que se suma por cada punto de T por encima de eliteStartThreat (0,0107 da 80 % con T = 80 partiendo de 5 % con T = 10).")
                .defineInRange("eliteChancePerThreat", 0.0107, 0.0, 1.0);
        DIFFICULTY_ELITE_TRAIT_STEP = b
                .comment("Un rasgo más cada tantos puntos de T por encima de eliteStartThreat (con 30: 1 rasgo con T = 10, 2 con 40, 3 con 70).")
                .defineInRange("eliteThreatPerTrait", 30.0, 1.0, 1000.0);
        DIFFICULTY_ELITE_MAX_TRAITS = b
                .comment("Máximo de rasgos de un mob de élite.")
                .defineInRange("eliteMaxTraits", 3, 1, 10);
        DIFFICULTY_TRAIT_PARTICLES = b
                .comment("Cada rasgo de élite suelta partículas propias para reconocerlo de lejos. Desactívalo si molesta o por rendimiento.")
                .define("traitParticles", true);
        b.comment("Un apartado por rasgo: enabled lo permite o no, unlockThreat es la T mínima para que pueda salir.").push("traits");
        traitHeader(b, Trait.REGENERATING); b.pop();
        traitHeader(b, Trait.IGNEOUS);
        TRAIT_IGNEOUS_SECONDS = b.comment("Segundos de fuego con cada golpe.").defineInRange("seconds", 5, 1, 120);
        b.pop();
        traitHeader(b, Trait.VAMPIRIC);
        TRAIT_VAMPIRIC_HEAL = b.comment("Fracción del daño causado que el mob se cura.").defineInRange("healFraction", 0.3, 0.0, 5.0);
        b.pop();
        traitHeader(b, Trait.CRIPPLING);
        TRAIT_CRIPPLING_SECONDS = b.comment("Segundos de Lentitud II y Debilidad I con cada golpe.").defineInRange("seconds", 3, 1, 120);
        b.pop();
        traitHeader(b, Trait.VOLATILE);
        TRAIT_VOLATILE_POWER = b.comment("Potencia de la explosión al morir (no rompe bloques; la de un creeper es 3).")
                .defineInRange("power", 2.5, 0.5, 10.0);
        b.pop();
        traitHeader(b, Trait.POISONOUS);
        TRAIT_POISON_SECONDS = b.comment("Segundos de Veneno con cada golpe.").defineInRange("seconds", 5, 1, 120);
        b.pop();
        traitHeader(b, Trait.SWIFT);
        TRAIT_SWIFT_SPEED = b.comment("Bonus de velocidad de movimiento (0,5 = +50 %).").defineInRange("speedBonus", 0.5, 0.0, 5.0);
        b.pop();
        traitHeader(b, Trait.TELEPORTING);
        TRAIT_TELEPORT_CHANCE = b.comment("Probabilidad por segundo de teletransportarse junto a su objetivo.")
                .defineInRange("chancePerSecond", 0.2, 0.0, 1.0);
        TRAIT_TELEPORT_MIN_DISTANCE = b.comment("Solo se teletransporta si el objetivo está al menos a esta distancia (bloques).")
                .defineInRange("minDistance", 6.0, 1.0, 64.0);
        TRAIT_TELEPORT_MAX_DISTANCE = b.comment("...y como mucho a esta distancia (bloques).")
                .defineInRange("maxDistance", 32.0, 2.0, 128.0);
        b.pop();
        traitHeader(b, Trait.DEFLECTING);
        TRAIT_DEFLECT_SPEED = b.comment("Fracción de la velocidad con la que rebota el proyectil (0,6 = vuelve al 60 %).")
                .defineInRange("bounceSpeed", 0.6, 0.0, 3.0);
        b.pop();
        traitHeader(b, Trait.COLOSSUS);
        TRAIT_COLOSSUS_SCALE = b.comment("Tamaño del mob (modelo y caja de colisión; 1,5 = 50 % más grande).")
                .defineInRange("scale", 1.5, 1.0, 4.0);
        TRAIT_COLOSSUS_HEALTH = b.comment("Vida extra como fracción de la base (0,5 = +50 %).")
                .defineInRange("healthBonus", 0.5, 0.0, 10.0);
        TRAIT_COLOSSUS_SLOW = b.comment("Velocidad de movimiento que pierde (0,25 = -25 %).")
                .defineInRange("speedPenalty", 0.25, 0.0, 0.9);
        TRAIT_COLOSSUS_SPAWNS = b.comment("Mobs iguales, sin rasgos, que suelta al morir.")
                .defineInRange("spawnsOnDeath", 3, 0, 10);
        b.pop();
        b.pop();
        b.pop();
        b.push("debug");
        DEBUG_LOG = b
                .comment("Registro de depuración en logs/rites_and_paths-debug.log: habilidades, pasivas, efectos, daño y curas.",
                        "Los errores se registran siempre. También se cambia en partida con /clases debug log on|off.",
                        "Apagado por defecto: activarlo cuesta rendimiento (escribe en disco y formatea cada golpe y cada pasiva).")
                .define("debugLog", false);
        b.pop();
        SPEC = b.build();
    }

    /** Abre el apartado del rasgo con sus dos opciones comunes; quien llama añade las suyas y hace b.pop(). */
    private static void traitHeader(ForgeConfigSpec.Builder b, Trait trait) {
        b.push(trait.id);
        TRAIT_ENABLED.put(trait, b.define("enabled", true));
        TRAIT_UNLOCK.put(trait, b.defineInRange("unlockThreat", trait.defaultUnlock, 0.0, 1000.0));
    }

    private ServerConfig() {}
}
