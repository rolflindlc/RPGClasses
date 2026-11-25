package io.github.rpgclasses.rogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.properties.Property;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.server.AbilityService;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Rostros del Cambiarrostros: qué se puede copiar, a qué facción engaña cada rostro y cómo se guardan.
 *
 * <p>Un rostro es un {@link CompoundTag}: {@code kind} ("mob" o "player"), {@code key} y, según el tipo, {@code type}
 * (id del mob) o {@code uuid}, {@code name}, {@code tex}/{@code sig} (skin) y {@code scale} (tamaño racial, solo visual).
 * Se guardan en {@link PlayerClassData#faces}: {@code known} (clave -> rostro) y {@code progress} (clave -> ticks).
 * Los mobs se aprenden por tipo y los jugadores por jugador concreto.
 */
public final class Faces {
    public static final String MOB = "mob", PLAYER = "player";

    /** Facción definida en {@code abilities/disguise.json}: rostros que la representan y mobs a los que engaña. */
    public record Faction(String id, TagKey<EntityType<?>> faces, TagKey<EntityType<?>> members, boolean allUndead) {}

    private static JsonObject cachedParams;
    private static List<Faction> cachedFactions = List.of();

    private Faces() {}

    public static List<Faction> factions() {
        JsonObject params = AbilityService.params(FaceChanger.DISGUISE);
        if (params != cachedParams) {
            cachedParams = params;
            List<Faction> out = new ArrayList<>();
            if (params.has("factions") && params.get("factions").isJsonArray()) {
                for (JsonElement el : params.getAsJsonArray("factions")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject f = el.getAsJsonObject();
                    ResourceLocation faces = ResourceLocation.tryParse(str(f, "faces"));
                    ResourceLocation members = ResourceLocation.tryParse(str(f, "members"));
                    if (faces == null || members == null) continue;
                    out.add(new Faction(str(f, "id"), TagKey.create(Registries.ENTITY_TYPE, faces),
                            TagKey.create(Registries.ENTITY_TYPE, members), f.has("all_undead") && f.get("all_undead").getAsBoolean()));
                }
            }
            cachedFactions = List.copyOf(out);
        }
        return cachedFactions;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    /** Facción a la que pertenece el rostro de un mob de este tipo (null: no se puede copiar). */
    public static Faction factionOfFace(EntityType<?> type) {
        for (Faction f : factions()) if (type.is(f.faces())) return f;
        return null;
    }

    /** Facción del rostro guardado (null para rostros de jugador o de mobs que ya no están en ninguna). */
    public static Faction factionOf(CompoundTag face) {
        if (!MOB.equals(face.getString("kind"))) return null;
        EntityType<?> type = EntityType.byString(face.getString("type")).orElse(null);
        return type == null ? null : factionOfFace(type);
    }

    /** ¿Engaña la facción a este mob? Los jefes nunca se dejan engañar. */
    public static boolean isMember(Faction faction, LivingEntity mob) {
        EntityType<?> type = mob.getType();
        if (type.is(net.minecraftforge.common.Tags.EntityTypes.BOSSES)) return false;
        return type.is(faction.members()) || faction.allUndead() && mob.getMobType() == MobType.UNDEAD;
    }

    /** ¿Se puede estudiar a esta entidad? Jugadores (no uno mismo) y los mobs de los rostros de alguna facción. */
    public static boolean copyable(Entity e, ServerPlayer viewer) {
        if (e == viewer || !(e instanceof LivingEntity) || !e.isAlive()) return false;
        if (e instanceof Player p) return !p.isSpectator() && !(p instanceof net.minecraftforge.common.util.FakePlayer);
        return factionOfFace(e.getType()) != null;
    }

    public static String key(Entity e) {
        return e instanceof Player ? PLAYER + ":" + e.getUUID() : MOB + ":" + EntityType.getKey(e.getType());
    }

    /** Rostro de la entidad tal como es ahora (skin y tamaño del jugador incluidos). */
    public static CompoundTag faceOf(Entity e) {
        CompoundTag face = new CompoundTag();
        face.putString("key", key(e));
        if (e instanceof Player p) {
            face.putString("kind", PLAYER);
            face.putUUID("uuid", p.getUUID());
            face.putString("name", p.getGameProfile().getName());
            for (Property prop : p.getGameProfile().getProperties().get("textures")) {
                face.putString("tex", prop.getValue());
                if (prop.getSignature() != null) face.putString("sig", prop.getSignature());
                break;
            }
            face.putFloat("scale", io.github.rpgclasses.content.race.RaceTraits.scale(p));
        } else {
            face.putString("kind", MOB);
            face.putString("type", EntityType.getKey(e.getType()).toString());
        }
        return face;
    }

    /** Nombre que se ve del rostro (el del jugador copiado o el del tipo de mob). */
    public static Component name(CompoundTag face) {
        if (PLAYER.equals(face.getString("kind"))) return Component.literal(face.getString("name"));
        return EntityType.byString(face.getString("type")).map(EntityType::getDescription)
                .orElse(Component.literal(face.getString("type")));
    }

    // ------------------------------------------------------------------ guardado

    public static CompoundTag known(PlayerClassData data) {
        if (!data.faces.contains("known", Tag.TAG_COMPOUND)) data.faces.put("known", new CompoundTag());
        return data.faces.getCompound("known");
    }

    public static CompoundTag progress(PlayerClassData data) {
        if (!data.faces.contains("progress", Tag.TAG_COMPOUND)) data.faces.put("progress", new CompoundTag());
        return data.faces.getCompound("progress");
    }

    /** Rostros aprendidos, en el orden en que se guardaron. Los de jugador con este mismo jugador no cuentan. */
    public static List<CompoundTag> list(PlayerClassData data, UUID self) {
        List<CompoundTag> out = new ArrayList<>();
        CompoundTag known = known(data);
        for (String key : known.getAllKeys()) {
            CompoundTag face = known.getCompound(key);
            if (PLAYER.equals(face.getString("kind")) && face.hasUUID("uuid") && face.getUUID("uuid").equals(self)) continue;
            if (MOB.equals(face.getString("kind")) && EntityType.byString(face.getString("type")).isEmpty()) continue;
            out.add(face);
        }
        out.sort((a, b) -> {
            int kind = a.getString("kind").compareTo(b.getString("kind"));
            return kind != 0 ? -kind : a.getString("key").compareTo(b.getString("key")); // jugadores primero
        });
        return out;
    }
}
