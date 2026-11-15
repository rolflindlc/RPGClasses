package io.github.ironrituals.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Configuración por servidor (plan, 2.7). Se irá ampliando con cada fase. */
public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue CORE_OWNER_ONLY;
    public static final ForgeConfigSpec.BooleanValue CORE_BREAK_PROTECTION;
    public static final ForgeConfigSpec.BooleanValue OPS_BYPASS;
    public static final ForgeConfigSpec.BooleanValue SCOREBOARD_TEAMS_ARE_ALLIES;

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

        SPEC = b.build();
    }

    private ServerConfig() {}
}
