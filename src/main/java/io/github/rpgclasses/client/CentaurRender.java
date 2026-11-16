package io.github.rpgclasses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.content.race.RaceTraits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.client.event.RenderPlayerEvent;

/**
 * Cuerpo de caballo del Centauro (adenda 0.7, 6.1). El jugador se dibuja como siempre (brazos, armadura, objetos y
 * capa incluidos), pero sin piernas y subido y adelantado sobre la parte delantera de un cuerpo de caballo, que se
 * dibuja aparte con las patas animadas por el paso del jugador y un pelaje según su UUID. Las grebas y las botas no
 * se dibujan (mixin de la capa de armadura). Medidas a ajustar en el juego [MEDIR].
 */
public final class CentaurRender {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(RpgClasses.id("centaur_body"), "main");
    private static final String[] COATS = {"white", "creamy", "chestnut", "brown", "black", "gray", "darkbrown"};

    /** Escala del cuerpo de caballo (un caballo vanilla se dibuja a 1,1). */
    private static final float HORSE_SCALE = 0.8f;
    /** Cuánto se sube y se adelanta el torso humano, en bloques (la cintura queda sobre el pecho del caballo). */
    private static final float HUMAN_UP = 0.34f, HUMAN_FORWARD = 0.42f;
    /** Escala del torso humano. */
    private static final float HUMAN_SCALE = 0.92f;

    private static ModelPart body, tail, leftHind, rightHind, leftFront, rightFront;
    private static boolean pushed;
    private static boolean hidingLegs;

    private CentaurRender() {}

    /** Malla del cuerpo de caballo: tronco, cola y patas del caballo vanilla (misma textura, 64×64). */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition b = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-5.0f, -8.0f, -17.0f, 10.0f, 10.0f, 22.0f, new CubeDeformation(0.05f)), PartPose.offset(0.0f, 11.0f, 5.0f));
        b.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(42, 36).addBox(-1.5f, 0.0f, 0.0f, 3.0f, 14.0f, 4.0f),
                PartPose.offsetAndRotation(0.0f, -5.0f, 2.0f, (float) Math.PI / 6f, 0.0f, 0.0f));
        root.addOrReplaceChild("left_hind_leg", CubeListBuilder.create().texOffs(48, 21).mirror()
                .addBox(-3.0f, -1.01f, -1.0f, 4.0f, 11.0f, 4.0f), PartPose.offset(4.0f, 14.0f, 7.0f));
        root.addOrReplaceChild("right_hind_leg", CubeListBuilder.create().texOffs(48, 21)
                .addBox(-1.0f, -1.01f, -1.0f, 4.0f, 11.0f, 4.0f), PartPose.offset(-4.0f, 14.0f, 7.0f));
        root.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(48, 21).mirror()
                .addBox(-3.0f, -1.01f, -1.9f, 4.0f, 11.0f, 4.0f), PartPose.offset(4.0f, 14.0f, -12.0f));
        root.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(48, 21)
                .addBox(-1.0f, -1.01f, -1.9f, 4.0f, 11.0f, 4.0f), PartPose.offset(-4.0f, 14.0f, -12.0f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static boolean bake() {
        if (body != null) return true;
        try {
            ModelPart root = Minecraft.getInstance().getEntityModels().bakeLayer(LAYER);
            body = root.getChild("body");
            tail = body.getChild("tail");
            leftHind = root.getChild("left_hind_leg");
            rightHind = root.getChild("right_hind_leg");
            leftFront = root.getChild("left_front_leg");
            rightFront = root.getChild("right_front_leg");
            return true;
        } catch (RuntimeException e) {
            RpgClasses.LOGGER.warn("[rpgclasses] Cuerpo de centauro no disponible: {}", e.toString());
            return false;
        }
    }

    /** Al recargar los recursos se vuelve a hornear. */
    public static void reset() {
        body = null;
    }

    /** La capa de armadura lo consulta: mientras se dibuja un centauro, sin grebas ni botas. */
    public static boolean hidingLegs() {
        return hidingLegs;
    }

    /** El cuerpo de caballo se dibuja para este jugador (y por tanto sus piernas humanas no). */
    public static boolean drawsBody(AbstractClientPlayer p) {
        if (!RaceTraits.isCentaur(p) || p.isSpectator()) return false;
        Pose pose = p.getPose();
        return pose == Pose.STANDING || pose == Pose.CROUCHING;
    }

    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        hidingLegs = false;
        if (!(event.getEntity() instanceof AbstractClientPlayer p) || !drawsBody(p) || !bake()) return;
        float pt = event.getPartialTick();
        PoseStack ps = event.getPoseStack();
        float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);

        // Cuerpo de caballo (como lo colocaría LivingEntityRenderer).
        if (!p.isInvisible()) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
            ps.scale(-HORSE_SCALE, -HORSE_SCALE, HORSE_SCALE);
            ps.translate(0.0f, -1.501f, 0.0f);
            boolean moving = !p.isPassenger() && p.isAlive();
            float pos = moving ? p.walkAnimation.position(pt) : 0f;
            float speed = moving ? Math.min(1f, p.walkAnimation.speed(pt)) : 0f;
            float swing = Mth.cos(pos * 0.6662f) * 1.4f * speed;
            rightHind.xRot = swing;
            leftHind.xRot = -swing;
            rightFront.xRot = -swing;
            leftFront.xRot = swing;
            tail.yRot = Mth.cos((p.tickCount + pt) * 0.08f) * 0.12f;
            tail.xRot = (float) Math.PI / 6f + speed * 0.6f;
            VertexConsumer vc = event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(coat(p)));
            int light = event.getPackedLight();
            int overlay = LivingEntityRenderer.getOverlayCoords(p, 0f);
            body.render(ps, vc, light, overlay);
            leftHind.render(ps, vc, light, overlay);
            rightHind.render(ps, vc, light, overlay);
            leftFront.render(ps, vc, light, overlay);
            rightFront.render(ps, vc, light, overlay);
            ps.popPose();
        }

        // Torso humano: sin piernas, subido y adelantado (en coordenadas del mundo según el giro del cuerpo).
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        model.leftLeg.visible = false;
        model.rightLeg.visible = false;
        model.leftPants.visible = false;
        model.rightPants.visible = false;
        hidingLegs = true;
        double yaw = Math.toRadians(bodyYaw);
        ps.pushPose();
        ps.translate(-Math.sin(yaw) * HUMAN_FORWARD, HUMAN_UP, Math.cos(yaw) * HUMAN_FORWARD);
        ps.scale(HUMAN_SCALE, HUMAN_SCALE, HUMAN_SCALE);
        pushed = true;
    }

    public static void onRenderPost(RenderPlayerEvent.Post event) {
        hidingLegs = false;
        if (!pushed) return;
        pushed = false;
        event.getPoseStack().popPose();
    }

    private static ResourceLocation coat(AbstractClientPlayer p) {
        String c = COATS[Math.floorMod(p.getUUID().hashCode(), COATS.length)];
        return new ResourceLocation("textures/entity/horse/horse_" + c + ".png");
    }
}
