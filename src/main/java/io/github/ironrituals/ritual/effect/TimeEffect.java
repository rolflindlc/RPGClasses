package io.github.ironrituals.ritual.effect;

import io.github.ironrituals.IronRituals;
import io.github.ironrituals.core.RitualMath;
import io.github.ironrituals.ritual.RitualContext;
import io.github.ironrituals.ritual.RitualEffect;
import io.github.ironrituals.ritual.active.ActiveRitual;
import io.github.ironrituals.ritual.active.RitualSavedData;
import io.github.ironrituals.ritual.active.RitualTicker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.DerivedLevelData;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Rituales del Sol y de la Noche (plan, 4.A.1 y 4.A.2). Aceleran el reloj hasta el objetivo ({@code target_time})
 * en unos 10 s y, con el catalizador {@code freeze_clock}, congelan el reloj de esa dimensión durante la duración
 * del nivel (escala con el poder de la escuela).
 * <p>
 * El reloj congelado es por dimensión: cada tick se repone el {@code dayTime} guardado (no se toca la gamerule) y
 * se anula el avance por sueño. Las dimensiones que comparten el reloj del Overworld (datos derivados) lo usan a él.
 * Un ritual de tiempo nuevo sustituye al activo en esa dimensión (sección 2.6). Su temporizador avanza aunque el
 * propietario se desconecte, para no dejar el mundo congelado.
 */
public class TimeEffect implements RitualEffect {
    public static final ResourceLocation SUN = IronRituals.id("sun");
    public static final ResourceLocation NIGHT = IronRituals.id("night");

    private static final int SHIFT_TICKS = 200;
    /** Margen del temporizador mientras acelera (el propio efecto marca cuándo termina). */
    private static final long SHIFT_BUDGET = SHIFT_TICKS + 40;

    @Nullable
    @Override
    public Component checkStart(RitualContext ctx) {
        if (ctx.level().dimensionType().hasFixedTime()) return Component.translatable("ritual.ironrituals.time.no_cycle");
        return null;
    }

    @Override
    public void apply(RitualContext ctx) {
        ServerLevel clock = clockLevel(ctx.level());
        MinecraftServer server = ctx.level().getServer();
        for (ActiveRitual old : activeIn(server, clock.dimension())) RitualTicker.end(server, old);

        long now = clock.getDayTime();
        long target = now + RitualMath.ticksUntil(now, ctx.paramInt("target_time", 0));
        CompoundTag data = new CompoundTag();
        data.putString("ClockDim", clock.dimension().location().toString());
        data.putLong("Target", target);
        data.putDouble("Step", Math.max(1.0, (target - now) / (double) SHIFT_TICKS));
        data.putBoolean("Freeze", ctx.hasCatalyst("freeze_clock"));
        data.putLong("FreezeTicks", ctx.scaledDurationTicks());
        ctx.startPersistent(0, SHIFT_BUDGET, data);

        ctx.level().playSound(null, ctx.core(), ctx.recipe().ritual().equals(SUN) ? SoundEvents.BEACON_POWER_SELECT
                : SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 2.0F, 0.7F);
    }

    @Override
    public void tick(ActiveRitual r, ServerLevel ignored) {
        ServerLevel clock = clockLevelOf(ignored.getServer(), r);
        if (clock == null) {
            r.remaining = 0;
            return;
        }
        CompoundTag d = r.data;
        if (d.getBoolean("Frozen")) {
            clock.setDayTime(d.getLong("FrozenAt"));
            // Tras la sincronización de hora de vanilla (cada 20 ticks): hora fija para que el cliente no la adelante.
            if (clock.getServer().getTickCount() % 20 == 0) syncFrozen(clock);
            return;
        }
        long target = d.getLong("Target");
        long now = clock.getDayTime();
        long next = Math.min(target, now + (long) Math.ceil(d.getDouble("Step")));
        clock.setDayTime(next);
        if (next < target) return;

        if (d.getBoolean("Freeze")) {
            d.putBoolean("Frozen", true);
            d.putLong("FrozenAt", target);
            r.remaining = Math.max(20, d.getLong("FreezeTicks"));
            r.total = r.remaining;
            RitualSavedData.get(clock.getServer()).setDirty();
        } else {
            r.remaining = 0;
        }
    }

    @Override
    public int tickInterval() {
        return 1;
    }

    @Override
    public boolean timerNeedsOwner() {
        return false;
    }

    private static void syncFrozen(ServerLevel clock) {
        MinecraftServer server = clock.getServer();
        for (ServerLevel l : server.getAllLevels()) {
            if (clockLevel(l) != clock) continue;
            server.getPlayerList().broadcastAll(new ClientboundSetTimePacket(l.getGameTime(), l.getDayTime(), false), l.dimension());
        }
    }

    /** Al terminar, la sincronización normal devuelve el avance del reloj a los clientes. */
    @Override
    public void onEnd(ActiveRitual r, ServerLevel level) {
        ServerLevel clock = clockLevelOf(level.getServer(), r);
        if (clock == null) return;
        boolean cycle = clock.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT);
        for (ServerLevel l : level.getServer().getAllLevels()) {
            if (clockLevel(l) != clock) continue;
            level.getServer().getPlayerList().broadcastAll(new ClientboundSetTimePacket(l.getGameTime(), l.getDayTime(), cycle), l.dimension());
        }
    }

    // ------------------------------------------------------------------ consultas para los eventos

    /** Instante congelado en esa dimensión, o {@code null} si su reloj no está congelado. */
    @Nullable
    public static Long frozenAt(MinecraftServer server, ResourceKey<Level> clockDim) {
        for (ActiveRitual r : activeIn(server, clockDim)) {
            if (r.data.getBoolean("Frozen") && !r.paused) return r.data.getLong("FrozenAt");
        }
        return null;
    }

    private static List<ActiveRitual> activeIn(MinecraftServer server, ResourceKey<Level> clockDim) {
        String dim = clockDim.location().toString();
        return RitualSavedData.get(server).matching(r -> (r.ritual.equals(SUN) || r.ritual.equals(NIGHT))
                && dim.equals(r.data.getString("ClockDim")));
    }

    /** Nivel cuyo reloj se modifica: las dimensiones con datos derivados usan el del Overworld. */
    public static ServerLevel clockLevel(ServerLevel level) {
        return level.getLevelData() instanceof DerivedLevelData ? level.getServer().overworld() : level;
    }

    @Nullable
    private static ServerLevel clockLevelOf(MinecraftServer server, ActiveRitual r) {
        ResourceLocation id = ResourceLocation.tryParse(r.data.getString("ClockDim"));
        if (id == null) return null;
        return server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
    }
}
