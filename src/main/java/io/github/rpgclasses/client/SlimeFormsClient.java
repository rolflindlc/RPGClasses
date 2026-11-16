package io.github.rpgclasses.client;

import io.github.rpgclasses.content.race.MitosisCopy;
import io.github.rpgclasses.content.race.SlimeForms;
import io.github.rpgclasses.content.race.SwarmSlime;
import io.github.rpgclasses.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cliente de las formas del Slime: el jugador dividido en el Enjambre se dibuja como un slime pequeño (para todos, y
 * sin manos en primera persona), y el Mitótico ve la barra de fusión mientras está agachado junto a su copia.
 */
public final class SlimeFormsClient {
    /** Aplastamiento al botar de cada jugador-slime: {squish, oSquish, targetSquish, wasOnGround}. */
    private static final Map<UUID, float[]> SQUISH = new HashMap<>();
    private static SwarmSlime dummy;
    private static int mergeTicks;

    private SlimeFormsClient() {}

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            SQUISH.clear();
            dummy = null;
            return;
        }
        for (Player p : mc.level.players()) {
            if (!SlimeForms.isSwarming(p)) {
                SQUISH.remove(p.getUUID());
                continue;
            }
            float[] s = SQUISH.computeIfAbsent(p.getUUID(), k -> new float[]{0, 0, 0, 1});
            s[0] += (s[2] - s[0]) * 0.5f;
            s[1] = s[0];
            boolean ground = p.onGround();
            if (ground && s[3] == 0) s[2] = -0.5f;
            else if (!ground && s[3] == 1) s[2] = 1.0f;
            s[3] = ground ? 1 : 0;
            s[2] *= 0.6f;
        }
        mergeTick(mc.player);
    }

    /** Barra de fusión del Mitótico: agachado a 2 bloques o menos de su copia. */
    private static void mergeTick(LocalPlayer p) {
        if (p == null || !SlimeForms.isSplit(p) || !p.isShiftKeyDown()) {
            mergeTicks = 0;
            return;
        }
        boolean close = !p.level().getEntitiesOfClass(MitosisCopy.class, p.getBoundingBox().inflate(2.5),
                c -> c.isAlive() && c.getOwnerUuid().filter(id -> id.equals(p.getUUID())).isPresent() && c.distanceToSqr(p) <= 4).isEmpty();
        mergeTicks = close ? mergeTicks + 1 : 0;
    }

    /** El jugador dividido se dibuja como un slime pequeño. */
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        if (!SlimeForms.isSwarming(p)) return;
        event.setCanceled(true);
        if (p.isInvisible()) return;
        Minecraft mc = Minecraft.getInstance();
        if (dummy == null || dummy.level() != p.level()) {
            dummy = ModEntities.SWARM_SLIME.get().create(p.level());
            if (dummy == null) return;
        }
        float[] s = SQUISH.getOrDefault(p.getUUID(), new float[]{0, 0, 0, 1});
        dummy.squish = s[0];
        dummy.oSquish = s[1];
        float yaw = p.yBodyRot;
        dummy.setYRot(yaw);
        dummy.yRotO = yaw;
        dummy.yBodyRot = yaw;
        dummy.yBodyRotO = yaw;
        dummy.yHeadRot = yaw;
        dummy.yHeadRotO = yaw;
        dummy.hurtTime = p.hurtTime;
        dummy.setOnGround(p.onGround());
        mc.getEntityRenderDispatcher().getRenderer(dummy).render(dummy, yaw, event.getPartialTick(), event.getPoseStack(),
                event.getMultiBufferSource(), event.getPackedLight());
    }

    /** Sin manos en primera persona mientras es un slime. */
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && SlimeForms.isSwarming(mc.player)) event.setCanceled(true);
    }

    /** Barra de fusión del Mitótico, encima de la barra de experiencia. */
    public static void renderHud(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mergeTicks <= 0) return;
        float frac = Math.min(1f, mergeTicks / (float) Math.max(1, SlimeForms.mergeTicks(mc.player)));
        int x = width / 2 - 91, y = height - 35;
        g.fill(x, y, x + 182, y + 5, 0xFF000000);
        g.fill(x + 1, y + 1, x + 181, y + 4, 0xFF303030);
        g.fill(x + 1, y + 1, x + 1 + (int) (180 * frac), y + 4, frac >= 1f ? 0xFF55FF55 : 0xFF7CDB5A);
    }
}
