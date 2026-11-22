package io.github.rpgclasses.druid;

import io.github.rpgclasses.RpgClasses;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * Epic Fight y las formas de bestia: transformado, el jugador ataca con el golpe vanilla (el de la bestia), así que se
 * le pasa al modo normal ("minería") de Epic Fight si estaba en el de combate. Todo por reflexión: los nombres de los
 * métodos cambian entre versiones de Epic Fight y, si no existen, simplemente no se hace nada (se avisa una vez).
 */
final class EpicFightBridge {
    private static boolean lookedUp, failed;
    private static Method getPatch, isBattle, toMining;
    private static Object miningMode;
    private static Class<?> patchClass;

    private EpicFightBridge() {}

    static void toVanillaMode(ServerPlayer p) {
        if (failed || !ModList.get().isLoaded("epicfight")) return;
        try {
            if (!lookedUp) lookUp();
            if (failed) return;
            Object patch = getPatch.invoke(null, p, patchClass);
            if (patch == null || !(Boolean) isBattle.invoke(patch)) return;
            if (miningMode != null) toMining.invoke(patch, miningMode, true);
            else toMining.invoke(patch, true);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failed = true;
            RpgClasses.LOGGER.warn("[rites_and_paths] Epic Fight: no se pudo pasar al modo normal en forma de bestia ({})", e.toString());
        }
    }

    private static void lookUp() throws ReflectiveOperationException {
        lookedUp = true;
        Class<?> caps = Class.forName("yesman.epicfight.world.capabilities.EpicFightCapabilities");
        patchClass = Class.forName("yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch");
        getPatch = caps.getMethod("getEntityPatch", Entity.class, Class.class);
        isBattle = patchClass.getMethod("isBattleMode");
        try {
            toMining = patchClass.getMethod("toMiningMode", boolean.class);
        } catch (NoSuchMethodException e) {
            // Versiones con PlayerPatch.PlayerMode: toMode(MINING / VANILLA, sincronizar).
            Class<?> mode = Class.forName("yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch$PlayerMode");
            for (Object constant : mode.getEnumConstants()) {
                String n = ((Enum<?>) constant).name();
                if (n.equals("MINING") || n.equals("VANILLA")) miningMode = constant;
            }
            if (miningMode == null) throw new NoSuchMethodException("PlayerMode sin MINING/VANILLA");
            toMining = patchClass.getMethod("toMode", mode, boolean.class);
        }
    }
}
