package io.github.rpgclasses.server;

import io.github.rpgclasses.registry.ModAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Da efecto a los atributos propios que no necesitan habilidades: salto, detección y curación.
 * Se registra en ambos lados porque el salto lo ejecuta el cliente (los atributos están sincronizados).
 */
public final class AttributeHandlers {
    /** Altura aproximada de un salto vanilla, para descontar la altura extra del daño por caída. */
    private static final double VANILLA_JUMP_HEIGHT = 1.25;

    /** jump_bonus = fracción de ALTURA extra. La altura crece con el cuadrado de la velocidad, de ahí la raíz. */
    @SubscribeEvent
    public void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        double bonus = ModAttributes.value(player, ModAttributes.JUMP_BONUS);
        if (bonus <= 0) return;
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(v.x, v.y * Math.sqrt(1 + bonus), v.z);
    }

    @SubscribeEvent
    public void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        double bonus = ModAttributes.value(player, ModAttributes.JUMP_BONUS);
        if (bonus <= 0) return;
        event.setDistance((float) Math.max(0, event.getDistance() - VANILLA_JUMP_HEIGHT * bonus));
    }

    /** Sigilo Físico: los mobs detectan al jugador desde más cerca. */
    @SubscribeEvent
    public void onVisibility(LivingEvent.LivingVisibilityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        double change = ModAttributes.value(player, ModAttributes.MOB_DETECTION);
        if (change != 0) event.modifyVisibility(Math.max(0, 1 + change));
    }

    @SubscribeEvent
    public void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        double bonus = ModAttributes.value(player, ModAttributes.HEALING_RECEIVED);
        if (bonus != 0) event.setAmount((float) Math.max(0, event.getAmount() * (1 + bonus)));
    }

    /** Ponerse o quitarse armadura cambia las pasivas "sin armadura", Coraza y el muro de Armadura. */
    @SubscribeEvent
    public void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)
                && event.getSlot().getType() == EquipmentSlot.Type.ARMOR) {
            StatEngine.recompute(player);
        }
    }
}
