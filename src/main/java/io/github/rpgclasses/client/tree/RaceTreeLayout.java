package io.github.rpgclasses.client.tree;

import io.github.rpgclasses.core.definition.Definitions.EffectDef;
import io.github.rpgclasses.core.definition.Definitions.RiteDef;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Posiciones del árbol de raza, en las mismas "unidades de árbol" que el de clase. De izquierda a derecha:
 * el núcleo (la raza) con sus rasgos de tronco, la subraza con sus rasgos de base y los tres ritos que suben
 * el Rango 1, 2 y 3, cada uno con las habilidades que se ganan con ese rango. Lógica pura (sin Minecraft).
 */
public final class RaceTreeLayout implements TreeCamera.Bounds {
    public enum Kind { CORE, SUB_KEY, RITE, GEM }

    /** Qué tipo de rasgo representa una gema. */
    public enum GemType { ATTRIBUTES, MECHANIC }

    public static final class Node {
        public final Kind kind;
        public final float x, y, r;
        /** RITE: rango que concede (1..3). GEM: rango en el que se gana (0..3). */
        public final int rank;
        /** Nodo con el que se une hacia el núcleo (null en CORE). */
        public final Node parent;
        /** Dirección hacia fuera (unitaria), para colocar etiquetas. */
        public final float dx, dy;
        public final int color;
        /** GEM: efectos que muestra (todos los atributos juntos, o una mecánica). */
        public final List<EffectDef> effects = new ArrayList<>();
        public GemType gemType;
        /** GEM: true si es un rasgo de la raza (tronco) y no de la subraza. */
        public boolean trunk;
        /** RITE: su definición (null si la subraza no tiene ese rito). */
        public RiteDef rite;
        /** Texto que se muestra junto al nodo (lo rellena quien conoce las traducciones). */
        public String label = "";
        public IconRef icon;

        Node(Kind kind, float x, float y, float r, int rank, Node parent, float dx, float dy, int color) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.r = r;
            this.rank = rank;
            this.parent = parent;
            this.dx = dx;
            this.dy = dy;
            this.color = color;
        }
    }

    static final float CORE_R = 26, KEY_R = 16, RITE_R = 11, GEM_R = 5;
    static final float COLUMN = 165, GEM_NEAR = 46, GEM_ROW = 22;

    public final List<Node> nodes = new ArrayList<>();
    public final Node core;
    public final Node subKey;
    /** Los nodos de rito, en orden: el primero lleva al Rango 1. */
    public final List<Node> rites = new ArrayList<>();
    public final List<Node> gems = new ArrayList<>();
    public float minX, minY, maxX, maxY;
    /** Gemas ya colocadas en la columna de cada nodo. */
    private final Map<Node, Integer> slots = new IdentityHashMap<>();

    /**
     * @param color color del brazo de la subraza
     * @param trunkRanks por rango 0..3: los rasgos de la raza que se ganan en ese rango
     * @param subRanks por rango 0..3: los rasgos de la subraza que se ganan en ese rango
     * @param riteDefs los ritos de la subraza (hasta 3)
     * @param isAttribute si un efecto es de atributos (se juntan en una sola gema)
     */
    public RaceTreeLayout(int color, List<List<EffectDef>> trunkRanks, List<List<EffectDef>> subRanks, List<RiteDef> riteDefs,
                          Predicate<EffectDef> isAttribute) {
        core = add(new Node(Kind.CORE, 0, 0, CORE_R, 0, null, 0, -1, TreeLayout.GOLD));
        subKey = add(new Node(Kind.SUB_KEY, COLUMN, 0, KEY_R, 0, core, 1, 0, color));

        // Rango 0: los rasgos de la raza cuelgan del núcleo y los de la subraza, de su nodo.
        addGems(core, rank(trunkRanks, 0), 0, true, TreeLayout.GEM_COLOR, isAttribute);
        addGems(subKey, rank(subRanks, 0), 0, false, color, isAttribute);

        Node prev = subKey;
        float[] wave = {-16, 14, -10};
        for (int i = 0; i < 3; i++) {
            Node rite = add(new Node(Kind.RITE, COLUMN * (i + 2), wave[i], RITE_R, i + 1, prev, 1, 0, color));
            if (i < riteDefs.size()) rite.rite = riteDefs.get(i);
            rites.add(rite);
            addGems(rite, rank(trunkRanks, i + 1), i + 1, true, TreeLayout.GEM_COLOR, isAttribute);
            addGems(rite, rank(subRanks, i + 1), i + 1, false, color, isAttribute);
            prev = rite;
        }

        minX = minY = Float.MAX_VALUE;
        maxX = maxY = -Float.MAX_VALUE;
        for (Node n : nodes) {
            minX = Math.min(minX, n.x - n.r);
            minY = Math.min(minY, n.y - n.r);
            maxX = Math.max(maxX, n.x + n.r);
            maxY = Math.max(maxY, n.y + n.r);
        }
        // Margen para las etiquetas de las gemas (a la derecha de cada columna) y del título de cada rito.
        minX -= 60;
        minY -= 50;
        maxX += 150;
        maxY += 50;
    }

    private static List<EffectDef> rank(List<List<EffectDef>> byRank, int rank) {
        return rank < byRank.size() ? byRank.get(rank) : List.of();
    }

    /** Coloca las gemas de un rango en una columna encima y debajo de su nodo, alternando. */
    private void addGems(Node anchor, List<EffectDef> effects, int rank, boolean trunk, int color, Predicate<EffectDef> isAttribute) {
        List<EffectDef> mechanics = new ArrayList<>();
        List<EffectDef> attributes = new ArrayList<>();
        for (EffectDef e : effects) (isAttribute.test(e) ? attributes : mechanics).add(e);
        if (!attributes.isEmpty()) gem(anchor, rank, trunk, color, GemType.ATTRIBUTES, attributes);
        for (EffectDef m : mechanics) gem(anchor, rank, trunk, color, GemType.MECHANIC, List.of(m));
    }

    private void gem(Node anchor, int rank, boolean trunk, int color, GemType type, List<EffectDef> effects) {
        int index = slots.merge(anchor, 1, Integer::sum) - 1;
        int side = index % 2 == 0 ? -1 : 1; // pares arriba, impares abajo
        int row = index / 2;
        float gy = anchor.y + side * (anchor.r + GEM_NEAR + row * GEM_ROW);
        Node n = add(new Node(Kind.GEM, anchor.x, gy, GEM_R, rank, anchor, 0, side, color));
        n.effects.addAll(effects);
        n.gemType = type;
        n.trunk = trunk;
        gems.add(n);
    }

    private Node add(Node node) {
        nodes.add(node);
        return node;
    }

    @Override public float minX() { return minX; }
    @Override public float minY() { return minY; }
    @Override public float maxX() { return maxX; }
    @Override public float maxY() { return maxY; }
    @Override public float baseMinX() { return minX; }
    @Override public float baseMinY() { return minY; }
    @Override public float baseMaxX() { return maxX; }
    @Override public float baseMaxY() { return maxY; }
}
