package io.github.rpgclasses.client.druid;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.rpgclasses.druid.BeastRemains;
import io.github.rpgclasses.druid.Beasts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/** Restos de una bestia: el modelo de su especie, tumbado como al morir (y teñido de rojo). */
public class BeastRemainsRenderer extends EntityRenderer<BeastRemains> {
    private static final ResourceLocation NONE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private final Map<BeastRemains, LivingEntity> bodies = new WeakHashMap<>();

    public BeastRemainsRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0;
    }

    @Override
    public void render(BeastRemains remains, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        LivingEntity body = bodies.get(remains);
        if (body == null && !remains.species().isEmpty()) {
            body = DruidClient.create(Beasts.type(remains.species()), remains);
            if (body == null) return;
            // Muerto del todo: el renderizador vanilla lo dibuja tumbado (y rojo, como al morir).
            body.deathTime = 20;
            body.yBodyRot = body.yBodyRotO = body.yHeadRot = body.yHeadRotO = remains.bodyYaw();
            bodies.put(remains, body);
        }
        if (body == null) return;
        DruidClient.renderEntity(Minecraft.getInstance(), body, remains.bodyYaw(), partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(BeastRemains remains) {
        return NONE;
    }
}
