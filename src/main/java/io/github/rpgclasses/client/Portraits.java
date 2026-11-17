package io.github.rpgclasses.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * Retratos (todos 64x64; se muestran a 36 px de interfaz) de clases, subclases, razas y subrazas ({@code textures/gui/portrait/<tipo>/<id>.png}).
 * Si una definición (p. ej. de un datapack) no tiene retrato, se dibuja su icono de objeto.
 */
public final class Portraits {
    /** Tamaño en la interfaz (píxeles de GUI). */
    public static final int TEX = 36;
    /** Resolución de la textura: 64x64 para clases, subclases, razas y subrazas. */
    public static final int RES = 64;
    private static final Map<ResourceLocation, Boolean> EXISTS = new HashMap<>();

    private Portraits() {}

    /** Se llama al abrir o redimensionar una pantalla: un paquete de recursos nuevo puede añadir retratos. */
    public static void clearCache() {
        EXISTS.clear();
    }

    public static ResourceLocation classTex(String id) {
        return tex("class", id, null);
    }

    public static ResourceLocation subclassTex(String id) {
        return tex("subclass", id, null);
    }

    public static ResourceLocation raceTex(String id) {
        return tex("race", id, null);
    }

    public static ResourceLocation subraceTex(String raceId, String subId) {
        return tex("subrace", raceId, subId);
    }

    private static ResourceLocation tex(String kind, String id, String sub) {
        ResourceLocation rl = id == null ? null : ResourceLocation.tryParse(id);
        if (rl == null) return null;
        String path = "textures/gui/portrait/" + kind + "/" + rl.getPath() + (sub == null ? "" : "/" + sub) + ".png";
        try {
            return new ResourceLocation(rl.getNamespace(), path);
        } catch (net.minecraft.ResourceLocationException e) {
            return null; // id de subraza con caracteres no válidos en una ruta
        }
    }

    private static boolean exists(ResourceLocation tex) {
        if (tex == null) return false;
        return EXISTS.computeIfAbsent(tex, t -> Minecraft.getInstance().getResourceManager().getResource(t).isPresent());
    }

    /**
     * Dibuja el retrato a {@code size} píxeles de interfaz (36 = tamaño real; 18 = mitad).
     * @param fallbackItem id del objeto que se dibuja si no hay retrato (puede ser null).
     */
    public static void draw(GuiGraphics g, ResourceLocation tex, String fallbackItem, int x, int y, int size) {
        if (exists(tex)) {
            g.blit(tex, x, y, size, size, 0, 0, RES, RES, RES, RES);
            return;
        }
        g.fill(x, y, x + size, y + size, 0xFF2A2A30);
        ResourceLocation itemId = fallbackItem == null ? null : ResourceLocation.tryParse(fallbackItem);
        Item item = itemId == null ? null : ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null) return;
        if (size >= 16) {
            g.renderItem(new ItemStack(item), x + (size - 16) / 2, y + (size - 16) / 2);
        }
    }
}
