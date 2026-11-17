package io.github.rpgclasses.bard;

import io.github.rpgclasses.RpgClasses;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Entidades de los conjuros del Bardo: la Nota Cortante y el Piano de Cola. */
public final class BardEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, RpgClasses.MODID);

    public static final RegistryObject<EntityType<CuttingNoteEntity>> CUTTING_NOTE = ENTITIES.register("cutting_note",
            () -> EntityType.Builder.<CuttingNoteEntity>of(CuttingNoteEntity::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f).noSave().clientTrackingRange(6).updateInterval(1)
                    .build(RpgClasses.MODID + ":cutting_note"));

    public static final RegistryObject<EntityType<GrandPianoEntity>> GRAND_PIANO = ENTITIES.register("grand_piano",
            () -> EntityType.Builder.<GrandPianoEntity>of(GrandPianoEntity::new, MobCategory.MISC)
                    .sized(2.0f, 1.2f).noSave().clientTrackingRange(8).updateInterval(1)
                    .build(RpgClasses.MODID + ":grand_piano"));

    private BardEntities() {}
}
