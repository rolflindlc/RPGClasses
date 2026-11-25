package io.github.rpgclasses.client.rogue;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.rpgclasses.client.ClientHandlers;
import io.github.rpgclasses.network.DisguisePackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cambiarrostros en el cliente. Con un rostro de mob, el jugador se dibuja como ese mob (como las formas del Druida);
 * con un rostro de jugador, con la skin, el modelo (normal o delgado), el tamaño racial (solo a la vista) y el nombre del
 * copiado. Los aliados lo ven disfrazado pero con su nombre real encima. Los disfrazados no salen en el Tab de los demás
 * (TabHideMixin) y la skin la cambia DisguiseSkinMixin.
 */
public final class DisguiseClient {
    /** Rostro puesto por cada jugador disfrazado. */
    private static final Map<UUID, CompoundTag> FACES = new HashMap<>();
    /** Cuerpo de mob con el que se dibuja a cada disfrazado de mob. */
    private static final Map<UUID, LivingEntity> DUMMIES = new HashMap<>();
    /** Skins descargadas de jugadores copiados que no están en la lista de jugadores. */
    private static final Map<UUID, Skin> SKINS = new HashMap<>();
    private static final Set<UUID> LOADING = new HashSet<>();
    private static final Set<String> BROKEN = new HashSet<>();

    private record Skin(ResourceLocation texture, String model) {}

    private static boolean scaled;

    private DisguiseClient() {}

    public static void register(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, DisguiseClient::onRenderMob);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, DisguiseClient::onRenderScalePre);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, DisguiseClient::onRenderScalePost);
        MinecraftForge.EVENT_BUS.addListener(DisguiseClient::onNameTag);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> reset());
    }

    private static void reset() {
        FACES.clear();
        DUMMIES.clear();
        SKINS.clear();
        LOADING.clear();
        scaled = false;
    }

    // ------------------------------------------------------------------ paquetes

    public static void onSync(DisguisePackets.Sync msg) {
        DUMMIES.remove(msg.player());
        if (msg.face().isEmpty()) FACES.remove(msg.player());
        else FACES.put(msg.player(), msg.face());
    }

    public static void onFaceList(DisguisePackets.FaceList msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) mc.setScreen(new FaceScreen(msg));
    }

    // ------------------------------------------------------------------ consultas (mixins y otras capas)

    public static boolean disguised(Player p) {
        return p != null && FACES.containsKey(p.getUUID());
    }

    private static boolean isPlayerFace(CompoundTag face) {
        return face != null && "player".equals(face.getString("kind")) && face.hasUUID("uuid");
    }

    /** Los disfrazados no salen en el Tab de los demás (uno mismo sí se ve). */
    public static boolean hiddenInTab(UUID id) {
        if (!FACES.containsKey(id)) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null || !mc.player.getUUID().equals(id);
    }

    public static boolean anyDisguised() {
        return !FACES.isEmpty();
    }

    /** Skin del disfraz de jugador (null: la propia). */
    public static ResourceLocation skin(AbstractClientPlayer p) {
        CompoundTag face = FACES.get(p.getUUID());
        return isPlayerFace(face) ? skinFor(face).texture() : null;
    }

    /** Modelo ("default" o "slim") del disfraz de jugador (null: el propio). */
    public static String model(AbstractClientPlayer p) {
        CompoundTag face = FACES.get(p.getUUID());
        return isPlayerFace(face) ? skinFor(face).model() : null;
    }

    /** Disfrazado de otro jugador: sin capa (la suya no se puede copiar). */
    public static boolean hidesCape(AbstractClientPlayer p) {
        return isPlayerFace(FACES.get(p.getUUID()));
    }

    private static Skin skinFor(CompoundTag face) {
        UUID target = face.getUUID("uuid");
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener conn = mc.getConnection();
        PlayerInfo info = conn == null ? null : conn.getPlayerInfo(target);
        if (info != null) return new Skin(info.getSkinLocation(), info.getModelName());
        Skin loaded = SKINS.get(target);
        if (loaded != null) return loaded;
        if (LOADING.add(target)) {
            // El copiado no está conectado: se descarga su skin con la textura que se guardó al estudiarlo.
            GameProfile profile = new GameProfile(target, face.getString("name"));
            if (face.contains("tex")) {
                profile.getProperties().put("textures", new Property("textures", face.getString("tex"),
                        face.contains("sig") ? face.getString("sig") : null));
            }
            try {
                mc.getSkinManager().registerSkins(profile, (type, location, texture) -> {
                    if (type != MinecraftProfileTexture.Type.SKIN) return;
                    String model = texture.getMetadata("model");
                    SKINS.put(target, new Skin(location, model == null ? "default" : model));
                }, false);
            } catch (RuntimeException ex) {
                io.github.rpgclasses.RpgClasses.LOGGER.warn("[rites_and_paths] No se pudo cargar la skin del disfraz ({})", ex.toString());
            }
        }
        return new Skin(DefaultPlayerSkin.getDefaultSkin(target), DefaultPlayerSkin.getSkinModelName(target));
    }

    // ------------------------------------------------------------------ dibujo

    /** Disfraz de mob: se cancela el dibujo del jugador y se dibuja el mob en su lugar. */
    private static void onRenderMob(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        CompoundTag face = FACES.get(p.getUUID());
        if (face == null || !"mob".equals(face.getString("kind"))) return;
        LivingEntity d = dummy(p, face.getString("type"));
        if (d == null) return; // sin cuerpo dibujable: se ve al jugador
        event.setCanceled(true);
        if (p.isInvisible()) return;
        float partial = event.getPartialTick();
        animate(d, p);
        // Los aliados lo ven disfrazado, pero con su nombre real encima.
        Minecraft mc = Minecraft.getInstance();
        boolean ally = p != mc.player && ClientHandlers.isAllyEntity(p);
        d.setCustomName(ally ? p.getName() : null);
        d.setCustomNameVisible(ally);
        float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
        try {
            @SuppressWarnings("unchecked")
            EntityRenderer<LivingEntity> renderer = (EntityRenderer<LivingEntity>) mc.getEntityRenderDispatcher().getRenderer(d);
            renderer.render(d, yaw, partial, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
        } catch (RuntimeException ex) {
            String id = face.getString("type");
            if (BROKEN.add(id)) io.github.rpgclasses.RpgClasses.LOGGER.warn("[rites_and_paths] Fallo al dibujar el disfraz {} ({})", id, ex.toString());
            DUMMIES.remove(p.getUUID());
        }
    }

    private static LivingEntity dummy(Player p, String typeId) {
        LivingEntity cached = DUMMIES.get(p.getUUID());
        if (cached != null && cached.level() == p.level()) return cached;
        if (BROKEN.contains(typeId)) return null;
        EntityType<?> type = EntityType.byString(typeId).orElse(null);
        if (type == null) return null;
        try {
            Entity e = type.create(p.level());
            if (e instanceof LivingEntity le) {
                le.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0);
                if (le instanceof Mob mob) mob.setNoAi(true);
                DUMMIES.put(p.getUUID(), le);
                return le;
            }
        } catch (RuntimeException | LinkageError ex) {
            io.github.rpgclasses.RpgClasses.LOGGER.warn("[rites_and_paths] No se puede dibujar el disfraz {} ({})", typeId, ex.toString());
        }
        BROKEN.add(typeId);
        return null;
    }

    /** Copia del jugador al mob: posición, giro de cuerpo y cabeza, paso, golpe, daño y lo que lleva en las manos. */
    private static void animate(LivingEntity d, Player p) {
        d.tickCount = p.tickCount;
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
        d.setSprinting(p.isSprinting());
        d.setShiftKeyDown(p.isShiftKeyDown());
        d.swinging = p.swinging;
        d.swingTime = p.swingTime;
        d.swingingArm = p.swingingArm;
        d.oAttackAnim = p.oAttackAnim;
        d.attackAnim = p.attackAnim;
        d.yBodyRot = p.yBodyRot;
        d.yBodyRotO = p.yBodyRotO;
        d.yHeadRot = p.yHeadRot;
        d.yHeadRotO = p.yHeadRotO;
        d.setYRot(p.getYRot());
        d.yRotO = p.yRotO;
        d.setXRot(p.getXRot());
        d.xRotO = p.xRotO;
        d.setItemSlot(EquipmentSlot.MAINHAND, p.getMainHandItem());
        d.setItemSlot(EquipmentSlot.OFFHAND, p.getOffhandItem());
    }

    /** Disfraz de jugador: el tamaño racial del copiado, solo a la vista (la hitbox sigue siendo la propia). */
    private static void onRenderScalePre(RenderPlayerEvent.Pre event) {
        scaled = false;
        CompoundTag face = FACES.get(event.getEntity().getUUID());
        if (!isPlayerFace(face) || !face.contains("scale")) return;
        float own = io.github.rpgclasses.content.race.RaceTraits.scale(event.getEntity());
        float s = face.getFloat("scale") / Math.max(0.1f, own);
        if (Math.abs(s - 1f) < 1e-3) return;
        scaled = true;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.scale(s, s, s);
    }

    private static void onRenderScalePost(RenderPlayerEvent.Post event) {
        if (!scaled) return;
        scaled = false;
        event.getPoseStack().popPose();
    }

    /** Sobre un disfrazado de jugador, el nombre del copiado (los aliados y uno mismo siguen viendo el real). */
    private static void onNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        CompoundTag face = FACES.get(p.getUUID());
        if (!isPlayerFace(face) || ClientHandlers.isAllyEntity(p)) return;
        event.setContent(Component.literal(face.getString("name")));
    }
}
