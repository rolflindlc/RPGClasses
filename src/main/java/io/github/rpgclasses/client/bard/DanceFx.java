package io.github.rpgclasses.client.bard;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.rpgclasses.network.Packets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Baile Forzado en el cliente: el baile de sacudir los hombros (torso de lado a lado, brazos doblados y rebote
 * vertical). El rebote y el balanceo del cuerpo valen para cualquier criatura; los brazos, para los modelos humanoides
 * (mixin en {@code HumanoidModel.setupAnim}). Si el jugador local baila, no puede moverse, atacar ni usar objetos.
 * Las criaturas que Epic Fight dibuja con su propio animador solo se quedan quietas [verificar en el juego].
 */
public final class DanceFx {
    /** Entidad → tick de juego (cliente) en que deja de bailar. */
    private static final Map<Integer, Long> DANCING = new HashMap<>();
    private static final Set<Integer> PUSHED = new HashSet<>();

    private DanceFx() {}

    public static void onSync(Packets.DanceSync msg) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        DANCING.put(msg.entityId(), level.getGameTime() + msg.ticks());
    }

    public static boolean dancing(Entity e) {
        Long end = DANCING.get(e.getId());
        return end != null && e.level() != null && end > e.level().getGameTime();
    }

    public static boolean localDancing() {
        var p = Minecraft.getInstance().player;
        return p != null && dancing(p);
    }

    public static void reset() {
        DANCING.clear();
        PUSHED.clear();
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || DANCING.isEmpty()) return;
        var level = Minecraft.getInstance().level;
        if (level == null) {
            DANCING.clear();
            return;
        }
        long now = level.getGameTime();
        DANCING.values().removeIf(end -> end <= now);
    }

    private static float time(LivingEntity e, float partial) {
        return (e.tickCount + partial) * 0.55f;
    }

    /** Rebote y balanceo del cuerpo entero. */
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity e = event.getEntity();
        if (!dancing(e)) return;
        float t = time(e, event.getPartialTick());
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        PUSHED.add(e.getId());
        pose.translate(0, Math.abs(Mth.sin(t)) * 0.12 * e.getBbHeight() / 1.8, 0);
        pose.mulPose(Axis.ZP.rotation(Mth.sin(t) * 0.10f));
    }

    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (PUSHED.remove(event.getEntity().getId())) event.getPoseStack().popPose();
    }

    /** Brazos doblados que suben y bajan alternados y torso de lado a lado (lo llama el mixin del modelo). */
    public static void pose(LivingEntity e, float ageInTicks, HumanoidModel<?> model) {
        if (!dancing(e)) return;
        float t = ageInTicks * 0.55f;
        float s = Mth.sin(t);
        model.rightArm.xRot = -1.25f + s * 0.45f;
        model.leftArm.xRot = -1.25f - s * 0.45f;
        model.rightArm.zRot = 0.25f;
        model.leftArm.zRot = -0.25f;
        model.rightArm.yRot = -0.2f;
        model.leftArm.yRot = 0.2f;
        model.body.yRot = s * 0.25f;
        model.head.zRot = s * 0.12f;
    }

    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!localDancing()) return;
        var in = event.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!localDancing()) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }
}
