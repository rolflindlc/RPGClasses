package io.github.ironrituals.ritual.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Catalizador opcional (plan, 1): componentes extra que activan un sub-efecto.
 *
 * @param id       identificador que lee el efecto (p. ej. {@code freeze_clock})
 * @param group    catalizadores del mismo grupo son excluyentes (solo cuenta el primero que encaje); "" = sin grupo
 * @param minLevel nivel mínimo del ritual para que cuente
 */
public record RitualCatalyst(String id, String group, int minLevel, List<RitualComponent> components) {

    public static RitualCatalyst fromJson(JsonObject json) {
        List<RitualComponent> comps = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "components")) comps.add(RitualComponent.fromJson(e.getAsJsonObject()));
        return new RitualCatalyst(GsonHelper.getAsString(json, "id"), GsonHelper.getAsString(json, "group", ""),
                GsonHelper.getAsInt(json, "min_level", 1), List.copyOf(comps));
    }

    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(group);
        buf.writeVarInt(minLevel);
        buf.writeCollection(components, (b, c) -> c.toNetwork(b));
    }

    public static RitualCatalyst fromNetwork(FriendlyByteBuf buf) {
        return new RitualCatalyst(buf.readUtf(), buf.readUtf(), buf.readVarInt(), List.copyOf(buf.readList(RitualComponent::fromNetwork)));
    }
}
