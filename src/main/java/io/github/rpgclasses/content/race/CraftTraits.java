package io.github.rpgclasses.content.race;

import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.mixin.FishingHookAccessor;
import io.github.rpgclasses.mixin.FurnaceAccessor;
import io.github.rpgclasses.server.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rasgos de oficio de las subrazas (fase R2 de razas):
 * <ul>
 *   <li>Enano Herrero: durabilidad extra en lo que fabrica o repara (marca en el objeto), yunque más barato e
 *       Irrompibilidad +1 en lo que fabrica (Rango 3).</li>
 *   <li>Enano Demoledor: +25 % de radio de su TNT y Túnel 3×3 agachado (Rango 3).</li>
 *   <li>Elfo Arcano: encantar en mesa o yunque cuesta menos experiencia.</li>
 *   <li>Tritón Pescador: los peces pican antes.</li>
 *   <li>Infernal Fundidor: hornos cercanos más rápidos. Infernal Nativo: trueques con piglins dobles a veces.</li>
 * </ul>
 * Los ganchos de vanilla están en los mixins {@code ItemDurabilityMixin}, {@code ItemCraftedMixin},
 * {@code AnvilMenuMixin}, {@code PlayerEnchantMixin} y {@code PiglinBarterMixin}.
 */
public final class CraftTraits {
    /** Durabilidad extra (fracción) guardada en el objeto: viaja con él aunque cambie de dueño. */
    public static final String FORGED_TAG = "rites_and_paths.forged";

    // ------------------------------------------------------------------ Enano Herrero

    /** Mixin de {@code ItemStack.getMaxDamage}: durabilidad base × (1 + marca). */
    public static int maxDamage(ItemStack stack, int base) {
        if (base <= 0) return base;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(FORGED_TAG)) return base;
        float bonus = tag.getFloat(FORGED_TAG);
        return bonus > 0 ? Math.round(base * (1 + bonus)) : base;
    }

    private static void markForged(ItemStack stack, double bonus) {
        if (bonus <= 0 || stack.isEmpty() || stack.getItem().getMaxDamage(stack) <= 0) return;
        CompoundTag tag = stack.getOrCreateTag();
        if (tag.getFloat(FORGED_TAG) < bonus) tag.putFloat(FORGED_TAG, (float) bonus);
    }

    /** Mixin de {@code Item.onCraftedBy} (mesa de trabajo, herrería): marca y, en Rango 3, Irrompibilidad +1. */
    public static void onCrafted(Player player, ItemStack stack) {
        if (!(player instanceof ServerPlayer p) || p instanceof FakePlayer || stack.isEmpty()) return;
        Rites.markCrafted(p, stack);
        ActiveMechanic m = Races.find(p, "race_forged");
        if (m == null || stack.getItem().getMaxDamage(stack) <= 0) return;
        boolean first = stack.getTag() == null || !stack.getTag().contains(FORGED_TAG);
        markForged(stack, m.number("durability", 0));
        int extra = (int) m.number("unbreaking", 0);
        if (first && extra > 0 && Enchantments.UNBREAKING.canEnchant(stack)) {
            Map<net.minecraft.world.item.enchantment.Enchantment, Integer> ench = EnchantmentHelper.getEnchantments(stack);
            int level = ench.getOrDefault(Enchantments.UNBREAKING, 0);
            int next = Math.min(Enchantments.UNBREAKING.getMaxLevel(), level + extra);
            if (next > level) {
                ench.put(Enchantments.UNBREAKING, next);
                EnchantmentHelper.setEnchantments(ench, stack);
            }
        }
    }

    /**
     * Mixin de {@code AnvilMenu.createResult}: el Herrero marca lo que repara o combina y paga menos niveles;
     * el Elfo Arcano también paga menos. Devuelve el coste nuevo.
     */
    public static int onAnvil(AnvilMenu menu, ItemStack result, int cost) {
        ServerPlayer p = viewer(menu);
        if (p == null || result.isEmpty() || cost <= 0) return cost;
        ActiveMechanic forged = Races.find(p, "race_forged");
        if (forged != null) markForged(result, forged.number("durability", 0));
        double discount = Math.max(Races.number(p, "race_anvil_discount", "discount", 0),
                Races.number(p, "race_arcane", "xp_discount", 0));
        return discount > 0 ? Math.max(1, (int) Math.round(cost * (1 - discount))) : cost;
    }

    private static ServerPlayer viewer(Object menu) {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (p.containerMenu == menu) return p;
        return null;
    }

    // ------------------------------------------------------------------ Elfo Arcano

    /** Mixin de {@code Player.onEnchantmentPerformed}: devuelve una parte de la experiencia gastada en la mesa. */
    public static void onEnchanted(Player player, ItemStack stack, int levels) {
        if (!(player instanceof ServerPlayer p) || p instanceof FakePlayer || levels <= 0) return;
        Rites.markEnchanted(p, stack);
        double discount = Races.number(p, "race_arcane", "xp_discount", 0);
        if (discount <= 0) return;
        int points = 0;
        for (int i = 0; i < levels; i++) points += xpForLevel(p.experienceLevel + i);
        int refund = (int) Math.round(points * discount);
        if (refund > 0) p.giveExperiencePoints(refund);
    }

    /** Puntos para pasar del nivel {@code level} al siguiente (igual que vanilla). */
    static int xpForLevel(int level) {
        if (level >= 30) return 112 + (level - 30) * 9;
        if (level >= 15) return 37 + (level - 15) * 5;
        return 7 + level * 2;
    }

    // ------------------------------------------------------------------ Infernal Nativo

    /** Mixin de {@code PiglinAi.getBarterResponseItems}: probabilidad de duplicar el trueque si hay un Nativo cerca. */
    public static List<ItemStack> onBarter(Piglin piglin, List<ItemStack> items) {
        if (items.isEmpty() || !(piglin.level() instanceof ServerLevel)) return items;
        Player near = piglin.level().getNearestPlayer(piglin, 10);
        if (!(near instanceof ServerPlayer p)) return items;
        Rites.onBarter(p);
        double chance = Races.number(p, "race_barter", "extra_chance", 0);
        if (chance <= 0 || piglin.getRandom().nextDouble() >= chance) return items;
        List<ItemStack> out = new ArrayList<>(items);
        for (ItemStack stack : items) out.add(stack.copy());
        DebugLog.event(p, "raza", "Trato Infernal: trueque doble");
        return out;
    }

    // ------------------------------------------------------------------ Eventos

    private static final Map<UUID, int[]> LURE = io.github.rpgclasses.server.SessionState.track(new HashMap<>());     // [id del gancho, último tiempo visto]
    private static final Map<UUID, List<BlockPos>> FURNACES = io.github.rpgclasses.server.SessionState.track(new HashMap<>());
    private static final Set<BlockPos> HEATED_THIS_TICK = io.github.rpgclasses.server.SessionState.track(new HashSet<>());
    private static boolean exploding;
    private static boolean tunneling;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) HEATED_THIS_TICK.clear();
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        lure(p);
        heat(p);
    }

    /** Tritón Pescador: cada vez que el gancho empieza a esperar un pez, la espera se acorta. */
    private static void lure(ServerPlayer p) {
        FishingHook hook = p.fishing;
        if (hook == null) {
            LURE.remove(p.getUUID());
            return;
        }
        double speed = Races.number(p, "race_fisher", "lure_speed", 0);
        if (speed <= 0) return;
        FishingHookAccessor acc = (FishingHookAccessor) hook;
        int t = acc.rites_and_paths$getTimeUntilLured();
        int[] last = LURE.get(p.getUUID());
        boolean fresh = last == null || last[0] != hook.getId() || t > last[1];
        if (t > 0 && fresh) {
            t = Math.max(1, (int) Math.round(t * (1 - speed)));
            acc.rites_and_paths$setTimeUntilLured(t);
        }
        LURE.put(p.getUUID(), new int[]{hook.getId(), t});
    }

    /** Infernal Fundidor: hornos, altos hornos y ahumadores cercanos avanzan más rápido (sin gastar más combustible). */
    private static void heat(ServerPlayer p) {
        ActiveMechanic m = Races.find(p, "race_forge_heat");
        if (m == null) {
            FURNACES.remove(p.getUUID());
            return;
        }
        Level level = p.level();
        long time = level.getGameTime();
        if (time % 20 == 0 || !FURNACES.containsKey(p.getUUID())) FURNACES.put(p.getUUID(), scanFurnaces(p, m.number("radius", 8)));
        double speed = m.number("furnace_speed", 0.5);
        int every = speed <= 0 ? 0 : Math.max(1, (int) Math.round(1 / speed)); // 0,5 → 1 tick extra de cada 2
        if (every == 0 || time % every != 0) return;
        for (BlockPos pos : FURNACES.get(p.getUUID())) {
            if (!HEATED_THIS_TICK.add(pos)) continue; // dos fundidores no lo aceleran el doble
            if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) continue;
            FurnaceAccessor acc = (FurnaceAccessor) furnace;
            int progress = acc.rites_and_paths$getCookingProgress();
            if (acc.rites_and_paths$getLitTime() > 0 && progress > 0 && progress < acc.rites_and_paths$getCookingTotalTime() - 1) {
                acc.rites_and_paths$setCookingProgress(progress + 1);
            }
        }
    }

    private static List<BlockPos> scanFurnaces(ServerPlayer p, double radius) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos center = p.blockPosition();
        int r = (int) Math.ceil(radius);
        double r2 = radius * radius;
        for (int cx = (center.getX() - r) >> 4; cx <= (center.getX() + r) >> 4; cx++) {
            for (int cz = (center.getZ() - r) >> 4; cz <= (center.getZ() + r) >> 4; cz++) {
                if (!p.level().hasChunk(cx, cz)) continue;
                LevelChunk chunk = p.level().getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof AbstractFurnaceBlockEntity && be.getBlockPos().distSqr(center) <= r2) out.add(be.getBlockPos());
                }
            }
        }
        return out;
    }

    /** Enano Demoledor: su TNT explota con más radio (se repite la explosión con el radio nuevo). */
    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Start event) {
        if (exploding || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getExplosion().getExploder() instanceof PrimedTnt tnt) || !(tnt.getOwner() instanceof ServerPlayer owner)) return;
        double bonus = Races.number(owner, "race_blast_proof", "tnt_radius", 0);
        if (bonus <= 0) return;
        event.setCanceled(true);
        Vec3 at = event.getExplosion().getPosition();
        exploding = true;
        try {
            level.explode(tnt, at.x, at.y, at.z, (float) (4.0 * (1 + bonus)), Level.ExplosionInteraction.TNT);
        } finally {
            exploding = false;
        }
    }

    /** Enano Demoledor, Túnel (Rango 3): agachado, al picar piedra rompe un cuadrado de 3×3 frente a la mirada. */
    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (tunneling || !(event.getPlayer() instanceof ServerPlayer p) || p instanceof FakePlayer || !p.isShiftKeyDown()) return;
        BlockState state = event.getState();
        if (!state.is(Races.DWARF_MINEABLE) || state.is(net.minecraftforge.common.Tags.Blocks.ORES)) return;
        ActiveMechanic m = Races.find(p, "race_tunnel");
        if (m == null || p.getMainHandItem().isEmpty()) return;
        int half = Math.max(1, (int) m.number("size", 3)) / 2;
        Direction facing = Direction.getNearest(p.getLookAngle().x, p.getLookAngle().y, p.getLookAngle().z);
        Direction a, b;
        if (facing.getAxis() == Direction.Axis.Y) {
            a = Direction.EAST;
            b = Direction.SOUTH;
        } else {
            a = Direction.UP;
            b = facing.getClockWise();
        }
        double exhaustion = m.number("exhaustion_per_block", 0.1);
        tunneling = true;
        try {
            for (int i = -half; i <= half; i++) {
                for (int j = -half; j <= half; j++) {
                    if (i == 0 && j == 0) continue;
                    BlockPos pos = event.getPos().relative(a, i).relative(b, j);
                    BlockState other = p.level().getBlockState(pos);
                    if (!other.is(Races.DWARF_MINEABLE) || other.getDestroySpeed(p.level(), pos) < 0) continue;
                    if (p.getMainHandItem().isEmpty()) return; // la herramienta se ha roto
                    if (p.gameMode.destroyBlock(pos)) p.causeFoodExhaustion((float) exhaustion);
                }
            }
        } finally {
            tunneling = false;
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LURE.remove(event.getEntity().getUUID());
        FURNACES.remove(event.getEntity().getUUID());
    }
}
