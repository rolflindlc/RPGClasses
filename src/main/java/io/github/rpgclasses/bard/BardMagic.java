package io.github.rpgclasses.bard;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Puente del Bardo con Iron's Spells. Lo instala {@code compat.irons.music.MusicBridge} si Iron's está cargado; sin
 * Iron's {@link Bard#magic} es null y el Bardo toca y acumula combo, pero no tiene conjuros. Así, nada del paquete {@code bard} toca
 * clases de Iron's.
 */
public interface BardMagic {
    /** Clic derecho con el instrumento: lanza el conjuro musical elegido en la rueda de Iron's (si es del instrumento). */
    void castFromInstrument(ServerPlayer player, InteractionHand hand, ItemStack instrument);

    /** Reescribe el contenedor de conjuros de Iron's del instrumento a partir de sus partituras (si ha cambiado). */
    void syncContainer(ServerPlayer holder, ItemStack instrument);

    /** Contrapunto: todos los conjuros en recarga pierden esa fracción de lo que les queda. */
    void refundAllCooldowns(ServerPlayer player, double fraction);

    /** Maná actual y máximo, y sumar maná (Arpa de Cristal). */
    float mana(ServerPlayer player);

    float maxMana(ServerPlayer player);

    void addMana(ServerPlayer player, float amount);
}
