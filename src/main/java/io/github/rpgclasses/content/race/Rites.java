package io.github.rpgclasses.content.race;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.definition.Definitions.ObjectiveDef;
import io.github.rpgclasses.core.definition.Definitions.RaceDef;
import io.github.rpgclasses.core.definition.Definitions.RiteDef;
import io.github.rpgclasses.core.definition.Definitions.SubraceDef;
import io.github.rpgclasses.core.progression.ProgressionRules;
import io.github.rpgclasses.core.rite.RiteProgress;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.server.Allies;
import io.github.rpgclasses.server.DebugLog;
import io.github.rpgclasses.server.StatEngine;
import io.github.rpgclasses.server.WeaponClassifier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ritos de raza (anexo de razas, regla 5). Cada subraza tiene 3 ritos (en {@code races/*.json}); el rito en curso
 * es el que lleva al rango siguiente. Hazañas (derrotas, golpes, robos, trueques) cuentan solas; las ofrendas y
 * construcciones se entregan en el {@link RaceAltarBlock Altar de Raza} (clic derecho), y los jefes de rito se
 * invocan en él (agachado + clic derecho) y solo cuentan para quien los invoca. Al completar todos los objetivos
 * sube el rango.
 */
public final class Rites {
    public static final String CRAFTER = "rites_and_paths.crafter";
    public static final String ENCHANTER = "rites_and_paths.enchanter";
    public static final String FISHED = "rites_and_paths.fished";
    public static final String MOB_EQUIP = "rites_and_paths.mob_equip";
    public static final String RITE_DROP = "rites_and_paths.rite_drop";
    private static final String BOSS_OWNER = "rites_and_paths.rite_owner";
    private static final String BOSS_RITE = "rites_and_paths.rite_id";

    private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, RpgClasses.id("rite_bosses"));
    private static final TagKey<EntityType<?>> BOSSES_FALLBACK = TagKey.create(Registries.ENTITY_TYPE, RpgClasses.id("rite_bosses_fallback"));
    private static final UUID FALLBACK_HEALTH = UUID.fromString("9d1c4b7e-5a2f-4e3b-8c6d-1f2e3a4b5c6d");
    private static final int SUMMON_COOLDOWN = 20 * 60;
    private static final int FIGHT_LIMIT = 20 * 60 * 10;

    /** Combate contra un jefe de rito: lo que el rito necesita comprobar al final. */
    private static final class Fight {
        final UUID owner;
        final String riteId;
        final long start;
        final ResourceLocation level;
        final Map<String, Float> damageByStyle = new HashMap<>();
        float ownerDamage, totalDamage, racialDamage, thirstHeal;
        int bossHits, projectiles, explosions, snares, blocks;
        final Map<String, Integer> abilityUses = new HashMap<>();
        /** Adenda 0.7: daño al jefe por fuente racial (aliento, embestida, enjambre, pasajero, forma Titán…) y contadores. */
        final Map<String, Float> sourceDamage = new HashMap<>();
        final Map<String, Integer> counts = new HashMap<>();
        boolean dismounted, armorOff;
        long farSince = -1;

        Fight(UUID owner, String riteId, long start, ResourceLocation level) {
            this.owner = owner;
            this.riteId = riteId;
            this.start = start;
            this.level = level;
        }
    }

    private static final Map<UUID, Fight> FIGHTS = new HashMap<>();      // jefe -> combate

    // ------------------------------------------------------------------ fuentes raciales (adenda 0.7)

    /** Fuente racial del daño que se está haciendo ahora mismo (ver {@link #dealing}). */
    private static final ThreadLocal<String> SOURCE = new ThreadLocal<>();
    /** Estados del jugador que cuentan como fuente (p. ej. "titan" mientras dura la forma, "sticky" pegado). */
    public static final Map<String, java.util.function.Predicate<ServerPlayer>> STATE_SOURCES = new HashMap<>();
    /** Huecos ocupados del cinturón del Porteador (lo instala el Porteador). */
    public static java.util.function.ToIntFunction<ServerPlayer> BELT_ITEMS = p -> 0;
    /** Slimes vivos del Enjambre (lo instala el Enjambre). */
    public static java.util.function.ToIntFunction<ServerPlayer> swarmAlive = p -> 0;
    private record Credit(UUID player, String source, long time) {}
    private static final Map<UUID, Credit> CREDIT = new HashMap<>();
    private static final int CREDIT_TICKS = 60;

    /** Ejecuta un golpe atribuido a una fuente racial: cuenta para "kill.by" y para el reparto de daño del jefe. */
    public static void dealing(String source, Runnable hit) {
        String prev = SOURCE.get();
        SOURCE.set(source);
        try {
            hit.run();
        } finally {
            if (prev == null) SOURCE.remove(); else SOURCE.set(prev);
        }
    }

    /** Marca a la víctima con la fuente racial (para derrotas que llegan después, p. ej. por un efecto). */
    public static void credit(ServerPlayer p, LivingEntity victim, String source) {
        long now = victim.level().getGameTime();
        if (CREDIT.size() > 512) CREDIT.values().removeIf(c -> now - c.time() > CREDIT_TICKS);
        CREDIT.put(victim.getUUID(), new Credit(p.getUUID(), source, victim.level().getGameTime()));
    }

    /** Suma n a los objetivos "counter" con esa clave y al contador del combate contra el jefe de rito. */
    public static void count(ServerPlayer p, String key, int n) {
        UUID bossId = BOSS_OF.get(p.getUUID());
        Fight f = bossId == null ? null : FIGHTS.get(bossId);
        if (f != null) f.counts.merge(key, n, Integer::sum);
        Current c = current(p);
        if (c == null) return;
        for (int i = 0; i < c.rite().objectives().size(); i++) {
            ObjectiveDef o = c.rite().objectives().get(i);
            if ("counter".equals(o.type()) && key.equals(str(o.params(), "key", ""))) advance(p, c, i, null, n);
        }
    }

    /** true si el jugador está luchando contra su jefe de rito y ese es el objetivo. */
    public static boolean isOwnBoss(ServerPlayer p, Entity e) {
        UUID bossId = BOSS_OF.get(p.getUUID());
        return bossId != null && bossId.equals(e.getUUID());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onAnyDamage(LivingDamageEvent event) {
        String src = SOURCE.get();
        if (src == null || event.getAmount() <= 0 || event.getEntity().level().isClientSide()) return;
        if (event.getSource().getEntity() instanceof ServerPlayer p) credit(p, event.getEntity(), src);
    }

    private static boolean byMatches(ServerPlayer p, LivingEntity victim, String by) {
        Credit c = CREDIT.get(victim.getUUID());
        if (c != null && c.player().equals(p.getUUID()) && victim.level().getGameTime() - c.time() <= CREDIT_TICKS
                && (c.source().equals(by) || c.source().startsWith(by + "_"))) return true;
        var state = STATE_SOURCES.get(by);
        return state != null && state.test(p);
    }
    private static final Map<UUID, UUID> BOSS_OF = new HashMap<>();       // dueño -> jefe
    private static final Map<UUID, Long> LAST_SUMMON = new HashMap<>();
    private static final Map<UUID, Deque<Long>> SHIELD_BLOCKS = new HashMap<>();

    // ------------------------------------------------------------------ rito en curso

    private record Current(PlayerClassData data, RiteDef rite, int rank) {}

    private static Current current(ServerPlayer p) {
        PlayerClassData data = PlayerClassDataProvider.get(p);
        if (data == null || data.state.raceId == null || data.state.raceRank >= ProgressionRules.RACE_MAX_RANK) return null;
        RaceDef race = StatEngine.race(data.state);
        SubraceDef sub = race == null ? null : race.subrace(data.state.subraceId);
        RiteDef rite = sub == null ? null : sub.riteFor(data.state.raceRank);
        return rite == null ? null : new Current(data, rite, data.state.raceRank);
    }

    private static Component riteName(RiteDef rite) {
        return Component.translatable("rite." + RpgClasses.MODID + "." + rite.id());
    }

    private static Component objectiveName(RiteDef rite, int i) {
        return Component.translatable("rite." + RpgClasses.MODID + "." + rite.id() + ".o" + i);
    }

    /** Suma a un objetivo y avisa en la barra de acción; si el rito queda completo, sube el rango. */
    private static void advance(ServerPlayer p, Current c, int i, String sub, int amount) {
        ObjectiveDef o = c.rite().objectives().get(i);
        Map<String, Integer> prog = c.data().state.riteProgress;
        if (RiteProgress.done(o, i, prog)) return;
        if (sub != null) RiteProgress.addSub(prog, i, sub, amount);
        else RiteProgress.add(prog, i, amount);
        int value = Math.min(RiteProgress.value(o, i, prog), RiteProgress.target(o));
        p.displayClientMessage(Component.translatable("msg.rites_and_paths.rite.progress", objectiveName(c.rite(), i), value,
                RiteProgress.target(o)).withStyle(ChatFormatting.GOLD), true);
        if (RiteProgress.done(o, i, prog)) objectiveDone(p, c, i);
    }

    private static void markDone(ServerPlayer p, Current c, int i) {
        if (RiteProgress.done(c.rite().objectives().get(i), i, c.data().state.riteProgress)) return;
        RiteProgress.markDone(c.data().state.riteProgress, i);
        objectiveDone(p, c, i);
    }

    private static void objectiveDone(ServerPlayer p, Current c, int i) {
        p.sendSystemMessage(Component.translatable("msg.rites_and_paths.rite.objective_done", objectiveName(c.rite(), i)).withStyle(ChatFormatting.GOLD));
        DebugLog.event(p, "rito", "{} objetivo {} cumplido", c.rite().id(), i);
        if (!RiteProgress.complete(c.rite(), c.data().state.riteProgress)) return;
        int rank = c.rank() + 1;
        c.data().state.raceRank = rank;
        c.data().state.riteProgress.clear();
        StatEngine.refresh(p);
        io.github.rpgclasses.network.Net.syncPlayer(p);
        p.sendSystemMessage(Component.translatable("msg.rites_and_paths.rite.completed", riteName(c.rite()), rank)
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        DebugLog.event(p, "rito", "{} completado: rango {}", c.rite().id(), rank);
    }

    /** Estado del rito en el chat (altar y /raza ritos). */
    public static void showStatus(ServerPlayer p) {
        Current c = current(p);
        if (c == null) {
            p.sendSystemMessage(Component.translatable("msg.rites_and_paths.rite.none"));
            return;
        }
        p.sendSystemMessage(Component.translatable("msg.rites_and_paths.rite.header", c.rank() + 1, riteName(c.rite()))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        p.sendSystemMessage(Component.translatable("rite." + RpgClasses.MODID + "." + c.rite().id() + ".desc").withStyle(ChatFormatting.GRAY));
        for (int i = 0; i < c.rite().objectives().size(); i++) {
            ObjectiveDef o = c.rite().objectives().get(i);
            boolean done = RiteProgress.done(o, i, c.data().state.riteProgress);
            int value = Math.min(RiteProgress.value(o, i, c.data().state.riteProgress), RiteProgress.target(o));
            p.sendSystemMessage(Component.translatable("msg.rites_and_paths.rite.objective",
                    Component.translatable(done ? "msg.rites_and_paths.rite.done_mark" : "msg.rites_and_paths.rite.todo_mark"),
                    objectiveName(c.rite(), i), value, RiteProgress.target(o))
                    .withStyle(done ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        }
        p.sendSystemMessage(Component.translatable("msg.rites_and_paths.rite.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    // ------------------------------------------------------------------ estilos y filtros

    private static boolean isSpell(DamageSource s) {
        return s.typeHolder().unwrapKey().map(k -> "irons_spellbooks".equals(k.location().getNamespace())).orElse(false);
    }

    private static Method spellOf, spellIdOf;
    private static boolean spellLookupDone;

    private static String spellId(DamageSource s) {
        if (!isSpell(s)) return null;
        if (!spellLookupDone) {
            spellLookupDone = true;
            try {
                spellOf = Class.forName("io.redspace.ironsspellbooks.damage.SpellDamageSource").getMethod("spell");
                spellIdOf = Class.forName("io.redspace.ironsspellbooks.api.spells.AbstractSpell").getMethod("getSpellId");
            } catch (ReflectiveOperationException | LinkageError e) {
                spellOf = null;
            }
        }
        if (spellOf == null || !spellOf.getDeclaringClass().isInstance(s)) return null;
        try {
            return (String) spellIdOf.invoke(spellOf.invoke(s));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static final Set<String> RACIAL_SPELLS = Set.of("irons_spellbooks:firebolt", "irons_spellbooks:magma_bomb", "irons_spellbooks:raise_hell");
    /** Conjuros de agua del Moldeador (T.O Magic 'n Extras). */
    private static final Set<String> AQUA_SPELLS = Set.of("traveloptics:hydroshot", "traveloptics:overflow",
            "traveloptics:jet_steam", "traveloptics:jet_stream", "traveloptics:jetstream", "traveloptics:tidal_grasp");

    private static boolean racialSpell(ServerPlayer p, DamageSource s) {
        String id = spellId(s);
        if (id == null) return false;
        return (RACIAL_SPELLS.contains(id) && Races.has(p, "race_racial_spells"))
                || (AQUA_SPELLS.contains(id) && Races.has(p, "race_aqua_spells"));
    }

    /** Estilo de combate del daño: magia, arco, pesada, ligera o "unarmed"; null si no es de ningún estilo. */
    static String styleOf(DamageSource s, ServerPlayer p) {
        if (isSpell(s)) return Ids.MAGIC;
        if (s.is(DamageTypeTags.IS_EXPLOSION)) return null;
        Entity direct = s.getDirectEntity();
        if (direct instanceof Projectile) return Ids.BOW;
        if (direct != p) return null;
        WeaponClassifier.Classification c = WeaponClassifier.classify(p.getMainHandItem());
        if (c.unarmed()) return "unarmed";
        String d = c.discipline();
        return d == null || Ids.SHIELD.equals(d) || Ids.ARMOR.equals(d) || Ids.BOW.equals(d) ? Ids.LIGHT : d;
    }

    private static int armorPieces(Player p) {
        int n = 0;
        for (ItemStack s : p.getArmorSlots()) if (s.getItem() instanceof ArmorItem) n++;
        return n;
    }

    private static int recentBlocks(ServerPlayer p) {
        Deque<Long> q = SHIELD_BLOCKS.get(p.getUUID());
        if (q == null) return 0;
        long now = p.level().getGameTime();
        while (!q.isEmpty() && now - q.peekFirst() > 20 * 30) q.pollFirst();
        return q.size();
    }

    /** Disciplina del muro principal del Humano usada en una derrota: estilo, armadura puesta o escudo usado. */
    private static boolean wallKill(ServerPlayer p, DamageSource s, String wall) {
        if (wall == null) return false;
        if (Ids.ARMOR.equals(wall)) return armorPieces(p) >= 3;
        if (Ids.SHIELD.equals(wall)) return recentBlocks(p) >= 10;
        return wall.equals(styleOf(s, p));
    }

    private static String mainWall(PlayerClassData data) {
        return data.state.humanWalls.isEmpty() ? null : data.state.humanWalls.get(0);
    }

    private static boolean hasTag(ItemStack stack, String key, UUID who) {
        return stack.hasTag() && stack.getTag().hasUUID(key) && stack.getTag().getUUID(key).equals(who);
    }

    private static boolean allyNear(ServerPlayer p, double radius) {
        return !p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(radius),
                e -> e != p && e.isAlive() && e.distanceToSqr(p) <= radius * radius && Allies.ownerOf(e) != null
                        && Allies.isAlly(p, e)).isEmpty();
    }

    private static int alliesNear(ServerPlayer p, double radius) {
        return p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(radius),
                e -> e != p && e.isAlive() && e.distanceToSqr(p) <= radius * radius && Allies.ownerOf(e) != null
                        && Allies.isAlly(p, e)).size();
    }

    private static boolean matchesKill(ServerPlayer p, LivingEntity victim, DamageSource s, JsonObject f, PlayerClassData data) {
        String target = str(f, "target", "enemy");
        boolean enemy = victim instanceof Enemy || isRiteBoss(victim) || (victim instanceof Player && Allies.isEnemy(p, victim));
        if ("enemy".equals(target) && !enemy) return false;
        if ("animal".equals(target) && (!(victim instanceof Animal) || victim instanceof Enemy || Allies.ownerOf(victim) != null)) return false;
        if ("any".equals(target) && victim instanceof Player) return false;
        if (f.has("entity_tag") && !victim.getType().is(entityTag(str(f, "entity_tag", "")))) return false;
        if (f.has("exclude_tag") && victim.getType().is(entityTag(str(f, "exclude_tag", "")))) return false;
        if ("wall".equals(str(f, "style", null)) && !wallKill(p, s, mainWall(data))) return false;
        Level level = p.level();
        if (bool(f, "underground") && level.canSeeSky(p.blockPosition())) return false;
        if (f.has("max_light") && level.getMaxLocalRawBrightness(victim.blockPosition()) > f.get("max_light").getAsInt()) return false;
        if (bool(f, "dark_or_night") && !level.isNight() && level.getMaxLocalRawBrightness(victim.blockPosition()) > 7) return false;
        if (f.has("biome") && !level.getBiome(p.blockPosition()).is(TagKey.create(Registries.BIOME, new ResourceLocation(str(f, "biome", ""))))) return false;
        if (bool(f, "on_lava") && !p.isInLava() && !level.getFluidState(p.blockPosition().below()).is(FluidTags.LAVA)
                && !level.getFluidState(p.blockPosition()).is(FluidTags.LAVA)) return false;
        if (bool(f, "submerged") && !p.isUnderWater()) return false;
        if (bool(f, "target_in_water") && !victim.isInWater()) return false;
        if (bool(f, "mounted") && !p.isPassenger()) return false;
        if (f.has("ally_radius") && !allyNear(p, f.get("ally_radius").getAsDouble())) return false;
        if (bool(f, "weapon_crafted") && (s.getDirectEntity() != p || !hasTag(p.getMainHandItem(), CRAFTER, p.getUUID()))) return false;
        if (bool(f, "weapon_enchanted") && (s.getDirectEntity() != p || !hasTag(p.getMainHandItem(), ENCHANTER, p.getUUID()))) return false;
        if (bool(f, "explosion") && !s.is(DamageTypeTags.IS_EXPLOSION)) return false;
        if (bool(f, "racial_spell") && !racialSpell(p, s)) return false;
        if (f.has("by") && !byMatches(p, victim, str(f, "by", ""))) return false;
        if (f.has("belt_min") && BELT_ITEMS.applyAsInt(p) < f.get("belt_min").getAsInt()) return false;
        if (bool(f, "snared") && !(victim.getPersistentData().hasUUID(RaceAbilities.SNARED_BY)
                && victim.getPersistentData().getUUID(RaceAbilities.SNARED_BY).equals(p.getUUID()))) return false;
        return true;
    }

    private static TagKey<EntityType<?>> entityTag(String id) {
        return TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(id));
    }

    private static String str(JsonObject o, String k, String fallback) {
        return o.has(k) ? o.get(k).getAsString() : fallback;
    }

    private static boolean bool(JsonObject o, String k) {
        return o.has(k) && o.get(k).getAsBoolean();
    }

    private static double num(JsonObject o, String k, double fallback) {
        return o.has(k) ? o.get(k).getAsDouble() : fallback;
    }

    // ------------------------------------------------------------------ hazañas

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        if (victim instanceof ServerPlayer dead) ownerDied(dead);
        if (isRiteBoss(victim)) bossDied(victim, event.getSource());
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer || victim == p) return;
        Current c = current(p);
        if (c == null) return;
        for (int i = 0; i < c.rite().objectives().size(); i++) {
            ObjectiveDef o = c.rite().objectives().get(i);
            switch (o.type()) {
                case "kill" -> {
                    JsonObject f = o.params().has("filter") ? o.params().getAsJsonObject("filter") : new JsonObject();
                    if (matchesKill(p, victim, event.getSource(), f, c.data())) advance(p, c, i, null, 1);
                }
                case "kill_variety" -> {
                    if (victim instanceof Enemy || isRiteBoss(victim)) {
                        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(victim.getType());
                        if (type != null) advance(p, c, i, type.toString(), 1);
                    }
                }
                case "kill_styles" -> {
                    String style = styleOf(event.getSource(), p);
                    if (style != null && (victim instanceof Enemy || isRiteBoss(victim))) advance(p, c, i, style, 1);
                }
                case "boss" -> {
                    String alt = str(o.params(), "alt_entity", null);
                    ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(victim.getType());
                    if (alt != null && type != null && alt.equals(type.toString())) markDone(p, c, i);
                }
                default -> {}
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHurt(LivingHurtEvent event) {
        if (event.getAmount() <= 0 || !(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        DamageSource s = event.getSource();
        Entity attacker = s.getEntity();
        if (attacker == null || attacker == p) return;
        boolean projectile = s.getDirectEntity() instanceof Projectile;
        // Combate contra el jefe de rito: golpes del jefe y proyectiles recibidos.
        UUID bossId = BOSS_OF.get(p.getUUID());
        Fight fight = bossId == null ? null : FIGHTS.get(bossId);
        if (fight != null) {
            if (attacker.getUUID().equals(bossId)) fight.bossHits++;
            if (projectile && !(attacker instanceof Player)) fight.projectiles++;
        }
        Current c = current(p);
        if (c == null) return;
        for (int i = 0; i < c.rite().objectives().size(); i++) {
            ObjectiveDef o = c.rite().objectives().get(i);
            if (!"hits_taken".equals(o.type())) continue;
            if (bool(o.params(), "projectile") && !projectile) continue;
            advance(p, c, i, null, 1);
        }
    }

    public static void onSteal(ServerPlayer p) {
        countSimple(p, "steals");
    }

    public static void onBarter(ServerPlayer p) {
        countSimple(p, "barters");
    }

    private static void countSimple(ServerPlayer p, String type) {
        Current c = current(p);
        if (c == null) return;
        for (int i = 0; i < c.rite().objectives().size(); i++) {
            if (type.equals(c.rite().objectives().get(i).type())) advance(p, c, i, null, 1);
        }
    }

    // ------------------------------------------------------------------ marcas en objetos

    /** Lo que fabrica el jugador: marca de autor si es Herrero (objetos con durabilidad) o si su rito lo pide. */
    public static void markCrafted(ServerPlayer p, ItemStack stack) {
        if (stack.isEmpty()) return;
        boolean mark = Races.has(p, "race_forged") && stack.getItem().getMaxDamage(stack) > 0;
        Current c = current(p);
        if (!mark && c != null) {
            for (ObjectiveDef o : c.rite().objectives()) {
                if (!"offer".equals(o.type())) continue;
                for (JsonElement e : o.params().getAsJsonArray("items")) {
                    JsonObject entry = e.getAsJsonObject();
                    if ("crafted_by_you".equals(str(entry, "pred", null)) && matchesRef(stack, entry)) mark = true;
                }
            }
        }
        if (mark) stack.getOrCreateTag().putUUID(CRAFTER, p.getUUID());
    }

    /** Elfo Arcano: lo que encanta en la mesa lleva su marca. */
    public static void markEnchanted(ServerPlayer p, ItemStack stack) {
        if (!stack.isEmpty() && Races.has(p, "race_arcane")) stack.getOrCreateTag().putUUID(ENCHANTER, p.getUUID());
    }

    /** Tritón Pescador: los tesoros pescados llevan su marca. */
    public static void markFished(ServerPlayer p, List<ItemStack> drops) {
        if (!Races.has(p, "race_fisher")) return;
        TagKey<Item> treasure = TagKey.create(Registries.ITEM, RpgClasses.id("rite/fishing_treasure"));
        for (ItemStack s : drops) if (s.is(treasure)) s.getOrCreateTag().putUUID(FISHED, p.getUUID());
    }

    /** Equipo robado a un mob (Mano Larga). */
    public static void markMobEquipment(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem().getMaxDamage(stack) > 0) stack.getOrCreateTag().putBoolean(MOB_EQUIP, true);
    }

    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) return;
        if (isRiteBoss(dead)) {
            UUID owner = dead.getPersistentData().getUUID(BOSS_OWNER);
            for (ItemEntity drop : event.getDrops()) drop.getItem().getOrCreateTag().putUUID(RITE_DROP, owner);
        }
        // Equipo soltado por mobs que mata un Goblin Ladrón.
        if (dead instanceof Mob && dead instanceof Enemy && event.getSource().getEntity() instanceof ServerPlayer p
                && Races.has(p, "race_long_hand")) {
            for (ItemEntity drop : event.getDrops()) markMobEquipment(drop.getItem());
        }
    }

    @SubscribeEvent
    public void onShieldBlock(ShieldBlockEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        SHIELD_BLOCKS.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>()).addLast(p.level().getGameTime());
        UUID bossId = BOSS_OF.get(p.getUUID());
        if (bossId != null && FIGHTS.containsKey(bossId)) FIGHTS.get(bossId).blocks++;
    }

    // ------------------------------------------------------------------ altar: ofrendas y construcciones

    /** Clic derecho en el altar (agachado: invocar al jefe de rito). */
    public static void onAltar(ServerPlayer p, BlockPos pos, boolean sneaking) {
        Current c = current(p);
        if (c == null) {
            showStatus(p);
            return;
        }
        if (sneaking) {
            summon(p, c, pos);
            return;
        }
        for (int i = 0; i < c.rite().objectives().size(); i++) {
            ObjectiveDef o = c.rite().objectives().get(i);
            if (RiteProgress.done(o, i, c.data().state.riteProgress)) continue;
            if ("structure".equals(o.type())) checkStructure(p, c, i, o, pos);
            else if ("offer".equals(o.type())) tryOffer(p, c, i, o, pos);
            if (current(p) == null || current(p).rite() != c.rite()) return; // el rito se ha completado
        }
        showStatus(p);
    }

    private static boolean altarPlace(ServerLevel level, BlockPos pos, String place) {
        return switch (place) {
            case "underground" -> !level.canSeeSky(pos.above());
            case "nether" -> level.dimension() == Level.NETHER;
            case "underwater" -> level.getFluidState(pos.above()).is(FluidTags.WATER);
            case "forest" -> level.getBiome(pos).is(BiomeTags.IS_FOREST);
            case "snowy" -> level.getBiome(pos).value().coldEnoughToSnow(pos);
            case "storm" -> level.isThundering() && level.canSeeSky(pos.above());
            case "high" -> pos.getY() >= 150;
            case "deep_water" -> level.getFluidState(pos.above()).is(FluidTags.WATER) && level.getFluidState(pos.above(2)).is(FluidTags.WATER)
                    && level.getFluidState(pos.above(3)).is(FluidTags.WATER);
            case "near_lava" -> {
                for (BlockPos q : BlockPos.betweenClosed(pos.offset(-4, -4, -4), pos.offset(4, 4, 4))) {
                    if (level.getFluidState(q).is(FluidTags.LAVA)) yield true;
                }
                yield false;
            }
            default -> true;
        };
    }

    private static void checkStructure(ServerPlayer p, Current c, int i, ObjectiveDef o, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        int r = (int) num(o.params(), "radius", 2), dy = (int) num(o.params(), "dy", 1);
        for (JsonElement e : o.params().getAsJsonArray("blocks")) {
            JsonObject entry = e.getAsJsonObject();
            int need = entry.get("count").getAsInt(), have = 0;
            for (BlockPos q : BlockPos.betweenClosed(pos.offset(-r, -dy, -r), pos.offset(r, dy, r))) {
                BlockState state = level.getBlockState(q);
                if (!matchesBlock(state, entry)) continue;
                if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) != BedPart.HEAD) continue;
                have++;
            }
            if (have < need) {
                p.sendSystemMessage(Component.translatable("msg.rites_and_paths.rite.structure_missing", refName(entry), have, need)
                        .withStyle(ChatFormatting.RED));
                return;
            }
        }
        p.sendSystemMessage(Component.translatable("msg.rites_and_paths.rite.structure_done", objectiveName(c.rite(), i)).withStyle(ChatFormatting.GREEN));
        markDone(p, c, i);
    }

    private static boolean matchesBlock(BlockState state, JsonObject entry) {
        if (entry.has("tag")) return state.is(TagKey.create(Registries.BLOCK, new ResourceLocation(entry.get("tag").getAsString())));
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(entry.get("block").getAsString()));
        return b != null && state.is(b);
    }

    private static Component refName(JsonObject entry) {
        if (entry.has("tag")) return Component.literal("#" + entry.get("tag").getAsString());
        String id = entry.has("item") ? entry.get("item").getAsString() : entry.get("block").getAsString();
        if ("*".equals(id)) return Component.literal(str(entry, "pred", "?"));
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return item == null || item == Items.AIR ? Component.literal(id) : item.getDescription();
    }

    private static boolean matchesRef(ItemStack s, JsonObject entry) {
        if (s.isEmpty()) return false;
        if (entry.has("tag")) return s.is(TagKey.create(Registries.ITEM, new ResourceLocation(entry.get("tag").getAsString())));
        String id = entry.get("item").getAsString();
        if ("*".equals(id)) return true;
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return item != null && s.is(item);
    }

    /** Herramienta, arma o armadura de hierro o mejor (cadena y oro no cuentan). */
    private static boolean ironQuality(ItemStack s) {
        if (s.getItem() instanceof ArmorItem a) return a.getDefense() >= ArmorMaterials.IRON.getDefenseForType(a.getType())
                && a.getMaterial() != ArmorMaterials.GOLD && a.getMaterial() != ArmorMaterials.CHAIN;
        if (s.getItem() instanceof net.minecraft.world.item.TieredItem t) return t.getTier().getLevel() >= 2;
        return false;
    }

    /** Predicados de ofrenda sobre una pila. */
    private static boolean matchesPred(ServerPlayer p, ItemStack s, String pred, PlayerClassData data) {
        if (pred == null) return true;
        if (pred.startsWith("potion:")) {
            ResourceLocation want = new ResourceLocation(pred.substring("potion:".length()));
            ResourceLocation got = ForgeRegistries.POTIONS.getKey(PotionUtils.getPotion(s));
            // Vale también la versión larga o fuerte (minecraft:long_poison, minecraft:strong_healing).
            return got != null && got.getNamespace().equals(want.getNamespace())
                    && (got.getPath().equals(want.getPath()) || got.getPath().equals("long_" + want.getPath())
                    || got.getPath().equals("strong_" + want.getPath()));
        }
        return switch (pred) {
            case "armor" -> s.getItem() instanceof ArmorItem;
            case "spell_item" -> {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
                yield id != null && "irons_spellbooks".equals(id.getNamespace())
                        && (id.getPath().contains("scroll") || id.getPath().contains("spell_book"));
            }
            case "crafted_by_you" -> hasTag(s, CRAFTER, p.getUUID());
            case "enchanted_by_you" -> hasTag(s, ENCHANTER, p.getUUID()) && s.isEnchanted();
            case "enchanted" -> s.isEnchanted();
            case "fished" -> hasTag(s, FISHED, p.getUUID());
            case "rite_drop" -> hasTag(s, RITE_DROP, p.getUUID());
            case "mob_equipment" -> s.hasTag() && s.getTag().getBoolean(MOB_EQUIP);
            case "max_level_book", "twin_books" -> maxLevelBook(s) != null;
            case "iron_quality" -> ironQuality(s);
            case "potion_2" -> {
                for (var e : PotionUtils.getMobEffects(s)) if (e.getAmplifier() >= 1) yield true;
                yield false;
            }
            case "explored_map" -> {
                MapItemSavedData map = MapItem.getSavedData(s, p.level());
                if (map == null) yield false;
                int filled = 0;
                for (byte b : map.colors) if (b != 0) filled++;
                yield filled >= map.colors.length * 0.9;
            }
            case "wall_masterpiece" -> wallMasterpiece(s, mainWall(data));
            case "discipline_heavy" -> Ids.HEAVY.equals(WeaponClassifier.classify(s).discipline());
            case "discipline_light" -> Ids.LIGHT.equals(WeaponClassifier.classify(s).discipline());
            case "discipline_bow" -> Ids.BOW.equals(WeaponClassifier.classify(s).discipline());
            case "discipline_shield" -> Ids.SHIELD.equals(WeaponClassifier.classify(s).discipline());
            default -> false;
        };
    }

    private static Enchantment maxLevelBook(ItemStack s) {
        if (!s.is(Items.ENCHANTED_BOOK)) return null;
        for (Map.Entry<Enchantment, Integer> e : EnchantmentHelper.getEnchantments(s).entrySet()) {
            if (e.getValue() >= e.getKey().getMaxLevel()) return e.getKey();
        }
        return null;
    }

    private static boolean wallMasterpiece(ItemStack s, String wall) {
        if (wall == null) return false;
        if (Ids.ARMOR.equals(wall)) {
            return s.getItem() instanceof ArmorItem a && (a.getMaterial() == ArmorMaterials.DIAMOND || a.getMaterial() == ArmorMaterials.NETHERITE);
        }
        if (Ids.MAGIC.equals(wall)) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
            boolean spell = id != null && "irons_spellbooks".equals(id.getNamespace())
                    && (id.getPath().contains("scroll") || id.getPath().contains("spell_book"));
            return spell || maxLevelBook(s) != null;
        }
        if (!wall.equals(WeaponClassifier.classify(s).discipline())) return false;
        if (s.getItem() instanceof TieredItem t) return t.getTier().getLevel() >= 3;
        return s.isEnchanted(); // arcos y escudos: "de calidad alta" = encantados
    }

    /** Entrega una ofrenda: solo si están todos los objetos a la vez; entonces se consumen. */
    private static void tryOffer(ServerPlayer p, Current c, int i, ObjectiveDef o, BlockPos pos) {
        String place = str(o.params(), "altar", null);
        if (place != null && !altarPlace(p.serverLevel(), pos, place)) {
            p.sendSystemMessage(Component.translatable("msg.rites_and_paths.rite.need_altar_place",
                    Component.translatable("rite.rites_and_paths.place." + place)).withStyle(ChatFormatting.RED));
            return;
        }
        var inv = p.getInventory();
        int[] left = new int[inv.getContainerSize()];
        for (int s = 0; s < left.length; s++) left[s] = inv.getItem(s).getCount();
        Map<Integer, Integer> take = new HashMap<>();
        for (JsonElement e : o.params().getAsJsonArray("items")) {
            JsonObject entry = e.getAsJsonObject();
            if (!plan(p, c.data(), entry, left, take)) {
                p.sendSystemMessage(Component.translatable("msg.rites_and_paths.rite.missing", describe(entry)).withStyle(ChatFormatting.RED));
                return;
            }
        }
        for (Map.Entry<Integer, Integer> t : take.entrySet()) inv.removeItem(t.getKey(), t.getValue());
        p.level().playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1f, 0.8f);
        p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.2,
                pos.getZ() + 0.5, 40, 0.4, 0.4, 0.4, 0.5);
        p.sendSystemMessage(Component.translatable("msg.rites_and_paths.rite.offered", objectiveName(c.rite(), i)).withStyle(ChatFormatting.GREEN));
        markDone(p, c, i);
    }

    private static Component describe(JsonObject entry) {
        int count = entry.get("count").getAsInt();
        MutableComponent out = Component.literal((entry.has("types") ? entry.get("types").getAsInt() + " × " : "") + count + " ")
                .append(refName(entry));
        if (entry.has("distinct")) out.append(" (" + entry.get("distinct").getAsInt() + "+ tipos)");
        if (entry.has("pred") && !"*".equals(str(entry, "item", ""))) out.append(" [" + entry.get("pred").getAsString() + "]");
        return out;
    }

    /** Reserva objetos del inventario para una entrada; false si no hay suficientes. */
    private static boolean plan(ServerPlayer p, PlayerClassData data, JsonObject entry, int[] left, Map<Integer, Integer> take) {
        var inv = p.getInventory();
        String pred = str(entry, "pred", null);
        int count = entry.get("count").getAsInt();
        int types = entry.has("types") ? entry.get("types").getAsInt() : 0;
        int distinct = entry.has("distinct") ? entry.get("distinct").getAsInt() : 0;
        Map<Item, List<Integer>> byItem = new HashMap<>();
        for (int s = 0; s < left.length; s++) {
            ItemStack st = inv.getItem(s);
            if (left[s] > 0 && matchesRef(st, entry) && matchesPred(p, st, pred, data)) byItem.computeIfAbsent(st.getItem(), k -> new ArrayList<>()).add(s);
        }
        if ("twin_books".equals(pred)) return planTwins(inv, byItem, left, take);
        List<int[]> chosen = new ArrayList<>(); // [slot, amount]
        if (types > 0) {
            int ok = 0;
            for (List<Integer> slots : byItem.values()) {
                if (ok >= types) break;
                int have = 0;
                for (int s : slots) have += left[s];
                if (have < count) continue;
                int need = count;
                for (int s : slots) {
                    int t = Math.min(need, left[s]);
                    if (t > 0) chosen.add(new int[]{s, t});
                    need -= t;
                    if (need == 0) break;
                }
                ok++;
            }
            if (ok < types) return false;
        } else {
            if (byItem.size() < distinct) return false;
            int need = count;
            // Primero uno de cada tipo hasta cubrir los tipos distintos; después lo que falte.
            int typesUsed = 0;
            for (List<Integer> slots : byItem.values()) {
                if (typesUsed >= distinct || need == 0) break;
                chosen.add(new int[]{slots.get(0), 1});
                left[slots.get(0)]--;
                need--;
                typesUsed++;
            }
            for (List<Integer> slots : byItem.values()) {
                for (int s : slots) {
                    if (need == 0) break;
                    int t = Math.min(need, left[s]);
                    if (t > 0) {
                        chosen.add(new int[]{s, t});
                        left[s] -= t;
                        need -= t;
                    }
                }
            }
            // Deshacer las reservas parciales si no llega.
            if (need > 0) {
                for (int[] ch : chosen) left[ch[0]] += ch[1];
                return false;
            }
            for (int[] ch : chosen) take.merge(ch[0], ch[1], Integer::sum);
            return true;
        }
        for (int[] ch : chosen) {
            left[ch[0]] -= ch[1];
            take.merge(ch[0], ch[1], Integer::sum);
        }
        return true;
    }

    private static boolean planTwins(net.minecraft.world.entity.player.Inventory inv, Map<Item, List<Integer>> byItem, int[] left,
                                     Map<Integer, Integer> take) {
        Map<Enchantment, Integer> first = new HashMap<>();
        for (List<Integer> slots : byItem.values()) {
            for (int s : slots) {
                Enchantment e = maxLevelBook(inv.getItem(s));
                if (e == null) continue;
                Integer other = first.putIfAbsent(e, s);
                if (other != null) {
                    left[other]--;
                    left[s]--;
                    take.merge(other, 1, Integer::sum);
                    take.merge(s, 1, Integer::sum);
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ jefes de rito

    private static boolean isRiteBoss(Entity e) {
        return e.getPersistentData().hasUUID(BOSS_OWNER);
    }

    private static void summon(ServerPlayer p, Current c, BlockPos pos) {
        int index = -1;
        for (int i = 0; i < c.rite().objectives().size(); i++) {
            ObjectiveDef o = c.rite().objectives().get(i);
            if ("boss".equals(o.type()) && !RiteProgress.done(o, i, c.data().state.riteProgress)) index = i;
        }
        if (index < 0) {
            p.sendSystemMessage(Component.translatable("msg.rites_and_paths.rite.not_now"));
            return;
        }
        ServerLevel level = p.serverLevel();
        UUID active = BOSS_OF.get(p.getUUID());
        if (active != null) {
            for (ServerLevel l : level.getServer().getAllLevels()) {
                Entity e = l.getEntity(active);
                if (e != null && e.isAlive()) {
                    p.sendSystemMessage(Component.translatable("msg.rites_and_paths.rite.boss_active").withStyle(ChatFormatting.RED));
                    return;
                }
            }
            FIGHTS.remove(active);
            BOSS_OF.remove(p.getUUID());
        }
        long now = level.getGameTime();
        long last = LAST_SUMMON.getOrDefault(p.getUUID(), -1_000_000L);
        if (now - last < SUMMON_COOLDOWN) {
            p.sendSystemMessage(Component.translatable("msg.rites_and_paths.rite.boss_cooldown", (SUMMON_COOLDOWN - (now - last)) / 20)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        ObjectiveDef o = c.rite().objectives().get(index);
        String place = str(o.params(), "altar", null);
        if (place != null && !altarPlace(level, pos, place)) {
            p.sendSystemMessage(Component.translatable("msg.rites_and_paths.rite.need_altar_place",
                    Component.translatable("rite.rites_and_paths.place." + place)).withStyle(ChatFormatting.RED));
            return;
        }
        List<EntityType<?>> pool = bossTypes(BOSSES);
        boolean fallback = pool.isEmpty();
        if (fallback) pool = bossTypes(BOSSES_FALLBACK);
        if (pool.isEmpty()) {
            p.sendSystemMessage(Component.translatable("msg.rites_and_paths.rite.boss_none").withStyle(ChatFormatting.RED));
            return;
        }
        EntityType<?> type = pool.get(p.getRandom().nextInt(pool.size()));
        Entity created = type.create(level);
        if (!(created instanceof Mob boss)) {
            if (created != null) created.discard();
            p.sendSystemMessage(Component.translatable("msg.rites_and_paths.rite.boss_none").withStyle(ChatFormatting.RED));
            return;
        }
        BlockPos spot = freeSpot(level, pos, boss);
        if (spot == null) {
            p.sendSystemMessage(Component.translatable("msg.rites_and_paths.rite.boss_no_room").withStyle(ChatFormatting.RED));
            return;
        }
        boss.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, p.getRandom().nextFloat() * 360, 0);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null, null);
        boss.setPersistenceRequired();
        boss.getPersistentData().putUUID(BOSS_OWNER, p.getUUID());
        boss.getPersistentData().putString(BOSS_RITE, c.rite().id());
        boss.setCustomName(Component.translatable("msg.rites_and_paths.rite.boss_name", type.getDescription()).withStyle(ChatFormatting.DARK_PURPLE));
        boss.setCustomNameVisible(true);
        boss.setGlowingTag(true);
        if (fallback) {
            AttributeInstance health = boss.getAttribute(Attributes.MAX_HEALTH);
            if (health != null && health.getModifier(FALLBACK_HEALTH) == null) {
                health.addPermanentModifier(new AttributeModifier(FALLBACK_HEALTH, "rites_and_paths rite boss", 1.0, AttributeModifier.Operation.MULTIPLY_BASE));
                boss.setHealth(boss.getMaxHealth());
            }
        }
        FIGHTS.put(boss.getUUID(), new Fight(p.getUUID(), c.rite().id(), now, level.dimension().location()));
        level.addFreshEntity(boss);
        boss.setTarget(p);
        BOSS_OF.put(p.getUUID(), boss.getUUID());
        LAST_SUMMON.put(p.getUUID(), now);
        level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6f, 1.2f);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, spot.getX() + 0.5, spot.getY() + 1, spot.getZ() + 0.5,
                60, 0.6, 1, 0.6, 0.05);
        p.sendSystemMessage(Component.translatable("msg.rites_and_paths.rite.boss_summoned", type.getDescription()).withStyle(ChatFormatting.DARK_PURPLE));
        DebugLog.event(p, "rito", "jefe de rito invocado: {} ({})", type, fallback ? "vanilla" : "mod");
    }

    private static List<EntityType<?>> bossTypes(TagKey<EntityType<?>> tag) {
        List<EntityType<?>> out = new ArrayList<>();
        var tags = ForgeRegistries.ENTITY_TYPES.tags();
        if (tags != null) for (EntityType<?> t : tags.getTag(tag)) out.add(t);
        return out;
    }

    private static BlockPos freeSpot(ServerLevel level, BlockPos altar, Mob boss) {
        for (int radius = 3; radius <= 6; radius++) {
            for (int a = 0; a < 16; a++) {
                double ang = a * Math.PI / 8;
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos at = BlockPos.containing(altar.getX() + 0.5 + Math.cos(ang) * radius, altar.getY() + dy,
                            altar.getZ() + 0.5 + Math.sin(ang) * radius);
                    AABB box = boss.getType().getAABB(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                    if (level.noCollision(box) && !level.getBlockState(at.below()).isAir()) return at;
                }
            }
        }
        return null;
    }

    /** Daño al jefe de rito: lo hecho por su dueño, por estilo, con conjuros raciales y con explosiones. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBossDamage(LivingDamageEvent event) {
        LivingEntity boss = event.getEntity();
        if (boss.level().isClientSide() || event.getAmount() <= 0) return;
        Fight f = FIGHTS.get(boss.getUUID());
        if (f == null) return;
        float amount = Math.min(event.getAmount(), boss.getHealth());
        f.totalDamage += amount;
        DamageSource s = event.getSource();
        // Pasajero aliado montado sobre el dueño (Porteador): su daño cuenta como "passenger".
        if (s.getEntity() instanceof ServerPlayer rider && !rider.getUUID().equals(f.owner) && rider.getVehicle() instanceof ServerPlayer mount
                && mount.getUUID().equals(f.owner)) f.sourceDamage.merge("passenger", amount, Float::sum);
        if (!(s.getEntity() instanceof ServerPlayer p) || !p.getUUID().equals(f.owner)) return;
        f.ownerDamage += amount;
        String src = SOURCE.get();
        if (src != null) {
            f.sourceDamage.merge(src, amount, Float::sum);
            int cut = src.indexOf('_');
            if (cut > 0) f.sourceDamage.merge(src.substring(0, cut), amount, Float::sum);
        }
        for (var e : STATE_SOURCES.entrySet()) if (e.getValue().test(p)) f.sourceDamage.merge(e.getKey(), amount, Float::sum);
        String style = styleOf(s, p);
        if (style != null) f.damageByStyle.merge(style, amount, Float::sum);
        if (racialSpell(p, s)) f.racialDamage += amount;
        if (s.is(DamageTypeTags.IS_EXPLOSION)) f.explosions++;
    }

    public static void onAbilityUsed(ServerPlayer p, String mechanic) {
        UUID bossId = BOSS_OF.get(p.getUUID());
        Fight f = bossId == null ? null : FIGHTS.get(bossId);
        if (f != null) f.abilityUses.merge(mechanic, 1, Integer::sum);
    }

    public static void onThirstHeal(ServerPlayer p, double amount) {
        UUID bossId = BOSS_OF.get(p.getUUID());
        Fight f = bossId == null ? null : FIGHTS.get(bossId);
        if (f != null) f.thirstHeal += (float) amount;
    }

    public static void onSnared(UUID owner, LivingEntity victim) {
        Fight f = FIGHTS.get(victim.getUUID());
        if (f != null && f.owner.equals(owner)) f.snares++;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || FIGHTS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        for (Map.Entry<UUID, Fight> en : new ArrayList<>(FIGHTS.entrySet())) {
            Fight f = en.getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(f.owner);
            Entity boss = null;
            for (ServerLevel l : server.getAllLevels()) {
                boss = l.getEntity(en.getKey());
                if (boss != null) break;
            }
            if (boss == null) {
                // Fuera de los chunks cargados: se espera, salvo que el combate haya caducado.
                if (owner == null || owner.level().getGameTime() - f.start > FIGHT_LIMIT) endFight(en.getKey(), null, owner);
                continue;
            }
            long now = boss.level().getGameTime();
            boolean far = owner == null || owner.level() != boss.level() || owner.distanceToSqr(boss) > 64 * 64;
            if (far) {
                if (f.farSince < 0) f.farSince = now;
            } else {
                f.farSince = -1;
            }
            if (owner == null || now - f.start > FIGHT_LIMIT || (f.farSince >= 0 && now - f.farSince > 20 * 30)) {
                endFight(en.getKey(), boss, owner);
                continue;
            }
            if (!(owner.getVehicle() instanceof Wolf w) || !owner.getUUID().equals(w.getOwnerUUID())) f.dismounted = true;
            if (armorPieces(owner) < 3) f.armorOff = true;
        }
    }

    /** Acaba un combate sin victoria: el jefe se desvanece. */
    private static void endFight(UUID bossId, Entity boss, ServerPlayer owner) {
        Fight f = FIGHTS.remove(bossId);
        if (f != null) BOSS_OF.remove(f.owner);
        if (boss != null) {
            if (boss.level() instanceof ServerLevel l) {
                l.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, boss.getX(), boss.getY() + 1, boss.getZ(), 30, 0.5, 1, 0.5, 0.02);
            }
            boss.discard();
        }
        if (owner != null) owner.sendSystemMessage(Component.translatable("msg.rites_and_paths.rite.boss_gone").withStyle(ChatFormatting.GRAY));
    }

    private static void ownerDied(ServerPlayer dead) {
        UUID bossId = BOSS_OF.get(dead.getUUID());
        if (bossId == null) return;
        Entity boss = dead.serverLevel().getEntity(bossId);
        endFight(bossId, boss, dead);
    }

    private static void bossDied(LivingEntity boss, DamageSource source) {
        Fight f = FIGHTS.remove(boss.getUUID());
        if (f == null) return;
        BOSS_OF.remove(f.owner);
        MinecraftServer server = boss.getServer();
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(f.owner);
        if (p == null || !p.isAlive()) return;
        Current c = current(p);
        if (c == null || !c.rite().id().equals(f.riteId)) return;
        for (int i = 0; i < c.rite().objectives().size(); i++) {
            ObjectiveDef o = c.rite().objectives().get(i);
            if (!"boss".equals(o.type()) || RiteProgress.done(o, i, c.data().state.riteProgress)) continue;
            String fail = failReason(p, boss, f, o.params(), c.data());
            if (fail != null) {
                p.sendSystemMessage(Component.translatable("msg.rites_and_paths.rite.boss_failed",
                        Component.translatable("rite.rites_and_paths.fail." + fail)).withStyle(ChatFormatting.RED));
                DebugLog.event(p, "rito", "jefe de rito sin cumplir: {}", fail);
                return;
            }
            markDone(p, c, i);
            return;
        }
    }

    /** null si el combate cumple todas las condiciones del rito; si no, la primera que falla. */
    private static String failReason(ServerPlayer p, LivingEntity boss, Fight f, JsonObject o, PlayerClassData data) {
        if (f.ownerDamage < boss.getMaxHealth() * 0.25f) return "damage";
        if (bool(o, "night") && !p.level().isNight()) return "night";
        if (f.bossHits < num(o, "boss_hits", 0)) return "boss_hits";
        if (f.projectiles < num(o, "projectiles", 0)) return "projectiles";
        if (f.explosions < num(o, "explosions", 0)) return "explosions";
        if (f.snares < num(o, "snares", 0)) return "snares";
        if (f.thirstHeal < num(o, "thirst", 0)) return "thirst";
        if (o.has("ability")) {
            int uses = 0;
            for (String m : str(o, "ability", "").split(",")) uses += f.abilityUses.getOrDefault(m.trim(), 0);
            if (uses < num(o, "uses", 1)) return "ability";
        }
        if (bool(o, "mounted") && (f.dismounted || !(p.getVehicle() instanceof Wolf))) return "mounted";
        if (o.has("allies") && alliesNear(p, num(o, "ally_radius", 12)) < o.get("allies").getAsInt()) return "allies";
        if (bool(o, "crafted_diamond")) {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack s = p.getItemBySlot(slot);
                if (!(s.getItem() instanceof ArmorItem a) || !hasTag(s, CRAFTER, p.getUUID())
                        || (a.getMaterial() != ArmorMaterials.DIAMOND && a.getMaterial() != ArmorMaterials.NETHERITE)) return "crafted_diamond";
            }
        }
        if (o.has("source_share") && f.sourceDamage.getOrDefault(str(o, "source", ""), 0f) < f.totalDamage * num(o, "source_share", 0)) return "source_share";
        if (o.has("counts")) {
            for (var e : o.getAsJsonObject("counts").entrySet()) {
                if (f.counts.getOrDefault(e.getKey(), 0) < e.getValue().getAsInt()) return "counts";
            }
        }
        if (o.has("min_slimes") && swarmAlive.applyAsInt(p) < o.get("min_slimes").getAsInt()) return "min_slimes";
        if (o.has("racial_share") && f.racialDamage < f.totalDamage * num(o, "racial_share", 0)) return "racial_share";
        if (o.has("wall_share")) {
            double share = num(o, "wall_share", 0);
            List<String> walls = "both".equals(str(o, "wall_mode", "main")) ? data.state.humanWalls
                    : data.state.humanWalls.isEmpty() ? List.of() : List.of(data.state.humanWalls.get(0));
            if (walls.isEmpty() || ("both".equals(str(o, "wall_mode", "main")) && walls.size() < 2)) return "wall_share";
            for (String wall : walls) {
                boolean ok;
                if (Ids.ARMOR.equals(wall)) ok = !f.armorOff;
                else if (Ids.SHIELD.equals(wall)) ok = f.blocks >= 10;
                else ok = f.damageByStyle.getOrDefault(wall, 0f) >= f.totalDamage * share;
                if (!ok) return "wall_share";
            }
        }
        return null;
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        SHIELD_BLOCKS.remove(id);
        UUID bossId = BOSS_OF.get(id);
        if (bossId != null && event.getEntity() instanceof ServerPlayer p) {
            Entity boss = null;
            for (ServerLevel l : p.server.getAllLevels()) {
                boss = l.getEntity(bossId);
                if (boss != null) break;
            }
            endFight(bossId, boss, null);
        }
    }

    /** Jefe de rito cargado de disco tras un reinicio: ya no hay combate en memoria, así que se retira. */
    @SubscribeEvent
    public void onJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        if (!event.getLevel().isClientSide() && isRiteBoss(e) && !FIGHTS.containsKey(e.getUUID())) {
            event.setCanceled(true);
        }
    }

    private Rites() {}

    public static Rites listener() {
        return new Rites();
    }
}
