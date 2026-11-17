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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RenderPlayerEvent;

/**
 * Cuerpo de caballo del Centauro (adenda 0.7, 6.1). El jugador se dibuja como siempre (brazos, armadura, objetos y
 * capa incluidos), pero sin piernas y subido y adelantado sobre la parte delantera de un cuerpo de caballo, que se
 * dibuja aparte con las patas animadas por el paso del jugador y un pelaje según su UUID. Las grebas y las botas no
 * se dibujan (mixin de la capa de armadura). Medidas a ajustar en el juego [MEDIR].
 */
public final class CentaurRender {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(RpgClasses.id("centaur_body"), "main");
    /** Grebas y botas sobre las patas del caballo: 4 patas de grebas y 4 de botas (texturas de armadura humanas). */
    public static final ModelLayerLocation ARMOR_LAYER = new ModelLayerLocation(RpgClasses.id("centaur_armor"), "main");
    private static final String[] LEG_NAMES = {"left_hind_leg", "right_hind_leg", "left_front_leg", "right_front_leg"};
    private static final float[][] LEG_POS = {{4f, 14f, 7f}, {-4f, 14f, 7f}, {4f, 14f, -12f}, {-4f, 14f, -12f}};
    private static ModelPart[] legArmor, bootArmor;
    private static ItemStack stashedLegs = ItemStack.EMPTY, stashedFeet = ItemStack.EMPTY;
    /** Jugador al que se le quitaron las grebas y botas para dibujarlas en las patas (se le devuelven en Post). */
    private static AbstractClientPlayer stashedFor;
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

    /** Capa de las grebas y botas: cuatro patas con la UV de la pierna humana (0,16) y distinta hinchazón. */
    public static LayerDefinition createArmorLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        for (int i = 0; i < 4; i++) {
            float[] o = LEG_POS[i];
            boolean left = i % 2 == 0;
            float x0 = left ? -3.0f : -1.0f;
            float z0 = i < 2 ? -1.0f : -1.9f;
            root.addOrReplaceChild("legs_" + LEG_NAMES[i], CubeListBuilder.create().texOffs(0, 16).mirror(left)
                    .addBox(x0, -1.01f, z0, 4.0f, 11.0f, 4.0f, new CubeDeformation(0.25f)),
                    PartPose.offset(o[0], o[1], o[2]));
            root.addOrReplaceChild("feet_" + LEG_NAMES[i], CubeListBuilder.create().texOffs(0, 16).mirror(left)
                    .addBox(x0, -1.01f, z0, 4.0f, 11.0f, 4.0f, new CubeDeformation(0.4f)),
                    PartPose.offset(o[0], o[1], o[2]));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static boolean bake() {
        if (body != null) return true;
        try {
            ModelPart armorRoot = Minecraft.getInstance().getEntityModels().bakeLayer(ARMOR_LAYER);
            legArmor = new ModelPart[4];
            bootArmor = new ModelPart[4];
            for (int i = 0; i < 4; i++) {
                legArmor[i] = armorRoot.getChild("legs_" + LEG_NAMES[i]);
                bootArmor[i] = armorRoot.getChild("feet_" + LEG_NAMES[i]);
            }
            ModelPart root = Minecraft.getInstance().getEntityModels().bakeLayer(LAYER);
            body = root.getChild("body");
            tail = body.getChild("tail");
            leftHind = root.getChild("left_hind_leg");
            rightHind = root.getChild("right_hind_leg");
            leftFront = root.getChild("left_front_leg");
            rightFront = root.getChild("right_front_leg");
            return true;
        } catch (RuntimeException e) {
            RpgClasses.LOGGER.warn("[rites_and_paths] Cuerpo de centauro no disponible: {}", e.toString());
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
            drawLegArmor(p, ps, event, light, overlay);
            ps.popPose();
        }

        // Torso humano: sin piernas, subido y adelantado (en coordenadas del mundo según el giro del cuerpo).
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        model.leftLeg.visible = false;
        model.rightLeg.visible = false;
        model.leftPants.visible = false;
        model.rightPants.visible = false;
        hidingLegs = true;
        restoreArmor();
        stashedLegs = p.getItemBySlot(EquipmentSlot.LEGS);
        stashedFeet = p.getItemBySlot(EquipmentSlot.FEET);
        if (!stashedLegs.isEmpty() || !stashedFeet.isEmpty()) {
            stashedFor = p;
            p.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            p.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        }
        double yaw = Math.toRadians(bodyYaw);
        ps.pushPose();
        ps.translate(-Math.sin(yaw) * HUMAN_FORWARD, HUMAN_UP, Math.cos(yaw) * HUMAN_FORWARD);
        ps.scale(HUMAN_SCALE, HUMAN_SCALE, HUMAN_SCALE);
        pushed = true;
    }

    /** Devuelve las grebas y botas al jugador al que se le quitaron (aunque otro render se haya cortado a medias). */
    private static void restoreArmor() {
        AbstractClientPlayer p = stashedFor;
        if (p == null) return;
        stashedFor = null;
        p.setItemSlot(EquipmentSlot.LEGS, stashedLegs);
        p.setItemSlot(EquipmentSlot.FEET, stashedFeet);
        stashedLegs = stashedFeet = ItemStack.EMPTY;
    }

    private static void drawLegArmor(AbstractClientPlayer p, PoseStack ps, RenderPlayerEvent.Pre event, int light, int overlay) {
        ModelPart[] hooves = {leftHind, rightHind, leftFront, rightFront};
        for (int i = 0; i < 4; i++) {
            legArmor[i].xRot = bootArmor[i].xRot = hooves[i].xRot;
        }
        drawPiece(p, ps, event, light, overlay, p.getItemBySlot(EquipmentSlot.LEGS), EquipmentSlot.LEGS, legArmor);
        drawPiece(p, ps, event, light, overlay, p.getItemBySlot(EquipmentSlot.FEET), EquipmentSlot.FEET, bootArmor);
    }

    private static void drawPiece(AbstractClientPlayer p, PoseStack ps, RenderPlayerEvent.Pre event, int light, int overlay,
                                  ItemStack stack, EquipmentSlot slot, ModelPart[] parts) {
        if (!(stack.getItem() instanceof ArmorItem armor) || armor.getEquipmentSlot() != slot) return;
        String mat = armor.getMaterial().getName();
        int colon = mat.indexOf(':');
        String domain = colon >= 0 ? mat.substring(0, colon) : "minecraft";
        String name = colon >= 0 ? mat.substring(colon + 1) : mat;
        String base = domain + ":textures/models/armor/" + name + "_layer_" + (slot == EquipmentSlot.LEGS ? 2 : 1);
        float r = 1f, g = 1f, b = 1f;
        if (armor instanceof DyeableLeatherItem dye) {
            int c = dye.getColor(stack);
            r = (c >> 16 & 255) / 255f; g = (c >> 8 & 255) / 255f; b = (c & 255) / 255f;
        }
        ResourceLocation tex = new ResourceLocation(ForgeHooksClient.getArmorTexture(p, stack, base + ".png", slot, null));
        var buf = event.getMultiBufferSource();
        for (ModelPart part : parts) part.render(ps, buf.getBuffer(RenderType.armorCutoutNoCull(tex)), light, overlay, r, g, b, 1f);
        if (armor instanceof DyeableLeatherItem) {
            ResourceLocation over = new ResourceLocation(base + "_overlay.png");
            for (ModelPart part : parts) part.render(ps, buf.getBuffer(RenderType.armorCutoutNoCull(over)), light, overlay, 1f, 1f, 1f, 1f);
        }
        if (stack.hasFoil()) {
            for (ModelPart part : parts) part.render(ps, buf.getBuffer(RenderType.armorEntityGlint()), light, overlay, 1f, 1f, 1f, 1f);
        }
    }

    public static void onRenderPost(RenderPlayerEvent.Post event) {
        restoreArmor();
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
