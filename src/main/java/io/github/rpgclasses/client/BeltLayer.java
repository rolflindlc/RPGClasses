package io.github.rpgclasses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bloques del cinturón del Coloso Porteador (adenda 0.7, 4.3) dibujados alrededor de su cintura: delante, a los lados y
 * detrás, en el orden de los huecos. Los manda el servidor ({@code Packets.BeltVisual}) al cambiar el cinturón y a quien
 * empieza a ver al Porteador.
 */
public class BeltLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final Map<Integer, List<ItemStack>> BELTS = new HashMap<>();
    /** Posición de cada hueco en la cintura (x, z en bloques del modelo; delante = −z) y su giro. */
    private static final float[][] SPOTS = {
            {-0.17f, -0.2f, 0}, {0.17f, -0.2f, 0}, {-0.33f, 0f, 90}, {0.33f, 0f, -90}, {-0.15f, 0.2f, 180}, {0.15f, 0.2f, 180}};
    private static final float WAIST_Y = 0.68f;
    private static final float BLOCK_SCALE = 0.32f;

    public BeltLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    public static void receive(int entityId, List<ItemStack> blocks) {
        if (blocks.isEmpty()) BELTS.remove(entityId);
        else BELTS.put(entityId, List.copyOf(blocks));
    }

    public static void reset() {
        BELTS.clear();
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        List<ItemStack> blocks = BELTS.get(player.getId());
        if (blocks == null || blocks.isEmpty() || player.isInvisible()) return;
        Minecraft mc = Minecraft.getInstance();
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0f);
        for (int i = 0; i < Math.min(blocks.size(), SPOTS.length); i++) {
            float[] spot = SPOTS[i];
            poseStack.pushPose();
            getParentModel().body.translateAndRotate(poseStack);
            poseStack.translate(spot[0], WAIST_Y, spot[1]);
            poseStack.mulPose(Axis.YP.rotationDegrees(spot[2]));
            poseStack.scale(BLOCK_SCALE, -BLOCK_SCALE, -BLOCK_SCALE);
            mc.getItemRenderer().renderStatic(player, blocks.get(i), ItemDisplayContext.FIXED, false, poseStack, buffer,
                    player.level(), light, overlay, player.getId() + i);
            poseStack.popPose();
        }
    }
}
