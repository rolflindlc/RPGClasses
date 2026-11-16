package io.github.rpgclasses.content.race;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.server.Allies;
import io.github.rpgclasses.server.DebugLog;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Coloso Porteador (adenda 0.7, 4.3): el cinturón. Bloques funcionales (tag {@code rpgclasses:belt_blocks}) sujetos
 * al cuerpo, en 3–6 huecos según el Rango; dos cofres en un hueco son un cofre grande y cuentan como 2.
 * <ul>
 *   <li>Tecla racial 1: el menú del cinturón (poner y quitar bloques y abrirlos). Los aliados lo abren agachados con
 *       clic derecho sobre el Porteador (solo pueden usar los bloques, no cambiarlos).</li>
 *   <li>Cofres, cofres grandes y barriles son contenedores virtuales; hornos, altos hornos y ahumadores cocinan con el
 *       tick del dueño (también mientras camina); las mesas (crafteo, encantamientos, yunque, telar, cortapiedras,
 *       afiladora, herrería y cartografía) abren su menú vanilla allí donde está el Porteador.</li>
 *   <li>Al quitar un bloque, su contenido va al inventario. Al morir (sin keepInventory) caen el cinturón y lo que
 *       contiene. Se guarda en los datos persistentes del jugador ({@code PlayerPersisted}).</li>
 *   <li>Coste: −5 % de velocidad por pasajero hasta el Rango 2 (el −10 % fijo va en el datapack, con los suelos).</li>
 * </ul>
 */
public final class PorterBelt {
    public static final TagKey<Item> BELT_BLOCKS = TagKey.create(Registries.ITEM, RpgClasses.id("belt_blocks"));
    public static final int MAX_SLOTS = 6;
    private static final String TAG = "rpgclasses.belt";
    private static final UUID PASSENGER_SPEED = UUID.fromString("4c8e2f1a-7d3b-4a6e-9f05-1b2c3d4e5f60");

    enum Kind { CHEST, BARREL, ENDER, FURNACE, BLAST, SMOKER, CRAFTING, ENCHANTING, ANVIL, LOOM, STONECUTTER, GRINDSTONE, SMITHING, CARTOGRAPHY }

    private static final Map<UUID, Belt> BELTS = new HashMap<>();
    /** Menú de un bloque abierto desde el cinturón: al cerrarlo (Esc) se vuelve al menú del cinturón. */
    private record Back(net.minecraft.world.inventory.AbstractContainerMenu menu, UUID owner, boolean manage) {}
    private static final Map<UUID, Back> BACK = new HashMap<>();
    /** Volver al cinturón en el próximo tick (no se puede abrir un menú mientras se cierra el otro). */
    private static final Map<UUID, Back> REOPEN = new HashMap<>();
    /** Tick de juego hasta el que se ignora el clic derecho (el gesto de guardar en el cinturón ya lo gastó). */
    private static final Map<UUID, Long> SUPPRESS_USE = new HashMap<>();

    public PorterBelt() {
        Rites.BELT_ITEMS = PorterBelt::used;
    }

    // ------------------------------------------------------------------ bloques admitidos (cliente y servidor)

    static Kind kindOf(ItemStack s) {
        if (s.isEmpty()) return null;
        Item i = s.getItem();
        if (i == Items.CHEST || i == Items.TRAPPED_CHEST) return Kind.CHEST;
        if (i == Items.BARREL) return Kind.BARREL;
        if (i == Items.ENDER_CHEST) return Kind.ENDER;
        if (i == Items.FURNACE) return Kind.FURNACE;
        if (i == Items.BLAST_FURNACE) return Kind.BLAST;
        if (i == Items.SMOKER) return Kind.SMOKER;
        if (i == Items.CRAFTING_TABLE) return Kind.CRAFTING;
        if (i == Items.ENCHANTING_TABLE) return Kind.ENCHANTING;
        if (i == Items.ANVIL || i == Items.CHIPPED_ANVIL || i == Items.DAMAGED_ANVIL) return Kind.ANVIL;
        if (i == Items.LOOM) return Kind.LOOM;
        if (i == Items.STONECUTTER) return Kind.STONECUTTER;
        if (i == Items.GRINDSTONE) return Kind.GRINDSTONE;
        if (i == Items.SMITHING_TABLE) return Kind.SMITHING;
        if (i == Items.CARTOGRAPHY_TABLE) return Kind.CARTOGRAPHY;
        return null;
    }

    /** Se puede llevar en el cinturón: en el tag y con un uso conocido. */
    public static boolean allowed(ItemStack s) {
        return !s.isEmpty() && s.is(BELT_BLOCKS) && kindOf(s) != null;
    }

    /** Huecos que ocupa: 1, salvo dos cofres juntos (cofre grande), que ocupan 2. */
    public static int cost(ItemStack s) {
        if (s.isEmpty()) return 0;
        return kindOf(s) == Kind.CHEST ? Math.min(2, s.getCount()) : 1;
    }

    /** Cuántas unidades admite un hueco: 2 cofres (cofre grande) o 1 de lo demás. */
    public static int maxCount(ItemStack s) {
        return kindOf(s) == Kind.CHEST ? 2 : 1;
    }

    // ------------------------------------------------------------------ almacenamiento

    /** Contenedor del cinturón: solo lo usan el dueño y sus aliados cercanos. */
    public static final class BeltStorage extends SimpleContainer {
        private final UUID owner;
        private final Runnable onChange;

        BeltStorage(UUID owner, int size, Runnable onChange) {
            super(size);
            this.owner = owner;
            this.onChange = onChange;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (onChange != null) onChange.run();
        }

        @Override
        public boolean stillValid(Player player) {
            return canUse(player, owner);
        }
    }

    /** El cinturón de un jugador: los bloques (6 huecos como mucho) y lo que hay dentro de cada uno. */
    static final class Belt {
        final UUID owner;
        final BeltStorage blocks;
        /** Por hueco: BeltStorage (cofres, barril), VirtualFurnace (hornos) o null (mesas o vacío). */
        final Object[] parts = new Object[MAX_SLOTS];
        /** Lo que había en cada hueco la última vez (objeto + cantidad), para detectar cambios. */
        final String[] signature = new String[MAX_SLOTS];
        ServerPlayer ownerRef;
        boolean dirty;
        /** Hay que enviar a los clientes los bloques que se ven en el cuerpo. */
        boolean visualDirty = true;
        private boolean loading;

        Belt(UUID owner) {
            this.owner = owner;
            this.blocks = new BeltStorage(owner, MAX_SLOTS, this::onBlocksChanged);
        }

        private void onBlocksChanged() {
            dirty = true;
            visualDirty = true;
            if (!loading) reconcile();
        }

        void markDirty() {
            dirty = true;
        }

        /** Compara cada hueco con lo que había: si cambia el bloque, su contenido vuelve al dueño y se crea uno nuevo. */
        void reconcile() {
            for (int i = 0; i < MAX_SLOTS; i++) {
                ItemStack block = blocks.getItem(i);
                String sig = block.isEmpty() ? "" : net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(block.getItem()) + "x" + block.getCount();
                if (sig.equals(signature[i] == null ? "" : signature[i])) continue;
                Kind before = parts[i] instanceof VirtualFurnace ? Kind.FURNACE : parts[i] instanceof BeltStorage ? Kind.CHEST : null;
                Kind now = kindOf(block);
                if (before == Kind.CHEST && (now == Kind.CHEST || now == Kind.BARREL) && parts[i] instanceof BeltStorage old) {
                    // Cofre ↔ cofre grande ↔ barril: se conserva lo que cabe y sobra lo demás.
                    int size = now == Kind.CHEST && block.getCount() >= 2 ? 54 : 27;
                    if (old.getContainerSize() != size) {
                        BeltStorage resized = newStorage(size);
                        for (int s = 0; s < old.getContainerSize(); s++) {
                            ItemStack st = old.getItem(s);
                            if (st.isEmpty()) continue;
                            if (s < size) resized.setItem(s, st);
                            else spill(st);
                        }
                        parts[i] = resized;
                    }
                } else {
                    spillPart(i);
                    parts[i] = createPart(block);
                }
                signature[i] = sig;
                dirty = true;
            }
        }

        BeltStorage newStorage(int size) {
            return new BeltStorage(owner, size, this::markDirty);
        }

        Object createPart(ItemStack block) {
            Kind k = kindOf(block);
            if (k == null) return null;
            return switch (k) {
                case CHEST -> newStorage(block.getCount() >= 2 ? 54 : 27);
                case BARREL -> newStorage(27);
                case FURNACE -> new VirtualFurnace(RecipeType.SMELTING, newStorage(3));
                case BLAST -> new VirtualFurnace(RecipeType.BLASTING, newStorage(3));
                case SMOKER -> new VirtualFurnace(RecipeType.SMOKING, newStorage(3));
                default -> null;
            };
        }

        /** Lo que hay dentro del bloque del hueco i vuelve al dueño (inventario o el suelo). */
        void spillPart(int i) {
            Container c = parts[i] instanceof VirtualFurnace f ? f.items : parts[i] instanceof BeltStorage s ? s : null;
            if (c != null) {
                for (int s = 0; s < c.getContainerSize(); s++) spill(c.removeItemNoUpdate(s));
            }
            parts[i] = null;
        }

        private void spill(ItemStack stack) {
            if (stack.isEmpty()) return;
            if (ownerRef != null && ownerRef.isAlive()) ownerRef.getInventory().placeItemBackInInventory(stack);
            else if (ownerRef != null) ownerRef.drop(stack, false);
        }

        /** Todo lo del cinturón (bloques y contenido) en una lista, y el cinturón vacío. */
        List<ItemStack> takeAll() {
            List<ItemStack> out = new ArrayList<>();
            loading = true;
            try {
                for (int i = 0; i < MAX_SLOTS; i++) {
                    Container c = parts[i] instanceof VirtualFurnace f ? f.items : parts[i] instanceof BeltStorage s ? s : null;
                    if (c != null) for (int s = 0; s < c.getContainerSize(); s++) {
                        ItemStack st = c.removeItemNoUpdate(s);
                        if (!st.isEmpty()) out.add(st);
                    }
                    parts[i] = null;
                    ItemStack block = blocks.removeItemNoUpdate(i);
                    if (!block.isEmpty()) out.add(block);
                    signature[i] = "";
                }
            } finally {
                loading = false;
            }
            dirty = true;
            visualDirty = true;
            return out;
        }

        boolean isEmpty() {
            return blocks.isEmpty();
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.put("Blocks", saveItems(blocks));
            for (int i = 0; i < MAX_SLOTS; i++) {
                if (parts[i] instanceof VirtualFurnace f) tag.put("Part" + i, f.save());
                else if (parts[i] instanceof BeltStorage s) {
                    CompoundTag t = new CompoundTag();
                    t.put("Items", saveItems(s));
                    tag.put("Part" + i, t);
                }
            }
            return tag;
        }

        void load(CompoundTag tag) {
            loading = true;
            try {
                loadItems(blocks, tag.getList("Blocks", 10));
                for (int i = 0; i < MAX_SLOTS; i++) {
                    ItemStack block = blocks.getItem(i);
                    parts[i] = createPart(block);
                    CompoundTag part = tag.getCompound("Part" + i);
                    if (parts[i] instanceof VirtualFurnace f) f.load(part);
                    else if (parts[i] instanceof BeltStorage s) loadItems(s, part.getList("Items", 10));
                    signature[i] = block.isEmpty() ? "" : net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(block.getItem()) + "x" + block.getCount();
                }
            } finally {
                loading = false;
            }
        }
    }

    static ListTag saveItems(Container c) {
        ListTag list = new ListTag();
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack st = c.getItem(i);
            if (st.isEmpty()) continue;
            CompoundTag t = new CompoundTag();
            t.putByte("Slot", (byte) i);
            st.save(t);
            list.add(t);
        }
        return list;
    }

    static void loadItems(Container c, ListTag list) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            int slot = t.getByte("Slot") & 255;
            if (slot < c.getContainerSize()) c.setItem(slot, ItemStack.of(t));
        }
    }

    // ------------------------------------------------------------------ acceso

    static Belt belt(ServerPlayer p) {
        Belt b = BELTS.computeIfAbsent(p.getUUID(), id -> {
            Belt nb = new Belt(id);
            CompoundTag root = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            if (root.contains(TAG)) nb.load(root.getCompound(TAG));
            return nb;
        });
        b.ownerRef = p;
        return b;
    }

    private static void save(ServerPlayer p, Belt b) {
        CompoundTag data = p.getPersistentData();
        CompoundTag root = data.getCompound(Player.PERSISTED_NBT_TAG);
        root.put(TAG, b.save());
        data.put(Player.PERSISTED_NBT_TAG, root);
        b.dirty = false;
    }

    /** Huecos del cinturón según el Rango (0 si no es Porteador). */
    public static int capacity(Player p) {
        return Math.min(MAX_SLOTS, (int) Math.round(Races.number(p, "race_belt", "slots", 0)));
    }

    /** Huecos ocupados (para el rito Carga a Cuestas). */
    public static int used(ServerPlayer p) {
        Belt b = BELTS.get(p.getUUID());
        if (b == null) return 0;
        int n = 0;
        for (int i = 0; i < MAX_SLOTS; i++) n += cost(b.blocks.getItem(i));
        return n;
    }

    /** El dueño siempre; un aliado, si está vivo, en la misma dimensión y a 8 bloques o menos del Porteador. */
    public static boolean canUse(Player viewer, UUID owner) {
        if (viewer.getUUID().equals(owner)) return viewer.isAlive();
        if (!(viewer instanceof ServerPlayer sv)) return true; // en el cliente decide el servidor
        ServerPlayer o = sv.server.getPlayerList().getPlayer(owner);
        return o != null && o.isAlive() && viewer.isAlive() && o.level() == viewer.level() && o.distanceToSqr(viewer) <= 64
                && Allies.isAlly(o, viewer);
    }

    /** Tecla racial 1 del Porteador: abre su cinturón. Devuelve false para que no cuente como habilidad usada. */
    static boolean openOwn(ServerPlayer p, ActiveMechanic m) {
        openBelt(p, p, true);
        return false;
    }

    private static void openBelt(ServerPlayer viewer, ServerPlayer owner, boolean manage) {
        int cap = capacity(owner);
        if (cap <= 0) return;
        Belt b = belt(owner);
        UUID ownerId = owner.getUUID();
        Component title = Component.translatable("container.rpgclasses.belt", owner.getDisplayName());
        NetworkHooks.openScreen(viewer, new SimpleMenuProvider((id, inv, pl) -> new BeltMenu(id, inv, b.blocks, cap, manage, ownerId), title),
                buf -> {
                    buf.writeVarInt(cap);
                    buf.writeBoolean(manage);
                });
        viewer.level().playSound(null, owner.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.8f, 1f);
    }

    /** Botón "Abrir" del menú del cinturón: abre el menú vanilla del bloque de ese hueco. */
    static void openEntry(ServerPlayer viewer, UUID ownerId, int slot) {
        ServerPlayer owner = viewer.server.getPlayerList().getPlayer(ownerId);
        if (owner == null || !canUse(viewer, ownerId) || slot < 0 || slot >= capacity(owner)) return;
        Belt b = belt(owner);
        ItemStack block = b.blocks.getItem(slot);
        Kind k = kindOf(block);
        if (k == null) return;
        Object part = b.parts[slot];
        ContainerLevelAccess access = ContainerLevelAccess.create(owner.level(), owner.blockPosition());
        Component title = block.getHoverName();
        MenuProvider provider = switch (k) {
            case CHEST, BARREL -> part instanceof BeltStorage s ? new SimpleMenuProvider((id, inv, pl) -> s.getContainerSize() >= 54
                    ? ChestMenu.sixRows(id, inv, s) : ChestMenu.threeRows(id, inv, s),
                    k == Kind.CHEST && s.getContainerSize() >= 54 ? Component.translatable("container.chestDouble") : title) : null;
            case ENDER -> new SimpleMenuProvider((id, inv, pl) -> ChestMenu.threeRows(id, inv, pl.getEnderChestInventory()), title);
            case FURNACE -> part instanceof VirtualFurnace f ? new SimpleMenuProvider((id, inv, pl) -> new FurnaceMenu(id, inv, f.items, f.data), title) : null;
            case BLAST -> part instanceof VirtualFurnace f ? new SimpleMenuProvider((id, inv, pl) -> new BlastFurnaceMenu(id, inv, f.items, f.data), title) : null;
            case SMOKER -> part instanceof VirtualFurnace f ? new SimpleMenuProvider((id, inv, pl) -> new SmokerMenu(id, inv, f.items, f.data), title) : null;
            case CRAFTING -> new SimpleMenuProvider((id, inv, pl) -> new CraftingMenu(id, inv, access) {
                @Override
                public boolean stillValid(Player player) {
                    return canUse(player, ownerId);
                }
            }, title);
            case ENCHANTING -> new SimpleMenuProvider((id, inv, pl) -> new EnchantmentMenu(id, inv, access) {
                @Override
                public boolean stillValid(Player player) {
                    return canUse(player, ownerId);
                }
            }, title);
            case ANVIL -> new SimpleMenuProvider((id, inv, pl) -> new AnvilMenu(id, inv, access) {
                @Override
                public boolean stillValid(Player player) {
                    return canUse(player, ownerId);
                }
            }, title);
            case LOOM -> new SimpleMenuProvider((id, inv, pl) -> new LoomMenu(id, inv, access) {
                @Override
                public boolean stillValid(Player player) {
                    return canUse(player, ownerId);
                }
            }, title);
            case STONECUTTER -> new SimpleMenuProvider((id, inv, pl) -> new StonecutterMenu(id, inv, access) {
                @Override
                public boolean stillValid(Player player) {
                    return canUse(player, ownerId);
                }
            }, title);
            case GRINDSTONE -> new SimpleMenuProvider((id, inv, pl) -> new GrindstoneMenu(id, inv, access) {
                @Override
                public boolean stillValid(Player player) {
                    return canUse(player, ownerId);
                }
            }, title);
            case SMITHING -> new SimpleMenuProvider((id, inv, pl) -> new SmithingMenu(id, inv, access) {
                @Override
                public boolean stillValid(Player player) {
                    return canUse(player, ownerId);
                }
            }, title);
            case CARTOGRAPHY -> new SimpleMenuProvider((id, inv, pl) -> new CartographyTableMenu(id, inv, access) {
                @Override
                public boolean stillValid(Player player) {
                    return canUse(player, ownerId);
                }
            }, title);
        };
        if (provider == null) return;
        boolean manage = viewer.getUUID().equals(ownerId);
        viewer.openMenu(provider);
        if (viewer.containerMenu != viewer.inventoryMenu) BACK.put(viewer.getUUID(), new Back(viewer.containerMenu, ownerId, manage));
        DebugLog.event(viewer, "raza", "Cinturón de {}: abre {}", owner.getGameProfile().getName(), k);
    }

    // ------------------------------------------------------------------ eventos

    /** Un aliado agachado hace clic derecho en el Porteador: abre su cinturón (solo para usar los bloques). */
    @SubscribeEvent
    public void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer viewer) || viewer instanceof FakePlayer
                || !(event.getTarget() instanceof ServerPlayer owner) || owner instanceof FakePlayer) return;
        if (!viewer.isShiftKeyDown() || capacity(owner) <= 0 || !canUse(viewer, owner.getUUID())) return;
        openBelt(viewer, owner, false);
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    /** Esc (o cerrar) en el menú de un bloque del cinturón: se vuelve al menú del cinturón. */
    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        Back back = BACK.get(p.getUUID());
        if (back == null || back.menu() != event.getContainer()) return;
        BACK.remove(p.getUUID());
        REOPEN.put(p.getUUID(), back);
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        Back back = REOPEN.remove(p.getUUID());
        if (back != null && p.isAlive() && p.containerMenu == p.inventoryMenu && canUse(p, back.owner())) {
            ServerPlayer owner = p.server.getPlayerList().getPlayer(back.owner());
            if (owner != null) openBelt(p, owner, back.manage());
        }
        DebugLog.guard(p, "cinturón", () -> tick(p));
    }

    /** Ctrl + Mayús + clic derecho con un bloque admitido en la mano: lo guarda en el cinturón (lo manda el cliente). */
    public static void insertHeld(ServerPlayer p) {
        int cap = capacity(p);
        if (cap <= 0 || p.isSpectator()) return;
        ItemStack hand = p.getMainHandItem();
        if (!allowed(hand)) return;
        SUPPRESS_USE.put(p.getUUID(), p.level().getGameTime() + 4);
        Belt b = belt(p);
        int used = 0;
        for (int i = 0; i < cap; i++) used += cost(b.blocks.getItem(i));
        if (used >= cap) {
            p.displayClientMessage(Component.translatable("message.rpgclasses.belt_full"), true);
            return;
        }
        boolean chest = kindOf(hand) == Kind.CHEST;
        int target = -1;
        for (int i = 0; i < cap && target < 0; i++) {
            ItemStack in = b.blocks.getItem(i);
            if (chest && kindOf(in) == Kind.CHEST && in.getCount() < 2 && ItemStack.isSameItemSameTags(in, hand)) target = i;
        }
        for (int i = 0; i < cap && target < 0; i++) if (b.blocks.getItem(i).isEmpty()) target = i;
        if (target < 0) {
            p.displayClientMessage(Component.translatable("message.rpgclasses.belt_full"), true);
            return;
        }
        ItemStack in = b.blocks.getItem(target);
        b.blocks.setItem(target, in.isEmpty() ? hand.copyWithCount(1) : in.copyWithCount(in.getCount() + 1));
        hand.shrink(1);
        p.level().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (suppressed(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (suppressed(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    private static boolean suppressed(Player p) {
        if (p.level().isClientSide()) return false;
        Long until = SUPPRESS_USE.get(p.getUUID());
        if (until == null) return false;
        if (p.level().getGameTime() > until) {
            SUPPRESS_USE.remove(p.getUUID());
            return false;
        }
        return true;
    }

    private static void tick(ServerPlayer p) {
        passengerSpeed(p);
        int cap = capacity(p);
        Belt b = BELTS.get(p.getUUID());
        if (b == null) {
            // Solo se carga si es Porteador, o (una vez por segundo) si tiene un cinturón guardado que devolver.
            if (cap <= 0 && !(p.tickCount % 20 == 0 && p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).contains(TAG))) return;
            b = belt(p);
        }
        // Hornos: cocinan con el tick del dueño (también mientras camina).
        for (Object part : b.parts) {
            if (part instanceof VirtualFurnace f) {
                if (f.tick(p.level())) b.markDirty();
                if (f.xp >= 1) {
                    int whole = (int) f.xp;
                    p.giveExperiencePoints(whole);
                    f.xp -= whole;
                }
            }
        }
        if (b.visualDirty) {
            b.visualDirty = false;
            io.github.rpgclasses.network.Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                    visual(p, b));
        }
        if (p.tickCount % 20 == 0) {
            // Bloques en huecos que ya no tiene (cambio de subraza o de Rango por un administrador): vuelven a él.
            for (int i = 0; i < MAX_SLOTS; i++) {
                if (i < cap || b.blocks.getItem(i).isEmpty()) continue;
                ItemStack block = b.blocks.removeItemNoUpdate(i);
                b.spillPart(i);
                b.signature[i] = "";
                p.getInventory().placeItemBackInInventory(block);
                b.markDirty();
                b.visualDirty = true;
            }
            if (cap <= 0 && b.isEmpty()) {
                // Ya no es Porteador y no queda nada: se borra el cinturón guardado.
                BELTS.remove(p.getUUID());
                io.github.rpgclasses.network.Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                        new io.github.rpgclasses.network.Packets.BeltVisual(p.getId(), List.of()));
                CompoundTag root = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
                root.remove(TAG);
                p.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
                return;
            }
            if (b.dirty) save(p, b);
        }
    }

    /** Los bloques del cinturón que se dibujan en el cuerpo (los huecos que tiene, en orden). */
    private static io.github.rpgclasses.network.Packets.BeltVisual visual(ServerPlayer p, Belt b) {
        List<ItemStack> shown = new ArrayList<>();
        int cap = capacity(p);
        for (int i = 0; i < Math.min(cap, MAX_SLOTS); i++) {
            ItemStack st = b.blocks.getItem(i);
            if (!st.isEmpty()) shown.add(st.copyWithCount(1));
        }
        return new io.github.rpgclasses.network.Packets.BeltVisual(p.getId(), shown);
    }

    /** Quien empieza a ver a un Porteador recibe sus bloques del cinturón. */
    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof ServerPlayer owner) || !(event.getEntity() instanceof ServerPlayer viewer)) return;
        Belt b = BELTS.get(owner.getUUID());
        if (b != null && !b.isEmpty()) io.github.rpgclasses.network.Net.toPlayer(viewer, visual(owner, b));
    }

    /** −5 % de velocidad por pasajero (hasta el Rango 2). */
    private static void passengerSpeed(ServerPlayer p) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        double penalty = Races.number(p, "race_belt", "passenger_penalty", 0) * p.getPassengers().size();
        AttributeModifier current = speed.getModifier(PASSENGER_SPEED);
        if (current != null && current.getAmount() == -penalty) return;
        speed.removeModifier(PASSENGER_SPEED);
        if (penalty > 0) {
            speed.addTransientModifier(new AttributeModifier(PASSENGER_SPEED, "rpgclasses porter passengers", -penalty,
                    AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    /** Al morir caen al suelo los bloques del cinturón y lo que contienen (salvo con keepInventory). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        if (p.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;
        Belt b = BELTS.containsKey(p.getUUID()) || p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).contains(TAG) ? belt(p) : null;
        if (b == null || b.isEmpty()) return;
        BACK.remove(p.getUUID());
        for (ItemStack st : b.takeAll()) {
            ItemEntity item = new ItemEntity(p.level(), p.getX(), p.getY() + 0.5, p.getZ(), st);
            item.setDeltaMovement((p.getRandom().nextDouble() - 0.5) * 0.2, 0.2, (p.getRandom().nextDouble() - 0.5) * 0.2);
            item.setDefaultPickUpDelay();
            p.level().addFreshEntity(item);
        }
        save(p, b);
        io.github.rpgclasses.network.Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new io.github.rpgclasses.network.Packets.BeltVisual(p.getId(), List.of()));
        DebugLog.event(p, "raza", "Cinturón: cae al suelo al morir");
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Belt b = BELTS.remove(p.getUUID());
        if (b != null) save(p, b);
    }

    /** Al reaparecer, el jugador es un objeto nuevo: el cinturón (guardado en PlayerPersisted) se vuelve a leer. */
    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer old)) return;
        Belt b = BELTS.remove(old.getUUID());
        if (b != null && event.getEntity() instanceof ServerPlayer p) {
            save(p, b);
            b.ownerRef = p;
            b.visualDirty = true;
            BELTS.put(p.getUUID(), b);
        }
    }

    @SubscribeEvent
    public void onStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            Belt b = BELTS.get(p.getUUID());
            if (b != null) save(p, b);
        }
        BELTS.clear();
    }
}
