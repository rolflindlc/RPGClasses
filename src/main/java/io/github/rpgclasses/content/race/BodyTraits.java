package io.github.rpgclasses.content.race;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.registry.ModAttributes;
import io.github.rpgclasses.server.Allies;
import io.github.rpgclasses.server.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Troncos de las razas de la adenda 0.7 que cambian el cuerpo: Slime, Coloso y Centauro.
 * El movimiento del jugador lo calcula su cliente, así que el rebote del Slime, el hundirse del Coloso y el salto
 * cargado del Centauro están en {@code client.RaceBodyClient}; aquí va lo que decide el servidor.
 * <ul>
 *   <li>Slime: sin daño de caída, más retroceso recibido.</li>
 *   <li>Coloso: rotura en área, pisa cultivos, caída segura escalada, sin montar.</li>
 *   <li>Centauro: aceleración, embestida, caída segura, pasajeros aliados, sin montar.</li>
 * </ul>
 */
public final class BodyTraits {
    public static final TagKey<Block> TRAMPLABLE = TagKey.create(Registries.BLOCK, RpgClasses.id("colossus_tramplable"));
    private static final UUID CENTAUR_SPEED = UUID.fromString("6f3c2a1e-5b7d-4c11-9a0e-2f6d7c8b9e01");
    private static final double VANILLA_SAFE_FALL = 3.0;
    private static final double VANILLA_JUMP_HEIGHT = 1.25;
    /** Velocidad de carrera vanilla (atributo 0,1 × 1,3 al correr). */
    private static final double VANILLA_SPRINT = 0.13;

    /** Fracción de la velocidad racial máxima del Centauro (0–1), por jugador (servidor). */
    private static final Map<UUID, Double> GALLOP = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_DIR = new HashMap<>();
    /**
     * Última posición vista de cada Centauro. En el servidor el movimiento del jugador llega por paquetes entre ticks
     * (xo/zo ya valen la posición actual en el tick y getDeltaMovement no lo refleja): se compara con la anterior.
     */
    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
    /** Tick en que se movió por última vez (los paquetes de movimiento no llegan siempre cada tick). */
    private static final Map<UUID, Long> LAST_MOVED = new HashMap<>();
    /** Guerrero: tick de su último ataque al galope (el ataque vanilla corta la carrera; aquí se mantiene). */
    private static final Map<UUID, Long> WAR_ATTACK = new HashMap<>();
    /** Embestida: último golpe a cada víctima (para no golpearla cada tick). */
    private static final Map<UUID, Map<UUID, Long>> RAM_HITS = new HashMap<>();
    private static final ThreadLocal<Boolean> AREA_BREAKING = ThreadLocal.withInitial(() -> false);

    // ------------------------------------------------------------------ consultas

    /** Fracción de la velocidad máxima racial del Centauro (0 si no lo es). La usan el Guerrero y los ritos. */
    public static double gallop(Player p) {
        return GALLOP.getOrDefault(p.getUUID(), 0.0);
    }

    /**
     * En el cliente (jugador local): la fracción se deduce del modificador de velocidad del galope, que está
     * sincronizado (sin contar la reducción por pasajeros ni el límite técnico).
     */
    public static double clientGallop(Player p) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeModifier m = speed == null ? null : speed.getModifier(CENTAUR_SPEED);
        double max = Races.number(p, "race_centaur", "max_speed", 0.4);
        return m == null || max <= 0 ? 0 : Math.min(1, m.getAmount() / max);
    }

    /** Pasajeros que admite el jugador (Centauro o Porteador); 0 si ninguno. */
    public static int maxPassengers(Player p) {
        double n = Math.max(Races.number(p, "race_centaur", "passengers", 0), Races.number(p, "race_belt", "passengers", 0));
        return (int) Math.round(n);
    }

    // ------------------------------------------------------------------ caída y retroceso

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        // Slime: nunca recibe daño de caída (el rebote lo hace el cliente).
        if (Races.has(p, "race_bouncy")) {
            event.setDamageMultiplier(0);
            return;
        }
        // Coloso y Centauro: la caída segura crece con el cuerpo (6 bloques en vez de 3). El salto extra ya se
        // descuenta en AttributeHandlers, así que aquí solo se resta lo que falta.
        double height = Math.max(Races.number(p, "race_giant_fall", "fall_height", 0), Races.number(p, "race_centaur", "fall_height", 0));
        height = Math.max(height, ColossusForms.fallHeight(p));
        if (height <= VANILLA_SAFE_FALL) return;
        double alreadyFromJump = VANILLA_JUMP_HEIGHT * Math.max(0, ModAttributes.value(p, ModAttributes.JUMP_BONUS));
        double extra = Math.max(0, height - VANILLA_SAFE_FALL - alreadyFromJump);
        event.setDistance((float) Math.max(0, event.getDistance() - extra));
    }

    /** Slime: +50 % de retroceso recibido (el atributo de resistencia al retroceso no admite valores negativos). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onKnockback(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        double extra = Races.number(p, "race_soft_body", "knockback_taken", 0);
        if (extra > 0) event.setStrength((float) (event.getStrength() * (1 + extra)));
    }

    // ------------------------------------------------------------------ montar

    /** Coloso y Centauro: no montan animales ni barcas (sí pueden llevar pasajeros). */
    @SubscribeEvent
    public void onMount(EntityMountEvent event) {
        if (!event.isMounting() || !(event.getEntityMounting() instanceof Player p)) return;
        if (event.getEntityBeingMounted() instanceof Player) return;
        if (Races.has(p, "race_no_mount")) event.setCanceled(true);
    }

    /** Un aliado con la mano vacía hace clic derecho en el Centauro (o el Porteador) para montarse. */
    @SubscribeEvent
    public void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer rider) || rider instanceof FakePlayer
                || !(event.getTarget() instanceof ServerPlayer mount) || mount instanceof FakePlayer) return;
        if (!rider.getMainHandItem().isEmpty() || rider.isPassenger() || rider.isShiftKeyDown()) return;
        int max = maxPassengers(mount);
        if (max <= 0 || mount.getPassengers().size() >= max || !Allies.isAlly(mount, rider) || mount.isPassenger()) return;
        if (rider.startRiding(mount)) {
            mount.level().playSound(null, mount.blockPosition(), SoundEvents.HORSE_SADDLE, SoundSource.PLAYERS, 0.6f, 1.2f);
            DebugLog.event(rider, "raza", "se monta sobre {}", mount.getGameProfile().getName());
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    // ------------------------------------------------------------------ Coloso: rotura en área

    /** Bloques del cubo de rotura (sin incluir el que se rompe). Válido en ambos lados. */
    public static List<BlockPos> areaFor(Player p, BlockPos origin, int size) {
        List<BlockPos> out = new ArrayList<>();
        if (size <= 1) return out;
        double reach = p.getBlockReach() + 1;
        HitResult hit = p.pick(reach, 1f, false);
        Direction face = hit instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK && bh.getBlockPos().equals(origin)
                ? bh.getDirection() : Direction.getNearest(p.getLookAngle().x, p.getLookAngle().y, p.getLookAngle().z).getOpposite();
        Vec3 at = hit.getLocation();
        int[] from = new int[3], to = new int[3];
        Direction.Axis[] axes = Direction.Axis.values();
        for (int a = 0; a < 3; a++) {
            Direction.Axis axis = axes[a];
            if (axis == face.getAxis()) {
                // Hacia dentro del bloque (opuesto a la cara que miras).
                int inward = -face.getAxisDirection().getStep();
                from[a] = Math.min(0, inward * (size - 1));
                to[a] = Math.max(0, inward * (size - 1));
            } else if (size % 2 == 1) {
                from[a] = -(size / 2);
                to[a] = size / 2;
            } else {
                // Lado par: se extiende hacia la mitad del bloque a la que apunta la mira.
                double frac = axis.choose(at.x, at.y, at.z) - axis.choose(origin.getX(), origin.getY(), origin.getZ());
                int dir = frac >= 0.5 ? 1 : -1;
                from[a] = Math.min(0, dir * (size - 1));
                to[a] = Math.max(0, dir * (size - 1));
            }
        }
        for (int x = from[0]; x <= to[0]; x++) {
            for (int y = from[1]; y <= to[1]; y++) {
                for (int z = from[2]; z <= to[2]; z++) {
                    if (x != 0 || y != 0 || z != 0) out.add(origin.offset(x, y, z));
                }
            }
        }
        return out;
    }

    /** Lado del cubo de rotura del jugador ahora mismo (1 = sin área). Agachado siempre 1. */
    public static int areaSize(Player p) {
        if (p.isShiftKeyDown()) return 1;
        int size = (int) Math.round(Races.number(p, "race_area_break", "size", 1));
        return Math.max(size, ColossusForms.areaSize(p));
    }

    /** Solo lo que la herramienta rompe de forma efectiva; nunca contenedores ni bloques con datos; nunca irrompibles. */
    public static boolean breakable(Player p, BlockPos pos, BlockState state) {
        if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) return false;
        float hardness = state.getDestroySpeed(p.level(), pos);
        if (hardness < 0) return false;
        ItemStack tool = p.getMainHandItem();
        if (!p.hasCorrectToolForDrops(state)) return false;
        return tool.getDestroySpeed(state) > 1f || hardness <= 0.6f;
    }

    /** La rotura tarda lo que tarde el bloque más lento del cubo. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (AREA_BREAKING.get() || event.getPosition().isEmpty()) return;
        Player p = event.getEntity();
        int size = areaSize(p);
        if (size <= 1) return;
        BlockPos origin = event.getPosition().get();
        float own = event.getState().getDestroyProgress(p, p.level(), origin);
        if (own <= 0) return;
        float slowest = own;
        AREA_BREAKING.set(true);
        try {
            for (BlockPos q : areaFor(p, origin, size)) {
                BlockState st = p.level().getBlockState(q);
                if (!breakable(p, q, st)) continue;
                slowest = Math.min(slowest, st.getDestroyProgress(p, p.level(), q));
            }
        } finally {
            AREA_BREAKING.set(false);
        }
        if (slowest < own) event.setNewSpeed(event.getNewSpeed() * slowest / own);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || AREA_BREAKING.get() || !(event.getPlayer() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        int size = areaSize(p);
        if (size <= 1 || !breakable(p, event.getPos(), event.getState())) return;
        List<BlockPos> extra = areaFor(p, event.getPos(), size);
        int broken = 1;
        AREA_BREAKING.set(true);
        try {
            for (BlockPos q : extra) {
                BlockState st = p.level().getBlockState(q);
                if (!breakable(p, q, st)) continue;
                if (p.getMainHandItem().isDamageableItem() && p.getMainHandItem().getDamageValue() >= p.getMainHandItem().getMaxDamage() - 1) break;
                // destroyBlock lanza el evento de rotura de cada bloque (zonas protegidas) y gasta durabilidad.
                if (p.gameMode.destroyBlock(q)) broken++;
            }
        } finally {
            AREA_BREAKING.set(false);
        }
        Rites.count(p, "area_blocks", broken);
    }

    // ------------------------------------------------------------------ tick de servidor

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p instanceof FakePlayer || !p.isAlive()) return;
        DebugLog.guard(p, "cuerpo racial", () -> {
            trample(p);
            centaurTick(p);
        });
    }

    /** Coloso: pisa los cultivos (agachado no). Cada 4 ticks, los bloques bajo sus pies. */
    private static void trample(ServerPlayer p) {
        if (p.tickCount % 4 != 0 || p.isShiftKeyDown() || p.isSpectator() || !p.onGround()) return;
        if (!Races.has(p, "race_trample") && !ColossusForms.isGrown(p)) return;
        if (!p.mayBuild()) return;
        ServerLevel level = p.serverLevel();
        var box = p.getBoundingBox();
        int y = net.minecraft.util.Mth.floor(box.minY + 0.01);
        for (int x = net.minecraft.util.Mth.floor(box.minX); x <= net.minecraft.util.Mth.floor(box.maxX - 1e-4); x++) {
            for (int z = net.minecraft.util.Mth.floor(box.minZ); z <= net.minecraft.util.Mth.floor(box.maxZ - 1e-4); z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (level.getBlockState(pos).is(TRAMPLABLE) && level.mayInteract(p, pos)) level.destroyBlock(pos, true, p);
            }
        }
    }

    /** Centauro: aceleración gradual al correr en línea recta y embestida desde el 40 %. */
    private static void centaurTick(ServerPlayer p) {
        ActiveMechanic c = Races.find(p, "race_centaur");
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (c == null) {
            if (GALLOP.remove(p.getUUID()) != null && speed != null) speed.removeModifier(CENTAUR_SPEED);
            return;
        }
        UUID id = p.getUUID();
        long now = p.level().getGameTime();
        double frac = GALLOP.getOrDefault(id, 0.0);
        Vec3 pos = p.position();
        Vec3 prevPos = LAST_POS.put(id, pos);
        Vec3 flat = prevPos == null ? Vec3.ZERO : new Vec3(pos.x - prevPos.x, 0, pos.z - prevPos.z);
        if (flat.lengthSqr() > 4) flat = Vec3.ZERO; // teletransporte
        if (flat.lengthSqr() > 0.0025) LAST_MOVED.put(id, now);
        double accelTicks = Math.max(1, c.number("accel_seconds", 5) * 20);
        // Guerrero: atacar al galope no corta la carrera (vanilla deja de correr al golpear corriendo).
        boolean warAttack = now - WAR_ATTACK.getOrDefault(id, Long.MIN_VALUE / 2) <= 3;
        if (warAttack && !p.isSprinting() && !p.isShiftKeyDown()) p.setSprinting(true);
        boolean sprinting = p.isSprinting() || warAttack;
        boolean blocked = p.isPassenger() || p.isFallFlying() || p.isInWater();
        if (sprinting && !blocked && flat.lengthSqr() <= 0.0025 && now - LAST_MOVED.getOrDefault(id, 0L) <= 3) {
            // Sin paquete de movimiento en este tick: se mantiene la velocidad acumulada.
        } else if (sprinting && !blocked && flat.lengthSqr() > 0.0025) {
            Vec3 dir = flat.normalize();
            Vec3 last = LAST_DIR.put(id, dir);
            // Un giro brusco (más de ~25° en un tick) reduce la velocidad acumulada.
            if (last != null && last.dot(dir) < 0.9) frac *= 0.85;
            else frac = Math.min(1, frac + 1 / accelTicks);
        } else {
            LAST_DIR.remove(id);
            frac = Math.max(0, frac - (sprinting ? 0.02 : 0.1));
        }
        frac = ram(p, c, frac, flat);
        GALLOP.put(id, frac);

        // Velocidad racial: fuera del tope global (adenda 0.7, regla 8), con límite técnico y pasajeros que frenan.
        if (speed == null) return;
        double bonus = c.number("max_speed", 0.4) * frac * Math.max(0, 1 - c.number("passenger_penalty", 0.1) * p.getPassengers().size());
        AttributeModifier current = speed.getModifier(CENTAUR_SPEED);
        double limit = c.number("speed_limit", 3.0) * VANILLA_SPRINT / 1.3; // en valor del atributo (sin el ×1,3 de correr)
        double without = speed.getValue() / (current == null ? 1 : 1 + current.getAmount());
        if (without > 0) bonus = Math.min(bonus, Math.max(0, limit / without - 1));
        bonus = Math.round(bonus * 200) / 200.0; // pasos de 0,5 % para no reenviar el atributo (sincronizado) cada tick
        if (current != null && current.getAmount() == bonus) return;
        speed.removeModifier(CENTAUR_SPEED);
        if (bonus > 0) {
            speed.addTransientModifier(new AttributeModifier(CENTAUR_SPEED, "rpgclasses centaur gallop", bonus, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /** Embestida: al chocar con algo a ≥40 % de la velocidad máxima le hace daño y lo empuja; pierde velocidad. */
    private static double ram(ServerPlayer p, ActiveMechanic c, double frac, Vec3 moved) {
        double threshold = c.number("ram_threshold", 0.4);
        if (frac < threshold || p.isSpectator()) return frac;
        Vec3 dir = moved.lengthSqr() > 1e-6 ? moved.normalize() : Vec3.directionFromRotation(0, p.getYRot());
        var box = p.getBoundingBox().inflate(0.25).expandTowards(dir.scale(0.4));
        long now = p.level().getGameTime();
        Map<UUID, Long> hits = RAM_HITS.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        hits.values().removeIf(t -> now - t > 20);
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive() && !e.isPassengerOfSameVehicle(p)
                && e.getVehicle() != p && !hits.containsKey(e.getUUID()))) {
            if (!Allies.isEnemy(p, e)) continue;
            double scale = 0.25 + 0.75 * Math.min(1, (frac - threshold) / Math.max(1e-6, 1 - threshold));
            double bonus = Races.number(p, "race_horn", "ram_bonus", 0);
            float damage = (float) (c.number("ram_damage", 6) * scale * (1 + bonus));
            double push = c.number("ram_knockback", 4) * scale * 0.25 * (Races.isBoss(e) ? 0.5 : 1);
            hits.put(e.getUUID(), now);
            Rites.dealing("ram", () -> e.hurt(p.damageSources().playerAttack(p), damage));
            e.knockback(push, -dir.x, -dir.z);
            e.hurtMarked = true;
            p.level().playSound(null, e.blockPosition(), SoundEvents.HORSE_LAND, SoundSource.PLAYERS, 1f, 0.8f);
            double loss = Races.number(p, "race_war_gallop", "ram_speed_loss", c.number("ram_speed_loss", 0.75));
            frac *= 1 - loss;
            DebugLog.event(p, "raza", "embestida a {} ({} de daño, velocidad {})", e.getName().getString(), damage, frac);
            if (frac < threshold) break;
        }
        return frac;
    }

    /** Guerrero: un ataque al galope (≥40 %) no le hace dejar de correr. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        if (Races.has(p, "race_war_gallop") && gallop(p) >= Races.number(p, "race_centaur", "ram_threshold", 0.4) && p.isSprinting()) {
            WAR_ATTACK.put(p.getUUID(), p.level().getGameTime());
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        GALLOP.remove(id);
        LAST_DIR.remove(id);
        RAM_HITS.remove(id);
        LAST_POS.remove(id);
        LAST_MOVED.remove(id);
        WAR_ATTACK.remove(id);
    }
}
