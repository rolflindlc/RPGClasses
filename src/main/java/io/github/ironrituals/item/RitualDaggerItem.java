package io.github.ironrituals.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Daga Ritual (plan, 3.C). La captura de almas ocurre en {@code SoulEvents}; aquí está el descarte:
 * mantener Shift + clic derecho en el aire durante 3 s vacía la daga.
 */
public class RitualDaggerItem extends SwordItem {
    /** Ticks que hay que mantener Shift + clic derecho para liberar el alma (3 s). */
    public static final int DISCARD_TICKS = 60;

    public RitualDaggerItem(Properties props) {
        super(Tiers.IRON, 1, -1.8F, props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && SoulData.hasSoul(stack)) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return DISCARD_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (entity instanceof Player p && !p.isShiftKeyDown()) {
            p.stopUsingItem();
            return;
        }
        if (level.isClientSide && remaining % 4 == 0) {
            double y = entity.getY() + entity.getBbHeight() * 0.6;
            level.addParticle(ParticleTypes.SOUL, entity.getX(), y, entity.getZ(), 0, 0.05, 0);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && SoulData.hasSoul(stack)) {
            SoulData.clear(stack);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SOUL, entity.getX(), entity.getY() + 1.2, entity.getZ(), 12, 0.3, 0.4, 0.3, 0.03);
            }
            if (entity instanceof Player p) {
                p.displayClientMessage(Component.translatable("message.ironrituals.dagger.released"), true);
            }
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (SoulData.hasSoul(stack)) {
            tooltip.add(Component.translatable(SoulData.isPlayerSoul(stack)
                            ? "tooltip.ironrituals.dagger.player_soul" : "tooltip.ironrituals.dagger.soul",
                    SoulData.displayName(stack)).withStyle(ChatFormatting.RED));
            tooltip.add(Component.translatable("tooltip.ironrituals.dagger.release_hint").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.ironrituals.dagger.empty").withStyle(ChatFormatting.GRAY));
        }
    }
}
