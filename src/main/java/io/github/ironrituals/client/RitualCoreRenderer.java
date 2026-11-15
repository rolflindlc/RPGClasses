package io.github.ironrituals.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.ironrituals.block.RitualCoreBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Muestra el libro de ritual flotando y girando sobre el núcleo. */
public class RitualCoreRenderer implements BlockEntityRenderer<RitualCoreBlockEntity> {

    @Override
    public void render(RitualCoreBlockEntity core, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack book = core.getBook();
        if (book.isEmpty() || core.getLevel() == null) return;

        float time = core.getLevel().getGameTime() + partialTick;
        pose.pushPose();
        pose.translate(0.5, 1.15 + Math.sin(time / 12.0) * 0.06, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(time * 1.5F % 360));
        pose.scale(0.6F, 0.6F, 0.6F);
        Minecraft.getInstance().getItemRenderer().renderStatic(book, ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, pose, buffers, core.getLevel(), (int) core.getBlockPos().asLong());
        pose.popPose();
    }
}
