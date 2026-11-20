package io.github.rpgclasses.registry;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.content.DecoyEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Entidades propias del mod: el clon del Señuelo (Ilusionista de Sombras), las formas del Slime (adenda 0.7) y el cuerpo que reza (Intercesor). */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, RpgClasses.MODID);

    public static final RegistryObject<EntityType<DecoyEntity>> DECOY = ENTITIES.register("decoy",
            () -> EntityType.Builder.<DecoyEntity>of(DecoyEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).clientTrackingRange(8).updateInterval(2)
                    .build(RpgClasses.MODID + ":decoy"));

    /** Slime pequeño del Slime Enjambre (como un slime vanilla de tamaño 1). */
    public static final RegistryObject<EntityType<io.github.rpgclasses.content.race.SwarmSlime>> SWARM_SLIME = ENTITIES.register("swarm_slime",
            () -> EntityType.Builder.<io.github.rpgclasses.content.race.SwarmSlime>of(io.github.rpgclasses.content.race.SwarmSlime::new, MobCategory.MISC)
                    .sized(0.52f, 0.52f).clientTrackingRange(10).updateInterval(2)
                    .build(RpgClasses.MODID + ":swarm_slime"));

    /** Copia del Slime Mitótico (un clon del jugador, como el Señuelo). */
    public static final RegistryObject<EntityType<io.github.rpgclasses.content.race.MitosisCopy>> MITOSIS_COPY = ENTITIES.register("mitosis_copy",
            () -> EntityType.Builder.<io.github.rpgclasses.content.race.MitosisCopy>of(io.github.rpgclasses.content.race.MitosisCopy::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).clientTrackingRange(10).updateInterval(2)
                    .build(RpgClasses.MODID + ":mitosis_copy"));

    /** Cuerpo agachado del Clérigo mientras reza (Plegaria del Intercesor): el jugador va como una cabeza junto al aliado. */
    public static final RegistryObject<EntityType<io.github.rpgclasses.cleric.PrayingBody>> PRAYING_BODY = ENTITIES.register("praying_body",
            () -> EntityType.Builder.<io.github.rpgclasses.cleric.PrayingBody>of(io.github.rpgclasses.cleric.PrayingBody::new, MobCategory.MISC)
                    .sized(0.6f, 1.5f).clientTrackingRange(10).updateInterval(2).noSave()
                    .build(RpgClasses.MODID + ":praying_body"));

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(DECOY.get(), DecoyEntity.createAttributes().build());
        event.put(SWARM_SLIME.get(), io.github.rpgclasses.content.race.SwarmSlime.createAttributes().build());
        event.put(MITOSIS_COPY.get(), DecoyEntity.createAttributes().build());
        event.put(PRAYING_BODY.get(), DecoyEntity.createAttributes().build());
    }

    private ModEntities() {}
}
