package io.github.rpgclasses.compat.irons.music;

import io.github.rpgclasses.bard.Bard;
import io.github.rpgclasses.bard.BardState;
import io.github.rpgclasses.bard.MusicSpellInfo;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Rotación de conjuros del Bardo: con un instrumento en la mano, la selección de Iron's pasa sola al siguiente conjuro
 * que se pueda tocar (sin recarga y con el combo mínimo cubierto). Se mueve tras cada lanzamiento y, si el elegido deja
 * de poder tocarse (recarga, o el combo baja), al siguiente que pueda. Los conjuros que no son del instrumento no
 * aparecen en la rueda (mixin {@code SpellSelectionMixin}), así que solo rota entre los del instrumento.
 *
 * <p>La recarga y la selección sincronizada no están en la API de Iron's: se usan por reflexión.
 */
final class MusicRotation {
    private MusicRotation() {}

    private static Method getCooldowns, isOnCooldown, getSynced, getSelection, makeSelection, setSelection, doSync;
    private static Constructor<?> selectionCtor;
    private static boolean resolved, failed, selectWarned;

    private static boolean resolve() {
        if (resolved) return !failed;
        resolved = true;
        try {
            Class<?> cooldownsClass = Class.forName("io.redspace.ironsspellbooks.capabilities.magic.PlayerCooldowns");
            Class<?> syncedClass = Class.forName("io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData");
            Class<?> selectionClass = Class.forName("io.redspace.ironsspellbooks.gui.overlays.SpellSelection");
            getCooldowns = MagicData.class.getMethod("getPlayerCooldowns");
            isOnCooldown = cooldownsClass.getMethod("isOnCooldown", io.redspace.ironsspellbooks.api.spells.AbstractSpell.class);
            getSynced = MagicData.class.getMethod("getSyncedData");
            getSelection = syncedClass.getMethod("getSpellSelection");
            setSelection = syncedClass.getMethod("setSpellSelection", selectionClass);
            doSync = syncedClass.getMethod("doSync");
            makeSelection = selectionClass.getMethod("makeSelection", String.class, int.class);
            selectionCtor = selectionClass.getConstructor(String.class, int.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failed = true;
            io.github.rpgclasses.RpgClasses.LOGGER.warn("[rites_and_paths] La rotación de conjuros del Bardo no está disponible: {}", e.toString());
        }
        return !failed;
    }

    /** ¿Se puede tocar ahora? Combo suficiente y sin recarga. */
    static boolean castable(ServerPlayer player, SpellSelectionManager.SelectionOption option) {
        MusicSpellInfo info = MusicSpellInfo.byId(option.spellData.getSpell().getSpellId());
        if (info == null) return false;
        if (BardState.combo(player) < BardState.comboNeeded(player, info)) return false;
        if (!resolve()) return true;
        try {
            Object cooldowns = getCooldowns.invoke(MagicData.getPlayerMagicData(player));
            return cooldowns == null || !(Boolean) isOnCooldown.invoke(cooldowns, option.spellData.getSpell());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }
    }

    /**
     * Elige el siguiente conjuro que se pueda tocar. {@code afterCast}: avanza desde el actual aunque siga disponible.
     * {@code current}: la opción que se acaba de lanzar (la selección ya pudo cambiar); null = la seleccionada ahora.
     */
    static void rotate(ServerPlayer player, boolean afterCast, SpellSelectionManager.SelectionOption current) {
        if (Bard.instrumentHand(player) == null || !Bard.isBard(player) || !resolve()) return;
        SpellSelectionManager manager = new SpellSelectionManager(player);
        List<SpellSelectionManager.SelectionOption> options = new ArrayList<>();
        for (SpellSelectionManager.SelectionOption o : manager.getAllSpells()) {
            if (MusicBridge.isMusic(o.spellData.getSpell().getSpellId())) options.add(o);
        }
        if (options.isEmpty()) return;
        SpellSelectionManager.SelectionOption now = current != null ? current : manager.getSelection();
        int index = -1;
        for (int i = 0; i < options.size() && now != null; i++) {
            SpellSelectionManager.SelectionOption o = options.get(i);
            if (o.slot.equals(now.slot) && o.slotIndex == now.slotIndex) index = i;
        }
        if (!afterCast && index >= 0 && castable(player, options.get(index))) return;
        int n = options.size();
        int from = index < 0 ? 0 : index;
        for (int step = afterCast && index >= 0 ? 1 : 0; step <= n; step++) {
            SpellSelectionManager.SelectionOption candidate = options.get((from + step) % n);
            if (!castable(player, candidate)) continue;
            if (now == null || !candidate.slot.equals(now.slot) || candidate.slotIndex != now.slotIndex) select(player, candidate);
            return;
        }
    }

    private static void select(ServerPlayer player, SpellSelectionManager.SelectionOption option) {
        try {
            Object synced = getSynced.invoke(MagicData.getPlayerMagicData(player));
            Object selection = getSelection.invoke(synced);
            if (selection == null) selection = selectionCtor.newInstance(option.slot, option.slotIndex);
            else makeSelection.invoke(selection, option.slot, option.slotIndex);
            setSelection.invoke(synced, selection);
            doSync.invoke(synced);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!selectWarned) {
                selectWarned = true;
                io.github.rpgclasses.RpgClasses.LOGGER.warn("[rites_and_paths] No se pudo mover la selección de conjuro del Bardo: {}", e.toString());
            }
        }
    }
}
