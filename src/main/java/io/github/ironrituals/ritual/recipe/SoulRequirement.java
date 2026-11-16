package io.github.ironrituals.ritual.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.ironrituals.item.RitualDaggerItem;
import io.github.ironrituals.item.SoulData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Alma que debe llevar la Daga Ritual para servir de componente. JSON:
 * {@code {"any": true}}, {@code {"player": true}}, {@code {"tamable": true}} o {@code {"entities": ["minecraft:villager"]}}
 * (combinables: basta con que se cumpla una opción).
 */
public record SoulRequirement(boolean any, boolean player, boolean tamable, List<ResourceLocation> entities) {

    public boolean test(ItemStack stack, @Nullable Level level) {
        if (!(stack.getItem() instanceof RitualDaggerItem) || !SoulData.hasSoul(stack)) return false;
        if (any) return true;
        if (player && SoulData.isPlayerSoul(stack)) return true;
        if (SoulData.isPlayerSoul(stack)) return false;
        EntityType<?> type = SoulData.entityType(stack).orElse(null);
        if (type == null) return false;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (entities.contains(id)) return true;
        if (tamable && level != null) {
            Entity probe = type.create(level);
            boolean ok = probe instanceof OwnableEntity || probe instanceof AbstractHorse;
            if (probe != null) probe.discard();
            return ok;
        }
        return false;
    }

    /** Texto para la GUI del grimorio y JEI. */
    public Component describe() {
        if (any) return Component.translatable("soul.ironrituals.any");
        List<Component> parts = new ArrayList<>();
        for (ResourceLocation id : entities) {
            EntityType<?> t = ForgeRegistries.ENTITY_TYPES.getValue(id);
            parts.add(t == null ? Component.literal(id.toString()) : t.getDescription());
        }
        if (player) parts.add(Component.translatable("soul.ironrituals.player"));
        if (tamable) parts.add(Component.translatable("soul.ironrituals.tamable"));
        MutableComponent joined = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) joined.append(Component.translatable("soul.ironrituals.or"));
            joined.append(parts.get(i));
        }
        return Component.translatable("soul.ironrituals.of", joined);
    }

    public static SoulRequirement fromJson(JsonObject json) {
        List<ResourceLocation> ids = new ArrayList<>();
        if (json.has("entities")) {
            JsonArray arr = GsonHelper.getAsJsonArray(json, "entities");
            for (JsonElement e : arr) {
                ResourceLocation id = ResourceLocation.tryParse(e.getAsString());
                if (id == null) throw new IllegalArgumentException("id de entidad inválido: " + e.getAsString());
                ids.add(id);
            }
        }
        return new SoulRequirement(GsonHelper.getAsBoolean(json, "any", false), GsonHelper.getAsBoolean(json, "player", false),
                GsonHelper.getAsBoolean(json, "tamable", false), List.copyOf(ids));
    }

    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeBoolean(any);
        buf.writeBoolean(player);
        buf.writeBoolean(tamable);
        buf.writeCollection(entities, FriendlyByteBuf::writeResourceLocation);
    }

    public static SoulRequirement fromNetwork(FriendlyByteBuf buf) {
        return new SoulRequirement(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                List.copyOf(buf.readList(FriendlyByteBuf::readResourceLocation)));
    }
}
