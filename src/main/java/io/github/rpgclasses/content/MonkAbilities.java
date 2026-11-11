package io.github.rpgclasses.content;

import com.google.gson.JsonObject;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.stat.StatCalculator;
import io.github.rpgclasses.core.stat.StatMath;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.MechanicService;
import io.github.rpgclasses.server.StatEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;

/** Habilidades de la clase base de Monje: posturas, desvío de proyectiles y paso ligero. */
public final class MonkAbilities {
    public static final String STANCES = Ids.NS + ":stances";
    public static final String DEFLECT = Ids.NS + ":deflect";
    public static final String LIGHT_STEP = Ids.NS + ":light_step";

    /** Clave del contador guardado con el jugador. 0 = neutral, 1 = agua, 2 = piedra, 3 = fuego. */
    public static final String STANCE_COUNTER = "stance";
    public static final String[] STANCE_NAMES = {"none", "water", "stone", "fire"};

    private static final String SPEED = StatCalculator.MOVEMENT_SPEED;
    private static final String JUMP = StatCalculator.JUMP_BONUS;
    private static final String UNARMED = Ids.NS + ":unarmed_damage";
    private static final String ARMOR = "minecraft:generic.armor";
    private static final String TOUGHNESS = "minecraft:generic.armor_toughness";

    public static void register() {
        AbilityService.register(STANCES, MonkAbilities::cycleStance);
    }

    // --- Posturas ---

    /** Contadores guardados con el jugador: postura anterior, la de antes y la favorita (Postura Dominante). */
    public static final String PREV_COUNTER = "stance_prev";
    public static final String PREV2_COUNTER = "stance_prev2";
    public static final String FAVORITE_COUNTER = "stance_favorite";
    /** Flujo: hasta qué tick de servidor sigue la postura anterior (no se guarda: dura segundos). */
    private static final Map<java.util.UUID, Long> LINGER_UNTIL = new HashMap<>();

    private static boolean cycleStance(ServerPlayer player, JsonObject p) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return false;
        int current = data.counters.getOrDefault(STANCE_COUNTER, 0);
        // Postura Dominante: agachado + tecla fija la postura actual como favorita (sin cambiar ni gastar cooldown).
        if (player.isShiftKeyDown() && MechanicService.has(player, MonkPerks.DOMINANT) && current > 0) {
            data.counters.put(FAVORITE_COUNTER, current);
            StatEngine.recompute(player);
            player.displayClientMessage(Component.translatable("msg.rpgclasses.stance_favorite",
                    Component.translatable("stance.rpgclasses." + STANCE_NAMES[current])), false);
            return false;
        }
        int next = (current + 1) % STANCE_NAMES.length;
        data.counters.put(PREV2_COUNTER, data.counters.getOrDefault(PREV_COUNTER, 0));
        data.counters.put(PREV_COUNTER, current);
        data.counters.put(STANCE_COUNTER, next);
        ActiveMechanic flow = MechanicService.find(player, MonkPerks.FLOW);
        ActiveMechanic wave = MechanicService.find(player, MonkPerks.ELEMENTAL_WAVE);
        if (flow != null) {
            LINGER_UNTIL.put(player.getUUID(), player.server.getTickCount() + Math.round(flow.number("linger_seconds", 3) * 20));
        }
        // Cooldown de cambio: 4 s; Flujo 3 s y Onda Elemental 7 s (sustituyen la base); Híbrida +2 s.
        double seconds = AbilityService.number(p, "switch_cooldown_seconds", 4);
        if (flow != null) seconds = flow.number("switch_cooldown_seconds", 3);
        if (wave != null) seconds = wave.number("switch_cooldown_seconds", 7);
        seconds += MechanicService.number(player, MonkPerks.HYBRID, "switch_cooldown_change", 0);
        AbilityService.setCooldown(player, STANCES, (int) Math.round(seconds * 20));
        StatEngine.recompute(player);
        if (wave != null) MonkPerks.elementalWave(player, STANCE_NAMES[next], wave);
        player.displayClientMessage(Component.translatable("msg.rpgclasses.stance",
                Component.translatable("stance.rpgclasses." + STANCE_NAMES[next])), false);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0f, 0.8f + 0.2f * next);
        return true;
    }

    /** Flujo: al acabar el efecto de la postura anterior hay que recalcular las estadísticas. */
    static void tickLinger(ServerPlayer player) {
        Long until = LINGER_UNTIL.get(player.getUUID());
        if (until != null && player.server.getTickCount() >= until) {
            LINGER_UNTIL.remove(player.getUUID());
            StatEngine.recompute(player);
        }
    }

    static void forget(java.util.UUID id) {
        LINGER_UNTIL.remove(id);
    }

    /**
     * Multiplicadores que las posturas aplican a los bonus de las pasivas (atributo -> factor).
     * Normalmente solo la actual; con Postura Híbrida la actual y la anterior al 50 %; con Flujo la que se deja
     * sigue unos segundos a una fracción de su peso. Maestría de Postura y Postura Dominante agrandan los bonus
     * de cada postura (no sus penalizaciones). Vacío si el jugador no tiene posturas o todo es neutral.
     */
    public static Map<String, Double> stanceFactors(ServerPlayer player) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null || !AbilityService.has(player, STANCES)) return Map.of();
        JsonObject p = AbilityService.params(STANCES);
        int current = data.counters.getOrDefault(STANCE_COUNTER, 0);
        int prev = data.counters.getOrDefault(PREV_COUNTER, 0);
        int prev2 = data.counters.getOrDefault(PREV2_COUNTER, 0);
        ActiveMechanic flow = MechanicService.find(player, MonkPerks.FLOW);
        Long until = LINGER_UNTIL.get(player.getUUID());
        boolean lingering = flow != null && until != null && player.server.getTickCount() < until;
        double linger = flow == null ? 0 : flow.number("linger_factor", 0.5);

        Map<Integer, Double> weights = new HashMap<>();
        ActiveMechanic hybrid = MechanicService.find(player, MonkPerks.HYBRID);
        if (hybrid != null) {
            double w = hybrid.number("stance_factor", 0.5);
            weights.merge(current, w, Double::sum);
            weights.merge(prev, w, Double::sum);
            if (lingering) weights.merge(prev2, w * linger, Double::sum);
        } else {
            weights.merge(current, 1.0, Double::sum);
            if (lingering) weights.merge(prev, linger, Double::sum);
        }

        double mastery = MechanicService.number(player, MonkPerks.MASTERY, "stance_bonus", 0);
        ActiveMechanic dominant = MechanicService.find(player, MonkPerks.DOMINANT);
        int favorite = data.counters.getOrDefault(FAVORITE_COUNTER, 0);
        Map<String, Double> sum = new HashMap<>();
        for (Map.Entry<Integer, Double> w : weights.entrySet()) {
            int stance = w.getKey();
            if (stance <= 0 || stance >= STANCE_NAMES.length) continue;
            double bonusScale = 1 + mastery;
            if (dominant != null && favorite > 0) {
                bonusScale += stance == favorite ? dominant.number("favorite_bonus", 0.5) : dominant.number("others_bonus", -0.25);
            }
            for (Map.Entry<String, Double> f : rawFactors(STANCE_NAMES[stance], p).entrySet()) {
                double delta = f.getValue() - 1;
                if (delta > 0) delta *= Math.max(0, bonusScale); // las penalizaciones no cambian
                sum.merge(f.getKey(), w.getValue() * delta, Double::sum);
            }
        }
        Map<String, Double> out = new HashMap<>();
        for (Map.Entry<String, Double> e : sum.entrySet()) {
            if (Math.abs(e.getValue()) > 1.0E-9) out.put(e.getKey(), Math.max(0, 1 + e.getValue()));
        }
        return out;
    }

    /** Factores de una postura sola, sin perks. */
    private static Map<String, Double> rawFactors(String stance, JsonObject p) {
        Map<String, Double> f = new HashMap<>();
        switch (stance) {
            case "water" -> {
                double speed = AbilityService.number(p, "water_speed_factor", 2.0);
                f.put(SPEED, speed);
                f.put(JUMP, speed);
                f.put(UNARMED, AbilityService.number(p, "water_damage_factor", 0.5));
            }
            case "stone" -> {
                double armor = AbilityService.number(p, "stone_armor_factor", 1.25);
                f.put(ARMOR, armor);
                f.put(TOUGHNESS, armor);
                double speed = AbilityService.number(p, "stone_speed_factor", 0.25);
                f.put(SPEED, speed);
                f.put(JUMP, speed);
            }
            case "fire" -> {
                f.put(UNARMED, AbilityService.number(p, "fire_damage_factor", 1.5));
                double armor = AbilityService.number(p, "fire_armor_factor", 0.67);
                f.put(ARMOR, armor);
                f.put(TOUGHNESS, armor);
            }
            default -> { }
        }
        return f;
    }

    // --- Desvío y reflejo de proyectiles ---

    /** Si la flecha haría como mucho tanto daño como el Nivel Total, no impacta: vuelve por donde vino. */
    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof ServerPlayer monk) || monk instanceof FakePlayer) return;
        if (!(event.getProjectile() instanceof AbstractArrow arrow) || arrow.getOwner() == monk) return;
        if (!canDeflect(monk)) return;
        Vec3 v = arrow.getDeltaMovement();
        int predicted = Mth.ceil(Mth.clamp(v.length() * arrow.getBaseDamage(), 0.0, Integer.MAX_VALUE));
        if (predicted > totalLevel(monk)) return; // demasiado fuerte: solo se reducirá en onHurt

        event.setCanceled(true);
        arrow.setOwner(monk);
        arrow.setDeltaMovement(v.scale(-1));
        arrow.hurtMarked = true;
        startCooldown(monk);
        monk.level().playSound(null, monk.getX(), monk.getY(), monk.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.4f);
    }

    /** Cualquier otro proyectil (o una flecha demasiado fuerte): su daño se reduce en el Nivel Total. */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer monk) || monk instanceof FakePlayer) return;
        if (!event.getSource().is(DamageTypeTags.IS_PROJECTILE) || !canDeflect(monk)) return;
        float reduced = StatMath.sanitizeDamage(event.getAmount() - totalLevel(monk));
        startCooldown(monk);
        if (reduced <= 0) {
            event.setCanceled(true);
        } else {
            event.setAmount(reduced);
        }
    }

    private static boolean canDeflect(ServerPlayer monk) {
        return AbilityService.has(monk, DEFLECT) && AbilityService.remainingTicks(monk, DEFLECT) <= 0;
    }

    private static void startCooldown(ServerPlayer monk) {
        AbilityService.setCooldown(monk, DEFLECT, AbilityService.ticks(AbilityService.params(DEFLECT), "cooldown_seconds", 5));
    }

    private static int totalLevel(ServerPlayer player) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        return data == null ? 0 : data.state.totalLevel();
    }

    // --- Paso Ligero ---

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && AbilityService.has(player, LIGHT_STEP)) {
            event.setCanceled(true);
        }
    }
}
