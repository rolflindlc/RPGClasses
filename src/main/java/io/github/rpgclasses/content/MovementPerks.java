package io.github.rpgclasses.content;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.server.MechanicService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.UUID;

/**
 * Perks de movimiento que se ejecutan en los dos lados (el movimiento del jugador lo calcula su cliente
 * y el servidor lo valida): Pies Ligeros (Monje) y Caída de Pluma (Mago). El cliente llama a
 * {@link #lightFeetTick} y {@link #featherFallTick} para el jugador local; el servidor, aquí.
 */
public final class MovementPerks {
    public static final String LIGHT_FEET = "light_feet";
    public static final String FEATHER_FALL = "feather_fall";
    private static final UUID SURFACE_ID = UUID.fromString("9a1d7c3e-5b2f-4e8a-b6c4-0d3f2e1a9b57");
    /** No empieza por "rites_and_paths:" a propósito: StatEngine no debe borrarlo al recalcular. */
    private static final String SURFACE_NAME = "rites_and_paths_perk:light_feet";
    /** Velocidad máxima de caída manteniendo saltar (como Caída Lenta). */
    private static final double FEATHER_FALL_SPEED = -0.05;

    private static Field stuckField;
    private static boolean stuckFieldSearched;

    /**
     * Pies Ligeros: sin la ralentización de telarañas y nieve en polvo (se anula el "atascado" que dejan al
     * final del movimiento) ni la de arena de almas y miel (se compensa su factor de velocidad).
     * Llamar al final de cada tic del jugador.
     */
    public static void lightFeetTick(Player player, boolean active) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SURFACE_ID);
        if (!active) return;
        clearStuck(player);
        float factor = blockSpeedFactor(player);
        if (speed != null && factor > 0.05f && factor < 1f) {
            speed.addTransientModifier(new AttributeModifier(SURFACE_ID, SURFACE_NAME, 1 / factor - 1, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /** Caída de Pluma: manteniendo saltar, la caída es lenta. Lo llama el cliente del jugador local. */
    public static void featherFallTick(Player player, boolean jumpHeld) {
        if (!jumpHeld || player.onGround() || player.isInWater() || player.isFallFlying() || player.getAbilities().flying) return;
        Vec3 v = player.getDeltaMovement();
        if (v.y < FEATHER_FALL_SPEED) player.setDeltaMovement(v.x, FEATHER_FALL_SPEED, v.z);
        player.resetFallDistance();
    }

    /** Igual que Entity#getBlockSpeedFactor (protegido): el bloque de los pies y, si no frena, el de debajo. */
    private static float blockSpeedFactor(Player player) {
        BlockState feet = player.level().getBlockState(player.blockPosition());
        float f = feet.getBlock().getSpeedFactor();
        if (feet.is(Blocks.WATER) || feet.is(Blocks.BUBBLE_COLUMN) || f != 1.0f) return f;
        BlockPos below = BlockPos.containing(player.getX(), player.getBoundingBox().minY - 0.5000001, player.getZ());
        return player.level().getBlockState(below).getBlock().getSpeedFactor();
    }

    /**
     * El campo "stuckSpeedMultiplier" de Entity no es público y su nombre cambia con la ofuscación: se localiza
     * una vez marcando un valor único con el método público makeStuckInBlock y buscando qué campo lo guarda.
     */
    private static void clearStuck(Entity entity) {
        if (!stuckFieldSearched) {
            stuckFieldSearched = true;
            Vec3 marker = new Vec3(0.123456, 0.654321, 0.123456);
            entity.makeStuckInBlock(Blocks.AIR.defaultBlockState(), marker);
            try {
                for (Field f : Entity.class.getDeclaredFields()) {
                    if (f.getType() != Vec3.class || Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    if (f.get(entity) == marker) {
                        stuckField = f;
                        break;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                RpgClasses.LOGGER.warn("[rites_and_paths] Pies Ligeros: no se pudo localizar la ralentización por bloques", e);
            }
            if (stuckField == null) RpgClasses.LOGGER.warn("[rites_and_paths] Pies Ligeros: telarañas y nieve en polvo seguirán frenando");
        }
        if (stuckField == null) return;
        try {
            stuckField.set(entity, Vec3.ZERO);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // no debería pasar tras localizarlo
        }
    }

    // --- Servidor ---

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        lightFeetTick(player, MechanicService.has(player, LIGHT_FEET));
    }

    /** Caída de Pluma: sin daño por caída. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && MechanicService.has(player, FEATHER_FALL)) event.setCanceled(true);
    }
}
