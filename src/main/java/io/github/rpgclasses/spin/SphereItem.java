package io.github.rpgclasses.spin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Bola de un tier (adenda del Maestro del Giro, sección 3): se fabrica en cruz con 5 unidades del material, tiene
 * durabilidad (1 por impacto), se repara en el yunque con su material y acepta los encantamientos de bola. Puede llevar
 * una mejora ({@link SphereUpgrade}, en su NBT). Se lanza desde la bandolera ({@link BandolierItem}); suelta en la mano no
 * hace nada. Se dibuja como un núcleo voxel (color del tier) con un anillo (color de la mejora).
 */
public class SphereItem extends Item {
    public final SphereMaterial material;

    public SphereItem(SphereMaterial material, Properties properties) {
        super(properties);
        this.material = material;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return stack.getCount() == 1;
    }

    @Override
    public int getEnchantmentValue() {
        return material.enchantability;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return material.repairIngredient().test(repair) || super.isValidRepairItem(stack, repair);
    }

    @Override
    public Component getName(ItemStack stack) {
        SphereUpgrade u = SphereUpgrade.of(stack);
        return u == null ? super.getName(stack) : new SphereKind(material, u).name();
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        SphereUpgrade u = SphereUpgrade.of(stack);
        double damage = SpinMaster.anySide(SpinMaster.SPHERE_THROW, "damage_" + material.id, 1.0)
                * (u == null ? 1 : SpinMaster.anySide(SpinMaster.SPHERE_THROW, "damage_" + u.id, 1.0));
        tooltip.add(Component.translatable("tooltip.rites_and_paths.sphere.damage", String.format(Locale.ROOT, "%.2f", damage))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.rites_and_paths.sphere.effect." + material.id).withStyle(ChatFormatting.GOLD));
        if (u != null) tooltip.add(Component.translatable("tooltip.rites_and_paths.sphere.effect." + u.id).withStyle(ChatFormatting.YELLOW));
        else tooltip.add(Component.translatable("tooltip.rites_and_paths.sphere.upgrade_hint").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.rites_and_paths.sphere.only_class").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return io.github.rpgclasses.client.spin.SpinItemRenderer.get();
            }
        });
    }
}
