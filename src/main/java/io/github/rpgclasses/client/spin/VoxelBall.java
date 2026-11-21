package io.github.rpgclasses.client.spin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.spin.SphereKind;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * La bola en 3D pixelado (adenda del Maestro del Giro, sección 3): un núcleo voxel (cubos escalonados que forman una
 * "esfera" de bloques) y un anillo octogonal que gira por separado. Se usa en la entidad y en la mano (y en el inventario).
 * <p>
 * Textura de cada tier y de cada mejora ({@code textures/item/sphere/<id>.png}, 16x16): núcleo en (0,0)-(8,8), núcleo
 * oscuro en (0,8)-(8,16), anillo en (8,0)-(16,4) y anillo dorado en (8,4)-(16,8). El núcleo sale de la textura del tier
 * y el anillo de la de la mejora (o del tier, si no tiene). La hará Laira; la provisional sale de tools/art/make_spin.py.
 */
public final class VoxelBall {
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    private static final float A = 0.15f, B = 0.10f, C = 0.125f;
    private static final float RING_R = 0.235f, RING_T = 0.028f, RING_H = 0.05f;

    private VoxelBall() {}

    public static ResourceLocation texture(String id) {
        return TEXTURES.computeIfAbsent(id, k -> RpgClasses.id("textures/item/sphere/" + k + ".png"));
    }

    /** Textura del núcleo (la del tier). */
    public static ResourceLocation texture(SphereKind k) {
        return texture(k.tier().id);
    }

    /**
     * Dibuja la bola centrada en el origen actual.
     * @param ringAngle giro del anillo en grados.
     * @param golden anillo dorado (Giro I o Giro Dorado).
     * @param scale tamaño (1 = 0,3 bloques de núcleo).
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, int light, int overlay, SphereKind m, float ringAngle,
                              boolean golden, float scale, float coreSpin) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(texture(m)));
        pose.pushPose();
        pose.scale(scale, scale, scale);
        // Núcleo: tres "barras" cruzadas y un cubo interior: una esfera de bloques en tres escalones.
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(coreSpin));
        PoseStack.Pose last = pose.last();
        box(vc, last, -A, -B, -B, A, B, B, 0, 0, 8, 8, light, overlay, 1f);
        box(vc, last, -B, -A, -B, B, A, B, 0, 0, 8, 8, light, overlay, 1f);
        box(vc, last, -B, -B, -A, B, B, A, 0, 0, 8, 8, light, overlay, 1f);
        box(vc, last, -C, -C, -C, C, C, C, 0, 8, 8, 16, light, overlay, 1f);
        pose.popPose();
        // Anillo: octógono inclinado que gira aparte (con la textura de la mejora, si la tiene).
        if (m.upgrade() != null) vc = buffers.getBuffer(RenderType.entityCutoutNoCull(texture(m.upgrade().id)));
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(22f));
        pose.mulPose(Axis.YP.rotationDegrees(ringAngle));
        float half = (float) (RING_R * Math.tan(Math.PI / 8)) + RING_T;
        int v0 = golden ? 4 : 0;
        for (int k = 0; k < 8; k++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(k * 45f));
            box(vc, pose.last(), -half, -RING_H / 2, RING_R - RING_T, half, RING_H / 2, RING_R + RING_T, 8, v0, 16, v0 + 4, light, overlay, 1f);
            pose.popPose();
        }
        pose.popPose();
        pose.popPose();
    }

    /** Una caja con la misma región de textura (en píxeles de 16) en sus seis caras. */
    private static void box(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1,
                            int pu0, int pv0, int pu1, int pv1, int light, int overlay, float shade) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float u0 = pu0 / 16f, v0 = pv0 / 16f, u1 = pu1 / 16f, v1 = pv1 / 16f;
        // +Y / -Y
        quad(vc, m, n, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, u0, v0, u1, v1, light, overlay, shade);
        quad(vc, m, n, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0, -1, 0, u0, v0, u1, v1, light, overlay, shade * 0.6f);
        // +Z / -Z
        quad(vc, m, n, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, 0, 0, 1, u0, v0, u1, v1, light, overlay, shade * 0.85f);
        quad(vc, m, n, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, 0, 0, -1, u0, v0, u1, v1, light, overlay, shade * 0.85f);
        // +X / -X
        quad(vc, m, n, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, 1, 0, 0, u0, v0, u1, v1, light, overlay, shade * 0.75f);
        quad(vc, m, n, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, -1, 0, 0, u0, v0, u1, v1, light, overlay, shade * 0.75f);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                             float nx, float ny, float nz, float u0, float v0, float u1, float v1, int light, int overlay, float shade) {
        vertex(vc, m, n, ax, ay, az, u0, v0, nx, ny, nz, light, overlay, shade);
        vertex(vc, m, n, bx, by, bz, u0, v1, nx, ny, nz, light, overlay, shade);
        vertex(vc, m, n, cx, cy, cz, u1, v1, nx, ny, nz, light, overlay, shade);
        vertex(vc, m, n, dx, dy, dz, u1, v0, nx, ny, nz, light, overlay, shade);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, int light, int overlay, float shade) {
        vc.vertex(m, x, y, z).color(shade, shade, shade, 1f).uv(u, v).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}
