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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mago de Batalla (rev. 2.14): alterna espada y hechizo.
 * <ul>
 *   <li><b>Filo Imbuido:</b> cada conjuro carga el arma con su escuela; el siguiente golpe cuerpo a cuerpo (en unos
 *       segundos) hace más daño y aplica un efecto de esa escuela: fuego quema, hielo ralentiza, rayo salta a otro
 *       enemigo, sangre cura, naturaleza envenena.</li>
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
            switch (school.substring(school.indexOf(':') + 1)) {
                case "fire" -> MonkCharges.addBurn(target, imbued.number("burn_seconds", 3));
                case "ice" -> {
                    int t = (int) Math.round(imbued.number("slow_seconds", 2) * 20);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, t, 1), p);
                    target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + t));
                }
                case "lightning" -> {
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
                case "blood" -> p.heal((float) (extra * imbued.number("blood_heal", 0.25)));
                case "nature" -> target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), p);
                default -> { }
            }
            ClassFx.burst(target, particle(school), 14, 0.15);
            level.playSound(null, target.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8f, 1.6f);
        }
        return factor;
    }

    private static ParticleOptions particle(String school) {
        return switch (school.substring(school.indexOf(':') + 1)) {
            case "fire" -> ParticleTypes.FLAME;
            case "ice" -> ParticleTypes.SNOWFLAKE;
            case "lightning" -> ParticleTypes.ELECTRIC_SPARK;
            case "blood" -> ClassFx.BLOOD;
            case "nature" -> ParticleTypes.HAPPY_VILLAGER;
            case "ender" -> ParticleTypes.PORTAL;
            case "holy" -> ParticleTypes.END_ROD;
            default -> ParticleTypes.ENCHANT;
        };
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }
}
