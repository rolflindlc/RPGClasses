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
    /**
     * Renders abiertos (puede haber anidados): la entidad y si se le aplicó escala. Epic Fight cancela el Pre para
     * dibujar su propio modelo con la misma PoseStack, así que la escala se pone antes que él (HIGHEST) y, si el Pre
     * acaba cancelado (no habrá Post), se quita en el último manejador, que también recibe los cancelados.
     */
    private record Open(LivingEntity entity, boolean scaled) {}

    private final java.util.ArrayDeque<Open> open = new java.util.ArrayDeque<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (open.size() > 32) open.clear(); // un render que lanzó una excepción a medias no deja la pila atascada
        float scale = entity instanceof Player ? 1.0f : TraitBehaviors.sizeScale(entity);
        boolean scaled = scale != 1.0f;
        open.push(new Open(entity, scaled));
        if (!scaled) return;
        TraitBehaviors.syncSize(entity); // la caja de colisión del cliente (apuntar) también crece
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.scale(scale, scale, scale);
    }

    /** Pre cancelado (Epic Fight u otro mod ya lo ha dibujado): no habrá Post, se cierra aquí. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onPreCancelled(RenderLivingEvent.Pre<?, ?> event) {
        if (event.isCanceled()) close(event.getEntity(), event.getPoseStack());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPost(RenderLivingEvent.Post<?, ?> event) {
        close(event.getEntity(), event.getPoseStack());
    }

    private void close(LivingEntity entity, PoseStack pose) {
        Open top = open.peek();
        if (top == null || top.entity() != entity) return; // lo canceló alguien antes de nuestro Pre: no abrimos nada
        open.pop();
        if (top.scaled()) pose.popPose();
    }
}
