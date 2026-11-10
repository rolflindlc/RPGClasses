package io.github.rpgclasses.content;

import com.google.gson.JsonObject;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.stat.StatCalculator;
import io.github.rpgclasses.core.stat.StatMath;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.server.AbilityService;
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

    private static boolean cycleStance(ServerPlayer player, JsonObject p) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return false;
        int next = (data.counters.getOrDefault(STANCE_COUNTER, 0) + 1) % STANCE_NAMES.length;
        data.counters.put(STANCE_COUNTER, next);
        AbilityService.setCooldown(player, STANCES, AbilityService.ticks(p, "switch_cooldown_seconds", 4));
        StatEngine.recompute(player);
        player.displayClientMessage(Component.translatable("msg.rpgclasses.stance",
                Component.translatable("stance.rpgclasses." + STANCE_NAMES[next])), false);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0f, 0.8f + 0.2f * next);
        return true;
    }

    /**
     * Multiplicadores que la postura actual aplica a los bonus de las pasivas (atributo -> factor).
     * Vacío si el jugador no tiene posturas o está en la neutral.
     */
    public static Map<String, Double> stanceFactors(ServerPlayer player) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return Map.of();
        int stance = data.counters.getOrDefault(STANCE_COUNTER, 0);
        if (stance <= 0 || stance >= STANCE_NAMES.length || !AbilityService.has(player, STANCES)) return Map.of();
        JsonObject p = AbilityService.params(STANCES);
        Map<String, Double> f = new HashMap<>();
        switch (STANCE_NAMES[stance]) {
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
