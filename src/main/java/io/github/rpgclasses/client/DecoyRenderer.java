package io.github.rpgclasses.client;

import io.github.rpgclasses.content.DecoyEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Dibuja el clon del Señuelo con la apariencia (skin) de su dueño. */
public class DecoyRenderer extends MobRenderer<DecoyEntity, PlayerModel<DecoyEntity>> {
    public DecoyRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        // El clon lleva la armadura y los objetos de las dos manos del Pícaro (rev. 2.7f).
        addLayer(new net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<>(this,
                new net.minecraft.client.model.HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new net.minecraft.client.model.HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        addLayer(new net.minecraft.client.renderer.entity.layers.ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    /** El render del jugador escala el modelo a 0,9375 (y la raza lo reescala): el clon hace lo mismo para medir igual. */
    @Override
    protected void scale(DecoyEntity entity, com.mojang.blaze3d.vertex.PoseStack poseStack, float partialTick) {
        float s = 0.9375f * entity.getScale();
        poseStack.scale(s, s, s);
    }

    @Override
    public ResourceLocation getTextureLocation(DecoyEntity entity) {
        UUID owner = entity.getOwnerUuid().orElse(entity.getUUID());
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(owner);
        return info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(owner);
    }
}
