package io.github.ironrituals.ritual.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Un nivel de un ritual: componentes, maná y parámetros del efecto (radio, duración...).
 *
 * @param channelSeconds  canalización propia en segundos, o -1 para usar la fórmula común
 * @param durationSeconds duración base del efecto (0 = instantáneo o sin duración)
 * @param params          parámetros libres que lee el efecto (p. ej. {@code radius})
 */
public record RitualTier(int level, float mana, int channelSeconds, int durationSeconds,
                         List<RitualComponent> components, CompoundTag params) {

    public static RitualTier fromJson(JsonObject json) {
        List<RitualComponent> comps = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "components")) comps.add(RitualComponent.fromJson(e.getAsJsonObject()));
        CompoundTag params = new CompoundTag();
        if (json.has("params")) {
            Tag t = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, json.get("params"));
            if (t instanceof CompoundTag ct) params = ct;
        }
        int level = GsonHelper.getAsInt(json, "level", 1);
        if (level < 1 || level > 3) throw new IllegalArgumentException("level debe estar entre 1 y 3");
        return new RitualTier(level, GsonHelper.getAsFloat(json, "mana", 0),
                GsonHelper.getAsInt(json, "channel_seconds", -1), GsonHelper.getAsInt(json, "duration_seconds", 0),
                List.copyOf(comps), params);
    }

    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeVarInt(level);
        buf.writeFloat(mana);
        buf.writeInt(channelSeconds);
        buf.writeVarInt(durationSeconds);
        buf.writeCollection(components, (b, c) -> c.toNetwork(b));
        buf.writeNbt(params);
    }

    public static RitualTier fromNetwork(FriendlyByteBuf buf) {
        int level = buf.readVarInt();
        float mana = buf.readFloat();
        int channel = buf.readInt();
        int duration = buf.readVarInt();
        List<RitualComponent> comps = buf.readList(RitualComponent::fromNetwork);
        CompoundTag params = buf.readNbt();
        return new RitualTier(level, mana, channel, duration, List.copyOf(comps), params == null ? new CompoundTag() : params);
    }
}
