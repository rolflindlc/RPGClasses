package io.github.rpgclasses.client.druid;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * Tiñe todo lo que se dibuja a través de él (multiplica el color de cada vértice). Para las quimeras: el cuerpo del
 * torso con el color de las patas, y la quimera que se forma en el altar.
 */
final class TintedBufferSource implements MultiBufferSource {
    private final MultiBufferSource delegate;
    private final float r, g, b;

    TintedBufferSource(MultiBufferSource delegate, int rgb) {
        this.delegate = delegate;
        this.r = ((rgb >> 16) & 255) / 255f;
        this.g = ((rgb >> 8) & 255) / 255f;
        this.b = (rgb & 255) / 255f;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return new Tinted(delegate.getBuffer(type));
    }

    private final class Tinted implements VertexConsumer {
        private final VertexConsumer d;

        Tinted(VertexConsumer d) {
            this.d = d;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            d.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            d.color((int) (red * r), (int) (green * g), (int) (blue * b), alpha);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            d.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            d.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            d.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            d.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            d.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            d.defaultColor((int) (red * r), (int) (green * g), (int) (blue * b), alpha);
        }

        @Override
        public void unsetDefaultColor() {
            d.unsetDefaultColor();
        }
    }
}
