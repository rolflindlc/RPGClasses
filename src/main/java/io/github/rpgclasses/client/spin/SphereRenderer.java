package io.github.rpgclasses.client.spin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.rpgclasses.spin.SphereEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Bola en vuelo: núcleo voxel que rueda en la dirección del vuelo y anillo que gira más rápido cuanto más Giro tenía el
 * lanzador; clavada en un enemigo gira muy deprisa y tiembla (el "hit-stop" visual de los 3 tics).
 */
public class SphereRenderer extends EntityRenderer<SphereEntity> {
    public SphereRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.12f;
    }

    @Override
    public void render(SphereEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = e.tickCount + partialTick;
        byte state = e.state();
        boolean spinning = state == SphereEntity.SPINNING;
        float ringSpeed = spinning ? 75f : state == SphereEntity.MINE ? 6f : 20f + 40f * e.spin();
        pose.pushPose();
        pose.translate(0, e.getBbHeight() / 2, 0);
        if (spinning) {
            // Temblor del giro sobre el objetivo.
            pose.translate(Mth.sin(t * 7.1f) * 0.03f, Mth.sin(t * 9.3f) * 0.03f, Mth.cos(t * 8.2f) * 0.03f);
        } else {
            // Orientado al vuelo: el anillo gira alrededor de la dirección de avance.
            Vec3 v = e.getDeltaMovement();
            if (v.lengthSqr() > 1.0E-4) {
                float yRot = (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG);
                float xRot = (float) (Mth.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)) * Mth.RAD_TO_DEG);
                pose.mulPose(Axis.YP.rotationDegrees(yRot));
                pose.mulPose(Axis.XP.rotationDegrees(-xRot + 90f));
            }
        }
        boolean golden = e.golden() || e.spin() >= 0.33f;
        VoxelBall.render(pose, buffers, light, OverlayTexture.NO_OVERLAY, e.material(), t * ringSpeed, golden, 1.0f, t * (spinning ? 40f : 14f));
        pose.popPose();
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SphereEntity e) {
        return VoxelBall.texture(e.material());
    }
}
