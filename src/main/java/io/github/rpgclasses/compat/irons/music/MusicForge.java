package io.github.rpgclasses.compat.irons.music;

import io.github.rpgclasses.bard.MusicSpellInfo;
import io.github.rpgclasses.bard.Sheets;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.world.item.ItemStack;

/**
 * Forja de pergaminos de Iron's: los conjuros musicales se hacen allí como cualquier otro (papel + tinta + un foco de
 * la escuela, que aquí son los discos de música, el bloque musical y la gramola), pero en lugar de un pergamino sale la
 * partitura. El nivel lo da la rareza de la tinta, igual que en los pergaminos, sin pasar del máximo del conjuro.
 * Lo llama {@code ScrollForgeMenuMixin} con el pergamino que Iron's iba a poner en la ranura de resultado.
 */
public final class MusicForge {
    private MusicForge() {}

    public static ItemStack toSheet(ItemStack scroll) {
        try {
            if (scroll == null || scroll.isEmpty() || !ISpellContainer.isSpellContainer(scroll)) return scroll;
            ISpellContainer container = ISpellContainer.get(scroll);
            SpellData data = container == null ? null : container.getSpellAtIndex(0);
            if (data == null || data == SpellData.EMPTY) return scroll;
            String id = data.getSpell().getSpellId();
            MusicSpellInfo info = MusicBridge.isMusic(id) ? MusicSpellInfo.byId(id) : null;
            if (info == null) return scroll;
            return Sheets.sheet(info, Math.max(1, Math.min(info.maxLevel, data.getLevel())));
        } catch (RuntimeException e) {
            return scroll;
        }
    }
}
