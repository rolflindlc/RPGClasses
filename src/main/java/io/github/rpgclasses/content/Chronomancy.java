package io.github.rpgclasses.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.value.LevelValue;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.Allies;
import io.github.rpgclasses.server.DebugLog;
import io.github.rpgclasses.server.EffectRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cronomante (subclase del Mago que sustituye al Evocador Caótico; adenda del Cronomante).
 *
 * <ul>
 *   <li><b>Retroceso Temporal</b> ({@link #REWIND}): cada {@code sample_interval_seconds} se guarda la posición, la vida y
 *       el maná del jugador. Al activarlo vuelve al estado de hace {@code window_seconds} (según el nivel de subclase) y
 *       recupera parte de la vida perdida y del maná gastado (nunca quita). Desde ciertos niveles borra los efectos
 *       negativos recibidos en la ventana y devuelve parte de la recarga de los conjuros lanzados en ella.</li>
 *   <li><b>Parada Temporal</b> ({@link #STOP}): esfera fija de {@code radius} bloques. Todo lo que no es aliado y está
 *       dentro, o entra mientras dura (proyectiles incluidos), queda paralizado hasta que acaba. Los jefes, como mucho
 *       {@code boss_factor} de la duración.</li>
 * </ul>
 *
 * El estado previo de cada entidad paralizada se guarda en sus datos persistentes ({@link #TAG}) para restaurarlo
 * siempre: al acabar, al morir el Cronomante, al recargar el chunk o al reiniciar el servidor.
 */
public final class Chronomancy {
    public static final String REWIND = Ids.NS + ":time_rewind";
    public static final String STOP = Ids.NS + ":time_stop";

    /** Datos persistentes de una entidad paralizada (estado previo y hasta cuándo). */
    public static final String TAG = "rpgclasses:time_frozen";
    /** Entidades que la Parada Temporal nunca paraliza. */
    public static final TagKey<EntityType<?>> IMMUNE = TagKey.create(Registries.ENTITY_TYPE,
            new net.minecraft.resources.ResourceLocation(Ids.NS, "time_stop_immune"));
    private static final UUID FREEZE_SPEED_ID = UUID.fromString("3c1f7a52-9d8e-4b61-a0f3-6e2b5c9d7a14");

    /** Puente con Iron's (lo rellena IronsCompat). Sin Iron's es null: no hay maná que guardar ni cobrar. */
    public interface ManaBridge {
        float mana(ServerPlayer player);

        float maxMana(ServerPlayer player);

        void setMana(ServerPlayer player, float mana);

        /** Quita {@code fraction} de la recarga restante de esos conjuros. */
        void refundCooldowns(ServerPlayer player, Collection<String> spellIds, double fraction);
    }

    public static ManaBridge mana;

    public static void register() {
        AbilityService.register(REWIND, Chronomancy::rewind);
        AbilityService.register(STOP, Chronomancy::timeStop);
    }

    // =====================================================================================================
    // Retroceso Temporal
    // =====================================================================================================

    private record Snap(long tick, ResourceKey<Level> dim, double x, double y, double z, float health, float mana) {}
    private record EffectStamp(MobEffect effect, long tick) {}
    private record CastStamp(String spellId, long tick) {}

    private static final class History {
        final ArrayDeque<Snap> snaps = new ArrayDeque<>();
        final ArrayDeque<EffectStamp> effects = new ArrayDeque<>();
        final ArrayDeque<CastStamp> casts = new ArrayDeque<>();

        void clear() {
            snaps.clear();
            effects.clear();
            casts.clear();
        }
    }

    private static final Map<UUID, History> HISTORY = new HashMap<>();

    /** Iron's avisa de cada conjuro lanzado (para devolver recargas en los niveles altos). */
    public static void recordCast(ServerPlayer player, String spellId) {
        History h = HISTORY.get(player.getUUID());
        if (h != null && spellId != null) h.casts.addLast(new CastStamp(spellId, player.level().getGameTime()));
    }

    @SubscribeEvent
    public void onEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        History h = HISTORY.get(player.getUUID());
        if (h != null) h.effects.addLast(new EffectStamp(event.getEffectInstance().getEffect(), player.level().getGameTime()));
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        JsonObject p = AbilityService.params(REWIND);
        int interval = Math.max(1, AbilityService.ticks(p, "sample_interval_seconds", 0.5));
        if (player.tickCount % interval != 0) return;
        if (!player.isAlive() || !AbilityService.has(player, REWIND)) {
            HISTORY.remove(player.getUUID());
            return;
        }
        History h = HISTORY.computeIfAbsent(player.getUUID(), id -> new History());
        long now = player.level().getGameTime();
        Snap last = h.snaps.peekLast();
        double maxJump = AbilityService.number(p, "max_jump_blocks", 64);
        if (last != null && (!last.dim().equals(player.level().dimension())
                || player.position().distanceToSqr(last.x(), last.y(), last.z()) > maxJump * maxJump)) {
            DebugLog.event(player, "Cronomante", "historial borrado (cambio de dimensión o teletransporte lejano)");
            h.clear();
        }
        h.snaps.addLast(snapshot(player, now));
        long keep = now - maxTicks(p, "window_seconds", 8) - interval * 2L;
        while (!h.snaps.isEmpty() && h.snaps.peekFirst().tick() < keep) h.snaps.pollFirst();
        while (!h.effects.isEmpty() && h.effects.peekFirst().tick() < keep) h.effects.pollFirst();
        while (!h.casts.isEmpty() && h.casts.peekFirst().tick() < keep) h.casts.pollFirst();

        // Eco del Retroceso: el propio Cronomante ve unas chispas donde volvería ahora mismo (si está listo).
        if (AbilityService.remainingTicks(player, REWIND) <= 0) {
            Snap target = target(h, now - AbilityService.levelTicks(player, p, "window_seconds", 3));
            if (target != null && target.dim().equals(player.level().dimension())
                    && player.position().distanceToSqr(target.x(), target.y(), target.z()) > 4) {
                player.serverLevel().sendParticles(player, ParticleTypes.REVERSE_PORTAL, true,
                        target.x(), target.y() + 1.0, target.z(), 4, 0.2, 0.5, 0.2, 0.0);
            }
        }
    }

    private static Snap snapshot(ServerPlayer player, long now) {
        float m = mana == null ? Float.NaN : mana.mana(player);
        return new Snap(now, player.level().dimension(), player.getX(), player.getY(), player.getZ(), player.getHealth(), m);
    }

    /** La instantánea más antigua dentro de la ventana (la que está "hace X segundos"). */
    private static Snap target(History h, long from) {
        for (Snap s : h.snaps) if (s.tick() >= from) return s;
        return h.snaps.peekLast();
    }

    private static boolean rewind(ServerPlayer player, JsonObject p) {
        History h = HISTORY.get(player.getUUID());
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Snap target = h == null || h.snaps.isEmpty() ? null : target(h, now - AbilityService.levelTicks(player, p, "window_seconds", 3));
        if (target == null || !target.dim().equals(level.dimension())) {
            if (h != null && target != null) h.clear();
            player.displayClientMessage(Component.translatable("msg.rpgclasses.time_rewind_empty"), true);
            return false;
        }
        Vec3 dest = safeSpot(level, player, new Vec3(target.x(), target.y(), target.z()),
                (int) Math.round(AbilityService.number(p, "safe_search_blocks", 3)));
        if (dest == null) {
            player.displayClientMessage(Component.translatable("msg.rpgclasses.time_rewind_blocked"), true);
            return false;
        }
        int subLevel = Math.max(1, AbilityService.subclassLevel(player));
        long from = target.tick();
        Vec3 origin = player.position();

        if (player.isPassenger()) player.stopRiding();
        player.ejectPassengers();
        player.connection.teleport(dest.x, dest.y, dest.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.fallDistance = 0; // anula el daño de caída acumulado

        float healthBefore = player.getHealth();
        float lost = target.health() - healthBefore;
        if (lost > 0) {
            double frac = AbilityService.levelNumber(player, p, "health_restore", 0.5);
            player.setHealth(Math.min(player.getMaxHealth(), healthBefore + (float) (lost * frac)));
        }
        float manaGain = 0;
        if (mana != null && !Float.isNaN(target.mana())) {
            float current = mana.mana(player);
            float spent = target.mana() - current;
            if (spent > 0) {
                manaGain = (float) (spent * AbilityService.levelNumber(player, p, "mana_restore", 0.25));
                mana.setMana(player, current + manaGain);
            }
        }
        int cleansed = 0;
        if (subLevel >= AbilityService.number(p, "cleanse_from_level", 4)) {
            Set<MobEffect> seen = new HashSet<>();
            for (EffectStamp e : h.effects) {
                if (e.tick() < from || !seen.add(e.effect())) continue;
                if (EffectRules.isNegative(e.effect()) && player.hasEffect(e.effect())) {
                    player.removeEffect(e.effect());
                    cleansed++;
                }
            }
        }
        int refunded = 0;
        if (mana != null && subLevel >= AbilityService.number(p, "spell_cooldown_from_level", 8)) {
            Set<String> ids = new LinkedHashSet<>();
            for (CastStamp c : h.casts) if (c.tick() >= from) ids.add(c.spellId());
            if (!ids.isEmpty()) {
                mana.refundCooldowns(player, ids, AbilityService.number(p, "spell_cooldown_refund", 0.5));
                refunded = ids.size();
            }
        }

        // Efectos: rastro de partículas del sitio actual al de destino y sonido de "rebobinado".
        Vec3 step = dest.subtract(origin);
        int points = (int) Math.min(40, Math.max(6, step.length() * 2));
        for (int i = 0; i <= points; i++) {
            Vec3 at = origin.add(step.scale(i / (double) points));
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1.0, at.z, 2, 0.15, 0.4, 0.15, 0.0);
        }
        level.sendParticles(ParticleTypes.END_ROD, dest.x, dest.y + 1.0, dest.z, 20, 0.3, 0.6, 0.3, 0.05);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 0.5f);
        level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 0.6f);

        DebugLog.event(player, "Cronomante", "Retroceso (nivel {}): {} s atrás, {} bloques; vida {} -> {}, maná +{}, {} efectos quitados, {} conjuros con recarga devuelta",
                subLevel, (now - from) / 20.0, Math.round(origin.distanceTo(dest) * 10) / 10.0, healthBefore, player.getHealth(),
                manaGain, cleansed, refunded);
        // Tras volver, el historial empieza de nuevo (no se puede encadenar hacia atrás).
        h.clear();
        h.snaps.addLast(snapshot(player, now));
        return true;
    }

    /** El sitio guardado si es seguro; si no, el hueco seguro más cercano a {@code search} bloques o menos. */
    private static Vec3 safeSpot(ServerLevel level, ServerPlayer player, Vec3 pos, int search) {
        if (safe(level, player, pos)) return pos;
        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -search; dx <= search; dx++) {
            for (int dy = -search; dy <= search; dy++) {
                for (int dz = -search; dz <= search; dz++) {
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d >= bestDist || d == 0) continue;
                    Vec3 c = pos.add(dx, dy, dz);
                    if (safe(level, player, c)) {
                        best = c;
                        bestDist = d;
                    }
                }
            }
        }
        return best;
    }

    private static boolean safe(ServerLevel level, Entity e, Vec3 pos) {
        if (pos.y < level.getMinBuildHeight() || pos.y > level.getMaxBuildHeight()) return false;
        AABB box = e.getBoundingBox().move(pos.subtract(e.position()));
        if (!level.getWorldBorder().isWithinBounds(box) || !level.noCollision(e, box)) return false;
        for (BlockPos bp : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY - 1.0e-3, box.maxZ))) {
            if (level.getFluidState(bp).is(FluidTags.LAVA)) return false;
        }
        return true;
    }

    /** Valor máximo (nivel 10) de un parámetro por nivel, en ticks. */
    private static int maxTicks(JsonObject p, String key, double fallbackSeconds) {
        JsonElement v = p.get(key);
        double seconds = fallbackSeconds;
        if (v != null && v.isJsonPrimitive()) seconds = v.getAsDouble();
        else if (v != null && v.isJsonObject()) {
            try {
                seconds = LevelValue.fromJson(v.getAsJsonObject(), 10).max();
            } catch (RuntimeException ignored) {
                // valor mal formado: se usa el de respaldo
            }
        }
        return (int) Math.round(seconds * 20);
    }

    // =====================================================================================================
    // Parada Temporal
    // =====================================================================================================

    private static final class Sphere {
        final int id;
        final UUID owner;
        final ResourceKey<Level> dim;
        final Vec3 center;
        final double radius;
        final long end;
        final int bossTicks;
        final Set<UUID> touched = new HashSet<>();

        Sphere(int id, UUID owner, ResourceKey<Level> dim, Vec3 center, double radius, long end, int bossTicks) {
            this.id = id;
            this.owner = owner;
            this.dim = dim;
            this.center = center;
            this.radius = radius;
            this.end = end;
            this.bossTicks = bossTicks;
        }
    }

    private record Frozen(ResourceKey<Level> dim, long until) {}

    private static final List<Sphere> SPHERES = new ArrayList<>();
    private static final Map<UUID, Frozen> FROZEN = new HashMap<>();
    private static int nextSphereId = 1;

    /** ¿Está paralizada por una Parada Temporal? (no puede atacar, moverse, usar objetos, conjuros ni habilidades) */
    public static boolean isFrozen(Entity e) {
        return e != null && FROZEN.containsKey(e.getUUID());
    }

    private static boolean timeStop(ServerPlayer player, JsonObject p) {
        ServerLevel level = player.serverLevel();
        int duration = Math.max(1, AbilityService.levelTicks(player, p, "duration_seconds", 0.5));
        double radius = AbilityService.number(p, "radius", 10);
        if (mana != null && !player.isCreative()) {
            float cost = (float) (mana.maxMana(player) * AbilityService.number(p, "mana_cost", 0.2));
            float current = mana.mana(player);
            if (current < cost) {
                player.displayClientMessage(Component.translatable("msg.rpgclasses.time_stop_mana"), true);
                return false;
            }
            mana.setMana(player, current - cost);
        }
        long now = level.getGameTime();
        Vec3 c = player.getBoundingBox().getCenter();
        int bossTicks = (int) Math.max(1, Math.round(duration * AbilityService.number(p, "boss_factor", 0.5)));
        Sphere s = new Sphere(nextSphereId++, player.getUUID(), level.dimension(), c, radius, now + duration, bossTicks);
        SPHERES.add(s);
        Net.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(c.x, c.y, c.z, radius + 64, level.dimension())),
                new Packets.TimeSphere(s.id, c.x, c.y, c.z, (float) radius, duration));
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.2f, 0.6f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.6f);
        int frozen = scan(level, s, now);
        DebugLog.event(player, "Cronomante", "Parada Temporal: radio {}, {} ticks (jefes {}), {} entidades paralizadas al empezar",
                radius, duration, bossTicks, frozen);
        return true;
    }

    /** Paraliza lo que está dentro de la esfera y aún no ha tocado. Devuelve cuántas entidades ha paralizado. */
    private static int scan(ServerLevel level, Sphere s, long now) {
        AABB box = new AABB(s.center, s.center).inflate(s.radius + 4);
        int count = 0;
        for (Entity e : level.getEntities((Entity) null, box, x -> x.isAlive() && !s.touched.contains(x.getUUID()))) {
            if (!inside(s, e) || !affects(level, s, e)) continue;
            s.touched.add(e.getUUID());
            long until = s.end;
            if (e instanceof LivingEntity living && io.github.rpgclasses.content.race.Races.isBoss(living)) {
                until = Math.min(s.end, now + s.bossTicks);
            }
            freeze(level, e, until, s.id, now);
            count++;
        }
        return count;
    }

    /** Dentro si su caja toca la esfera; los proyectiles, también si van a entrar en este tick. */
    private static boolean inside(Sphere s, Entity e) {
        double r2 = s.radius * s.radius;
        if (distanceSqrToBox(s.center, e.getBoundingBox()) <= r2) return true;
        if (e instanceof Projectile) {
            return distanceSqrToBox(s.center, e.getBoundingBox().move(e.getDeltaMovement())) <= r2;
        }
        return false;
    }

    private static double distanceSqrToBox(Vec3 p, AABB b) {
        double dx = Math.max(0, Math.max(b.minX - p.x, p.x - b.maxX));
        double dy = Math.max(0, Math.max(b.minY - p.y, p.y - b.maxY));
        double dz = Math.max(0, Math.max(b.minZ - p.z, p.z - b.maxZ));
        return dx * dx + dy * dy + dz * dz;
    }

    /** Todo lo que no es aliado del Cronomante: criaturas, jugadores y proyectiles. Él y sus aliados, nunca. */
    private static boolean affects(ServerLevel level, Sphere s, Entity e) {
        if (e.getUUID().equals(s.owner) || e.isSpectator() || e.getType().is(IMMUNE)) return false;
        MinecraftServer server = level.getServer();
        if (e instanceof Projectile projectile) {
            UUID owner = Allies.ownerOf(projectile);
            return owner == null || !(owner.equals(s.owner) || Allies.considers(server, s.owner, owner));
        }
        if (e instanceof ArmorStand) return false;
        return e instanceof LivingEntity living && Allies.isEnemyOf(server, s.owner, living);
    }

    private static void freeze(ServerLevel level, Entity e, long until, int sphereId, long now) {
        CompoundTag data = e.getPersistentData();
        if (data.contains(TAG)) { // ya paralizada por otra esfera: se alarga si hace falta
            CompoundTag t = data.getCompound(TAG);
            if (t.getLong("until") < until) {
                t.putLong("until", until);
                t.putInt("sphere", sphereId);
                data.put(TAG, t);
                FROZEN.put(e.getUUID(), new Frozen(level.dimension(), until));
                if (e instanceof ServerPlayer sp) Net.toPlayer(sp, new Packets.TimeFrozen((int) (until - now)));
            }
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putLong("until", until);
        t.putInt("sphere", sphereId);
        t.putDouble("px", e.getX());
        t.putDouble("py", e.getY());
        t.putDouble("pz", e.getZ());
        Vec3 v = e.getDeltaMovement();
        t.putDouble("vx", v.x);
        t.putDouble("vy", v.y);
        t.putDouble("vz", v.z);
        t.putBoolean("noGravity", e.isNoGravity());
        if (e instanceof Mob mob) {
            t.putBoolean("noAi", mob.isNoAi());
            mob.getNavigation().stop();
            mob.setNoAi(true);
        }
        if (e instanceof AbstractHurtingProjectile h) {
            t.putBoolean("power", true);
            t.putDouble("ax", h.xPower);
            t.putDouble("ay", h.yPower);
            t.putDouble("az", h.zPower);
            h.xPower = h.yPower = h.zPower = 0;
        }
        data.put(TAG, t);
        e.setNoGravity(true);
        e.setDeltaMovement(Vec3.ZERO);
        e.hurtMarked = true;
        if (e instanceof ServerPlayer sp) {
            AttributeInstance speed = sp.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null && speed.getModifier(FREEZE_SPEED_ID) == null) {
                speed.addTransientModifier(new AttributeModifier(FREEZE_SPEED_ID, "rpgclasses time stop", -1.0,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
            sp.stopUsingItem();
            Net.toPlayer(sp, new Packets.TimeFrozen((int) (until - now)));
        }
        FROZEN.put(e.getUUID(), new Frozen(level.dimension(), until));
    }

    /** Devuelve a la entidad su estado previo. Seguro de llamar aunque no esté paralizada. */
    public static void unfreeze(Entity e) {
        FROZEN.remove(e.getUUID());
        CompoundTag data = e.getPersistentData();
        if (!data.contains(TAG)) return;
        CompoundTag t = data.getCompound(TAG);
        data.remove(TAG);
        e.setNoGravity(t.getBoolean("noGravity"));
        if (e instanceof Mob mob && t.contains("noAi")) mob.setNoAi(t.getBoolean("noAi"));
        if (e instanceof AbstractHurtingProjectile h && t.getBoolean("power")) {
            h.xPower = t.getDouble("ax");
            h.yPower = t.getDouble("ay");
            h.zPower = t.getDouble("az");
        }
        if (e instanceof ServerPlayer sp) {
            AttributeInstance speed = sp.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) speed.removeModifier(FREEZE_SPEED_ID);
            Net.toPlayer(sp, new Packets.TimeFrozen(0));
        } else {
            // Los proyectiles (y los mobs) recuperan la velocidad que llevaban.
            e.setDeltaMovement(t.getDouble("vx"), t.getDouble("vy"), t.getDouble("vz"));
        }
        e.hurtMarked = true;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (!SPHERES.isEmpty()) {
            for (Iterator<Sphere> it = SPHERES.iterator(); it.hasNext(); ) {
                Sphere s = it.next();
                ServerLevel level = server.getLevel(s.dim);
                if (level == null || level.getGameTime() >= s.end) {
                    it.remove();
                    continue;
                }
                scan(level, s, level.getGameTime());
            }
        }
        if (FROZEN.isEmpty()) return;
        for (Map.Entry<UUID, Frozen> entry : new ArrayList<>(FROZEN.entrySet())) {
            ServerLevel level = server.getLevel(entry.getValue().dim());
            Entity e = level == null ? null : level.getEntity(entry.getKey());
            long now = level == null ? 0 : level.getGameTime();
            if (e == null) {
                // Descargada o desaparecida: se restaura sola al volver a cargarse (onJoin).
                if (level == null || now > entry.getValue().until() + 100) FROZEN.remove(entry.getKey());
                continue;
            }
            CompoundTag t = e.getPersistentData().getCompound(TAG);
            if (!e.isAlive() || !e.getPersistentData().contains(TAG) || now >= t.getLong("until")) {
                unfreeze(e);
                continue;
            }
            hold(e, t);
        }
    }

    /** Mantiene quieta a la entidad: misma posición y sin velocidad (ni aceleración, en las bolas de fuego). */
    private static void hold(Entity e, CompoundTag t) {
        double x = t.getDouble("px"), y = t.getDouble("py"), z = t.getDouble("pz");
        e.setDeltaMovement(Vec3.ZERO);
        if (e instanceof AbstractHurtingProjectile h) h.xPower = h.yPower = h.zPower = 0;
        if (e instanceof ServerPlayer sp) {
            if (sp.position().distanceToSqr(x, y, z) > 0.0625) sp.connection.teleport(x, y, z, sp.getYRot(), sp.getXRot());
            sp.fallDistance = 0;
        } else if (e.position().distanceToSqr(x, y, z) > 1.0e-6) {
            e.setPos(x, y, z);
        }
    }

    private static void endSpheresOf(MinecraftServer server, UUID owner) {
        for (Iterator<Sphere> it = SPHERES.iterator(); it.hasNext(); ) {
            Sphere s = it.next();
            if (!s.owner.equals(owner)) continue;
            it.remove();
            ServerLevel level = server.getLevel(s.dim);
            if (level == null) continue;
            for (UUID id : s.touched) {
                Entity e = level.getEntity(id);
                if (e != null && e.getPersistentData().getCompound(TAG).getInt("sphere") == s.id) unfreeze(e);
            }
            Net.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(s.center.x, s.center.y, s.center.z,
                    s.radius + 64, s.dim)), new Packets.TimeSphere(s.id, s.center.x, s.center.y, s.center.z, (float) s.radius, 0));
        }
    }

    /** Al volver a cargarse (chunk, reinicio, reconexión): si ya pasó su tiempo o es un jugador, se restaura. */
    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity e = event.getEntity();
        if (!e.getPersistentData().contains(TAG)) return;
        long until = e.getPersistentData().getCompound(TAG).getLong("until");
        if (e instanceof Player || event.getLevel().getGameTime() >= until) unfreeze(e);
        else FROZEN.put(e.getUUID(), new Frozen(event.getLevel().dimension(), until));
    }

    // --- Limpieza: muerte, desconexión y cambio de dimensión ---

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        Entity e = event.getEntity();
        if (e.level().isClientSide()) return;
        if (FROZEN.containsKey(e.getUUID())) unfreeze(e);
        if (e instanceof ServerPlayer player) {
            HISTORY.remove(player.getUUID());
            endSpheresOf(player.server, player.getUUID());
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        HISTORY.remove(player.getUUID());
        endSpheresOf(player.server, player.getUUID());
        unfreeze(player);
    }

    @SubscribeEvent
    public void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        History h = HISTORY.get(player.getUUID());
        if (h != null) h.clear();
        endSpheresOf(player.server, player.getUUID());
        unfreeze(player);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        History h = HISTORY.get(event.getEntity().getUUID());
        if (h != null) h.clear();
    }

    // --- Lo paralizado no puede hacer nada ---

    /** Los golpes de lo paralizado no hacen nada (los proyectiles que no están paralizados sí). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(LivingAttackEvent event) {
        if (FROZEN.isEmpty() || event.getEntity().level().isClientSide()) return;
        Entity direct = event.getSource().getDirectEntity(), source = event.getSource().getEntity();
        if (isFrozen(direct) || (isFrozen(source) && (direct == null || direct == source))) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onKnockback(LivingKnockBackEvent event) {
        if (isFrozen(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onImpact(ProjectileImpactEvent event) {
        if (isFrozen(event.getProjectile())) event.setImpactResult(ProjectileImpactEvent.ImpactResult.STOP_AT_CURRENT_NO_DAMAGE);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttackEntity(AttackEntityEvent event) {
        if (isFrozen(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isFrozen(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isFrozen(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isFrozen(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (isFrozen(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (isFrozen(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (isFrozen(event.getEntity())) event.setCanceled(true);
    }
}
