package io.github.rpgclasses.druid;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import java.util.List;

/**
 * Recipiente de Carne (Escultor): clic derecho abre sus cuatro huecos (cabeza, torso, patas, instinto). Completo, se
 * lanza sobre un Altar de Carne para crear la quimera. Las partes se guardan en el propio objeto ({@code Parts}).
 */
public class FleshVesselItem extends Item {
    public static final String PARTS = "Parts";

    public FleshVesselItem(Properties properties) {
        super(properties);
    }

    /** Las cuatro partes (vacías si falta alguna). */
    public static ItemStack[] parts(ItemStack vessel) {
        ItemStack[] out = new ItemStack[4];
        ListTag list = vessel.getTag() == null ? new ListTag() : vessel.getTag().getList(PARTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < 4; i++) out[i] = i < list.size() ? ItemStack.of(list.getCompound(i)) : ItemStack.EMPTY;
        return out;
    }

    public static void setParts(ItemStack vessel, ItemStack[] parts) {
        ListTag list = new ListTag();
        for (int i = 0; i < 4; i++) list.add(parts[i] == null || parts[i].isEmpty() ? new CompoundTag() : parts[i].save(new CompoundTag()));
        vessel.getOrCreateTag().put(PARTS, list);
    }

    /** La quimera que forman las partes, o null si falta alguna o no encaja en su hueco. */
    public static FormSpec.Chimera chimera(ItemStack vessel) {
        ItemStack[] parts = parts(vessel);
        String[] species = new String[4];
        for (int i = 0; i < 4; i++) {
            if (!(parts[i].getItem() instanceof BeastPartItem part) || part.part != i) return null;
            species[i] = BeastPartItem.species(parts[i]);
            if (species[i].isEmpty()) return null;
        }
        return new FormSpec.Chimera(species[0], species[1], species[2], species[3]);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, pl) -> new VesselMenu(id, inv, hand),
                    stack.getHoverName()), buf -> buf.writeBoolean(hand == InteractionHand.MAIN_HAND));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        int n = 0;
        for (ItemStack p : parts(stack)) if (!p.isEmpty()) n++;
        tooltip.add(Component.translatable("tooltip.rites_and_paths.flesh_vessel.parts", n).withStyle(n == 4 ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        for (ItemStack p : parts(stack)) if (!p.isEmpty()) tooltip.add(Component.literal(" - ").append(p.getHoverName()).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.rites_and_paths.flesh_vessel").withStyle(ChatFormatting.GRAY));
    }
}
