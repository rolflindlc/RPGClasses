package io.github.rpgclasses.content;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.registry.ModEffects;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.MechanicService;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ramas extra y perks del Ranger (rev. 2.7c). DamagePipeline pide aquí los factores de las flechas:
 * {@link #spawnFactor} al disparar y {@link #hitFactor}/{@link #onArrowHit} al impactar.
 */
public final class RangerPerks {
    public static final String STEADY = "steady_shooter";
    public static final String MOBILE = "mobile_shooter";
    public static final String DRAW_NO_SLOW = "draw_no_slow";
    public static final String QUICK_COUNTER = "quick_counter";
    public static final String PIERCING = "piercing";
    public static final String HUNTERS_MARK = "hunters_mark";
    public static final String POTION_ARROWS = "potion_arrows";
    public static final String EXPLOSIVE = "explosive_arrow";
    public static final String STICKY = "sticky_arrow";
    public static final String RETREAT = "retreat";
    public static final String EVASIVE_LEAP = "evasive_leap";
    public static final String HUNTER_TRAP = "hunter_trap";
    public static final ResourceKey<DamageType> ARROW_BLAST = ResourceKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("arrow_blast"));

    private static final String TAG_HITS = "rites_and_paths.hits";
    private static final String TAG_EXPLOSIVE = "rites_and_paths.explosive";
    private static final String TAG_MARK_ICD = "rites_and_paths.mark_icd";
    private static final String TAG_MARK_BONUS = "rites_and_paths.mark_bonus";
    private static final String CD_RETREAT = "rites_and_paths:retreat";
    private static final String CD_LEAP = "rites_and_paths:evasive_leap";
    private static final String CD_TRAP = "rites_and_paths:hunter_trap";
    private static final String CD_QUICK = "rites_and_paths:quick_counter";

    /** Último tick de servidor en que el jugador se movió en horizontal, y su posición anterior. */
    private static final Map<UUID, Long> LAST_MOVED = io.github.rpgclasses.server.SessionState.track(new HashMap<>());
    private static final Map<UUID, Vec3> LAST_POS = io.github.rpgclasses.server.SessionState.track(new HashMap<>());
    /** Contraataque Veloz: fin de la ventana tras el golpe, y si la próxima flecha sale de un tensado instantáneo. */
    private static final Map<UUID, Long> QUICK_WINDOW = io.github.rpgclasses.server.SessionState.track(new HashMap<>());
    private static final Map<UUID, Boolean> QUICK_ARMED = io.github.rpgclasses.server.SessionState.track(new HashMap<>());
    /** Golpe de flecha en curso: los efectos que aplica vanilla justo después son los de la flecha (Flechas Especiales). */
    private static UUID arrowHitShooter;
    private static int arrowHitTarget = -1;
    private static long arrowHitTick = -1;
    private static boolean reapplying;

    private record Trap(ServerLevel level, Vec3 pos, long untilTick, UUID owner, int amplifier) {}
    private static final List<Trap> TRAPS = io.github.rpgclasses.server.SessionState.track(new ArrayList<>());

    // --- Disparo e impacto (llamados desde DamagePipeline) ---

    /** Factor de daño que se graba en la flecha al disparar. También ajusta perforación y la marca de explosiva. */
    public static double spawnFactor(ServerPlayer shooter, AbstractArrow arrow) {
        double factor = 1;
        long now = shooter.server.getTickCount();
        long sinceMoved = now - LAST_MOVED.getOrDefault(shooter.getUUID(), now);
        boolean moving = sinceMoved <= 4;
        ActiveMechanic steady = MechanicService.find(shooter, STEADY);
        if (steady != null) {
            if (shooter.isSprinting() || !shooter.onGround()) factor *= 1 + steady.number("moving_penalty", -0.15);
            else if (sinceMoved >= steady.number("still_seconds", 1) * 20) factor *= 1 + steady.number("still_bonus", 0.25);
        }
        ActiveMechanic mobile = MechanicService.find(shooter, MOBILE);
        if (mobile != null) factor *= 1 + (moving ? mobile.number("moving_bonus", 0.2) : mobile.number("still_penalty", -0.15));

        if (Boolean.TRUE.equals(QUICK_ARMED.remove(shooter.getUUID()))) {
            factor *= MechanicService.number(shooter, QUICK_COUNTER, "damage_fraction", 0.7);
        }
        ActiveMechanic piercing = MechanicService.find(shooter, PIERCING);
        if (piercing != null) {
            arrow.setPierceLevel((byte) (arrow.getPierceLevel() + (int) Math.round(piercing.number("extra_targets", 1))));
            arrow.getPersistentData().putBoolean(io.github.rpgclasses.cleric.Sanctuary.PIERCING_MARK, true); // atraviesa el Santuario
        }
        ActiveMechanic explosive = MechanicService.find(shooter, EXPLOSIVE);
        if (explosive != null && isPlainArrow(arrow)) {
            factor *= 1 + explosive.number("direct_damage", -0.1);
            arrow.getPersistentData().putBoolean(TAG_EXPLOSIVE, true);
        }
        return factor;
    }

    /** Perforante: el primer impacto hace menos daño y el segundo una fracción. Lleva la cuenta en la flecha. */
    public static double hitFactor(ServerPlayer shooter, Projectile projectile) {
        CompoundTag tag = projectile.getPersistentData();
        int hit = tag.getInt(TAG_HITS);
        tag.putInt(TAG_HITS, hit + 1);
        ActiveMechanic piercing = MechanicService.find(shooter, PIERCING);
        if (piercing == null || !(projectile instanceof AbstractArrow)) return 1;
        if (hit == 0) return 1 + piercing.number("first_damage", -0.1);
        if (hit == 1) return piercing.number("second_damage", 0.6);
        return 1;
    }

    /** Tras calcular el daño de una flecha: marca, lentitud, explosión y contexto para los efectos de poción. */
    public static void onArrowHit(ServerPlayer shooter, Projectile projectile, LivingEntity target, float damage) {
        if (!(projectile instanceof AbstractArrow)) return;
        long now = target.level().getGameTime();
        ActiveMechanic mark = MechanicService.find(shooter, HUNTERS_MARK);
        CompoundTag data = target.getPersistentData();
        if (mark != null && !target.hasEffect(ModEffects.HUNTERS_MARK.get()) && data.getLong(TAG_MARK_ICD) <= now) {
            int ticks = (int) Math.round(mark.number("duration_seconds", 6) * 20);
            target.addEffect(new MobEffectInstance(ModEffects.HUNTERS_MARK.get(), ticks, 0), shooter);
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false), shooter);
            data.putDouble(TAG_MARK_BONUS, mark.number("damage_bonus", 0.12));
            data.putLong(TAG_MARK_ICD, now + Math.round(mark.number("icd_seconds", 10) * 20));
        }
        ActiveMechanic sticky = MechanicService.find(shooter, STICKY);
        if (sticky != null) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    (int) Math.round(sticky.number("slowness_seconds", 2) * 20), 0), shooter);
        }
        if (projectile.getPersistentData().getBoolean(TAG_EXPLOSIVE)) {
            projectile.getPersistentData().remove(TAG_EXPLOSIVE);
            blast(shooter, projectile.position(), target, damage);
        }
        arrowHitShooter = shooter.getUUID();
        arrowHitTarget = target.getId();
        arrowHitTick = now;
    }

    private static boolean isPlainArrow(AbstractArrow arrow) {
        return arrow.getClass() == Arrow.class && ((Arrow) arrow).getColor() == -1;
    }

    /** Flecha Explosiva: fracción del daño a los enemigos cercanos, sin romper bloques. */
    private static void blast(ServerPlayer shooter, Vec3 at, Entity exclude, float arrowDamage) {
        ActiveMechanic m = MechanicService.find(shooter, EXPLOSIVE);
        if (m == null || !(shooter.level() instanceof ServerLevel level)) return;
        double radius = m.number("radius", 2);
        float damage = (float) (arrowDamage * m.number("damage_fraction", 0.3));
        DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ARROW_BLAST), shooter);
        double knockback = m.number("knockback_strength", 0.8);
        // Rev. 2.7f: el radio se mide hasta el cuerpo del enemigo (no hasta sus pies) y empuja, también al alcanzado.
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius + 2),
                e -> CombatAbilities.canHit(shooter, e)
                        && closest(e.getBoundingBox(), at).distanceToSqr(at) <= radius * radius)) {
            if (damage > 0 && e != exclude) e.hurt(source, damage);
            Vec3 away = e.position().subtract(at);
            if (knockback > 0 && away.horizontalDistanceSqr() > 1.0E-4) {
                e.knockback(knockback, -away.x, -away.z);
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.25, 0));
                e.hurtMarked = true;
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6f, 1.3f);
    }

    private static Vec3 closest(AABB box, Vec3 p) {
        return new Vec3(Math.max(box.minX, Math.min(p.x, box.maxX)), Math.max(box.minY, Math.min(p.y, box.maxY)),
                Math.max(box.minZ, Math.min(p.z, box.maxZ)));
    }

    /** Flecha Explosiva que impacta contra un bloque: el daño de referencia es el de la flecha en ese momento. */
    @SubscribeEvent
    public void onImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow) || arrow.level().isClientSide()) return;
        if (event.getRayTraceResult().getType() != HitResult.Type.BLOCK || !arrow.getPersistentData().getBoolean(TAG_EXPLOSIVE)) return;
        if (!(arrow.getOwner() instanceof ServerPlayer shooter)) return;
        arrow.getPersistentData().remove(TAG_EXPLOSIVE);
        double factor = arrow.getPersistentData().contains("rites_and_paths.factor") ? arrow.getPersistentData().getDouble("rites_and_paths.factor") : 1;
        float damage = (float) (Math.ceil(arrow.getDeltaMovement().length() * arrow.getBaseDamage()) * factor);
        blast(shooter, event.getRayTraceResult().getLocation(), null, damage);
    }

    /** Flechas Especiales: los efectos de las flechas con poción duran más. Vanilla los aplica justo tras el golpe. */
    @SubscribeEvent
    public void onEffectAdded(MobEffectEvent.Added event) {
        if (reapplying || arrowHitShooter == null) return;
        LivingEntity target = event.getEntity();
        if (target.getId() != arrowHitTarget || target.level().getGameTime() != arrowHitTick) return;
        if (!(event.getEffectSource() instanceof ServerPlayer shooter) || !shooter.getUUID().equals(arrowHitShooter)) return;
        MobEffectInstance instance = event.getEffectInstance();
        if (instance.getEffect().isInstantenous() || instance.isInfiniteDuration()) return;
        double bonus = MechanicService.number(shooter, POTION_ARROWS, "potion_bonus", 0);
        if (bonus <= 0) return;
        reapplying = true;
        try {
            target.addEffect(new MobEffectInstance(instance.getEffect(), (int) Math.round(instance.getDuration() * (1 + bonus)),
                    instance.getAmplifier(), instance.isAmbient(), instance.isVisible(), instance.showIcon()), shooter);
        } finally {
            reapplying = false;
        }
    }

    // --- Al recibir un golpe cuerpo a cuerpo: Retirada, Salto Evasivo, Trampa y Contraataque Veloz ---

    private static boolean isMelee(DamageSource source) {
        return source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker;
    }

    /** Salto Evasivo: se cancela el golpe y el Ranger salta hacia atrás. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || !isMelee(event.getSource())) return;
        ActiveMechanic leap = MechanicService.find(player, EVASIVE_LEAP);
        if (leap == null || AbilityService.remainingTicks(player, CD_LEAP) > 0) return;
        LivingEntity attacker = (LivingEntity) event.getSource().getEntity();
        Vec3 away = player.position().subtract(attacker.position());
        away = new Vec3(away.x, 0, away.z);
        if (away.lengthSqr() < 1.0E-4) away = player.getLookAngle().multiply(-1, 0, -1);
        double speed = leap.number("leap_blocks", 4) * 0.25; // aproximado [MEDIR]
        player.setDeltaMovement(away.normalize().scale(speed).add(0, 0.4, 0));
        player.hurtMarked = true;
        AbilityService.setCooldown(player, CD_LEAP, (int) Math.round(leap.number("icd_seconds", 12) * 20));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.0f, 1.2f);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        // Marca de Cazador: el marcado recibe más daño de todas las fuentes.
        if (victim.hasEffect(ModEffects.HUNTERS_MARK.get()) && event.getAmount() > 0) {
            event.setAmount((float) (event.getAmount() * (1 + victim.getPersistentData().getDouble(TAG_MARK_BONUS))));
        }
        if (!(victim instanceof ServerPlayer player) || player instanceof FakePlayer || !isMelee(event.getSource())) return;

        ActiveMechanic retreat = MechanicService.find(player, RETREAT);
        if (retreat != null && retreat.level() >= retreat.number("min_points", 0) && AbilityService.remainingTicks(player, CD_RETREAT) <= 0) {
            player.addEffect(new MobEffectInstance(ModEffects.RETREAT.get(), (int) Math.round(retreat.number("duration_seconds", 2) * 20), 0));
            AbilityService.setCooldown(player, CD_RETREAT, (int) Math.round(retreat.number("icd_seconds", 5) * 20));
        }
        ActiveMechanic trap = MechanicService.find(player, HUNTER_TRAP);
        if (trap != null && AbilityService.remainingTicks(player, CD_TRAP) <= 0) {
            TRAPS.add(new Trap(player.serverLevel(), player.position(),
                    player.level().getGameTime() + Math.round(trap.number("duration_seconds", 3) * 20), player.getUUID(),
                    Math.max(0, (int) Math.round(trap.number("slowness_level", 2)) - 1)));
            AbilityService.setCooldown(player, CD_TRAP, (int) Math.round(trap.number("icd_seconds", 12) * 20));
        }
        ActiveMechanic quick = MechanicService.find(player, QUICK_COUNTER);
        if (quick != null && AbilityService.remainingTicks(player, CD_QUICK) <= 0) {
            QUICK_WINDOW.put(player.getUUID(), (long) player.server.getTickCount() + Math.round(quick.number("window_seconds", 3) * 20));
            AbilityService.setCooldown(player, CD_QUICK, (int) Math.round(quick.number("icd_seconds", 8) * 20));
        }
    }

    /** Contraataque Veloz: el arco que se tensa dentro de la ventana queda tensado al instante. */
    @SubscribeEvent
    public void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getItem().getItem() instanceof BowItem)) return;
        Long until = QUICK_WINDOW.get(player.getUUID());
        if (until == null) return;
        QUICK_WINDOW.remove(player.getUUID());
        if (player.server.getTickCount() > until) return;
        event.setDuration(Math.max(1, event.getDuration() - 20));
        QUICK_ARMED.put(player.getUUID(), true);
        player.playNotifySound(SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 1.0f, 1.4f);
    }

    // --- Tics: movimiento (Tirador Firme/Móvil) y trampas ---

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        if (!MechanicService.has(player, STEADY) && !MechanicService.has(player, MOBILE)) return;
        Vec3 pos = player.position();
        Vec3 last = LAST_POS.put(player.getUUID(), pos);
        if (last == null || last.subtract(pos).horizontalDistanceSqr() > 0.0004) {
            LAST_MOVED.put(player.getUUID(), (long) player.server.getTickCount());
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TRAPS.isEmpty()) return;
        boolean particles = event.getServer().getTickCount() % 5 == 0;
        for (Iterator<Trap> it = TRAPS.iterator(); it.hasNext(); ) {
            Trap t = it.next();
            if (t.level().getGameTime() > t.untilTick()) {
                it.remove();
                continue;
            }
            ServerPlayer owner = event.getServer().getPlayerList().getPlayer(t.owner());
            net.minecraft.server.MinecraftServer server = event.getServer();
            if (particles) t.level().sendParticles(ParticleTypes.CRIT, t.pos().x, t.pos().y + 0.1, t.pos().z, 4, 0.4, 0, 0.4, 0);
            for (LivingEntity e : t.level().getEntitiesOfClass(LivingEntity.class, new AABB(t.pos(), t.pos()).inflate(0.9, 0.5, 0.9),
                    e -> io.github.rpgclasses.server.Allies.isEnemyOf(server, t.owner(), e))) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, t.amplifier()), owner);
            }
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST_MOVED.remove(id);
        LAST_POS.remove(id);
        QUICK_WINDOW.remove(id);
        QUICK_ARMED.remove(id);
    }
}
