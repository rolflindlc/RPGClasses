package io.github.rpgclasses.content;

import com.google.gson.JsonObject;
import io.github.rpgclasses.content.race.Races;
import io.github.rpgclasses.core.stat.StatCalculator;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.registry.ModAttributes;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.MechanicService;
import io.github.rpgclasses.server.StatEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cargas de postura del Monje (rev. 2.9; números de la rev. 2.20: con 0 cargas por debajo de la media de las clases,
 * con 5 en la media y con 10 por encima). Solo se acumulan cargas de la postura activa:
 * <ul>
 *   <li><b>Fuego (Combo):</b> cada golpe desarmado (o con arma marcial); +8 % de daño por carga. Efecto: Golpe Ígneo.
 *       Descarga: onda de fuego.</li>
 *   <li><b>Piedra (Temple):</b> cada golpe recibido de ≥2 HP antes de armadura (ICD 0,5 s); +1,4 de armadura y +0,8 de
 *       dureza por carga, sin más techo que el máximo vanilla (30 / 20; lo que no cabe da resistencia al empuje).
 *       Efecto: aturdimiento. Descarga: absorción.</li>
 *   <li><b>Agua (Fluir):</b> esquivar o desviar (+3) y cada 2 s corriendo (+1); +3 % de velocidad y salto por carga.
 *       Efecto: Contracorriente (lentitud y esquiva garantizada). Descarga: lentitud y empuje en área.</li>
 * </ul>
 * El efecto propio se dispara en el siguiente golpe desarmado al llegar al máximo, o antes con Shift + golpe (o la
 * tecla de posturas agachado) teniendo el umbral (10 cargas). Las ramas Puño, Viento y Piel potencian cada postura;
 * los números están en los parámetros de la habilidad {@code stances} y de las mecánicas, así que se cambian por datapack.
 */
public final class MonkCharges {
    public static final int NONE = 0, WATER = 1, STONE = 2, FIRE = 3;

    public static final String FIRE_POTENCY = "fire_potency";
    public static final String WATER_POTENCY = "water_potency";
    public static final String STONE_POTENCY = "stone_potency";
    public static final String STEADY_FLAME = "steady_flame";
    public static final String EARLY_IGNITION = "early_ignition";
    public static final String STONE_ECHO = "stone_echo";
    public static final String CONTINUOUS_FLOW = "continuous_flow";
    public static final String MASTERY = "stance_mastery";
    public static final String DOMINANT = "dominant_stance";

    private static final String TAG_STUN = "rpgclasses.monk_stun.";

    private static final class State {
        int stance;
        int charges;
        long lastGain;
        long lastDecay;
        long holdUntil;
        long lastStoneGain = -1000;
        int sprintTicks;
        int stillTicks;
        Vec3 lastPos;
        boolean armed;
        long dodgeUntil;
        double absorbGranted;
        long absorbUntil;
        boolean wasArmored;
        int sentStance = -1, sentCharges = -1, sentMax = -1, sentThreshold = -1;
        boolean sentArmed;
        /** Golpe Ígneo en curso: fracción de armadura que ignora este golpe. */
        double pendingArmorIgnore;
        /** Fracción de carga de Fuego acumulada con armas marciales lentas (rev. 2.20). */
        double fireFraction;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    /** Instancia solo para el bus de eventos. */
    public MonkCharges() {}

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), k -> new State());
    }

    // ------------------------------------------------------------------ consultas

    private static JsonObject params() {
        return AbilityService.params(MonkAbilities.STANCES);
    }

    private static double num(String key, double fallback) {
        return AbilityService.number(params(), key, fallback);
    }

    /** Postura activa (0 si no tiene posturas). */
    public static int stance(ServerPlayer player) {
        if (!AbilityService.has(player, MonkAbilities.STANCES)) return NONE;
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return NONE;
        int s = data.counters.getOrDefault(MonkAbilities.STANCE_COUNTER, 0);
        return s >= WATER && s <= FIRE ? s : NONE;
    }

    public static int charges(ServerPlayer player) {
        State st = STATES.get(player.getUUID());
        return st == null ? 0 : st.charges;
    }

    /**
     * Máximo de cargas de una postura: 10 (rev. 2.21: Maestría de Postura y Postura Dominante ya no lo cambian;
     * dan eficacia a la descarga y al efecto propio, ver {@link #efficacy}).
     */
    public static int maxCharges(ServerPlayer player, int stance) {
        return Math.max(1, (int) Math.round(num("max_charges", 10)));
    }

    /**
     * Eficacia de la descarga al cambiar de postura y del efecto propio (rev. 2.21): Maestría de Postura +20 % con
     * 5 puntos y +40 % con 10; Postura Dominante +30 % en la favorita y −30 % en las otras. No toca el buff por carga.
     */
    public static double efficacy(ServerPlayer player, int stance) {
        double e = 1 + MechanicService.number(player, MASTERY, "efficacy", 0);
        ActiveMechanic dominant = MechanicService.find(player, DOMINANT);
        PlayerClassData data = PlayerClassDataProvider.get(player);
        int favorite = data == null ? 0 : data.counters.getOrDefault(MonkAbilities.FAVORITE_COUNTER, 0);
        if (dominant != null && favorite >= WATER && favorite <= FIRE) {
            e += stance == favorite ? dominant.number("favorite_efficacy", 0.3) : dominant.number("others_efficacy", -0.3);
        }
        return Math.max(0.1, e);
    }

    /** Cargas necesarias para disparar el efecto propio antes de llegar al máximo (Ignición Temprana: 6 en Fuego). */
    public static int threshold(ServerPlayer player, int stance) {
        int t = (int) Math.round(num("finisher_threshold", 10));
        if (stance == FIRE) t = (int) Math.round(MechanicService.number(player, EARLY_IGNITION, "threshold", t));
        return Math.min(t, maxCharges(player, stance));
    }

    private static double potency(ServerPlayer player, int stance) {
        String id = switch (stance) {
            case FIRE -> FIRE_POTENCY;
            case WATER -> WATER_POTENCY;
            case STONE -> STONE_POTENCY;
            default -> null;
        };
        return id == null ? 0 : MechanicService.number(player, id, "potency", 0);
    }

    private static boolean active(ServerPlayer player) {
        return !(player instanceof FakePlayer) && stance(player) != NONE && !StatEngine.isWearingArmor(player);
    }

    // ------------------------------------------------------------------ estadísticas (StatEngine)

    /** Coste fijo de la postura sobre el suelo (Cuerpo Marcial): Fuego −25 % de armadura y dureza, Agua −25 % de daño. */
    public static Map<String, Double> traitFactors(ServerPlayer player) {
        int stance = stance(player);
        Map<String, Double> out = new HashMap<>();
        if (stance == FIRE) {
            double f = 1 - num("fire_floor_armor_cost", 0.25);
            out.put("minecraft:generic.armor", f);
            out.put("minecraft:generic.armor_toughness", f);
        } else if (stance == WATER) {
            out.put(io.github.rpgclasses.core.Ids.NS + ":unarmed_damage", 1 - num("water_floor_damage_cost", 0.25));
        }
        return out;
    }

    /** Bonus de las cargas de Agua y coste fijo de Piedra, sumados antes de los topes globales de velocidad y salto. */
    public static Map<StatCalculator.Key, Double> extra(ServerPlayer player) {
        int stance = stance(player);
        if (stance == NONE || StatEngine.isWearingArmor(player)) return Map.of();
        Map<StatCalculator.Key, Double> out = new HashMap<>();
        StatCalculator.Key speed = new StatCalculator.Key(StatCalculator.MOVEMENT_SPEED, "multiply_base");
        StatCalculator.Key jump = new StatCalculator.Key(StatCalculator.JUMP_BONUS, "add");
        if (stance == WATER) {
            double bonus = charges(player) * num("water_buff_per_charge", 0.025) * (1 + potency(player, WATER));
            if (bonus > 0) {
                out.put(speed, bonus);
                out.put(jump, bonus);
            }
        } else if (stance == STONE) {
            out.put(speed, num("stone_speed_cost", -0.15));
            out.put(jump, num("stone_jump_cost", -0.3));
        }
        return out;
    }

    /** Armadura y dureza de las cargas de Piedra hasta el máximo vanilla (30 / 20); lo que no cabe da resistencia al empuje. Paso final del recálculo. */
    public static void applyStoneArmor(ServerPlayer player) {
        if (stance(player) != STONE || StatEngine.isWearingArmor(player)) return;
        int n = charges(player);
        if (n <= 0) return;
        double scale = 1 + potency(player, STONE);
        double perArmor = num("stone_armor_per_charge", 0.75) * scale;
        double perTough = num("stone_toughness_per_charge", 0.25) * scale;
        double armorRoom = Math.max(0, num("stone_armor_cap", 30) - player.getAttributeValue(Attributes.ARMOR));
        double toughRoom = Math.max(0, num("stone_toughness_cap", 20) - player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        double armor = Math.min(n * perArmor, armorRoom);
        double tough = Math.min(n * perTough, toughRoom);
        StatEngine.applyModifier(player, Attributes.ARMOR, "stone_charges", AttributeModifier.Operation.ADDITION, armor);
        StatEngine.applyModifier(player, Attributes.ARMOR_TOUGHNESS, "stone_charges", AttributeModifier.Operation.ADDITION, tough);
        int over = perArmor <= 0 ? 0 : n - (int) Math.ceil(armor / perArmor - 1.0E-6);
        if (over > 0) {
            double kb = Math.min(1.0, over * num("stone_knockback_per_charge", 0.1));
            StatEngine.applyModifier(player, Attributes.KNOCKBACK_RESISTANCE, "stone_charges", AttributeModifier.Operation.ADDITION, kb);
        }
    }

    // ------------------------------------------------------------------ cambio de postura (tecla)

    /**
     * Tecla de posturas. Agachado: con el umbral de cargas prepara el efecto propio para el siguiente golpe; si no
     * hay umbral y tienes Postura Dominante, fija la postura actual como favorita. Sin agacharse: siguiente postura
     * (Agua → Piedra → Fuego), descargando las cargas de la que dejas.
     */
    static boolean onStanceKey(ServerPlayer player, JsonObject p) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return false;
        State st = state(player);
        int current = stance(player);
        if (player.isShiftKeyDown() && current != NONE) {
            if (st.charges >= threshold(player, current)) {
                st.armed = true;
                player.displayClientMessage(Component.translatable("msg.rpgclasses.monk_armed"), true);
                sound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.6f);
                sync(player, st);
                return false;
            }
            if (MechanicService.has(player, DOMINANT)) {
                data.counters.put(MonkAbilities.FAVORITE_COUNTER, current);
                clamp(player, st);
                StatEngine.recompute(player);
                sync(player, st);
                player.displayClientMessage(Component.translatable("msg.rpgclasses.stance_favorite",
                        Component.translatable("stance.rpgclasses." + MonkAbilities.STANCE_NAMES[current])), false);
                return false;
            }
        }
        int next = current == NONE ? WATER : current % 3 + 1;
        int n = st.charges;
        ActiveMechanic flow = MechanicService.find(player, CONTINUOUS_FLOW);
        int kept = flow == null ? 0 : (int) Math.floor(n * flow.number("keep_fraction", 0.3));
        if (current != NONE && n - kept > 0) discharge(player, st, current, n - kept);
        data.counters.put(MonkAbilities.STANCE_COUNTER, next);
        long now = player.server.getTickCount();
        st.stance = next;
        st.charges = Math.min(kept + io.github.rpgclasses.gear.SetEffects.stanceEntryCharges(player, kept), maxCharges(player, next));
        st.armed = false;
        st.lastGain = now;
        st.lastDecay = now;
        st.stillTicks = 0;
        double seconds = AbilityService.number(p, "switch_cooldown_seconds", 2);
        if (flow != null) seconds += flow.number("switch_cooldown_change", 1);
        AbilityService.setCooldown(player, MonkAbilities.STANCES, (int) Math.round(seconds * 20));
        StatEngine.recompute(player);
        sync(player, st);
        player.displayClientMessage(Component.translatable("msg.rpgclasses.stance",
                Component.translatable("stance.rpgclasses." + MonkAbilities.STANCE_NAMES[next])), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0f, 0.8f + 0.2f * next);
        return true;
    }

    /** Descarga al cambiar de postura: el efecto de la postura que dejas, proporcional a las cargas gastadas. */
    private static void discharge(ServerPlayer player, State st, int stance, int n) {
        double scale = (1 + potency(player, stance)) * efficacy(player, stance);
        double radius = num("discharge_radius", 3);
        ServerLevel level = (ServerLevel) player.level();
        switch (stance) {
            case FIRE -> {
                float damage = (float) (unarmedHit(player) * n * num("fire_wave_per_charge", 0.05) * scale);
                DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(PaladinPerks.SHOCKWAVE_DAMAGE), player);
                for (LivingEntity e : enemiesAround(player, radius)) {
                    if (damage > 0) e.hurt(source, damage);
                }
                ring(level, player, ParticleTypes.FLAME, radius, 12 + n * 3);
                sound(player, SoundEvents.BLAZE_SHOOT, 1.0f);
            }
            case STONE -> {
                double amount = n * num("stone_absorb_per_charge", 0.5) * scale
                        * MechanicService.number(player, STONE_ECHO, "absorb_factor", 1);
                player.setAbsorptionAmount((float) (player.getAbsorptionAmount() + amount));
                st.absorbGranted += amount;
                st.absorbUntil = player.server.getTickCount() + Math.round(num("stone_absorb_seconds", 5) * 20);
                ring(level, player, ParticleTypes.CRIT, 1.2, 10 + n * 2);
                sound(player, SoundEvents.STONE_PLACE, 0.7f);
            }
            case WATER -> {
                int slow = (int) Math.round(n / 2.0 * scale * 20);
                double blocks = n / 2.0 * scale;
                for (LivingEntity e : enemiesAround(player, radius)) {
                    if (slow > 0) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slow, 0), player);
                    e.knockback(blocks / 3.0, player.getX() - e.getX(), player.getZ() - e.getZ()); // ≈ 3 bloques por 1,0 [MEDIR]
                    e.hurtMarked = true;
                }
                ring(level, player, ParticleTypes.SPLASH, radius, 20 + n * 4);
                sound(player, SoundEvents.GENERIC_SPLASH, 1.0f);
            }
            default -> { }
        }
        io.github.rpgclasses.gear.SetEffects.onMonkDischarge(player, stance, n);
    }

    // ------------------------------------------------------------------ golpe desarmado (DamagePipeline)

    /**
     * Golpe desarmado del Monje: devuelve el factor de daño (buff de Fuego y Golpe Ígneo), dispara el efecto propio
     * si toca y suma la carga de Fuego. La armadura ignorada del Golpe Ígneo se aplica luego con {@link #armorIgnoreFactor}.
     */
    public static double onUnarmedHit(ServerPlayer attacker, LivingEntity target) {
        if (!active(attacker)) return 1;
        State st = state(attacker);
        int stance = stance(attacker);
        long now = attacker.server.getTickCount();
        int n = st.charges;
        double factor = 1;
        if (stance == FIRE && n > 0) {
            double buff = num("fire_buff_per_charge", 0.03) * (1 + potency(attacker, FIRE))
                    * (1 + MechanicService.number(attacker, STEADY_FLAME, "buff_change", 0));
            factor *= 1 + n * buff;
        }
        int max = maxCharges(attacker, stance);
        boolean fire = n > 0 && (n >= max || (n >= threshold(attacker, stance) && (st.armed || attacker.isShiftKeyDown())));
        if (fire) {
            // Nudilleras de dragonsteel: efectos propios un 20 % más fuertes (rev. 2.20).
            double boost = io.github.rpgclasses.gear.GearEvents.finisherStrength(attacker) * efficacy(attacker, stance);
            double strength = n / 10.0 * boost;
            switch (stance) {
                case FIRE -> {
                    double perCharge = MechanicService.number(attacker, EARLY_IGNITION, "ignite_bonus_per_charge",
                            num("fire_strike_bonus_per_charge", 0.05));
                    factor *= 1 + n * perCharge * boost;
                    st.pendingArmorIgnore = Math.min(num("fire_armor_ignore_cap", 0.8), n * num("fire_armor_ignore_per_charge", 0.05));
                    addBurn(target, num("fire_strike_burn_seconds", 4));
                    burst((ServerLevel) attacker.level(), target, ParticleTypes.FLAME, 10 + n * 2);
                    sound(attacker, SoundEvents.FIRECHARGE_USE, 1.2f);
                }
                case STONE -> {
                    int ticks = (int) Math.round(n * boost * num("stone_stun_per_charge", 0.15) * 20 * (Races.isBoss(target) ? 0.5 : 1));
                    if (ticks > 0 && tryStun(attacker, target, (int) Math.round(num("stone_stun_icd_seconds", 8) * 20))) {
                        CombatAbilities.stun(target, ticks);
                    }
                    burst((ServerLevel) attacker.level(), target, ParticleTypes.CRIT, 8 + n * 2);
                    sound(attacker, SoundEvents.ANVIL_LAND, 0.6f);
                }
                case WATER -> {
                    int slow = (int) Math.round(n * boost * num("water_slow_per_charge", 0.2) * 20);
                    if (slow > 0) target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slow, 1), attacker);
                    st.dodgeUntil = now + Math.round(n * num("water_dodge_window_per_charge", 0.3) * 20);
                    burst((ServerLevel) attacker.level(), target, ParticleTypes.SPLASH, 12 + n * 3);
                    sound(attacker, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.2f);
                }
                default -> { }
            }
            io.github.rpgclasses.server.DebugLog.event(attacker, "cargas", "efecto propio de {} con {} cargas (fuerza {})",
                    MonkAbilities.STANCE_NAMES[stance], n, strength);
            io.github.rpgclasses.gear.SetEffects.onMonkFinisher(attacker, target, stance, n);
            st.charges = 0;
            st.armed = false;
            changed(attacker, st, stance);
        } else if (stance == FIRE) {
            // Rev. 2.20: un arma marcial lenta da más cargas por golpe para ganar las mismas por segundo que los puños.
            st.fireFraction += io.github.rpgclasses.gear.GearRules.chargesPerHit(attacker);
            int whole = (int) Math.floor(st.fireFraction + 1.0E-9);
            st.fireFraction -= whole;
            if (whole > 0) gain(attacker, st, whole);
        }
        return factor;
    }

    /** Golpe Ígneo: el golpe ignora parte de la armadura del objetivo (como la Estocada del Pícaro). Se consume. */
    public static double armorIgnoreFactor(ServerPlayer attacker, LivingEntity target, DamageSource source, double damage) {
        State st = STATES.get(attacker.getUUID());
        if (st == null || st.pendingArmorIgnore <= 0) return 1;
        double ignored = st.pendingArmorIgnore;
        st.pendingArmorIgnore = 0;
        if (damage <= 0 || source.is(DamageTypeTags.BYPASSES_ARMOR)) return 1;
        float armor = target.getArmorValue();
        if (armor <= 0) return 1;
        float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float actual = CombatRules.getDamageAfterAbsorb((float) damage, armor, toughness);
        float wanted = CombatRules.getDamageAfterAbsorb((float) damage, (float) (armor * (1 - ignored)), toughness);
        return actual <= 0 ? 1 : wanted / actual;
    }

    // ------------------------------------------------------------------ reglas compartidas (6 y 7)

    /** Regla 6: un aturdimiento del Monje al mismo objetivo solo entra si pasó su propio ICD desde el último (de cualquier origen). */
    public static boolean tryStun(ServerPlayer monk, LivingEntity target, int icdTicks) {
        long now = target.level().getGameTime();
        String key = TAG_STUN + monk.getUUID();
        var tag = target.getPersistentData();
        if (tag.contains(key) && now - tag.getLong(key) < icdTicks) return false;
        tag.putLong(key, now);
        return true;
    }

    /** Regla 7: las quemaduras del Monje suman su duración a la que ya tenga el objetivo, con tope. */
    public static void addBurn(LivingEntity target, double seconds) {
        if (target.fireImmune() || seconds <= 0) return;
        int cap = (int) Math.round(num("burn_cap_seconds", 15) * 20);
        int current = Math.max(0, target.getRemainingFireTicks());
        target.setRemainingFireTicks(Math.min(cap, current + (int) Math.round(seconds * 20)));
    }

    // ------------------------------------------------------------------ otras fuentes de carga

    /** Desvío de proyectiles (regla 5): +1 en Fuego, +3 en Agua y +1 en Piedra. */
    public static void onDeflect(ServerPlayer monk) {
        if (!active(monk)) return;
        int stance = stance(monk);
        int amount = (int) Math.round(switch (stance) {
            case FIRE -> num("deflect_fire_charges", 1);
            case WATER -> num("deflect_water_charges", 3);
            case STONE -> num("deflect_stone_charges", 1);
            default -> 0;
        });
        if (amount > 0) amount += io.github.rpgclasses.gear.SetEffects.deflectBonus(monk);
        if (amount > 0) gain(monk, state(monk), amount);
    }

    /** Esquiva con éxito de Epic Fight: +3 cargas de Agua. */
    public static void onDodge(ServerPlayer monk) {
        if (active(monk) && stance(monk) == WATER) gain(monk, state(monk), (int) Math.round(num("water_dodge_charges", 3)));
    }

    /** Cargas de la postura activa ganadas por el equipo (parry del sai, efectos de set). */
    public static void grant(ServerPlayer monk, int amount) {
        if (active(monk) && amount > 0) gain(monk, state(monk), amount);
    }

    /** Fuerza de las cargas actuales (0 sin postura). La usan los efectos de set del Monje. */
    public static int activeStance(ServerPlayer monk) {
        return active(monk) ? stance(monk) : NONE;
    }

    /** Meditación: al terminar, cargas de la postura actual que no decaen durante un tiempo. */
    public static void grantHeld(ServerPlayer monk, int amount, double holdSeconds) {
        if (!active(monk) || amount <= 0) return;
        State st = state(monk);
        gain(monk, st, amount);
        st.holdUntil = monk.server.getTickCount() + Math.round(holdSeconds * 20);
    }

    private static void gain(ServerPlayer player, State st, int amount) {
        int stance = stance(player);
        long now = player.server.getTickCount();
        st.stance = stance;
        st.charges = Math.min(maxCharges(player, stance), st.charges + amount);
        st.lastGain = now;
        st.lastDecay = now;
        changed(player, st, stance);
        io.github.rpgclasses.gear.SetEffects.onMonkGain(player, st.charges, threshold(player, stance));
    }

    private static void clamp(ServerPlayer player, State st) {
        int stance = stance(player);
        st.charges = stance == NONE ? 0 : Math.min(st.charges, maxCharges(player, stance));
    }

    /** Agua y Piedra cambian estadísticas con cada carga; Fuego no (su buff va en el golpe). */
    private static void changed(ServerPlayer player, State st, int stance) {
        if (stance == WATER || stance == STONE) StatEngine.recompute(player);
        sync(player, st);
    }

    // ------------------------------------------------------------------ eventos

    /** Piedra: cada golpe recibido de ≥2 HP (≥4 con Eco de Piedra), medido antes de la armadura. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !active(player) || stance(player) != STONE) return;
        double threshold = io.github.rpgclasses.gear.SetEffects.stoneThreshold(player,
                MechanicService.number(player, STONE_ECHO, "hit_threshold", num("stone_hit_threshold", 2)));
        if (event.getAmount() < threshold) return;
        State st = state(player);
        long now = player.server.getTickCount();
        if (now - st.lastStoneGain < num("stone_hit_icd_seconds", 0.5) * 20) return;
        st.lastStoneGain = now;
        gain(player, st, 1);
    }

    /** Contracorriente: esquiva garantizada del siguiente golpe recibido dentro de la ventana. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) return;
        State st = STATES.get(player.getUUID());
        if (st == null || st.dodgeUntil <= 0) return;
        if (player.server.getTickCount() > st.dodgeUntil) {
            st.dodgeUntil = 0;
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity)) return; // golpes, no el entorno
        st.dodgeUntil = 0;
        event.setCanceled(true);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1, player.getZ(), 20, 0.4, 0.5, 0.4, 0.1);
        }
        sound(player, SoundEvents.PLAYER_ATTACK_NODAMAGE, 1.4f);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        int stance = stance(player);
        State st = STATES.get(player.getUUID());
        if (stance == NONE) {
            if (st != null) {
                STATES.remove(player.getUUID());
                Net.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                        new Packets.MonkChargesSync(player.getId(), 0, 0, 0, 0, false));
            }
            return;
        }
        if (st == null) st = state(player);
        long now = player.server.getTickCount();

        // Absorción de la descarga de Piedra: lo que quede se retira al acabar.
        if (st.absorbGranted > 0 && now >= st.absorbUntil) {
            player.setAbsorptionAmount((float) Math.max(0, player.getAbsorptionAmount() - st.absorbGranted));
            st.absorbGranted = 0;
        }

        // Armadura: borra las cargas y no se acumulan mientras la lleve.
        boolean armored = StatEngine.isWearingArmor(player);
        if (armored != st.wasArmored) {
            st.wasArmored = armored;
            StatEngine.recompute(player);
        }
        if (armored) {
            if (st.charges > 0) {
                st.charges = 0;
                st.armed = false;
                changed(player, st, stance);
            }
            sync(player, st);
            return;
        }
        if (st.stance != stance) { // cambio por comando o reinicio
            st.stance = stance;
            st.charges = 0;
            st.armed = false;
            StatEngine.recompute(player);
        }
        if (st.charges > maxCharges(player, stance)) {
            clamp(player, st);
            changed(player, st, stance);
        }

        Vec3 pos = player.position();
        boolean moved = st.lastPos != null && st.lastPos.distanceToSqr(pos) > 1.0E-4;
        st.lastPos = pos;

        // Agua: +1 cada 2 s corriendo.
        if (stance == WATER) {
            st.sprintTicks = player.isSprinting() ? st.sprintTicks + 1 : 0;
            if (st.sprintTicks >= num("water_sprint_seconds", 2) * 20) {
                st.sprintTicks = 0;
                gain(player, st, 1);
            }
        }

        if (st.charges > 0 && now >= st.holdUntil) decay(player, st, stance, now, moved);
        sync(player, st);
    }

    private static void decay(ServerPlayer player, State st, int stance, long now, boolean moved) {
        double gear = io.github.rpgclasses.gear.SetEffects.monkDecayFactor(player, stance); // equipo de clase (rev. 2.20)
        switch (stance) {
            case FIRE -> {
                double slow = gear / Math.max(0.1, 1 - MechanicService.number(player, STEADY_FLAME, "decay_slowdown", 0));
                double delay = num("fire_decay_delay_seconds", 1.5) * 20 * slow;
                double interval = num("fire_decay_interval_seconds", 0.5) * 20 * slow;
                if (now - st.lastGain >= delay && now - st.lastDecay >= interval) lose(player, st, stance, now);
            }
            case STONE -> {
                double delay = num("stone_decay_delay_seconds", 5) * 20 * gear;
                double interval = num("stone_decay_interval_seconds", 5) * 20 * gear;
                if (now - st.lastStoneGain >= delay && now - st.lastDecay >= interval && now - st.lastGain >= delay) lose(player, st, stance, now);
            }
            case WATER -> {
                st.stillTicks = moved ? 0 : st.stillTicks + 1;
                if (st.stillTicks >= num("water_decay_interval_seconds", 1.5) * 20 * gear) {
                    st.stillTicks = 0;
                    lose(player, st, stance, now);
                }
            }
            default -> { }
        }
    }

    private static void lose(ServerPlayer player, State st, int stance, long now) {
        st.charges = Math.max(0, st.charges - 1);
        st.lastDecay = now;
        if (st.charges < threshold(player, stance)) st.armed = false;
        changed(player, st, stance);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            State st = STATES.remove(player.getUUID());
            if (st != null) {
                Net.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                        new Packets.MonkChargesSync(player.getId(), 0, 0, 0, 0, false));
            }
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    /** Quien empieza a ver a un Monje recibe sus cargas (para el efecto de los puños). */
    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof ServerPlayer monk) || !(event.getEntity() instanceof ServerPlayer viewer)) return;
        State st = STATES.get(monk.getUUID());
        if (st == null || st.stance == NONE) return;
        Net.toPlayer(viewer, new Packets.MonkChargesSync(monk.getId(), st.stance, st.charges, Math.max(0, st.sentMax),
                Math.max(0, st.sentThreshold), st.armed));
    }

    // ------------------------------------------------------------------ red y efectos

    private static void sync(ServerPlayer player, State st) {
        int stance = stance(player);
        int max = stance == NONE ? 0 : maxCharges(player, stance);
        int threshold = stance == NONE ? 0 : threshold(player, stance);
        if (stance == st.sentStance && st.charges == st.sentCharges && max == st.sentMax && threshold == st.sentThreshold
                && st.armed == st.sentArmed) return;
        st.sentStance = stance;
        st.sentCharges = st.charges;
        st.sentMax = max;
        st.sentThreshold = threshold;
        st.sentArmed = st.armed;
        Net.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new Packets.MonkChargesSync(player.getId(), stance, st.charges, max, threshold, st.armed));
    }

    private static double unarmedHit(ServerPlayer player) {
        return player.getAttributeValue(Attributes.ATTACK_DAMAGE) + ModAttributes.value(player, ModAttributes.UNARMED_DAMAGE);
    }

    private static java.util.List<LivingEntity> enemiesAround(ServerPlayer player, double radius) {
        return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> CombatAbilities.canHit(player, e) && e.distanceToSqr(player) <= radius * radius);
    }

    private static void ring(ServerLevel level, Player p, ParticleOptions particle, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count;
            level.sendParticles(particle, p.getX() + Math.cos(a) * radius, p.getY() + 0.3, p.getZ() + Math.sin(a) * radius,
                    1, 0, 0.05, 0, 0.02);
        }
    }

    private static void burst(ServerLevel level, LivingEntity target, ParticleOptions particle, int count) {
        level.sendParticles(particle, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                count, target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.1);
    }

    private static void sound(ServerPlayer player, SoundEvent sound, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 0.9f, pitch);
    }
}
