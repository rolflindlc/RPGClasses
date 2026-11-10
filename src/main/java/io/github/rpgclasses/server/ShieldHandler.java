package io.github.rpgclasses.server;

import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.stat.StatCalculator;
import io.github.rpgclasses.core.stat.StatMath;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Muro de Escudos (parte vanilla): un muro de X aumenta el desgaste del escudo un X y su
 * cooldown de recuperación (cuando un hacha lo desactiva) un 2X. La guardia de Epic Fight
 * se añadirá por su puente en la fase 4.
 */
public final class ShieldHandler {
    /** Cooldown vanilla del escudo al ser desactivado por un hacha. */
    private static final int VANILLA_DISABLE_TICKS = 100;
    /** Vanilla solo desgasta el escudo si el golpe bloqueado es de 3 o más. */
    private static final float MIN_DAMAGE_TO_WEAR = 3f;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onShieldBlock(ShieldBlockEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        double wall = shieldWall(player);
        if (wall <= 0) return;

        ItemStack shield = player.getUseItem();
        if (shield.isEmpty()) return;
        InteractionHand hand = player.getUsedItemHand();
        Item item = shield.getItem();

        // Desgaste extra: vanilla quitará 1 + floor(daño); aquí se añade la fracción del muro.
        if (event.shieldTakesDamage() && event.getBlockedDamage() >= MIN_DAMAGE_TO_WEAR) {
            int vanillaWear = 1 + (int) Math.floor(event.getBlockedDamage());
            int extra = (int) Math.round(vanillaWear * (StatMath.shieldDurabilityFactor(wall) - 1));
            if (extra > 0) shield.hurtAndBreak(extra, player, p -> p.broadcastBreakEvent(hand));
        }

        // El cooldown por hacha se aplica después de este evento: se comprueba en cuanto termine el golpe.
        int extended = (int) Math.round(VANILLA_DISABLE_TICKS * StatMath.shieldCooldownFactor(wall));
        player.server.tell(new TickTask(player.server.getTickCount(), () -> {
            if (player.isRemoved()) return;
            // Un cooldown recién puesto está casi al 100%; así no se alarga dos veces el mismo.
            if (player.getCooldowns().getCooldownPercent(item, 0f) > 0.9f) {
                player.getCooldowns().addCooldown(item, extended);
            }
        }));
    }

    private static double shieldWall(ServerPlayer player) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        ClassDef classDef = data == null ? null : DefinitionStore.SERVER.classDef(data.state.classId);
        if (classDef == null) return 0;
        return StatCalculator.wall(data.state, classDef, DefinitionStore.SERVER.subclassDef(data.state.subclassId), Ids.SHIELD);
    }
}
