package io.github.rpgclasses.content.race;

import com.google.gson.JsonElement;
import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.content.CombatAbilities;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Habilidades activas de las subrazas. Se usan con las dos teclas raciales: ranura 0 y 1; agachado, 2 y 3
 * (si la subraza no tiene nada en la ranura agachada, se usa la normal). Enfriamientos guardados con el jugador.
 */
public final class RaceAbilities {
    private interface Handler {
        boolean run(ServerPlayer player, ActiveMechanic m);
    }

    private record Entry(String mechanic, int slot, Handler handler) {}

    private static final List<Entry> ENTRIES = List.of(
            new Entry("race_warcry", 0, RaceAbilities::warcry),
            new Entry("race_stone_sense", 0, RaceAbilities::stoneSense),
            new Entry("race_smoke_cloud", 0, RaceAbilities::smokeCloud),
            new Entry("race_snare", 0, RaceAbilities::snare),
            new Entry("race_tame_water", 0, RaceAbilities::tameWater),
            new Entry("race_wave", 1, RaceAbilities::wave),
            new Entry("race_water_wall", 2, RaceAbilities::waterWall),
            new Entry("race_nether_compass", 0, RaceAbilities::netherCompass),
            new Entry("race_portable_furnace", 0, RaceAbilities::portableFurnace),
            new Entry("race_ethereal", 0, RaceAbilities::ethereal));

    private static final TagKey<Structure> COMPASS_TARGETS = TagKey.create(Registries.STRUCTURE, RpgClasses.id("nether_compass"));
    private static final String TEMP_TAG = "rpgclasses.temp";
    public static final String SNARED_BY = "rpgclasses.snared_by";

    private record Cloud(ResourceKey<Level> dim, Vec3 center, double radius, long end, UUID owner, int slowness) {}
    private record Trap(ResourceKey<Level> dim, Vec3 pos, UUID owner, long end, double seconds, double bossSeconds, double pvpFactor) {}
    private record WaterKey(ResourceKey<Level> dim, BlockPos pos) {}
    private record Immunity(long end, Set<MobEffect> effects) {}

    private static final List<Cloud> CLOUDS = new ArrayList<>();
    private static final List<Trap> TRAPS = new ArrayList<>();
    private static final Map<WaterKey, Long> TEMP_WATER = new HashMap<>();
    private static final Map<Entity, Long> TEMP_ENTITIES = new HashMap<>();
    private static final Map<UUID, Immunity> CONTROL_IMMUNE = new HashMap<>();
    private static final Map<UUID, Long> ETHEREAL = new HashMap<>();

    /** El jugador pulsa una tecla racial. Todo se valida aquí. */
    public static void use(ServerPlayer player, int slot) {
        if (slot < 0 || slot > 3 || !player.isAlive() || player.isSpectator()) return;
        Entry entry = entryFor(player, slot);
        if (entry == null && slot >= 2) entry = entryFor(player, slot - 2);
        if (entry == null) {
            if (slot == 0) player.displayClientMessage(Component.translatable("message.rpgclasses.no_race_ability"), true);
            return;
        }
        ActiveMechanic m = Races.find(player, entry.mechanic());
        String key = Ids.NS + ":" + entry.mechanic();
        Component name = Component.translatable(Ids.langKey("mechanic", key, "name"));
        // Cargas (Ola del Moldeador en Rango 3): cada carga tiene su propio enfriamiento.
        int charges = Math.max(1, (int) m.number("charges", 1));
        long remaining = AbilityService.remainingTicks(player, key);
        for (int c = 2; c <= charges && remaining > 0; c++) {
            String other = key + "#" + c;
            long left = AbilityService.remainingTicks(player, other);
            if (left <= 0) {
                key = other;
                remaining = 0;
            } else {
                remaining = Math.min(remaining, left);
            }
        }
        if (remaining > 0) {
            player.displayClientMessage(Component.translatable("msg.rpgclasses.ability_cooldown", name,
                    String.format(Locale.ROOT, "%.1f", remaining / 20.0)), true);
            return;
        }
        boolean used;
        try {
            used = entry.handler().run(player, m);
        } catch (Throwable t) {
            DebugLog.error(player, "raza", t, "{} lanzó una excepción al activarse", key);
            return;
        }
        DebugLog.event(player, "raza", "{} pulsada: {}", key, used ? "usada" : "no se activó");
        if (!used) return;
        Rites.onAbilityUsed(player, entry.mechanic());
        AbilityService.setCooldown(player, key, (int) Math.round(m.number("cooldown_seconds", 0) * 20));
        player.displayClientMessage(Component.translatable("msg.rpgclasses.ability_used", name), true);
        io.github.rpgclasses.network.Net.syncPlayer(player);
    }

    private static Entry entryFor(ServerPlayer player, int slot) {
        for (Entry e : ENTRIES) if (e.slot() == slot && Races.has(player, e.mechanic())) return e;
        return null;
    }

    private static long now(ServerPlayer p) {
        return p.level().getGameTime();
    }

    private static void sound(ServerPlayer p, net.minecraft.sounds.SoundEvent sound) {
        p.level().playSound(null, p.blockPosition(), sound, SoundSource.PLAYERS, 1f, 1f);
    }

    // ------------------------------------------------------------------ Orco Caudillo

    private static boolean warcry(ServerPlayer p, ActiveMechanic m) {
        Set<MobEffect> effects = new HashSet<>();
        if (m.params().has("effects")) {
            for (JsonElement e : m.params().getAsJsonArray("effects")) {
                MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(e.getAsString()));
                if (effect != null) effects.add(effect);
            }
        }
        double radius = m.number("radius", 8);
        long immuneUntil = now(p) + Math.round(m.number("immunity_seconds", 0) * 20);
        // Aliados: el Caudillo, los jugadores que tiene como aliados y las mascotas e invocaciones de todos ellos.
        List<LivingEntity> allies = p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(radius),
                e -> e.isAlive() && e.distanceToSqr(p) <= radius * radius && io.github.rpgclasses.server.Allies.isAlly(p, e)
                        && io.github.rpgclasses.server.Allies.ownerOf(e) != null);
        for (LivingEntity ally : allies) {
            for (MobEffect effect : effects) ally.removeEffect(effect);
            if (immuneUntil > now(p)) CONTROL_IMMUNE.put(ally.getUUID(), new Immunity(immuneUntil, effects));
        }
        sound(p, SoundEvents.RAID_HORN.value());
        return true;
    }

    /** Voz del Caudillo (Rango 2+): inmunidad breve a los efectos de control que acaba de purgar. */
    static boolean isControlImmune(LivingEntity e, MobEffect effect) {
        Immunity immunity = CONTROL_IMMUNE.get(e.getUUID());
        if (immunity == null) return false;
        if (e.level().getGameTime() >= immunity.end()) {
            CONTROL_IMMUNE.remove(e.getUUID());
            return false;
        }
        return immunity.effects().contains(effect);
    }

    // ------------------------------------------------------------------ Enano de las Profundidades

    private static boolean stoneSense(ServerPlayer p, ActiveMechanic m) {
        if (!(p.level() instanceof ServerLevel level)) return false;
        int r = (int) m.number("radius", 8);
        BlockPos origin = p.blockPosition();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
            if (pos.distSqr(origin) > r * r) continue;
            BlockState state = level.getBlockState(pos);
            if (state.is(Tags.Blocks.ORES) || (state.is(Blocks.LAVA) && state.getFluidState().isSource())) found.add(pos.immutable());
        }
        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        long end = now(p) + Math.round(m.number("duration_seconds", 5) * 20);
        int shown = 0;
        for (BlockPos pos : found) {
            if (shown >= 96) break;
            Display.BlockDisplay display = EntityType.BLOCK_DISPLAY.create(level);
            if (display == null) break;
            CompoundTag tag = new CompoundTag();
            tag.put("block_state", NbtUtils.writeBlockState(level.getBlockState(pos)));
            tag.putBoolean("Glowing", true);
            display.load(tag);
            display.setPos(pos.getX(), pos.getY(), pos.getZ());
            display.getPersistentData().putBoolean(TEMP_TAG, true);
            level.addFreshEntity(display);
            TEMP_ENTITIES.put(display, end);
            shown++;
        }
        sound(p, SoundEvents.AMETHYST_BLOCK_CHIME);
        p.displayClientMessage(Component.translatable("message.rpgclasses.stone_sense", shown), true);
        return true;
    }

    /** Los marcadores de Sentido de la Piedra no deben sobrevivir a un reinicio del servidor. */
    @SubscribeEvent
    public void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getEntity().getPersistentData().getBoolean(TEMP_TAG)) event.setCanceled(true);
    }

    // ------------------------------------------------------------------ Elfo Oscuro

    private static boolean smokeCloud(ServerPlayer p, ActiveMechanic m) {
        CLOUDS.add(new Cloud(p.level().dimension(), p.position(), m.number("radius", 8),
                now(p) + Math.round(m.number("duration_seconds", 6) * 20), p.getUUID(), (int) m.number("slowness", 0)));
        sound(p, SoundEvents.FIRE_EXTINGUISH);
        return true;
    }

    private static void tickCloud(MinecraftServer server, Cloud c) {
        ServerLevel level = server.getLevel(c.dim());
        if (level == null) return;
        double r = c.radius();
        AABB box = AABB.ofSize(c.center(), 2 * r, 2 * r, 2 * r);
        List<LivingEntity> all = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e.position().distanceToSqr(c.center()) <= r * r);
        // Sistema de aliados: la nube oculta al Elfo y a sus aliados (y sus mascotas); a los enemigos no.
        Set<LivingEntity> inside = new HashSet<>();
        for (LivingEntity e : all) {
            if (io.github.rpgclasses.server.Allies.isAllyOf(server, c.owner(), e)) {
                // No se revela por cercanía a otros jugadores.
                e.addEffect(Races.hidden(MobEffects.INVISIBILITY, 15, 0));
                inside.add(e);
            } else if (c.slowness() > 0 && io.github.rpgclasses.server.Allies.isEnemyOf(server, c.owner(), e) && !Races.isBoss(e)) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, c.slowness() - 1, true, false, true));
            }
        }
        // Fuera de la nube, los mobs pierden de vista a los ocultos.
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box.inflate(24))) {
            LivingEntity target = mob.getTarget();
            if (target != null && inside.contains(target) && !inside.contains(mob)) mob.setTarget(null);
        }
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.center().x, c.center().y + 1, c.center().z,
                (int) (r * 8), r / 2, 1.2, r / 2, 0.005);
    }

    // ------------------------------------------------------------------ Goblin Artificiero

    private static boolean snare(ServerPlayer p, ActiveMechanic m) {
        if (!p.onGround()) return false;
        int max = Math.max(1, (int) m.number("traps", 1));
        List<Trap> mine = TRAPS.stream().filter(t -> t.owner().equals(p.getUUID())).toList();
        for (int i = 0; i <= mine.size() - max; i++) TRAPS.remove(mine.get(i)); // se retira la más antigua
        TRAPS.add(new Trap(p.level().dimension(), p.position(), p.getUUID(),
                now(p) + Math.round(m.number("lifetime_seconds", 60) * 20),
                m.number("root_seconds", 3), m.number("boss_root_seconds", 1), m.number("pvp_factor", 0.5)));
        sound(p, SoundEvents.TRIPWIRE_ATTACH);
        return true;
    }

    /** @return true si la trampa ha saltado y hay que retirarla. */
    private static boolean tickTrap(MinecraftServer server, Trap t, long time) {
        ServerLevel level = server.getLevel(t.dim());
        ServerPlayer owner = server.getPlayerList().getPlayer(t.owner());
        if (level == null || owner == null) return false;
        if (time % 10 == 0) level.sendParticles(ParticleTypes.CRIT, t.pos().x, t.pos().y + 0.1, t.pos().z, 2, 0.25, 0.02, 0.25, 0);
        AABB box = AABB.ofSize(t.pos().add(0, 0.5, 0), 1.4, 1.2, 1.4);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> CombatAbilities.canHit(owner, e))) {
            double seconds = Races.isBoss(e) ? t.bossSeconds() : t.seconds();
            if (e instanceof Player) seconds *= t.pvpFactor();
            int ticks = (int) Math.round(seconds * 20);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, true, true));
            e.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, 128, false, false, false));
            e.getPersistentData().putUUID(SNARED_BY, t.owner());
            Rites.onSnared(t.owner(), e);
            level.playSound(null, BlockPos.containing(t.pos()), SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 1f, 0.7f);
            DebugLog.event(owner, "raza", "Cepo: atrapa a {} durante {} s", DebugLog.describe(e), seconds);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ Tritón Moldeador

    private static boolean placeWater(ServerLevel level, BlockPos pos, long end) {
        if (level.dimensionType().ultraWarm()) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.isAir() || state.is(BlockTags.FIRE) || (state.canBeReplaced() && state.getFluidState().isEmpty()))) return false;
        level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        TEMP_WATER.put(new WaterKey(level.dimension(), pos.immutable()), end);
        return true;
    }

    private static boolean tameWater(ServerPlayer p, ActiveMechanic m) {
        if (!(p.level() instanceof ServerLevel level)) return false;
        Vec3 eye = p.getEyePosition();
        BlockHitResult block = level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(p.getLookAngle().scale(5)),
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        if (block.getType() != HitResult.Type.BLOCK) return false;
        boolean placed = placeWater(level, block.getBlockPos().relative(block.getDirection()),
                now(p) + Math.round(m.number("duration_seconds", 30) * 20));
        if (placed) sound(p, SoundEvents.BUCKET_EMPTY);
        return placed;
    }

    private static boolean wave(ServerPlayer p, ActiveMechanic m) {
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4) return false;
        Vec3 dir = look.normalize();
        double range = m.number("range", 6), strength = m.number("strength", 1.5);
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range),
                e -> CombatAbilities.canHit(p, e) && e.distanceToSqr(p) <= range * range)) {
            Vec3 to = e.position().subtract(p.position()).multiply(1, 0, 1);
            if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(dir) < 0.5) continue;
            e.knockback(strength, -dir.x, -dir.z);
            e.clearFire();
        }
        if (p.level() instanceof ServerLevel level) {
            for (int i = 1; i <= (int) range; i++) {
                Vec3 at = p.position().add(dir.scale(i));
                level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.6, at.z, 25, 0.6, 0.4, 0.6, 0.1);
            }
        }
        sound(p, SoundEvents.PLAYER_SPLASH_HIGH_SPEED);
        return true;
    }

    private static boolean waterWall(ServerPlayer p, ActiveMechanic m) {
        if (!(p.level() instanceof ServerLevel level)) return false;
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4) return false;
        Vec3 dir = look.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Vec3 center = p.position().add(dir.scale(2.5));
        long end = now(p) + Math.round(m.number("duration_seconds", 8) * 20);
        boolean placed = false;
        for (int i = -1; i <= 1; i++) {
            BlockPos base = BlockPos.containing(center.add(side.scale(i)));
            for (int y = 0; y < 3; y++) placed |= placeWater(level, base.above(y), end);
        }
        if (placed) sound(p, SoundEvents.BUCKET_EMPTY);
        return placed;
    }

    // ------------------------------------------------------------------ Infernal

    private static boolean netherCompass(ServerPlayer p, ActiveMechanic m) {
        if (!(p.level() instanceof ServerLevel level) || level.dimension() != Level.NETHER) {
            p.displayClientMessage(Component.translatable("message.rpgclasses.compass_not_nether"), true);
            return false;
        }
        BlockPos found = level.findNearestMapStructure(COMPASS_TARGETS, p.blockPosition(), 100, false);
        if (found == null) {
            p.displayClientMessage(Component.translatable("message.rpgclasses.compass_nothing"), true);
            return false;
        }
        int distance = (int) Math.sqrt(found.atY(p.getBlockY()).distSqr(p.blockPosition()));
        p.sendSystemMessage(Component.translatable("message.rpgclasses.compass_found", found.getX(), found.getZ(), distance));
        return true;
    }

    private static boolean portableFurnace(ServerPlayer p, ActiveMechanic m) {
        ItemStack hand = p.getMainHandItem();
        if (hand.isEmpty()) return false;
        Optional<SmeltingRecipe> recipe = p.level().getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SimpleContainer(hand.copyWithCount(1)), p.level());
        if (recipe.isEmpty()) return false;
        ItemStack result = recipe.get().getResultItem(p.level().registryAccess());
        if (result.isEmpty()) return false;
        int count = Math.min(hand.getCount(), (int) m.number("max_items", 16));
        int needed = count * 200, available = 0;
        int selected = p.getInventory().selected;
        for (int i = 0; i < p.getInventory().items.size() && available < needed; i++) {
            ItemStack fuel = p.getInventory().items.get(i);
            if (i == selected || fuel.isEmpty() || fuel.hasCraftingRemainingItem()) continue;
            available += ForgeHooks.getBurnTime(fuel, RecipeType.SMELTING) * fuel.getCount();
        }
        if (available < needed) {
            p.displayClientMessage(Component.translatable("message.rpgclasses.furnace_no_fuel"), true);
            return false;
        }
        for (int i = 0; i < p.getInventory().items.size() && needed > 0; i++) {
            ItemStack fuel = p.getInventory().items.get(i);
            if (i == selected || fuel.isEmpty() || fuel.hasCraftingRemainingItem()) continue;
            int burn = ForgeHooks.getBurnTime(fuel, RecipeType.SMELTING);
            while (burn > 0 && needed > 0 && !fuel.isEmpty()) {
                fuel.shrink(1);
                needed -= burn;
            }
        }
        hand.shrink(count);
        p.getInventory().placeItemBackInInventory(result.copyWithCount(result.getCount() * count));
        sound(p, SoundEvents.BLASTFURNACE_FIRE_CRACKLE);
        return true;
    }

    // ------------------------------------------------------------------ No-muerto Espectro

    private static boolean ethereal(ServerPlayer p, ActiveMechanic m) {
        int ticks = (int) Math.round(m.number("duration_seconds", 2) * 20);
        if (ticks <= 0) return false;
        ETHEREAL.put(p.getUUID(), now(p) + ticks);
        p.addEffect(Races.hidden(MobEffects.INVISIBILITY, ticks, 0));
        sound(p, SoundEvents.PHANTOM_FLAP);
        return true;
    }

    public static boolean isEthereal(Entity e) {
        Long end = ETHEREAL.get(e.getUUID());
        return end != null && e.level().getGameTime() < end;
    }

    /** Forma Etérea: ni recibe daño (salvo el sol) ni puede atacar. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide() || ETHEREAL.isEmpty()) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || event.getSource().is(Races.SUNLIGHT)) return;
        Entity attacker = event.getSource().getEntity();
        if (isEthereal(event.getEntity()) || (attacker instanceof ServerPlayer && !(attacker instanceof FakePlayer) && isEthereal(attacker))) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        long time = server.overworld().getGameTime();

        CLOUDS.removeIf(c -> time >= c.end());
        if (time % 5 == 0) for (Cloud c : CLOUDS) tickCloud(server, c);

        if (time % 2 == 0) TRAPS.removeIf(t -> time >= t.end() || tickTrap(server, t, time));

        for (Iterator<Map.Entry<WaterKey, Long>> it = TEMP_WATER.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<WaterKey, Long> e = it.next();
            if (time < e.getValue()) continue;
            ServerLevel level = server.getLevel(e.getKey().dim());
            if (level != null && level.isLoaded(e.getKey().pos()) && level.getBlockState(e.getKey().pos()).is(Blocks.WATER)) {
                level.setBlockAndUpdate(e.getKey().pos(), Blocks.AIR.defaultBlockState());
            }
            it.remove();
        }
        for (Iterator<Map.Entry<Entity, Long>> it = TEMP_ENTITIES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Entity, Long> e = it.next();
            if (time < e.getValue()) continue;
            e.getKey().discard();
            it.remove();
        }
        ETHEREAL.values().removeIf(end -> time >= end);
    }
}
