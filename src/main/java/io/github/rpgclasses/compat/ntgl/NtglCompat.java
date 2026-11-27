package io.github.rpgclasses.compat.ntgl;

import io.github.rpgclasses.RpgClasses;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

/**
 * Optional bridge to NTGL (NukaTeam's Gun Lib, mod id {@code ntgl}), the library behind Create: Gunsmithing
 * (Engineer addendum). There is no compile-time dependency: guns and their projectiles are recognised by the names of
 * NTGL's base classes, and NTGL's helpers and events are reached by reflection. If NTGL is missing or its internals
 * change, every query answers "not a firearm" / "unknown" and the rest of the mod keeps working.
 *
 * <p>How NTGL deals damage (checked against ntgl 1.20.1-3.1.2): a bullet hit uses a damage type in the {@code ntgl}
 * namespace whose attacker <i>and</i> direct entity are the shooter; an explosion uses vanilla's explosion type with the
 * projectile as the direct entity. {@code GunProjectileHitEvent} is posted right before the hit deals its damage, on the
 * same thread, which is how {@link #currentProjectile} knows which projectile caused a hit.
 *
 * <p>Reloading is driven on the server by {@code ReloadTracker}: one tracker per entity whose public {@code reloadTick}
 * counts down; at 0 a magazine weapon is refilled (a per-cartridge weapon adds one round and starts the next one).
 */
public final class NtglCompat {
    public static final String MOD_ID = "ntgl";
    private static final String PKG = "com.nukateam.ntgl.common.";
    private static final String WEAPON_ITEM = PKG + "foundation.item.WeaponItem";
    private static final String PROJECTILE_ENTITY = PKG + "foundation.entity.ProjectileEntity";

    private static final Map<Class<?>, Boolean> WEAPON_CLASSES = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> PROJECTILE_CLASSES = new ConcurrentHashMap<>();

    /** A shot fired by a living entity (server or client side, see {@code client}). */
    public record Shot(LivingEntity shooter, ItemStack stack, boolean client) {}

    /** A projectile just spawned by a shot (server side). */
    public record Spawn(Entity projectile, LivingEntity shooter) {}

    public static Consumer<Shot> onFirePre;
    public static Consumer<Shot> onFirePost;
    public static Consumer<Spawn> onSpawn;

    private static boolean ready;
    private static Class<?> weaponDataClass;
    private static MethodHandle newWeaponData;
    private static MethodHandle getAmmoCount, setAmmoCount, getCurrentAmmo, holderId;
    private static MethodHandle getMaxAmmo, getRate, getSpread, getReloadTime, getProjectileDamage, getLoadingType;
    private static MethodHandle getAmmoConfig, ammoLife, ammoSpeed, ammoReduceOverLife;
    private static Map<?, ?> reloadTrackers;
    private static Field reloadTickField;
    private static MethodHandle startReloading, stopReloading;
    private static Field wielderField, weaponField;
    private static MethodHandle hitEventProjectile, hitEventRayTrace, fireEventStack, fireEventClient, spawnProjectile, spawnShooter;
    private static final Map<Class<?>, Method> HEADSHOT = new ConcurrentHashMap<>();

    /** Projectile whose hit is being resolved right now (set by the hit event, read by the damage pipeline). */
    private record CurrentHit(Entity projectile, long gameTime, boolean headshot) {}

    private static final ThreadLocal<CurrentHit> CURRENT = new ThreadLocal<>();

    private NtglCompat() {
    }

    public static boolean loaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** True once {@link #init()} resolved NTGL's helpers: the Engineer's gun mechanics need them. */
    public static boolean ready() {
        return ready;
    }

    private static Class<?> cls(String name) throws ClassNotFoundException {
        return Class.forName(name, true, NtglCompat.class.getClassLoader());
    }

    /** Resolves NTGL's helpers and subscribes to its events. Called once at mod construction, only with NTGL installed. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void init() {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        try {
            weaponDataClass = cls(PKG + "data.WeaponData");
            newWeaponData = lookup.findConstructor(weaponDataClass, MethodType.methodType(void.class, ItemStack.class, LivingEntity.class));
            wielderField = weaponDataClass.getField("wielder");
            weaponField = weaponDataClass.getField("weapon");
            Class<?> state = cls(PKG + "util.util.WeaponStateHelper");
            Class<?> holder = cls(PKG + "data.holders.AmmoHolder");
            getAmmoCount = lookup.findStatic(state, "getAmmoCount", MethodType.methodType(int.class, weaponDataClass));
            setAmmoCount = lookup.findStatic(state, "setAmmoCount", MethodType.methodType(void.class, weaponDataClass, int.class));
            getCurrentAmmo = lookup.findStatic(state, "getCurrentAmmo", MethodType.methodType(holder, weaponDataClass));
            holderId = lookup.findVirtual(holder, "getId", MethodType.methodType(ResourceLocation.class));
            Class<?> mod = cls(PKG + "util.util.WeaponModifierHelper");
            getMaxAmmo = lookup.findStatic(mod, "getMaxAmmo", MethodType.methodType(int.class, weaponDataClass));
            getRate = lookup.findStatic(mod, "getRate", MethodType.methodType(int.class, weaponDataClass));
            getSpread = lookup.findStatic(mod, "getSpread", MethodType.methodType(float.class, weaponDataClass));
            getReloadTime = lookup.findStatic(mod, "getReloadTime", MethodType.methodType(int.class, weaponDataClass));
            getProjectileDamage = lookup.findStatic(mod, "getProjectileDamage",
                    MethodType.methodType(float.class, ResourceLocation.class, weaponDataClass));
            Class<?> loading = cls(PKG + "data.holders.LoadingType");
            getLoadingType = lookup.findStatic(mod, "getLoadingType", MethodType.methodType(loading, weaponDataClass));
            Class<?> ammoConfig = cls(PKG + "data.config.weapon.AmmoConfig");
            getAmmoConfig = lookup.findStatic(mod, "getAmmoConfig", MethodType.methodType(ammoConfig, ResourceLocation.class, weaponDataClass));
            ammoLife = lookup.findVirtual(ammoConfig, "getLife", MethodType.methodType(int.class));
            ammoSpeed = lookup.findVirtual(ammoConfig, "getSpeed", MethodType.methodType(float.class));
            ammoReduceOverLife = lookup.findVirtual(ammoConfig, "isDamageReduceOverLife", MethodType.methodType(boolean.class));
            Class<?> tracker = cls(PKG + "util.trackers.ReloadTracker");
            Field map = tracker.getDeclaredField("RELOAD_TRACKER_MAP");
            map.setAccessible(true);
            reloadTrackers = (Map<?, ?>) map.get(null);
            reloadTickField = tracker.getField("reloadTick");
            startReloading = lookup.findStatic(tracker, "startReloading", MethodType.methodType(void.class, weaponDataClass, InteractionHand.class));
            stopReloading = lookup.findStatic(tracker, "stopReloading", MethodType.methodType(void.class, LivingEntity.class, InteractionHand.class));
            ready = true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            RpgClasses.LOGGER.warn("NTGL is loaded but its helpers could not be resolved; Engineer gun mechanics are off", e);
            ready = false;
        }
        try {
            Class projectile = cls(PROJECTILE_ENTITY);
            Class hit = cls(PKG + "event.GunProjectileHitEvent");
            hitEventProjectile = lookup.findVirtual(hit, "getProjectile", MethodType.methodType(projectile));
            hitEventRayTrace = lookup.findVirtual(hit, "getRayTrace", MethodType.methodType(net.minecraft.world.phys.HitResult.class));
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, hit, NtglCompat::onHit);

            Class fire = cls(PKG + "event.GunFireEvent");
            fireEventStack = lookup.findVirtual(fire, "getStack", MethodType.methodType(ItemStack.class));
            fireEventClient = lookup.findVirtual(fire, "isClient", MethodType.methodType(boolean.class));
            Class firePre = cls(PKG + "event.GunFireEvent$Pre");
            Class firePost = cls(PKG + "event.GunFireEvent$Post");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, firePre, (Consumer) e -> onFire((Event) e, onFirePre));
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, firePost, (Consumer) e -> onFire((Event) e, onFirePost));

            Class spawn = cls(PKG + "foundation.event.GunProjectileSpawnEvent");
            spawnProjectile = lookup.findVirtual(spawn, "getProjectile", MethodType.methodType(projectile));
            spawnShooter = lookup.findVirtual(spawn, "getShooter", MethodType.methodType(LivingEntity.class));
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, spawn, NtglCompat::onSpawnEvent);
            RpgClasses.LOGGER.info("NTGL detected: firearms go through the class damage pipeline (Gunpowder discipline)");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            RpgClasses.LOGGER.warn("NTGL is loaded but its events could not be resolved; ammo-specific rules are off", e);
            hitEventProjectile = null;
        }
    }

    private static void onHit(Event event) {
        if (hitEventProjectile == null) return;
        try {
            Entity projectile = (Entity) hitEventProjectile.invoke(event);
            if (projectile == null || projectile.level().isClientSide) return;
            boolean headshot = false;
            Object ray = hitEventRayTrace.invoke(event);
            if (ray != null) {
                Method m = HEADSHOT.computeIfAbsent(ray.getClass(), c -> {
                    try {
                        return c.getMethod("isHeadshot");
                    } catch (NoSuchMethodException e) {
                        return null;
                    }
                });
                if (m != null) headshot = (boolean) m.invoke(ray);
            }
            CURRENT.set(new CurrentHit(projectile, projectile.level().getGameTime(), headshot));
        } catch (Throwable t) {
            RpgClasses.LOGGER.warn("NTGL hit event could not be read; ammo-specific rules are off", t);
            hitEventProjectile = null;
        }
    }

    private static void onFire(Event event, Consumer<Shot> handler) {
        if (handler == null || !(event instanceof net.minecraftforge.event.entity.living.LivingEvent living)) return;
        try {
            handler.accept(new Shot(living.getEntity(), (ItemStack) fireEventStack.invoke(event), (boolean) fireEventClient.invoke(event)));
        } catch (Throwable t) {
            RpgClasses.LOGGER.warn("NTGL fire event could not be handled", t);
        }
    }

    private static void onSpawnEvent(Event event) {
        if (onSpawn == null) return;
        try {
            onSpawn.accept(new Spawn((Entity) spawnProjectile.invoke(event), (LivingEntity) spawnShooter.invoke(event)));
        } catch (Throwable t) {
            RpgClasses.LOGGER.warn("NTGL projectile spawn event could not be handled", t);
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
     * namespace), an NTGL explosion (the direct entity is an NTGL projectile) or the Engineer's own shots and blasts.
     */
    public static boolean isFirearmDamage(DamageSource source) {
        if (isProjectile(source.getDirectEntity())) return true;
        return source.typeHolder().unwrapKey().map(k -> MOD_ID.equals(k.location().getNamespace())
                || k.location().equals(RpgClasses.id("engineer_bullet"))
                || k.location().equals(RpgClasses.id("engineer_blast"))).orElse(false);
    }

    /**
     * The NTGL projectile behind this damage: the direct entity for explosions, or the one whose hit is being resolved
     * on this thread for bullets (only trusted during the same tick). Null when unknown.
     */
    public static Entity currentProjectile(DamageSource source) {
        if (isProjectile(source.getDirectEntity())) return source.getDirectEntity();
        CurrentHit hit = CURRENT.get();
        if (hit == null || hit.projectile().level().getGameTime() != hit.gameTime()) return null;
        return hit.projectile();
    }

    /** Whether the bullet hit being resolved on this thread was a headshot (as NTGL decided it). */
    public static boolean currentHeadshot(DamageSource source) {
        if (isProjectile(source.getDirectEntity())) return false;
        CurrentHit hit = CURRENT.get();
        return hit != null && hit.projectile().level().getGameTime() == hit.gameTime() && hit.headshot();
    }

    /** Ammunition of the projectile behind this damage (for example {@code cgs:spear}), or null. */
    public static ResourceLocation currentAmmo(DamageSource source) {
        Entity projectile = currentProjectile(source);
        return projectile == null ? null : ammoOf(projectile);
    }

    private static Field ammoHolderField;
    private static MethodHandle ammoHolderId;

    /** Ammo id stored in an NTGL projectile (its {@code ammoHolder}), or null. */
    public static ResourceLocation ammoOf(Entity projectile) {
        if (!isProjectile(projectile)) return null;
        try {
            if (ammoHolderField == null) {
                Field f = cls(PROJECTILE_ENTITY).getDeclaredField("ammoHolder");
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

    // ------------------------------------------------------------------ weapon state (server and client)

    private static Object data(ItemStack stack, LivingEntity wielder) throws Throwable {
        return newWeaponData.invoke(stack, wielder);
    }

    /** Rounds in the gun's magazine, or -1 when unknown. */
    public static int ammo(ItemStack stack, LivingEntity wielder) {
        if (!ready || !isFirearm(stack)) return -1;
        try {
            return (int) getAmmoCount.invoke(data(stack, wielder));
        } catch (Throwable t) {
            return -1;
        }
    }

    public static void setAmmo(ItemStack stack, LivingEntity wielder, int rounds) {
        if (!ready || !isFirearm(stack)) return;
        try {
            setAmmoCount.invoke(data(stack, wielder), Math.max(0, rounds));
        } catch (Throwable ignored) {
        }
    }

    /** Magazine size, or 0 when unknown. */
    public static int maxAmmo(ItemStack stack, LivingEntity wielder) {
        if (!ready || !isFirearm(stack)) return 0;
        try {
            return (int) getMaxAmmo.invoke(data(stack, wielder));
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Ammo the gun is set to use (for example {@code cgs:round_revolver}), or null. */
    public static ResourceLocation currentAmmoId(ItemStack stack, LivingEntity wielder) {
        if (!ready || !isFirearm(stack)) return null;
        try {
            Object holder = getCurrentAmmo.invoke(data(stack, wielder));
            return holder == null ? null : (ResourceLocation) holderId.invoke(holder);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Ticks between shots, or 20 when unknown. */
    public static int rate(ItemStack stack, LivingEntity wielder) {
        if (!ready || !isFirearm(stack)) return 20;
        try {
            return Math.max(1, (int) getRate.invoke(data(stack, wielder)));
        } catch (Throwable t) {
            return 20;
        }
    }

    /** Spread in degrees, or 0. */
    public static float spread(ItemStack stack, LivingEntity wielder) {
        if (!ready || !isFirearm(stack)) return 0;
        try {
            return (float) getSpread.invoke(data(stack, wielder));
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Reload time in ticks (per round for per-cartridge guns), or 40 when unknown. */
    public static int reloadTime(ItemStack stack, LivingEntity wielder) {
        if (!ready || !isFirearm(stack)) return 40;
        try {
            return Math.max(1, (int) getReloadTime.invoke(data(stack, wielder)));
        } catch (Throwable t) {
            return 40;
        }
    }

    /** True for guns that load one round at a time (the nailgun). */
    public static boolean perCartridge(ItemStack stack, LivingEntity wielder) {
        if (!ready || !isFirearm(stack)) return false;
        try {
            Object type = getLoadingType.invoke(data(stack, wielder));
            return type != null && type.toString().toUpperCase(java.util.Locale.ROOT).contains("CARTRIDGE");
        } catch (Throwable t) {
            return false;
        }
    }

    /** Total damage of one shot with that ammo (NTGL splits it between the pellets), or 0. */
    public static float shotDamage(ItemStack stack, LivingEntity wielder, ResourceLocation ammo) {
        if (!ready || ammo == null || !isFirearm(stack)) return 0;
        try {
            return (float) getProjectileDamage.invoke(ammo, data(stack, wielder));
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Range of a shot in blocks (speed × life of the ammo, at most {@code cap}) and whether its damage fades with the
     * distance travelled. Unknown ammo: {@code cap} and no fading.
     */
    public record Ballistics(double range, boolean fades) {}

    public static Ballistics ballistics(ItemStack stack, LivingEntity wielder, ResourceLocation ammo, double cap) {
        if (!ready || ammo == null || !isFirearm(stack)) return new Ballistics(cap, false);
        try {
            Object config = getAmmoConfig.invoke(ammo, data(stack, wielder));
            if (config == null) return new Ballistics(cap, false);
            double range = (float) ammoSpeed.invoke(config) * (int) ammoLife.invoke(config);
            return new Ballistics(Math.max(2, Math.min(cap, range)), (boolean) ammoReduceOverLife.invoke(config));
        } catch (Throwable t) {
            return new Ballistics(cap, false);
        }
    }

    // ------------------------------------------------------------------ reloading (server)

    /** Reload tracker of an entity (an opaque object; a new one starts with each reload), or null when not reloading. */
    public static Object reloadTracker(LivingEntity entity) {
        if (!ready || reloadTrackers == null) return null;
        try {
            return reloadTrackers.get(entity);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Ticks left on the tracker's current step (the whole reload, or the current round), or -1. */
    public static int reloadTicks(Object tracker) {
        if (tracker == null) return -1;
        try {
            return reloadTickField.getInt(tracker);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    public static void setReloadTicks(Object tracker, int ticks) {
        if (tracker == null) return;
        try {
            reloadTickField.setInt(tracker, Math.max(0, ticks));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    /** Starts NTGL's reload of the gun in that hand (as if its reload key had been pressed). */
    public static void startReload(LivingEntity entity, InteractionHand hand) {
        if (!ready) return;
        try {
            startReloading.invoke(data(entity.getItemInHand(hand), entity), hand);
        } catch (Throwable ignored) {
        }
    }

    public static void stopReload(LivingEntity entity, InteractionHand hand) {
        if (!ready) return;
        try {
            stopReloading.invoke(entity, hand);
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ mixin helpers (WeaponModifierHelper)

    /** Wielder of an NTGL {@code WeaponData}, or null. */
    public static LivingEntity wielder(Object weaponData) {
        if (weaponData == null || wielderField == null) return null;
        try {
            return (LivingEntity) wielderField.get(weaponData);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Weapon of an NTGL {@code WeaponData}, or empty. */
    public static ItemStack weapon(Object weaponData) {
        if (weaponData == null || weaponField == null) return ItemStack.EMPTY;
        try {
            Object s = weaponField.get(weaponData);
            return s instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }
}
