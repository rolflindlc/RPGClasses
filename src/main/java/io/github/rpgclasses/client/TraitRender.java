package io.github.rpgclasses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.rpgclasses.difficulty.TraitBehaviors;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Rasgo Coloso de los mobs de élite: dibuja al mob más grande. El tamaño va en la amplificación del efecto de
 * gigantismo (en décimas), que el servidor sincroniza, porque el cliente no tiene la configuración del servidor.
 */
public final class TraitRender {
    private boolean scaled;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        float scale = entity instanceof Player ? 1.0f : TraitBehaviors.sizeScale(entity);
        scaled = scale != 1.0f;
        if (!scaled) return;
        TraitBehaviors.syncSize(entity); // la caja de colisión del cliente (apuntar) también crece
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.scale(scale, scale, scale);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPost(RenderLivingEvent.Post<?, ?> event) {
        if (!scaled) return;
        scaled = false;
        event.getPoseStack().popPose();
    }
}
