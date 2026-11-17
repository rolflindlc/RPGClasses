package io.github.rpgclasses.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.registry.ModAttributes;
import io.github.rpgclasses.server.AbilityService;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Habilidades de la clase base de Ranger (y las mejoras de salto y disparo de sus subclases). */
public final class RangerAbilities {
    public static final String AMMO_CONSERVATION = Ids.NS + ":ammo_conservation";
    public static final String DOUBLE_JUMP = Ids.NS + ":double_jump";
    public static final String TRIPLE_JUMP = Ids.NS + ":triple_jump";
    public static final String HEAVY_SHOT = Ids.NS + ":heavy_shot";
    public static final String LETHAL_SHOT = Ids.NS + ":lethal_shot";

    /** Ticks de uso con los que un arco vanilla queda tensado al máximo. */
    private static final int FULL_DRAW_TICKS = 20;

    private record PendingShot(int tick, double damageBonus, boolean ammoConserved) {}

    /** Datos del disparo en curso: se escriben al soltar el arco y los recoge la flecha al aparecer, en el mismo tick. */
    private static final Map<UUID, PendingShot> PENDING = new HashMap<>();
    private static final Map<UUID, Integer> AIR_JUMPS_USED = new HashMap<>();
    /** Arco tensado al máximo: {tick de servidor en que se alcanzó, tramos ya anunciados}. */
    private static final Map<UUID, int[]> FULL_DRAW = new HashMap<>();

    // --- Disparo ---

    @SubscribeEvent
    public void onArrowLoose(ArrowLooseEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        Set<String> unlocked = AbilityService.unlocked(player);

        // Disparo Pesado / Letal: el contador empieza con el arco tensado al máximo.
        double bonus = 0;
        String shot = unlocked.contains(LETHAL_SHOT) ? LETHAL_SHOT : unlocked.contains(HEAVY_SHOT) ? HEAVY_SHOT : null;
        if (shot != null) {
            // Tiempo REAL con el arco al máximo (la velocidad de carga no lo acelera).
            int[] draw = FULL_DRAW.get(player.getUUID());
            double held = draw == null ? 0 : (player.server.getTickCount() - draw[0]) / 20.0;
            bonus = stageBonus(AbilityService.params(shot), held);
        }

        // Conservación de Munición: se devuelve la flecha antes de que el arco la consuma.
        boolean conserved = false;
        if (unlocked.contains(AMMO_CONSERVATION) && !player.getAbilities().instabuild) {
            double chance = AbilityService.number(AbilityService.params(AMMO_CONSERVATION), "chance", 0.33);
            ItemStack ammo = player.getProjectile(event.getBow());
            if (!ammo.isEmpty() && player.getRandom().nextDouble() < chance) {
                ammo.grow(1);
                conserved = true;
            }
        }
        PENDING.put(player.getUUID(), new PendingShot(player.server.getTickCount(), bonus, conserved));
    }

    /** Bonus del último tramo alcanzado tras {@code heldSeconds} con el arco al máximo. */
    static double stageBonus(JsonObject params, double heldSeconds) {
        if (!params.has("thresholds_seconds") || !params.has("damage_bonuses")) return 0;
        JsonArray thresholds = params.getAsJsonArray("thresholds_seconds");
        JsonArray bonuses = params.getAsJsonArray("damage_bonuses");
        double bonus = 0;
        for (int i = 0; i < Math.min(thresholds.size(), bonuses.size()); i++) {
            if (heldSeconds >= thresholds.get(i).getAsDouble()) bonus = bonuses.get(i).getAsDouble();
        }
        return bonus;
    }

    private static PendingShot takePending(ServerPlayer shooter) {
        PendingShot p = PENDING.remove(shooter.getUUID());
        return p != null && p.tick() == shooter.server.getTickCount() ? p : null;
    }

    /** Lo llama DamagePipeline al aparecer una flecha: {bonus aditivo de daño, 1 si la munición se conservó}. */
    public static double[] takeShot(ServerPlayer shooter) {
        PendingShot p = takePending(shooter);
        return p == null ? new double[] {0, 0} : new double[] {p.damageBonus(), p.ammoConserved() ? 1 : 0};
    }

    // --- Conservación de Munición con ballesta: la munición se gasta al cargarla, no al disparar ---

    private static final String CONSERVED_TAG = "rites_and_paths.conserved_shot";
    private record CrossbowLoad(ItemStack crossbow, ItemStack ammo) {}
    private static final Map<UUID, CrossbowLoad> CROSSBOW_LOADS = new HashMap<>();

    /** Al soltar una ballesta sin cargar se recuerda qué munición iba a gastar. */
    @SubscribeEvent
    public void onUseStop(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Stop event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || player.getAbilities().instabuild) return;
        ItemStack crossbow = event.getItem();
        if (!(crossbow.getItem() instanceof net.minecraft.world.item.CrossbowItem) || net.minecraft.world.item.CrossbowItem.isCharged(crossbow)) return;
        if (!AbilityService.unlocked(player).contains(AMMO_CONSERVATION)) return;
        ItemStack ammo = player.getProjectile(crossbow);
        if (!ammo.isEmpty()) CROSSBOW_LOADS.put(player.getUUID(), new CrossbowLoad(crossbow, ammo.copyWithCount(1)));
    }

    /** Si la ballesta ha quedado cargada, con la probabilidad de la habilidad se devuelve la munición gastada. */
    private static void crossbowLoaded(ServerPlayer player) {
        CrossbowLoad load = CROSSBOW_LOADS.remove(player.getUUID());
        if (load == null || !net.minecraft.world.item.CrossbowItem.isCharged(load.crossbow())) return;
        double chance = AbilityService.number(AbilityService.params(AMMO_CONSERVATION), "chance", 0.33);
        if (player.getRandom().nextDouble() >= chance) return;
        player.getInventory().placeItemBackInInventory(load.ammo());
        load.crossbow().getOrCreateTag().putBoolean(CONSERVED_TAG, true);
    }

    /** Lo llama DamagePipeline al aparecer una flecha: true si sale de una ballesta cuya munición se conservó. */
    public static boolean takeCrossbowConserved(ServerPlayer shooter) {
        for (ItemStack hand : new ItemStack[] {shooter.getMainHandItem(), shooter.getOffhandItem()}) {
            if (hand.getItem() instanceof net.minecraft.world.item.CrossbowItem && hand.hasTag() && hand.getTag().getBoolean(CONSERVED_TAG)) {
                hand.getTag().remove(CONSERVED_TAG);
                return true;
            }
        }
        return false;
    }

    /** Aviso (sonido, partículas y texto) al alcanzar cada tramo de Disparo Pesado; reinicio de los saltos aéreos. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.onGround() || player.isInWater() || player.onClimbable()) AIR_JUMPS_USED.remove(player.getUUID());
        if (!CROSSBOW_LOADS.isEmpty()) crossbowLoaded(player);

        if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof BowItem)) {
            FULL_DRAW.remove(player.getUUID());
            return;
        }
        if (player.getTicksUsingItem() < FULL_DRAW_TICKS) return;
        int now = player.server.getTickCount();
        int[] draw = FULL_DRAW.computeIfAbsent(player.getUUID(), k -> new int[] {now, 0});

        Set<String> unlocked = AbilityService.unlocked(player);
        String shot = unlocked.contains(LETHAL_SHOT) ? LETHAL_SHOT : unlocked.contains(HEAVY_SHOT) ? HEAVY_SHOT : null;
        if (shot == null) return;
        JsonObject params = AbilityService.params(shot);
        if (!params.has("thresholds_seconds")) return;
        JsonArray thresholds = params.getAsJsonArray("thresholds_seconds");
        double held = (now - draw[0]) / 20.0;
        int reached = 0;
        for (int i = 0; i < thresholds.size(); i++) {
            if (held >= thresholds.get(i).getAsDouble()) reached = i + 1;
        }
        if (reached <= draw[1]) return;
        draw[1] = reached;

        boolean last = reached == thresholds.size();
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 0.6f + 0.3f * reached);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(last ? ParticleTypes.FLAME : ParticleTypes.ENCHANTED_HIT,
                    player.getX(), player.getY() + 1.2, player.getZ(), 10 + 8 * reached, 0.35, 0.4, 0.35, 0.05);
        }
        player.displayClientMessage(Component.translatable("msg.rites_and_paths.heavy_shot_stage",
                Component.translatable(Ids.langKey("ability", shot, "name")),
                Math.round(stageBonus(params, held) * 100)), true);
    }

    /**
     * Velocidad de carga: cada tick de uso del arco descuenta ticks extra. Sin estado: los ticks extra
     * de este tick son la diferencia de la parte entera acumulada. Corre en cliente y servidor (atributo sincronizado).
     */
    @SubscribeEvent
    public void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getItem().getItem() instanceof BowItem)) return;
        double bonus = ModAttributes.value(player, ModAttributes.DRAW_SPEED);
        if (bonus <= 0) return;
        int used = event.getItem().getUseDuration() - event.getDuration();
        int extra = (int) (Math.floor((used + 1) * bonus) - Math.floor(used * bonus));
        if (extra > 0) event.setDuration(event.getDuration() - extra);
    }

    // --- Saltos aéreos ---

    /** El cliente ya ha saltado; aquí se valida y se reinicia la distancia de caída. */
    public static void onAirJump(ServerPlayer player) {
        Set<String> unlocked = AbilityService.unlocked(player);
        int allowed = (unlocked.contains(DOUBLE_JUMP) ? 1 : 0) + (unlocked.contains(TRIPLE_JUMP) ? 1 : 0);
        int used = AIR_JUMPS_USED.getOrDefault(player.getUUID(), 0);
        if (used >= allowed || player.onGround()) return; // sin validar: la caída se cuenta desde el salto original
        AIR_JUMPS_USED.put(player.getUUID(), used + 1);
        player.resetFallDistance();
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        CROSSBOW_LOADS.remove(event.getEntity().getUUID());
        AIR_JUMPS_USED.remove(event.getEntity().getUUID());
        FULL_DRAW.remove(event.getEntity().getUUID());
    }
}
