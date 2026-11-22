package io.github.rpgclasses.compat.improvedarmor;

import io.github.rpgclasses.RpgClasses;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.fml.ModList;

/**
 * Optional bridge to Improved Armor (mod id {@code improvedarmor}), which replaces vanilla's armor curve with a
 * flat one. There is no compile-time dependency: the API is resolved by reflection the first time it is needed,
 * and every method falls back to vanilla maths when the mod is missing, switched off or fails.
 *
 * <p>Class gear and class defenses are balanced in armor <i>points</i> (a class set plus the class's own
 * defense equals the normal set of its tier), so they need no rescaling under another curve. What does depend on
 * the curve is any maths that evaluates the armor formula itself, such as "this hit ignores 30% of the armor";
 * those go through {@link #damageAfterArmor}.
 */
public final class ImprovedArmorCompat {
    public static final String MOD_ID = "improvedarmor";
    private static final String API_CLASS = "io.github.rolflindlc.improvedarmor.api.ImprovedArmorApi";

    private static boolean resolved;
    private static MethodHandle isActive;
    private static MethodHandle damageAfterArmor;
    private static MethodHandle effectiveArmor;

    private ImprovedArmorCompat() {
    }

    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;
        if (!ModList.get().isLoaded(MOD_ID)) return;
        try {
            Class<?> api = Class.forName(API_CLASS, true, ImprovedArmorCompat.class.getClassLoader());
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            isActive = lookup.findStatic(api, "isActive", MethodType.methodType(boolean.class));
            damageAfterArmor = lookup.findStatic(api, "damageAfterArmor",
                    MethodType.methodType(double.class, LivingEntity.class, DamageSource.class, double.class, double.class));
            effectiveArmor = lookup.findStatic(api, "effectiveArmor",
                    MethodType.methodType(double.class, LivingEntity.class, DamageSource.class));
            RpgClasses.LOGGER.info("Improved Armor detected: armor-ignoring effects use its flat curve");
        } catch (ReflectiveOperationException | LinkageError e) {
            RpgClasses.LOGGER.warn("Improved Armor is loaded but its API could not be resolved; using vanilla armor maths", e);
            disable();
        }
    }

    private static void disable() {
        isActive = null;
        damageAfterArmor = null;
        effectiveArmor = null;
    }

    /** True when Improved Armor is loaded and its curve is switched on. */
    public static boolean active() {
        if (!resolved) resolve();
        if (isActive == null) return false;
        try {
            return (boolean) isActive.invoke();
        } catch (Throwable t) {
            RpgClasses.LOGGER.warn("Improved Armor API call failed; using vanilla armor maths", t);
            disable();
            return false;
        }
    }

    /**
     * Damage the target takes from one hit after armor (and, with Improved Armor, after Resistance and
     * Protection too). {@code armorScale} multiplies the armor attribute: 1 for the real value, 0.7 to pretend the
     * hit ignores 30% of it. The caller has already excluded damage that bypasses armor.
     */
    public static double damageAfterArmor(LivingEntity target, DamageSource source, double damage, double armorScale) {
        if (active()) {
            try {
                return (double) damageAfterArmor.invoke(target, source, damage, armorScale);
            } catch (Throwable t) {
                RpgClasses.LOGGER.warn("Improved Armor API call failed; using vanilla armor maths", t);
                disable();
            }
        }
        float armor = target.getArmorValue();
        float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        return CombatRules.getDamageAfterAbsorb((float) damage, (float) (armor * armorScale), toughness);
    }

    /** Armor points that apply against this damage source under the flat curve, or -1 when it is not active. */
    public static double effectiveArmor(LivingEntity target, DamageSource source) {
        if (!active()) return -1;
        try {
            return (double) effectiveArmor.invoke(target, source);
        } catch (Throwable t) {
            RpgClasses.LOGGER.warn("Improved Armor API call failed; using vanilla armor maths", t);
            disable();
            return -1;
        }
    }
}
