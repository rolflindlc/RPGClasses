package io.github.rpgclasses.client.hud;

import io.github.rpgclasses.config.ClientConfig;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.HashMap;
import java.util.Map;

/**
 * Sitio de cada elemento del HUD del mod. Cada elemento se dibuja en su sitio normal y aquí se desplaza (o se oculta)
 * según la configuración del cliente, que se cambia con {@link HudEditorScreen}.
 * <p>
 * En su sitio normal, las barras del centro (sigilo, Giro, fusión del Slime) se apilan con {@link #stackCenter} encima
 * de lo más alto que haya a los dos lados de la barra de objetos (vida, armadura de Improved Armor, cargas del Monje,
 * comida, aire, vida de la montura…), igual que hace vanilla, así que no se pisan entre sí ni con esas barras.
 */
public final class HudLayout {
    /** Caja de un elemento en coordenadas de la GUI, sin desplazar. */
    public record Box(int x, int y, int w, int h) {
        boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private static final Map<String, int[]> OFFSET = new HashMap<>();
    private static final Map<String, Boolean> HIDDEN = new HashMap<>();
    private static final Map<String, Box> LAST = new HashMap<>();
    private static final Map<String, Long> SEEN = new HashMap<>();
    private static boolean loaded;

    private HudLayout() {}

    private static void load() {
        if (loaded) return;
        try {
            for (String id : ClientConfig.HUD_IDS) {
                OFFSET.put(id, new int[] {ClientConfig.HUD_X.get(id).get(), ClientConfig.HUD_Y.get(id).get()});
                HIDDEN.put(id, ClientConfig.HUD_HIDDEN.get(id).get());
            }
            loaded = true;
        } catch (IllegalStateException e) {
            // La configuración aún no está cargada: sitio normal.
        }
    }

    /** Vuelve a leer la configuración (al recargarse el archivo). */
    public static void reload() {
        loaded = false;
        OFFSET.clear();
        HIDDEN.clear();
    }

    public static int dx(String id) {
        load();
        int[] o = OFFSET.get(id);
        return o == null ? 0 : o[0];
    }

    public static int dy(String id) {
        load();
        int[] o = OFFSET.get(id);
        return o == null ? 0 : o[1];
    }

    public static boolean hidden(String id) {
        load();
        return HIDDEN.getOrDefault(id, false);
    }

    static void move(String id, int dx, int dy) {
        load();
        OFFSET.put(id, new int[] {dx, dy});
    }

    static void setHidden(String id, boolean hidden) {
        load();
        HIDDEN.put(id, hidden);
    }

    /** Guarda los cambios del editor en rites_and_paths-client.toml. */
    static void save() {
        load();
        try {
            for (String id : ClientConfig.HUD_IDS) {
                ClientConfig.HUD_X.get(id).set(dx(id));
                ClientConfig.HUD_Y.get(id).set(dy(id));
                ClientConfig.HUD_HIDDEN.get(id).set(hidden(id));
            }
            ClientConfig.SPEC.save();
        } catch (IllegalStateException e) {
            io.github.rpgclasses.RpgClasses.LOGGER.warn("[rites_and_paths] No se pudo guardar la posición del HUD ({})", e.toString());
        }
    }

    /**
     * Envuelve el dibujo de un elemento: si está oculto no se dibuja y si está movido se desplaza. Un elemento movido no
     * ocupa sitio en las pilas de los lados (para no empujar a los demás desde donde ya no está).
     */
    public static IGuiOverlay wrap(String id, IGuiOverlay inner) {
        return (gui, g, partialTick, width, height) -> {
            if (hidden(id)) return;
            int ox = dx(id), oy = dy(id);
            int left = gui.leftHeight, right = gui.rightHeight;
            g.pose().pushPose();
            g.pose().translate(ox, oy, 0);
            try {
                inner.render(gui, g, partialTick, width, height);
            } finally {
                g.pose().popPose();
            }
            if (ox != 0 || oy != 0) {
                gui.leftHeight = left;
                gui.rightHeight = right;
            }
        };
    }

    /** El elemento dice dónde se ha dibujado (sin desplazar), para que el editor enseñe su caja real. */
    public static void report(String id, int x, int y, int w, int h) {
        LAST.put(id, new Box(x, y, Math.max(4, w), Math.max(4, h)));
        SEEN.put(id, Util.getMillis());
    }

    /** Última caja dibujada en el último segundo y medio, o null. */
    static Box seen(String id) {
        Long t = SEEN.get(id);
        return t != null && Util.getMillis() - t < 1500 ? LAST.get(id) : null;
    }

    /**
     * Sitio para un elemento centrado de {@code contentHeight} píxeles encima de todo lo que ya ocupan los dos lados de
     * la barra de objetos. Devuelve la fila de arriba del elemento y reserva su altura en los dos lados.
     */
    public static int stackCenter(ForgeGui gui, int screenHeight, int contentHeight) {
        int bottom = screenHeight - Math.max(gui.leftHeight, gui.rightHeight) + 9;
        int top = bottom - contentHeight - 1;
        int used = screenHeight - top + 10;
        gui.leftHeight = Math.max(gui.leftHeight, used);
        gui.rightHeight = Math.max(gui.rightHeight, used);
        return top;
    }

    /** Caja de ejemplo para el editor cuando el elemento no se está dibujando ahora (no eres Monje, no cargas…). */
    static Box fallback(String id, int w, int h) {
        return switch (id) {
            case "abilities" -> new Box(w - 130, h - 46, 124, 42);
            case "stealth" -> new Box(w / 2 - 40, h - 75, 80, 16);
            case "monk_charges" -> new Box(w / 2 - 91, h - 59, 81, 9);
            case "spin_bar" -> new Box(w / 2 - 50, h - 97, 100, 20);
            case "mitosis_merge" -> new Box(w / 2 - 91, h - 105, 182, 5);
            case "wolf_jump" -> new Box(w / 2 - 91, h - 29, 182, 5);
            case "centaur_jump" -> new Box(w / 2 - 91, h - 35, 182, 11);
            case "druid_cast" -> new Box(w / 2 - 60, h / 2 + 16, 120, 34);
            case "druid_form" -> new Box(6, h - 52, 140, 30);
            case "bard_rhythm" -> new Box(w / 2 - 45, h / 2 - 45, 90, 100);
            default -> new Box(w / 2 - 20, h / 2 - 5, 40, 10);
        };
    }

    /** Sitio actual en pantalla (con el desplazamiento). */
    static Box placed(String id, int w, int h) {
        Box b = seen(id);
        if (b == null) b = fallback(id, w, h);
        return new Box(b.x() + dx(id), b.y() + dy(id), b.w(), b.h());
    }

    static boolean drawnRecently(String id) {
        return seen(id) != null;
    }

    static void drawBox(GuiGraphics g, Box b, int color) {
        g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + 1, color);
        g.fill(b.x(), b.y() + b.h() - 1, b.x() + b.w(), b.y() + b.h(), color);
        g.fill(b.x(), b.y(), b.x() + 1, b.y() + b.h(), color);
        g.fill(b.x() + b.w() - 1, b.y(), b.x() + b.w(), b.y() + b.h(), color);
    }
}
