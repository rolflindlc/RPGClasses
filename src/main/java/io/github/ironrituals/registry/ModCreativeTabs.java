package io.github.ironrituals.registry;

import io.github.ironrituals.IronRituals;
import io.github.ironrituals.core.RitualIds;
import io.github.ironrituals.item.RitualTomeItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IronRituals.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.ironrituals"))
            .icon(() -> new ItemStack(ModItems.RITUAL_CORE.get()))
            .displayItems((params, out) -> {
                out.accept(ModItems.RITUAL_CORE.get());
                out.accept(ModItems.ARCANE_CANDLE.get());
                out.accept(ModItems.ARCANE_CANDLE_WORN.get());
                out.accept(ModItems.ARCANE_CANDLE_STUB.get());
                out.accept(ModItems.RITUAL_DAGGER.get());
                for (String id : RitualIds.ALL) out.accept(RitualTomeItem.create(IronRituals.id(id)));
            })
            .build());

    private ModCreativeTabs() {}
}
