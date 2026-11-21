package io.github.rpgclasses.core;

import java.util.Set;

/**
 * Identificadores conocidos por el núcleo. Lógica pura: sin dependencias de Minecraft.
 * Cuando existan los registros de Forge (fase 2+), estos conjuntos se alimentarán de ellos.
 */
public final class Ids {
    public static final String NS = "rites_and_paths";

    public static final String MAGIC = NS + ":magic";
    public static final String BOW = NS + ":bow";
    public static final String HEAVY = NS + ":heavy";
    public static final String LIGHT = NS + ":light";
    public static final String ARMOR = NS + ":armor";
    public static final String SHIELD = NS + ":shield";
    /** Esferas (adenda del Maestro del Giro): las bolas del Maestro del Giro. Las demás clases tienen un muro fijo del 100 %. */
    public static final String SPHERES = NS + ":spheres";

    public static final Set<String> DISCIPLINES = Set.of(MAGIC, BOW, HEAVY, LIGHT, ARMOR, SHIELD, SPHERES);

    /** Modificadores de atributo que escalan con los puntos. */
    public static final String EFFECT_ATTRIBUTE = NS + ":attribute";
    /** Furia de Heridas del Bárbaro. */
    public static final String EFFECT_WOUND_FURY = NS + ":wound_fury";
    /** Multiplica la armadura/dureza que aportan las piezas equipadas (Coraza). */
    public static final String EFFECT_EQUIPPED_ARMOR = NS + ":equipped_armor_multiplier";
    /** Mecánica con código propio en content/, identificada por su campo "id". */
    public static final String EFFECT_MECHANIC = NS + ":mechanic";

    public static final Set<String> EFFECT_TYPES =
            Set.of(EFFECT_ATTRIBUTE, EFFECT_WOUND_FURY, EFFECT_EQUIPPED_ARMOR, EFFECT_MECHANIC);

    public static final Set<String> CONDITIONS = Set.of(NS + ":no_armor");

    /** Clave de idioma por convención: "rites_and_paths:mage" + "class" + "name" -> class.rites_and_paths.mage.name */
    public static String langKey(String kind, String id, String suffix) {
        return kind + "." + id.replace(':', '.').replace('/', '.') + "." + suffix;
    }

    private Ids() {}
}
