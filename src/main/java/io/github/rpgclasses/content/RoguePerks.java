package io.github.rpgclasses.content;

import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.MechanicService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Perks del Pícaro que no dependen de Epic Fight (rev. 2.7c): Golpe a Traición, Sigilo Profundo,
 * Ligereza (drenaje), Estocada y Esquiva. Toxinas está en {@link Toxins}.
 */
public final class RoguePerks {
    public static final String TREACHEROUS = "treacherous_strike";
    public static final String DEEP_STEALTH = "deep_stealth";
    public static final String STEALTH_DRAIN = "stealth_drain";
    public static final String ARMOR_PIERCE = "armor_pierce";
    public static final String EVASION = "evasion";
    private static final String CD_EVASION = "rpgclasses:evasion";

    /** Víctima -> (atacante -> tick de servidor del último golpe que el atacante le dio). Solo entre jugadores. */
    private static final Map<UUID, Map<UUID, Long>> PLAYER_HITS = new HashMap<>();

    /** Factor sobre el drenaje de la barra de Sigilo: duración (Golpe a Traición, Sigilo Profundo) y Ligereza. */
    public static double stealthDrainFactor(ServerPlayer player) {
        double duration = MechanicService.number(player, TREACHEROUS, "stealth_duration", 0)
                + MechanicService.number(player, DEEP_STEALTH, "stealth_duration", 0);
        double drain = MechanicService.number(player, STEALTH_DRAIN, "drain_change", 0);
        return (1 + drain) / Math.max(0.1, 1 + duration);
    }

    /**
     * Golpe a Traición: bonus contra quien no te tiene como objetivo. Mobs: su objetivo actual.
     * Jugadores: no te han golpeado en los últimos {@code pvp_window_seconds}.
     */
    public static double treacherousBonus(ServerPlayer attacker, LivingEntity target) {
        ActiveMechanic m = MechanicService.find(attacker, TREACHEROUS);
        if (m == null) return 0;
        if (target instanceof Mob mob) return mob.getTarget() == attacker ? 0 : m.number("damage_bonus", 0.25);
        if (target instanceof ServerPlayer other) {
            Long last = PLAYER_HITS.getOrDefault(attacker.getUUID(), Map.of()).get(other.getUUID());
            long window = Math.round(m.number("pvp_window_seconds", 5) * 20);
            return last != null && attacker.server.getTickCount() - last < window ? 0 : m.number("damage_bonus", 0.25);
        }
        return 0;
    }

    /**
     * Estocada: el golpe ignora una parte de la armadura del objetivo. Como la armadura se aplica después de
     * LivingHurtEvent, se devuelve el factor que deja el daño final igual al de un objetivo con menos armadura.
     */
    public static double armorPierceFactor(ServerPlayer attacker, LivingEntity target, DamageSource source, double damage) {
        ActiveMechanic m = MechanicService.find(attacker, ARMOR_PIERCE);
        if (m == null || damage <= 0 || source.is(DamageTypeTags.BYPASSES_ARMOR)) return 1;
        float armor = target.getArmorValue();
        if (armor <= 0) return 1;
        float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        double ignored = Math.max(0, Math.min(1, m.number("armor_ignored", 0.3)));
        float actual = CombatRules.getDamageAfterAbsorb((float) damage, armor, toughness);
        float wanted = CombatRules.getDamageAfterAbsorb((float) damage, (float) (armor * (1 - ignored)), toughness);
        return actual <= 0 ? 1 : wanted / actual;
    }

    /** Esquiva: probabilidad de evitar un golpe cuerpo a cuerpo o un proyectil, saltando hacia atrás. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == player) return;
        boolean melee = source.getDirectEntity() == attacker;
        if (!melee && !(source.getDirectEntity() instanceof Projectile)) return; // ni área ni entorno
        ActiveMechanic m = MechanicService.find(player, EVASION);
        if (m == null || AbilityService.remainingTicks(player, CD_EVASION) > 0) return;
        if (player.getRandom().nextDouble() >= m.number("chance", 0.15)) return;
        event.setCanceled(true);
        AbilityService.setCooldown(player, CD_EVASION, (int) Math.round(m.number("icd_seconds", 1) * 20));

        Vec3 from = source.getDirectEntity() != null ? source.getDirectEntity().position() : attacker.position();
        Vec3 away = player.position().subtract(from).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0E-4) away = player.getLookAngle().multiply(-1, 0, -1);
        away = away.normalize();
        double blocks = m.number("leap_blocks", 1);
        if (isSafe(player.level(), player.position().add(away.scale(blocks)))) {
            player.setDeltaMovement(away.scale(0.25 + blocks * 0.2).add(0, 0.25, 0)); // aproximado [MEDIR]
            player.hurtMarked = true;
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE,
                SoundSource.PLAYERS, 1.0f, 1.3f);
    }

    /** Destino seguro: sin lava y con suelo a menos de 3 bloques por debajo. */
    private static boolean isSafe(Level level, Vec3 pos) {
        BlockPos base = BlockPos.containing(pos);
        for (int dy = 0; dy >= -3; dy--) {
            BlockPos p = base.offset(0, dy, 0);
            if (level.getFluidState(p).is(FluidTags.LAVA)) return false;
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return true;
            if (level.getFluidState(p).is(FluidTags.WATER)) return true;
        }
        return false;
    }

    /** Registro de golpes entre jugadores (para Golpe a Traición en PvP). */
    @SubscribeEvent(priority = EventPriority.MONITOR)
    public void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || !(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (victim == attacker || event.getAmount() <= 0) return;
        PLAYER_HITS.computeIfAbsent(victim.getUUID(), k -> new HashMap<>()).put(attacker.getUUID(), (long) attacker.server.getTickCount());
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYER_HITS.remove(event.getEntity().getUUID());
    }
}
