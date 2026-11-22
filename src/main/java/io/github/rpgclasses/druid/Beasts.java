package io.github.rpgclasses.druid;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.rpgclasses.RpgClasses;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Qué mobs son "animales y bestias" para el Druida y los números de cada forma. Lo que no está en la tabla del
 * datapack ({@code abilities/druid_beasts.json}) sale de los atributos del mob.
 * <ul>
 *   <li>Valen solos los de categoría de animal (CREATURE, AMBIENT, WATER_*, AXOLOTLS), más los del tag
 *       {@code druid/allowed}; nunca los del tag {@code druid/forbidden}, los jugadores ni los que tienen más vida que
 *       {@code max_health} de beast_lore (salvo los permitidos a mano).</li>
 *   <li>Poder = vida + {@code damage_weight} × daño. Puntos de conocimiento = 1 + poder / {@code power_per_point} (de 1
 *       a {@code max_points}). Maná = {@code mana_base} + {@code mana_per_power} × poder, más caro si vuela o es acuática.</li>
 * </ul>
 * Funciona en los dos lados (el cliente lo usa para la pantalla del bestiario).
 */
public final class Beasts {
    public static final TagKey<EntityType<?>> ALLOWED = TagKey.create(Registries.ENTITY_TYPE, RpgClasses.id("druid/allowed"));
    public static final TagKey<EntityType<?>> FORBIDDEN = TagKey.create(Registries.ENTITY_TYPE, RpgClasses.id("druid/forbidden"));
    private static final Set<MobCategory> ANIMALS = Set.of(MobCategory.CREATURE, MobCategory.AMBIENT, MobCategory.WATER_CREATURE,
            MobCategory.WATER_AMBIENT, MobCategory.UNDERGROUND_WATER_CREATURE, MobCategory.AXOLOTLS);
    private static final Set<MobCategory> WATER = Set.of(MobCategory.WATER_CREATURE, MobCategory.WATER_AMBIENT,
            MobCategory.UNDERGROUND_WATER_CREATURE);

    /**
     * Números de una forma (sin las mejoras del jugador). {@code power}: lo fuerte que es (decide puntos y maná).
     * {@code points}: conocimiento que pide. {@code mana}: maná base antes de ramas y perks.
     */
    public record Info(String id, EntityType<?> type, double health, double damage, double armor, double speed,
                       double attackSpeed, double jump, double knockbackResistance, boolean flying, boolean aquatic,
                       boolean amphibious, String ability, List<String> passives, double power, int points, double mana) {
        public boolean breathesWater() {
            return aquatic || amphibious;
        }

        public boolean hasPassive(String p) {
            return passives.contains(p);
        }
    }

    private static final Map<String, Info> SERVER_CACHE = new HashMap<>();
    private static final Map<String, Info> CLIENT_CACHE = new HashMap<>();
    private static final Map<String, Float> EYE_RATIO = new HashMap<>();
    private static Object serverTable, clientTable;

    private Beasts() {}

    public static EntityType<?> type(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !ForgeRegistries.ENTITY_TYPES.containsKey(rl)) return null;
        return ForgeRegistries.ENTITY_TYPES.getValue(rl);
    }

    public static String id(EntityType<?> type) {
        ResourceLocation rl = ForgeRegistries.ENTITY_TYPES.getKey(type);
        return rl == null ? "" : rl.toString();
    }

    public static Component name(String id) {
        EntityType<?> type = type(id);
        return type == null ? Component.translatable("druid.rites_and_paths.unknown_species") : type.getDescription();
    }

    // ------------------------------------------------------------------ ¿vale?

    /** ¿Es un animal o una bestia en la que el Druida puede aprender a transformarse? */
    public static boolean eligible(EntityType<?> type, boolean client) {
        if (type == null || type == EntityType.PLAYER || !DefaultAttributes.hasSupplier(type)) return false;
        if (type.is(FORBIDDEN)) return false;
        boolean allowed = type.is(ALLOWED);
        if (!allowed && !ANIMALS.contains(type.getCategory())) return false;
        if (allowed) return true;
        Info info = info(type, client);
        return info != null && info.health() <= Druid.number(Druid.BEAST_LORE, "max_health", 100, client);
    }

    public static boolean eligible(Entity e) {
        return e instanceof LivingEntity && !(e instanceof Player) && eligible(e.getType(), e.level().isClientSide);
    }

    // ------------------------------------------------------------------ números

    private static JsonObject table(boolean client) {
        JsonObject params = Druid.params(Druid.BEASTS, client);
        // La tabla cambia con /reload: si el objeto es otro, se vacía la caché.
        if (client) {
            if (clientTable != params) {
                clientTable = params;
                CLIENT_CACHE.clear();
            }
        } else if (serverTable != params) {
            serverTable = params;
            SERVER_CACHE.clear();
        }
        return params;
    }

    /** Parámetros de una habilidad especial de bestia (vacío si no existe). */
    public static JsonObject abilityParams(String ability, boolean client) {
        JsonObject all = table(client).has("abilities") ? table(client).getAsJsonObject("abilities") : new JsonObject();
        return ability != null && all.has(ability) && all.get(ability).isJsonObject() ? all.getAsJsonObject(ability) : new JsonObject();
    }

    public static Info info(String id, boolean client) {
        return info(type(id), client);
    }

    public static Info info(EntityType<?> type, boolean client) {
        if (type == null || !DefaultAttributes.hasSupplier(type)) return null;
        JsonObject table = table(client);
        Map<String, Info> cache = client ? CLIENT_CACHE : SERVER_CACHE;
        String id = id(type);
        Info cached = cache.get(id);
        if (cached != null) return cached;
        JsonObject beasts = table.has("beasts") && table.get("beasts").isJsonObject() ? table.getAsJsonObject("beasts") : new JsonObject();
        JsonObject row = beasts.has(id) && beasts.get(id).isJsonObject() ? beasts.getAsJsonObject(id) : new JsonObject();
        @SuppressWarnings("unchecked")
        AttributeSupplier attrs = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
        MobCategory cat = type.getCategory();

        double health = num(row, "health", base(attrs, Attributes.MAX_HEALTH, 10));
        double damage = num(row, "damage", base(attrs, Attributes.ATTACK_DAMAGE, 1));
        double armor = num(row, "armor", base(attrs, Attributes.ARMOR, 0));
        boolean flying = bool(row, "flying", cat == MobCategory.AMBIENT || attrs.hasAttribute(Attributes.FLYING_SPEED));
        boolean aquatic = bool(row, "aquatic", WATER.contains(cat));
        boolean amphibious = bool(row, "amphibious", cat == MobCategory.AXOLOTLS);
        double minSpeed = Druid.number(Druid.WILD_SHAPE, "form_min_speed", .5, client);
        double maxSpeed = Druid.number(Druid.WILD_SHAPE, "form_max_speed", 1.6, client);
        double walk = base(attrs, Attributes.MOVEMENT_SPEED, .25);
        // Los mobs se mueven con la velocidad al cuadrado (entrada × velocidad); el jugador, con la velocidad tal cual.
        double speed = num(row, "speed", flying || aquatic ? 1.0 : Math.max(minSpeed, Math.min(maxSpeed, walk * walk * 16)));
        double attackSpeed = num(row, "attack_speed", Druid.number(Druid.WILD_SHAPE, "form_attack_speed", 1.6, client));
        double jump = num(row, "jump", 0);
        double kb = num(row, "knockback_resistance", base(attrs, Attributes.KNOCKBACK_RESISTANCE, 0));
        String ability = row.has("ability") ? row.get("ability").getAsString() : null;
        List<String> passives = new ArrayList<>();
        if (row.has("passives") && row.get("passives").isJsonArray()) {
            for (JsonElement e : row.getAsJsonArray("passives")) passives.add(e.getAsString());
        }
        double power = health + Druid.number(Druid.WILD_SHAPE, "damage_weight", 3, client) * damage;
        int maxPoints = (int) Druid.number(Druid.BEAST_LORE, "max_points", 6, client);
        int points = (int) num(row, "points", Math.max(1, Math.min(maxPoints,
                1 + Math.floor(power / Math.max(1, Druid.number(Druid.BEAST_LORE, "power_per_point", 20, client))))));
        double mana = row.has("mana") ? row.get("mana").getAsDouble() : manaFor(power, flying, aquatic && !amphibious, client);
        Info info = new Info(id, type, Math.max(1, health), Math.max(0, damage), Math.max(0, armor), speed, Math.max(.2, attackSpeed),
                jump, Math.max(0, Math.min(1, kb)), flying, aquatic, amphibious, ability, List.copyOf(passives), power, points, mana);
        cache.put(id, info);
        return info;
    }

    private static double manaFor(double power, boolean flying, boolean aquatic, boolean client) {
        double mana = Druid.number(Druid.WILD_SHAPE, "mana_base", 15, client) + Druid.number(Druid.WILD_SHAPE, "mana_per_power", 1.5, client) * power;
        if (flying) mana *= 1 + Druid.number(Druid.WILD_SHAPE, "flying_cost", .25, client);
        if (aquatic) mana *= 1 + Druid.number(Druid.WILD_SHAPE, "aquatic_cost", .25, client);
        return mana;
    }

    /**
     * Quimera (adenda, 6.3): vida, armadura y tamaño del torso; daño de la cabeza; movimiento (velocidad, salto, vuelo,
     * agua) de las patas; habilidad especial del instinto. Rasgos: los de las patas y los del instinto. Su poder es la
     * suma del de las cuatro partes (propuesta, [MEDIR]).
     */
    public static Info chimera(FormSpec.Chimera c, boolean client) {
        Info head = info(c.head(), client), torso = info(c.torso(), client), legs = info(c.legs(), client), inst = info(c.instinct(), client);
        if (head == null || torso == null || legs == null || inst == null) return null;
        Set<String> passives = new LinkedHashSet<>(legs.passives());
        passives.addAll(inst.passives());
        double power = head.power() + torso.power() + legs.power() + inst.power();
        double mana = manaFor(power, legs.flying(), legs.aquatic() && !legs.amphibious(), client);
        return new Info(torso.id(), torso.type(), torso.health(), head.damage(), torso.armor(), legs.speed(), legs.attackSpeed(),
                legs.jump(), torso.knockbackResistance(), legs.flying(), legs.aquatic(), legs.amphibious(), inst.ability(),
                List.copyOf(passives), power, 0, mana);
    }

    public static Info info(FormSpec spec, boolean client) {
        if (spec == null) return null;
        return spec.isChimera() ? chimera(spec.chimera, client) : info(spec.type, client);
    }

    private static double base(AttributeSupplier attrs, Attribute attribute, double fallback) {
        return attrs.hasAttribute(attribute) ? attrs.getBaseValue(attribute) : fallback;
    }

    private static double num(JsonObject row, String key, double fallback) {
        return row.has(key) && row.get(key).isJsonPrimitive() ? row.get(key).getAsDouble() : fallback;
    }

    private static boolean bool(JsonObject row, String key, boolean fallback) {
        return row.has(key) && row.get(key).isJsonPrimitive() ? row.get(key).getAsBoolean() : fallback;
    }

    /** Lista del JSON (para los rasgos de las bestias de mods sin entrada). */
    static List<String> strings(JsonArray a) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : a) out.add(e.getAsString());
        return out;
    }

    // ------------------------------------------------------------------ cuerpo

    /**
     * Altura de los ojos de la especie respecto a su altura (se mide creando una entidad sin añadirla al mundo, una
     * vez por especie). 0,85 si no se puede.
     */
    public static float eyeRatio(EntityType<?> type, Level level) {
        String id = id(type);
        Float r = EYE_RATIO.get(id);
        if (r != null) return r;
        float ratio = .85f;
        try {
            Entity e = type.create(level);
            if (e != null && e.getBbHeight() > 0) ratio = Math.max(.2f, Math.min(1f, e.getEyeHeight() / e.getBbHeight()));
        } catch (RuntimeException | LinkageError ex) {
            RpgClasses.LOGGER.debug("[rites_and_paths] No se pudo medir la altura de ojos de {}", id, ex);
        }
        EYE_RATIO.put(id, ratio);
        return ratio;
    }

    // ------------------------------------------------------------------ con las mejoras del jugador

    /** Nivel Total del jugador × {@code level_scaling} (+2 % por nivel: +80 % en el 40). */
    private static double levelBonus(Player p) {
        return Druid.totalLevel(p) * Druid.number(Druid.WILD_SHAPE, "level_scaling", .02, p.level().isClientSide);
    }

    /** Multiplicador de la vida de las formas. */
    public static double healthFactor(Player p) {
        double f = 1 + levelBonus(p)
                + Druid.mech(p, Druid.FEROCITY, "form_health", 0)
                + Druid.mech(p, Druid.HUNTER, "form_health", 0) + Druid.mech(p, Druid.COLOSSUS, "form_health", 0)
                + Druid.mech(p, Druid.BODY_SAP, "form_health", 0)
                + Druid.mech(p, Druid.RICH_FORM, "stats", 0) + Druid.mech(p, Druid.SUPERNATURAL, "stats", 0);
        return Math.max(.1, f);
    }

    /** Multiplicador del daño de las formas. */
    public static double damageFactor(Player p) {
        double f = 1 + levelBonus(p)
                + Druid.mech(p, Druid.FEROCITY, "form_damage", 0)
                + Druid.mech(p, Druid.HUNTER, "form_damage", 0) + Druid.mech(p, Druid.COLOSSUS, "form_damage", 0)
                + Druid.mech(p, Druid.RICH_FORM, "stats", 0) + Druid.mech(p, Druid.SUPERNATURAL, "stats", 0);
        return Math.max(.1, f);
    }

    /** Maná que cuesta transformarse (y que se reserva mientras dura la forma, salvo en el Círculo de la Luna). */
    public static int manaCost(Player p, Info info) {
        double cost = info.mana() * (1 + Druid.mech(p, Druid.WILD_RESERVE, "cost", 0))
                * (1 + Druid.mech(p, Druid.CHEAP_FORM, "cost", 0)) * (1 + Druid.mech(p, Druid.RICH_FORM, "cost", 0));
        return (int) Math.max(1, Math.round(cost));
    }

    /** Puntos de conocimiento que pide una especie a este jugador (Saber lleno: uno menos; mínimo 1). */
    public static int requiredPoints(Player p, Info info) {
        return Math.max(1, info.points() - (int) Math.round(Druid.mech(p, Druid.LORE, "points_reduction", 0)));
    }

    /** ¿Conoce bien la especie (puede transformarse en ella)? */
    public static boolean known(Player p, String species) {
        Info info = info(species, p.level().isClientSide);
        return info != null && Bestiary.points(Druid.data(p), species) >= requiredPoints(p, info);
    }
}
