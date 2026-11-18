package io.github.rpgclasses.cleric;

import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.Allies;
import io.github.rpgclasses.server.DebugLog;
import io.github.rpgclasses.server.EffectRules;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
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
 * Curación del Clérigo. Cada curación que hace un Clérigo pasa por {@link #onHeal}:
 * <ul>
 *   <li><b>Conjuros</b> (Iron's avisa con {@code SpellHealEvent}, que trae lanzador y objetivo, justo antes de curar):
 *       Sanación (+50 %), Canal de Vida (−75 % sobre ti, recuperas el 50 % de lo curado a otros, 3 objetivos por
 *       lanzamiento) y Fe Propia (+50 % sobre ti, −75 % sobre otros); Retorno de Fe.</li>
 *   <li><b>Habilidades</b> (Imposición de Manos, Sacrificio): sin Sanación ni sus perks (no son conjuros).</li>
 *   <li><b>Las dos</b>: Purificación (Absolución, Penitencia), Égida (Profunda, Veloz) y levantar a un tumbado (Milagro).</li>
 * </ul>
 * Solo cuenta la curación <b>efectiva</b> (la que no pasa de la vida máxima) para Canal de Vida y Retorno de Fe; la que
 * sobra es la que usa Égida.
 */
public final class ClericHealing {
    /** Curación de conjuro anunciada por Iron's: objetivo → lanzador y tick (Iron's cura justo después). */
    private record Pending(UUID caster, long tick) {}

    private static final Map<UUID, Pending> SPELL_HEALS = new HashMap<>();
    /** Curación de una habilidad del Clérigo en curso (la pone {@link #abilityHeal}). */
    private static ServerPlayer abilityHealer;
    /** Curaciones internas (Canal de Vida): no vuelven a pasar por aquí. */
    private static boolean internal;
    /** Canal de Vida: objetivos que ya han contado este tick por lanzador. */
    private static final Map<UUID, Integer> CHANNEL_COUNT = new HashMap<>();
    private static long channelTick;
    /** Absolución: último tick en que cada Clérigo limpió a cada objetivo ("clérigo|objetivo"). */
    private static final Map<String, Long> ABSOLVED = new HashMap<>();
    /** Égida: absorción propia de cada objetivo (aparte de la de manzanas, etc.) y cuándo caduca. */
    private static final Map<UUID, Aegis> AEGIS = new HashMap<>();
    private static final String FAITH_RETURN_CD = "rites_and_paths:faith_return_icd";

    private static final class Aegis {
        float own;
        long expires;
    }

    /** Puente con Iron's (lo pone ClericSpells): maná del último conjuro lanzado por el jugador este tick, o 0. */
    public interface ManaInfo {
        float lastCastMana(ServerPlayer player);
    }

    public static ManaInfo manaInfo = p -> 0;

    public ClericHealing() {}

    /** Iron's: un conjuro de {@code caster} va a curar a {@code target} (SpellHealEvent). */
    public static void spellHeal(LivingEntity caster, LivingEntity target) {
        if (!(caster instanceof ServerPlayer p) || target == null || target.level().isClientSide()) return;
        SPELL_HEALS.put(target.getUUID(), new Pending(p.getUUID(), target.level().getGameTime()));
    }

    /** Cura con una habilidad del Clérigo: pasa por Purificación, Égida y levanta a un tumbado. Devuelve lo curado. */
    public static float abilityHeal(ServerPlayer healer, LivingEntity target, float amount) {
        float before = target.getHealth();
        ServerPlayer previous = abilityHealer;
        abilityHealer = healer;
        try {
            target.heal(amount);
        } finally {
            abilityHealer = previous;
        }
        return target.getHealth() - before;
    }

    /** ¿Quién cura ahora a {@code target}? (un jugador con un conjuro o una habilidad), o null. */
    private static ServerPlayer healerOf(LivingEntity target, boolean[] spell) {
        if (abilityHealer != null) {
            spell[0] = false;
            return abilityHealer;
        }
        Pending p = SPELL_HEALS.remove(target.getUUID());
        if (p == null || p.tick() != target.level().getGameTime()) return null;
        MinecraftServer server = target.getServer();
        if (server == null) return null;
        spell[0] = true;
        return server.getPlayerList().getPlayer(p.caster());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onHeal(LivingHealEvent event) {
        LivingEntity target = event.getEntity();
        if (internal || target.level().isClientSide() || event.getAmount() <= 0) return;
        boolean[] spell = {false};
        ServerPlayer healer = healerOf(target, spell);
        if (healer == null || healer instanceof FakePlayer) return;
        boolean self = healer == target;

        // Levantar a un tumbado: curación de otro jugador (conjuro o habilidad). Lo suyo propio no le levanta.
        if (!self && target instanceof ServerPlayer downed && Miracle.isDowned(downed)) {
            event.setAmount(Miracle.revive(downed, healer, event.getAmount()));
        }
        if (!Cleric.isCleric(healer)) return;

        float amount = event.getAmount();
        if (spell[0]) {
            double factor = 1 + Cleric.mech(healer, Cleric.HEALING_POWER, "heal_bonus", 0);
            ActiveMechanic channel = Cleric.mech(healer, Cleric.LIFE_CHANNEL);
            ActiveMechanic faith = Cleric.mech(healer, Cleric.OWN_FAITH);
            if (channel != null && self) factor *= 1 + channel.number("self_heal", -0.75);
            if (faith != null) factor *= 1 + (self ? faith.number("self_heal", 0.5) : faith.number("others_heal", -0.75));
            amount = (float) Math.max(0, amount * factor);
            event.setAmount(amount);
        }
        float missing = Math.max(0, target.getMaxHealth() - target.getHealth());
        float effective = Math.min(amount, missing);
        float overflow = amount - effective;
        String kind = spell[0] ? "conjuro" : "habilidad";
        DebugLog.event(healer, "clérigo", "cura ({}) a {}: {} (efectiva {}, sobrante {})", kind,
                target.getName().getString(), amount, effective, overflow);

        if (spell[0] && !self && effective > 0) {
            lifeChannel(healer, effective);
            faithReturn(healer);
        }
        purify(healer, target);
        if (overflow > 0) aegis(healer, target, overflow);
    }

    // ------------------------------------------------------------------ perks de Sanación y Reserva Divina

    private static void lifeChannel(ServerPlayer healer, float effective) {
        ActiveMechanic channel = Cleric.mech(healer, Cleric.LIFE_CHANNEL);
        if (channel == null) return;
        long now = healer.level().getGameTime();
        if (now != channelTick) {
            CHANNEL_COUNT.clear();
            channelTick = now;
        }
        int count = CHANNEL_COUNT.merge(healer.getUUID(), 1, Integer::sum);
        if (count > channel.number("max_targets", 3)) return;
        float back = (float) (effective * channel.number("recovery", 0.5));
        if (back > 0 && healer.getHealth() < healer.getMaxHealth()) {
            // Otra fuente: no pasa por el −75 % sobre sí mismo ni por Purificación/Égida.
            internal = true;
            try {
                healer.heal(back);
            } finally {
                internal = false;
            }
            ((ServerLevel) healer.level()).sendParticles(ParticleTypes.HEART, healer.getX(), healer.getY() + 1.9, healer.getZ(), 1, 0.2, 0.1, 0.2, 0);
        }
    }

    private static void faithReturn(ServerPlayer healer) {
        ActiveMechanic m = Cleric.mech(healer, Cleric.FAITH_RETURN);
        if (m == null || AbilityService.remainingTicks(healer, FAITH_RETURN_CD) > 0) return;
        float mana = manaInfo.lastCastMana(healer);
        if (mana <= 0 || io.github.rpgclasses.content.Chronomancy.mana == null) return;
        var bridge = io.github.rpgclasses.content.Chronomancy.mana;
        float refund = (float) (mana * m.number("mana_refund", 0.15));
        bridge.setMana(healer, Math.min(bridge.maxMana(healer), bridge.mana(healer) + refund));
        AbilityService.setCooldown(healer, FAITH_RETURN_CD, (int) Math.round(m.number("icd_seconds", 2) * 20));
        DebugLog.event(healer, "clérigo", "Retorno de Fe: +{} de maná", refund);
    }

    // ------------------------------------------------------------------ Purificación

    private static void purify(ServerPlayer healer, LivingEntity target) {
        ActiveMechanic branch = Cleric.mech(healer, Cleric.PURIFICATION);
        if (branch == null) return;
        double reduction = branch.number("debuff_reduction", 0);
        ActiveMechanic absolution = Cleric.mech(healer, Cleric.ABSOLUTION);
        ActiveMechanic penance = Cleric.mech(healer, Cleric.PENANCE);
        long now = target.level().getGameTime();
        List<MobEffectInstance> negatives = new ArrayList<>();
        for (MobEffectInstance inst : target.getActiveEffects()) {
            if (EffectRules.isNegative(inst.getEffect())) negatives.add(inst);
        }
        if (negatives.isEmpty()) return;

        if (absolution != null) {
            reduction *= absolution.number("reduction_factor", 0.5);
            String key = healer.getUUID() + "|" + target.getUUID();
            Long last = ABSOLVED.get(key);
            long icd = Math.round(absolution.number("icd_seconds", 12) * 20);
            if (last == null || now - last >= icd) {
                ABSOLVED.put(key, now);
                for (MobEffectInstance inst : negatives) target.removeEffect(inst.getEffect());
                if (target.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 12, 0.3, 0.4, 0.3, 0.05);
                }
                DebugLog.event(healer, "clérigo", "Absolución: {} efectos negativos quitados a {}", negatives.size(), target.getName().getString());
                return;
            }
        }
        if (reduction <= 0) return;
        LivingEntity sinner = penance == null ? null : nearestEnemy(healer, target, penance.number("radius", 8));
        for (MobEffectInstance inst : negatives) {
            if (inst.isInfiniteDuration() || inst.getDuration() <= 1) continue;
            int cut = (int) Math.round(inst.getDuration() * Math.min(1, reduction));
            if (cut <= 0) continue;
            MobEffect effect = inst.getEffect();
            EffectRules.replace(target, new MobEffectInstance(effect, inst.getDuration() - cut, inst.getAmplifier(),
                    inst.isAmbient(), inst.isVisible(), inst.showIcon()));
            if (sinner != null) {
                int given = (int) Math.round(cut * penance.number("transfer", 0.5));
                if (given > 0) sinner.addEffect(new MobEffectInstance(effect, given, inst.getAmplifier()), healer);
            }
        }
        if (sinner != null && sinner.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SMOKE, sinner.getX(), sinner.getY() + sinner.getBbHeight() * 0.6, sinner.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
        }
    }

    /** Penitencia: el enemigo vivo más cercano al curado. */
    private static LivingEntity nearestEnemy(ServerPlayer healer, LivingEntity around, double radius) {
        LivingEntity best = null;
        double bestDist = radius * radius;
        for (LivingEntity e : around.level().getEntitiesOfClass(LivingEntity.class, around.getBoundingBox().inflate(radius))) {
            if (e == around || !e.isAlive() || !Allies.isEnemy(healer, e)) continue;
            if (!(e instanceof net.minecraft.world.entity.monster.Enemy || e instanceof Player || (e instanceof Mob m && m.getTarget() != null))) continue;
            double d = e.distanceToSqr(around);
            if (d <= bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ Égida

    private static void aegis(ServerPlayer healer, LivingEntity target, float overflow) {
        ActiveMechanic branch = Cleric.mech(healer, Cleric.AEGIS);
        if (branch == null) return;
        double conversion = branch.number("conversion", 0);
        double cap = branch.number("cap_health_fraction", 0.25);
        ActiveMechanic deep = Cleric.mech(healer, Cleric.DEEP_AEGIS);
        ActiveMechanic swift = Cleric.mech(healer, Cleric.SWIFT_AEGIS);
        if (deep != null) {
            conversion *= 1 + deep.number("conversion_factor", -0.75);
            cap = deep.number("cap_health_fraction", 0.5);
        }
        if (swift != null) {
            conversion *= 1 + swift.number("conversion_factor", 0.5);
            cap = swift.number("cap_health_fraction", 0.15);
        }
        float add = (float) (overflow * conversion);
        if (add <= 0) return;
        float capAmount = (float) (target.getMaxHealth() * cap);
        Aegis a = AEGIS.computeIfAbsent(target.getUUID(), k -> new Aegis());
        float own = Math.min(a.own, target.getAbsorptionAmount());
        float next = Math.min(capAmount, own + add);
        if (next <= own) return;
        target.setAbsorptionAmount(target.getAbsorptionAmount() - own + next);
        a.own = next;
        a.expires = target.level().getGameTime() + Math.round(branch.number("absorption_seconds", 10) * 20);
        if (target.level() instanceof ServerLevel sl && target.tickCount % 4 == 0) {
            sl.sendParticles(io.github.rpgclasses.content.ClassFx.GOLD, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 4, 0.35, 0.4, 0.35, 0);
        }
    }

    /** Caducidad de la absorción de Égida (solo se quita la parte propia que quede). */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 10 == 0) {
            long cutoff = server.overworld().getGameTime() - 20 * 60;
            ABSOLVED.values().removeIf(t -> t < cutoff);
        }
        if (AEGIS.isEmpty() || server.getTickCount() % 5 != 0) return;
        Iterator<Map.Entry<UUID, Aegis>> it = AEGIS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Aegis> e = it.next();
            LivingEntity target = find(server, e.getKey());
            if (target == null || !target.isAlive()) {
                it.remove();
                continue;
            }
            Aegis a = e.getValue();
            a.own = Math.min(a.own, target.getAbsorptionAmount());
            if (a.own <= 0) {
                it.remove();
            } else if (target.level().getGameTime() >= a.expires) {
                target.setAbsorptionAmount(Math.max(0, target.getAbsorptionAmount() - a.own));
                it.remove();
            }
        }
    }

    private static LivingEntity find(MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) return p;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof LivingEntity l) return l;
        }
        return null;
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        AEGIS.remove(event.getEntity().getUUID());
        SPELL_HEALS.remove(event.getEntity().getUUID());
    }
}
