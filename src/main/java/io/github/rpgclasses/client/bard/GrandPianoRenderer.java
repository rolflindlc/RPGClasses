package io.github.rpgclasses.client.bard;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.rpgclasses.bard.GrandPianoEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Piano de Cola: la sombra (un disco oscuro que se encoge y se oscurece) y, al final, el piano que cae, hecho de
 * bloques escalados (cuerpo y tapa negros, teclado blanco, patas de roble oscuro).
 */
public class GrandPianoRenderer extends EntityRenderer<GrandPianoEntity> {
    private static final ResourceLocation SHADOW = new ResourceLocation("textures/misc/shadow.png");
    private static final float DROP_HEIGHT = 9f;

    public GrandPianoRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(GrandPianoEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = entity.age() + partialTick;
        if (age < 0) return;
        float size = entity.size();
        int shadowEnd = GrandPianoEntity.SHADOW + GrandPianoEntity.FALL;
        if (age < shadowEnd) {
            float t = Mth.clamp(age / GrandPianoEntity.SHADOW, 0, 1);
            float r = size * (2.2f - 1.2f * t);
            float alpha = 0.35f + 0.5f * t;
            shadow(pose, buffers, r, alpha, light);
        }
        if (age >= GrandPianoEntity.SHADOW) {
            float u = Mth.clamp((age - GrandPianoEntity.SHADOW) / GrandPianoEntity.FALL, 0, 1);
            float h = DROP_HEIGHT * (1 - u * u);
            pose.pushPose();
            pose.translate(0, h, 0);
            pose.mulPose(Axis.YP.rotationDegrees((entity.getId() * 47) % 360));
            piano(pose, buffers, light);
            pose.popPose();
        }
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    private static void shadow(PoseStack pose, MultiBufferSource buffers, float r, float alpha, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(SHADOW));
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        Matrix3f n = last.normal();
        float y = 0.03f;
        vertex(vc, m, n, -r, y, -r, 0, 0, alpha, light);
        vertex(vc, m, n, -r, y, r, 0, 1, alpha, light);
        vertex(vc, m, n, r, y, r, 1, 1, alpha, light);
        vertex(vc, m, n, r, y, -r, 1, 0, alpha, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, float alpha, int light) {
        vc.vertex(m, x, y, z).color(0f, 0f, 0f, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
    }

    private static void piano(PoseStack pose, MultiBufferSource buffers, int light) {
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        BlockState black = Blocks.BLACK_CONCRETE.defaultBlockState();
        BlockState white = Blocks.WHITE_CONCRETE.defaultBlockState();
        BlockState wood = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        // Patas
        block(blocks, pose, buffers, light, wood, -0.9f, 0f, -0.55f, 0.14f, 0.55f, 0.14f);
        block(blocks, pose, buffers, light, wood, 0.75f, 0f, -0.55f, 0.14f, 0.55f, 0.14f);
        block(blocks, pose, buffers, light, wood, -0.1f, 0f, 0.5f, 0.14f, 0.55f, 0.14f);
        // Cuerpo
        block(blocks, pose, buffers, light, black, -1.0f, 0.55f, -0.7f, 2.0f, 0.45f, 1.4f);
        // Teclado (teclas blancas y una franja negra)
        block(blocks, pose, buffers, light, white, -0.95f, 0.78f, -0.95f, 1.9f, 0.1f, 0.3f);
        block(blocks, pose, buffers, light, black, -0.95f, 0.88f, -0.85f, 1.9f, 0.04f, 0.12f);
        // Tapa abierta, apoyada en el borde trasero
        pose.pushPose();
        pose.translate(-1.0f, 1.0f, 0.7f);
        pose.mulPose(Axis.XP.rotationDegrees(-35));
        pose.translate(0, 0, -1.4f);
        pose.scale(2.0f, 0.06f, 1.4f);
        blocks.renderSingleBlock(black, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    private static void block(BlockRenderDispatcher blocks, PoseStack pose, MultiBufferSource buffers, int light, BlockState state,
                              float x, float y, float z, float sx, float sy, float sz) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(sx, sy, sz);
        blocks.renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(GrandPianoEntity entity) {
        return SHADOW;
    }
}
