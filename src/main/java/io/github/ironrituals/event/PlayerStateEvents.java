package io.github.ironrituals.event;

import io.github.ironrituals.IronRituals;
import io.github.ironrituals.compat.ScaleCompat;
import io.github.ironrituals.config.ServerConfig;
import io.github.ironrituals.registry.ModEffects;
import io.github.ironrituals.ritual.active.ActiveRitual;
import io.github.ironrituals.ritual.active.RitualTicker;
import io.github.ironrituals.ritual.effect.EternalForgeEffect;
import io.github.ironrituals.ritual.effect.ShrinkingEffect;
import io.github.ironrituals.ritual.effect.SoulAnchorEffect;
import io.github.ironrituals.ritual.effect.TimeEffect;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.SleepFinishedTimeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.DropRulesEvent;
import top.theillusivec4.curios.api.type.capability.ICurio;

/**
 * Eventos de los rituales de la Fase 4: reloj congelado y sueño, objetos forjados a prueba de fuego, trepa de
 * paredes, fin del Encogimiento y Ancla del Alma.
 */
@Mod.EventBusSubscriber(modid = IronRituals.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerStateEvents {
    /** Inventario retenido por el Ancla entre la muerte y la reaparición. */
    private static final String ANCHOR_ITEMS = "ironrituals_anchor_items";
    /** Marca durante la muerte para que Curios conserve sus ranuras. */
    private static final String ANCHOR_DYING = "ironrituals_anchor_dying";

    private PlayerStateEvents() {}

    // ------------------------------------------------------------------ Sol y Noche

    /** Con el reloj congelado, dormir no adelanta la hora (plan, 4.A.1). */
    @SubscribeEvent
    public static void onSleepFinished(SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ServerLevel clock = TimeEffect.clockLevel(level);
        Long frozen = TimeEffect.frozenAt(level.getServer(), clock.dimension());
        if (frozen != null) event.setTimeAddition(frozen);
    }

    // ------------------------------------------------------------------ Forja Eterna

    /** Los objetos forjados con Obsidiana Llorosa no se queman ni explotan en el suelo. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity item && EternalForgeEffect.isFireproof(item.getItem())) {
            item.setInvulnerable(true);
        }
    }

    // ------------------------------------------------------------------ trepa y restos

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.hasEffect(ModEffects.WALL_CLIMBING.get()) && player.horizontalCollision && !player.getAbilities().flying) {
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, player.isShiftKeyDown() ? Math.max(0.0, v.y) : 0.2, v.z);
            player.resetFallDistance();
        }
        // Salvaguarda: si la muerte se canceló después de retirar el inventario, se devuelve.
        if (player instanceof ServerPlayer sp && sp.isAlive() && sp.tickCount % 20 == 0
                && sp.getPersistentData().contains(ANCHOR_ITEMS)) {
            restoreInventory(sp, sp.getPersistentData());
        }
    }

    /** Al conectarse: deshace un Encogimiento que terminó con el jugador desconectado (Pehkui). */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp
                && ShrinkingEffect.activeFor(sp.server, sp.getUUID()).isEmpty()) {
            ScaleCompat.restore(sp);
        }
    }

    // ------------------------------------------------------------------ muerte

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;

        // El Encogimiento termina al morir.
        for (ActiveRitual r : ShrinkingEffect.activeFor(player.server, player.getUUID())) RitualTicker.end(player.server, r);

        if (!SoulAnchorEffect.has(player)) return;
        CompoundTag pd = player.getPersistentData();
        pd.remove(SoulAnchorEffect.FLAG); // un solo uso: hasta la próxima muerte
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;
        if (event.getSource().getEntity() instanceof Player killer && killer != player && !ServerConfig.SOUL_ANCHOR_PVP.get()) {
            player.sendSystemMessage(Component.translatable("message.ironrituals.soul_anchor.pvp").withStyle(ChatFormatting.GRAY));
            return;
        }

        // Retira del inventario lo que se conserva para que no caiga; la Maldición de Desaparición sigue actuando.
        Inventory inv = player.getInventory();
        ListTag kept = new ListTag();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || EnchantmentHelper.hasVanishingCurse(s)) continue;
            CompoundTag t = new CompoundTag();
            t.putInt("Idx", i);
            t.put("Item", s.save(new CompoundTag()));
            kept.add(t);
            inv.setItem(i, ItemStack.EMPTY);
        }
        if (!kept.isEmpty()) pd.put(ANCHOR_ITEMS, kept);
        pd.putBoolean(ANCHOR_DYING, true);
    }

    /** Curios: las ranuras del jugador anclado se conservan (salvo Maldición de Desaparición). */
    @SubscribeEvent
    public static void onCurioDropRules(DropRulesEvent event) {
        if (event.getEntity() instanceof Player p && p.getPersistentData().getBoolean(ANCHOR_DYING)) {
            event.addOverride(s -> !EnchantmentHelper.hasVanishingCurse(s), ICurio.DropRule.ALWAYS_KEEP);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer fresh)) return;
        Player old = event.getOriginal();
        CompoundTag pd = old.getPersistentData();
        if (!pd.getBoolean(ANCHOR_DYING)) return;
        pd.remove(ANCHOR_DYING);

        restoreInventory(fresh, pd);
        // Curios ya copia sus ranuras al reaparecer; se repite aquí por si la versión instalada no lo hace.
        old.reviveCaps();
        CuriosApi.getCuriosInventory(old).ifPresent(oldCurios ->
                CuriosApi.getCuriosInventory(fresh).ifPresent(newCurios -> newCurios.readTag(oldCurios.writeTag())));
        old.invalidateCaps();
        fresh.sendSystemMessage(Component.translatable("message.ironrituals.soul_anchor.saved").withStyle(ChatFormatting.AQUA));
    }

    /** Devuelve al jugador los objetos retenidos en {@code source} (y los borra de ahí). */
    private static void restoreInventory(ServerPlayer player, CompoundTag source) {
        ListTag kept = source.getList(ANCHOR_ITEMS, Tag.TAG_COMPOUND);
        source.remove(ANCHOR_ITEMS);
        Inventory inv = player.getInventory();
        for (Tag raw : kept) {
            CompoundTag t = (CompoundTag) raw;
            ItemStack s = ItemStack.of(t.getCompound("Item"));
            int idx = t.getInt("Idx");
            if (s.isEmpty()) continue;
            if (idx >= 0 && idx < inv.getContainerSize() && inv.getItem(idx).isEmpty()) inv.setItem(idx, s);
            else if (!inv.add(s)) player.drop(s, false);
        }
    }
}
