package io.github.rpgclasses.bard;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;


/**
 * Mesa de inscripción de Iron's con instrumentos: el instrumento va en la ranura del libro y una partitura en la del
 * pergamino. Iron's solo acepta {@code SpellBook} y {@code Scroll}, así que unos mixins ({@code mixin/irons} y
 * {@code mixin/client}) dejan pasar nuestros objetos y esta clase hace la inscripción: la partitura se guarda en el
 * NBT del instrumento (la fuente de verdad, como en el atril) y se reescribe su contenedor de conjuros.
 *
 * <p>Sacar: como con un libro, se elige un hueco del instrumento y su partitura aparece en la ranura de resultado;
 * al llevársela se quita del instrumento ({@link #extract}).
 */
public final class InscriptionTable {
    /** Posiciones de las ranuras de la mesa en el menú de Iron's (tras los 36 huecos del inventario). */
    public static final int BOOK_SLOT = 36, SCROLL_SLOT = 37, RESULT_SLOT = 38;
    /**
     * Botón de inscribir una partitura. Iron's usa -1 para inscribir y también para «sin selección» (la pantalla lo
     * manda cada vez que cambia el objeto de la ranura del libro), así que con -1 se inscribía sola una partitura que
     * estuviera ya puesta. El nuestro es otro número.
     */
    public static final int INSCRIBE_BUTTON = -2;

    /** Lo que el mixin del menú de Iron's añade a {@code InscriptionTableMenu}. */
    public interface Menu {
        /**
         * Se ha cogido lo que había en la ranura de resultado. Con un instrumento en la mesa quita su partitura del
         * instrumento (en el servidor) y devuelve true; con un libro devuelve false y sigue el camino de Iron's.
         */
        boolean rites_and_paths$onResultTaken(Player player);
    }

    private InscriptionTable() {}

    /** Partitura guardada en el instrumento que corresponde al hueco {@code index} que dibuja la mesa, o null. */
    private static Sheets.Entry entryAt(ItemStack instrument, int index) {
        if (Bard.magic == null || index < 0 || !isInstrument(instrument)) return null;
        MusicSpellInfo spell = Bard.magic.spellAt(instrument, index);
        if (spell == null) return null;
        for (Sheets.Entry e : Sheets.read(instrument)) {
            if (e.spell() == spell) return e;
        }
        return null;
    }

    /** Lo que sale en la ranura de resultado con ese hueco elegido: la partitura con su nivel guardado, o nada. */
    public static ItemStack extractPreview(ItemStack instrument, int index) {
        Sheets.Entry e = entryAt(instrument, index);
        return e == null ? ItemStack.EMPTY : Sheets.sheet(e.spell(), e.level());
    }

    /** Servidor: el jugador se ha llevado la partitura del hueco {@code index}; se quita del instrumento. */
    public static void extract(ServerPlayer player, Slot bookSlot, int index) {
        ItemStack instrument = bookSlot.getItem();
        Sheets.Entry e = entryAt(instrument, index);
        if (e == null) return;
        SimpleContainer sheets = new SimpleContainer(Instrument.MAX_SLOTS);
        Sheets.fill(instrument, sheets);
        sheets.setItem(e.slot(), ItemStack.EMPTY);
        Sheets.store(instrument, sheets);
        if (Bard.magic != null) Bard.magic.syncContainer(player, instrument);
        bookSlot.setChanged();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.8f);
    }

    public static boolean isInstrument(ItemStack stack) {
        return BardItems.instrumentOf(stack) != null;
    }

    public static boolean isSheet(ItemStack stack) {
        return stack != null && !stack.isEmpty() && Sheets.spellOf(stack) != null;
    }

    /** Servidor: guarda la partitura en el primer hueco libre del instrumento (con las mismas reglas que el atril). */
    public static void inscribe(ServerPlayer player, Slot bookSlot, Slot sheetSlot) {
        ItemStack instrument = bookSlot.getItem();
        ItemStack sheet = sheetSlot.getItem();
        Instrument kind = BardItems.instrumentOf(instrument);
        MusicSpellInfo spell = Sheets.spellOf(sheet);
        if (kind == null || spell == null) return;
        if (!Bard.isBard(player)) {
            player.displayClientMessage(Component.translatable("msg.rites_and_paths.bard.not_bard"), true);
            return;
        }
        int capacity = Bard.capacity(player, kind);
        if (Sheets.levelOf(sheet) > spell.levelCap(kind.levelCap())) return;
        SimpleContainer sheets = new SimpleContainer(Instrument.MAX_SLOTS);
        Sheets.fill(instrument, sheets);
        int free = -1;
        for (int i = 0; i < sheets.getContainerSize(); i++) {
            if (Sheets.spellOf(sheets.getItem(i)) == spell) return; // sin repetir conjuro
            if (free < 0 && i < capacity && sheets.getItem(i).isEmpty()) free = i;
        }
        if (free < 0) return;
        sheets.setItem(free, sheet.copyWithCount(1));
        Sheets.store(instrument, sheets);
        sheetSlot.remove(1);
        if (Bard.magic != null) Bard.magic.syncContainer(player, instrument);
        bookSlot.setChanged();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
    }
}
