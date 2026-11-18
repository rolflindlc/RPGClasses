package io.github.rpgclasses.client.tree;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Efectos breves del árbol (destellos al comprar, onda al bloquearse una rama). Lógica pura. */
public final class TreeFx {
    public enum Type { GAIN, PLAN, LOCK }

    public record Burst(Type type, float x, float y, float r, int color, float start) {}

    private final List<Burst> bursts = new ArrayList<>();

    public void add(Type type, float x, float y, float r, int color, float start) {
        if (bursts.size() < 200) bursts.add(new Burst(type, x, y, r, color, start));
    }

    public static float duration(Type type) {
        return switch (type) {
            case GAIN -> 0.8f;
            case PLAN -> 0.35f;
            case LOCK -> 1.1f;
        };
    }

    /** Efectos vivos en {@code time}; quita los terminados. */
    public List<Burst> live(float time) {
        List<Burst> out = new ArrayList<>();
        for (Iterator<Burst> it = bursts.iterator(); it.hasNext(); ) {
            Burst b = it.next();
            float age = time - b.start();
            if (age > duration(b.type())) it.remove();
            else if (age >= 0) out.add(b);
        }
        return out;
    }

    public void clear() {
        bursts.clear();
    }
}
