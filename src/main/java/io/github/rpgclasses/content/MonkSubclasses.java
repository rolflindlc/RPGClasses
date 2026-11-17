package io.github.rpgclasses.content;

import com.google.gson.JsonObject;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.registry.ModAttributes;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.MechanicService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.particles.ParticleTypes;
import io.github.rpgclasses.content.race.Races;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Subclases del Monje (rev. 2.7d) y la Marcha Elemental de Paso Ligero:
 * <ul>
 *   <li>Maestro de la Brisa — Impulso de Viento: activa, dash horizontal en el aire (1 carga = cooldown de recarga).</li>
 *   <li>Palma Pesada — empuje desarmado (atributo unarmed_knockback) y Puño de Inhabilitación ({@code disabling_fist}).</li>
 *   <li>Guardián Trimundial — Afinidad Corporal ({@code stance_affinity}) y Manifiesto ({@code elemental_manifest}) por postura.</li>
 *   <li>Paso Ligero (Nivel Total 30): caminar sobre el agua, y sobre la lava en Postura de Fuego. El movimiento es del cliente.</li>
 * </ul>
 */
public final class MonkSubclasses {
    public static final String WIND_DASH = Ids.NS + ":wind_dash";
    public static final String DISABLING_FIST = "disabling_fist";
    public static final String AFFINITY = "stance_affinity";
    public static final String MANIFEST = "elemental_manifest";

    private static final UUID SWIM_ID = UUID.fromString("6e1f3a9c-4b2d-4f7e-8c05-a9d3b7e1f240");
    private static final String SWIM_NAME = "rites_and_paths_ability:elemental_manifest";
    private static final String TAG_FIST_UNTIL = "rites_and_paths.disabling_fist.";
    /** Golpe desarmado de este tick: el empuje se multiplica en LivingKnockBackEvent. */
    private static final Map<Integer, Double> KNOCKBACK = new HashMap<>();
    private static final Map<UUID, Boolean> REFLECTING = new HashMap<>();

    public static void register() {
        AbilityService.register(WIND_DASH, MonkSubclasses::windDash);
    }

    // --- Impulso de Viento ---

    private static boolean windDash(ServerPlayer player, JsonObject p) {
        if (player.onGround() || player.isInWater() || player.isPassenger() || player.getAbilities().flying) return false;
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) return false;
        double speed = AbilityService.number(p, "dash_speed", 1.3); // [MEDIR] ~6 bloques
        player.setDeltaMovement(flat.normalize().scale(speed).add(0, 0.12, 0));
        player.hurtMarked = true;
        player.resetFallDistance();
        // Con 1 carga, la recarga es un cooldown normal.
        AbilityService.setCooldown(player, WIND_DASH, AbilityService.ticks(p, "recharge_seconds", 10));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.0f, 1.4f);
        return true;
    }

    // --- Golpes desarmados ---

    public static final String CHARGED_PALM = "charged_palm";
    public static final String IMPACT = "palm_impact";

    /** Palma Pesada: ticks seguidos agachado (carga del Golpe Cargado). */
    private static final Map<UUID, Integer> CROUCH = new HashMap<>();
    /** Objetivos lanzados por la Palma: pueden chocar contra un bloque u otro enemigo durante un momento. */
    private record Thrown(UUID attacker, long until, double hit) {}
    private static final Map<Integer, Thrown> THROWN = new HashMap<>();

    /**
     * Llamado por DamagePipeline tras un golpe desarmado del Monje. Devuelve el factor de daño (Golpe Cargado de
     * Palma Pesada) y prepara el empuje y el Impacto.
     */
    public static double onUnarmedHit(ServerPlayer attacker, LivingEntity target, double hit) {
        double factor = 1;
        double knock = ModAttributes.value(attacker, ModAttributes.UNARMED_KNOCKBACK);

        // Golpe Cargado: agachado, la palma se carga hasta 1 s; más daño y empuje, y a carga completa rompe la guardia.
        ActiveMechanic charged = MechanicService.find(attacker, CHARGED_PALM);
        if (charged != null && attacker.isShiftKeyDown()) {
            double f = Math.min(1, CROUCH.getOrDefault(attacker.getUUID(), 0) / (charged.number("charge_seconds", 1) * 20));
            CROUCH.put(attacker.getUUID(), 0);
            if (f > 0.1) {
                factor *= 1 + f * charged.number("damage_bonus", 0.5);
                knock = (1 + knock) * (1 + f * charged.number("knockback_bonus", 1.0)) - 1;
                ClassFx.burst(target, ParticleTypes.CLOUD, (int) (6 + 14 * f), 0.15);
                attacker.level().playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1f, 0.6f);
                if (f >= 1 && target instanceof Player blocker && blocker.isBlocking()) {
                    blocker.disableShield(true); // rompe la guardia
                }
            }
        }
        if (knock > 0) KNOCKBACK.put(target.getId(), 1 + knock);

        // Impacto: el objetivo lanzado que choca contra un bloque o un enemigo recibe daño y queda aturdido.
        ActiveMechanic impact = MechanicService.find(attacker, IMPACT);
        if (impact != null && target.isAlive() && !Races.isBoss(target)) {
            THROWN.put(target.getId(), new Thrown(attacker.getUUID(),
                    attacker.level().getGameTime() + Math.round(impact.number("window_seconds", 1) * 20), hit * factor));
        }

        ActiveMechanic manifest = MechanicService.find(attacker, MANIFEST);
        if (manifest != null && "fire".equals(stance(attacker)) && target.isAlive()) {
            MonkCharges.addBurn(target, manifest.number("ignite_seconds", 6)); // regla 7: se suma a la quemadura
        }
        return factor;
    }

    /** Cada tic: los objetivos lanzados que chocan contra un bloque u otro enemigo sufren el Impacto. */
    @SubscribeEvent
    public void onImpactTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || THROWN.isEmpty()) return;
        var server = event.getServer();
        var it = THROWN.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Thrown t = e.getValue();
            ServerPlayer attacker = server.getPlayerList().getPlayer(t.attacker());
            if (attacker == null || attacker.level().getGameTime() > t.until()) {
                it.remove();
                continue;
            }
            if (!(attacker.level().getEntity(e.getKey()) instanceof LivingEntity target) || !target.isAlive()) {
                it.remove();
                continue;
            }
            LivingEntity other = null;
            for (LivingEntity o : attacker.level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(0.2),
                    o -> o != target && o != attacker && CombatAbilities.canHit(attacker, o))) {
                other = o;
                break;
            }
            if (!target.horizontalCollision && other == null) continue;
            it.remove();
            impact(attacker, target, t.hit());
            if (other != null) impact(attacker, other, t.hit());
        }
    }

    private static void impact(ServerPlayer attacker, LivingEntity target, double hit) {
        ActiveMechanic m = MechanicService.find(attacker, IMPACT);
        if (m == null) return;
        float damage = (float) (m.number("flat_damage", 2) + hit * m.number("damage_fraction", 0.4));
        target.invulnerableTime = 0;
        target.hurt(attacker.damageSources().flyIntoWall(), damage);
        // Puño de Inhabilitación: ahora sale con cada Impacto (comparte el registro de aturdimientos, regla 6).
        ActiveMechanic fist = MechanicService.find(attacker, DISABLING_FIST);
        int ticks = (int) Math.round((fist != null ? fist.number("duration_seconds", 1.5) : m.number("stun_seconds", 1)) * 20);
        int icd = (int) Math.round((fist != null ? fist.number("icd_seconds", 6) : 6) * 20);
        if (MonkCharges.tryStun(attacker, target, icd)) {
            if (fist != null) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1), attacker);
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0), attacker);
            }
            CombatAbilities.stun(target, ticks);
        }
        if (attacker.level() instanceof net.minecraft.server.level.ServerLevel level) {
            level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 15, 0.4, 0.4, 0.4, 0.3);
        }
        attacker.level().playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 0.7f);
    }

    @SubscribeEvent
    public void onKnockback(LivingKnockBackEvent event) {
        Double factor = KNOCKBACK.remove(event.getEntity().getId());
        if (factor != null) event.setStrength((float) (event.getStrength() * factor));
    }

    // --- Daño recibido: Afinidad Corporal y reflejo de Piedra ---

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDefend(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || event.getAmount() <= 0) return;
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        String stance = stance(player);
        ActiveMechanic affinity = MechanicService.find(player, AFFINITY);
        if (affinity != null) {
            double reduction = switch (stance) {
                case "fire" -> source.is(DamageTypeTags.IS_FIRE) ? affinity.number("fire_damage_reduction", 0) : 0;
                case "water" -> source.is(DamageTypeTags.IS_PROJECTILE) ? affinity.number("projectile_reduction", 0) : 0;
                case "stone" -> isMelee(source) ? affinity.number("melee_reduction", 0) : 0;
                default -> 0;
            };
            if (reduction > 0) event.setAmount((float) (event.getAmount() * Math.max(0, 1 - reduction)));
        }
        ActiveMechanic manifest = MechanicService.find(player, MANIFEST);
        if (manifest != null && "stone".equals(stance) && isPhysical(source) && source.getEntity() instanceof LivingEntity attacker
                && attacker != player && !REFLECTING.containsKey(player.getUUID())) {
            float reflected = (float) (event.getAmount() * manifest.number("reflect_fraction", 0.15));
            if (reflected > 0) {
                REFLECTING.put(player.getUUID(), true);
                try {
                    attacker.hurt(player.damageSources().thorns(player), reflected);
                } finally {
                    REFLECTING.remove(player.getUUID());
                }
            }
        }
    }

    private static boolean isMelee(DamageSource source) {
        return source.getEntity() instanceof LivingEntity && source.getDirectEntity() == source.getEntity()
                && !source.is(DamageTypeTags.IS_EXPLOSION) && !source.is(DamageTypes.THORNS);
    }

    /** Daño físico: golpes y proyectiles (no magia, fuego, explosiones ni espinas). */
    private static boolean isPhysical(DamageSource source) {
        if (source.is(DamageTypes.THORNS) || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)) return false;
        return isMelee(source) || source.getDirectEntity() instanceof Projectile;
    }

    // --- Estado por postura (cada segundo) y lava ---

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        // Golpe Cargado: cuenta el tiempo agachado (con la mano vacía).
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty() && MechanicService.has(player, CHARGED_PALM)) {
            int c = CROUCH.merge(player.getUUID(), 1, Integer::sum);
            if (c == 20) player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 0.6f);
        } else {
            CROUCH.remove(player.getUUID());
        }
        if (player.server.getTickCount() % 20 != 0) return;
        ActiveMechanic manifest = MechanicService.find(player, MANIFEST);
        String stance = manifest == null ? "none" : stance(player);
        // Agua: nado +100 % y Respiración Acuática.
        AttributeInstance swim = player.getAttribute(ForgeMod.SWIM_SPEED.get());
        double swimBonus = "water".equals(stance) ? manifest.number("swim_bonus", 1.0) : 0;
        if (swim != null) {
            AttributeModifier old = swim.getModifier(SWIM_ID);
            if (old == null || Math.abs(old.getAmount() - swimBonus) > 1.0E-6) {
                if (old != null) swim.removeModifier(SWIM_ID);
                if (swimBonus != 0) swim.addTransientModifier(new AttributeModifier(SWIM_ID, SWIM_NAME, swimBonus,
                        AttributeModifier.Operation.MULTIPLY_BASE));
            }
        }
        if ("water".equals(stance)) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 40, 0, true, false, true));
        }
        // Piedra: sin Veneno, Wither ni Hambre.
        if ("stone".equals(stance)) {
            player.removeEffect(MobEffects.POISON);
            player.removeEffect(MobEffects.WITHER);
            player.removeEffect(MobEffects.HUNGER);
        }
    }

    /** Piedra: inmune a Veneno, Wither y Hambre (no llegan a aplicarse). */
    public static boolean stoneImmune(ServerPlayer player, net.minecraft.world.effect.MobEffect effect) {
        return (effect == MobEffects.POISON || effect == MobEffects.WITHER || effect == MobEffects.HUNGER)
                && MechanicService.has(player, MANIFEST) && "stone".equals(stance(player));
    }

    /** Marcha Elemental: sobre la lava en Postura de Fuego no hay daño de lava ni de quemadura. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        DamageSource s = event.getSource();
        if (!(s.is(DamageTypes.LAVA) || s.is(DamageTypes.ON_FIRE) || s.is(DamageTypes.IN_FIRE) || s.is(DamageTypes.HOT_FLOOR))) return;
        if (!AbilityService.has(player, MonkAbilities.LIGHT_STEP) || !"fire".equals(stance(player))) return;
        if (!nearLava(player)) return;
        event.setCanceled(true);
        player.clearFire();
    }

    private static boolean nearLava(Player p) {
        BlockPos feet = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
        return p.level().getFluidState(feet).is(FluidTags.LAVA) || p.level().getFluidState(feet.above()).is(FluidTags.LAVA)
                || p.isInLava();
    }

    /**
     * Movimiento de Marcha Elemental (cliente): sin agacharse, el jugador se queda sobre la superficie del agua
     * (y de la lava si {@code lava}). Agacharse permite hundirse.
     */
    public static void fluidWalk(Player p, boolean lava) {
        fluidWalk(p, true, lava);
    }

    /** Caminar sobre fluidos: agua y/o lava (Marcha Elemental, Caminalava del Infernal Fundidor). */
    public static void fluidWalk(Player p, boolean water, boolean lava) {
        if (p.isShiftKeyDown() || p.isPassenger() || p.getAbilities().flying || p.isFallFlying()) return;
        Vec3 v = p.getDeltaMovement();
        BlockPos at = BlockPos.containing(p.getX(), p.getY() + 0.05, p.getZ());
        BlockPos below = BlockPos.containing(p.getX(), p.getY() - 0.05, p.getZ());
        FluidState here = p.level().getFluidState(at);
        FluidState under = p.level().getFluidState(below);
        if (walkable(here, water, lava) && p.level().getFluidState(at.above()).isEmpty()) {
            // Metido un poco en el fluido: subir a la superficie.
            double surface = at.getY() + here.getHeight(p.level(), at);
            if (surface - p.getY() < 0.7) p.setDeltaMovement(v.x, Math.min(0.25, Math.max(v.y, surface - p.getY() + 0.02)), v.z);
            p.resetFallDistance();
        } else if (walkable(under, water, lava) && v.y <= 0) {
            double surface = below.getY() + under.getHeight(p.level(), below);
            if (p.getY() - surface < 0.1) {
                p.setDeltaMovement(v.x, 0, v.z);
                p.setOnGround(true);
                p.resetFallDistance();
            }
        }
    }

    private static boolean walkable(FluidState f, boolean water, boolean lava) {
        return !f.isEmpty() && ((water && f.is(FluidTags.WATER)) || (lava && f.is(FluidTags.LAVA)));
    }

    /** Postura actual ("none", "water", "stone", "fire"). */
    public static String stance(ServerPlayer player) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return "none";
        int i = data.counters.getOrDefault(MonkAbilities.STANCE_COUNTER, 0);
        return i > 0 && i < MonkAbilities.STANCE_NAMES.length ? MonkAbilities.STANCE_NAMES[i] : "none";
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) KNOCKBACK.clear();
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        REFLECTING.remove(event.getEntity().getUUID());
    }
}
