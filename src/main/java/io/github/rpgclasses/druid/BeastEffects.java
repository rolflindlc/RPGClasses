package io.github.rpgclasses.druid;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.rpgclasses.server.Allies;
import io.github.rpgclasses.server.DebugLog;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Estados de la forma: si la bestia pone un estado al golpear (veneno de la araña de cueva o de la abeja) o a quien
 * tiene cerca, la forma del Druida también.
 * <ul>
 *   <li><b>Tabla:</b> {@code druid_beasts.json}, por especie, {@code "hit_effects"} y {@code "aura_effects"}: listas de
 *       {@code {"effect": id, "seconds": s, "level": n, "radius": r}} (radius solo en las de cercanía). Si la fila tiene
 *       la clave (aunque esté vacía), manda: no se aprende nada para esa especie y ese tipo.</li>
 *   <li><b>Aprendizaje:</b> para lo que la tabla no cubre (mobs de otros mods), el servidor mira qué estados ponen los
 *       mobs válidos para el Druida a otras criaturas. Si la víctima recibió un golpe de ese mob en el mismo tic, es un
 *       estado al golpear; si no, de cercanía (con la distancia a la que lo puso como radio). Se guarda en el mundo
 *       ({@link Learned}), quedándose con el nivel, la duración y el radio más altos vistos.</li>
 *   <li><b>Fuerza:</b> el mismo estado y nivel que el mob; la duración sube con el nivel total del Druida
 *       ({@code level_scaling} de la Forma Salvaje, +2 % por nivel).</li>
 *   <li><b>Quimeras:</b> los estados al golpear son los de la cabeza; los de cercanía, los del instinto.</li>
 * </ul>
 */
public final class BeastEffects {
    public static final String HIT = "hit_effects", AURA = "aura_effects";
    /** Tope de duración que se aprende (los mobs de mods a veces ponen estados de minutos). */
    private static final int MAX_LEARNED_TICKS = 20 * 30;
    /** Cada cuánto se aplican los de cercanía y cuánto le tiene que quedar al estado para no renovarlo. */
    private static final int AURA_INTERVAL = 10, AURA_REFRESH_BELOW = 40;

    public record Eff(MobEffect effect, int amplifier, int ticks, double radius) {}

    // ------------------------------------------------------------------ lectura

    /** Estados de la especie de ese tipo (tabla, o lo aprendido si la tabla no lo fija). */
    public static List<Eff> of(MinecraftServer server, String species, String kind) {
        if (species == null) return List.of();
        JsonObject row = Beasts.row(species, false);
        if (row.has(kind) && row.get(kind).isJsonArray()) return parse(row.getAsJsonArray(kind));
        return server == null ? List.of() : Learned.get(server).list(species, kind);
    }

    private static List<Eff> parse(JsonArray array) {
        List<Eff> out = new ArrayList<>();
        for (JsonElement e : array) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            ResourceLocation id = o.has("effect") ? ResourceLocation.tryParse(o.get("effect").getAsString()) : null;
            MobEffect effect = id == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(id);
            if (effect == null) continue;
            int level = o.has("level") ? o.get("level").getAsInt() : 1;
            double seconds = o.has("seconds") ? o.get("seconds").getAsDouble() : 5;
            double radius = o.has("radius") ? o.get("radius").getAsDouble() : 2;
            out.add(new Eff(effect, Math.max(0, level - 1), (int) Math.round(seconds * 20), radius));
        }
        return out;
    }

    /** Especies de las que salen los estados de la forma en curso (quimera: cabeza para golpear, instinto para cerca). */
    private static String species(WildShape.Form form, String kind) {
        if (form.spec.isChimera()) return HIT.equals(kind) ? form.spec.chimera.head() : form.spec.chimera.instinct();
        return form.spec.type;
    }

    private static int scaledTicks(ServerPlayer p, int ticks) {
        double bonus = Druid.totalLevel(p) * Druid.number(Druid.WILD_SHAPE, "level_scaling", .02);
        return (int) Math.max(1, Math.round(ticks * (1 + bonus)));
    }

    // ------------------------------------------------------------------ aplicación

    /** Tras un golpe de bestia que entra. */
    static void onBeastHit(ServerPlayer p, LivingEntity victim) {
        WildShape.Form form = WildShape.form(p);
        if (form == null) return;
        for (Eff e : of(p.getServer(), species(form, HIT), HIT)) {
            victim.addEffect(new MobEffectInstance(e.effect(), scaledTicks(p, e.ticks()), e.amplifier()), p);
        }
    }

    /** Cada tic de la forma (lo llama WildShape): estados de cercanía a quien no sea aliado. */
    static void tickAura(ServerPlayer p, WildShape.Form form) {
        if (p.tickCount % AURA_INTERVAL != 0 || p.isSpectator()) return;
        List<Eff> aura = of(p.getServer(), species(form, AURA), AURA);
        if (aura.isEmpty()) return;
        double max = 0;
        for (Eff e : aura) max = Math.max(max, e.radius());
        for (LivingEntity target : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(max),
                t -> t != p && t.isAlive() && !(t instanceof Player pl && pl.isSpectator()) && !Allies.isAlly(p, t))) {
            double dist = target.distanceTo(p);
            for (Eff e : aura) {
                if (dist > e.radius()) continue;
                MobEffectInstance current = target.getEffect(e.effect());
                if (current != null && current.getAmplifier() >= e.amplifier() && current.getDuration() > AURA_REFRESH_BELOW) continue;
                target.addEffect(new MobEffectInstance(e.effect(), scaledTicks(p, e.ticks()), e.amplifier()), p);
            }
        }
    }

    // ------------------------------------------------------------------ aprendizaje

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEffectAdded(MobEffectEvent.Added event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || target.getServer() == null) return;
        MobEffectInstance inst = event.getEffectInstance();
        if (inst == null || inst.isInfiniteDuration() || inst.getDuration() <= 0) return;
        boolean hurtNow = target.getLastHurtByMob() != null && target.getLastHurtByMobTimestamp() == target.tickCount;
        Entity source = event.getEffectSource();
        if (!(source instanceof LivingEntity)) source = hurtNow ? target.getLastHurtByMob() : null;
        if (!(source instanceof LivingEntity mob) || mob == target || mob instanceof Player || !Beasts.eligible(mob)) return;
        String species = Beasts.id(mob.getType());
        String kind = hurtNow && target.getLastHurtByMob() == mob ? HIT : AURA;
        if (Beasts.row(species, false).has(kind)) return; // la tabla manda
        double radius = Math.ceil(mob.distanceTo(target) * 2) / 2.0;
        if (kind.equals(AURA) && radius > 8) return; // demasiado lejos para ser "estar cerca": será un proyectil u otra cosa
        Eff eff = new Eff(inst.getEffect(), inst.getAmplifier(), Math.min(MAX_LEARNED_TICKS, inst.getDuration()), Math.max(1.5, radius));
        if (Learned.get(target.getServer()).learn(species, kind, eff)) {
            DebugLog.event(null, "Druida", "aprende que {} pone {} ({}, nivel {}, {} tics, radio {})", species,
                    ForgeRegistries.MOB_EFFECTS.getKey(eff.effect()), kind, eff.amplifier() + 1, eff.ticks(), eff.radius());
        }
    }

    /** Estados aprendidos, guardados en el mundo (supervivencia del servidor). */
    public static final class Learned extends SavedData {
        private static final String NAME = "rites_and_paths_druid_beast_effects";
        /** especie → tipo → id de efecto → estado. */
        private final Map<String, Map<String, Map<ResourceLocation, Eff>>> data = new HashMap<>();

        public static Learned get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Learned::load, Learned::new, NAME);
        }

        List<Eff> list(String species, String kind) {
            Map<String, Map<ResourceLocation, Eff>> bySpecies = data.get(species);
            Map<ResourceLocation, Eff> byKind = bySpecies == null ? null : bySpecies.get(kind);
            return byKind == null ? List.of() : List.copyOf(byKind.values());
        }

        /** Guarda el estado (o mejora el que había). true si cambió algo. */
        boolean learn(String species, String kind, Eff e) {
            ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(e.effect());
            if (id == null) return false;
            Map<ResourceLocation, Eff> byKind = data.computeIfAbsent(species, k -> new HashMap<>()).computeIfAbsent(kind, k -> new HashMap<>());
            Eff old = byKind.get(id);
            Eff merged = old == null ? e : new Eff(e.effect(), Math.max(old.amplifier(), e.amplifier()),
                    Math.max(old.ticks(), e.ticks()), Math.max(old.radius(), e.radius()));
            if (merged.equals(old)) return false;
            byKind.put(id, merged);
            setDirty();
            return true;
        }

        static Learned load(CompoundTag tag) {
            Learned out = new Learned();
            ListTag list = tag.getList("effects", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag t = list.getCompound(i);
                ResourceLocation id = ResourceLocation.tryParse(t.getString("effect"));
                MobEffect effect = id == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(id);
                if (effect == null) continue;
                out.data.computeIfAbsent(t.getString("species"), k -> new HashMap<>()).computeIfAbsent(t.getString("kind"), k -> new HashMap<>())
                        .put(id, new Eff(effect, t.getInt("amplifier"), t.getInt("ticks"), t.getDouble("radius")));
            }
            return out;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            data.forEach((species, kinds) -> kinds.forEach((kind, effects) -> effects.forEach((id, e) -> {
                CompoundTag t = new CompoundTag();
                t.putString("species", species);
                t.putString("kind", kind);
                t.putString("effect", id.toString());
                t.putInt("amplifier", e.amplifier());
                t.putInt("ticks", e.ticks());
                t.putDouble("radius", e.radius());
                list.add(t);
            })));
            tag.put("effects", list);
            return tag;
        }
    }
}
