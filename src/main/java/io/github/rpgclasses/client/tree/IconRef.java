package io.github.rpgclasses.client.tree;

/**
 * Icono de un nodo: un objeto ({@code kind = "item"}) o un retrato ({@code "class"}, {@code "subclass"}, {@code "race"} o {@code "subrace"}),
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

    public static IconRef racePortrait(String id, String fallbackItem) {
        return new IconRef("race", id, fallbackItem);
    }

    /** Retrato de una subraza: el id lleva la raza y la subraza separadas por {@code #}. */
    public static IconRef subracePortrait(String raceId, String subraceId, String fallbackItem) {
        return new IconRef("subrace", raceId + "#" + subraceId, fallbackItem);
    }
}
