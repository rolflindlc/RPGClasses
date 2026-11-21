package io.github.rpgclasses.spin;

import com.google.gson.JsonObject;
import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.MechanicService;
import io.github.rpgclasses.server.StatEngine;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;

/**
 * Maestro del Giro (adenda del Maestro del Giro, clase experimental): ids, utilidades comunes y registro.
 * <ul>
 *   <li>{@link SphereItem}, {@link BandolierItem} y {@link Bandolier}: las bolas y la bandolera (tipo saco).</li>
 *   <li>{@link SphereEntity}: la bola en vuelo (directa, curva, rebotes, 3 tics de giro, vuelta a la mano, minas y espiral).</li>
 *   <li>{@link SpinThrow}: lanzar, cambiar de bola, Captura Perfecta, Bola Guiada y recoger minas.</li>
 *   <li>{@link SphereHits}: daño, crítico, efectos de material y de subclase y Giro de cada impacto.</li>
 *   <li>{@link SpinState}: la barra de Giro, la cadena de impactos y el Rectángulo Dorado.</li>
 *   <li>{@link SpinAbilities}: Ruta de Rebotes, Giro Reverso, Paso de Giro y Rectángulo Dorado.</li>
 * </ul>
 * Los números se leen siempre de los JSON (tools/spin.py).
 */
public final class SpinMaster {
    public static final String CLASS = Ids.NS + ":spin_master";

    // Habilidades.
    public static final String SPHERE_THROW = Ids.NS + ":sphere_throw";
    public static final String SPIN_BAR = Ids.NS + ":spin_bar";
    public static final String BOUNCE_ROUTE = Ids.NS + ":bounce_route";
    public static final String REVERSE_SPIN = Ids.NS + ":reverse_spin";
    public static final String SPIN_STEP = Ids.NS + ":spin_step";
    public static final String GOLDEN_RECTANGLE = Ids.NS + ":golden_rectangle";
    public static final String SHARED_WEAK_POINT = Ids.NS + ":shared_weak_point";

    // Mecánicas (ramas, perks y subclases).
    public static final String ROTATION = "rotation";
    public static final String REBOUND = "rebound";
    public static final String RETURN = "sphere_return";
    public static final String SCAN = "scan";
    public static final String CURVE = "curve";
    public static final String HEAVY_SPIN = "heavy_spin";
    public static final String SWIFT_SPIN = "swift_spin";
    public static final String BILLIARDS = "billiards";
    public static final String DRY_REBOUND = "dry_rebound";
    public static final String DOUBLE_HAND = "double_hand";
    public static final String SWIFT_RETURN = "swift_return";
    public static final String COLD_EYE = "cold_eye";
    public static final String STEADY_EYE = "steady_eye";
    public static final String TIGHT_CURVE = "tight_curve";
    public static final String GUIDED_BALL = "guided_ball";
    public static final String SPIN_CHARGE = "spin_charge";
    public static final String WEAK_POINT = "weak_point";
    public static final String SPIN_MINE = "spin_mine";

    public static final ResourceKey<DamageType> SPHERE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("sphere"));
    /** Daño de los efectos de las bolas (salto del cobre, onda de la amatista): no vuelve a pasar por el pipeline. */
    public static final ResourceKey<DamageType> SPHERE_EFFECT_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("sphere_effect"));

    private SpinMaster() {}

    public static void register() {
        AbilityService.register(BOUNCE_ROUTE, SpinAbilities::bounceRoute);
        AbilityService.register(REVERSE_SPIN, SpinAbilities::reverseSpin);
        AbilityService.register(SPIN_STEP, SpinAbilities::spinStep);
        AbilityService.register(GOLDEN_RECTANGLE, SpinAbilities::goldenRectangle);
        MinecraftForge.EVENT_BUS.register(new SpinState());
        MinecraftForge.EVENT_BUS.register(new SpinThrow());
    }

    /** ¿Es Maestro del Giro? (servidor y cliente; en el cliente solo el jugador local tiene datos). */
    public static boolean isSpinMaster(Player player) {
        PlayerClassData data = player == null ? null : PlayerClassDataProvider.get(player);
        return data != null && CLASS.equals(data.state.classId);
    }

    /** Puede lanzar bolas: el Maestro del Giro (su muro de Esferas no llega al 100 %). */
    public static boolean canThrow(ServerPlayer player) {
        return isSpinMaster(player) && StatEngine.wall(player, Ids.SPHERES) < 1.0;
    }

    public static int totalLevel(ServerPlayer player) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        return data == null ? 0 : data.state.totalLevel();
    }

    public static int subclassLevel(ServerPlayer player) {
        return AbilityService.subclassLevel(player);
    }

    // ------------------------------------------------------------------ números

    public static JsonObject params(String abilityId) {
        return AbilityService.params(abilityId);
    }

    public static double number(String abilityId, String key, double fallback) {
        return AbilityService.number(params(abilityId), key, fallback);
    }

    /** Para textos (descripciones de objetos): el JSON sincronizado del cliente si lo hay; si no, el del servidor. */
    public static double anySide(String abilityId, String key, double fallback) {
        var def = io.github.rpgclasses.core.definition.DefinitionStore.CLIENT.abilityDef(abilityId);
        if (def == null) def = io.github.rpgclasses.core.definition.DefinitionStore.SERVER.abilityDef(abilityId);
        return def == null ? fallback : AbilityService.number(def.params(), key, fallback);
    }

    /** Número base de los lanzamientos (habilidad sphere_throw). */
    public static double t(String key, double fallback) {
        return number(SPHERE_THROW, key, fallback);
    }

    /** Número de la barra de Giro (habilidad spin_bar). */
    public static double g(String key, double fallback) {
        return number(SPIN_BAR, key, fallback);
    }

    public static ActiveMechanic mech(ServerPlayer player, String id) {
        return MechanicService.find(player, id);
    }

    public static double mech(ServerPlayer player, String id, String key, double fallback) {
        return MechanicService.number(player, id, key, fallback);
    }

    public static boolean has(ServerPlayer player, String id) {
        return MechanicService.has(player, id);
    }

    public static int enchant(Enchantment enchantment, ItemStack stack) {
        return enchantment == null || stack == null || stack.isEmpty() ? 0 : EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
    }

    /** Daño de la bola (directa: la bola; causante: el jugador). */
    public static DamageSource sphereSource(Level level, Entity sphere, Entity owner) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SPHERE_DAMAGE), sphere, owner);
    }

    public static DamageSource effectSource(Level level, Entity sphere, Entity owner) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SPHERE_EFFECT_DAMAGE), sphere, owner);
    }
}
