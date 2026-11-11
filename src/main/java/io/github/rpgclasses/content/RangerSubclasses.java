package io.github.rpgclasses.content;

import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.MechanicService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Subclases del Ranger (rev. 2.7d):
 * <ul>
 *   <li>Maestro de Bestias — Vínculo Salvaje ({@code wild_bond}): bonus a las 8 mascotas más cercanas (no invocaciones)
 *       y Salvaguarda de Bestias ({@code beast_safeguard}): golpe letal → 1 HP, vuelve al punto de reaparición y se sienta.</li>
 *   <li>Explorador Ágil — Escalada de Paredes: el movimiento lo hace el cliente (ClientSetup); aquí solo se anula la caída.</li>
 * </ul>
 * El Triple Salto y el Disparo Letal están en RangerAbilities; el recorte de velocidad de carga del Francotirador es un
 * modificador del atributo draw_speed (datapack).
 */
public final class RangerSubclasses {
    public static final String WILD_BOND = "wild_bond";
    public static final String SAFEGUARD = "beast_safeguard";
    public static final String WALL_CLIMB = Ids.NS + ":wall_climb";

    private static final UUID HEALTH_ID = UUID.fromString("2f6c1d8a-7b4e-4c39-a1d2-5e8f9a0b3c71");
    private static final UUID DAMAGE_ID = UUID.fromString("8a3e5b1c-2d7f-4e60-9b14-c6d0e2f4a835");
    private static final UUID SPEED_ID = UUID.fromString("c41d7e92-0a5b-4f83-8e26-1b9c3d5f7a04");
    private static final String NAME = "rpgclasses_ability:wild_bond";
    private static final String TAG_SAFEGUARD_UNTIL = "rpgclasses.safeguard_until";
    private static final String TAG_IDLE_UNTIL = "rpgclasses.safeguard_idle";
    private static final int PERIOD_TICKS = 20;

    /** Mascotas con bonus, por Ranger (para quitarlo cuando dejan de estar entre las 8 o el Ranger lo pierde). */
    private static final Map<UUID, Set<UUID>> BOOSTED = new HashMap<>();
    /** Mascotas salvadas pendientes de teletransporte (se hace al final del tick, fuera del evento de daño). */
    private static final List<Rescue> RESCUES = new ArrayList<>();
    private record Rescue(LivingEntity pet, UUID owner) {}

    // --- Vínculo Salvaje ---

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (!RESCUES.isEmpty()) {
            List<Rescue> todo = new ArrayList<>(RESCUES);
            RESCUES.clear();
            for (Rescue r : todo) rescue(server, r);
        }
        if (server.getTickCount() % PERIOD_TICKS != 0) return;
        for (ServerPlayer ranger : server.getPlayerList().getPlayers()) {
            if (ranger instanceof FakePlayer) continue;
            ActiveMechanic bond = MechanicService.find(ranger, WILD_BOND);
            Set<UUID> before = BOOSTED.getOrDefault(ranger.getUUID(), Set.of());
            Set<UUID> now = new HashSet<>();
            if (bond != null && ranger.isAlive()) {
                int max = (int) bond.number("max_pets", 8);
                List<LivingEntity> pets = ranger.serverLevel().getEntitiesOfClass(LivingEntity.class,
                        ranger.getBoundingBox().inflate(64), e -> isPetOf(e, ranger));
                pets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(ranger)));
                for (int i = 0; i < Math.min(max, pets.size()); i++) {
                    LivingEntity pet = pets.get(i);
                    boost(pet, bond.number("health", 0.35), bond.number("damage", 0.35), bond.number("speed", 0.25));
                    now.add(pet.getUUID());
                }
            }
            for (UUID id : before) {
                if (now.contains(id)) continue;
                if (ranger.serverLevel().getEntity(id) instanceof LivingEntity pet) unboost(pet);
            }
            if (now.isEmpty()) BOOSTED.remove(ranger.getUUID());
            else BOOSTED.put(ranger.getUUID(), now);
        }
    }

    /** Mascota domesticada del Ranger (lobos, gatos, loros, caballos...). Las invocaciones de Iron's no cuentan. */
    private static boolean isPetOf(Entity e, ServerPlayer ranger) {
        if (!(e instanceof OwnableEntity own) || !e.isAlive() || !ranger.getUUID().equals(own.getOwnerUUID())) return false;
        if (e instanceof AbstractHorse horse && !horse.isTamed()) return false;
        if (e.getPersistentData().contains("rpgclasses:summon_done")) return false;
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return type == null || !"irons_spellbooks".equals(type.getNamespace());
    }

    private static void boost(LivingEntity pet, double health, double damage, double speed) {
        set(pet, Attributes.MAX_HEALTH, HEALTH_ID, health);
        set(pet, Attributes.ATTACK_DAMAGE, DAMAGE_ID, damage);
        set(pet, Attributes.MOVEMENT_SPEED, SPEED_ID, speed);
    }

    private static void unboost(LivingEntity pet) {
        set(pet, Attributes.MAX_HEALTH, HEALTH_ID, 0);
        set(pet, Attributes.ATTACK_DAMAGE, DAMAGE_ID, 0);
        set(pet, Attributes.MOVEMENT_SPEED, SPEED_ID, 0);
        if (pet.getHealth() > pet.getMaxHealth()) pet.setHealth(pet.getMaxHealth());
    }

    /** Modificador transitorio (no se guarda: si el Ranger no está, la mascota vuelve a la normalidad al recargar). */
    private static void set(LivingEntity pet, Attribute attribute, UUID id, double value) {
        AttributeInstance inst = pet.getAttribute(attribute);
        if (inst == null) return;
        AttributeModifier old = inst.getModifier(id);
        if (old != null && Math.abs(old.getAmount() - value) < 1.0E-6) return;
        if (old != null) inst.removeModifier(id);
        if (value != 0) inst.addTransientModifier(new AttributeModifier(id, NAME, value, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    // --- Salvaguarda de Bestias ---

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onPetDamage(LivingDamageEvent event) {
        LivingEntity pet = event.getEntity();
        if (pet.level().isClientSide() || !(pet instanceof OwnableEntity own) || own.getOwnerUUID() == null) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || event.getAmount() < pet.getHealth()) return;
        ServerPlayer ranger = pet.level().getServer().getPlayerList().getPlayer(own.getOwnerUUID());
        if (ranger == null) return;
        ActiveMechanic m = MechanicService.find(ranger, SAFEGUARD);
        if (m == null || !isPetOf(pet, ranger)) return;
        long now = pet.level().getGameTime();
        if (pet.getPersistentData().getLong(TAG_SAFEGUARD_UNTIL) > now) return;
        pet.getPersistentData().putLong(TAG_SAFEGUARD_UNTIL, now + (long) (m.number("cooldown_seconds", 300) * 20));
        pet.getPersistentData().putLong(TAG_IDLE_UNTIL, now + (long) (m.number("idle_seconds", 60) * 20));
        event.setAmount(Math.max(0, pet.getHealth() - 1));
        RESCUES.add(new Rescue(pet, ranger.getUUID()));
    }

    private static void rescue(MinecraftServer server, Rescue r) {
        LivingEntity pet = r.pet();
        if (!pet.isAlive()) return;
        ServerPlayer ranger = server.getPlayerList().getPlayer(r.owner());
        ResourceKey<Level> dim = ranger != null ? ranger.getRespawnDimension() : Level.OVERWORLD;
        BlockPos pos = ranger != null ? ranger.getRespawnPosition() : null;
        ServerLevel dest = server.getLevel(dim);
        if (dest == null || pos == null) {
            dest = server.overworld();
            pos = dest.getSharedSpawnPos();
        }
        Vec3 at = Vec3.atBottomCenterOf(pos).add(0, 1, 0);
        pet.stopRiding();
        pet.ejectPassengers();
        Entity moved = pet;
        if (pet.level() == dest) {
            pet.teleportTo(at.x, at.y, at.z);
        } else {
            moved = pet.changeDimension(dest, new ITeleporter() {
                @Override
                public PortalInfo getPortalInfo(Entity entity, ServerLevel level, Function<ServerLevel, PortalInfo> defaultPortalInfo) {
                    return new PortalInfo(at, Vec3.ZERO, entity.getYRot(), entity.getXRot());
                }

                @Override
                public Entity placeEntity(Entity entity, ServerLevel current, ServerLevel destination, float yaw,
                                          Function<Boolean, Entity> repositionEntity) {
                    return repositionEntity.apply(false);
                }
            });
        }
        if (moved instanceof TamableAnimal tame) {
            tame.setOrderedToSit(true);
            tame.setInSittingPose(true);
            tame.setTarget(null);
        }
        if (moved instanceof net.minecraft.world.entity.Mob mob) mob.getNavigation().stop();
    }

    /** Sin órdenes durante unos segundos tras salvarse: no se le puede levantar ni montar. */
    @SubscribeEvent
    public void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getTarget().getPersistentData().getLong(TAG_IDLE_UNTIL) > event.getLevel().getGameTime()) event.setCanceled(true);
    }

    // --- Escalada de Paredes ---

    /** El cliente mueve; el servidor solo anula la caída acumulada mientras está adherido. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.getServer().getTickCount() % 2 != 0 || !player.isShiftKeyDown()) return;
        if (!AbilityService.has(player, WALL_CLIMB) || !canClimb(player)) return;
        player.resetFallDistance();
    }

    /** Agachado, tocando una pared y sin tensar arco/ballesta. Vale en cliente y servidor. */
    public static boolean canClimb(Player p) {
        if (p.isPassenger() || p.getAbilities().flying || p.isFallFlying() || p.isInWater() || p.isInLava()) return false;
        if (p.isUsingItem() && p.getUseItem().getItem() instanceof ProjectileWeaponItem) return false;
        return !p.level().noCollision(p, p.getBoundingBox().inflate(0.12, -0.1, 0.12));
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Set<UUID> pets = BOOSTED.remove(event.getEntity().getUUID());
        if (pets == null || !(event.getEntity() instanceof ServerPlayer ranger)) return;
        for (UUID id : pets) if (ranger.serverLevel().getEntity(id) instanceof LivingEntity pet) unboost(pet);
    }
}
