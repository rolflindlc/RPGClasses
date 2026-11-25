package io.github.rpgclasses.cleric;

import com.google.gson.JsonObject;
import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.content.ClassFx;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.registry.ModEffects;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.DebugLog;
import io.github.rpgclasses.server.EffectRules;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Habilidades del Clérigo y de sus subclases Ángel y Mártir (el Intercesor está en {@link Prayer}).
 * <ul>
 *   <li><b>Imposición de Manos</b> (Nivel Total 5): 30 % de la vida máxima del aliado al que miras × poder sagrado.</li>
 *   <li><b>Forma Angelical</b> (Ángel 5): vuelo 10 s a velocidad de caminar, purga y Regeneración II + Absorción I para ti
 *       y tus aliados a 10 bloques; sin daño de caída al terminar. <b>Gracia Alada</b>: −10 % de daño de caída por nivel.</li>
 *   <li><b>Sacrificio</b> (Mártir 5): pagas el 15 % de tu vida máxima y el aliado al que miras recupera el 20 % de la suya
 *       (mínimo lo pagado). Con la vida ≤ 15 %, el Sacrificio final (dos pulsaciones): el aliado recupera toda la vida
 *       que le falta y gana tantos corazones dorados como vida tenía. <b>Resolución</b>: menos daño
 *       recibido por debajo del 50 % de vida.</li>
 * </ul>
 */
public final class ClericSubclasses {
    public static final ResourceKey<DamageType> MARTYRDOM = ResourceKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("martyrdom"));

    /** Forma Angelical: fin de la forma por jugador. */
    private static final Map<UUID, Long> ANGEL_UNTIL = io.github.rpgclasses.server.SessionState.track(new HashMap<>());
    /** Sin daño de caída hasta tocar el suelo tras la Forma Angelical. */
    private static final Set<UUID> FALL_SAFE = io.github.rpgclasses.server.SessionState.track(new HashSet<>());
    /** Sacrificio final armado: aliado elegido y hasta cuándo vale la segunda pulsación. */
    private record Armed(UUID target, long until) {}
    private static final Map<UUID, Armed> ARMED = io.github.rpgclasses.server.SessionState.track(new HashMap<>());
    private static final float ANGEL_FLY_SPEED = 0.02f, DEFAULT_FLY_SPEED = 0.05f;

    public ClericSubclasses() {}

    // ------------------------------------------------------------------ Imposición de Manos

    static boolean layingOnHands(ServerPlayer player, JsonObject p) {
        LivingEntity target = Cleric.lookAlly(player, AbilityService.number(p, "range", 8));
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.rites_and_paths.cleric.no_target"), true);
            return false;
        }
        boolean downed = Miracle.isDowned(target);
        if (!downed && target.getHealth() >= target.getMaxHealth()) {
            player.displayClientMessage(Component.translatable("message.rites_and_paths.cleric.full_health", target.getDisplayName()), true);
            return false;
        }
        float amount = (float) (target.getMaxHealth() * AbilityService.number(p, "heal_fraction", 0.3) * Cleric.holyPower(player));
        float healed = ClericHealing.abilityHeal(player, target, amount);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ClassFx.GOLD, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 24, 0.4, 0.5, 0.4, 0);
        level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(), 3, 0.3, 0.1, 0.3, 0);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 1.4f);
        DebugLog.event(player, "clérigo", "Imposición de Manos: {} cura {} ({} efectivos)", target.getName().getString(), amount, healed);
        return true;
    }

    // ------------------------------------------------------------------ Ángel

    static boolean angelicForm(ServerPlayer player, JsonObject p) {
        int ticks = AbilityService.ticks(p, "duration_seconds", 10);
        double radius = AbilityService.number(p, "radius", 10);
        int regen = Math.max(0, (int) AbilityService.number(p, "regen_level", 2) - 1);
        int absorption = Math.max(0, (int) AbilityService.number(p, "absorption_level", 1) - 1);
        long now = player.level().getGameTime();
        ANGEL_UNTIL.put(player.getUUID(), now + ticks);
        FALL_SAFE.remove(player.getUUID());
        player.addEffect(new MobEffectInstance(ModEffects.ANGELIC_FORM.get(), ticks, 0, false, false, true));
        Abilities abilities = player.getAbilities();
        abilities.mayfly = true;
        abilities.flying = true;
        abilities.setFlyingSpeed(ANGEL_FLY_SPEED);
        player.onUpdateAbilities();
        player.setDeltaMovement(player.getDeltaMovement().add(0, 0.4, 0));
        player.hurtMarked = true;
        ServerLevel level = player.serverLevel();
        for (LivingEntity target : io.github.rpgclasses.server.Allies.alliesAround(player, radius, true)) {
            purge(target);
            target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ticks, regen), player);
            target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ticks, absorption), player);
            level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 1, target.getZ(), 16, 0.4, 0.6, 0.4, 0.05);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.8f, 1.6f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.2f);
        return true;
    }

    private static void purge(LivingEntity target) {
        List<MobEffectInstance> bad = new ArrayList<>();
        for (MobEffectInstance inst : target.getActiveEffects()) if (EffectRules.isNegative(inst.getEffect())) bad.add(inst);
        for (MobEffectInstance inst : bad) target.removeEffect(inst.getEffect());
    }

    public static boolean angelic(Player player) {
        return player.hasEffect(ModEffects.ANGELIC_FORM.get());
    }

    private static void endAngel(ServerPlayer player) {
        ANGEL_UNTIL.remove(player.getUUID());
        player.removeEffect(ModEffects.ANGELIC_FORM.get());
        Abilities abilities = player.getAbilities();
        abilities.setFlyingSpeed(DEFAULT_FLY_SPEED);
        if (!player.isCreative() && !player.isSpectator()) {
            abilities.mayfly = false;
            abilities.flying = false;
            if (!player.onGround()) FALL_SAFE.add(player.getUUID());
        }
        player.onUpdateAbilities();
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Long until = ANGEL_UNTIL.get(player.getUUID());
        if (until != null) {
            if (player.level().getGameTime() >= until || !player.isAlive()) endAngel(player);
            else {
                if (player.isSprinting()) player.setSprinting(false);
                if (player.tickCount % 4 == 0) player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                        player.getX(), player.getY() + 1.2, player.getZ(), 2, 0.5, 0.3, 0.5, 0.01);
            }
        }
        if (!FALL_SAFE.isEmpty() && FALL_SAFE.contains(player.getUUID()) && (player.onGround() || player.isInWater())) {
            player.resetFallDistance();
            FALL_SAFE.remove(player.getUUID());
        }
    }

    /** Gracia Alada y el final de la Forma Angelical. */
    @SubscribeEvent
    public void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (FALL_SAFE.remove(player.getUUID()) || ANGEL_UNTIL.containsKey(player.getUUID())) {
            event.setCanceled(true);
            return;
        }
        ActiveMechanic grace = Cleric.mech(player, Cleric.WINGED_GRACE);
        if (grace != null) event.setDamageMultiplier((float) (event.getDamageMultiplier() * Math.max(0, 1 - grace.number("fall_reduction", 0))));
    }

    // ------------------------------------------------------------------ Mártir

    static boolean sacrifice(ServerPlayer player, JsonObject p) {
        double cost = player.getMaxHealth() * AbilityService.number(p, "cost_fraction", 0.15);
        LivingEntity target = Cleric.lookAlly(player, AbilityService.number(p, "range", 12));
        long now = player.level().getGameTime();
        if (player.getHealth() <= cost) return finalSacrifice(player, p, target, now);
        ARMED.remove(player.getUUID());
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.rites_and_paths.cleric.no_target"), true);
            return false;
        }
        boolean downed = Miracle.isDowned(target);
        if (!downed && target.getHealth() >= target.getMaxHealth()) {
            player.displayClientMessage(Component.translatable("message.rites_and_paths.cleric.full_health", target.getDisplayName()), true);
            return false;
        }
        player.setHealth((float) (player.getHealth() - cost));
        BloodBond.onSelfPay(player, (float) cost); // Vínculo de Sangre: el enemigo vinculado también lo sufre
        float heal = (float) Math.max(target.getMaxHealth() * AbilityService.number(p, "heal_fraction", 0.2), cost);
        trueHeal(player, target, heal);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ClassFx.BLOOD, player.getX(), player.getY() + 1.2, player.getZ(), 12, 0.3, 0.4, 0.3, 0);
        ClassFx.line(player, target, ClassFx.RED, 12);
        level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(), 2, 0.3, 0.1, 0.3, 0);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.PLAYERS, 0.8f, 0.8f);
        DebugLog.event(player, "clérigo", "Sacrificio: paga {} de vida y cura {} a {}", cost, heal, target.getName().getString());
        return true;
    }

    /** Sacrificio final: la primera pulsación avisa; la segunda (con el mismo aliado a la vista) lo ejecuta. */
    private static boolean finalSacrifice(ServerPlayer player, JsonObject p, LivingEntity target, long now) {
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.rites_and_paths.cleric.no_target"), true);
            return false;
        }
        Armed armed = ARMED.get(player.getUUID());
        if (armed == null || now > armed.until() || !armed.target().equals(target.getUUID())) {
            ARMED.put(player.getUUID(), new Armed(target.getUUID(), now + AbilityService.ticks(p, "confirm_seconds", 5)));
            player.displayClientMessage(Component.translatable("message.rites_and_paths.cleric.final_armed", target.getDisplayName()), true);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0f, 1.0f);
            return false;
        }
        ARMED.remove(player.getUUID());
        // Recupera toda la vida que le falta y gana tantos corazones dorados (absorción) como vida tenía. Ignora toda
        // alteración de la curación: no pasa por el evento de curación (ni perks, ni otros mods, ni Purificación/Égida).
        float had = target.getHealth();
        float missing = Math.max(0, target.getMaxHealth() - had);
        trueHeal(player, target, (float) (missing * AbilityService.number(p, "final_missing_heal", 1.0)));
        float golden = (float) (had * AbilityService.number(p, "final_absorption", 1.0));
        if (golden > 0) target.setAbsorptionAmount(target.getAbsorptionAmount() + golden);
        Miracle.grantImmunity(target, AbilityService.ticks(p, "final_immunity_seconds", 2));
        if (target instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("message.rites_and_paths.cleric.final_done", player.getDisplayName()), true);
        bloodBurst(player);
        DebugLog.event(player, "clérigo", "Sacrificio final por {}", target.getName().getString());
        // Muerte voluntaria: el tipo de daño ignora la invulnerabilidad, así que no saltan el Tótem, Milagro ni las supervivencias.
        player.invulnerableTime = 0;
        player.hurt(new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(MARTYRDOM)), Float.MAX_VALUE);
        if (player.isAlive()) player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
        return true;
    }

    /**
     * Curación verdadera del Mártir (Sacrificio y Sacrificio final): un valor fijo que nada puede aumentar ni reducir.
     * No pasa por el evento de curación (ni perks, ni Sanación, ni efectos, ni otros mods); levanta a un tumbado.
     */
    private static void trueHeal(ServerPlayer martyr, LivingEntity target, float amount) {
        if (Miracle.isDowned(target)) Miracle.revive(target, martyr, 0);
        target.setHealth(Math.min(target.getMaxHealth(), target.getHealth() + Math.max(0, amount)));
    }

    /** Explosión de sangre: solo visual y sonora. */
    private static void bloodBurst(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockParticleOption blood = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
        level.sendParticles(blood, player.getX(), player.getY() + 1, player.getZ(), 80, 0.5, 0.7, 0.5, 0.4);
        level.sendParticles(ClassFx.BLOOD, player.getX(), player.getY() + 1, player.getZ(), 60, 0.9, 0.9, 0.9, 0.2);
        level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7f, 1.4f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.5f, 0.5f);
    }

    /** Resolución (Mártir) y Cuerpo en Oración (Intercesor): factor sobre el daño recibido. */
    public static double damageFactor(ServerPlayer victim) {
        double f = 1;
        ActiveMechanic resolve = Cleric.mech(victim, Cleric.MARTYR_RESOLVE);
        if (resolve != null && victim.getHealth() < victim.getMaxHealth() * resolve.number("health_threshold", 0.5)) {
            f *= 1 - resolve.number("damage_reduction", 0);
        }
        ActiveMechanic body = Cleric.mech(victim, Cleric.PRAYER_BODY);
        if (body != null && Prayer.praying(victim)) f *= 1 - body.number("damage_reduction", 0);
        return Math.max(0, f);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            if (ANGEL_UNTIL.containsKey(p.getUUID())) endAngel(p);
            ARMED.remove(p.getUUID());
            FALL_SAFE.remove(p.getUUID());
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            if (ANGEL_UNTIL.containsKey(p.getUUID())) endAngel(p);
            ARMED.remove(p.getUUID());
            FALL_SAFE.remove(p.getUUID());
        }
    }

    /** Al reaparecer o cambiar de dimensión el servidor rehace las capacidades: se quita la forma. */
    @SubscribeEvent
    public void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && ANGEL_UNTIL.containsKey(p.getUUID())) endAngel(p);
    }

    static void tickCleanup(MinecraftServer server) {
        ANGEL_UNTIL.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }
}
