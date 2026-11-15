package io.github.ironrituals.event;

import io.github.ironrituals.IronRituals;
import io.github.ironrituals.block.RitualCoreBlockEntity;
import io.github.ironrituals.config.ServerConfig;
import io.github.ironrituals.item.RitualDaggerItem;
import io.github.ironrituals.item.SoulData;
import io.github.ironrituals.registry.ModTags;
import io.github.ironrituals.ritual.Allies;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = IronRituals.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CommonEvents {
    private CommonEvents() {}

    /**
     * Captura de almas con la Daga Ritual (plan, 3.C). Prioridad mínima para respetar a quien cancele la muerte.
     * La daga no gana un alma si ya tiene una.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player killer)) return;
        if (event.getSource().getDirectEntity() != killer) return; // golpe directo, no proyectiles
        if (!(killer.level() instanceof ServerLevel level)) return;
        ItemStack dagger = killer.getMainHandItem();
        if (!(dagger.getItem() instanceof RitualDaggerItem)) return;

        LivingEntity target = event.getEntity();
        double x = target.getX(), y = target.getY() + target.getBbHeight() * 0.5, z = target.getZ();

        boolean soulless = target.getType().is(ModTags.SOUL_BLACKLIST) || target.getPersistentData().getBoolean(SoulData.SOULLESS_FLAG);
        if (SoulData.hasSoul(dagger) || soulless) {
            level.sendParticles(ParticleTypes.SMOKE, x, y, z, 10, 0.2, 0.3, 0.2, 0.01);
            level.playSound(null, x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.4F);
            killer.displayClientMessage(Component.translatable(soulless
                    ? "message.ironrituals.dagger.soulless" : "message.ironrituals.dagger.full").withStyle(ChatFormatting.GRAY), true);
            return;
        }

        SoulData.capture(dagger, target);
        level.sendParticles(ParticleTypes.SOUL, x, y, z, 14, 0.25, 0.4, 0.25, 0.04);
        level.playSound(null, x, y, z, SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0F, 0.7F);
        killer.displayClientMessage(Component.translatable("message.ironrituals.dagger.captured", SoulData.displayName(dagger))
                .withStyle(ChatFormatting.RED), true);
    }

    /** Protección del núcleo frente a no aliados (plan, 2.4). */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!ServerConfig.CORE_BREAK_PROTECTION.get()) return;
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof RitualCoreBlockEntity core)) return;
        Player player = event.getPlayer();
        if (Allies.canAccess(player, core.getOwner(), core.getOwnerName())) return;
        event.setCanceled(true);
        player.displayClientMessage(Component.translatable("message.ironrituals.core.not_yours", core.getOwnerName())
                .withStyle(ChatFormatting.RED), true);
    }
}
