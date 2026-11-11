package io.github.rpgclasses.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.rpgclasses.content.RangerAbilities;
import io.github.rpgclasses.core.ability.UnlockResolver;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.registry.ModAttributes;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

/** Registro de todo lo que solo existe en el cliente. Se llama desde el mod a través de DistExecutor. */
public final class ClientSetup {
    public static final KeyMapping OPEN_BOOK = new KeyMapping(
            "key.rpgclasses.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.rpgclasses");

    public static final KeyMapping[] ABILITY_KEYS = {
            new KeyMapping("key.rpgclasses.ability_1", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.categories.rpgclasses"),
            new KeyMapping("key.rpgclasses.ability_2", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.rpgclasses"),
            new KeyMapping("key.rpgclasses.ability_3", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.rpgclasses"),
    };

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.register(OPEN_BOOK);
            for (KeyMapping key : ABILITY_KEYS) event.register(key);
        });
        modBus.addListener((RegisterGuiOverlaysEvent event) -> event.registerAboveAll("abilities", AbilityHud::render));
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onFov);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientHandlers.reset());
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Sin clase: la pantalla de selección vuelve a abrirse siempre que no haya otra pantalla.
        if (ClientHandlers.needsSelection() && mc.screen == null && !DefinitionStore.CLIENT.classIds().isEmpty()) {
            mc.setScreen(new ClassScreen(true));
            return;
        }
        airJump(mc);
        tickZoom(mc.player);
        for (int i = 0; i < ABILITY_KEYS.length; i++) {
            while (ABILITY_KEYS[i].consumeClick()) {
                if (mc.screen == null) Net.CHANNEL.sendToServer(new Packets.UseAbility(i));
            }
        }
        while (OPEN_BOOK.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new ClassScreen(false));
        }
    }

    private static boolean jumpWasDown;
    private static int airJumpsUsed;
    private static int airTicks;

    /**
     * Doble y triple salto: pulsar saltar de nuevo en el aire. El movimiento lo ejecuta el cliente
     * (como todo el movimiento del jugador) y el servidor valida y reinicia la distancia de caída.
     */
    private static void airJump(Minecraft mc) {
        LocalPlayer p = mc.player;
        boolean down = mc.options.keyJump.isDown();
        if (p.onGround() || p.isInWater() || p.isInLava() || p.onClimbable() || p.isPassenger()) {
            airJumpsUsed = 0;
            airTicks = 0;
        } else {
            airTicks++;
            java.util.Set<String> unlocked = UnlockResolver.unlocked(ClientHandlers.LOCAL_DATA.state,
                    DefinitionStore.CLIENT.classDef(ClientHandlers.LOCAL_DATA.state.classId),
                    DefinitionStore.CLIENT.subclassDef(ClientHandlers.LOCAL_DATA.state.subclassId));
            int allowed = (unlocked.contains(RangerAbilities.DOUBLE_JUMP) ? 1 : 0) + (unlocked.contains(RangerAbilities.TRIPLE_JUMP) ? 1 : 0);
            boolean pressed = down && !jumpWasDown;
            if (pressed && airTicks > 2 && airJumpsUsed < allowed && mc.screen == null
                    && !p.getAbilities().flying && !p.isFallFlying()) {
                double boost = p.hasEffect(MobEffects.JUMP) ? 0.1 * (p.getEffect(MobEffects.JUMP).getAmplifier() + 1) : 0;
                double jump = 0.42 * Math.sqrt(1 + ModAttributes.value(p, ModAttributes.JUMP_BONUS)) + boost;
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(v.x, jump, v.z);
                p.hasImpulse = true;
                p.resetFallDistance();
                airJumpsUsed++;
                Net.CHANNEL.sendToServer(new Packets.AirJump());
            }
        }
        jumpWasDown = down;
    }

    private static float zoomProgress;
    private static float zoomProgressPrev;

    /** Avanza la animación del zoom: activo al agacharse con un arco o ballesta en la mano, o al tensarlo. */
    private static void tickZoom(LocalPlayer p) {
        boolean holding = p.getMainHandItem().getItem() instanceof ProjectileWeaponItem
                || p.getOffhandItem().getItem() instanceof ProjectileWeaponItem;
        boolean drawing = p.isUsingItem() && p.getUseItem().getItem() instanceof ProjectileWeaponItem;
        boolean active = ModAttributes.value(p, ModAttributes.ZOOM) > 0 && (drawing || (holding && p.isShiftKeyDown()));
        zoomProgressPrev = zoomProgress;
        zoomProgress += ((active ? 1f : 0f) - zoomProgress) * 0.4f;
        if (Math.abs(zoomProgress) < 0.001f) zoomProgress = 0f;
    }

    /**
     * Visión de Halcón. Se cambia el campo de visión real (no el "efecto de FOV"), así que funciona
     * aunque el jugador tenga los efectos de FOV al mínimo en sus opciones.
     */
    private static void onFov(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !event.usedConfiguredFov()) return;
        float progress = (float) (zoomProgressPrev + (zoomProgress - zoomProgressPrev) * event.getPartialTick());
        if (progress <= 0f) return;
        double zoom = ModAttributes.value(mc.player, ModAttributes.ZOOM);
        event.setFOV(event.getFOV() * (1 - zoom * progress));
    }

    private ClientSetup() {}
}
