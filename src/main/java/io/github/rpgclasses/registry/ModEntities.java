package io.github.rpgclasses.registry;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.content.DecoyEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Entidades propias del mod. Por ahora solo el clon del Señuelo del Ilusionista de Sombras. */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, RpgClasses.MODID);

    public static final RegistryObject<EntityType<DecoyEntity>> DECOY = ENTITIES.register("decoy",
            () -> EntityType.Builder.<DecoyEntity>of(DecoyEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).clientTrackingRange(8).updateInterval(2)
                    .build(RpgClasses.MODID + ":decoy"));

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(DECOY.get(), DecoyEntity.createAttributes().build());
    }

    private ModEntities() {}
}
