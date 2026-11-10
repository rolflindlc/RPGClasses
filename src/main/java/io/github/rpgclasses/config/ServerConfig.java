package io.github.rpgclasses.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** Configuración de servidor (se sincroniza sola a los clientes). El balance NO va aquí: va en el datapack. */
public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue XP_COST_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue OUT_OF_COMBAT_SECONDS;
    public static final ForgeConfigSpec.BooleanValue PROTECT_UNCLASSED;
    public static final ForgeConfigSpec.DoubleValue BASE_CRIT_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLED_CLASSES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("progression");
        XP_COST_MULTIPLIER = b
                .comment("Multiplicador del coste en XP de cada nivel de clase (coste = (80 + 6n) x multiplicador).")
                .defineInRange("xpCostMultiplier", 1.0, 0.0, 100.0);
        OUT_OF_COMBAT_SECONDS = b
                .comment("Segundos sin dar ni recibir daño para considerarse fuera de combate (lo exige el respec).")
                .defineInRange("outOfCombatSeconds", 10, 0, 600);
        PROTECT_UNCLASSED = b
                .comment("Si es true, un jugador que aún no ha elegido clase no recibe daño.")
                .define("protectUnclassed", true);
        DISABLED_CLASSES = b
                .comment("Clases que no se pueden elegir en este servidor, p. ej. [\"rpgclasses:mage\"].")
                .defineListAllowEmpty(List.of("disabledClasses"), () -> List.<String>of(), o -> o instanceof String);
        b.pop();
        b.push("combat");
        BASE_CRIT_CHANCE = b
                .comment("Probabilidad base de crítico para todas las clases [MEDIR]. Cada clase suma su crit_chance_bonus.")
                .defineInRange("baseCritChance", 0.30, 0.0, 1.0);
        b.pop();
        SPEC = b.build();
    }

    private ServerConfig() {}
}
