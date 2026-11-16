package io.github.ironrituals.registry;

import io.github.ironrituals.IronRituals;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, IronRituals.MOD_ID);

    /** Trepa por paredes verticales (catalizador {@code climb} del Encogimiento). La lógica está en {@code PlayerStateEvents}. */
    public static final RegistryObject<MobEffect> WALL_CLIMBING = EFFECTS.register("wall_climbing",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x6B8E23) {});

    private ModEffects() {}
}
