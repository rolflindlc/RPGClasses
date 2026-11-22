package io.github.rpgclasses.client.druid;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.rpgclasses.client.ClientHandlers;
import io.github.rpgclasses.client.ClientSetup;
import io.github.rpgclasses.druid.Beasts;
import io.github.rpgclasses.druid.Druid;
import io.github.rpgclasses.druid.DruidRegistry;
import io.github.rpgclasses.druid.FormSpec;
import io.github.rpgclasses.druid.WildShape;
import io.github.rpgclasses.network.DruidPackets;
import io.github.rpgclasses.network.Net;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cliente del Druida: el jugador transformado se dibuja como su bestia (para todos; sin manos en primera persona), las
 * quimeras con el cuerpo del torso y rasgos de las otras partes, las barras de lanzamiento, estudio y vuelo, el brillo
 * de Rastrear Presas, la creación de quimeras sobre el altar y las pantallas (bestiario, ritual, recipiente).
 */
public final class DruidClient {
    public static final KeyMapping BEAST_ABILITY = new KeyMapping("key.rites_and_paths.beast_ability", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G, "key.categories.rites_and_paths");

    /** Cuerpos de bestia de cada jugador transformado (entidades sin añadir al mundo). */
    private static final Map<UUID, LivingEntity[]> DUMMIES = new HashMap<>();
    private static final Map<UUID, FormSpec> DUMMY_SPEC = new HashMap<>();
    private static final Map<UUID, Float> LAST_ATTACK = new HashMap<>();
    /** Entidades que brillan (id → tic del cliente en que dejan de brillar). */
    private static final Map<Integer, Long> GLOW = new HashMap<>();
    private static long clientTicks;
    /** Último estado de las barras (del servidor). */
    static DruidPackets.State state;
    private static final List<Formation> FORMATIONS = new ArrayList<>();
    private static final Map<String, Boolean> BROKEN = new HashMap<>();

    private record Formation(BlockPos pos, FormSpec.Chimera chimera, int total, long start) {}

    private DruidClient() {}

    public static void register(IEventBus modBus) {
        Druid.clientMechanic = (p, id) -> p == Minecraft.getInstance().player ? ClientSetup.localMechanic(id) : null;
        Druid.clientData = () -> ClientHandlers.LOCAL_DATA;
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(BEAST_ABILITY));
        modBus.addListener((RegisterGuiOverlaysEvent e) -> e.registerAboveAll("druid", DruidClient::renderHud));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerEntityRenderer(DruidRegistry.BEAST_REMAINS.get(), BeastRemainsRenderer::new));
        modBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e) -> e.enqueueWork(() ->
                net.minecraft.client.gui.screens.MenuScreens.register(DruidRegistry.VESSEL_MENU.get(), VesselScreen::new)));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, DruidClient::onRenderPlayer);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, DruidClient::onRenderHand);
        MinecraftForge.EVENT_BUS.addListener(DruidClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(DruidClient::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> reset());
    }

    private static void reset() {
        WildShape.CLIENT_FORMS.clear();
        DUMMIES.clear();
        DUMMY_SPEC.clear();
        LAST_ATTACK.clear();
        GLOW.clear();
        FORMATIONS.clear();
        state = null;
    }

    // ------------------------------------------------------------------ paquetes

    public static void onForm(DruidPackets.Form msg) {
        FormSpec spec = FormSpec.load(msg.spec());
        if (spec == null) WildShape.CLIENT_FORMS.remove(msg.player());
        else WildShape.CLIENT_FORMS.put(msg.player(), spec);
        DUMMIES.remove(msg.player());
        DUMMY_SPEC.remove(msg.player());
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(msg.entityId()) instanceof Player p) p.refreshDimensions();
        if (mc.screen instanceof BestiaryScreen s) s.rebuild();
    }

    public static void onState(DruidPackets.State msg) {
        state = msg;
    }

    public static void onOpenBestiary(DruidPackets.OpenBestiary msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) mc.setScreen(new BestiaryScreen());
    }

    public static void onGlow(DruidPackets.Glow msg) {
        for (int id : msg.ids()) GLOW.put(id, clientTicks + msg.ticks());
    }

    public static void onRitualChoice(DruidPackets.RitualChoice msg) {
        Minecraft.getInstance().setScreen(new RitualChoiceScreen(msg.species(), msg.parts()));
    }

    public static void onChimeraReplace(DruidPackets.ChimeraReplace msg) {
        Minecraft.getInstance().setScreen(new ChimeraReplaceScreen());
    }

    public static void onChimeraFx(DruidPackets.ChimeraFx msg) {
        FormSpec.Chimera c = FormSpec.Chimera.load(msg.chimera());
        if (c != null) FORMATIONS.add(new Formation(msg.pos(), c, Math.max(1, msg.ticks()), clientTicks));
    }

    static void send(int kind, CompoundTag tag, int value) {
        Net.CHANNEL.sendToServer(new DruidPackets.Action(kind, tag, value));
    }

    /** Brilla para el jugador local (Rastrear Presas, Ecolocalización). Lo usa HealerGlowMixin. */
    public static boolean glows(Entity e) {
        Long until = GLOW.get(e.getId());
        return until != null && until > clientTicks;
    }

    // ------------------------------------------------------------------ cada tic

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        clientTicks++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        LocalPlayer me = mc.player;
        while (BEAST_ABILITY.consumeClick()) {
            if (mc.screen == null && WildShape.inForm(me)) send(DruidPackets.Action.BEAST_ABILITY, new CompoundTag(), 0);
        }
        Beasts.Info info = WildShape.inForm(me) ? WildShape.info(me) : null;
        if (info != null) {
            // Trepar (arañas): pegado a una pared, se sube con avanzar o saltar.
            if (info.hasPassive("climb") && me.horizontalCollision && !me.getAbilities().flying) {
                boolean up = mc.screen == null && (mc.options.keyUp.isDown() || mc.options.keyJump.isDown());
                Vec3 v = me.getDeltaMovement();
                me.setDeltaMovement(v.x, up ? .2 : me.isShiftKeyDown() ? 0 : Math.max(v.y, -.15), v.z);
                me.resetFallDistance();
            }
            if (info.hasPassive("lava_walk")) io.github.rpgclasses.content.MonkSubclasses.fluidWalk(me, false, true);
        }
        GLOW.values().removeIf(t -> t <= clientTicks);
        FORMATIONS.removeIf(f -> clientTicks - f.start() > f.total() + 10);
        // Cuerpos de bestia: se crean al ver la forma y se animan con el movimiento del jugador.
        DUMMIES.keySet().removeIf(id -> !WildShape.CLIENT_FORMS.containsKey(id));
        for (Player p : mc.level.players()) {
            FormSpec spec = WildShape.CLIENT_FORMS.get(p.getUUID());
            if (spec == null) continue;
            LivingEntity[] body = dummies(p, spec);
            if (body == null) continue;
            for (LivingEntity d : body) if (d != null) animate(d, p);
        }
    }

    /** [cuerpo, cabeza de la quimera] del jugador (null si la especie no se puede dibujar). */
    private static LivingEntity[] dummies(Player p, FormSpec spec) {
        if (!spec.equals(DUMMY_SPEC.get(p.getUUID()))) {
            DUMMIES.remove(p.getUUID());
            DUMMY_SPEC.put(p.getUUID(), spec);
        }
        LivingEntity[] cached = DUMMIES.get(p.getUUID());
        if (cached != null) return cached[0] == null ? null : cached;
        LivingEntity body = create(spec.entityType(), p);
        LivingEntity head = spec.isChimera() && !spec.chimera.head().equals(spec.chimera.torso())
                ? create(Beasts.type(spec.chimera.head()), p) : null;
        LivingEntity[] out = {body, head};
        DUMMIES.put(p.getUUID(), out);
        return body == null ? null : out;
    }

    static LivingEntity create(EntityType<?> type, Entity near) {
        if (type == null) return null;
        String id = Beasts.id(type);
        if (BROKEN.containsKey(id)) return null;
        try {
            Entity e = type.create(near.level());
            if (e instanceof LivingEntity le) {
                le.moveTo(near.getX(), near.getY(), near.getZ(), near.getYRot(), 0);
                return le;
            }
        } catch (RuntimeException | LinkageError ex) {
            io.github.rpgclasses.RpgClasses.LOGGER.warn("[rites_and_paths] No se puede dibujar la forma {} ({})", id, ex.toString());
        }
        BROKEN.put(id, true);
        return null;
    }

    private static void animate(LivingEntity d, Player p) {
        d.tickCount++;
        d.xo = p.xo;
        d.yo = p.yo;
        d.zo = p.zo;
        d.setPos(p.getX(), p.getY(), p.getZ());
        double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
        float move = Math.min(1f, (float) Math.sqrt(dx * dx + dz * dz) * 4f);
        d.walkAnimation.update(move, .4f);
        d.setOnGround(p.onGround());
        d.hurtTime = p.hurtTime;
        d.hurtDuration = p.hurtDuration;
        d.setInvisible(p.isInvisible());
        d.setSprinting(p.isSprinting());
        if (d instanceof TamableAnimal t) t.setInSittingPose(p.isCrouching() && move < .05f);
        // Ataque: el balanceo del jugador mueve el del cuerpo y dispara la animación de ataque del mob (evento 4).
        Float last = LAST_ATTACK.get(p.getUUID());
        if (p.swinging && p.swingTime == 0 && (last == null || last == 0f)) d.handleEntityEvent((byte) 4);
        LAST_ATTACK.put(p.getUUID(), p.swinging ? 1f : 0f);
        d.swinging = p.swinging;
        d.swingTime = p.swingTime;
        d.oAttackAnim = p.oAttackAnim;
        d.attackAnim = p.attackAnim;
    }

    // ------------------------------------------------------------------ dibujo

    private static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        FormSpec spec = WildShape.CLIENT_FORMS.get(p.getUUID());
        if (spec == null) return;
        LivingEntity[] body = dummies(p, spec);
        if (body == null) return; // sin cuerpo dibujable: se ve al jugador
        event.setCanceled(true);
        if (p.isInvisible()) return;
        float partial = event.getPartialTick();
        LivingEntity d = body[0];
        sync(d, p);
        Minecraft mc = Minecraft.getInstance();
        float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
        MultiBufferSource buffers = event.getMultiBufferSource();
        if (spec.isChimera()) buffers = new TintedBufferSource(buffers, tint(spec.chimera.legs()));
        renderEntity(mc, d, yaw, partial, event.getPoseStack(), buffers, event.getPackedLight());
        if (spec.isChimera() && body[1] != null) {
            // Cabeza injertada: la especie de la cabeza, pequeña, delante y arriba del torso.
            LivingEntity head = body[1];
            sync(head, p);
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            double r = Math.toRadians(yaw);
            double fwd = d.getBbWidth() * .45;
            pose.translate(-Math.sin(r) * fwd, d.getBbHeight() * .55, Math.cos(r) * fwd);
            pose.scale(.5f, .5f, .5f);
            renderEntity(mc, head, yaw, partial, pose, event.getMultiBufferSource(), event.getPackedLight());
            pose.popPose();
        }
    }

    private static void sync(LivingEntity d, Player p) {
        d.yBodyRot = p.yBodyRot;
        d.yBodyRotO = p.yBodyRotO;
        d.yHeadRot = p.yHeadRot;
        d.yHeadRotO = p.yHeadRotO;
        d.setYRot(p.getYRot());
        d.yRotO = p.yRotO;
        d.setXRot(p.getXRot());
        d.xRotO = p.xRotO;
    }

    @SuppressWarnings("unchecked")
    static void renderEntity(Minecraft mc, LivingEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        try {
            EntityRenderer<LivingEntity> renderer = (EntityRenderer<LivingEntity>) mc.getEntityRenderDispatcher().getRenderer(e);
            renderer.render(e, yaw, partial, pose, buffers, light);
        } catch (RuntimeException ex) {
            String id = Beasts.id(e.getType());
            if (!BROKEN.containsKey(id)) io.github.rpgclasses.RpgClasses.LOGGER.warn("[rites_and_paths] Fallo al dibujar la forma {} ({})", id, ex.toString());
            BROKEN.put(id, true);
        }
    }

    /** Color de las patas de la quimera (el de su huevo de aparición, suavizado), en 0xRRGGBB. */
    static int tint(String species) {
        EntityType<?> type = Beasts.type(species);
        SpawnEggItem egg = type == null ? null : net.minecraftforge.common.ForgeSpawnEggItem.fromEntityType(type);
        if (egg == null) return 0xFFFFFF;
        int c = egg.getColor(0);
        int r = (255 + ((c >> 16) & 255)) / 2, g = (255 + ((c >> 8) & 255)) / 2, b = (255 + (c & 255)) / 2;
        return (r << 16) | (g << 8) | b;
    }

    private static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && WildShape.inForm(mc.player) && DUMMIES.containsKey(mc.player.getUUID())) event.setCanceled(true);
    }

    /** Quimeras que se forman sobre un altar: crecen girando, oscuras al principio. */
    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || FORMATIONS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float partial = event.getPartialTick();
        for (Formation f : FORMATIONS) {
            float t = Mth.clamp((clientTicks - f.start() + partial) / f.total(), 0f, 1f);
            LivingEntity body = create(Beasts.type(f.chimera().torso()), mc.player);
            if (body == null) continue;
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            pose.translate(f.pos().getX() + .5 - cam.x, f.pos().getY() + 1 - cam.y, f.pos().getZ() + .5 - cam.z);
            float s = .1f + .9f * t;
            pose.scale(s, s, s);
            float yaw = t * 540f;
            body.yBodyRot = body.yBodyRotO = body.yHeadRot = body.yHeadRotO = yaw;
            int dark = (int) (80 + 175 * t);
            int color = (dark << 16) | ((int) (dark * .4f) << 8) | (int) (dark * .4f);
            int light = LevelRenderer.getLightColor(mc.level, f.pos().above());
            renderEntity(mc, body, yaw, partial, pose, new TintedBufferSource(buffers, t > .9f ? tint(f.chimera().legs()) : color), light);
            pose.popPose();
        }
        buffers.endBatch();
    }

    // ------------------------------------------------------------------ HUD

    private static void renderHud(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !Druid.isDruid(mc.player)) return;
        Font font = mc.font;
        DruidPackets.State st = state;
        int cy = height / 2 + 16;
        if (st != null && st.cast() >= 0) {
            Component name = st.castType().startsWith("chimera:")
                    ? Component.translatable("druid.rites_and_paths.chimera", Beasts.name(st.castType().substring(8)))
                    : Beasts.name(st.castType());
            bar(g, font, width / 2, cy, Component.translatable("hud.rites_and_paths.druid.casting", name), st.cast(), 0xFF6FBF4A);
            cy += 18;
        }
        if (st != null && st.study() >= 0) {
            Entity target = mc.level == null || st.studyTarget() < 0 ? null : mc.level.getEntity(st.studyTarget());
            Component name = target == null ? Component.literal("?") : target.getDisplayName();
            bar(g, font, width / 2, cy, Component.translatable("hud.rites_and_paths.druid.study", name), st.study(), 0xFFB98CE0);
        }
        FormSpec spec = WildShape.spec(mc.player);
        if (spec == null) return;
        Beasts.Info info = WildShape.info(mc.player);
        Component formName = spec.isChimera() ? Component.translatable("druid.rites_and_paths.chimera", Beasts.name(spec.type)) : Beasts.name(spec.type);
        int x = 6, y = height - 52;
        Component line = st != null && st.formTicks() >= 0
                ? Component.translatable("hud.rites_and_paths.druid.form_timer", formName, st.formTicks() / 20)
                : Component.translatable("hud.rites_and_paths.druid.form", formName);
        g.drawString(font, line, x, y, 0xFF9FE07A);
        if (info != null && info.ability() != null) {
            Component ability = Component.translatable("druid.rites_and_paths.ability." + info.ability());
            Component key = BEAST_ABILITY.getTranslatedKeyMessage();
            Component text = st != null && st.abilityCooldown() > 0
                    ? Component.translatable("hud.rites_and_paths.druid.ability_cooldown", key, ability,
                    String.format(java.util.Locale.ROOT, "%.1f", st.abilityCooldown() / 20.0))
                    : Component.translatable("hud.rites_and_paths.druid.ability", key, ability);
            g.drawString(font, text, x, y + 11, st != null && st.abilityCooldown() > 0 ? 0xFF999999 : 0xFFFFFFFF);
        }
        if (st != null && st.flight() >= 0) {
            g.drawString(font, Component.translatable("hud.rites_and_paths.druid.flight"), x, y + 22, 0xFFBFE6FF);
            int bx = x + font.width(Component.translatable("hud.rites_and_paths.druid.flight")) + 4;
            g.fill(bx, y + 24, bx + 62, y + 29, 0xFF000000);
            g.fill(bx + 1, y + 25, bx + 1 + (int) (60 * st.flight()), y + 28, st.flight() > .2f ? 0xFF8FD3FF : 0xFFE05050);
        }
    }

    private static void bar(GuiGraphics g, Font font, int cx, int y, Component label, float frac, int color) {
        int w = 120;
        g.drawCenteredString(font, label, cx, y, 0xFFFFFFFF);
        g.fill(cx - w / 2, y + 10, cx + w / 2, y + 15, 0xFF000000);
        g.fill(cx - w / 2 + 1, y + 11, cx - w / 2 + 1 + (int) ((w - 2) * Mth.clamp(frac, 0, 1)), y + 14, color);
    }
}
