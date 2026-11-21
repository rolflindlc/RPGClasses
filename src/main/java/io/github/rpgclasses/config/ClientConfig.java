package io.github.rpgclasses.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Configuración de cliente (cada jugador la suya, en config/rites_and_paths-client.toml). Solo efectos visuales. */
public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue TITAN_SHAKE;
    public static final ForgeConfigSpec.DoubleValue SPIN_SHAKE;
    public static final ForgeConfigSpec.BooleanValue SPIN_CINEMATIC;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("effects");
        TITAN_SHAKE = b
                .comment("Intensidad del temblor de pantalla de los pisotones del Coloso Titán (0 = sin temblor, 1 = normal).")
                .defineInRange("titanShake", 1.0, 0.0, 1.0);
        SPIN_SHAKE = b
                .comment("Intensidad del temblor de los impactos de las bolas del Maestro del Giro (0 = sin temblor, 1 = normal).")
                .defineInRange("spinShake", 1.0, 0.0, 1.0);
        SPIN_CINEMATIC = b
                .comment("Cámara de cine del Rectángulo Dorado (planos, bandas, rectángulo áureo y destellos). Con false la secuencia sigue igual, pero con la cámara normal.")
                .define("spinCinematic", true);
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

    public static double spinShake() {
        try {
            return SPIN_SHAKE.get();
        } catch (IllegalStateException e) {
            return 1.0;
        }
    }

    public static boolean spinCinematic() {
        try {
            return SPIN_CINEMATIC.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private ClientConfig() {}
}
