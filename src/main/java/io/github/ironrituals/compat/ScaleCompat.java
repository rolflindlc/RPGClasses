package io.github.ironrituals.compat;

import io.github.ironrituals.IronRituals;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Escala de entidades sin dependencia dura (plan, 4.C.16). Orden de preferencia:
 * <ol>
 *   <li>Mod de Razas y Clases: atributo {@code rpgclasses:scale} (escala = 1 + valor). Se aplica un modificador
 *       <b>transitorio</b> recalculado cada segundo, así el factor es exactamente multiplicativo aunque cambie la
 *       escala racial, y nada queda guardado si el jugador se desconecta o muere.</li>
 *   <li>Pehkui (por reflexión): escala base × factor; se guarda una marca en el jugador para deshacerlo
 *       (÷ factor) aunque el ritual termine con él desconectado.</li>
 * </ol>
 */
public final class ScaleCompat {
    private static final ResourceLocation RPG_SCALE = ResourceLocation.fromNamespaceAndPath("rpgclasses", "scale");
    private static final UUID MODIFIER_ID = UUID.fromString("5f0c2a7e-2b1d-4c8e-9a51-3d7e1b6c0a42");
    public static final String PEHKUI_MARK = "ironrituals_pehkui_scale";

    private ScaleCompat() {}

    @Nullable
    private static Attribute rpgAttribute() {
        return ForgeRegistries.ATTRIBUTES.getValue(RPG_SCALE);
    }

    public static boolean available() {
        return rpgAttribute() != null || ModList.get().isLoaded("pehkui");
    }

    /** Aplica (o recalcula) el factor de escala. Idempotente. */
    public static void apply(ServerPlayer player, double factor) {
        Attribute attr = rpgAttribute();
        if (attr != null) {
            AttributeInstance inst = player.getAttribute(attr);
            if (inst == null) return;
            AttributeModifier current = inst.getModifier(MODIFIER_ID);
            inst.removeModifier(MODIFIER_ID);
            double base = inst.getValue(); // escala sin este ritual: 1 + base
            double amount = (factor - 1.0) * (1.0 + base);
            if (current != null && Math.abs(current.getAmount() - amount) < 1e-6) {
                inst.addTransientModifier(current);
                return;
            }
            inst.addTransientModifier(new AttributeModifier(MODIFIER_ID, "Iron Rituals: shrinking", amount, AttributeModifier.Operation.ADDITION));
            return;
        }
        if (player.getPersistentData().contains(PEHKUI_MARK)) return;
        Float target = pehkuiTarget(player);
        if (target == null) return;
        if (pehkuiSet(player, (float) (target * factor))) player.getPersistentData().putDouble(PEHKUI_MARK, factor);
    }

    /** Deshace el factor aplicado (exacto: quita el modificador o divide la escala de Pehkui). */
    public static void restore(ServerPlayer player) {
        Attribute attr = rpgAttribute();
        if (attr != null) {
            AttributeInstance inst = player.getAttribute(attr);
            if (inst != null) inst.removeModifier(MODIFIER_ID);
        }
        if (player.getPersistentData().contains(PEHKUI_MARK)) {
            double factor = player.getPersistentData().getDouble(PEHKUI_MARK);
            Float target = pehkuiTarget(player);
            if (target != null && factor > 0 && pehkuiSet(player, (float) (target / factor))) {
                player.getPersistentData().remove(PEHKUI_MARK);
            }
        }
    }

    // ------------------------------------------------------------------ Pehkui por reflexión

    private static Object pehkuiData(Entity entity) throws ReflectiveOperationException {
        Class<?> types = Class.forName("virtuoel.pehkui.api.ScaleTypes");
        Object base = types.getField("BASE").get(null);
        Method get = base.getClass().getMethod("getScaleData", Entity.class);
        return get.invoke(base, entity);
    }

    @Nullable
    private static Float pehkuiTarget(Entity entity) {
        if (!ModList.get().isLoaded("pehkui")) return null;
        try {
            Object data = pehkuiData(entity);
            return (Float) data.getClass().getMethod("getTargetScale").invoke(data);
        } catch (ReflectiveOperationException | RuntimeException e) {
            IronRituals.LOGGER.warn("No se pudo leer la escala de Pehkui", e);
            return null;
        }
    }

    private static boolean pehkuiSet(Entity entity, float scale) {
        try {
            Object data = pehkuiData(entity);
            data.getClass().getMethod("setTargetScale", float.class).invoke(data, scale);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            IronRituals.LOGGER.warn("No se pudo cambiar la escala de Pehkui", e);
            return false;
        }
    }
}
