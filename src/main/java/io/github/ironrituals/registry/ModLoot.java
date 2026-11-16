package io.github.ironrituals.registry;

import com.mojang.serialization.Codec;
import io.github.ironrituals.IronRituals;
import io.github.ironrituals.loot.AddRitualTomeModifier;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModLoot {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, IronRituals.MOD_ID);

    public static final RegistryObject<Codec<AddRitualTomeModifier>> ADD_RITUAL_TOME =
            MODIFIERS.register("add_ritual_tome", AddRitualTomeModifier.CODEC);

    private ModLoot() {}
}
