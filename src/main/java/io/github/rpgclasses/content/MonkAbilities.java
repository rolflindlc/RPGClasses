package io.github.rpgclasses.content;

import com.google.gson.JsonObject;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.stat.StatMath;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.server.AbilityService;
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


/** Habilidades de la clase base de Monje: posturas, desvío de proyectiles y paso ligero. */
public final class MonkAbilities {
    public static final String STANCES = Ids.NS + ":stances";
    public static final String DEFLECT = Ids.NS + ":deflect";
    public static final String LIGHT_STEP = Ids.NS + ":light_step";

    /** Clave del contador guardado con el jugador. 0 = sin elegir, 1 = agua, 2 = piedra, 3 = fuego. */
    public static final String STANCE_COUNTER = "stance";
    public static final String[] STANCE_NAMES = {"none", "water", "stone", "fire"};


    public static void register() {
        AbilityService.register(STANCES, MonkAbilities::cycleStance);
    }

    // --- Posturas (rev. 2.9: cargas de postura, ver MonkCharges) ---

    /** Contador guardado con el jugador: postura favorita (Postura Dominante). */
    public static final String FAVORITE_COUNTER = "stance_favorite";

    private static boolean cycleStance(ServerPlayer player, JsonObject p) {
        return MonkCharges.onStanceKey(player, p);
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
        MonkCharges.onDeflect(monk);
        monk.level().playSound(null, monk.getX(), monk.getY(), monk.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.4f);
    }

    /** Cualquier otro proyectil (o una flecha demasiado fuerte): su daño se reduce en el Nivel Total. */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer monk) || monk instanceof FakePlayer) return;
        if (!event.getSource().is(DamageTypeTags.IS_PROJECTILE) || !canDeflect(monk)) return;
        float reduced = StatMath.sanitizeDamage(event.getAmount() - totalLevel(monk));
        startCooldown(monk);
        MonkCharges.onDeflect(monk);
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
