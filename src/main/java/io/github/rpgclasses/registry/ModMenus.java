package io.github.rpgclasses.registry;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.content.race.BeltMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Menús propios (contenedores con pantalla). Por ahora solo el cinturón del Coloso Porteador. */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, RpgClasses.MODID);

    public static final RegistryObject<MenuType<BeltMenu>> BELT = MENUS.register("belt", () -> IForgeMenuType.create(BeltMenu::new));

    private ModMenus() {}
}
