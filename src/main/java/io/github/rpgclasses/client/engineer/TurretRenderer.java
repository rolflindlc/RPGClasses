package io.github.rpgclasses.client.engineer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.rpgclasses.engineer.TurretEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Torreta: un trípode (pararrayos y yunque pequeño) con el arma de fuego encima, girada hacia donde apunta. */
public class TurretRenderer extends EntityRenderer<TurretEntity> {
    public TurretRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.3f;
    }

    @Override
    public void render(TurretEntity turret, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        var blocks = Minecraft.getInstance().getBlockRenderer();
        pose.pushPose();
        pose.translate(-0.25, 0, -0.25);
        pose.scale(0.5f, 0.9f, 0.5f);
        blocks.renderSingleBlock(Blocks.LIGHTNING_ROD.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        ItemStack gun = turret.gun();
        if (!gun.isEmpty()) {
            pose.pushPose();
            pose.translate(0, 0.95, 0);
            float rot = Mth.rotLerp(partialTick, turret.yRotO, turret.getYRot());
            pose.mulPose(Axis.YP.rotationDegrees(-rot));
            pose.mulPose(Axis.YP.rotationDegrees(-90));
            pose.scale(0.9f, 0.9f, 0.9f);
            Minecraft.getInstance().getItemRenderer().renderStatic(gun, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                    pose, buffers, turret.level(), turret.getId());
            pose.popPose();
        }
        super.render(turret, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(TurretEntity turret) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
