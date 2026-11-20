package io.github.rpgclasses.client.cleric;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.rpgclasses.client.DecoyRenderer;
import io.github.rpgclasses.content.DecoyEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;

/** Cuerpo del Clérigo que reza: el clon del Señuelo (skin, armadura y manos del dueño), siempre agachado. */
public class PrayingBodyRenderer extends DecoyRenderer {
    public PrayingBodyRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(DecoyEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        getModel().crouching = true;
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
        getModel().crouching = false;
    }

    /** Igual que el jugador agachado: el modelo baja un poco. */
    @Override
    public Vec3 getRenderOffset(DecoyEntity entity, float partialTick) {
        return new Vec3(0, -0.125 * entity.getScale(), 0);
    }
}
