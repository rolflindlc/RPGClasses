package io.github.rpgclasses.server;

import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Eventos genéricos para el registro de depuración: efectos puestos y quitados, daño final, curas y muertes
 * en los que interviene un jugador con clase. Lo concreto de cada habilidad o pasiva se registra en su código.
 */
public final class DebugEvents {
    /** Curación acumulada por jugador; se vuelca cada 2 s para no escribir una línea por tick de regeneración. */
    private static final Map<UUID, float[]> HEALS = new HashMap<>();
    private int tick;

    public static void install() {
        DebugErrorCapture.install();
        DebugLog.event(null, "arranque", "Rolflin's Rites & Paths {} cargado; Iron's {}, Epic Fight {}", DebugLog.modVersion(),
                loaded("irons_spellbooks"), loaded("epicfight"));
    }

    private static String loaded(String modId) {
        return net.minecraftforge.fml.ModList.get().getModContainerById(modId)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("no");
    }

    private static boolean classed(Entity entity) {
        if (!(entity instanceof ServerPlayer player) || player instanceof FakePlayer) return false;
        PlayerClassData data = PlayerClassDataProvider.get(player);
        return data != null && data.state.classId != null;
    }

    /** Jugador con clase al que atribuir la línea: el afectado o, si no, el causante. */
    private static Player owner(Entity affected, Entity cause) {
        if (classed(affected)) return (Player) affected;
        if (classed(cause)) return (Player) cause;
        return null;
    }

    private static String effectName(MobEffectInstance effect) {
        ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(effect.getEffect());
        return id == null ? effect.getDescriptionId() : id.toString();
    }

    private static boolean ours(MobEffectInstance effect) {
        ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(effect.getEffect());
        return id != null && "rites_and_paths".equals(id.getNamespace());
    }

    // ------------------------------------------------------------------ efectos

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEffectAdded(MobEffectEvent.Added event) {
        if (event.getEntity().level().isClientSide()) return;
        MobEffectInstance e = event.getEffectInstance();
        Player owner = owner(event.getEntity(), event.getEffectSource());
        if (owner == null && !ours(e)) return;
        DebugLog.event(owner, "efecto", "{} recibe {} nivel {} durante {} s{}{}", DebugLog.describe(event.getEntity()),
                effectName(e), e.getAmplifier() + 1, DebugLog.num(e.getDuration() / 20.0),
                event.getEffectSource() == null ? "" : " (de " + DebugLog.describe(event.getEffectSource()) + ")",
                event.getOldEffectInstance() == null ? "" : " [renovado]");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEffectRemoved(MobEffectEvent.Remove event) {
        if (event.getEntity().level().isClientSide() || event.isCanceled() || event.getEffectInstance() == null) return;
        MobEffectInstance e = event.getEffectInstance();
        Player owner = owner(event.getEntity(), null);
        if (owner == null && !ours(e)) return;
        DebugLog.event(owner, "efecto", "a {} se le quita {} (le quedaban {} s)", DebugLog.describe(event.getEntity()),
                effectName(e), DebugLog.num(e.getDuration() / 20.0));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEffectExpired(MobEffectEvent.Expired event) {
        if (event.getEntity().level().isClientSide() || event.getEffectInstance() == null) return;
        MobEffectInstance e = event.getEffectInstance();
        Player owner = owner(event.getEntity(), null);
        if (owner == null && !ours(e)) return;
        DebugLog.event(owner, "efecto", "a {} se le acaba {}", DebugLog.describe(event.getEntity()), effectName(e));
    }

    // ------------------------------------------------------------------ daño, curas y muertes

    /** Daño final (tras armadura, encantamientos y absorción): lo que de verdad se pierde de vida. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDamage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        Player owner = owner(victim, event.getSource().getEntity());
        if (owner == null) return;
        DebugLog.event(owner, "daño", "{} pierde {} de vida ({} → {}) por {} de {}{}", DebugLog.describe(victim),
                DebugLog.num(event.getAmount()), DebugLog.num(victim.getHealth()),
                DebugLog.num(Math.max(0, victim.getHealth() - event.getAmount())), event.getSource().getMsgId(),
                DebugLog.describe(event.getSource().getEntity()), event.isCanceled() ? " [cancelado]" : "");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHeal(LivingHealEvent event) {
        if (event.getEntity().level().isClientSide() || event.isCanceled() || !classed(event.getEntity())) return;
        float[] acc = HEALS.computeIfAbsent(event.getEntity().getUUID(), k -> new float[2]);
        acc[0] += event.getAmount();
        acc[1]++;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tick % 40 != 0 || HEALS.isEmpty()) return;
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        for (Map.Entry<UUID, float[]> e : HEALS.entrySet()) {
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(e.getKey());
            if (player == null) continue;
            DebugLog.event(player, "cura", "{} curaciones en 2 s: +{} (vida {} / {})", (int) e.getValue()[1],
                    DebugLog.num(e.getValue()[0]), DebugLog.num(player.getHealth()), DebugLog.num(player.getMaxHealth()));
        }
        HEALS.clear();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        Player owner = owner(event.getEntity(), event.getSource().getEntity());
        if (owner == null) return;
        DebugLog.event(owner, "muerte", "{} muere por {} de {}{}", DebugLog.describe(event.getEntity()),
                event.getSource().getMsgId(), DebugLog.describe(event.getSource().getEntity()),
                event.isCanceled() ? " [cancelada: la salvó algo]" : "");
    }

    // ------------------------------------------------------------------ sesión

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerClassData data = PlayerClassDataProvider.get(player);
        DebugLog.event(player, "sesión", "entra; clase {}, subclase {} (nivel {}), ramas {}, perks {}",
                data == null ? "?" : data.state.classId, data == null ? "?" : data.state.subclassId,
                data == null ? 0 : data.state.subclassLevel, data == null ? "?" : data.state.branchPoints,
                data == null ? "?" : data.state.perks);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        DebugLog.event(event.getEntity(), "sesión", "sale");
        HEALS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        DebugLog.event(event.getEntity(), "sesión", "reaparece{}", event.isEndConquered() ? " (vuelve del End)" : "");
    }
}
