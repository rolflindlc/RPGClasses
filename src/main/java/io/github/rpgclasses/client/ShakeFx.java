package io.github.rpgclasses.client;

import io.github.rpgclasses.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;

/**
 * Temblor de pantalla de los pisotones del Coloso Titán (adenda 0.7, 4.4). El servidor manda la fuerza (ya atenuada
 * por la distancia); aquí se acumula como "trauma" que decae, y la cámara se sacude con ruido suave proporcional a
 * trauma². La opción de cliente {@code titanShake} lo reduce o lo quita.
 */
public final class ShakeFx {
    private static final float DECAY_PER_TICK = 0.04f;
    private static final float MAX_PITCH = 3.5f, MAX_YAW = 2.5f, MAX_ROLL = 3.0f;
    private static float trauma;

    private ShakeFx() {}

    public static void add(float strength) {
        trauma = Mth.clamp(trauma + strength * (float) ClientConfig.titanShake(), 0f, 1f);
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && trauma > 0) trauma = Math.max(0f, trauma - DECAY_PER_TICK);
    }

    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (trauma <= 0 || mc.level == null) return;
        float shake = trauma * trauma;
        float t = (mc.level.getGameTime() + (float) event.getPartialTick()) * 1.6f;
        event.setPitch(event.getPitch() + shake * MAX_PITCH * noise(t, 0));
        event.setYaw(event.getYaw() + shake * MAX_YAW * noise(t, 17));
        event.setRoll(event.getRoll() + shake * MAX_ROLL * noise(t, 41));
    }

    /** Ruido suave en [-1, 1] (suma de senos desfasados). */
    private static float noise(float t, float seed) {
        return (Mth.sin(t + seed) * 0.6f + Mth.sin(t * 2.3f + seed * 1.7f) * 0.3f + Mth.sin(t * 4.1f + seed * 0.3f) * 0.1f);
    }
}
