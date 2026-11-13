package io.github.rpgclasses.client;

import io.github.rpgclasses.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderPlayerEvent;

/**
 * Gallina (Magia Caótica): mientras un jugador tiene el efecto, en vez de su modelo se dibuja una gallina que
 * copia su orientación y bate las alas cuando está en el aire. Solo es visual; el vuelo lo da el servidor.
 */
public final class ChickenFx {
    private static Chicken dummy;

    private ChickenFx() {}

    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        if (!p.hasEffect(ModEffects.CHICKEN.get()) || p.isSpectator()) return;
        Minecraft mc = Minecraft.getInstance();
        if (dummy == null || dummy.level() != p.level()) dummy = new Chicken(EntityType.CHICKEN, p.level());
        Chicken c = dummy;
        c.setPos(p.getX(), p.getY(), p.getZ());
        c.xo = p.xo; c.yo = p.yo; c.zo = p.zo;
        c.setYRot(p.getYRot()); c.yRotO = p.yRotO;
        c.setXRot(p.getXRot()); c.xRotO = p.xRotO;
        c.yBodyRot = p.yBodyRot; c.yBodyRotO = p.yBodyRotO;
        c.yHeadRot = p.yHeadRot; c.yHeadRotO = p.yHeadRotO;
        c.tickCount = p.tickCount;
        c.setInvisible(p.isInvisible());
        // Alas: baten solo en el aire (la fórmula del modelo usa estos campos).
        c.oFlap = c.flap;
        c.oFlapSpeed = c.flapSpeed;
        c.flapSpeed = p.onGround() ? 0.0f : 1.0f;
        c.flap = p.tickCount * 0.9f;
        event.setCanceled(true);
        mc.getEntityRenderDispatcher().getRenderer(c)
                .render(c, Mth_lerp(p.yRotO, p.getYRot(), event.getPartialTick()), event.getPartialTick(),
                        event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
    }

    private static float Mth_lerp(float a, float b, float t) {
        return net.minecraft.util.Mth.rotLerp(t, a, b);
    }
}
