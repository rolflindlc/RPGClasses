package io.github.rpgclasses.druid;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Libro de Estudio del Druida: mantén clic derecho sobre un animal o una bestia vivos hasta llenar la barra. Si recibes
 * daño o dejas de mirarlo, se reinicia. Se gasta al terminar y da conocimiento de la especie ({@link Knowledge}).
 */
public class StudyBookItem extends Item {
    public StudyBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            if (!Knowledge.startStudy(sp)) return InteractionResultHolder.fail(stack);
        } else if (!Druid.isDruid(player) || Knowledge.lookTarget(player) == null) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (entity instanceof ServerPlayer sp) Knowledge.tickStudy(sp, stack, getUseDuration(stack) - remaining);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (entity instanceof ServerPlayer sp) Knowledge.cancelStudy(sp, false);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.rites_and_paths.study_book").withStyle(ChatFormatting.GRAY));
    }
}
