package io.github.rpgclasses.bard;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Partituras: la que va suelta (un objeto con conjuro y nivel) y las guardadas en un instrumento. El instrumento
 * guarda sus partituras en su propio NBT, que es la fuente de verdad; el contenedor de conjuros de Iron's se
 * reescribe a partir de él ({@link BardMagic#syncContainer}).
 */
public final class Sheets {
    public static final String SPELL = "rpgclasses_spell";
    public static final String LEVEL = "rpgclasses_level";
    public static final String LIST = "rpgclasses_sheets";

    /** Partitura guardada en el hueco {@code slot} de un instrumento. */
    public record Entry(int slot, MusicSpellInfo spell, int level) {}

    private Sheets() {}

    public static ItemStack sheet(MusicSpellInfo spell, int level) {
        ItemStack stack = new ItemStack(BardItems.SHEET_MUSIC.get());
        stack.getOrCreateTag().putString(SPELL, spell.fullId());
        stack.getOrCreateTag().putInt(LEVEL, Math.max(1, Math.min(spell.maxLevel, level)));
        return stack;
    }

    public static MusicSpellInfo spellOf(ItemStack sheet) {
        CompoundTag tag = sheet.getTag();
        return tag == null || !(sheet.getItem() instanceof SheetMusicItem) ? null : MusicSpellInfo.byId(tag.getString(SPELL));
    }

    public static int levelOf(ItemStack sheet) {
        CompoundTag tag = sheet.getTag();
        MusicSpellInfo spell = spellOf(sheet);
        if (tag == null || spell == null) return 0;
        return Math.max(1, Math.min(spell.maxLevel, tag.getInt(LEVEL)));
    }

    /** Partituras del instrumento, ordenadas por hueco (las de conjuros que ya no existen se ignoran). */
    public static List<Entry> read(ItemStack instrument) {
        List<Entry> out = new ArrayList<>();
        CompoundTag tag = instrument.getTag();
        if (tag == null || !tag.contains(LIST, Tag.TAG_LIST)) return out;
        ListTag list = tag.getList(LIST, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            MusicSpellInfo spell = MusicSpellInfo.byId(e.getString(SPELL));
            int slot = e.getInt("slot");
            if (spell == null || slot < 0 || slot >= Instrument.MAX_SLOTS) continue;
            out.add(new Entry(slot, spell, Math.max(1, Math.min(spell.maxLevel, e.getInt(LEVEL)))));
        }
        out.sort(Comparator.comparingInt(Entry::slot));
        return out;
    }

    /** Copia las partituras del instrumento en un contenedor (un hueco por partitura). */
    public static void fill(ItemStack instrument, Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) container.setItem(i, ItemStack.EMPTY);
        for (Entry e : read(instrument)) {
            if (e.slot() < container.getContainerSize()) container.setItem(e.slot(), sheet(e.spell(), e.level()));
        }
    }

    /** Guarda en el instrumento las partituras del contenedor. */
    public static void store(ItemStack instrument, Container container) {
        ListTag list = new ListTag();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            MusicSpellInfo spell = spellOf(s);
            if (spell == null) continue;
            CompoundTag e = new CompoundTag();
            e.putInt("slot", i);
            e.putString(SPELL, spell.fullId());
            e.putInt(LEVEL, levelOf(s));
            list.add(e);
        }
        if (list.isEmpty()) instrument.removeTagKey(LIST);
        else instrument.getOrCreateTag().put(LIST, list);
    }

    /**
     * Conjuros activos con la capacidad del jugador que lo lleva: los de los huecos por debajo de la capacidad,
     * con el nivel limitado por la rareza del instrumento.
     */
    public static List<Entry> active(ItemStack instrument, int capacity) {
        Instrument kind = BardItems.instrumentOf(instrument);
        List<Entry> out = new ArrayList<>();
        if (kind == null) return out;
        for (Entry e : read(instrument)) {
            if (e.slot() >= capacity) continue;
            out.add(new Entry(e.slot(), e.spell(), Math.min(e.level(), e.spell().levelCap(kind.levelCap()))));
        }
        return out;
    }
}
