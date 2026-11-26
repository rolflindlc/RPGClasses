package io.github.rpgclasses.client.spin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.spin.Bandolier;
import io.github.rpgclasses.spin.BandolierItem;
import io.github.rpgclasses.spin.SphereItem;
import io.github.rpgclasses.spin.SphereKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Dibujo de las bolas y de la bandolera como objetos. Las bolas: el modelo voxel con el anillo girando (en la mano, en el
 * suelo y en el inventario). La bandolera: en la mano, la bola elegida girando (si hay alguna lista); en el inventario, en
 * el suelo y en los marcos, su icono plano ({@code item/sphere_bandolier_icon}).
 */
public final class SpinItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ResourceLocation BANDOLIER_ICON = RpgClasses.id("item/sphere_bandolier_icon");
    private static SpinItemRenderer instance;

    private SpinItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static SpinItemRenderer get() {
        if (instance == null) instance = new SpinItemRenderer();
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        float t = (mc.level == null ? 0 : mc.level.getGameTime()) + mc.getPartialTick();
        if (stack.getItem() instanceof SphereItem s) {
            ball(pose, buffers, light, overlay, SphereKind.of(stack), t, false, 20f);
            return;
        }
        if (!(stack.getItem() instanceof BandolierItem)) return;
        boolean hand = ctx.firstPerson() || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        if (hand) {
            SphereKind m = Bandolier.current(stack);
            long now = mc.level == null ? 0 : mc.level.getGameTime();
            if (m != null && Bandolier.ready(stack, m, now) > 0) {
                // La propia: el anillo gira más rápido cuanto más Giro.
                float spin = SpinClient.spin();
                boolean golden = SpinClient.golden() || spin >= 0.33f;
                // Carga del clic derecho (solo la propia, en primera persona): la bola se echa atrás y hacia arriba,
                // crece un poco, el anillo gira cada vez más rápido y, con la carga completa, tiembla.
                float c = ctx.firstPerson() ? SpinClient.chargeProgress(mc.getPartialTick()) : 0f;
                if (c > 0f) {
                    float ease = c * c * (3 - 2 * c);
                    pose.pushPose();
                    float jx = 0, jy = 0;
                    // Sobrecarga (giro muscular): tiembla cada vez más y gira aún más rápido.
                    float o = SpinClient.overcharge(mc.getPartialTick());
                    if (c >= 1f) {
                        float amp = 0.015f + 0.035f * o;
                        jx = (float) Math.sin(t * (2.7f + 3f * o)) * amp;
                        jy = (float) Math.cos(t * (3.1f + 3f * o)) * amp;
                    }
                    pose.translate(0.5 + jx, 0.5 + 0.12 * ease + jy, 0.5 + 0.18 * ease);
                    float sc = 1f + 0.15f * ease;
                    pose.scale(sc, sc, sc);
                    pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-12f * ease));
                    pose.translate(-0.5, -0.5, -0.5);
                    ball(pose, buffers, light, overlay, m, t, golden || c >= 1f, 20f + 50f * spin + 160f * ease + 240f * o);
                    pose.popPose();
                    return;
                }
                ball(pose, buffers, light, overlay, m, t, golden, 20f + 50f * spin);
            }
            return;
        }
        BakedModel icon = mc.getModelManager().getModel(BANDOLIER_ICON);
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        mc.getItemRenderer().render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, overlay, icon);
        pose.popPose();
    }

    private static void ball(PoseStack pose, MultiBufferSource buffers, int light, int overlay, SphereKind m, float t, boolean golden, float ringSpeed) {
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        VoxelBall.render(pose, buffers, light, overlay, m, t * ringSpeed, golden, 2.0f, t * 3f);
        pose.popPose();
    }
}
