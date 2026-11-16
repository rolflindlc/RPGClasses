package io.github.ironrituals.item;

import io.github.ironrituals.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Libro de Ritual (plan, 3.D): un único ítem con el id del ritual en NBT ({@code Ritual}). Clic derecho abre la
 * GUI de lectura con lore, pista, diagrama del círculo y componentes.
 */
public class RitualTomeItem extends Item {
    public static final String TAG_RITUAL = "Ritual";

    public RitualTomeItem(Properties props) {
        super(props);
    }

    public static ItemStack create(ResourceLocation ritual) {
        ItemStack stack = new ItemStack(ModItems.RITUAL_TOME.get());
        stack.getOrCreateTag().putString(TAG_RITUAL, ritual.toString());
        return stack;
    }

    @Nullable
    public static ResourceLocation ritualId(ItemStack stack) {
        if (stack.getTag() == null || !stack.getTag().contains(TAG_RITUAL)) return null;
        return ResourceLocation.tryParse(stack.getTag().getString(TAG_RITUAL));
    }

    public static Component ritualName(ItemStack stack) {
        ResourceLocation id = ritualId(stack);
        if (id == null) return Component.translatable("ritual.ironrituals.unknown");
        return Component.translatable("ritual." + id.getNamespace() + "." + id.getPath());
    }

    @Override
    public Component getName(ItemStack stack) {
        return ritualId(stack) == null
                ? super.getName(stack)
                : Component.translatable("item.ironrituals.ritual_tome.named", ritualName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation id = ritualId(stack);
        if (id == null) return;
        tooltip.add(Component.translatable("ritual." + id.getNamespace() + "." + id.getPath() + ".hint")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> io.github.ironrituals.client.ClientHooks.openTome(stack));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
