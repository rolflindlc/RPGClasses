package io.github.rpgclasses.client.tree;

/**
 * Icono de un nodo: un objeto ({@code kind = "item"}) o un retrato ({@code "class"} o {@code "subclass"}),
 * con un objeto de respaldo si el retrato no existe.
 */
public record IconRef(String kind, String id, String fallbackItem) {
    public static IconRef item(String id) {
        return new IconRef("item", id, id);
    }

    public static IconRef classPortrait(String id, String fallbackItem) {
        return new IconRef("class", id, fallbackItem);
    }

    public static IconRef subclassPortrait(String id, String fallbackItem) {
        return new IconRef("subclass", id, fallbackItem);
    }
}
