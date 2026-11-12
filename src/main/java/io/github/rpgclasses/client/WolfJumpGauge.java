package io.github.rpgclasses.client;

import io.github.rpgclasses.content.race.WolfRider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Barra de carga del salto del lobo montado, en el sitio de la barra de XP (como la del caballo).
 * El cliente cuenta los ticks que se mantiene el salto con la misma fórmula que el servidor ({@link WolfRider#chargeFraction}).
 */
public final class WolfJumpGauge {
    private static int held;

    private WolfJumpGauge() {}

    /** Llamado en cada tick de cliente. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        boolean charging = p != null && mc.level != null && mc.screen == null && mc.options.keyJump.isDown()
                && p.getVehicle() instanceof Wolf wolf && wolf.isTame() && p.getUUID().equals(wolf.getOwnerUUID())
                && wolf.onGround();
        held = charging ? Math.min(WolfRider.CHARGE_TICKS, held + 1) : 0;
    }

    public static void renderHud(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (held <= 0 || mc.player == null || mc.options.hideGui) return;
        int x = width / 2 - 91;
        int y = height - 29;
        float fraction = WolfRider.chargeFraction(held);
        g.fill(x, y, x + 182, y + 5, 0xFF000000);
        g.fill(x + 1, y + 1, x + 181, y + 4, 0xFF303030);
        g.fill(x + 1, y + 1, x + 1 + (int) (180 * fraction), y + 4, fraction >= 1f ? 0xFF55FF55 : 0xFFE0C040);
    }
}
