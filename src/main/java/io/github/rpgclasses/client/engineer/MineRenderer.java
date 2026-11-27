package io.github.rpgclasses.client.engineer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.rpgclasses.client.ClientHandlers;
import io.github.rpgclasses.engineer.MineEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;

/** Mina: una placa de presión de hierro. Armada, solo la ven su dueño y sus aliados. */
public class MineRenderer extends EntityRenderer<MineEntity> {
    public MineRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public boolean shouldRender(MineEntity mine, Frustum frustum, double x, double y, double z) {
        var viewer = Minecraft.getInstance().player;
        if (viewer != null && !mine.visibleTo(viewer, ClientHandlers::isAlly)) return false;
        return super.shouldRender(mine, frustum, x, y, z);
    }

    @Override
    public void render(MineEntity mine, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(-0.375, 0, -0.375);
        pose.scale(0.75f, 1f, 0.75f);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState(), pose,
                buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(mine, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MineEntity mine) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
