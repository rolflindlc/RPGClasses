package io.github.rpgclasses.bard;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Instrumento del Bardo (adenda del Bardo, sección 6). Va en la mano secundaria y guarda partituras; el clic derecho
 * (el carril de "usar" del minijuego) lanza el conjuro elegido. Sus bonus solo cuentan para un Bardo y los aplica
 * {@link BardState} como modificadores transitorios, no el objeto.
 */
public class InstrumentItem extends Item {
    public final Instrument instrument;

    public InstrumentItem(Instrument instrument, Properties props) {
        super(props.stacksTo(1));
        this.instrument = instrument;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return BardPlay.use(level, player, hand, player.getItemInHand(hand));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        BardPlay.inventoryTick(stack, level, entity, slot);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return instrument.tier == Instrument.Tier.LEGENDARY || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        BardPlay.tooltip(stack, instrument, tooltip);
    }
}
