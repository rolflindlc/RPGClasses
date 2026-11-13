package io.github.rpgclasses.content;

import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.server.MechanicService;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mago de Batalla (rev. 2.14): alterna espada y hechizo.
 * <ul>
 *   <li><b>Filo Imbuido:</b> cada conjuro carga el arma con su escuela; el siguiente golpe cuerpo a cuerpo (en unos
 *       segundos) hace más daño y aplica un efecto de esa escuela (las 10, ver {@link Schools}): fuego quema,
 *       hielo ralentiza, rayo salta, sangre cura, naturaleza envenena, ender te pone a su espalda, invocación alza
 *       colmillos, eldritch suelta una onda sónica en línea, agua empuja y moja.</li>
 *   <li><b>Eco Marcial:</b> cada 3 golpes cuerpo a cuerpo seguidos, el siguiente conjuro cuesta menos maná.</li>
 * </ul>
 * El lado de Iron's (al lanzar) está en {@code IronsCompat}; aquí no hay dependencias de Iron's.
 */
public final class BattleMage {
    public static final String IMBUED = "imbued_blade";
    public static final String ECHO = "martial_echo";

    private static final class State {
        String school;
        long imbueUntil;
        int hits;
        long lastHit;
        boolean echoReady;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    /** Golpe Sónico pendiente (se dispara un tic después para no dañar dentro del propio golpe). */
    private record Sonic(UUID owner, ServerLevel level, Vec3 from, Vec3 dir, double length, double damage, long at) {}
    private static final List<Sonic> SONICS = new ArrayList<>();

    /** Instancia solo para el bus de eventos. */
    public BattleMage() {}

    private static State state(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    /** Al lanzar un conjuro: imbuye el arma con su escuela. */
    public static void onSpellCast(ServerPlayer p, String school) {
        ActiveMechanic m = MechanicService.find(p, IMBUED);
        if (m == null) return;
        State st = state(p);
        st.school = school;
        st.imbueUntil = p.level().getGameTime() + Math.round(m.number("window_seconds", 5) * 20);
        if (p.level() instanceof ServerLevel level) {
            level.sendParticles(particle(school), p.getX(), p.getY() + 1.0, p.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
        }
    }

    /** Eco Marcial: factor de maná del conjuro que se está lanzando (lo consume). 1 = sin descuento. */
    public static double manaFactor(ServerPlayer p) {
        ActiveMechanic m = MechanicService.find(p, ECHO);
        State st = STATES.get(p.getUUID());
        if (m == null || st == null || !st.echoReady) return 1;
        st.echoReady = false;
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.4f);
        return 1 - m.number("mana_discount", 0.3);
    }

    /**
     * Golpe cuerpo a cuerpo con arma (desde DamagePipeline): cuenta para el Eco Marcial y, si el arma está imbuida,
     * devuelve el factor de daño del Filo Imbuido y aplica su efecto. 1 = sin cambio.
     */
    public static double onMeleeHit(ServerPlayer p, LivingEntity target, double damage) {
        long now = p.level().getGameTime();
        double factor = 1;
        ActiveMechanic echo = MechanicService.find(p, ECHO);
        ActiveMechanic imbued = MechanicService.find(p, IMBUED);
        if (echo == null && imbued == null) return 1;
        State st = state(p);

        if (echo != null) {
            st.hits = now - st.lastHit <= echo.number("window_seconds", 4) * 20 ? st.hits + 1 : 1;
            st.lastHit = now;
            if (st.hits >= echo.number("hits", 3) && !st.echoReady) {
                st.hits = 0;
                st.echoReady = true;
                p.displayClientMessage(Component.translatable("msg.rpgclasses.martial_echo"), true);
                ClassFx.burst(p, ParticleTypes.ENCHANT, 20, 0.5);
            }
        }

        if (imbued != null && st.school != null && now <= st.imbueUntil) {
            String school = st.school;
            st.school = null;
            double fraction = imbued.number("damage_fraction", 0.35);
            factor = 1 + fraction;
            double extra = damage * fraction;
            ServerLevel level = (ServerLevel) p.level();
            Schools.School kind = Schools.of(school);
            if (kind != null) switch (kind) {
                case FIRE -> MonkCharges.addBurn(target, imbued.number("burn_seconds", 3));
                case ICE -> {
                    int t = (int) Math.round(imbued.number("slow_seconds", 2) * 20);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, t, 1), p);
                    target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + t));
                }
                case LIGHTNING -> {
                    double range = imbued.number("arc_range", 4);
                    LivingEntity next = null;
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(range),
                            e -> e != target && CombatAbilities.canHit(p, e))) {
                        if (next == null || e.distanceToSqr(target) < next.distanceToSqr(target)) next = e;
                    }
                    if (next != null) {
                        next.hurt(level.damageSources().indirectMagic(p, p), (float) (damage * imbued.number("arc_fraction", 0.5)));
                        ClassFx.line(target, next, ParticleTypes.ELECTRIC_SPARK, 10);
                    }
                }
                case BLOOD -> p.heal((float) (extra * imbued.number("blood_heal", 0.25)));
                case NATURE -> target.addEffect(new MobEffectInstance(MobEffects.POISON,
                        (int) Math.round(imbued.number("poison_seconds", 3) * 20), 1), p);
                // Rev. 2.15: las otras cinco escuelas.
                case ENDER -> {
                    // Ender: apareces a la espalda del objetivo.
                    Vec3 back = target.position().subtract(target.getLookAngle().multiply(1, 0, 1).normalize()
                            .scale(target.getBbWidth() / 2 + 1.0));
                    double ox = p.getX(), oy = p.getY(), oz = p.getZ();
                    if (level.noCollision(p, p.getBoundingBox().move(back.subtract(p.position())))) {
                        p.teleportTo(back.x, back.y, back.z);
                        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
                        level.sendParticles(ParticleTypes.PORTAL, ox, oy + 1, oz, 20, 0.3, 0.5, 0.3, 0.2);
                        level.playSound(null, ox, oy, oz, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 1.3f);
                    }
                }
                case EVOCATION -> Schools.fangs(p, target, (int) Math.round(imbued.number("fangs", 3)), 1.2);
                case ELDRITCH -> {
                    // Golpe Sónico (rev. 2.16): el extra no va en el golpe; sale como onda sónica en línea (ignora la
                    // armadura) en el tic siguiente, atravesando al objetivo y a los enemigos detrás.
                    factor = 1;
                    Vec3 dir = target.position().subtract(p.position()).multiply(1, 0, 1);
                    if (dir.lengthSqr() < 1e-4) dir = p.getLookAngle().multiply(1, 0, 1);
                    SONICS.add(new Sonic(p.getUUID(), level, p.getEyePosition().add(0, -0.4, 0), dir.normalize(),
                            imbued.number("sonic_length", 6), extra * imbued.number("sonic_fraction", 1.0), level.getGameTime() + 1));
                    level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.6f, 1.8f);
                }
                case AQUA -> {
                    // Agua (T.O Magic 'n Extras): empuje, lentitud y Mojado de T.O.
                    Vec3 dir = target.position().subtract(p.position()).multiply(1, 0, 1).normalize();
                    double push = imbued.number("push", 0.8);
                    target.push(dir.x * push, 0.2, dir.z * push);
                    target.hurtMarked = true;
                    int t = (int) Math.round(imbued.number("slow_seconds", 2) * 20);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, t, 0), p);
                    Schools.applyWet(target, p, t * 2, 0);
                    level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.8f, 1.2f);
                }
            }
            ClassFx.burst(target, particle(school), 14, 0.15);
            level.playSound(null, target.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8f, 1.6f);
        }
        return factor;
    }

    private static ParticleOptions particle(String school) {
        return Schools.particle(Schools.of(school));
    }

    @SubscribeEvent
    public void onSonicTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SONICS.isEmpty()) return;
        Iterator<Sonic> it = SONICS.iterator();
        while (it.hasNext()) {
            Sonic s = it.next();
            if (s.level().getGameTime() < s.at()) continue;
            it.remove();
            ServerPlayer p = s.level().getServer().getPlayerList().getPlayer(s.owner());
            if (p == null || p.level() != s.level()) continue;
            fireSonic(p, s);
        }
    }

    /** Onda sónica en línea: anillos del Warden, almas de sculk a los lados y daño que ignora la armadura. */
    private static void fireSonic(ServerPlayer p, Sonic s) {
        ServerLevel level = s.level();
        java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
        for (double d = 0.8; d <= s.length(); d += 0.5) {
            Vec3 at = s.from().add(s.dir().scale(d));
            if (((int) (d * 2)) % 2 == 0) level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y, at.z, 2, 0.35, 0.35, 0.35, 0.02);
            hit.addAll(level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(0.8),
                    e -> e != p && e.isAlive() && CombatAbilities.canHit(p, e)));
        }
        level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8f, 1.3f);
        for (LivingEntity e : hit) {
            e.invulnerableTime = 0;
            e.hurt(level.damageSources().sonicBoom(p), (float) s.damage());
            level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }
}
