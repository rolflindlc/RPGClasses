package io.github.rpgclasses.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Configuración de cliente (cada jugador la suya, en config/rites_and_paths-client.toml). Solo efectos visuales. */
public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue TITAN_SHAKE;
    public static final ForgeConfigSpec.DoubleValue SPIN_SHAKE;
    public static final ForgeConfigSpec.BooleanValue SPIN_CINEMATIC;

    /** Elementos del HUD que se pueden mover u ocultar (editor en el juego o a mano en la sección [hud]). */
    public static final String[] HUD_IDS = {
            "abilities", "stealth", "monk_charges", "spin_bar", "mitosis_merge", "wolf_jump", "centaur_jump",
            "druid_cast", "druid_form", "bard_rhythm", "engineer_reload", "engineer_ammo", "artificer_charges"};
    public static final java.util.Map<String, ForgeConfigSpec.IntValue> HUD_X = new java.util.LinkedHashMap<>();
    public static final java.util.Map<String, ForgeConfigSpec.IntValue> HUD_Y = new java.util.LinkedHashMap<>();
    public static final java.util.Map<String, ForgeConfigSpec.BooleanValue> HUD_HIDDEN = new java.util.LinkedHashMap<>();

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
        b.comment("Posición de cada elemento del HUD: desplazamiento en píxeles de la GUI desde su sitio normal (x a la derecha,",
                "y hacia abajo) y si se oculta. Se cambia más cómodo con el editor del HUD (tecla en Controles > Rites & Paths).",
                "En su sitio normal los elementos se apilan solos encima de la vida, la armadura (Improved Armor incluido) y la comida.")
                .push("hud");
        for (String id : HUD_IDS) {
            b.push(id);
            HUD_X.put(id, b.defineInRange("x", 0, -4000, 4000));
            HUD_Y.put(id, b.defineInRange("y", 0, -4000, 4000));
            HUD_HIDDEN.put(id, b.define("hidden", false));
            b.pop();
        }
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
