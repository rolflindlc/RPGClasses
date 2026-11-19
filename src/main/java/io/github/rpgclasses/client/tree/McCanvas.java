package io.github.rpgclasses.client.tree;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.rpgclasses.client.Portraits;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link Canvas} de Minecraft: agrupa todos los cuadriláteros seguidos en un solo lote de vértices con color
 * (sin texturas ni shaders propios, para no chocar con paquetes de shaders) y lo dibuja antes de cualquier
 * texto o icono, así se respeta el orden de dibujo.
 */
public final class McCanvas implements Canvas {
    private final Map<String, ItemStack> stacks = new HashMap<>();
    private GuiGraphics g;
    private Font font;
    private BufferBuilder buffer;
    private Matrix4f pose;
    private boolean open;

    public void begin(GuiGraphics g, Font font) {
        this.g = g;
        this.font = font;
    }

    public void end() {
        flushGeometry();
        g = null;
    }

    @Override
    public void quad(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
        if ((c0 | c1 | c2 | c3) >>> 24 == 0) return;
        if (!open) {
            g.flush();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            pose = g.pose().last().pose();
            open = true;
        }
        vertex(x0, y0, c0);
        vertex(x1, y1, c1);
        vertex(x2, y2, c2);
        vertex(x3, y3, c3);
    }

    private void vertex(float x, float y, int c) {
        buffer.vertex(pose, x, y, 0).color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF).endVertex();
    }

    /** Dibuja el lote pendiente y deja el estado como lo espera el resto de la interfaz. */
    public void flushGeometry() {
        if (!open) return;
        open = false;
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    @Override
    public void text(String s, float x, float y, int color, boolean shadow) {
        flushGeometry();
        g.drawString(font, s, Math.round(x), Math.round(y), color, shadow);
    }

    @Override
    public int textWidth(String s) {
        return font.width(s);
    }

    @Override
    public void icon(IconRef icon, float cx, float cy, float size) {
        if (icon == null || size < 3) return;
        flushGeometry();
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        if ("item".equals(icon.kind())) {
            ItemStack stack = stack(icon.id());
            if (!stack.isEmpty()) {
                float s = size / 16f;
                g.pose().scale(s, s, 1);
                g.renderItem(stack, -8, -8);
            }
        } else {
            ResourceLocation tex = portrait(icon);
            float s = size / 32f;
            g.pose().scale(s, s, 1);
            RenderSystem.enableBlend();
            Portraits.draw(g, tex, icon.fallbackItem(), -16, -16, 32);
            RenderSystem.disableBlend();
        }
        g.pose().popPose();
    }

    private static ResourceLocation portrait(IconRef icon) {
        return switch (icon.kind()) {
            case "class" -> Portraits.classTex(icon.id());
            case "race" -> Portraits.raceTex(icon.id());
            case "subrace" -> {
                int cut = icon.id().lastIndexOf('#');
                yield cut < 0 ? null : Portraits.subraceTex(icon.id().substring(0, cut), icon.id().substring(cut + 1));
            }
            default -> Portraits.subclassTex(icon.id());
        };
    }

    private ItemStack stack(String id) {
        return stacks.computeIfAbsent(id, key -> {
            ResourceLocation rl = ResourceLocation.tryParse(key);
            Item item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        });
    }
}
