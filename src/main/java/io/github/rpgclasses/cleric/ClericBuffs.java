package io.github.rpgclasses.cleric;

import io.github.rpgclasses.content.BannerAura;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.registry.ModEffects;
import io.github.rpgclasses.server.CombatTracker;
import io.github.rpgclasses.server.DebugLog;
import io.github.rpgclasses.server.EffectRules;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Buffs del Clérigo.
 * <ul>
 *   <li><b>Bendición</b> y sus perks: los efectos beneficiosos que da un conjuro del Clérigo (a él o a sus aliados) se
 *       interceptan mientras se lanza ({@code SpellOnCastEvent} → fin de {@code castSpell}) y se vuelven a aplicar al final
 *       del tick con su duración y nivel cambiados. Así se sabe a cuántos alcanzó el conjuro: si fueron varios es un
 *       buff de área (Bendición Egoísta). Los que tocan al propio Clérigo también duran +50 % con Egoísta.</li>
 *   <li><b>Sanación</b> sobre la absorción: Absorción y Fortificar de Iron's suben de nivel con el bonus de curación.</li>
 *   <li><b>Bendición Mayor</b>: +1 nivel (tope III) y +20 % del maná del conjuro, que se cobra al final del tick.</li>
 *   <li><b>Fe Ciega</b>: regeneración de maná distinta dentro y fuera de combate (modificador que se revisa cada segundo).</li>
 *   <li><b>Halo</b> (Ángel): regenera a los aliados cercanos (no se suma al Estandarte de Vida: cuenta el más alto).</li>
 * </ul>
 * Los efectos que aplican entidades creadas por el conjuro más tarde (nubes, círculos) no pasan por aquí.
 */
public final class ClericBuffs {
    /** Conjuro del Clérigo que se está lanzando ahora (entre SpellOnCastEvent y el final de castSpell). */
    private record Cast(UUID caster, String spellId, float mana, long tick) {}

    private record Captured(LivingEntity target, MobEffectInstance instance) {}

    private static Cast current;
    private static final Map<Cast, List<Captured>> PENDING = io.github.rpgclasses.server.SessionState.track(new LinkedHashMap<>());
    private static boolean reapplying;

    private static final UUID BLIND_FAITH_ID = UUID.fromString("a7c2e1d4-5b3f-4e8a-9c61-2f0d7b8e4a19");
    private static final String BLIND_FAITH_NAME = "rites_and_paths_cleric:blind_faith";
    private static final ResourceLocation FORTIFY = new ResourceLocation("irons_spellbooks", "fortify");

    public ClericBuffs() {}

    // ------------------------------------------------------------------ ventana de lanzamiento (puente con Iron's)

    /** Iron's va a ejecutar un conjuro (SpellOnCastEvent). {@code mana}: lo que costará (0 si no gasta maná). */
    public static void castStart(ServerPlayer player, String spellId, float mana) {
        current = Cleric.isCleric(player) ? new Cast(player.getUUID(), spellId, mana, player.level().getGameTime()) : null;
    }

    /** Fin de {@code castSpell} (mixin): lo que se aplique a partir de aquí ya no es de este conjuro. */
    public static void castEnd() {
        current = null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onApplicable(MobEffectEvent.Applicable event) {
        Cast cast = current;
        if (cast == null || reapplying || EffectRules.reapplying() || event.getResult() == Event.Result.DENY) return;
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || target.level().getGameTime() != cast.tick()) return;
        MobEffectInstance inst = event.getEffectInstance();
        MobEffect effect = inst.getEffect();
        if (effect.getCategory() != MobEffectCategory.BENEFICIAL || effect instanceof ModEffects.StateEffect) return;
        MinecraftServer server = target.getServer();
        ServerPlayer caster = server == null ? null : server.getPlayerList().getPlayer(cast.caster());
        if (caster == null || !Cleric.ally(caster, target) || !touchesBuffs(caster)) return;
        event.setResult(Event.Result.DENY);
        PENDING.computeIfAbsent(cast, k -> new ArrayList<>()).add(new Captured(target, new MobEffectInstance(inst)));
    }

    /** ¿Tiene algo que cambie sus buffs? (si no, no hace falta interceptarlos). */
    private static boolean touchesBuffs(ServerPlayer p) {
        return Cleric.mech(p, Cleric.BLESSING) != null || Cleric.mech(p, Cleric.GREATER_BLESSING) != null
                || Cleric.mech(p, Cleric.SELFISH_BLESSING) != null || Cleric.mech(p, Cleric.HEALING_POWER) != null;
    }

    private static void flush(MinecraftServer server) {
        current = null;
        if (PENDING.isEmpty()) return;
        List<Map.Entry<Cast, List<Captured>>> batch = new ArrayList<>(PENDING.entrySet());
        PENDING.clear();
        for (Map.Entry<Cast, List<Captured>> e : batch) {
            ServerPlayer caster = server.getPlayerList().getPlayer(e.getKey().caster());
            List<Captured> list = e.getValue();
            if (caster == null) {
                for (Captured c : list) reapply(c.target(), c.instance());
                continue;
            }
            Set<LivingEntity> targets = new LinkedHashSet<>();
            for (Captured c : list) targets.add(c.target());
            boolean area = targets.size() > 1;
            double duration = 1 + Cleric.mech(caster, Cleric.BLESSING, "duration_bonus", 0);
            ActiveMechanic greater = Cleric.mech(caster, Cleric.GREATER_BLESSING);
            ActiveMechanic selfish = Cleric.mech(caster, Cleric.SELFISH_BLESSING);
            if (greater != null) duration *= 1 + greater.number("duration_change", -0.25);
            double heal = 1 + Cleric.mech(caster, Cleric.HEALING_POWER, "heal_bonus", 0);
            for (Captured c : list) {
                boolean self = c.target() == caster;
                if (selfish != null && area && !self) continue; // Bendición Egoísta: el área solo te alcanza a ti.
                double d = duration * (selfish != null && self ? 1 + selfish.number("duration_change", 0.5) : 1);
                MobEffectInstance in = c.instance();
                int amp = in.getAmplifier();
                if (isAbsorption(in.getEffect()) && heal > 1) amp = (int) Math.max(amp, Math.round((amp + 1) * heal) - 1);
                if (greater != null) {
                    int cap = (int) greater.number("max_amplifier", 2);
                    if (amp < cap) amp = Math.min(cap, amp + (int) greater.number("extra_levels", 1));
                }
                int ticks = in.isInfiniteDuration() ? in.getDuration() : (int) Math.max(1, Math.round(in.getDuration() * d));
                reapply(c.target(), new MobEffectInstance(in.getEffect(), ticks, amp, in.isAmbient(), in.isVisible(), in.showIcon()));
            }
            // Bendición Mayor: +20 % del maná del conjuro de buff.
            if (greater != null && e.getKey().mana() > 0 && io.github.rpgclasses.content.Chronomancy.mana != null && !caster.isCreative()) {
                var bridge = io.github.rpgclasses.content.Chronomancy.mana;
                float extra = (float) (e.getKey().mana() * greater.number("extra_mana_cost", 0.2));
                bridge.setMana(caster, Math.max(0, bridge.mana(caster) - extra));
            }
            DebugLog.event(caster, "clérigo", "Bendición: {} efectos de {} ({} objetivos{}), duración x{}", list.size(),
                    e.getKey().spellId(), targets.size(), area ? ", área" : "", duration);
        }
    }

    private static boolean isAbsorption(MobEffect effect) {
        return effect == MobEffects.ABSORPTION || FORTIFY.equals(ForgeRegistries.MOB_EFFECTS.getKey(effect));
    }

    private static void reapply(LivingEntity target, MobEffectInstance inst) {
        if (!target.isAlive()) return;
        reapplying = true;
        try {
            target.addEffect(inst);
        } finally {
            reapplying = false;
        }
    }

    // ------------------------------------------------------------------ Fe Ciega y Halo (cada segundo)

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        flush(server);
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p instanceof FakePlayer) continue;
            blindFaith(p);
            halo(p);
        }
    }

    private static void blindFaith(ServerPlayer p) {
        var regen = Cleric.ironsAttribute("mana_regen");
        if (regen == null) return;
        ActiveMechanic m = Cleric.mech(p, Cleric.BLIND_FAITH);
        double amount = 0;
        if (m != null) amount = CombatTracker.inCombat(p) ? m.number("in_combat_regen", -0.25) : m.number("out_of_combat_regen", 0.8);
        Cleric.setModifier(p, regen, BLIND_FAITH_ID, BLIND_FAITH_NAME, amount, AttributeModifier.Operation.MULTIPLY_BASE);
    }

    private static void halo(ServerPlayer angel) {
        ActiveMechanic m = Cleric.mech(angel, Cleric.HALO);
        if (m == null || !angel.isAlive() || angel.isSpectator() || Miracle.isDowned(angel)) return;
        double radius = m.number("radius", 8);
        double perSecond = m.number("ally_heal_per_second", 0);
        if (perSecond <= 0) return;
        ServerLevel level = angel.serverLevel();
        for (LivingEntity target : io.github.rpgclasses.server.Allies.alliesAround(angel, radius, false)) {
            if (Miracle.isDowned(target)) continue;
            BannerAura.Aura banner = BannerAura.of(target);
            double extra = perSecond - (banner == null ? 0 : banner.regen()); // no se suma al Estandarte de Vida
            if (extra > 0 && target.getHealth() < target.getMaxHealth()) {
                target.heal((float) extra);
                if (angel.tickCount % 40 < 20) level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 2.1, target.getZ(), 2, 0.2, 0.05, 0.2, 0.01);
            }
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (current != null && current.caster().equals(event.getEntity().getUUID())) current = null;
    }
}
