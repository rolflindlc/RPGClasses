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
