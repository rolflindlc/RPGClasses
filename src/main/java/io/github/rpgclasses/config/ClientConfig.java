package io.github.rpgclasses.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Configuración de cliente (cada jugador la suya, en config/rpgclasses-client.toml). Solo efectos visuales. */
public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue TITAN_SHAKE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("effects");
        TITAN_SHAKE = b
                .comment("Intensidad del temblor de pantalla de los pisotones del Coloso Titán (0 = sin temblor, 1 = normal).")
                .defineInRange("titanShake", 1.0, 0.0, 1.0);
        b.pop();
        SPEC = b.build();
    }

    /** Valor de la opción, o el normal si la configuración aún no está cargada. */
    public static double titanShake() {
        try {
            return TITAN_SHAKE.get();
        } catch (IllegalStateException e) {
            return 1.0;
        }
    }

    private ClientConfig() {}
}
