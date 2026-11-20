package io.github.rpgclasses.client.cleric;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.rpgclasses.cleric.Cleric;
import io.github.rpgclasses.client.AbilityHud;
import io.github.rpgclasses.client.ClientHandlers;
import io.github.rpgclasses.client.ClientSetup;
import io.github.rpgclasses.core.ability.UnlockResolver;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import io.github.rpgclasses.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente del Clérigo.
 * <ul>
 *   <li><b>Ojo del Sanador:</b> vida de los aliados (jugadores y mascotas) a 32 bloques sobre sus cabezas (se ve a través de las paredes) y brillo
 *       (contorno) de los que bajan del 40 %. Los tumbados muestran su cuenta atrás. Solo lo ve el Clérigo.</li>
 *   <li><b>Tumbado (Milagro):</b> el jugador se dibuja acostado (postura de dormir sin cama), no puede moverse ni
 *       actuar, y su pantalla muestra la cuenta atrás.</li>
 *   <li><b>Santuario:</b> esfera dorada translúcida, sin postprocesado (funciona con shaders), que sigue a su ancla.
 *       Se agrieta (y parpadea al final) a medida que pierde vida.
 *       Al soltar la tecla de la ranura del Santuario se avisa al servidor.</li>
 *   <li><b>Plegaria:</b> el jugador que reza es una cabeza semitransparente que flota junto al aliado (también la ven
 *       los demás); vuela libremente sin alejarse más de {@code spirit_range} bloques del aliado. Su cuerpo agachado es
 *       una entidad aparte ({@link PrayingBodyRenderer}). La lista de aliados es {@link PrayerScreen}.</li>
 *   <li><b>Ángel:</b> sin correr en el aire durante la Forma Angelical; caída lenta con Gracia Alada (salto pulsado).</li>
 * </ul>
 */
public final class ClericClient {
    private static final int SAFETY_TICKS = 20;
    /** Tumbados: id de entidad → tick (del cliente) en que acaba. */
    private static final Map<Integer, Long> DOWNED = new HashMap<>();

    private record Sphere(int anchorId, float radius, long start, int expandTicks, long end, float shield, List<float[][]> cracks) {}

    private static final Map<Integer, Sphere> SPHERES = new HashMap<>();
    private static int prayerTarget = -1;
    private static float prayerRange = 15;
    /** Modelo de cabeza de jugador para dibujar a los que rezan (se crea al primer uso). */
    private static net.minecraft.client.model.SkullModel spiritModel;
    /** Transparencia de la cabeza. */
    private static final float SPIRIT_ALPHA = 0.55f;
    private static boolean healerEye;
    private static final boolean[] KEY_DOWN = new boolean[3];
    /** Jugador que se está dibujando acostado y su pose real. */
    private static Player posed;
    private static Pose savedPose;

    private ClericClient() {}

    public static void register(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.addListener(ClericClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ClericClient::onMovementInput);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ClericClient::onInteractionKey);
        MinecraftForge.EVENT_BUS.addListener(ClericClient::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener(ClericClient::onRenderLiving);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ClericClient::onRenderSpirit);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, ClericClient::onRenderPlayerPre);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ClericClient::onRenderPlayerPost);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> reset());
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.Clone event) -> {
            prayerTarget = -1;
            io.github.rpgclasses.cleric.Prayer.CLIENT_SPIRITS.clear();
            DOWNED.clear();
        });
        modBus.addListener((RegisterGuiOverlaysEvent event) -> event.registerAboveAll("cleric_downed", ClericClient::renderHud));
        // Rezando eres una cabeza: ni mano en primera persona ni contorno de bloques (no puedes usarlos).
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RenderHandEvent event) -> {
            if (prayerTarget >= 0) event.setCanceled(true);
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RenderHighlightEvent.Block event) -> {
            if (prayerTarget >= 0) event.setCanceled(true);
        });
    }

    private static void reset() {
        DOWNED.clear();
        SPHERES.clear();
        prayerTarget = -1;
        io.github.rpgclasses.cleric.Prayer.CLIENT_SPIRITS.clear();
        healerEye = false;
    }

    // ------------------------------------------------------------------ paquetes

    public static void onDowned(Packets.ClericDowned msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (msg.ticks() <= 0) DOWNED.remove(msg.entityId());
        else DOWNED.put(msg.entityId(), mc.level.getGameTime() + msg.ticks());
    }

    public static void onSanctuary(Packets.SanctuarySync msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (msg.ticks() <= 0) {
            SPHERES.remove(msg.id());
            return;
        }
        long now = mc.level.getGameTime();
        Sphere old = SPHERES.get(msg.id());
        long start = old != null ? old.start() : now;
        List<float[][]> cracks = old != null ? old.cracks() : cracks(msg.id());
        SPHERES.put(msg.id(), new Sphere(msg.anchorId(), msg.radius(), start, Math.max(1, msg.expandTicks()),
                now + msg.ticks() + SAFETY_TICKS, Mth.clamp(msg.shield(), 0f, 1f), cracks));
    }

    public static void onPrayerState(Packets.PrayerState msg) {
        Minecraft mc = Minecraft.getInstance();
        var spirits = io.github.rpgclasses.cleric.Prayer.CLIENT_SPIRITS;
        if (msg.targetId() >= 0 && msg.size() > 0) spirits.put(msg.entityId(), msg.size());
        else spirits.remove(msg.entityId());
        Entity e = mc.level == null ? null : mc.level.getEntity(msg.entityId());
        if (e != null) e.refreshDimensions();
        if (mc.player == null || msg.entityId() != mc.player.getId()) return;
        prayerTarget = msg.targetId();
        prayerRange = msg.range() <= 0 ? 15 : msg.range();
        if (prayerTarget < 0 && mc.screen instanceof PrayerScreen) mc.setScreen(null);
    }

    public static void onPrayerList(Packets.PrayerList msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null || mc.screen instanceof PrayerScreen) mc.setScreen(new PrayerScreen(msg));
    }

    // ------------------------------------------------------------------ estado

    public static boolean downed(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (e == null || mc.level == null) return false;
        Long end = DOWNED.get(e.getId());
        return end != null && mc.level.getGameTime() < end;
    }

    private static int downedSeconds(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        Long end = DOWNED.get(e.getId());
        return end == null || mc.level == null ? 0 : (int) Math.ceil(Math.max(0, end - mc.level.getGameTime()) / 20.0);
    }

    public static boolean praying() {
        return prayerTarget >= 0;
    }

    /** Contorno brillante (Ojo del Sanador): aliado a 32 bloques por debajo del 40 % de vida. */
    public static boolean glows(Entity e) {
        if (!healerEye || !(e instanceof LivingEntity p) || p == Minecraft.getInstance().player) return false;
        if (!ClientHandlers.isAllyEntity(p)) return false;
        Minecraft mc = Minecraft.getInstance();
        double range = number(Cleric.HEALER_EYE, "range", 32);
        if (mc.player == null || p.distanceToSqr(mc.player) > range * range) return false;
        return downed(p) || p.getHealth() < p.getMaxHealth() * number(Cleric.HEALER_EYE, "low_health", 0.4);
    }

    private static double number(String ability, String key, double fallback) {
        var def = DefinitionStore.CLIENT.abilityDef(ability);
        return def == null || !def.params().has(key) ? fallback : def.params().get(key).getAsDouble();
    }

    private static boolean localHas(String ability) {
        var s = ClientHandlers.LOCAL_DATA.state;
        var c = DefinitionStore.CLIENT.classDef(s.classId);
        return c != null && UnlockResolver.unlocked(s, c, DefinitionStore.CLIENT.subclassDef(s.subclassId)).contains(ability);
    }

    // ------------------------------------------------------------------ tick y entrada

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }
        long now = mc.level.getGameTime();
        DOWNED.values().removeIf(end -> now >= end + SAFETY_TICKS);
        SPHERES.values().removeIf(s -> now >= s.end());
        healerEye = localHas(Cleric.HEALER_EYE);
        prayerTick(mc);

        // Santuario: avisar al soltar su tecla.
        List<String> actives = AbilityHud.localActives();
        for (int i = 0; i < KEY_DOWN.length && i < ClientSetup.ABILITY_KEYS.length; i++) {
            boolean down = ClientSetup.ABILITY_KEYS[i].isDown();
            if (KEY_DOWN[i] && !down && i < actives.size() && Cleric.SANCTUARY.equals(actives.get(i))) {
                Net.CHANNEL.sendToServer(new Packets.AbilityRelease(i));
            }
            KEY_DOWN[i] = down;
        }

        // Forma Angelical: vuela a paso de caminar (sin correr en el aire).
        Player p = mc.player;
        if (p.hasEffect(ModEffects.ANGELIC_FORM.get()) && p.getAbilities().flying && p.isSprinting()) p.setSprinting(false);
        // Gracia Alada: caída lenta manteniendo el salto desde el nivel indicado.
        var grace = ClientSetup.localMechanic(io.github.rpgclasses.core.Ids.NS + ":" + Cleric.WINGED_GRACE);
        if (grace != null && grace.level() >= grace.number("slow_fall_level", 5) && mc.screen == null) {
            io.github.rpgclasses.content.MovementPerks.featherFallTick(p, mc.options.keyJump.isDown());
        }
    }

    private static void onMovementInput(MovementInputUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!downed(event.getEntity())) return;
        var in = event.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    // ------------------------------------------------------------------ Plegaria: la cabeza

    /**
     * Cabeza del jugador local: siempre volando (en supervivencia tocar el suelo quita el vuelo) y atada al aliado con
     * una correa de {@code prayerRange} bloques. Además, la hitbox de las cabezas que ve se pone al día si hace falta.
     */
    private static void prayerTick(Minecraft mc) {
        var spirits = io.github.rpgclasses.cleric.Prayer.CLIENT_SPIRITS;
        for (Map.Entry<Integer, Float> e : spirits.entrySet()) {
            Entity en = mc.level.getEntity(e.getKey());
            if (en != null && Math.abs(en.getBbWidth() - e.getValue()) > 1.0E-3f) en.refreshDimensions();
        }
        if (prayerTarget < 0) return;
        Player p = mc.player;
        if (!p.getAbilities().flying) {
            p.getAbilities().mayfly = true;
            p.getAbilities().flying = true;
        }
        Entity ally = mc.level.getEntity(prayerTarget);
        if (ally == null) return;
        Vec3 center = ally.getEyePosition();
        Vec3 head = p.getEyePosition();
        Vec3 off = head.subtract(center);
        double d = off.length();
        if (d <= prayerRange || d < 1.0E-6) return;
        Vec3 n = off.scale(1 / d);
        Vec3 to = center.add(n.scale(prayerRange));
        p.setPos(to.x, to.y - p.getEyeHeight(), to.z);
        Vec3 v = p.getDeltaMovement();
        double out = v.dot(n);
        if (out > 0) p.setDeltaMovement(v.subtract(n.scale(out)));
    }

    /** Los que rezan se dibujan como su cabeza: semitransparente, del tamaño de la hitbox y flotando un poco. */
    private static void onRenderSpirit(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        Float size = io.github.rpgclasses.cleric.Prayer.CLIENT_SPIRITS.get(p.getId());
        if (size == null) return;
        event.setCanceled(true);
        if (!(p instanceof net.minecraft.client.player.AbstractClientPlayer acp)) return;
        Minecraft mc = Minecraft.getInstance();
        if (spiritModel == null) {
            spiritModel = new net.minecraft.client.model.SkullModel(mc.getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_HEAD));
        }
        float pt = event.getPartialTick();
        float yaw = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot);
        float pitch = Mth.lerp(pt, p.xRotO, p.getXRot());
        float bob = Mth.sin((p.tickCount + pt) * 0.1f) * 0.04f;
        float scale = size / 0.5f; // la cabeza del jugador mide medio bloque
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(0, size / 2 + bob, 0);
        ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180 - yaw));
        ps.scale(-1, -1, 1);
        ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitch));
        ps.scale(scale, scale, scale);
        ps.translate(0, 0.25, 0); // el cubo de la cabeza va de -0,5 a 0 en el modelo: se centra
        spiritModel.setupAnim(0, 0, 0);
        var buffer = event.getMultiBufferSource().getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(acp.getSkinTextureLocation()));
        spiritModel.renderToBuffer(ps, buffer, event.getPackedLight(), net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                1f, 1f, 1f, SPIRIT_ALPHA);
        ps.popPose();
    }

    private static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (downed(mc.player) || (prayerTarget >= 0 && (event.isAttack() || event.isPickBlock()))) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    // ------------------------------------------------------------------ dibujo: tumbados

    private static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (event.isCanceled() || !downed(event.getEntity())) return;
        Player p = event.getEntity();
        if (posed != null && posed != p) restorePose();
        if (p.getPose() == Pose.SLEEPING) return;
        posed = p;
        savedPose = p.getPose();
        p.setPose(Pose.SLEEPING);
    }

    private static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (posed == event.getEntity()) restorePose();
    }

    private static void restorePose() {
        if (posed != null && savedPose != null) posed.setPose(savedPose);
        posed = null;
        savedPose = null;
    }

    // ------------------------------------------------------------------ dibujo: Ojo del Sanador

    private static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        if (!healerEye) return;
        LivingEntity target = event.getEntity();
        Minecraft mc = Minecraft.getInstance();
        if (target == mc.player || mc.player == null || target.isInvisible() || !ClientHandlers.isAllyEntity(target)) return;
        double range = number(Cleric.HEALER_EYE, "range", 32);
        if (target.distanceToSqr(mc.player) > range * range) return;
        MutableComponent line;
        if (downed(target)) {
            int secs = downedSeconds(target);
            boolean blink = (mc.level.getGameTime() / 5) % 2 == 0;
            line = Component.literal("✝ " + secs + " s").withStyle(blink ? ChatFormatting.GOLD : ChatFormatting.YELLOW);
        } else {
            float frac = Mth.clamp(target.getHealth() / Math.max(1, target.getMaxHealth()), 0, 1);
            int filled = Math.round(frac * 20);
            ChatFormatting color = frac < number(Cleric.HEALER_EYE, "low_health", 0.4) ? ChatFormatting.RED
                    : frac < 0.7f ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
            line = Component.literal("|".repeat(filled)).withStyle(color)
                    .append(Component.literal("|".repeat(20 - filled)).withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(" " + Math.round(target.getHealth()) + "/" + Math.round(target.getMaxHealth())).withStyle(ChatFormatting.WHITE));
        }
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(0, target.getBbHeight() + 0.8, 0);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(-0.025f, -0.025f, 0.025f);
        Matrix4f m = ps.last().pose();
        Font font = mc.font;
        float x = -font.width(line) / 2f;
        int bg = (int) (mc.options.getBackgroundOpacity(0.25f) * 255.0f) << 24;
        font.drawInBatch(line, x, 0, 0x20FFFFFF, false, m, event.getMultiBufferSource(), Font.DisplayMode.SEE_THROUGH, bg, event.getPackedLight());
        font.drawInBatch(line, x, 0, 0xFFFFFFFF, false, m, event.getMultiBufferSource(), Font.DisplayMode.NORMAL, 0, event.getPackedLight());
        ps.popPose();
    }

    // ------------------------------------------------------------------ dibujo: Santuario

    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SPHERES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        float now = mc.level.getGameTime() + partial;
        PoseStack pose = event.getPoseStack();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        for (Sphere s : SPHERES.values()) {
            Entity anchor = mc.level.getEntity(s.anchorId());
            if (anchor == null) continue;
            float grow = Mth.clamp((now - s.start()) / s.expandTicks(), 0.05f, 1f);
            float fade = Mth.clamp((s.end() - SAFETY_TICKS - now) / 4f, 0f, 1f);
            if (fade <= 0) continue;
            float r = s.radius() * grow;
            Vec3 c = anchor.getPosition(partial).add(0, anchor.getBbHeight() / 2, 0);
            boolean inside = cam.distanceToSqr(c) < (double) r * r;
            float pulse = 0.85f + 0.15f * Mth.sin(now * 0.25f);
            // Con poca vida el escudo parpadea y se ve más tenue.
            float damage = 1 - s.shield();
            float flicker = damage > 0.6f ? 0.75f + 0.25f * Mth.sin(now * (1.2f + damage)) : 1f;
            pose.pushPose();
            pose.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
            drawShell(pose.last().pose(), r, inside, 0.22f * fade * pulse * flicker * (1 - 0.35f * damage));
            drawCracks(pose.last().pose(), r, s, fade);
            pose.popPose();
        }
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static final int LAT = 16, LON = 28;
    private static final int MAX_CRACKS = 28;

    /**
     * Grietas de una esfera: polilíneas sobre la superficie (ángulos θ, φ), siempre las mismas para el mismo Santuario.
     * Cada una tiene un tronco de 5 a 9 tramos y a veces una rama.
     */
    private static List<float[][]> cracks(int seed) {
        java.util.Random rnd = new java.util.Random(seed * 31L + 7);
        List<float[][]> out = new java.util.ArrayList<>();
        while (out.size() < MAX_CRACKS) {
            int n = 5 + rnd.nextInt(5);
            float[][] line = new float[n + 1][2];
            float theta = 0.25f + rnd.nextFloat() * (Mth.PI - 0.5f), phi = rnd.nextFloat() * Mth.TWO_PI;
            float heading = rnd.nextFloat() * Mth.TWO_PI;
            line[0] = new float[] {theta, phi};
            for (int i = 1; i <= n; i++) {
                heading += (rnd.nextFloat() - 0.5f) * 1.4f;
                float step = 0.07f + rnd.nextFloat() * 0.09f;
                theta = Mth.clamp(theta + Mth.cos(heading) * step, 0.05f, Mth.PI - 0.05f);
                phi += Mth.sin(heading) * step / Math.max(0.2f, Mth.sin(theta));
                line[i] = new float[] {theta, phi};
            }
            out.add(line);
            if (rnd.nextFloat() < 0.5f && out.size() < MAX_CRACKS) {
                int from = 1 + rnd.nextInt(n - 1);
                float[][] branch = new float[4][2];
                branch[0] = line[from];
                float bt = line[from][0], bp = line[from][1], bh = heading + (rnd.nextBoolean() ? 1.2f : -1.2f);
                for (int i = 1; i < 4; i++) {
                    float step = 0.05f + rnd.nextFloat() * 0.06f;
                    bt = Mth.clamp(bt + Mth.cos(bh) * step, 0.05f, Mth.PI - 0.05f);
                    bp += Mth.sin(bh) * step / Math.max(0.2f, Mth.sin(bt));
                    branch[i] = new float[] {bt, bp};
                }
                out.add(branch);
            }
        }
        return out;
    }

    /** Más grietas cuanta menos vida le queda al escudo (ninguna con la vida llena; todas casi roto). */
    private static void drawCracks(Matrix4f m, float r, Sphere s, float fade) {
        float damage = 1 - s.shield();
        int visible = Math.min(s.cracks().size(), Math.round(damage * damage * 0.4f * MAX_CRACKS + damage * 0.6f * MAX_CRACKS));
        if (visible <= 0) return;
        float alpha = Mth.clamp(0.45f + damage * 0.55f, 0f, 1f) * fade;
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int k = 0; k < visible; k++) {
            float[][] line = s.cracks().get(k);
            for (int i = 0; i + 1 < line.length; i++) {
                crackVertex(bb, m, r * 1.004f, line[i], alpha);
                crackVertex(bb, m, r * 1.004f, line[i + 1], alpha);
            }
        }
        RenderSystem.lineWidth(2.0f);
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.lineWidth(1.0f);
    }

    private static void crackVertex(BufferBuilder bb, Matrix4f m, float r, float[] p, float alpha) {
        float sin = Mth.sin(p[0]);
        bb.vertex(m, r * sin * Mth.cos(p[1]), r * Mth.cos(p[0]), r * sin * Mth.sin(p[1])).color(1.0f, 0.97f, 0.85f, alpha).endVertex();
    }

    private static void drawShell(Matrix4f m, float r, boolean inside, float alpha) {
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < LAT; i++) {
            float t0 = Mth.PI * i / LAT, t1 = Mth.PI * (i + 1) / LAT;
            for (int j = 0; j < LON; j++) {
                float p0 = Mth.TWO_PI * j / LON, p1 = Mth.TWO_PI * (j + 1) / LON;
                if (inside) {
                    vertex(bb, m, r, t0, p0, alpha);
                    vertex(bb, m, r, t1, p0, alpha);
                    vertex(bb, m, r, t1, p1, alpha);
                    vertex(bb, m, r, t0, p1, alpha);
                } else {
                    vertex(bb, m, r, t0, p0, alpha);
                    vertex(bb, m, r, t0, p1, alpha);
                    vertex(bb, m, r, t1, p1, alpha);
                    vertex(bb, m, r, t1, p0, alpha);
                }
            }
        }
        BufferUploader.drawWithShader(bb.end());
    }

    private static void vertex(BufferBuilder bb, Matrix4f m, float r, float theta, float phi, float alpha) {
        float sin = Mth.sin(theta);
        // Dorado, más claro hacia arriba.
        float up = 0.5f + 0.5f * Mth.cos(theta);
        bb.vertex(m, r * sin * Mth.cos(phi), r * Mth.cos(theta), r * sin * Mth.sin(phi))
                .color(1.0f, 0.82f + 0.12f * up, 0.35f + 0.4f * up, alpha).endVertex();
    }

    // ------------------------------------------------------------------ HUD

    private static void renderHud(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (downed(mc.player)) {
            float t = (mc.level.getGameTime() + partialTick) * 0.15f;
            int alpha = (int) (60 + 30 * Mth.sin(t));
            g.fill(0, 0, width, height, (alpha << 24) | 0x302010);
            Component text = Component.translatable("hud.rites_and_paths.downed", downedSeconds(mc.player));
            g.drawCenteredString(mc.font, text, width / 2, height / 2 - 30, 0xFFE6C35A);
        }
    }

    /** Fila del Sacrificio en el HUD de habilidades: roja y palpitante cuando pagar el coste te mataría. */
    public static boolean sacrificeFinal() {
        Player p = Minecraft.getInstance().player;
        if (p == null) return false;
        return p.getHealth() <= p.getMaxHealth() * number(Cleric.SACRIFICE, "cost_fraction", 0.15);
    }

    /** Ancho de la esfera más cercana (depuración). */
    public static int spheres() {
        return SPHERES.size();
    }

    static LivingEntity entity(int id) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getEntity(id) instanceof LivingEntity l ? l : null;
    }
}
