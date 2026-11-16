package io.github.rpgclasses.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraftforge.common.MinecraftForge;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.client.forgeevent.PrepareModelEvent;
import yesman.epicfight.api.client.forgeevent.UpdatePlayerMotionEvent;
import yesman.epicfight.client.mesh.HumanoidMesh;

/**
 * Centauro con Epic Fight: este mod dibuja al jugador con su propia malla, que ignora la visibilidad de las piezas de
 * vanilla. Aquí se ocultan las piernas de esa malla y se evita la animación de agachado (que echa el torso hacia
 * atrás). Solo se carga si Epic Fight está presente.
 */
final class CentaurEpicFight {
    private CentaurEpicFight() {}

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(CentaurEpicFight::onPrepareModel);
        MinecraftForge.EVENT_BUS.addListener(CentaurEpicFight::onMotion);
    }

    private static AbstractClientPlayer centaur(Object original) {
        return original instanceof AbstractClientPlayer p && CentaurRender.drawsBody(p) ? p : null;
    }

    private static void onPrepareModel(PrepareModelEvent event) {
        if (event.getEntityPatch() == null || centaur(event.getEntityPatch().getOriginal()) == null) return;
        if (event.getMesh() instanceof HumanoidMesh m) {
            m.leftLeg.setHidden(true);
            m.rightLeg.setHidden(true);
            m.leftPants.setHidden(true);
            m.rightPants.setHidden(true);
        }
    }

    private static void onMotion(UpdatePlayerMotionEvent.BaseLayer event) {
        if (event.getMotion() != LivingMotions.SNEAK) return;
        AbstractClientPlayer p = centaur(event.getPlayerPatch().getOriginal());
        if (p == null) return;
        double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
        event.setMotion(dx * dx + dz * dz > 1.0E-4 ? LivingMotions.WALK : LivingMotions.IDLE);
    }
}
