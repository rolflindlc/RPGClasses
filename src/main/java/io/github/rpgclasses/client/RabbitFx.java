package io.github.rpgclasses.client;

import io.github.rpgclasses.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderPlayerEvent;

/** Conejo (Magia Caótica): mientras un jugador tiene el efecto se dibuja un conejo con su orientación. Solo visual. */
public final class RabbitFx {
    private static Rabbit dummy;

    private RabbitFx() {}

    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        if (!p.hasEffect(ModEffects.RABBIT.get()) || p.isSpectator()) return;
        Minecraft mc = Minecraft.getInstance();
        if (dummy == null || dummy.level() != p.level()) dummy = new Rabbit(EntityType.RABBIT, p.level());
        Rabbit r = dummy;
        r.setPos(p.getX(), p.getY(), p.getZ());
        r.xo = p.xo; r.yo = p.yo; r.zo = p.zo;
        r.setYRot(p.getYRot()); r.yRotO = p.yRotO;
        r.setXRot(p.getXRot()); r.xRotO = p.xRotO;
        r.yBodyRot = p.yBodyRot; r.yBodyRotO = p.yBodyRotO;
        r.yHeadRot = p.yHeadRot; r.yHeadRotO = p.yHeadRotO;
        r.tickCount = p.tickCount;
        r.setInvisible(p.isInvisible());
        event.setCanceled(true);
        mc.getEntityRenderDispatcher().getRenderer(r)
                .render(r, Mth.rotLerp(event.getPartialTick(), p.yRotO, p.getYRot()), event.getPartialTick(),
                        event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
    }
}
