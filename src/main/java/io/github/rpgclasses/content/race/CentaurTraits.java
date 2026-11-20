package io.github.rpgclasses.content.race;

import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.server.Allies;
import io.github.rpgclasses.server.DebugLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Subrazas del Centauro (adenda 0.7, sección 6): Pegaso (planeo), Unicornio (aura de maná, curación, cuerno) y
 * Guerrero (daño según la velocidad). La aceleración y la embestida del tronco están en {@link BodyTraits}.
 */
public final class CentaurTraits {
    private static final ResourceLocation IRONS_MANA_REGEN = new ResourceLocation("irons_spellbooks", "mana_regen");
    private static final UUID AURA = UUID.fromString("2d6f1b3c-8e4a-4f7d-b1c2-5a9e0d3f6c71");
    private static final double GALLOP_MIN = 0.4;

    /** Pegaso: jugadores planeando ahora mismo (para los ritos y para quitar el efecto al aterrizar). */
    private static final Set<UUID> GLIDING = io.github.rpgclasses.server.SessionState.track(new HashSet<>());
    /** Unicornio: aliados que llevan ahora el bono de maná del aura. */
    private static final Set<UUID> IN_AURA = io.github.rpgclasses.server.SessionState.track(new HashSet<>());
    /** Guerrero: golpes seguidos al galope. */
    private static final Map<UUID, Integer> CHAIN = io.github.rpgclasses.server.SessionState.track(new HashMap<>());

    public static boolean gliding(Player p) {
        return GLIDING.contains(p.getUUID());
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p instanceof FakePlayer || !p.isAlive()) return;
        DebugLog.guard(p, "centauro", () -> {
            glide(p);
            if (p.tickCount % 20 == 0) manaAura(p);
            if (BodyTraits.gallop(p) < GALLOP_MIN) CHAIN.remove(p.getUUID());
        });
    }

    /** Pegaso: al caer desde la altura del Rango gana caída lenta (oculta) hasta aterrizar; la caída lenta anula el daño. */
    private static void glide(ServerPlayer p) {
        ActiveMechanic g = Races.find(p, "race_glide");
        UUID id = p.getUUID();
        if (g == null || p.onGround() || p.isInWater() || p.isPassenger() || p.getAbilities().flying || p.isFallFlying()) {
            if (GLIDING.remove(id)) {
                net.minecraft.world.effect.MobEffectInstance e = p.getEffect(MobEffects.SLOW_FALLING);
                if (e != null && e.isAmbient() && !e.isVisible()) p.removeEffect(MobEffects.SLOW_FALLING);
            }
            return;
        }
        // La distancia de caída la calcula el servidor con el movimiento que llega del cliente (solo crece al caer).
        if (!GLIDING.contains(id) && p.fallDistance >= g.number("min_fall", 8)) {
            GLIDING.add(id);
            if (p.fallDistance >= 8) Rites.count(p, "glide_8", 1);
            Rites.count(p, "glide", 1);
            p.serverLevel().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.8, p.getZ(), 8, 0.6, 0.2, 0.6, 0.02);
        }
        if (GLIDING.contains(id)) p.addEffect(Races.hidden(MobEffects.SLOW_FALLING, 40, 0));
    }

    /** Unicornio: los aliados (jugadores) dentro del radio regeneran maná más rápido. A él no le afecta; no se acumula. */
    private static void manaAura(ServerPlayer p) {
        ActiveMechanic aura = Races.find(p, "race_mana_aura");
        if (aura == null) return;
        Attribute regen = ForgeRegistries.ATTRIBUTES.getValue(IRONS_MANA_REGEN);
        if (regen == null) return;
        double r = aura.number("radius", 8);
        for (ServerPlayer ally : p.serverLevel().getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(r),
                a -> a != p && a.isAlive() && a.distanceToSqr(p) <= r * r && Allies.isAlly(p, a))) {
            AttributeInstance inst = ally.getAttribute(regen);
            if (inst == null) continue;
            if (inst.getModifier(AURA) == null) {
                inst.addTransientModifier(new AttributeModifier(AURA, "rites_and_paths unicorn aura", aura.number("mana_regen", 0.5),
                        AttributeModifier.Operation.MULTIPLY_BASE));
            }
            ally.getPersistentData().putLong("rites_and_paths.unicorn_aura", p.level().getGameTime());
            IN_AURA.add(ally.getUUID());
        }
    }

    /** Quita el bono del aura a quien lleva más de 2 s sin estar cerca de un Unicornio. */
    @SubscribeEvent
    public void onAnyTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 20 != 10
                || !IN_AURA.contains(p.getUUID())) return;
        long last = p.getPersistentData().getLong("rites_and_paths.unicorn_aura");
        if (p.level().getGameTime() - last <= 40) return;
        Attribute regen = ForgeRegistries.ATTRIBUTES.getValue(IRONS_MANA_REGEN);
        AttributeInstance inst = regen == null ? null : p.getAttribute(regen);
        if (inst != null) inst.removeModifier(AURA);
        IN_AURA.remove(p.getUUID());
    }

    /** Unicornio: +50 % de curación de pociones y conjuros. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        double bonus = Races.number(p, "race_blessed", "healing", 0);
        if (bonus > 0 && HealSources.potionOrSpell(p)) event.setAmount((float) (event.getAmount() * (1 + bonus)));
    }

    /** Guerrero: más daño cuerpo a cuerpo cuanto más cerca de la velocidad máxima racial (desde el 40 %). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || event.getSource().getDirectEntity() != p) return;
        ActiveMechanic war = Races.find(p, "race_war_gallop");
        if (war == null || !event.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)) return;
        double f = BodyTraits.gallop(p);
        if (f < GALLOP_MIN) return;
        double bonus = war.number("damage_bonus", 0.2) * (f - GALLOP_MIN) / (1 - GALLOP_MIN);
        event.setAmount((float) (event.getAmount() * (1 + bonus)));
        Rites.credit(p, event.getEntity(), "gallop");
        int chain = CHAIN.merge(p.getUUID(), 1, Integer::sum);
        if (chain == 10) Rites.count(p, "gallop_chain", 10); // en el combate contra el jefe de rito cuenta para su rito
        if (p.level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.SWEEP_ATTACK, event.getEntity().getX(),
                event.getEntity().getY() + event.getEntity().getBbHeight() * 0.6, event.getEntity().getZ(), 1, 0, 0, 0, 0);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        GLIDING.remove(id);
        IN_AURA.remove(id);
        CHAIN.remove(id);
    }
}
