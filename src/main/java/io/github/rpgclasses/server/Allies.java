package io.github.rpgclasses.server;

import com.mojang.authlib.GameProfile;
import io.github.rpgclasses.config.ServerConfig;
import io.github.rpgclasses.content.DecoyEntity;
import io.github.rpgclasses.core.ally.AllyBook;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.scores.Team;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sistema de aliados. Cada jugador elige a quién considera aliado ({@code /aliados} o la pantalla de aliados);
 * el resto cuenta como enemigo.
 * <ul>
 *   <li><b>Perspectiva:</b> la relación es unilateral y se mira desde quien produce el efecto: los apoyos de A
 *       alcanzan a quien A tiene como aliado, y los efectos ofensivos de A no le afectan.</li>
 *   <li><b>Dueños:</b> una mascota, invocación, señuelo o proyectil cuenta como su dueño. Tus mascotas y las de
 *       tus aliados son aliadas tuyas.</li>
 *   <li><b>Vanilla y otros mods:</b> {@code Entity.isAlliedTo} sigue esta relación (mixin), así que la respetan
 *       el barrido de espada, las mascotas, las invocaciones y los conjuros de Iron's y los ataques de Epic Fight.</li>
 *   <li><b>Fuego amigo:</b> si está desactivado (por defecto), no puedes dañar a quien tienes como aliado.</li>
 *   <li>Opcional: un mismo equipo de scoreboard ({@code /team}) también cuenta como aliado.</li>
 * </ul>
 * Las entidades sin dueño jugador (mobs salvajes, aldeanos) siguen las reglas vanilla de cada efecto.
 */
public final class Allies {
    private static AllyStore cached;
    private static MinecraftServer cachedServer;

    private Allies() {}

    // ------------------------------------------------------------------ Datos

    public static AllyBook book(MinecraftServer server) {
        if (cachedServer != server || cached == null) {
            cached = AllyStore.get(server);
            cachedServer = server;
        }
        return cached.book;
    }

    private static void changed(MinecraftServer server) {
        book(server);
        cached.setDirty();
    }

    // ------------------------------------------------------------------ Dueños

    private static final String IRONS_SUMMON = "io.redspace.ironsspellbooks.entity.mobs.IMagicSummon";
    private static Class<?> summonClass;
    private static Method summonerMethod;
    private static boolean summonLookupDone;

    /** Jugador "dueño" de una entidad: ella misma si es jugador; si no, su dueño (mascota, invocación, señuelo, proyectil). */
    public static UUID ownerOf(Entity e) {
        return ownerOf(e, 0);
    }

    private static UUID ownerOf(Entity e, int depth) {
        if (e == null || depth > 4) return null;
        if (e instanceof Player p) return p instanceof FakePlayer ? null : p.getUUID();
        if (e instanceof DecoyEntity decoy) return decoy.getOwnerUuid().orElse(null);
        if (e instanceof io.github.rpgclasses.content.race.SwarmSlime slime) return slime.getOwnerUuid().orElse(null);
        // Dominar Animal (Druida): mientras dure, el animal es del Druida.
        UUID dominated = io.github.rpgclasses.druid.Dominate.ownerOf(e);
        if (dominated != null) return dominated;
        if (e instanceof OwnableEntity pet && pet.getOwnerUUID() != null) return pet.getOwnerUUID();
        if (e instanceof Projectile projectile) return ownerOf(projectile.getOwner(), depth + 1);
        Entity summoner = summoner(e);
        return summoner != null && summoner != e ? ownerOf(summoner, depth + 1) : null;
    }

    /** Invocador de una invocación de Iron's (por reflexión: la interfaz no está en su API). */
    private static Entity summoner(Entity e) {
        if (!summonLookupDone) {
            summonLookupDone = true;
            try {
                summonClass = Class.forName(IRONS_SUMMON);
                summonerMethod = summonClass.getMethod("getSummoner");
            } catch (ReflectiveOperationException | LinkageError ex) {
                summonClass = null;
            }
        }
        if (summonClass == null || !summonClass.isInstance(e)) return null;
        try {
            return (Entity) summonerMethod.invoke(e);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------ Relaciones

    /** ¿El jugador {@code owner} considera aliado al jugador {@code other}? (lista propia o mismo equipo de scoreboard). */
    public static boolean considers(MinecraftServer server, UUID owner, UUID other) {
        if (owner == null || other == null) return false;
        if (owner.equals(other)) return true;
        return book(server).considers(owner, other) || sameTeam(server, owner, other);
    }

    private static boolean sameTeam(MinecraftServer server, UUID a, UUID b) {
        if (!flag(ServerConfig.ALLY_SCOREBOARD_TEAMS, true)) return false;
        ServerPlayer pa = server.getPlayerList().getPlayer(a);
        ServerPlayer pb = server.getPlayerList().getPlayer(b);
        if (pa == null || pb == null) return false;
        Team team = pa.getTeam();
        return team != null && team.isAlliedTo(pb.getTeam());
    }

    /**
     * Desde el punto de vista de {@code source} (o de su dueño): ¿{@code target} es aliado?
     * Uno mismo, las mascotas propias y las de los aliados cuentan como aliados. Sin dueño jugador en
     * alguno de los dos, se usa la regla vanilla ({@code isAlliedTo}).
     */
    public static boolean isAlly(Entity source, Entity target) {
        if (source == null || target == null) return false;
        if (source == target) return true;
        MinecraftServer server = source.getServer();
        UUID a = ownerOf(source), b = ownerOf(target);
        if (server != null && a != null && b != null) return considers(server, a, b);
        return source.isAlliedTo(target);
    }

    /** ¿Es una mascota con dueño jugador (domesticada: lobos, gatos, loros, caballos...)? */
    public static boolean isPet(Entity e) {
        return e instanceof OwnableEntity pet && !(e instanceof Player) && pet.getOwnerUUID() != null;
    }

    /**
     * Aliados vivos de {@code source} a {@code radius} bloques: jugadores (no espectadores) y entidades con dueño
     * jugador aliado (mascotas propias y de los aliados, invocaciones). {@code includeSelf}: incluir a {@code source}.
     */
    public static java.util.List<LivingEntity> alliesAround(Entity source, double radius, boolean includeSelf) {
        java.util.List<LivingEntity> out = new ArrayList<>();
        double r2 = radius * radius;
        for (LivingEntity e : source.level().getEntitiesOfClass(LivingEntity.class, source.getBoundingBox().inflate(radius))) {
            if (!e.isAlive() || e.distanceToSqr(source) > r2 || (e == source && !includeSelf)) continue;
            if (e instanceof Player p && (p.isSpectator() || p instanceof FakePlayer)) continue;
            if (!(e instanceof Player) && ownerOf(e) == null) continue;
            if (e == source || isAlly(source, e)) out.add(e);
        }
        return out;
    }

    /** Variante con el dueño por UUID (trampas, nubes...: el dueño puede estar desconectado). */
    public static boolean isAllyOf(MinecraftServer server, UUID owner, Entity target) {
        if (owner == null || target == null) return false;
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        if (online != null) return isAlly(online, target);
        UUID b = ownerOf(target);
        return b != null && considers(server, owner, b);
    }

    /**
     * ¿{@code target} es un enemigo válido para un efecto ofensivo de {@code source}? Vivo, distinto de
     * {@code source}, no aliado, y si es un jugador: que se le pueda dañar (PvP del servidor, equipos) y que
     * no esté en espectador o creativo. Cada efecto añade después su propio filtro de mobs (hostiles, jefes...).
     */
    public static boolean isEnemy(Entity source, LivingEntity target) {
        if (source == null || target == null || target == source || !target.isAlive()) return false;
        if (isAlly(source, target)) return false;
        if (!(target instanceof Player victim)) return true;
        if (victim.isSpectator() || victim.isCreative()) return false;
        Player attacker = playerOwner(source);
        if (attacker == null) return source.getServer() == null || source.getServer().isPvpAllowed();
        return attacker != victim && attacker.canHarmPlayer(victim);
    }

    /** Variante con el dueño por UUID. */
    public static boolean isEnemyOf(MinecraftServer server, UUID owner, LivingEntity target) {
        if (owner == null || target == null || !target.isAlive()) return false;
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        if (online != null) return isEnemy(online, target);
        UUID b = ownerOf(target);
        if (owner.equals(b) || (b != null && considers(server, owner, b))) return false;
        if (!(target instanceof Player victim)) return true;
        return !victim.isSpectator() && !victim.isCreative() && server.isPvpAllowed();
    }

    private static Player playerOwner(Entity e) {
        if (e instanceof Player p) return p;
        UUID id = ownerOf(e);
        MinecraftServer server = e.getServer();
        return id == null || server == null ? null : server.getPlayerList().getPlayer(id);
    }

    /**
     * Para el mixin de {@code Entity.isAlliedTo}: la respuesta del sistema de aliados, o null si no aplica
     * (cliente, o alguna de las dos entidades sin dueño jugador).
     */
    public static Boolean alliedOverride(Entity self, Entity other) {
        if (self == null || other == null || self.level().isClientSide()) return null;
        MinecraftServer server = self.getServer();
        if (server == null) return null;
        UUID a = ownerOf(self);
        if (a == null) return null;
        UUID b = ownerOf(other);
        if (b == null) return null;
        return considers(server, a, b);
    }

    private static boolean flag(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------ Cambios

    /** Marca o desmarca a {@code target} como aliado de {@code player}; avisa a los dos y sincroniza. */
    public static void set(ServerPlayer player, UUID target, String targetName, boolean ally) {
        MinecraftServer server = player.server;
        AllyBook book = book(server);
        AllyBook.Result result;
        if (ally) {
            int max;
            try {
                max = ServerConfig.MAX_ALLIES.get();
            } catch (IllegalStateException e) {
                max = 32;
            }
            result = book.add(player.getUUID(), target, max);
        } else {
            result = book.remove(player.getUUID(), target);
        }
        switch (result) {
            case SELF -> player.sendSystemMessage(Component.translatable("msg.rites_and_paths.ally.self").withStyle(ChatFormatting.RED));
            case ALREADY -> player.sendSystemMessage(Component.translatable("msg.rites_and_paths.ally.already", targetName));
            case NOT_ALLY -> player.sendSystemMessage(Component.translatable("msg.rites_and_paths.ally.not_ally", targetName));
            case FULL -> player.sendSystemMessage(Component.translatable("msg.rites_and_paths.ally.full",
                    book.alliesOf(player.getUUID()).size()).withStyle(ChatFormatting.RED));
            case OK -> {
                changed(server);
                player.sendSystemMessage(Component.translatable(ally ? "msg.rites_and_paths.ally.added" : "msg.rites_and_paths.ally.removed",
                        targetName).withStyle(ally ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
                ServerPlayer other = server.getPlayerList().getPlayer(target);
                if (other != null) {
                    other.sendSystemMessage(notice(player, ally, book.considers(target, player.getUUID())));
                    sync(other);
                }
                DebugLog.event(player, "aliados", "{} {} como aliado", ally ? "marca a" : "desmarca a", targetName);
            }
        }
        sync(player);
    }

    /** Aviso a quien acaban de marcar o desmarcar; si no es recíproco, con un enlace para marcarle también. */
    private static Component notice(ServerPlayer from, boolean ally, boolean reciprocal) {
        String name = from.getGameProfile().getName();
        MutableComponent msg = Component.translatable(ally ? "msg.rites_and_paths.ally.marked_you" : "msg.rites_and_paths.ally.unmarked_you", name)
                .withStyle(ally ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        if (ally && !reciprocal) {
            String command = "/aliados agregar " + name;
            msg.append(" ").append(Component.translatable("msg.rites_and_paths.ally.mark_back").withStyle(Style.EMPTY
                    .withColor(ChatFormatting.AQUA).withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command)))));
        }
        return msg;
    }

    public static int clear(ServerPlayer player) {
        List<UUID> before = book(player.server).alliesOf(player.getUUID());
        int n = book(player.server).clear(player.getUUID());
        if (n > 0) changed(player.server);
        sync(player);
        for (UUID id : before) {
            ServerPlayer other = player.server.getPlayerList().getPlayer(id);
            if (other != null) sync(other);
        }
        return n;
    }

    public static String nameOf(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return online.getGameProfile().getName();
        if (server.getProfileCache() != null) {
            java.util.Optional<GameProfile> profile = server.getProfileCache().get(id);
            if (profile.isPresent() && profile.get().getName() != null) return profile.get().getName();
        }
        return id.toString().substring(0, 8);
    }

    private static List<Packets.SyncAllies.Entry> entries(MinecraftServer server, List<UUID> ids) {
        List<Packets.SyncAllies.Entry> out = new ArrayList<>();
        for (UUID id : ids) out.add(new Packets.SyncAllies.Entry(id, nameOf(server, id)));
        return out;
    }

    /** Envía al jugador su lista de aliados y quién le tiene como aliado (para la pantalla). */
    public static void sync(ServerPlayer player) {
        if (player instanceof FakePlayer) return;
        AllyBook book = book(player.server);
        Net.toPlayer(player, new Packets.SyncAllies(entries(player.server, book.alliesOf(player.getUUID())),
                entries(player.server, book.alliedBy(player.getUUID()))));
    }

    // ------------------------------------------------------------------ Eventos

    public static Object listener() {
        return new Events();
    }

    public static final class Events {
        /** Fuego amigo: no se puede dañar a quien se tiene como aliado (ni con mascotas, invocaciones o proyectiles). */
        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onAttack(LivingAttackEvent event) {
            LivingEntity victim = event.getEntity();
            if (victim.level().isClientSide() || flag(ServerConfig.ALLY_FRIENDLY_FIRE, false)) return;
            Entity attacker = event.getSource().getEntity();
            if (attacker == null || attacker == victim || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            MinecraftServer server = victim.getServer();
            UUID a = ownerOf(attacker), b = ownerOf(victim);
            // Mismo dueño (tu mascota, tu invocación): lo decide vanilla o el mod de la invocación.
            if (server == null || a == null || b == null || a.equals(b)) return;
            if (considers(server, a, b)) {
                event.setCanceled(true);
                DebugLog.event(victim instanceof ServerPlayer sp ? sp : null, "aliados", "fuego amigo bloqueado: {} -> {}",
                        DebugLog.describe(attacker), DebugLog.describe(victim));
            }
        }

        @SubscribeEvent
        public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) sync(player);
        }

        @SubscribeEvent
        public void onServerStopped(ServerStoppedEvent event) {
            cached = null;
            cachedServer = null;
        }
    }
}
