package io.github.rpgclasses.content;

import com.google.gson.JsonObject;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.DebugLog;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Francotirador (rev. 2.22).
 * <ul>
 *   <li><b>Camuflaje:</b> agachado y quieto {@code still_seconds} (2 s), invisible de verdad para mobs y jugadores hasta
 *   que se mueve, se levanta o dispara.</li>
 *   <li><b>Marca de Mira:</b> mirar con el catalejo a un enemigo {@code study_seconds} (5 s) seguidos lo marca
 *   {@code mark_seconds} (30 s); solo una marca a la vez. El siguiente proyectil tuyo que lo golpea hace
 *   +{@code damage_bonus} (+50 %) y gasta la marca.</li>
 * </ul>
 */
public final class SniperTools {
    public static final String CAMOUFLAGE = Ids.NS + ":camouflage";
    public static final String SNIPER_MARK = Ids.NS + ":sniper_mark";

    private static final class State {
        double x, y, z;
        int still;
        boolean hidden;
        int aimTarget = -1;
        int aimTicks;
        UUID marked;
        long markUntil;
    }

    private static final Map<UUID, State> STATES = io.github.rpgclasses.server.SessionState.track(new HashMap<>());

    private SniperTools() {}

    public static void register() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new SniperTools.Events());
    }

    public static boolean camouflaged(ServerPlayer player) {
        State st = STATES.get(player.getUUID());
        return st != null && st.hidden;
    }

    private static void reveal(ServerPlayer player, State st) {
        if (!st.hidden) return;
        st.hidden = false;
        MobEffectInstance e = player.getEffect(MobEffects.INVISIBILITY);
        if (e != null && e.isAmbient() && e.getDuration() <= 12) player.removeEffect(MobEffects.INVISIBILITY);
    }

    private static void camouflage(ServerPlayer player, State st) {
        JsonObject p = AbilityService.params(CAMOUFLAGE);
        double dx = player.getX() - st.x, dy = player.getY() - st.y, dz = player.getZ() - st.z;
        st.x = player.getX();
        st.y = player.getY();
        st.z = player.getZ();
        boolean still = dx * dx + dy * dy + dz * dz < 1.0e-4;
        if (!player.isShiftKeyDown() || !still || !player.onGround()) {
            st.still = 0;
            reveal(player, st);
            return;
        }
        st.still++;
        if (st.still >= AbilityService.ticks(p, "still_seconds", 2)) {
            if (!st.hidden) {
                st.hidden = true;
                player.serverLevel().sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, player.getX(), player.getY() + 1, player.getZ(), 12, 0.3, 0.5, 0.3, 0);
            }
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 12, 0, true, false, false));
        }
    }

    private static void aim(ServerPlayer player, State st) {
        JsonObject p = AbilityService.params(SNIPER_MARK);
        long now = player.level().getGameTime();
        if (st.marked != null && now > st.markUntil) st.marked = null;
        LivingEntity target = player.isScoping() ? io.github.rpgclasses.rogue.FaceChanger.lookedAt(player, AbilityService.number(p, "range", 96)) : null;
        if (target == null || !io.github.rpgclasses.server.Allies.isEnemy(player, target)) {
            st.aimTarget = -1;
            st.aimTicks = 0;
            return;
        }
        if (target.getId() != st.aimTarget) {
            st.aimTarget = target.getId();
            st.aimTicks = 0;
        }
        st.aimTicks++;
        int need = AbilityService.ticks(p, "study_seconds", 5);
        if (st.aimTicks % 10 == 0 && st.aimTicks < need) {
            player.displayClientMessage(Component.translatable("msg.rites_and_paths.sniper_mark.aiming", st.aimTicks * 100 / need), true);
        }
        if (st.aimTicks == need) {
            int ticks = AbilityService.ticks(p, "mark_seconds", 30);
            st.marked = target.getUUID();
            st.markUntil = now + ticks;
            Net.toPlayer(player, new io.github.rpgclasses.network.DruidPackets.Glow(new int[] {target.getId()}, ticks));
            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.6f);
            player.displayClientMessage(Component.translatable("msg.rites_and_paths.sniper_mark.marked", target.getDisplayName()), true);
            DebugLog.event(player, "habilidad", "Marca de Mira sobre {}", DebugLog.describe(target));
        }
    }

    public static final class Events {
        @SubscribeEvent
        public void onTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player instanceof FakePlayer) return;
            boolean camo = AbilityService.has(player, CAMOUFLAGE);
            boolean mark = AbilityService.has(player, SNIPER_MARK);
            if (!camo && !mark) {
                State st = STATES.remove(player.getUUID());
                if (st != null) reveal(player, st);
                return;
            }
            State st = STATES.computeIfAbsent(player.getUUID(), k -> new State());
            if (camo && player.isAlive()) camouflage(player, st);
            if (mark && player.isAlive()) aim(player, st);
        }

        /** Disparar rompe el camuflaje. */
        @SubscribeEvent
        public void onProjectile(EntityJoinLevelEvent event) {
            if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Projectile proj)) return;
            if (!(proj.getOwner() instanceof ServerPlayer player)) return;
            State st = STATES.get(player.getUUID());
            if (st != null) {
                st.still = 0;
                reveal(player, st);
            }
        }

        /** Invisible de verdad: los mobs solo lo ven pegado a ellos. */
        @SubscribeEvent
        public void onVisibility(LivingEvent.LivingVisibilityEvent event) {
            if (event.getEntity() instanceof ServerPlayer player && camouflaged(player)) event.modifyVisibility(0.05);
        }

        /** El primer proyectil tuyo que golpea al marcado hace +50 % y gasta la marca. */
        @SubscribeEvent(priority = EventPriority.LOW)
        public void onHurt(LivingHurtEvent event) {
            if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
            Entity direct = event.getSource().getDirectEntity();
            if (!(direct instanceof Projectile)) return;
            State st = STATES.get(player.getUUID());
            if (st == null || st.marked == null || !st.marked.equals(event.getEntity().getUUID())) return;
            if (player.level().getGameTime() > st.markUntil) {
                st.marked = null;
                return;
            }
            st.marked = null;
            double bonus = AbilityService.number(AbilityService.params(SNIPER_MARK), "damage_bonus", 0.5);
            event.setAmount((float) (event.getAmount() * (1 + bonus)));
            LivingEntity t = event.getEntity();
            player.serverLevel().sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 20, 0.3, 0.4, 0.3, 0.3);
            player.serverLevel().playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1f, 0.6f);
        }

        @SubscribeEvent
        public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            STATES.remove(event.getEntity().getUUID());
        }
    }
}
