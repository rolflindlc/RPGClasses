package io.github.rpgclasses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.rpgclasses.content.race.SwarmSlime;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Slime pequeño del Enjambre: el modelo y la textura del slime vanilla de tamaño 1, con su aplastamiento al botar. */
public class SwarmSlimeRenderer extends MobRenderer<SwarmSlime, SlimeModel<SwarmSlime>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/entity/slime/slime.png");

    public SwarmSlimeRenderer(EntityRendererProvider.Context context) {
        super(context, new SlimeModel<>(context.bakeLayer(ModelLayers.SLIME)), 0.25f);
        addLayer(new SlimeOuterLayer<>(this, context.getModelSet()));
    }

    @Override
    protected void scale(SwarmSlime slime, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.999f, 0.999f, 0.999f);
        poseStack.translate(0.0f, 0.001f, 0.0f);
        float squish = Mth.lerp(partialTick, slime.oSquish, slime.squish) / 1.5f;
        float f = 1.0f / (squish + 1.0f);
        poseStack.scale(f, 1.0f / f, f);
    }

    @Override
    public ResourceLocation getTextureLocation(SwarmSlime slime) {
        return TEXTURE;
    }
}
