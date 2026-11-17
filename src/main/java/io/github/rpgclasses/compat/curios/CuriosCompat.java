package io.github.rpgclasses.compat.curios;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.gear.Curio;
import io.github.rpgclasses.gear.CurioEffects;
import io.github.rpgclasses.gear.CurioItem;
import io.github.rpgclasses.gear.GearRules;
import io.github.rpgclasses.gear.KnucklesItem;
import io.github.rpgclasses.gear.ModGear;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * Puente con Curios (solo se carga si el mod está instalado): busca nudilleras y curios equipados y aplica las
 * restricciones de equipo (clase o raza, y como máximo 1 Reliquia). Las ranuras se asignan al jugador por datos
 * ({@code data/rites_and_paths/curios/entities}) y cada objeto va a su ranura por tag ({@code curios:<ranura>}).
 */
public final class CuriosCompat {
    public static void init() {
        GearRules.curioKnuckles = p -> find(p, s -> s.getItem() instanceof KnucklesItem);
        CurioEffects.equipped = (p, c) -> find(p, s -> s.getItem() instanceof CurioItem ci && ci.curio == c);
        RpgClasses.LOGGER.info("[rites_and_paths] Curios detectado: nudilleras y curios de clase y raza activos");
    }

    /** Registro de las restricciones de equipo; llamar en la configuración común, con los objetos ya registrados. */
    public static void registerCurios() {
        ICurioItem restricted = new ICurioItem() {
            @Override
            public boolean canEquip(SlotContext context, ItemStack stack) {
                LivingEntity entity = context.entity();
                if (!(stack.getItem() instanceof CurioItem ci)) return true;
                if (!(entity instanceof Player player) || !ci.curio.allowed(player)) return false;
                if (ci.curio.rarity != Curio.Rarity.RELIC) return true;
                // Máximo 1 Reliquia: no se puede equipar si ya hay otra distinta en otra ranura.
                ItemStack other = find(player, s -> s != stack && s.getItem() instanceof CurioItem o && o.curio.rarity == Curio.Rarity.RELIC);
                return other.isEmpty();
            }
        };
        for (RegistryObject<Item> ro : ModGear.CURIOS.values()) CuriosApi.registerCurio(ro.get(), restricted);
    }

    private static ItemStack find(Player player, java.util.function.Predicate<ItemStack> filter) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(handler -> handler.findFirstCurio(filter))
                .map(SlotResult::stack)
                .orElse(ItemStack.EMPTY);
    }

    private CuriosCompat() {}
}
