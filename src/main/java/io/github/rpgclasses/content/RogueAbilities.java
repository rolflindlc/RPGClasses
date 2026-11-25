package io.github.rpgclasses.content;

import com.google.gson.JsonObject;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.registry.ModEntities;
import io.github.rpgclasses.server.AbilityService;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Habilidades del Pícaro: barra de Sigilo (invisibilidad verdadera) y Señuelo. El Cambiarrostros está en {@code rogue.FaceChanger}. */
public final class RogueAbilities {
    public static final String STEALTH = Ids.NS + ":stealth";
    public static final String DECOY = Ids.NS + ":decoy";

    /** Contadores guardados con el jugador (los lee el HUD): barra en milésimas y estado. */
    public static final String BAR_COUNTER = "stealth_bar";
    public static final String STATE_COUNTER = "stealth_state";
    public static final int STATE_OFF = 0, STATE_ACTIVE = 1, STATE_LOCKED = 2;

    private static final class State {
        double bar = 1.0;
        boolean active;
        boolean locked;
        boolean ownsInvisibility;
        /** Ataque cuerpo a cuerpo hecho desde el sigilo: se cobra al final del tick (tras calcular el crítico). */
        boolean pendingAttack;
        int lastSyncedBar = 1000;
        int lastSyncedState = STATE_OFF;
    }

    private static final Map<UUID, State> STATES = io.github.rpgclasses.server.SessionState.track(new HashMap<>());

    public static void register() {
        io.github.rpgclasses.rogue.FaceChanger.register();
    }

    /** ¿Está el jugador ahora mismo invisible por Sigilo? (lo usan el crítico seguro, el clon y el disfraz). */
    public static boolean isStealthed(ServerPlayer player) {
        State st = STATES.get(player.getUUID());
        return st != null && st.active;
    }

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> {
            State st = new State();
            PlayerClassData data = PlayerClassDataProvider.get(player);
            if (data != null && data.counters.containsKey(BAR_COUNTER)) {
                st.bar = Math.max(0, Math.min(1000, data.counters.get(BAR_COUNTER))) / 1000.0;
                st.lastSyncedBar = (int) Math.round(st.bar * 1000);
            }
            return st;
        });
    }

    // --- Barra de Sigilo ---

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        if (!AbilityService.has(player, STEALTH)) {
            State st = STATES.get(player.getUUID());
            if (st != null && st.active) endStealth(player, st);
            return;
        }
        State st = state(player);
        JsonObject p = AbilityService.params(STEALTH);
        if (st.pendingAttack) {
            st.pendingAttack = false;
            if (st.active) spend(player, st, AbilityService.number(p, "action_cost", 0.4));
        }
        double drain = AbilityService.number(p, "drain_per_second", 0.10) / 20.0 * RoguePerks.stealthDrainFactor(player);
        double regen = AbilityService.number(p, "regen_per_second", 0.08) / 20.0;
        double lockoutUntil = AbilityService.number(p, "lockout_until", 0.5);

        boolean wants = player.isShiftKeyDown() && !st.locked && st.bar > 0 && !player.isSpectator() && player.isAlive();
        if (wants) {
            if (!st.active) beginStealth(player, st);
            st.bar -= drain;
            if (st.bar <= 0) {
                st.bar = 0;
                st.locked = true;
                endStealth(player, st);
            } else {
                // Invisible de verdad, sin partículas ni icono.
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 12, 0, true, false, false));
                st.ownsInvisibility = true;
                hideEffectParticles(player);
            }
        } else {
            if (st.active) endStealth(player, st);
            st.bar = Math.min(1.0, st.bar + regen);
            if (st.locked && st.bar >= lockoutUntil) st.locked = false;
        }
        publish(player, st, false);
    }

    private static void beginStealth(ServerPlayer player, State st) {
        st.active = true;
        publish(player, st, true);
        if (AbilityService.has(player, DECOY) && AbilityService.remainingTicks(player, DECOY) <= 0) {
            spawnDecoy(player);
            AbilityService.setCooldown(player, DECOY, AbilityService.ticks(AbilityService.params(DECOY), "cooldown_seconds", 25));
            Net.syncPlayer(player);
        }
    }

    private static void endStealth(ServerPlayer player, State st) {
        st.active = false;
        if (st.ownsInvisibility) {
            MobEffectInstance e = player.getEffect(MobEffects.INVISIBILITY);
            if (e != null && e.getDuration() <= 12 && e.isAmbient()) player.removeEffect(MobEffects.INVISIBILITY);
            st.ownsInvisibility = false;
        }
        publish(player, st, true);
    }

    /** Accesor sincronizado del color de partículas de efectos (LivingEntity, id 10); se busca por id para no depender de nombres. */
    private static net.minecraft.network.syncher.EntityDataAccessor<Integer> effectColor;
    private static boolean effectColorSearched;

    /** Sin partículas de efectos mientras dura el sigilo (al salir, quitar la invisibilidad las recalcula). */
    @SuppressWarnings("unchecked")
    private static void hideEffectParticles(ServerPlayer player) {
        if (!effectColorSearched) {
            effectColorSearched = true;
            for (java.lang.reflect.Field f : net.minecraft.world.entity.LivingEntity.class.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())
                        || f.getType() != net.minecraft.network.syncher.EntityDataAccessor.class) continue;
                try {
                    f.setAccessible(true);
                    var accessor = (net.minecraft.network.syncher.EntityDataAccessor<?>) f.get(null);
                    if (accessor.getId() == 10 && accessor.getSerializer() == net.minecraft.network.syncher.EntityDataSerializers.INT) {
                        effectColor = (net.minecraft.network.syncher.EntityDataAccessor<Integer>) accessor;
                        break;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
        }
        if (effectColor != null && player.getEntityData().get(effectColor) != 0) player.getEntityData().set(effectColor, 0);
    }

    /** Guarda barra y estado con el jugador y avisa al cliente solo cuando el cambio se nota. */
    private static void publish(ServerPlayer player, State st, boolean force) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return;
        int bar = (int) Math.round(st.bar * 1000);
        int s = st.locked ? STATE_LOCKED : st.active ? STATE_ACTIVE : STATE_OFF;
        data.counters.put(BAR_COUNTER, bar);
        data.counters.put(STATE_COUNTER, s);
        if (force || s != st.lastSyncedState || Math.abs(bar - st.lastSyncedBar) >= 40) {
            st.lastSyncedBar = bar;
            st.lastSyncedState = s;
            Net.syncPlayer(player);
        }
    }

    /** Cualquier ataque cuerpo a cuerpo a una entidad desde el sigilo gasta barra, haga daño o no (rev. 2.7f). */
    @SubscribeEvent
    public void onMeleeAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer attacker) || attacker instanceof FakePlayer) return;
        State st = STATES.get(attacker.getUUID());
        if (st != null && st.active) st.pendingAttack = true;
    }

    /** Disparar desde el sigilo gasta barra al impactar; si se agota, bloqueo. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onAttack(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer attacker) || attacker instanceof FakePlayer) return;
        Entity direct = source.getDirectEntity();
        if (!(direct instanceof Projectile)) return;
        State st = STATES.get(attacker.getUUID());
        if (st == null || !st.active || event.getAmount() <= 0) return;
        spend(attacker, st, AbilityService.number(AbilityService.params(STEALTH), "action_cost", 0.4));
    }

    /** Gasto de barra por una acción (ataque o, más adelante, hechizo). */
    public static void spend(ServerPlayer player, double amount) {
        State st = STATES.get(player.getUUID());
        if (st != null && st.active) spend(player, st, amount);
    }

    private static void spend(ServerPlayer player, State st, double amount) {
        st.bar -= amount;
        if (st.bar <= 0) {
            st.bar = 0;
            st.locked = true;
            endStealth(player, st);
        } else {
            publish(player, st, false);
        }
    }

    /** Invisible de verdad: los mobs solo te ven prácticamente pegado a ellos. */
    @SubscribeEvent
    public void onVisibility(LivingEvent.LivingVisibilityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isStealthed(player)) event.modifyVisibility(0.05);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    // --- Señuelo ---

    private static void spawnDecoy(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        DecoyEntity decoy = ModEntities.DECOY.get().create(level);
        if (decoy == null) return;
        Vec3 pos = player.position();
        decoy.moveTo(pos.x, pos.y, pos.z, player.getYRot(), 0);
        decoy.setOwner(player.getUUID());
        decoy.setCustomName(player.getName());
        decoy.setScale(io.github.rpgclasses.content.race.RaceTraits.scale(player));
        // El clon lleva lo mismo que el jugador (armas en ambas manos y armadura); no suelta nada al morir.
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            decoy.setItemSlot(slot, player.getItemBySlot(slot).copy());
            decoy.setDropChance(slot, 0f);
        }
        level.addFreshEntity(decoy);
        // Los enemigos que iban a por el jugador pasan a ir a por el clon.
        AABB area = player.getBoundingBox().inflate(24);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area, m -> m.getTarget() == player)) {
            mob.setTarget(decoy);
        }
        level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 1, pos.z, 15, 0.3, 0.5, 0.3, 0.02);
    }


}
