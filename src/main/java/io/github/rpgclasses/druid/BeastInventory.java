package io.github.rpgclasses.druid;

import io.github.rpgclasses.server.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Inventario de la Forma Salvaje: al transformarse, todo lo que lleva el jugador (inventario, armadura y mano
 * secundaria) se guarda aparte y desaparece; al volver a su forma se le devuelve tal cual estaba.
 * <ul>
 *   <li>Lo guardado va en los datos persistentes del jugador ({@link #KEY}), que se guardan con él: si el servidor se
 *       cae con el jugador transformado, al volver a entrar se le devuelve (sin perder ni duplicar nada).</li>
 *   <li>Transformado no recoge objetos ni abre contenedores (cofres, hornos, mesas, mochilas...). Puertas, botones y
 *       palancas siguen funcionando.</li>
 *   <li>Lo que entre en el inventario de la bestia por otra vía ({@code /give}, otros mods, flechas recogidas) pasa al
 *       inventario guardado en el mismo tic; si ese está lleno, cae al suelo a sus pies.</li>
 *   <li>Al morir transformado se devuelve antes de soltar el botín, así que la regla {@code keepInventory} y las
 *       tumbas de otros mods funcionan igual que siempre.</li>
 * </ul>
 */
public final class BeastInventory {
    /** Clave en {@code Player#getPersistentData()}. */
    public static final String KEY = "rites_and_paths_druid_stash";

    public static boolean hasStash(ServerPlayer p) {
        return p.getPersistentData().contains(KEY, Tag.TAG_LIST);
    }

    /** Guarda y vacía el inventario (si ya está guardado, por cambiar de forma, no hace nada). */
    static void stash(ServerPlayer p) {
        if (hasStash(p)) return;
        // Cierra cualquier menú: lo que llevara en el cursor vuelve al inventario antes de guardarlo.
        p.closeContainer();
        Inventory inv = p.getInventory();
        ListTag saved = inv.save(new ListTag());
        p.getPersistentData().put(KEY, saved);
        inv.clearContent();
        p.inventoryMenu.broadcastChanges();
        DebugLog.event(p, "Druida", "guarda el inventario al transformarse ({} pilas)", saved.size());
    }

    /** Devuelve el inventario guardado; lo que hubiera ahora en el de la bestia se añade encima (o cae al suelo). */
    static void restore(ServerPlayer p) {
        if (!hasStash(p)) return;
        Inventory inv = p.getInventory();
        java.util.List<ItemStack> extra = new java.util.ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) extra.add(s.copy());
        }
        inv.load(p.getPersistentData().getList(KEY, Tag.TAG_COMPOUND));
        p.getPersistentData().remove(KEY);
        for (ItemStack s : extra) {
            if (!inv.add(s) && !s.isEmpty()) p.drop(s, false);
        }
        p.inventoryMenu.broadcastChanges();
        DebugLog.event(p, "Druida", "recupera el inventario guardado ({} objetos sueltos añadidos)", extra.size());
    }

    /** Mueve al inventario guardado lo que haya aparecido en el de la bestia. */
    private static void sweep(ServerPlayer p) {
        Inventory inv = p.getInventory();
        if (inv.isEmpty()) return;
        Inventory saved = new Inventory(p);
        saved.load(p.getPersistentData().getList(KEY, Tag.TAG_COMPOUND));
        int moved = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            inv.setItem(i, ItemStack.EMPTY);
            ItemStack rest = s.copy();
            saved.add(rest);
            if (!rest.isEmpty()) p.drop(rest, false);
            moved++;
        }
        p.getPersistentData().put(KEY, saved.save(new ListTag()));
        p.inventoryMenu.broadcastChanges();
        DebugLog.event(p, "Druida", "{} pilas que entraron en forma de bestia pasan al inventario guardado", moved);
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        if (WildShape.inForm(p) && hasStash(p)) DebugLog.guard(p, "Druida: inventario", () -> sweep(p));
    }

    /** Si entra con un inventario guardado y sin forma (el servidor se cayó), se le devuelve. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !WildShape.inForm(p) && hasStash(p)) restore(p);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPickup(EntityItemPickupEvent event) {
        if (WildShape.inForm(event.getEntity())) event.setCanceled(true);
    }

    /** No abre contenedores: se corta el uso del bloque si guarda objetos o abre un menú (las puertas sí se abren). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!WildShape.inForm(event.getEntity())) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (isContainer(level, pos, level.getBlockState(pos))) {
            event.setUseBlock(Event.Result.DENY);
            if (!level.isClientSide) {
                event.getEntity().displayClientMessage(Component.translatable("message.rites_and_paths.druid.no_containers"), true);
            }
        }
    }

    private static boolean isContainer(Level level, BlockPos pos, BlockState state) {
        if (state.getMenuProvider(level, pos) != null) return true;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;
        return be instanceof Container || be instanceof MenuProvider
                || be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
    }

    /** Red de seguridad para lo que abra un menú por otra vía (cofre de ender, mochilas, teclas de otros mods). */
    @SubscribeEvent
    public void onOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer p && WildShape.inForm(p) && event.getContainer() != p.inventoryMenu) {
            p.closeContainer();
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.no_containers"), true);
        }
    }
}
