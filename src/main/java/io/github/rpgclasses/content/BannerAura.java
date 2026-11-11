package io.github.rpgclasses.content;

import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.registry.ModEffects;
import io.github.rpgclasses.server.MechanicService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.scores.Team;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rama Estandarte del Paladín (rev. 2.7c): aura alrededor del Paladín para él y sus aliados.
 * Se recalcula una vez por segundo. Las auras de varios Paladines no se apilan: cuenta el valor más alto de cada campo.
 * <ul>
 *   <li>Rama: +armadura y −duración de efectos negativos (la duración la aplica EffectRules).</li>
 *   <li>Estandarte de Guerra: +daño a los aliados (no al Paladín; lo aplica DamagePipeline).</li>
 *   <li>Estandarte de Vida: regenera a los aliados (no al Paladín).</li>
 * </ul>
 * Aliados = otros jugadores, salvo los de un equipo (scoreboard) distinto al del Paladín.
 */
public final class BannerAura {
    public static final String AURA = "banner_aura";
    public static final String WAR = "war_banner";
    public static final String LIFE = "life_banner";
    private static final UUID ARMOR_ID = UUID.fromString("0f6b8d52-3c1e-4d9a-a8f1-6b2c9e0d4a71");
    /** No empieza por "rpgclasses:" a propósito: StatEngine no debe borrarlo al recalcular. */
    private static final String ARMOR_NAME = "rpgclasses_aura:banner";
    private static final int PERIOD_TICKS = 20;

    /** Lo que recibe un jugador del aura más fuerte que le alcanza. */
    public record Aura(double armor, double debuffDuration, double damage, double regen) {
        Aura max(Aura o) {
            return new Aura(Math.max(armor, o.armor), Math.min(debuffDuration, o.debuffDuration),
                    Math.max(damage, o.damage), Math.max(regen, o.regen));
        }
    }

    private static final Map<UUID, Aura> ACTIVE = new HashMap<>();

    /** Aura que afecta al jugador ahora mismo (o null). */
    public static Aura of(ServerPlayer player) {
        return ACTIVE.get(player.getUUID());
    }

    /** Cambio de duración de los efectos negativos por el aura (negativo = más cortos). */
    public static double debuffDurationChange(ServerPlayer player) {
        Aura a = ACTIVE.get(player.getUUID());
        return a == null ? 0 : a.debuffDuration();
    }

    /** Estandarte de Guerra: bonus de daño del aliado. */
    public static double damageBonus(ServerPlayer player) {
        Aura a = ACTIVE.get(player.getUUID());
        return a == null ? 0 : a.damage();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % PERIOD_TICKS != 0) return;

        Map<UUID, Aura> next = new HashMap<>();
        for (ServerPlayer paladin : server.getPlayerList().getPlayers()) {
            if (paladin instanceof FakePlayer || !paladin.isAlive() || paladin.isSpectator()) continue;
            ActiveMechanic aura = MechanicService.find(paladin, AURA);
            if (aura == null) continue;
            double radius = aura.number("radius", 8);
            double armor = aura.number("aura_armor", 0);
            double debuff = aura.number("aura_debuff_duration", 0);
            double allyDamage = MechanicService.number(paladin, WAR, "ally_damage", 0);
            double allyRegen = MechanicService.number(paladin, LIFE, "ally_heal_per_second", 0);
            for (ServerPlayer target : paladin.serverLevel().players()) {
                if (target instanceof FakePlayer || !target.isAlive() || target.isSpectator()) continue;
                boolean self = target == paladin;
                if (!self && (!isAlly(paladin, target) || target.distanceToSqr(paladin) > radius * radius)) continue;
                Aura given = new Aura(armor, debuff, self ? 0 : allyDamage, self ? 0 : allyRegen);
                next.merge(target.getUUID(), given, Aura::max);
            }
        }

        // Quitar el aura a quien ya no la recibe.
        for (UUID id : ACTIVE.keySet()) {
            if (next.containsKey(id)) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) setArmor(p, 0);
        }
        ACTIVE.clear();
        ACTIVE.putAll(next);
        for (Map.Entry<UUID, Aura> e : next.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) continue;
            Aura a = e.getValue();
            setArmor(p, a.armor());
            if (a.regen() > 0 && p.getHealth() < p.getMaxHealth()) p.heal((float) (a.regen() * PERIOD_TICKS / 20.0));
            p.addEffect(new MobEffectInstance(ModEffects.BANNER.get(), PERIOD_TICKS + 15, 0, true, false, true));
        }
    }

    public static boolean isAlly(ServerPlayer paladin, ServerPlayer other) {
        Team a = paladin.getTeam();
        Team b = other.getTeam();
        return a == null || b == null || a.isAlliedTo(b);
    }

    private static void setArmor(ServerPlayer player, double fraction) {
        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        if (armor == null) return;
        armor.removeModifier(ARMOR_ID);
        if (fraction != 0) {
            armor.addTransientModifier(new AttributeModifier(ARMOR_ID, ARMOR_NAME, fraction, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }
}
