package io.github.ironrituals.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Configuración por servidor (plan, 2.7). Se irá ampliando con cada fase. */
public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue CORE_OWNER_ONLY;
    public static final ForgeConfigSpec.BooleanValue CORE_BREAK_PROTECTION;
    public static final ForgeConfigSpec.BooleanValue OPS_BYPASS;
    public static final ForgeConfigSpec.BooleanValue SCOREBOARD_TEAMS_ARE_ALLIES;

    public static final ForgeConfigSpec.IntValue CHANNEL_BASE_SECONDS;
    public static final ForgeConfigSpec.IntValue CHANNEL_PER_LEVEL_SECONDS;
    public static final ForgeConfigSpec.DoubleValue CASTER_MAX_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue MANA_COST_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue CREATIVE_FREE_MANA;
    public static final ForgeConfigSpec.DoubleValue DURATION_SCALE_CAP;
    public static final ForgeConfigSpec.DoubleValue TOME_LOOT_CHANCE_MULTIPLIER;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Núcleo del Ritual: propiedad y permisos (sección 2.4)").push("core");
        CORE_OWNER_ONLY = b
                .comment("Solo el propietario y sus aliados pueden abrir el núcleo, activarlo y retirar el libro.")
                .define("ownerOnlyAccess", true);
        CORE_BREAK_PROTECTION = b
                .comment("Solo el propietario y sus aliados pueden romper el núcleo.")
                .define("breakProtection", true);
        OPS_BYPASS = b
                .comment("Los operadores (nivel de permiso 2+) ignoran la propiedad de los núcleos.")
                .define("opsBypass", true);
        b.pop();

        b.comment("Aliados (sección 2.4)").push("allies");
        SCOREBOARD_TEAMS_ARE_ALLIES = b
                .comment("Los miembros del mismo equipo vanilla (/team) cuentan como aliados.")
                .define("scoreboardTeams", true);
        b.pop();

        b.comment("Ejecución de rituales (secciones 2.2 y 2.3)").push("rituals");
        CHANNEL_BASE_SECONDS = b
                .comment("Segundos base de canalización (fórmula: base + porNivel × nivel).")
                .defineInRange("channelBaseSeconds", 10, 0, 600);
        CHANNEL_PER_LEVEL_SECONDS = b
                .comment("Segundos de canalización extra por nivel del ritual.")
                .defineInRange("channelPerLevelSeconds", 5, 0, 600);
        CASTER_MAX_DISTANCE = b
                .comment("Distancia máxima del lanzador al núcleo durante la canalización.")
                .defineInRange("casterMaxDistance", 8.0, 1.0, 64.0);
        MANA_COST_MULTIPLIER = b
                .comment("Multiplicador de todos los costes de maná de los rituales.")
                .defineInRange("manaCostMultiplier", 1.0, 0.0, 100.0);
        CREATIVE_FREE_MANA = b
                .comment("Los jugadores en creativo no gastan maná.")
                .define("creativeFreeMana", true);
        DURATION_SCALE_CAP = b
                .comment("Tope del multiplicador de duración por poder de hechizo.")
                .defineInRange("durationScaleCap", 1.5, 1.0, 10.0);
        b.pop();

        b.comment("Grimorios en cofres (sección 3.D)").push("loot");
        TOME_LOOT_CHANCE_MULTIPLIER = b
                .comment("Multiplicador de la probabilidad de encontrar grimorios en cofres de ISS.")
                .defineInRange("tomeChanceMultiplier", 1.0, 0.0, 100.0);
        b.pop();

        SPEC = b.build();
    }

    private ServerConfig() {}
}
