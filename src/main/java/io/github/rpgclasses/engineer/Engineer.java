package io.github.rpgclasses.engineer;

import com.google.gson.JsonObject;
import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.compat.ntgl.NtglCompat;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.MechanicService;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Ingeniero (adenda del Ingeniero): armero de armas de fuego de NTGL / Create: Gunsmithing. Aquí están los ids, el estado
 * por jugador (solo en memoria: recarga, racha, balas especiales, cargas…) y el tic que lo mueve. Las piezas: recarga
 * activa ({@link ActiveReload}), disparos ({@link Shots}), daño ({@link EngineerCombat}), activas
 * ({@link EngineerAbilities}), Cartuchera ({@link Holster}) y Artificiero ({@link Artificer}).
 */
public final class Engineer {
    public static final String CLASS_ID = Ids.NS + ":engineer";
    public static final String GUNSLINGER = Ids.NS + ":gunslinger";
    public static final String ARTILLERIST = Ids.NS + ":artillerist";
    public static final String ARTIFICER = Ids.NS + ":artificer";

    // Mecánicas (ramas, perks y subclases).
    static final String IMPROVISED = "engineer_improvised", MOVING = "engineer_moving_reload", STREAK = "engineer_streak",
            HOLSTER = "engineer_holster", MARKSMAN = "engineer_marksman", RUSTED = "engineer_rusted_ammo",
            ARCANE = "engineer_arcane_ammo", RETREAT = "engineer_tactical_retreat", DASH = "engineer_reload_dash",
            FRAGILE = "engineer_fragile_streak", STEADY = "engineer_steady_streak", BANDOLIER = "engineer_rotating_bandolier",
            THROWING = "engineer_throwing_gun", HEADHUNTER = "engineer_headhunter", SUPPRESSION = "engineer_suppression",
            QUICK_HAND = "gunslinger_quick_hand", DUELIST = "gunslinger_duelist", HOT_BARREL = "artillerist_hot_barrel",
            IRON_ARM = "artillerist_iron_arm", GADGETS = "artificer_gadgets";

    // Habilidades.
    static final String ACTIVE_RELOAD = Ids.NS + ":active_reload", ARMORER = Ids.NS + ":armorer",
            RIFLE_BUTT = Ids.NS + ":rifle_butt", SUPPRESSING_FIRE = Ids.NS + ":suppressing_fire",
            LAST_MAGAZINE = Ids.NS + ":last_magazine", FAN = Ids.NS + ":fan_the_hammer", DEAD_EYE = Ids.NS + ":dead_eye",
            BOMBARDMENT = Ids.NS + ":bombardment", SIEGE = Ids.NS + ":siege_mode", TURRET = Ids.NS + ":deploy_turret",
            MINE = Ids.NS + ":throw_mine", TRENCH = Ids.NS + ":dig_trench";

    public static final TagKey<Item> AMMO = TagKey.create(Registries.ITEM, RpgClasses.id("firearm_ammo"));
    public static final TagKey<Item> LIGHT = TagKey.create(Registries.ITEM, RpgClasses.id("firearms/light"));
    public static final TagKey<Item> HEAVY = TagKey.create(Registries.ITEM, RpgClasses.id("firearms/heavy"));
    public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE,
            new net.minecraft.resources.ResourceLocation("forge", "bosses"));

    private static final UUID SPEED_ID = UUID.fromString("7c1f4a52-6f0e-4c2b-9d8e-3b2a1e5f6a01");
    private static final UUID ANCHOR_ID = UUID.fromString("7c1f4a52-6f0e-4c2b-9d8e-3b2a1e5f6a02");
    private static final UUID STEADY_ID = UUID.fromString("7c1f4a52-6f0e-4c2b-9d8e-3b2a1e5f6a03");

    /** Estado en memoria de un Ingeniero (se pierde al desconectar: son efectos de segundos). */
    static final class State {
        // Recarga activa.
        Object tracker;
        long reloadStart;
        int reloadTotal;
        boolean reloadResolved;
        boolean autoComplete;
        boolean jamPending;
        int ammoAtStart;
        long reloadEndedAt = Long.MIN_VALUE / 2;
        long perfectUntil;
        int streak;
        long lastShot = Long.MIN_VALUE / 2;
        long burstUntil;
        double burstSpeed;
        long dashReadyAt;
        boolean retreating;
        // Munición Improvisada.
        int nextSpecial = -1;
        int specialLeft;
        int loadedSpecial = -1;
        int firingSpecial = -1;
        // Activas.
        long suppressUntil, lastMagUntil, siegeUntil;
        // Artillero.
        long heatStart = -1, lastHeavyShot = Long.MIN_VALUE / 2;
        // Pistolero.
        UUID dueled;
        long duelKillUntil;
        long deadEyeUntil;
        final LinkedHashSet<Integer> marks = new LinkedHashSet<>();
        int fanLeft;
        long fanNext;
        // Artificiero: cargas gastadas que se recargan de una en una (la primera acaba en chargeReadyAt).
        int spent;
        long chargeReadyAt;
        // Bombardeo: obuses en camino {x, y, z, tic de impacto}.
        final java.util.List<double[]> shells = new java.util.ArrayList<>();
        // Cartuchera: tics acumulados por ranura de la barra.
        final int[] holster = new int[9];
        // Sincronización con el cliente.
        float[] factors = {1, 1, 1, 0};
        String synced = "";
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    static State state(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    private Engineer() {
    }

    // ------------------------------------------------------------------ consultas

    public static boolean is(ServerPlayer p) {
        if (p == null || p instanceof FakePlayer) return false;
        PlayerClassData data = PlayerClassDataProvider.get(p);
        return data != null && CLASS_ID.equals(data.state.classId);
    }

    static boolean sub(ServerPlayer p, String subclass) {
        if (!is(p)) return false;
        PlayerClassData data = PlayerClassDataProvider.get(p);
        return data != null && subclass.equals(data.state.subclassId) && data.state.subclassLevel > 0;
    }

    static int subLevel(ServerPlayer p) {
        PlayerClassData data = PlayerClassDataProvider.get(p);
        return data == null ? 0 : data.state.subclassLevel;
    }

    static double mech(ServerPlayer p, String id, String key, double fallback) {
        return MechanicService.number(p, id, key, fallback);
    }

    static boolean has(ServerPlayer p, String mechanic) {
        return MechanicService.has(p, mechanic);
    }

    static JsonObject ability(String id) {
        return AbilityService.params(id);
    }

    static double a(String ability, String key, double fallback) {
        return AbilityService.number(AbilityService.params(ability), key, fallback);
    }

    static boolean light(ItemStack stack) {
        return NtglCompat.isFirearm(stack) && stack.is(LIGHT);
    }

    static boolean heavy(ItemStack stack) {
        return NtglCompat.isFirearm(stack) && stack.is(HEAVY);
    }

    static boolean boss(LivingEntity e) {
        return e.getType().is(BOSSES);
    }

    /** Factores que lee el mixin de NTGL en el servidor: cadencia, dispersión, recarga y cambio de arma instantáneo. */
    static float[] serverFactors(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s == null || !is(p) ? null : s.factors;
    }

    // ------------------------------------------------------------------ registro y tic

    public static void register(IEventBus modBus) {
        EngineerRegistry.register(modBus);
        EngineerHooks.server = p -> p instanceof ServerPlayer sp ? serverFactors(sp) : null;
        NtglCompat.onFirePre = Shots::onFirePre;
        NtglCompat.onFirePost = Shots::onFirePost;
        NtglCompat.onSpawn = Shots::onSpawn;
        MinecraftForge.EVENT_BUS.addListener(Engineer::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(Engineer::onLogout);
        MinecraftForge.EVENT_BUS.register(new EngineerCombat());
        MinecraftForge.EVENT_BUS.register(new Artificer());
        EngineerAbilities.register();
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Holster.returnAll(p);
        STATES.remove(p.getUUID());
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.level().isClientSide) return;
        if (!is(p)) {
            if (STATES.containsKey(p.getUUID())) {
                clearModifiers(p);
                STATES.remove(p.getUUID());
                EngineerPackets.sync(p, null);
            }
            return;
        }
        State s = state(p);
        long now = p.level().getGameTime();
        ActiveReload.tick(p, s, now);
        Holster.tick(p, s, now);
        EngineerAbilities.tick(p, s, now);
        Shots.tick(p, s, now);
        Artificer.tick(p, s, now);
        updateFactors(p, s, now);
        updateModifiers(p, s, now);
        EngineerPackets.sync(p, s);
    }

    private static void updateFactors(ServerPlayer p, State s, long now) {
        ItemStack hand = p.getMainHandItem();
        double rate = 1;
        if (light(hand) && sub(p, GUNSLINGER)) rate += mech(p, QUICK_HAND, "rate", 0);
        if (heavy(hand) && sub(p, ARTILLERIST) && s.heatStart >= 0) {
            double perSecond = mech(p, HOT_BARREL, "rate_per_second", .02);
            rate += Math.min(mech(p, HOT_BARREL, "cap", 0), perSecond * (now - s.heatStart) / 20.0);
        }
        if (now < s.suppressUntil) rate += a(SUPPRESSING_FIRE, "rate", .25);
        if (now < s.siegeUntil) rate += a(SIEGE, "rate", .40);
        double spread = Math.max(0.05, 1 + mech(p, MARKSMAN, "spread", 0));
        double reload = 1 + (has(p, DASH) ? mech(p, DASH, "slow_reload", .10) : 0);
        s.factors = new float[] {(float) rate, (float) spread, (float) reload, sub(p, GUNSLINGER) ? 1 : 0};
    }

    /** Velocidad (Recarga en Marcha, acelerones, Duelista, Brazo de Hierro), anclaje del Modo Asedio y resistencia al empuje. */
    private static void updateModifiers(ServerPlayer p, State s, long now) {
        double speed = 0;
        if (s.tracker != null) {
            speed += mech(p, MOVING, "reload_speed", 0);
            if (s.retreating) speed += mech(p, RETREAT, "speed", .30);
        }
        if (now < s.burstUntil) speed += s.burstSpeed;
        if (now < s.duelKillUntil) speed += mech(p, DUELIST, "kill_speed", .20);
        boolean heavyInHand = heavy(p.getMainHandItem()) && sub(p, ARTILLERIST);
        if (heavyInHand) speed += mech(p, IRON_ARM, "speed", 0);
        set(p.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID, "engineer speed", speed, AttributeModifier.Operation.MULTIPLY_BASE);
        set(p.getAttribute(Attributes.MOVEMENT_SPEED), ANCHOR_ID, "siege anchor", now < s.siegeUntil ? -1 : 0,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
        double steady = (heavyInHand ? mech(p, IRON_ARM, "knockback_resistance", 0) : 0) + (now < s.siegeUntil ? 1 : 0);
        set(p.getAttribute(Attributes.KNOCKBACK_RESISTANCE), STEADY_ID, "engineer steady", Math.min(1, steady),
                AttributeModifier.Operation.ADDITION);
    }

    private static void clearModifiers(ServerPlayer p) {
        set(p.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID, "", 0, AttributeModifier.Operation.MULTIPLY_BASE);
        set(p.getAttribute(Attributes.MOVEMENT_SPEED), ANCHOR_ID, "", 0, AttributeModifier.Operation.MULTIPLY_TOTAL);
        set(p.getAttribute(Attributes.KNOCKBACK_RESISTANCE), STEADY_ID, "", 0, AttributeModifier.Operation.ADDITION);
    }

    private static void set(AttributeInstance attribute, UUID id, String name, double amount, AttributeModifier.Operation op) {
        if (attribute == null) return;
        AttributeModifier current = attribute.getModifier(id);
        if (current != null && Math.abs(current.getAmount() - amount) < 1e-6) return;
        if (current != null) attribute.removeModifier(id);
        if (Math.abs(amount) > 1e-6) attribute.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }

    // ------------------------------------------------------------------ rasgo Armero

    /** Rasgo Armero: +50 % de munición al fabricarla a mano (lo llama CraftTraits al recoger el resultado). */
    public static void onCrafted(ServerPlayer p, ItemStack stack) {
        if (!is(p) || !stack.is(AMMO) || !AbilityService.has(p, ARMORER)) return;
        double bonus = stack.getCount() * a(ARMORER, "ammo_bonus", .5);
        PlayerClassData data = PlayerClassDataProvider.get(p);
        if (data == null) return;
        // Las fracciones se acumulan entre fabricaciones (3 cartuchos dan 1,5: un extra ahora y medio guardado).
        String key = "engineer_ammo_carry";
        double carry = data.counters.getOrDefault(key, 0) / 1000.0 + bonus;
        int extra = (int) Math.floor(carry);
        data.counters.put(key, (int) Math.round((carry - extra) * 1000));
        if (extra > 0) {
            ItemStack give = stack.copy();
            give.setCount(extra);
            net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(p, give);
        }
    }
}
