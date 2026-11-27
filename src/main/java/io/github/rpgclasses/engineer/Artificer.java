package io.github.rpgclasses.engineer;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.compat.ntgl.NtglCompat;
import io.github.rpgclasses.server.AbilityService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * Artificiero (adenda del Ingeniero, 4.3): cargas compartidas por torretas, minas y trincheras. Hay tantas cargas como
 * torretas se pueden tener (1 → 2 en el nivel 4 → 3 en el nivel 8). Una torreta puesta ocupa su carga hasta que se recoge
 * (vuelve al instante) o se rompe (entonces empieza a recargarse); minas y trincheras la gastan. Las cargas gastadas se
 * recargan de una en una (15 s cada una; mientras una se recarga, la siguiente espera).
 *
 * <p>Torretas, minas, trincheras y armas por devolver se guardan en el mundo ({@link Data}): una torreta o mina de un
 * chunk descargado sigue contando y, si se recoge o se reemplaza mientras tanto, desaparece al cargarse el chunk.
 */
public final class Artificer {
    static final ResourceKey<DamageType> WIRE = ResourceKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("barbed_wire"));
    public static final int TURRET = 0, MINE = 1, TRENCH = 2;

    // ------------------------------------------------------------------ cargas

    static int maxCharges(ServerPlayer p) {
        return Math.max(1, (int) Math.round(Engineer.mech(p, Engineer.GADGETS, "charges", 1)));
    }

    static int available(ServerPlayer p, Engineer.State s) {
        return Math.max(0, maxCharges(p) - data(p.server).turrets(p.getUUID()).size() - s.spent);
    }

    private static int chargeTicks() {
        return (int) Math.round(Engineer.a(Engineer.TURRET, "charge_seconds", 15) * 20);
    }

    /** Gasta una carga: empieza a recargarse si no había otra recargándose. */
    static void spend(ServerPlayer p, Engineer.State s) {
        if (s.spent == 0) s.chargeReadyAt = p.level().getGameTime() + chargeTicks();
        s.spent++;
    }

    static void tick(ServerPlayer p, Engineer.State s, long now) {
        if (s.spent > 0 && now >= s.chargeReadyAt) {
            s.spent--;
            if (s.spent > 0) s.chargeReadyAt = now + chargeTicks();
        }
        // Más cargas gastadas de las que caben (subclase cambiada, nivel perdido): se ajusta.
        int max = Engineer.sub(p, Engineer.ARTIFICER) ? maxCharges(p) : 0;
        if (s.spent > max) s.spent = max;
    }

    /** Segundos que faltan para la próxima carga (HUD), o 0. */
    static int nextChargeTicks(ServerPlayer p, Engineer.State s) {
        return s.spent > 0 ? (int) Math.max(0, s.chargeReadyAt - p.level().getGameTime()) : 0;
    }

    // ------------------------------------------------------------------ teclas

    /** Tecla de artilugio pulsada (paquete del cliente). */
    static void use(ServerPlayer p, int kind) {
        if (!Engineer.sub(p, Engineer.ARTIFICER) || !p.isAlive() || p.isSpectator()) return;
        String ability = kind == TURRET ? Engineer.TURRET : kind == MINE ? Engineer.MINE : Engineer.TRENCH;
        if (!AbilityService.has(p, ability)) return;
        Engineer.State s = Engineer.state(p);
        if (kind == TURRET) {
            // Mano vacía: recoger todas. Otra cosa que no sea un arma de fuego: aviso.
            if (p.getMainHandItem().isEmpty()) {
                recallAll(p);
                return;
            }
            if (!NtglCompat.isFirearm(p.getMainHandItem())) {
                fail(p, "no_firearm");
                return;
            }
        }
        if (available(p, s) <= 0) {
            fail(p, "no_charges");
            return;
        }
        boolean used = switch (kind) {
            case TURRET -> deployTurret(p);
            case MINE -> throwMine(p);
            default -> digTrench(p);
        };
        if (used && kind != TURRET) spend(p, s);
    }

    private static void fail(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable("message.rites_and_paths.engineer." + key), true);
    }

    /** Permiso de construir en esa posición (protecciones de terreno, spawn, modo aventura). */
    private static boolean mayBuild(ServerPlayer p, ServerLevel level, BlockPos pos) {
        if (!p.mayBuild() || !level.mayInteract(p, pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        return !ForgeEventFactory.onBlockPlace(p, BlockSnapshot.create(level.dimension(), level, pos), Direction.UP);
    }

    private static boolean deployTurret(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(5)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p));
        Vec3 at = hit.getType() == HitResult.Type.MISS || hit.getDirection() != Direction.UP ? p.position() : hit.getLocation();
        BlockPos pos = BlockPos.containing(at);
        TurretEntity turret = EngineerRegistry.TURRET.get().create(level);
        if (turret == null) return false;
        turret.moveTo(at.x, at.y, at.z, p.getYRot(), 0);
        if (!mayBuild(p, level, pos) || !level.noCollision(turret)) {
            fail(p, "cant_place");
            return false;
        }
        ItemStack gun = p.getMainHandItem().copy();
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        turret.setup(p, gun);
        data(p.server).turrets(p.getUUID()).put(turret.getUUID(), gun.copy());
        data(p.server).setDirty();
        level.addFreshEntity(turret);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.6f, 1.5f);
        return true;
    }

    private static boolean throwMine(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        MineEntity mine = EngineerRegistry.MINE.get().create(level);
        if (mine == null) return false;
        if (!mayBuild(p, level, p.blockPosition())) {
            fail(p, "cant_place");
            return false;
        }
        var params = Engineer.ability(Engineer.MINE);
        mine.setup(p, (float) Engineer.mech(p, Engineer.GADGETS, "mine_damage", 7), AbilityService.number(params, "blast_radius", 2),
                AbilityService.ticks(params, "arm_seconds", 1));
        Vec3 eye = p.getEyePosition();
        mine.moveTo(eye.x, eye.y - 0.3, eye.z, p.getYRot(), 0);
        mine.setDeltaMovement(p.getLookAngle().scale(AbilityService.number(params, "throw_speed", .9)));
        Data d = data(p.server);
        Deque<UUID> mines = d.mines(p.getUUID());
        mines.addLast(mine.getUUID());
        int cap = maxCharges(p) * (int) AbilityService.number(params, "mines_per_charge", 3);
        while (mines.size() > cap) {
            Entity old = find(p.server, mines.pollFirst());
            if (old != null) old.discard();
        }
        d.setDirty();
        level.addFreshEntity(mine);
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.8f, 0.6f);
        return true;
    }

    /**
     * Semicírculo de alambre de espino a tu alrededor, hacia donde miras: 1 bloque de alto en el centro y 2 en las
     * esquinas. Solo ocupa huecos de aire; la trinchera anterior desaparece.
     */
    private static boolean digTrench(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        var params = Engineer.ability(Engineer.TRENCH);
        double radius = AbilityService.number(params, "radius", 3);
        double yaw = Math.toRadians(p.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Map<BlockPos, Integer> columns = new LinkedHashMap<>();
        int steps = (int) Math.ceil(Math.PI * radius * 3);
        for (int i = 0; i <= steps; i++) {
            double ang = -Math.PI / 2 + Math.PI * i / steps;
            Vec3 at = p.position().add(forward.scale(Math.cos(ang) * radius)).add(right.scale(Math.sin(ang) * radius));
            BlockPos column = BlockPos.containing(at.x, p.getY() + 0.5, at.z);
            int height = Math.abs(ang) > Math.toRadians(55) ? 2 : 1;
            columns.merge(column, height, Math::max);
        }
        List<BlockPos> placed = new ArrayList<>();
        Set<BlockPos> used = new HashSet<>();
        BlockState wire = EngineerRegistry.BARBED_WIRE.get().defaultBlockState();
        for (Map.Entry<BlockPos, Integer> e : columns.entrySet()) {
            BlockPos base = ground(level, e.getKey());
            if (base == null) continue;
            for (int h = 0; h < e.getValue(); h++) {
                BlockPos pos = base.above(h);
                if (!used.add(pos) || !level.getBlockState(pos).isAir() || !mayBuild(p, level, pos)) break;
                placed.add(pos);
            }
        }
        if (placed.isEmpty()) {
            fail(p, "cant_place");
            return false;
        }
        clearTrench(p.server, p.getUUID());
        int life = AbilityService.ticks(params, "max_seconds", 60);
        for (BlockPos pos : placed) {
            level.setBlock(pos, wire, 3);
            if (level.getBlockEntity(pos) instanceof BarbedWireBlock.Owner be) {
                be.owner = p.getUUID();
                be.setChanged();
                level.sendBlockUpdated(pos, wire, wire, 3);
            }
            level.scheduleTick(pos, wire.getBlock(), life);
        }
        Data d = data(p.server);
        d.trenches.put(p.getUUID(), new Trench(level.dimension().location(), placed));
        d.setDirty();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1f, 0.8f);
        return true;
    }

    /** Suelo de la columna: el primer hueco de aire con bloque sólido debajo, a un bloque arriba o abajo como mucho. */
    private static BlockPos ground(ServerLevel level, BlockPos column) {
        for (int dy : new int[] {0, 1, -1}) {
            BlockPos pos = column.above(dy);
            if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) return pos;
        }
        return null;
    }

    private static void clearTrench(MinecraftServer server, UUID owner) {
        Trench old = data(server).trenches.remove(owner);
        if (old == null) return;
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, old.dimension()));
        if (level == null) return;
        for (BlockPos pos : old.blocks()) {
            if (level.isLoaded(pos) && level.getBlockState(pos).is(EngineerRegistry.BARBED_WIRE.get())) level.removeBlock(pos, false);
        }
    }

    // ------------------------------------------------------------------ torretas: recogida y devolución

    /** Mano vacía: recoge todas las torretas (las cargas vuelven al momento). */
    static void recallAll(ServerPlayer p) {
        Map<UUID, ItemStack> turrets = data(p.server).turrets(p.getUUID());
        if (turrets.isEmpty()) return;
        int n = 0;
        for (Map.Entry<UUID, ItemStack> e : new ArrayList<>(turrets.entrySet())) {
            Entity loaded = find(p.server, e.getKey());
            if (loaded instanceof TurretEntity turret) {
                turret.recall();
            } else {
                // Chunk descargado: el arma guardada vuelve y la torreta desaparecerá al cargarse.
                turrets.remove(e.getKey());
                give(p, e.getValue());
            }
            n++;
        }
        data(p.server).setDirty();
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 1f, 1.2f);
        p.displayClientMessage(Component.translatable("message.rites_and_paths.engineer.turrets_recalled", n), true);
    }

    /** El arma de una torreta vuelve a su dueño (si está desconectado o muerto, cuando vuelva). */
    static void returnGun(TurretEntity turret, ItemStack gun, boolean broken) {
        MinecraftServer server = turret.getServer();
        UUID owner = turret.getOwnerUUID();
        if (server == null || owner == null) {
            turret.spawnAtLocation(gun);
            return;
        }
        Data d = data(server);
        d.turrets(owner).remove(turret.getUUID());
        d.setDirty();
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        if (p != null && broken && Engineer.is(p)) spend(p, Engineer.state(p));
        if (p != null && p.isAlive()) {
            give(p, gun);
        } else {
            d.pending.computeIfAbsent(owner, k -> new ArrayList<>()).add(gun.copy());
        }
    }

    private static void give(ServerPlayer p, ItemStack gun) {
        if (!gun.isEmpty()) ItemHandlerHelper.giveItemToPlayer(p, gun.copy());
    }

    private static void givePending(ServerPlayer p) {
        Data d = data(p.server);
        List<ItemStack> pending = d.pending.remove(p.getUUID());
        if (pending == null) return;
        for (ItemStack gun : pending) give(p, gun);
        d.setDirty();
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) givePending(p);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) givePending(p);
    }

    /** Torreta o mina que ya no existe para su dueño (recogida o reemplazada con el chunk descargado): no vuelve. */
    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !event.loadedFromDisk()) return;
        Entity e = event.getEntity();
        MinecraftServer server = e.getServer();
        if (server == null) return;
        if (e instanceof TurretEntity t && (t.getOwnerUUID() == null || !data(server).turrets(t.getOwnerUUID()).containsKey(t.getUUID()))) {
            event.setCanceled(true);
        } else if (e instanceof MineEntity m && (m.getOwnerUUID() == null || !data(server).mines(m.getOwnerUUID()).contains(m.getUUID()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity e = event.getEntity();
        MinecraftServer server = e.getServer();
        if (server == null) return;
        Entity.RemovalReason reason = e.getRemovalReason();
        boolean unloaded = reason == null || reason == Entity.RemovalReason.UNLOADED_TO_CHUNK
                || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER || reason == Entity.RemovalReason.CHANGED_DIMENSION;
        if (e instanceof TurretEntity t && t.getOwnerUUID() != null) {
            Map<UUID, ItemStack> turrets = data(server).turrets(t.getOwnerUUID());
            if (!turrets.containsKey(t.getUUID())) return;
            if (unloaded) {
                turrets.put(t.getUUID(), t.gun().copy()); // el arma con las balas que le queden
                data(server).setDirty();
            } else {
                returnGun(t, t.gun(), true); // borrada por otra cosa (comandos, limpieza de entidades)
            }
        } else if (e instanceof MineEntity m && m.getOwnerUUID() != null && !unloaded) {
            if (data(server).mines(m.getOwnerUUID()).remove(m.getUUID())) data(server).setDirty();
        }
    }

    /** Los monstruos que iban a por el dueño se lanzan contra una torreta suya cercana. */
    @SubscribeEvent
    public void onTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(event.getNewTarget() instanceof ServerPlayer p)) return;
        if (data(p.server).turrets(p.getUUID()).isEmpty()) return;
        TurretEntity closest = null;
        double best = mob.distanceToSqr(p);
        for (TurretEntity t : mob.level().getEntitiesOfClass(TurretEntity.class, mob.getBoundingBox().inflate(16),
                t -> p.getUUID().equals(t.getOwnerUUID()))) {
            double d = mob.distanceToSqr(t);
            if (d < best && mob.hasLineOfSight(t)) {
                best = d;
                closest = t;
            }
        }
        if (closest != null) event.setNewTarget(closest);
    }

    static Entity find(MinecraftServer server, UUID id) {
        if (id == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    /** Torretas de un jugador cargadas ahora (para el HUD y la ayuda). */
    static int deployed(ServerPlayer p) {
        return data(p.server).turrets(p.getUUID()).size();
    }

    // ------------------------------------------------------------------ datos del mundo

    record Trench(ResourceLocation dimension, List<BlockPos> blocks) {}

    static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, Data.NAME);
    }

    static final class Data extends SavedData {
        static final String NAME = "rites_and_paths_artificer";
        final Map<UUID, Map<UUID, ItemStack>> turrets = new HashMap<>();
        final Map<UUID, Deque<UUID>> mines = new HashMap<>();
        final Map<UUID, Trench> trenches = new HashMap<>();
        final Map<UUID, List<ItemStack>> pending = new HashMap<>();

        Map<UUID, ItemStack> turrets(UUID owner) {
            return turrets.computeIfAbsent(owner, k -> new LinkedHashMap<>());
        }

        Deque<UUID> mines(UUID owner) {
            return mines.computeIfAbsent(owner, k -> new ArrayDeque<>());
        }

        static Data load(CompoundTag tag) {
            Data d = new Data();
            for (Tag t : tag.getList("Owners", Tag.TAG_COMPOUND)) {
                CompoundTag o = (CompoundTag) t;
                UUID owner = o.getUUID("Id");
                for (Tag tt : o.getList("Turrets", Tag.TAG_COMPOUND)) {
                    CompoundTag c = (CompoundTag) tt;
                    d.turrets(owner).put(c.getUUID("Id"), ItemStack.of(c.getCompound("Gun")));
                }
                for (Tag mt : o.getList("Mines", Tag.TAG_INT_ARRAY)) d.mines(owner).addLast(NbtUtils.loadUUID(mt));
                if (o.contains("TrenchDim")) {
                    List<BlockPos> blocks = new ArrayList<>();
                    for (long l : o.getLongArray("Trench")) blocks.add(BlockPos.of(l));
                    d.trenches.put(owner, new Trench(new ResourceLocation(o.getString("TrenchDim")), blocks));
                }
                for (Tag pt : o.getList("Pending", Tag.TAG_COMPOUND)) {
                    d.pending.computeIfAbsent(owner, k -> new ArrayList<>()).add(ItemStack.of((CompoundTag) pt));
                }
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            Set<UUID> owners = new HashSet<>(turrets.keySet());
            owners.addAll(mines.keySet());
            owners.addAll(trenches.keySet());
            owners.addAll(pending.keySet());
            ListTag list = new ListTag();
            for (UUID owner : owners) {
                CompoundTag o = new CompoundTag();
                o.putUUID("Id", owner);
                ListTag ts = new ListTag();
                turrets.getOrDefault(owner, Map.of()).forEach((id, gun) -> {
                    CompoundTag c = new CompoundTag();
                    c.putUUID("Id", id);
                    c.put("Gun", gun.save(new CompoundTag()));
                    ts.add(c);
                });
                o.put("Turrets", ts);
                ListTag ms = new ListTag();
                for (UUID id : mines.getOrDefault(owner, new ArrayDeque<>())) ms.add(NbtUtils.createUUID(id));
                o.put("Mines", ms);
                Trench tr = trenches.get(owner);
                if (tr != null) {
                    o.putString("TrenchDim", tr.dimension().toString());
                    o.putLongArray("Trench", tr.blocks().stream().mapToLong(BlockPos::asLong).toArray());
                }
                ListTag ps = new ListTag();
                for (ItemStack gun : pending.getOrDefault(owner, List.of())) ps.add(gun.save(new CompoundTag()));
                o.put("Pending", ps);
                list.add(o);
            }
            tag.put("Owners", list);
            return tag;
        }
    }
}
