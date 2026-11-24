package io.github.rpgclasses.druid;

import com.google.gson.JsonObject;
import io.github.rpgclasses.content.Chronomancy;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.registry.ModAttributes;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.DebugLog;
import io.github.rpgclasses.server.SessionState;
import io.github.rpgclasses.server.StatEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forma Salvaje (adenda, 3): la transformación del Druida.
 * <ul>
 *   <li><b>Lanzamiento:</b> la tecla abre el bestiario; al elegir una especie se lanza durante {@code cast_seconds}
 *       (1 s en el Círculo de la Luna). Recibir daño lo interrumpe sin gastar nada. Mientras, vas más lento.</li>
 *   <li><b>Coste:</b> se paga el maná al terminar y, mientras dura la forma, el maná máximo baja en ese coste (salvo
 *       en el Círculo de la Luna). Al salir se libera.</li>
 *   <li><b>Cuerpo:</b> la vida, la armadura, la velocidad, el salto y el ritmo de golpes de la bestia (la vida y el daño
 *       suben con el nivel y las ramas), su tamaño, su modelo y su alcance. El inventario se guarda aparte
 *       ({@link BeastInventory}), no se pueden usar objetos ni
 *       lanzar conjuros (salvo los de naturaleza con Forma Sobrenatural) y los rasgos de raza se suspenden.</li>
 *   <li><b>Vida propia:</b> se entra con la vida de la bestia llena; si llega a 0, se vuelve a la forma propia con la
 *       vida que se tenía antes (el golpe sobrante no pasa).</li>
 *   <li><b>Fin:</b> agachado + tecla (al instante), o el botón del bestiario; también al dormir, morir o desconectarse.
 *       La recarga empieza al salir. Al cambiar de dimensión se sigue transformado.</li>
 * </ul>
 * Las estadísticas se aplican al final de {@link StatEngine#recompute} ({@link #applyStats}), con los modificadores
 * del mod, así que se borran y se rehacen en cada recálculo sin quedar huérfanos.
 */
public final class WildShape {

    /** Forma en curso de un jugador (servidor). */
    public static final class Form {
        public final FormSpec spec;
        public final Beasts.Info info;
        /** Vida del jugador antes de transformarse (a la que vuelve). */
        final float preHealth;
        /** Maná reservado (el maná máximo baja en esto mientras dura). */
        final int reserved;
        final long started;
        /** 0 = sin límite (Forma Barata lo pone). */
        final int maxTicks;
        double flight;
        boolean wasInWater;
        int beastAbilityUntil;

        Form(FormSpec spec, Beasts.Info info, float preHealth, int reserved, long started, int maxTicks, double flight) {
            this.spec = spec;
            this.info = info;
            this.preHealth = preHealth;
            this.reserved = reserved;
            this.started = started;
            this.maxTicks = maxTicks;
            this.flight = flight;
        }

        public int ticksLeft(long now) {
            return maxTicks <= 0 ? -1 : (int) Math.max(0, started + maxTicks - now);
        }

        public double flightFraction(ServerPlayer p) {
            double max = Druid.number(Druid.WILD_SHAPE, "flight_seconds", 10) * 20;
            return info.flying() ? Math.max(0, Math.min(1, flight / Math.max(1, max))) : -1;
        }
    }

    /** Lanzamiento en curso. */
    private static final class Cast {
        final FormSpec spec;
        final Beasts.Info info;
        final int total;
        int left;
        final int cost;

        Cast(FormSpec spec, Beasts.Info info, int total, int cost) {
            this.spec = spec;
            this.info = info;
            this.total = total;
            this.left = total;
            this.cost = cost;
        }
    }

    public enum Reason { VOLUNTARY, BROKEN, EXPIRED, SLEEP, LOGOUT, DEATH, LOST_CLASS }

    private static final Map<UUID, Form> FORMS = SessionState.track(new HashMap<>());
    private static final Map<UUID, Cast> CASTS = SessionState.track(new HashMap<>());
    /** Cliente: forma de cada jugador que se ve (la llena el paquete de forma). */
    public static final Map<UUID, FormSpec> CLIENT_FORMS = new ConcurrentHashMap<>();

    private static final UUID CAST_SLOW_ID = UUID.fromString("6c1d2b7e-0f45-4d3a-9b21-7e5a4c3d2f10");
    /** Sin el prefijo "rites_and_paths:": StatEngine no lo borra al recalcular mientras se lanza. */
    private static final String CAST_SLOW_NAME = "rites_and_paths_druid:cast_slow";
    private static final ResourceLocation MAX_MANA = new ResourceLocation("irons_spellbooks", "max_mana");
    private static final ThreadLocal<Boolean> BEAST_HIT = ThreadLocal.withInitial(() -> false);

    // ------------------------------------------------------------------ consultas (los dos lados)

    public static boolean inForm(Player p) {
        if (p == null || p instanceof FakePlayer) return false;
        return p.level().isClientSide ? CLIENT_FORMS.containsKey(p.getUUID()) : FORMS.containsKey(p.getUUID());
    }

    public static FormSpec spec(Player p) {
        if (p == null) return null;
        if (p.level().isClientSide) return CLIENT_FORMS.get(p.getUUID());
        Form f = FORMS.get(p.getUUID());
        return f == null ? null : f.spec;
    }

    public static Form form(ServerPlayer p) {
        return FORMS.get(p.getUUID());
    }

    /** Números de la forma en curso (en el cliente, recalculados con los datos sincronizados). */
    public static Beasts.Info info(Player p) {
        if (p instanceof ServerPlayer sp) {
            Form f = FORMS.get(sp.getUUID());
            return f == null ? null : f.info;
        }
        return Beasts.info(spec(p), true);
    }

    public static boolean casting(ServerPlayer p) {
        return CASTS.containsKey(p.getUUID());
    }

    /** Progreso del lanzamiento (0-1) o -1, y su forma: para la barra del cliente. */
    static float castProgress(ServerPlayer p) {
        Cast c = CASTS.get(p.getUUID());
        return c == null ? -1 : 1f - c.left / (float) Math.max(1, c.total);
    }

    static FormSpec castSpec(ServerPlayer p) {
        Cast c = CASTS.get(p.getUUID());
        return c == null ? null : c.spec;
    }

    /** Hitbox de la forma (todas las poses salvo dormido), o null si no está transformado. La usa PlayerSizeMixin. */
    public static EntityDimensions dimensions(Player p, Pose pose) {
        if (pose == Pose.SLEEPING) return null;
        FormSpec spec = spec(p);
        if (spec == null) return null;
        EntityType<?> type = spec.entityType();
        return type == null ? null : type.getDimensions();
    }

    /** Daño de la bestia en curso (golpe completo). */
    public static boolean isBeastHit() {
        return BEAST_HIT.get();
    }

    static void beastHit(boolean on) {
        BEAST_HIT.set(on);
    }

    /** ¿Impide la forma este conjuro? Solo se lanzan los de naturaleza, y solo con Forma Sobrenatural. */
    public static boolean blocksCast(ServerPlayer p, String school) {
        if (casting(p)) return true;
        if (!inForm(p)) return false;
        return !(Druid.hasMech(p, Druid.SUPERNATURAL) && Druid.NATURE_SCHOOL.equals(school));
    }

    // ------------------------------------------------------------------ tecla y bestiario

    /** Tecla de Forma Salvaje: transformado y agachado, vuelve al instante; si no, abre el bestiario. */
    static boolean activate(ServerPlayer p, JsonObject params) {
        if (inForm(p) && p.isShiftKeyDown()) {
            revert(p, Reason.VOLUNTARY);
            return false;
        }
        if (casting(p)) {
            cancelCast(p, true);
            return false;
        }
        DruidNet.openBestiary(p);
        return false;
    }

    /** El jugador elige una forma en el bestiario (o null: volver a su forma). */
    public static void request(ServerPlayer p, FormSpec spec) {
        if (!Druid.isDruid(p) || !Druid.has(p, Druid.WILD_SHAPE)) return;
        if (spec == null) {
            if (inForm(p)) revert(p, Reason.VOLUNTARY);
            return;
        }
        if (casting(p)) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.casting"), true);
            return;
        }
        Beasts.Info info = Beasts.info(spec, false);
        if (info == null || !allowed(p, spec)) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.form_unknown"), true);
            return;
        }
        // Minijefes bestia (más vida que el tope normal): la forma pide nivel 30.
        if (Beasts.miniboss(info, false) && Druid.totalLevel(p) < Beasts.minibossLevel(false)) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.miniboss_level", Beasts.minibossLevel(false)), true);
            return;
        }
        Form current = FORMS.get(p.getUUID());
        if (current != null && current.spec.equals(spec)) return;
        // La recarga solo cuenta desde que se sale de la forma: transformado se puede cambiar de forma.
        long remaining = AbilityService.remainingTicks(p, Druid.WILD_SHAPE);
        if (current == null && remaining > 0) {
            p.displayClientMessage(Component.translatable("msg.rites_and_paths.ability_cooldown",
                    Component.translatable("ability.rites_and_paths.wild_shape.name"), String.format(java.util.Locale.ROOT, "%.1f", remaining / 20.0)), true);
            return;
        }
        int cost = Beasts.manaCost(p, info);
        if (!p.isCreative() && Chronomancy.mana != null && availableMana(p, current) < cost) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.no_mana", cost), true);
            return;
        }
        double castSeconds = Druid.hasMech(p, Druid.MOON) ? Druid.mech(p, Druid.MOON, "cast_seconds", 1)
                : Druid.number(Druid.WILD_SHAPE, "cast_seconds", 6);
        int ticks = Math.max(1, (int) Math.round(castSeconds * 20));
        CASTS.put(p.getUUID(), new Cast(spec, info, ticks, cost));
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(CAST_SLOW_ID);
            speed.addTransientModifier(new AttributeModifier(CAST_SLOW_ID, CAST_SLOW_NAME,
                    Druid.number(Druid.WILD_SHAPE, "cast_slow", -.5), AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.cast_start", formName(spec)), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, .6f, 1.4f);
        DebugLog.event(p, "Druida", "empieza a transformarse en {} ({} tics, {} de maná)", spec.type, ticks, cost);
        DruidNet.sendState(p);
    }

    /** ¿Puede usar esa forma? (conoce la especie, o es una de sus quimeras y sigue siendo Escultor). */
    static boolean allowed(ServerPlayer p, FormSpec spec) {
        PlayerClassData data = Druid.data(p);
        if (data == null) return false;
        if (spec.isChimera()) {
            return Druid.hasMech(p, Druid.SCULPTOR) && Bestiary.chimeras(data).contains(spec.chimera) && spec.chimera.valid();
        }
        return Beasts.eligible(spec.entityType(), false) && Beasts.known(p, spec.type);
    }

    /** Maná con el que se cuenta para la siguiente forma (al cambiar de forma, el reservado vuelve antes). */
    private static float availableMana(ServerPlayer p, Form current) {
        return Chronomancy.mana.mana(p);
    }

    private static Component formName(FormSpec spec) {
        if (spec.isChimera()) return Component.translatable("druid.rites_and_paths.chimera", Beasts.name(spec.type));
        return Beasts.name(spec.type);
    }

    static void cancelCast(ServerPlayer p, boolean message) {
        if (CASTS.remove(p.getUUID()) == null) return;
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(CAST_SLOW_ID);
        if (message) p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.cast_interrupted"), true);
        DruidNet.sendState(p);
    }

    private static void tickCast(ServerPlayer p, Cast c) {
        if (!Druid.isDruid(p)) {
            cancelCast(p, false);
            return;
        }
        c.left--;
        ServerLevel level = p.serverLevel();
        if (c.left % 4 == 0) {
            double a = (c.total - c.left) * .35;
            for (int i = 0; i < 3; i++) {
                double ang = a + i * Math.PI * 2 / 3;
                level.sendParticles(ParticleTypes.COMPOSTER, p.getX() + Math.cos(ang) * .9, p.getY() + .2 + (c.total - c.left) / (double) c.total * 1.6,
                        p.getZ() + Math.sin(ang) * .9, 1, 0, 0, 0, 0);
            }
        }
        if (c.left % 10 == 0) level.playSound(null, p.blockPosition(), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.PLAYERS, .5f, 1.2f);
        if (c.left <= 0) finishCast(p, c);
    }

    private static void finishCast(ServerPlayer p, Cast c) {
        CASTS.remove(p.getUUID());
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(CAST_SLOW_ID);
        if (!allowed(p, c.spec)) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.form_unknown"), true);
            DruidNet.sendState(p);
            return;
        }
        Form previous = FORMS.get(p.getUUID());
        if (!p.isCreative() && Chronomancy.mana != null) {
            float mana = Chronomancy.mana.mana(p);
            if (mana < c.cost) {
                p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.no_mana", c.cost), true);
                DruidNet.sendState(p);
                return;
            }
            Chronomancy.mana.setMana(p, mana - c.cost);
        }
        float preHealth = previous != null ? previous.preHealth : p.getHealth();
        boolean moon = Druid.hasMech(p, Druid.MOON);
        int reserved = moon ? 0 : c.cost;
        int maxTicks = Druid.hasMech(p, Druid.CHEAP_FORM) ? (int) Math.round(Druid.mech(p, Druid.CHEAP_FORM, "max_seconds", 180) * 20) : 0;
        double flight = Druid.number(Druid.WILD_SHAPE, "flight_seconds", 10) * 20;
        Form form = new Form(c.spec, c.info, preHealth, reserved, p.level().getGameTime(), maxTicks, flight);
        form.wasInWater = p.isInWater();
        if (previous != null) clearPassives(p);
        else BeastInventory.stash(p);
        FORMS.put(p.getUUID(), form);
        PlayerClassData data = Druid.data(p);
        if (data != null) Bestiary.saveForm(data, c.spec, preHealth);
        StatEngine.recompute(p);
        p.refreshDimensions();
        p.setHealth(p.getMaxHealth());
        p.stopUsingItem();
        clampMana(p);
        EpicFightBridge.toVanillaMode(p);
        poof(p);
        p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.form_start", formName(c.spec)), true);
        DebugLog.event(p, "Druida", "se transforma en {} (vida {}, maná reservado {})", c.spec.type, p.getMaxHealth(), reserved);
        DruidNet.sendForm(p);
        DruidNet.sendState(p);
        Net.syncPlayer(p);
    }

    /** Vuelve a la forma propia. */
    public static void revert(ServerPlayer p, Reason reason) {
        cancelCast(p, false);
        Form form = FORMS.remove(p.getUUID());
        if (form == null) return;
        PlayerClassData data = Druid.data(p);
        if (data != null) Bestiary.clearForm(data);
        BeastInventory.restore(p);
        BeastMoves.clear(p);
        clearPassives(p);
        restoreFlight(p);
        StatEngine.recompute(p);
        p.refreshDimensions();
        if (reason != Reason.DEATH) {
            float health = Math.min(form.preHealth, p.getMaxHealth());
            double heal = Druid.hasMech(p, Druid.HEALING_EXIT) ? Druid.mech(p, Druid.HEALING_EXIT, "exit_heal", .25)
                    : Druid.mech(p, Druid.ROOTS, "exit_heal", 0);
            health = (float) Math.min(p.getMaxHealth(), health + heal * p.getMaxHealth());
            p.setHealth(Math.max(1f, health));
            poof(p);
            double cd = Druid.hasMech(p, Druid.MOON) ? Druid.mech(p, Druid.MOON, "exit_cooldown_seconds", 5)
                    : Druid.number(Druid.WILD_SHAPE, "exit_cooldown_seconds", 30);
            AbilityService.setCooldown(p, Druid.WILD_SHAPE, (int) Math.round(cd * 20));
            String key = switch (reason) {
                case BROKEN -> "message.rites_and_paths.druid.form_broken";
                case EXPIRED -> "message.rites_and_paths.druid.form_expired";
                default -> "message.rites_and_paths.druid.form_end";
            };
            if (reason != Reason.LOGOUT) p.displayClientMessage(Component.translatable(key), true);
        }
        DebugLog.event(p, "Druida", "vuelve a su forma ({}); vida {}", reason, p.getHealth());
        if (reason != Reason.LOGOUT) {
            DruidNet.sendForm(p);
            DruidNet.sendState(p);
            Net.syncPlayer(p);
        }
    }

    private static void poof(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        level.sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + p.getBbHeight() / 2, p.getZ(), 20, .4, .4, .4, .02);
        level.sendParticles(ParticleTypes.COMPOSTER, p.getX(), p.getY() + p.getBbHeight() / 2, p.getZ(), 16, .5, .5, .5, .05);
        level.playSound(null, p.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, .8f, .9f);
    }

    // ------------------------------------------------------------------ estadísticas (StatEngine)

    /**
     * Al final de {@link StatEngine#recompute}: si está transformado, las estadísticas pasan a ser las de la bestia
     * (con las mejoras del Druida) y se reserva el maná. Los modificadores llevan el prefijo del mod: el siguiente
     * recálculo los borra y los vuelve a poner.
     */
    public static void applyStats(ServerPlayer p) {
        Form f = FORMS.get(p.getUUID());
        if (f == null) return;
        Beasts.Info info = f.info;
        setTo(p, Attributes.MAX_HEALTH, "health", info.health() * Beasts.healthFactor(p));
        setTo(p, Attributes.ARMOR, "armor", info.armor());
        setTo(p, Attributes.ARMOR_TOUGHNESS, "toughness", 0);
        setTo(p, Attributes.KNOCKBACK_RESISTANCE, "knockback", info.knockbackResistance());
        setTo(p, Attributes.ATTACK_SPEED, "attack_speed", info.attackSpeed());
        // Alcance del golpe según el tamaño de la bestia (el de la quimera, el de su torso).
        setTo(p, ForgeMod.ENTITY_REACH.get(), "reach", Beasts.reach(f.spec.entityType(), false));
        double speed = info.speed();
        if (info.aquatic() && !info.amphibious() && !p.isInWater()) speed *= Druid.number(Druid.WILD_SHAPE, "aquatic_land_speed", .35);
        // Arraigada (plantas y bestias de emboscada): no se mueve.
        if (info.hasPassive("rooted")) speed = 0;
        set(p, Attributes.MOVEMENT_SPEED, "speed", AttributeModifier.Operation.MULTIPLY_TOTAL, speed - 1);
        set(p, ModAttributes.JUMP_BONUS.get(), "jump", AttributeModifier.Operation.ADDITION, info.jump());
        if (info.breathesWater()) {
            set(p, ForgeMod.SWIM_SPEED.get(), "swim", AttributeModifier.Operation.ADDITION, Druid.number(Druid.WILD_SHAPE, "form_swim_bonus", 1.5));
        }
        Attribute maxMana = ForgeRegistries.ATTRIBUTES.getValue(MAX_MANA);
        if (maxMana != null && f.reserved > 0) set(p, maxMana, "mana_reserve", AttributeModifier.Operation.ADDITION, -f.reserved);
    }

    /** Tras reservar maná, el que se tiene no puede pasar del nuevo máximo. */
    private static void clampMana(ServerPlayer p) {
        if (Chronomancy.mana == null) return;
        float max = Chronomancy.mana.maxMana(p);
        if (Chronomancy.mana.mana(p) > max) Chronomancy.mana.setMana(p, max);
    }

    /** Modificador transitorio del mod (lo borra el siguiente recálculo de StatEngine). */
    private static void set(ServerPlayer p, Attribute attribute, String key, AttributeModifier.Operation op, double amount) {
        AttributeInstance inst = attribute == null ? null : p.getAttribute(attribute);
        if (inst == null) return;
        String name = StatEngine.MODIFIER_PREFIX + "druid_form:" + key + ":" + op.name();
        UUID id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        inst.removeModifier(id);
        if (amount != 0 && Double.isFinite(amount)) inst.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }

    /**
     * Deja el atributo en {@code target} con todo lo demás puesto (equipo, ramas, pociones): mide cuánto mueve un punto
     * de suma (los multiplicadores lo escalan) y suma lo que falta.
     */
    private static void setTo(ServerPlayer p, Attribute attribute, String key, double target) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        set(p, attribute, key, AttributeModifier.Operation.ADDITION, 0);
        double v0 = inst.getValue();
        set(p, attribute, key, AttributeModifier.Operation.ADDITION, 1);
        double k = inst.getValue() - v0;
        if (k <= 1e-6) {
            set(p, attribute, key, AttributeModifier.Operation.ADDITION, 0);
            return;
        }
        set(p, attribute, key, AttributeModifier.Operation.ADDITION, (target - v0) / k);
    }

    // ------------------------------------------------------------------ cada tic

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        Cast c = CASTS.get(p.getUUID());
        if (c != null) DebugLog.guard(p, "Druida: lanzamiento", () -> tickCast(p, c));
        Form f = FORMS.get(p.getUUID());
        if (f != null) DebugLog.guard(p, "Druida: forma", () -> tickForm(p, f));
        if ((c != null || f != null || Knowledge.studying(p)) && p.tickCount % 4 == 0) DruidNet.sendState(p);
    }

    private static void tickForm(ServerPlayer p, Form f) {
        if (!Druid.isDruid(p) || !Druid.has(p, Druid.WILD_SHAPE)) {
            revert(p, Reason.LOST_CLASS);
            return;
        }
        long now = p.level().getGameTime();
        if (f.maxTicks > 0 && f.ticksLeft(now) <= 0) {
            revert(p, Reason.EXPIRED);
            return;
        }
        Beasts.Info info = f.info;
        // Acuáticas: fuera del agua van más lentas (el cambio rehace las estadísticas).
        boolean water = p.isInWater();
        if (water != f.wasInWater) {
            f.wasInWater = water;
            if (info.aquatic() && !info.amphibious()) StatEngine.recompute(p);
        }
        // Raíz: regeneración en forma (Salida Sanadora la quita; Savia Corporal la aumenta).
        if (p.tickCount % 20 == 0 && !Druid.hasMech(p, Druid.HEALING_EXIT)) {
            double regen = Druid.mech(p, Druid.ROOTS, "form_regen", 0) * (1 + Druid.mech(p, Druid.BODY_SAP, "form_regen", 0));
            if (regen > 0 && p.getHealth() < p.getMaxHealth()) p.heal((float) regen);
        }
        if (p.tickCount % 100 == 0) refreshPassives(p, info);
        if (p.tickCount % 20 == 0) EpicFightBridge.toVanillaMode(p);
        if (info.flying()) tickFlight(p, f);
        if (info.hasPassive("scare_creepers") && p.tickCount % 10 == 0) {
            for (Creeper creeper : p.level().getEntitiesOfClass(Creeper.class, p.getBoundingBox().inflate(8))) {
                if (creeper.getTarget() == p) {
                    creeper.setTarget(null);
                    creeper.setSwellDir(-1);
                }
            }
        }
        if (info.hasPassive("leaf_breaker") && p.tickCount % 5 == 0 && p.getDeltaMovement().horizontalDistanceSqr() > 1e-3
                && p.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            var box = p.getBoundingBox().inflate(.2);
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
                if (p.level().getBlockState(pos).is(BlockTags.LEAVES) && io.github.rpgclasses.content.race.BodyTraits.mayBreak(p, pos.immutable())) {
                    p.level().destroyBlock(pos.immutable(), true, p);
                }
            }
        }
        if (info.hasPassive("dolphin_grace") && p.tickCount % 20 == 0 && water) {
            for (LivingEntity ally : io.github.rpgclasses.server.Allies.alliesAround(p, 8, false)) {
                if (ally.isInWater()) ally.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 60, 0, true, true));
            }
        }
        BeastPassives.tick(p, info);
        BeastAbilities.tick(p, f);
        BeastEffects.tickAura(p, f);
    }

    // ------------------------------------------------------------------ rasgos de la forma

    /** Efectos de fondo de la forma (sin icono): se renuevan cada 5 s y se quitan al salir. */
    private static void refreshPassives(ServerPlayer p, Beasts.Info info) {
        if (info.hasPassive("night_vision")) hidden(p, MobEffects.NIGHT_VISION);
        if (info.breathesWater()) hidden(p, MobEffects.WATER_BREATHING);
        if (info.hasPassive("fire_immune") || info.hasPassive("lava_swim")) hidden(p, MobEffects.FIRE_RESISTANCE);
        if (info.hasPassive("slow_fall")) hidden(p, MobEffects.SLOW_FALLING);
    }

    private static void hidden(ServerPlayer p, MobEffect effect) {
        p.addEffect(new MobEffectInstance(effect, 400, 0, true, false, false));
    }

    /** Quita solo los efectos de fondo que puso la forma (ambientales, invisibles y cortos). */
    private static void clearPassives(ServerPlayer p) {
        for (MobEffect e : new MobEffect[] {MobEffects.NIGHT_VISION, MobEffects.WATER_BREATHING, MobEffects.FIRE_RESISTANCE, MobEffects.SLOW_FALLING}) {
            MobEffectInstance inst = p.getEffect(e);
            if (inst != null && inst.isAmbient() && !inst.isVisible() && inst.getDuration() <= 400) p.removeEffect(e);
        }
    }

    // ------------------------------------------------------------------ vuelo

    private static void tickFlight(ServerPlayer p, Form f) {
        if (p.isCreative() || p.isSpectator()) return;
        Abilities a = p.getAbilities();
        double max = Druid.number(Druid.WILD_SHAPE, "flight_seconds", 10) * 20;
        boolean changed = false;
        float speed = (float) Druid.number(Druid.WILD_SHAPE, "flight_speed", .03);
        if (a.getFlyingSpeed() != speed) {
            a.setFlyingSpeed(speed);
            changed = true;
        }
        if (a.flying) {
            f.flight -= 1;
            double ground = p.level().getHeight(Heightmap.Types.MOTION_BLOCKING, p.getBlockX(), p.getBlockZ());
            boolean tooHigh = p.getY() - ground > Druid.number(Druid.WILD_SHAPE, "flight_ceiling", 40);
            if (f.flight <= 0 || tooHigh) {
                a.flying = false;
                a.mayfly = false;
                changed = true;
                if (f.flight <= 0) p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.flight_tired"), true);
            }
        } else {
            if (p.onGround() || p.isInWater()) f.flight = Math.min(max, f.flight + max * Druid.number(Druid.WILD_SHAPE, "flight_recharge", .1) / 20);
            boolean may = f.flight > max * .2 || a.mayfly && f.flight > 0;
            if (a.mayfly != may) {
                a.mayfly = may;
                changed = true;
            }
        }
        if (changed) p.onUpdateAbilities();
    }

    private static void restoreFlight(ServerPlayer p) {
        Abilities a = p.getAbilities();
        boolean creative = p.isCreative() || p.isSpectator();
        if (a.mayfly == creative && a.getFlyingSpeed() == .05f && (creative || !a.flying)) return;
        a.mayfly = creative;
        if (!creative) a.flying = false;
        a.setFlyingSpeed(.05f);
        p.onUpdateAbilities();
    }

    // ------------------------------------------------------------------ daño, muerte, sesión

    /** Recibir daño interrumpe el lanzamiento. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getAmount() > 0 && casting(p)) cancelCast(p, true);
    }

    /** Si la bestia iba a caer, el golpe no entra: vuelve a su forma con la vida que tenía. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !inForm(p)) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (event.getAmount() >= p.getHealth() + p.getAbsorptionAmount()) {
            event.setCanceled(true);
            revert(p, Reason.BROKEN);
        }
    }

    /** Red de seguridad: si aun así muere transformado (salvo el vacío y /kill), vuelve a su forma. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !inForm(p)) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        event.setCanceled(true);
        revert(p, Reason.BROKEN);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeathFinal(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer p)) return;
        cancelCast(p, false);
        if (inForm(p)) revert(p, Reason.DEATH);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        CASTS.remove(p.getUUID());
        if (inForm(p)) revert(p, Reason.LOGOUT);
    }

    /** Si el servidor se cayó con el jugador transformado, recupera la vida que tenía antes de la forma. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || inForm(p)) return;
        PlayerClassData data = Druid.data(p);
        if (data == null || !Bestiary.hasSavedForm(data)) return;
        float health = Bestiary.savedHealth(data);
        Bestiary.clearForm(data);
        if (health > 0) p.setHealth(Math.min(health, p.getMaxHealth()));
        DebugLog.event(p, "Druida", "entra con una forma sin cerrar (caída del servidor): vida {}", p.getHealth());
    }

    @SubscribeEvent
    public void onSleep(PlayerSleepInBedEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && inForm(p)) revert(p, Reason.SLEEP);
    }

    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer target && inForm(target)) {
            DruidNet.sendFormTo(viewer, target);
        }
    }

    @SubscribeEvent
    public void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && inForm(p)) {
            p.refreshDimensions();
            DruidNet.sendForm(p);
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) DruidNet.sendForm(p);
    }

    /** Altura de los ojos de la forma (la hitbox la pone PlayerSizeMixin). Después de la del tamaño racial. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onSize(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player p) || !p.isAddedToWorld()) return;
        FormSpec spec = spec(p);
        if (spec == null || event.getPose() == Pose.SLEEPING) return;
        EntityType<?> type = spec.entityType();
        if (type == null) return;
        EntityDimensions dims = type.getDimensions();
        event.setNewEyeHeight(Math.max(.15f, dims.height * Beasts.eyeRatio(type, p.level())));
    }

    /** Cambiar el objeto de la mano cambia el ritmo de golpes: transformado se rehace (sigue siendo el de la bestia). */
    @SubscribeEvent
    public void onEquipment(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && inForm(p) && event.getSlot().getType() == EquipmentSlot.Type.HAND) {
            StatEngine.recompute(p);
        }
    }

    // ------------------------------------------------------------------ sin objetos

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (inForm(event.getEntity()) || event.getEntity() instanceof ServerPlayer sp && casting(sp)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (inForm(event.getEntity())) event.setUseItem(Event.Result.DENY);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (inForm(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onInteractEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (inForm(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player p && inForm(p)) event.setCanceled(true);
    }

    /** Transformado se pica con las garras: como con la mano vacía. */
    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (inForm(event.getEntity())) event.setNewSpeed(Math.min(event.getNewSpeed(), 1.0f));
    }

    @SubscribeEvent
    public void onHarvest(PlayerEvent.HarvestCheck event) {
        if (inForm(event.getEntity())) event.setCanHarvest(!event.getTargetBlock().requiresCorrectToolForDrops());
    }

    /** Sin daño de caída (gatos, voladoras), caída lenta (gallina) y menos daño si trepa (arañas). */
    @SubscribeEvent
    public void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        Beasts.Info info = info(p);
        if (info == null) return;
        if (info.flying() || info.hasPassive("no_fall") || info.hasPassive("slow_fall") || info.hasPassive("glide")) {
            event.setDistance(0);
            event.setDamageMultiplier(0);
        } else if (info.hasPassive("climb")) {
            event.setDamageMultiplier(event.getDamageMultiplier() * .5f);
        }
    }
}
