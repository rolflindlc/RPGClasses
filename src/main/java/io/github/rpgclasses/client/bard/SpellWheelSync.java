package io.github.rpgclasses.client.bard;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.bard.Bard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Rotación de conjuros del Bardo, lado cliente. El servidor mueve la selección de Iron's y la sincroniza
 * ({@code MusicRotation}), pero el cliente de Iron's guarda su rueda ({@code ClientMagicData.spellSelectionManager}) y
 * solo la rehace al cambiar de equipo: seguía mostrando, y marcando como elegido, el conjuro anterior. Cada tick, con
 * un instrumento en la mano, si la selección sincronizada no coincide con la de la rueda, se rehace la rueda.
 *
 * <p>Nada de esto está en la API de Iron's: va por reflexión y, si falla, se desactiva sin romper nada.
 */
public final class SpellWheelSync {
    private SpellWheelSync() {}

    private static Method syncedData, syncedSelection, manager, managerSelection, rebuild;
    private static Field slotField, indexField;
    private static boolean resolved, failed;

    private static boolean resolve() {
        if (resolved) return !failed;
        resolved = true;
        if (!ModList.get().isLoaded("irons_spellbooks")) {
            failed = true;
            return false;
        }
        try {
            ClassLoader loader = SpellWheelSync.class.getClassLoader();
            Class<?> client = Class.forName("io.redspace.ironsspellbooks.player.ClientMagicData", true, loader);
            Class<?> synced = Class.forName("io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData", true, loader);
            Class<?> ssm = Class.forName("io.redspace.ironsspellbooks.api.magic.SpellSelectionManager", true, loader);
            Class<?> selection = Class.forName("io.redspace.ironsspellbooks.gui.overlays.SpellSelection", true, loader);
            syncedData = client.getMethod("getSyncedSpellData", LivingEntity.class);
            syncedSelection = synced.getMethod("getSpellSelection");
            manager = client.getMethod("getSpellSelectionManager");
            managerSelection = ssm.getMethod("getCurrentSelection");
            rebuild = client.getMethod("updateSpellSelectionManager");
            slotField = selection.getField("equipmentSlot");
            indexField = selection.getField("index");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failed = true;
            RpgClasses.LOGGER.warn("[rites_and_paths] La rueda de conjuros del Bardo no se podrá refrescar sola: {}", e.toString());
        }
        return !failed;
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || Bard.instrumentHand(player) == null || !Bard.isBard(player) || !resolve()) return;
        try {
            Object wanted = syncedSelection.invoke(syncedData.invoke(null, player));
            Object wheel = manager.invoke(null);
            Object shown = wheel == null ? null : managerSelection.invoke(wheel);
            if (wanted == null || shown == null || wanted == shown) return;
            if (Objects.equals(slotField.get(wanted), slotField.get(shown)) && indexField.getInt(wanted) == indexField.getInt(shown)) return;
            rebuild.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            RpgClasses.LOGGER.warn("[rites_and_paths] Se desactiva el refresco de la rueda de conjuros del Bardo: {}", e.toString());
        }
    }
}
