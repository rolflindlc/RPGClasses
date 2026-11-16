package io.github.rpgclasses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.rpgclasses.content.race.PorterBelt;
import io.github.rpgclasses.content.race.RaceTraits;
import io.github.rpgclasses.content.race.Races;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bloques del cinturón del Coloso Porteador (adenda 0.7, 4.3) dibujados en pequeño alrededor de su cintura: delante, a
 * los lados y detrás, en el orden de los huecos. Se dibujan al pintar al jugador (no como capa del modelo), así se ven
 * también con Epic Fight, que dibuja el cuerpo con su propia malla. Los manda el servidor ({@code Packets.BeltVisual})
 * al cambiar el cinturón y a quien empieza a ver al Porteador. También recoge el gesto Ctrl + Mayús + clic derecho
 * (guardar el bloque de la mano en el cinturón).
 */
public final class BeltLayer {
    private static final Map<Integer, List<ItemStack>> BELTS = new HashMap<>();
    /** Posición de cada hueco en la cintura (x, z en bloques del modelo; delante = −z) y su giro. */
    private static final float[][] SPOTS = {
            {-0.17f, -0.2f, 0}, {0.17f, -0.2f, 0}, {-0.33f, 0f, 90}, {0.33f, 0f, -90}, {-0.15f, 0.2f, 180}, {0.15f, 0.2f, 180}};
    /** Altura de la cintura bajo el cuello (el modelo vanilla mide 1,5 de cuello a pies). */
    private static final float WAIST_Y = 0.68f;
    private static final float BLOCK_SCALE = 0.3f;

    private BeltLayer() {}

    public static void receive(int entityId, List<ItemStack> blocks) {
        if (blocks.isEmpty()) BELTS.remove(entityId);
        else BELTS.put(entityId, List.copyOf(blocks));
    }

    public static void reset() {
        BELTS.clear();
    }

    /** Antes de dibujar al jugador: los bloques en su cintura (el tamaño racial aún no se aplica en este punto). */
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) return;
        List<ItemStack> blocks = BELTS.get(player.getId());
        if (blocks == null || blocks.isEmpty() || player.isInvisible() || player.isSpectator()) return;
        // Solo de pie o agachado (nadando, durmiendo o planeando con élitros la cintura no está ahí).
        var pose = player.getPose();
        if (pose != net.minecraft.world.entity.Pose.STANDING && pose != net.minecraft.world.entity.Pose.CROUCHING) return;
        Minecraft mc = Minecraft.getInstance();
        PoseStack ps = event.getPoseStack();
        float bodyYaw = Mth.rotLerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot);
        float s = RaceTraits.scale(player);
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0f);
        float crouch = player.isCrouching() ? 0.12f : 0f;
        for (int i = 0; i < Math.min(blocks.size(), SPOTS.length); i++) {
            float[] spot = SPOTS[i];
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
            ps.scale(-s * 0.9375f, -s * 0.9375f, s * 0.9375f); // como PlayerRenderer.scale
            ps.translate(0.0f, -1.501f, 0.0f);
            ps.translate(spot[0], WAIST_Y + crouch, spot[1]);
            ps.mulPose(Axis.YP.rotationDegrees(spot[2]));
            ps.scale(BLOCK_SCALE, -BLOCK_SCALE, -BLOCK_SCALE);
            mc.getItemRenderer().renderStatic(player, blocks.get(i), ItemDisplayContext.FIXED, false, ps, event.getMultiBufferSource(),
                    player.level(), event.getPackedLight(), overlay, player.getId() + i);
            ps.popPose();
        }
    }

    /** Ctrl + Mayús + clic derecho con un bloque admitido en la mano: se guarda en el cinturón (lo hace el servidor). */
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        tryInsert(event, event.getEntity(), event.getHand());
    }

    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        tryInsert(event, event.getEntity(), event.getHand());
    }

    private static void tryInsert(PlayerInteractEvent event, net.minecraft.world.entity.player.Player p, InteractionHand hand) {
        if (!(p instanceof LocalPlayer lp) || hand != InteractionHand.MAIN_HAND || !p.level().isClientSide()) return;
        if (!Screen.hasControlDown() || !lp.isShiftKeyDown() || !Races.has(lp, "race_belt") || !PorterBelt.allowed(lp.getMainHandItem())) return;
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        // Un clic puede lanzar el evento de bloque y el de objeto en el mismo tick: solo se manda un paquete.
        if (lp.tickCount == lastGestureTick) return;
        lastGestureTick = lp.tickCount;
        io.github.rpgclasses.network.Net.CHANNEL.sendToServer(new io.github.rpgclasses.network.Packets.BeltInsert());
    }

    private static int lastGestureTick = Integer.MIN_VALUE;
}
