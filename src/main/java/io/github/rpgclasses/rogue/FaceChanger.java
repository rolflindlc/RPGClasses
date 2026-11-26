package io.github.rpgclasses.rogue;

import com.google.gson.JsonObject;
import io.github.rpgclasses.content.RogueAbilities;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.network.DisguisePackets;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.DebugLog;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Cambiarrostros (subclase del Pícaro, rev. 2.20; sustituye al Caminante de Sombras).
 *
 * <ul>
 *   <li><b>Catalejo de Espía</b> (pasiva): mirando con un catalejo ves la vida del objetivo (y su armadura y efectos a
 *   partir de ciertos niveles), y el objetivo brilla solo para ti unos segundos. Mirar a un jugador o a un mob de una
 *   facción acumula estudio; al completarlo aprendes su rostro (los mobs por tipo, los jugadores uno a uno).</li>
 *   <li><b>Disfraz</b> (activa): la tecla abre la lista de rostros. Con un rostro de mob, su facción entera te ignora; con
 *   uno de jugador, los demás te ven con su skin y su nombre, y no sales en el Tab. Atacar te delata ante el golpeado y
 *   su facción cercana; los mobs que ya te perseguían al disfrazarte saben que eres un impostor (y avisan a su facción)
 *   hasta que pierden el agro. El agua, la lluvia, el fuego o entrar en sigilo lo rompen: huida (Velocidad e
 *   invisibilidad breves) y empieza la recarga.</li>
 * </ul>
 *
 * El disfraz es solo visual: la hitbox, el alcance y los rasgos siguen siendo los del jugador.
 */
public final class FaceChanger {
    public static final String DISGUISE = Ids.NS + ":disguise";
    public static final String SPY_GLASS = Ids.NS + ":spy_glass";

    enum Reason { WATER, FIRE, STEALTH, VOLUNTARY, SILENT }

    /** Disfraces puestos (rostro), por jugador. */
    private static final Map<UUID, CompoundTag> DISGUISED = io.github.rpgclasses.server.SessionState.track(new HashMap<>());
    /** Mobs que saben que el jugador es un impostor -> último tick en que lo tenían de objetivo. */
    private static final Map<UUID, Map<UUID, Long>> EXPOSED = io.github.rpgclasses.server.SessionState.track(new HashMap<>());
    /** Estudio con el catalejo: a quién se mira y cuándo se avisó por última vez. */
    private static final Map<UUID, Scope> SCOPES = io.github.rpgclasses.server.SessionState.track(new HashMap<>());

    private static final class Scope {
        int targetId = -1;
        long lastGlow;
    }

    /** Se está calculando un mensaje de muerte (lo pone DeathMessageMixin): ahí se usa el nombre del disfraz. */
    private static final ThreadLocal<Boolean> DEATH_MESSAGE = ThreadLocal.withInitial(() -> false);

    private FaceChanger() {}

    public static void register() {
        AbilityService.register(DISGUISE, FaceChanger::press);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new Events());
    }

    // ------------------------------------------------------------------ consultas

    public static boolean isDisguised(Entity e) {
        return e != null && DISGUISED.containsKey(e.getUUID());
    }

    public static boolean inDeathMessage() {
        return DEATH_MESSAGE.get();
    }

    public static void beginDeathMessage() {
        DEATH_MESSAGE.set(true);
    }

    public static void endDeathMessage() {
        DEATH_MESSAGE.set(false);
    }

    /** En un mensaje de muerte o de kill, el nombre del disfraz en lugar del real (null: el nombre normal). */
    public static Component deathMessageName(Entity e) {
        if (!DEATH_MESSAGE.get() || e == null || e.level().isClientSide) return null;
        CompoundTag face = DISGUISED.get(e.getUUID());
        return face == null ? null : Faces.name(face);
    }

    // ------------------------------------------------------------------ tecla

    /** Disfrazado: te lo quitas. Si no: lista de rostros (la elección llega con {@link #select}). La recarga la pone el fin del disfraz. */
    private static boolean press(ServerPlayer player, JsonObject p) {
        if (isDisguised(player)) {
            reveal(player, Reason.VOLUNTARY);
            return false;
        }
        if (!canWear(player)) {
            player.displayClientMessage(Component.translatable("msg.rites_and_paths.disguise.cannot"), true);
            return false;
        }
        PlayerClassData data = PlayerClassDataProvider.get(player);
        List<CompoundTag> faces = data == null ? List.of() : Faces.list(data, player.getUUID());
        if (faces.isEmpty()) {
            player.displayClientMessage(Component.translatable("msg.rites_and_paths.disguise.no_faces"), true);
            return false;
        }
        List<DisguisePackets.FaceList.Entry> entries = new ArrayList<>();
        for (CompoundTag f : faces) {
            boolean isPlayer = Faces.PLAYER.equals(f.getString("kind"));
            entries.add(new DisguisePackets.FaceList.Entry(f.getString("key"), isPlayer, isPlayer ? f.getString("name") : f.getString("type")));
        }
        Net.toPlayer(player, new DisguisePackets.FaceList(entries));
        return false;
    }

    /** Ni mojado (agua o lluvia), ni ardiendo, ni en sigilo. */
    private static boolean canWear(ServerPlayer player) {
        return !player.isInWaterRainOrBubble() && !player.isOnFire() && !RogueAbilities.isStealthed(player);
    }

    /** El cliente eligió un rostro de la lista. Todo se vuelve a validar. */
    public static void select(ServerPlayer player, String key) {
        if (!AbilityService.has(player, DISGUISE) || isDisguised(player) || !player.isAlive() || player.isSpectator()) return;
        if (AbilityService.remainingTicks(player, DISGUISE) > 0) return;
        if (!canWear(player)) {
            player.displayClientMessage(Component.translatable("msg.rites_and_paths.disguise.cannot"), true);
            return;
        }
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return;
        CompoundTag face = null;
        for (CompoundTag f : Faces.list(data, player.getUUID())) if (f.getString("key").equals(key)) face = f;
        if (face == null) return;
        face = face.copy();
        // Un rostro de jugador recoge la skin actual del copiado si está conectado.
        if (Faces.PLAYER.equals(face.getString("kind")) && face.hasUUID("uuid")) {
            ServerPlayer target = player.server.getPlayerList().getPlayer(face.getUUID("uuid"));
            if (target != null) {
                CompoundTag fresh = Faces.faceOf(target);
                face = fresh;
                Faces.known(data).put(key, fresh.copy());
            }
        }
        DISGUISED.put(player.getUUID(), face);
        broadcast(player.getUUID(), face);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 1, player.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1f, 0.8f);
        player.displayClientMessage(Component.translatable("msg.rites_and_paths.disguise.on", Faces.name(face)), true);
        DebugLog.event(player, "habilidad", "Disfraz puesto: {}", key);
        // Quien ya te perseguía sabe que eres un impostor, y si es de la facción del rostro avisa a la suya.
        Faces.Faction faction = Faces.factionOf(face);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48), m -> m.isAlive() && targets(m, player))) {
            expose(player, mob);
            if (faction != null && Faces.isMember(faction, mob)) alert(player, mob, faction);
        }
    }

    /** Fin del disfraz: huida, recarga y aviso a todos. */
    static void reveal(ServerPlayer player, Reason reason) {
        CompoundTag face = DISGUISED.remove(player.getUUID());
        EXPOSED.remove(player.getUUID());
        if (face == null) return;
        broadcast(player.getUUID(), null);
        JsonObject p = AbilityService.params(DISGUISE);
        AbilityService.setCooldown(player, DISGUISE, AbilityService.levelTicks(player, p, "cooldown_seconds", 30));
        Net.syncPlayer(player);
        DebugLog.event(player, "habilidad", "Disfraz roto ({})", reason);
        if (reason == Reason.SILENT || !player.isAlive()) return;
        int speedLevel = (int) Math.round(AbilityService.number(p, "escape_speed_level", 2));
        if (speedLevel > 0) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                AbilityService.ticks(p, "escape_speed_seconds", 4), speedLevel - 1, false, false, true));
        int invis = AbilityService.ticks(p, "escape_invisibility_seconds", 1);
        if (invis > 0) player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, invis, 0, false, false, true));
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), 25, 0.3, 0.6, 0.3, 0.03);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.9f, 1.2f);
        String msg = switch (reason) {
            case WATER -> "water";
            case FIRE -> "fire";
            case STEALTH -> "stealth";
            default -> "voluntary";
        };
        player.displayClientMessage(Component.translatable("msg.rites_and_paths.disguise.broken." + msg), true);
    }

    // ------------------------------------------------------------------ mobs engañados y delatados

    private static boolean targets(Mob mob, ServerPlayer player) {
        if (mob.getTarget() == player) return true;
        var brain = mob.getBrain();
        return brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == player;
    }

    /** ¿Ignora este mob al jugador por su disfraz? */
    public static boolean fools(ServerPlayer player, LivingEntity mob) {
        CompoundTag face = DISGUISED.get(player.getUUID());
        if (face == null) return false;
        Faces.Faction faction = Faces.factionOf(face);
        if (faction == null || !Faces.isMember(faction, mob)) return false;
        Map<UUID, Long> exposed = EXPOSED.get(player.getUUID());
        return exposed == null || !exposed.containsKey(mob.getUUID());
    }

    private static void expose(ServerPlayer player, Mob mob) {
        EXPOSED.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(mob.getUUID(), player.level().getGameTime());
    }

    /** Los de la facción a {@code alert_radius} bloques de {@code from} descubren al impostor (como los piglins zombis). */
    private static void alert(ServerPlayer player, Mob from, Faces.Faction faction) {
        double r = AbilityService.number(AbilityService.params(DISGUISE), "alert_radius", 16);
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, from.getBoundingBox().inflate(r),
                m -> m.isAlive() && m != from && Faces.isMember(faction, m))) {
            boolean known = EXPOSED.getOrDefault(player.getUUID(), Map.of()).containsKey(mob.getUUID());
            expose(player, mob);
            if (!known && !targets(mob, player)) {
                mob.setTarget(player);
                mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, player);
            }
        }
    }

    private static void forget(Mob mob, ServerPlayer player) {
        if (mob.getTarget() == player) mob.setTarget(null);
        var brain = mob.getBrain();
        if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == player) brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        if (brain.hasMemoryValue(MemoryModuleType.ANGRY_AT)
                && player.getUUID().equals(brain.getMemory(MemoryModuleType.ANGRY_AT).orElse(null))) brain.eraseMemory(MemoryModuleType.ANGRY_AT);
    }

    /** Cada medio segundo: la facción deja de ir a por el disfrazado y los delatados que perdieron el agro lo olvidan. */
    private static void sweep(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Map<UUID, Long> exposed = EXPOSED.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48), m -> m.isAlive() && targets(m, player))) {
            if (exposed.containsKey(mob.getUUID())) exposed.put(mob.getUUID(), now);
            else if (fools(player, mob)) forget(mob, player);
        }
        long forgetTicks = AbilityService.ticks(AbilityService.params(DISGUISE), "forget_seconds", 3);
        for (Iterator<Map.Entry<UUID, Long>> it = exposed.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            Entity mob = level.getEntity(e.getKey());
            if (mob == null || !mob.isAlive() || now - e.getValue() > forgetTicks) it.remove();
        }
    }

    // ------------------------------------------------------------------ catalejo

    private static void scope(ServerPlayer player) {
        Scope scope = SCOPES.computeIfAbsent(player.getUUID(), k -> new Scope());
        LivingEntity target = player.isScoping() ? lookedAt(player) : null;
        if (target == null) {
            scope.targetId = -1;
            return;
        }
        JsonObject p = AbilityService.params(SPY_GLASS);
        long now = player.level().getGameTime();
        // Marca: el objetivo brilla solo para ti; se renueva cada segundo mientras lo miras.
        if (target.getId() != scope.targetId || now - scope.lastGlow >= 20) {
            scope.lastGlow = now;
            Net.toPlayer(player, new io.github.rpgclasses.network.DruidPackets.Glow(new int[] {target.getId()},
                    AbilityService.levelTicks(player, p, "mark_seconds", 3)));
        }
        scope.targetId = target.getId();
        Component studied = study(player, target, p);
        if (now % 10 != 0) return;
        List<Component> parts = new ArrayList<>(info(player, target, p));
        if (studied != null) parts.add(studied);
        MutableComponent line = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) line.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(parts.get(i));
        }
        player.displayClientMessage(line, true);
    }

    private static LivingEntity lookedAt(ServerPlayer player) {
        return lookedAt(player, AbilityService.number(AbilityService.params(SPY_GLASS), "study_range", 64));
    }

    /** Entidad viva a la que apunta la mira, con línea de visión (la usa también la marca del Francotirador). */
    public static LivingEntity lookedAt(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1f);
        Vec3 end = eye.add(look.scale(range));
        HitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        AABB box = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, box,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player && !e.isInvisibleTo(player));
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
    }

    /** Vida (siempre), armadura y efectos según el nivel de subclase. */
    private static List<Component> info(ServerPlayer player, LivingEntity target, JsonObject p) {
        int level = Math.max(1, AbilityService.subclassLevel(player));
        List<Component> out = new ArrayList<>();
        CompoundTag face = DISGUISED.get(target.getUUID());
        out.add((face != null ? Faces.name(face) : target.getDisplayName()).copy().withStyle(ChatFormatting.WHITE));
        out.add(Component.translatable("msg.rites_and_paths.spyglass.health", fmt(target.getHealth()), fmt(target.getMaxHealth()))
                .withStyle(ChatFormatting.RED));
        if (level >= AbilityService.number(p, "armor_from_level", 4)) {
            out.add(Component.translatable("msg.rites_and_paths.spyglass.armor", fmt(target.getArmorValue())).withStyle(ChatFormatting.GRAY));
        }
        if (level >= AbilityService.number(p, "effects_from_level", 7)) {
            MutableComponent fx = Component.empty();
            int n = 0;
            for (MobEffectInstance e : target.getActiveEffects()) {
                if (n++ > 0) fx.append(", ");
                fx.append(Component.translatable(e.getDescriptionId()));
                if (e.getAmplifier() > 0) fx.append(" ").append(Component.translatable("potion.potency." + e.getAmplifier()));
            }
            out.add(n == 0 ? Component.translatable("msg.rites_and_paths.spyglass.no_effects").withStyle(ChatFormatting.DARK_AQUA)
                    : fx.withStyle(ChatFormatting.AQUA));
        }
        return out;
    }

    private static String fmt(double v) {
        return Math.abs(v - Math.round(v)) < 0.05 ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }

    /** Un tick de estudio; devuelve la parte del mensaje sobre el rostro (null si no se puede copiar). */
    private static Component study(ServerPlayer player, LivingEntity target, JsonObject p) {
        // A un disfrazado no se le puede estudiar: lo que ves es el disfraz, no su rostro.
        if (!Faces.copyable(target, player) || isDisguised(target)) return null;
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return null;
        String key = Faces.key(target);
        Component name = Faces.name(Faces.faceOf(target));
        if (Faces.known(data).contains(key)) {
            return Component.translatable("msg.rites_and_paths.spyglass.known", name).withStyle(ChatFormatting.GREEN);
        }
        // Jugadores: 90 s; mobs (aldeanos, illagers, piglins…): 45 s.
        int total = Math.max(1, target instanceof net.minecraft.world.entity.player.Player
                ? AbilityService.ticks(p, "study_seconds", 90)
                : AbilityService.ticks(p, "mob_study_seconds", 45));
        net.minecraft.nbt.CompoundTag progress = Faces.progress(data);
        int done = progress.getInt(key) + 1;
        if (done >= total) {
            progress.remove(key);
            Faces.known(data).put(key, Faces.faceOf(target));
            player.displayClientMessage(Component.translatable("msg.rites_and_paths.spyglass.learned", name).withStyle(ChatFormatting.GOLD), true);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.6f);
            DebugLog.event(player, "habilidad", "Rostro aprendido: {}", key);
            return Component.translatable("msg.rites_and_paths.spyglass.known", name).withStyle(ChatFormatting.GREEN);
        }
        progress.putInt(key, done);
        return Component.translatable("msg.rites_and_paths.spyglass.study", name, done * 100 / total).withStyle(ChatFormatting.YELLOW);
    }

    // ------------------------------------------------------------------ red

    private static void broadcast(UUID player, CompoundTag face) {
        Net.CHANNEL.send(PacketDistributor.ALL.noArg(), new DisguisePackets.Sync(player, face == null ? new CompoundTag() : face));
    }

    /** Eventos del bus de Forge (instancia aparte para no mezclarlos con los métodos estáticos). */
    public static final class Events {
        @SubscribeEvent
        public void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player instanceof FakePlayer) return;
            if (isDisguised(player)) {
                if (!player.isAlive() || !AbilityService.has(player, DISGUISE) || player.isSpectator()) reveal(player, Reason.SILENT);
                else if (player.isInWaterRainOrBubble()) reveal(player, Reason.WATER);
                else if (player.isOnFire() || player.isInLava()) reveal(player, Reason.FIRE);
                else if (RogueAbilities.isStealthed(player)) reveal(player, Reason.STEALTH);
                else if (player.tickCount % 10 == 0) sweep(player);
            }
            if (player.isAlive() && AbilityService.has(player, SPY_GLASS)) scope(player);
            else SCOPES.remove(player.getUUID());
        }

        /** La facción del rostro no elige como objetivo al disfrazado (salvo los que ya lo descubrieron). */
        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onTarget(LivingChangeTargetEvent event) {
            if (event.getNewTarget() instanceof ServerPlayer player && isDisguised(player) && fools(player, event.getEntity())) {
                event.setCanceled(true);
            }
        }

        /** Atacar no rompe el disfraz, pero te delata ante el golpeado y, si es de la facción, ante los suyos cercanos. */
        @SubscribeEvent(priority = EventPriority.LOW)
        public void onHurt(LivingHurtEvent event) {
            if (!(event.getSource().getEntity() instanceof ServerPlayer player) || !isDisguised(player)) return;
            if (!(event.getEntity() instanceof Mob victim)) return;
            expose(player, victim);
            Faces.Faction faction = Faces.factionOf(DISGUISED.get(player.getUUID()));
            if (faction != null && Faces.isMember(faction, victim)) alert(player, victim, faction);
        }

        @SubscribeEvent
        public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            for (Map.Entry<UUID, CompoundTag> e : DISGUISED.entrySet()) {
                Net.toPlayer(player, new DisguisePackets.Sync(e.getKey(), e.getValue()));
            }
        }

        @SubscribeEvent
        public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) reveal(player, Reason.SILENT);
            SCOPES.remove(event.getEntity().getUUID());
        }

        @SubscribeEvent
        public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) reveal(player, Reason.SILENT);
        }

        @SubscribeEvent
        public void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            EXPOSED.remove(event.getEntity().getUUID());
        }
    }
}
