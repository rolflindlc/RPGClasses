package io.github.rpgclasses.bard;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;


/**
 * Mesa de inscripción de Iron's con instrumentos: el instrumento va en la ranura del libro y una partitura en la del
 * pergamino. Iron's solo acepta {@code SpellBook} y {@code Scroll}, así que unos mixins ({@code mixin/irons} y
 * {@code mixin/client}) dejan pasar nuestros objetos y esta clase hace la inscripción: la partitura se guarda en el
 * NBT del instrumento (la fuente de verdad, como en el atril) y se reescribe su contenedor de conjuros.
 */
public final class InscriptionTable {
    /** Posiciones de las ranuras de la mesa en el menú de Iron's (tras los 36 huecos del inventario). */
    public static final int BOOK_SLOT = 36, SCROLL_SLOT = 37;

    private InscriptionTable() {}

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
