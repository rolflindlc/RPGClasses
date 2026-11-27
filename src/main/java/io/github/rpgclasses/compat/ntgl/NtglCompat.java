package io.github.rpgclasses.compat.ntgl;

import io.github.rpgclasses.RpgClasses;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

/**
 * Optional bridge to NTGL (NukaTeam's Gun Lib, mod id {@code ntgl}), the library behind Create: Gunsmithing
 * (Ingeniero addendum). There is no compile-time dependency: guns and their projectiles are recognised by the names of
 * NTGL's base classes, and NTGL's events are subscribed to by reflection. If NTGL is missing or its internals change,
 * every method answers "not a firearm" and the rest of the mod keeps working.
 *
 * <p>How NTGL deals damage (checked against ntgl 1.20.1-3.1.2): a bullet hit uses a damage type in the {@code ntgl}
 * namespace whose attacker <i>and</i> direct entity are the shooter; an explosion uses vanilla's explosion type with the
 * projectile as the direct entity. {@code GunProjectileHitEvent} is posted right before the hit deals its damage, on the
 * same thread, which is how {@link #currentAmmo} knows which ammunition caused a hit.
 */
public final class NtglCompat {
    public static final String MOD_ID = "ntgl";
    private static final String WEAPON_ITEM = "com.nukateam.ntgl.common.foundation.item.WeaponItem";
    private static final String PROJECTILE_ENTITY = "com.nukateam.ntgl.common.foundation.entity.ProjectileEntity";
    private static final String HIT_EVENT = "com.nukateam.ntgl.common.event.GunProjectileHitEvent";

    private static final Map<Class<?>, Boolean> WEAPON_CLASSES = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> PROJECTILE_CLASSES = new ConcurrentHashMap<>();

    private static MethodHandle hitEventProjectile;
    private static Field ammoHolderField;
    private static MethodHandle ammoHolderId;

    /** Projectile whose hit is being resolved right now (set by the hit event, read by the damage pipeline). */
    private record CurrentHit(Entity projectile, long gameTime) {}

    private static final ThreadLocal<CurrentHit> CURRENT = new ThreadLocal<>();

    private NtglCompat() {
    }

    public static boolean loaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** Subscribes to NTGL's hit event. Called once at mod construction, only when NTGL is installed. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void init() {
        try {
            ClassLoader loader = NtglCompat.class.getClassLoader();
            Class hitEvent = Class.forName(HIT_EVENT, true, loader);
            hitEventProjectile = MethodHandles.publicLookup().findVirtual(hitEvent, "getProjectile",
                    MethodType.methodType(Class.forName(PROJECTILE_ENTITY, true, loader)));
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, hitEvent, NtglCompat::onHit);
            RpgClasses.LOGGER.info("NTGL detected: firearms go through the class damage pipeline (Pólvora discipline)");
        } catch (ReflectiveOperationException | LinkageError e) {
            RpgClasses.LOGGER.warn("NTGL is loaded but its hit event could not be resolved; ammo-specific rules are off", e);
            hitEventProjectile = null;
        }
    }

    private static void onHit(Event event) {
        if (hitEventProjectile == null) return;
        try {
            Entity projectile = (Entity) hitEventProjectile.invoke(event);
            if (projectile != null && !projectile.level().isClientSide) {
                CURRENT.set(new CurrentHit(projectile, projectile.level().getGameTime()));
            }
        } catch (Throwable t) {
            RpgClasses.LOGGER.warn("NTGL hit event could not be read; ammo-specific rules are off", t);
            hitEventProjectile = null;
        }
    }

    private static boolean extendsClass(Class<?> type, String name, Map<Class<?>, Boolean> cache) {
        return cache.computeIfAbsent(type, t -> {
            for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) {
                if (c.getName().equals(name)) return true;
            }
            return false;
        });
    }

    /** True for any NTGL weapon item (every Create: Gunsmithing gun, including the pneumatic hammer). */
    public static boolean isFirearm(ItemStack stack) {
        return !stack.isEmpty() && isFirearm(stack.getItem());
    }

    public static boolean isFirearm(Item item) {
        return extendsClass(item.getClass(), WEAPON_ITEM, WEAPON_CLASSES);
    }

    /** True for NTGL projectiles: bullets, pellets, rockets, grenades, flames, ballista spears. */
    public static boolean isProjectile(Entity entity) {
        return entity != null && extendsClass(entity.getClass(), PROJECTILE_ENTITY, PROJECTILE_CLASSES);
    }

    /**
     * True when the damage comes from a firearm: an NTGL bullet/flame/energy hit (damage type in the {@code ntgl}
     * namespace) or an NTGL explosion (the direct entity is an NTGL projectile).
     */
    public static boolean isFirearmDamage(DamageSource source) {
        if (isProjectile(source.getDirectEntity())) return true;
        return source.typeHolder().unwrapKey().map(k -> MOD_ID.equals(k.location().getNamespace())).orElse(false);
    }

    /**
     * Ammunition of the projectile whose hit is being resolved on this thread (for example {@code cgs:spear}), or null
     * when it is unknown. For explosions the projectile is the direct entity of the damage source. The value is only
     * trusted during the same tick and for the same shooter.
     */
    public static ResourceLocation currentAmmo(DamageSource source) {
        Entity projectile = isProjectile(source.getDirectEntity()) ? source.getDirectEntity() : null;
        if (projectile == null) {
            CurrentHit hit = CURRENT.get();
            if (hit == null || hit.projectile().level().getGameTime() != hit.gameTime()) return null;
            projectile = hit.projectile();
        }
        return ammoOf(projectile);
    }

    /** Ammo id stored in an NTGL projectile (its {@code ammoHolder}), or null. */
    public static ResourceLocation ammoOf(Entity projectile) {
        if (!isProjectile(projectile)) return null;
        try {
            if (ammoHolderField == null) {
                Class<?> base = Class.forName(PROJECTILE_ENTITY, true, NtglCompat.class.getClassLoader());
                Field f = base.getDeclaredField("ammoHolder");
                f.setAccessible(true);
                ammoHolderId = MethodHandles.publicLookup().findVirtual(f.getType(), "getId",
                        MethodType.methodType(ResourceLocation.class));
                ammoHolderField = f;
            }
            Object holder = ammoHolderField.get(projectile);
            return holder == null ? null : (ResourceLocation) ammoHolderId.invoke(holder);
        } catch (Throwable t) {
            return null;
        }
    }
}
