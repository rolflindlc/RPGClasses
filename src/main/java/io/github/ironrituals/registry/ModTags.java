package io.github.ironrituals.registry;

import io.github.ironrituals.IronRituals;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Tags del mod (plan, sección 2.7). */
public final class ModTags {
    public static final TagKey<Block> ARCANE_CANDLES = TagKey.create(Registries.BLOCK, IronRituals.id("arcane_candles"));
    public static final TagKey<Item> ARCANE_CANDLE_ITEMS = TagKey.create(Registries.ITEM, IronRituals.id("arcane_candles"));
    public static final TagKey<Block> TERRAFORMABLE = TagKey.create(Registries.BLOCK, IronRituals.id("terraformable"));

    public static final TagKey<EntityType<?>> SOUL_BLACKLIST = TagKey.create(Registries.ENTITY_TYPE, IronRituals.id("soul_blacklist"));
    public static final TagKey<EntityType<?>> TIME_IMMUNE = TagKey.create(Registries.ENTITY_TYPE, IronRituals.id("time_immune"));

    private ModTags() {}
}
