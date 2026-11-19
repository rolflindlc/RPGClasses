package io.github.rpgclasses.client.tree;

import io.github.rpgclasses.core.definition.Definitions.BranchDef;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.MilestoneDef;
import io.github.rpgclasses.core.definition.Definitions.SubclassDef;
import io.github.rpgclasses.core.progression.ProgressionRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Posiciones del árbol de habilidades de una clase, en "unidades de árbol" (píxeles de interfaz con zoom 1).
 * Isla de clase base: núcleo en (0, 0) con los hitos en su anillo y una rama por brazo, repartidas en estrella
 * y curvadas como una galaxia; al llenar un brazo, se bifurca en sus perks. Isla de subclase: a la derecha,
 * unida por un puente, con un brazo por subclase. Todo se deriva de la definición: una clase de un datapack
 * con otro número de ramas o subclases se dibuja igual. Lógica pura (sin Minecraft).
 */
public final class TreeLayout implements TreeCamera.Bounds {
    public enum Kind { CORE, KEYSTONE, POINT, PERK, GEM, SUB_HUB, SUB_KEY, SUB_POINT }

    /** Colores de los brazos de rama, por índice. */
    public static final int[] BRANCH_COLORS = {0xFFFFB347, 0xFF4FC3F7, 0xFFB98CFF, 0xFF5EE39A, 0xFFFF6E8E, 0xFFFFE066, 0xFF7DE3E0};
    /** Colores de los brazos de subclase, por índice. */
    public static final int[] SUBCLASS_COLORS = {0xFF4DE1C1, 0xFFFFD166, 0xFFF08CFF, 0xFF8FB8FF};
    public static final int GOLD = 0xFFE6C35A;
    public static final int GEM_COLOR = 0xFF5FE3FF;
    public static final int PERK_COLOR = 0xFFE07BFF;

    static final float CORE_R = 26, KEY_R = 12, POINT_R = 6.5f, PERK_R = 9, GEM_R = 5, HUB_R = 20, SUB_KEY_R = 16, SUB_POINT_R = 6;
    static final float KEY_DIST = 62, FIRST_POINT = 90, POINT_STEP = 21, PERK_DIST = 318, PERK_SPREAD = 21;
    /** Curvatura de los brazos (grados que gira el extremo respecto a la raíz). */
    static final float CURVE = 14;
    static final float GEM_RING = 36;
    static final float SUB_KEY_DIST = 72, SUB_FIRST = 104, SUB_STEP = 19;
    /** Distancia del núcleo de clase al de subclase. */
    static final float ISLAND_GAP = 690;

    public static final class Node {
        public final Kind kind;
        public final float x, y, r;
        /** Rama (KEYSTONE, POINT, PERK) o subclase (SUB_KEY, SUB_POINT); -1 en el resto. */
        public final int index;
        /** POINT y SUB_POINT: 1..10. GEM: nivel del hito. PERK: índice del perk. */
        public final int level;
        /** Nodo hacia el centro con el que se une (null en CORE, SUB_HUB y GEM). */
        public final Node parent;
        /** Dirección hacia fuera (unitaria), para colocar etiquetas. */
        public final float dx, dy;
        public final int color;
        /** Hitos asociados: GEM (uno) o SUB_POINT (los de ese nivel de subclase). */
        public final List<MilestoneDef> milestones = new ArrayList<>();
        /** Texto que se muestra junto al nodo (lo rellena quien conoce las traducciones). */
        public String label = "";
        public IconRef icon;

        Node(Kind kind, float x, float y, float r, int index, int level, Node parent, float dx, float dy, int color) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.r = r;
            this.index = index;
            this.level = level;
            this.parent = parent;
            this.dx = dx;
            this.dy = dy;
            this.color = color;
        }
    }

    public final ClassDef def;
    /** Definiciones de subclase en el orden de {@code def.subclasses()} (null si falta alguna). */
    public final List<SubclassDef> subclassDefs;
    public final List<Node> nodes = new ArrayList<>();
    public final Node core;
    /** null si la clase no tiene subclases. */
    public final Node subHub;
    public final List<Node> keystones = new ArrayList<>();
    /** Por rama: sus nodos POINT (1..maxPoints). */
    public final List<List<Node>> points = new ArrayList<>();
    /** Por rama: sus nodos PERK. */
    public final List<List<Node>> perks = new ArrayList<>();
    public final List<Node> gems = new ArrayList<>();
    public final List<Node> subKeys = new ArrayList<>();
    /** Por subclase: sus nodos SUB_POINT (1..10). */
    public final List<List<Node>> subPoints = new ArrayList<>();
    /** Por rama: nodo intermedio del brazo donde va su etiqueta, y dirección perpendicular. */
    public final float[][] branchLabelAnchor;
    public final float[][] subLabelAnchor;
    public float minX, minY, maxX, maxY;
    /** Rectángulo de la isla de clase base (sin la subclase), para el encuadre inicial. */
    public float baseMinX, baseMinY, baseMaxX, baseMaxY;

    public TreeLayout(ClassDef def, List<SubclassDef> subclassDefs) {
        this.def = def;
        this.subclassDefs = subclassDefs == null ? List.of() : subclassDefs;
        core = add(new Node(Kind.CORE, 0, 0, CORE_R, -1, 0, null, 0, -1, GOLD));
        core.icon = IconRef.classPortrait(def.id(), def.icon());

        int n = Math.max(1, def.branches().size());
        // Brazos en estrella dejando un hueco a la derecha (hacia la subclase); la rama 0 arriba a la izquierda.
        float[] angles = new float[n];
        int shift = 0;
        float best = Float.MAX_VALUE;
        for (int j = 0; j < n; j++) {
            float a = 360f / n * (j + 0.5f);
            float d = Math.abs(angleDiff(a, 250));
            if (d < best) {
                best = d;
                shift = j;
            }
        }
        for (int k = 0; k < n; k++) angles[k] = 360f / n * (((k + shift) % n) + 0.5f);

        branchLabelAnchor = new float[def.branches().size()][];
        for (int k = 0; k < def.branches().size(); k++) {
            BranchDef b = def.branches().get(k);
            int color = BRANCH_COLORS[k % BRANCH_COLORS.length];
            float a0 = angles[k];
            Node key = add(polar(Kind.KEYSTONE, 0, 0, KEY_DIST, a0, KEY_R, k, 0, core, color));
            key.icon = IconRef.item(b.icon() != null ? b.icon() : def.icon());
            keystones.add(key);
            List<Node> list = new ArrayList<>();
            Node prev = key;
            int max = Math.max(1, b.maxPoints());
            for (int i = 1; i <= max; i++) {
                float t = (float) i / max;
                float dist = FIRST_POINT + (i - 1) * POINT_STEP * 10f / Math.max(10, max);
                float r = i == max ? POINT_R + 2 : (i * 2 == max ? POINT_R + 1 : POINT_R);
                prev = add(polar(Kind.POINT, 0, 0, dist, a0 + CURVE * (float) Math.pow(t, 1.5), r, k, i, prev, color));
                list.add(prev);
            }
            points.add(list);
            List<Node> perkList = new ArrayList<>();
            int np = b.perks().size();
            float pa = a0 + CURVE * 1.12f;
            float ox = (float) Math.cos(Math.toRadians(pa)), oy = (float) Math.sin(Math.toRadians(pa));
            for (int p = 0; p < np; p++) {
                float off = np == 1 ? 0 : PERK_SPREAD * (2f * p / (np - 1) - 1);
                float px = ox * PERK_DIST - oy * off, py = oy * PERK_DIST + ox * off;
                perkList.add(add(new Node(Kind.PERK, px, py, PERK_R, k, p, prev, ox, oy, PERK_COLOR)));
            }
            perks.add(perkList);
            // Etiqueta entre los puntos del medio del brazo.
            Node m1 = list.get(Math.max(0, max / 2 - 1)), m2 = list.get(Math.min(max - 1, max / 2));
            float mx = (m1.x + m2.x) / 2, my = (m1.y + m2.y) / 2;
            float ax = m2.x - m1.x, ay = m2.y - m1.y;
            float len = (float) Math.sqrt(ax * ax + ay * ay);
            if (len < 1e-3f) {
                ax = m1.dx;
                ay = m1.dy;
                len = 1;
            }
            // Perpendicular hacia el lado que se abre con la curva (sentido horario en pantalla).
            branchLabelAnchor[k] = new float[]{mx, my, ay / len, -ax / len};
        }

        // Hitos de la clase en el anillo del núcleo (empieza arriba, sentido horario, 40 niveles = vuelta completa).
        int total = ProgressionRules.BASE_MAX + ProgressionRules.SUBCLASS_MAX;
        List<MilestoneDef> classMilestones = new ArrayList<>();
        for (MilestoneDef m : def.milestones()) if (!m.subclassLevel()) classMilestones.add(m);
        for (int i = 0; i < classMilestones.size(); i++) {
            MilestoneDef m = classMilestones.get(i);
            int same = 0, before = 0;
            for (int j = 0; j < classMilestones.size(); j++) {
                if (classMilestones.get(j).level() == m.level()) {
                    same++;
                    if (j < i) before++;
                }
            }
            float a = -90 + 360f * Math.min(m.level(), total) / total + (same > 1 ? 9f * (before - (same - 1) / 2f) : 0);
            Node gem = add(polar(Kind.GEM, 0, 0, GEM_RING, a, GEM_R, -1, m.level(), null, GEM_COLOR));
            gem.milestones.add(m);
            gems.add(gem);
        }

        // Isla de subclase.
        int ns = def.subclasses().size();
        subLabelAnchor = new float[ns][];
        if (ns > 0) {
            subHub = add(new Node(Kind.SUB_HUB, ISLAND_GAP, 0, HUB_R, -1, 0, null, 1, 0, 0xFF55FFFF));
            float spread = ns == 1 ? 0 : Math.min(230, 110f * (ns - 1));
            for (int j = 0; j < ns; j++) {
                float a = ns == 1 ? 0 : -spread / 2 + spread * j / (ns - 1);
                int color = SUBCLASS_COLORS[j % SUBCLASS_COLORS.length];
                Node key = add(polar(Kind.SUB_KEY, ISLAND_GAP, 0, SUB_KEY_DIST, a, SUB_KEY_R, j, 0, subHub, color));
                subKeys.add(key);
                SubclassDef sd = j < this.subclassDefs.size() ? this.subclassDefs.get(j) : null;
                key.icon = IconRef.subclassPortrait(def.subclasses().get(j), sd != null ? sd.icon() : "minecraft:barrier");
                List<Node> list = new ArrayList<>();
                Node prev = key;
                for (int i = 1; i <= ProgressionRules.SUBCLASS_MAX; i++) {
                    List<MilestoneDef> here = new ArrayList<>();
                    if (sd != null) for (MilestoneDef m : sd.milestones()) if (m.subclassLevel() && m.level() == i) here.add(m);
                    float r = here.isEmpty() ? (i == ProgressionRules.SUBCLASS_MAX ? SUB_POINT_R + 1.5f : SUB_POINT_R) : SUB_POINT_R + 2.5f;
                    prev = add(polar(Kind.SUB_POINT, ISLAND_GAP, 0, SUB_FIRST + (i - 1) * SUB_STEP, a, r, j, i, prev, color));
                    prev.milestones.addAll(here);
                    list.add(prev);
                }
                subPoints.add(list);
                Node m1 = list.get(4), m2 = list.get(5);
                float ax = m2.x - m1.x, ay = m2.y - m1.y;
                float len = (float) Math.sqrt(ax * ax + ay * ay);
                subLabelAnchor[j] = new float[]{(m1.x + m2.x) / 2, (m1.y + m2.y) / 2, ay / len, -ax / len};
            }
        } else {
            subHub = null;
        }

        minX = minY = baseMinX = baseMinY = Float.MAX_VALUE;
        maxX = maxY = baseMaxX = baseMaxY = -Float.MAX_VALUE;
        for (Node node : nodes) {
            minX = Math.min(minX, node.x - node.r);
            minY = Math.min(minY, node.y - node.r);
            maxX = Math.max(maxX, node.x + node.r);
            maxY = Math.max(maxY, node.y + node.r);
            if (node.kind == Kind.SUB_HUB || node.kind == Kind.SUB_KEY || node.kind == Kind.SUB_POINT) continue;
            baseMinX = Math.min(baseMinX, node.x - node.r);
            baseMinY = Math.min(baseMinY, node.y - node.r);
            baseMaxX = Math.max(baseMaxX, node.x + node.r);
            baseMaxY = Math.max(baseMaxY, node.y + node.r);
        }
        // Margen para las etiquetas de los perks y de las ramas.
        minX -= 90;
        maxX += 90;
        minY -= 40;
        maxY += 40;
    }

    /** Nodos en orden de dibujo inverso (los de encima primero), para detectar el que hay bajo el ratón. */
    public List<Node> hitOrder() {
        List<Node> out = new ArrayList<>(nodes);
        Collections.reverse(out);
        return out;
    }

    @Override public float minX() { return minX; }
    @Override public float minY() { return minY; }
    @Override public float maxX() { return maxX; }
    @Override public float maxY() { return maxY; }
    @Override public float baseMinX() { return baseMinX; }
    @Override public float baseMinY() { return baseMinY; }
    @Override public float baseMaxX() { return baseMaxX; }
    @Override public float baseMaxY() { return baseMaxY; }

    public float centerX() {
        return (minX + maxX) / 2;
    }

    public float centerY() {
        return (minY + maxY) / 2;
    }

    private Node add(Node node) {
        nodes.add(node);
        return node;
    }

    private static Node polar(Kind kind, float cx, float cy, float dist, float deg, float r, int index, int level, Node parent, int color) {
        double rad = Math.toRadians(deg);
        float dx = (float) Math.cos(rad), dy = (float) Math.sin(rad);
        return new Node(kind, cx + dx * dist, cy + dy * dist, r, index, level, parent, dx, dy, color);
    }

    static float angleDiff(float a, float b) {
        float d = (a - b) % 360;
        if (d > 180) d -= 360;
        if (d < -180) d += 360;
        return d;
    }
}
