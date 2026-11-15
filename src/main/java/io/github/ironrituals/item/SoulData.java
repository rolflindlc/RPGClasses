package io.github.ironrituals.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Alma guardada en la Daga Ritual (plan, 3.C). NBT: {@code IronRitualsSoul: {Entity, PlayerId?, PlayerName?}}.
 */
public final class SoulData {
    public static final String TAG = "IronRitualsSoul";
    /** Marca en {@code getPersistentData()} de entidades sin alma capturable (clones, cascarones). */
    public static final String SOULLESS_FLAG = "ironrituals:soulless";

    private SoulData() {}

    public static boolean hasSoul(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG);
    }

    public static void capture(ItemStack stack, LivingEntity target) {
        CompoundTag soul = new CompoundTag();
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        soul.putString("Entity", type == null ? "minecraft:pig" : type.toString());
        if (target instanceof Player p) {
            soul.putUUID("PlayerId", p.getUUID());
            soul.putString("PlayerName", p.getGameProfile().getName());
        }
        stack.getOrCreateTag().put(TAG, soul);
    }

    public static void clear(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove(TAG);
        if (tag.isEmpty()) stack.setTag(null);
    }

    public static Optional<EntityType<?>> entityType(ItemStack stack) {
        if (!hasSoul(stack)) return Optional.empty();
        ResourceLocation id = ResourceLocation.tryParse(stack.getTag().getCompound(TAG).getString("Entity"));
        if (id == null || !ForgeRegistries.ENTITY_TYPES.containsKey(id)) return Optional.empty();
        return Optional.ofNullable(ForgeRegistries.ENTITY_TYPES.getValue(id));
    }

    public static boolean isPlayerSoul(ItemStack stack) {
        return hasSoul(stack) && stack.getTag().getCompound(TAG).hasUUID("PlayerId");
    }

    @Nullable
    public static UUID playerId(ItemStack stack) {
        return isPlayerSoul(stack) ? stack.getTag().getCompound(TAG).getUUID("PlayerId") : null;
    }

    /** Nombre para mostrar: el del jugador o el del tipo de entidad. */
    public static Component displayName(ItemStack stack) {
        if (isPlayerSoul(stack)) return Component.literal(stack.getTag().getCompound(TAG).getString("PlayerName"));
        return entityType(stack).map(EntityType::getDescription).orElse(Component.literal("?"));
    }
}
