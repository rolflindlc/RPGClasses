package io.github.rpgclasses.cleric;

import com.google.gson.JsonObject;
import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.content.ClassFx;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.Allies;
import io.github.rpgclasses.server.DebugLog;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Vínculo de Sangre (Mártir, rev. 2.22). Mirando a un objetivo a {@code cast_range} bloques y pulsando la tecla, el Mártir
 * se vincula a él con un lazo de sangre visible. Puede tener un aliado y un enemigo a la vez.
 * <ul>
 *   <li><b>Aliado</b> (se rompe a más de {@code ally_range}, 15 bloques): del daño final que iba a recibir (tras todas sus
 *   reducciones), él recibe solo la mitad y el Mártir la otra mitad como daño verdadero.</li>
 *   <li><b>Enemigo</b> (se rompe a más de {@code enemy_range}, 10 bloques): recibe también el 25 % de todo el daño que
 *   recibe el Mártir, incluida la vida que paga con el Sacrificio. El Mártir no se ahorra nada.</li>
 * </ul>
 * Pulsar mirando a un vinculado rompe ese vínculo; sin objetivo, rompe los dos. Al romperse empieza la recarga.
 */
public final class BloodBond {
    public static final String BLOOD_BOND = Ids.NS + ":blood_bond";
    public static final ResourceKey<DamageType> BOND_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("blood_bond"));

    private static final class Bonds {
        UUID ally;
        UUID enemy;
    }

    private static final Map<UUID, Bonds> BONDS = io.github.rpgclasses.server.SessionState.track(new HashMap<>());
    /** Daño que el propio vínculo está repartiendo: no se vuelve a repartir (evita bucles entre Mártires). */
    private static final ThreadLocal<Boolean> SHARING = ThreadLocal.withInitial(() -> false);

    private BloodBond() {}

    public static void register() {
        AbilityService.register(BLOOD_BOND, BloodBond::press);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new Events());
    }

    private static JsonObject params() {
        return AbilityService.params(BLOOD_BOND);
    }

    private static boolean press(ServerPlayer player, JsonObject p) {
        Bonds b = BONDS.computeIfAbsent(player.getUUID(), k -> new Bonds());
        LivingEntity target = io.github.rpgclasses.rogue.FaceChanger.lookedAt(player, AbilityService.number(p, "cast_range", 10));
        if (target == null) {
            if (b.ally == null && b.enemy == null) {
                player.displayClientMessage(Component.translatable("msg.rites_and_paths.blood_bond.no_target"), true);
            } else {
                breakBond(player, true, "voluntary");
                breakBond(player, false, "voluntary");
            }
            return false;
        }
        if (target.getUUID().equals(b.ally)) {
            breakBond(player, true, "voluntary");
            return false;
        }
        if (target.getUUID().equals(b.enemy)) {
            breakBond(player, false, "voluntary");
            return false;
        }
        boolean ally = Cleric.ally(player, target);
        if (!ally && !Allies.isEnemy(player, target)) return false;
        if (ally) b.ally = target.getUUID();
        else b.enemy = target.getUUID();
        ServerLevel level = player.serverLevel();
        ClassFx.line(player, target, ClassFx.BLOOD, 16);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1f, 0.7f);
        player.displayClientMessage(Component.translatable(ally ? "msg.rites_and_paths.blood_bond.ally" : "msg.rites_and_paths.blood_bond.enemy",
                target.getDisplayName()), true);
        DebugLog.event(player, "clérigo", "Vínculo de Sangre con {} ({})", DebugLog.describe(target), ally ? "aliado" : "enemigo");
        return false; // la recarga empieza cuando el vínculo se rompe
    }

    private static void breakBond(ServerPlayer player, boolean ally, String why) {
        Bonds b = BONDS.get(player.getUUID());
        if (b == null) return;
        UUID id = ally ? b.ally : b.enemy;
        if (id == null) return;
        if (ally) b.ally = null;
        else b.enemy = null;
        AbilityService.setCooldown(player, BLOOD_BOND, AbilityService.ticks(params(), "cooldown_seconds", 15));
        Net.syncPlayer(player);
        player.displayClientMessage(Component.translatable("msg.rites_and_paths.blood_bond.broken"), true);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.8f, 0.6f);
        DebugLog.event(player, "clérigo", "Vínculo de Sangre roto ({}, {})", ally ? "aliado" : "enemigo", why);
    }

    private static LivingEntity find(ServerPlayer player, UUID id) {
        if (id == null) return null;
        Entity e = player.serverLevel().getEntity(id);
        return e instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    private static DamageSource bondSource(ServerPlayer martyr, Entity cause) {
        return new DamageSource(martyr.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BOND_DAMAGE),
                cause == null ? martyr : cause);
    }

    /** Daño del vínculo: no lo frena el tiempo de invulnerabilidad del golpe que lo provoca. */
    private static void hurtNow(LivingEntity target, DamageSource source, float amount) {
        int prev = target.invulnerableTime;
        target.invulnerableTime = 0;
        target.hurt(source, amount);
        target.invulnerableTime = Math.max(prev, 0);
    }

    /** El Mártir pierde vida sin un golpe (Sacrificio): el enemigo vinculado recibe su parte. */
    public static void onSelfPay(ServerPlayer martyr, float lost) {
        reflect(martyr, lost);
    }

    private static void reflect(ServerPlayer martyr, float amount) {
        Bonds b = BONDS.get(martyr.getUUID());
        if (b == null || b.enemy == null || amount <= 0) return;
        LivingEntity enemy = find(martyr, b.enemy);
        if (enemy == null) return;
        float share = (float) (amount * AbilityService.number(params(), "reflected_to_enemy", 0.25));
        SHARING.set(true);
        try {
            hurtNow(enemy, bondSource(martyr, martyr), share);
        } finally {
            SHARING.set(false);
        }
    }

    /** Mártir vinculado como aliado a esta entidad (o null). */
    private static ServerPlayer martyrOf(LivingEntity ally) {
        for (Map.Entry<UUID, Bonds> e : BONDS.entrySet()) {
            if (!ally.getUUID().equals(e.getValue().ally)) continue;
            ServerPlayer sp = ally.getServer() == null ? null : ally.getServer().getPlayerList().getPlayer(e.getKey());
            if (sp != null && sp.isAlive()) return sp;
        }
        return null;
    }

    public static final class Events {
        /**
         * Después de todas las reducciones (prioridad baja) y antes del Milagro y del Ángel Guardián (mínima): el aliado se
         * queda con la mitad y el Mártir recibe la otra mitad; lo que recibe el Mártir alcanza a su enemigo vinculado.
         */
        @SubscribeEvent(priority = EventPriority.LOW)
        public void onDamage(LivingDamageEvent event) {
            if (SHARING.get() || BONDS.isEmpty()) return;
            LivingEntity victim = event.getEntity();
            if (victim.level().isClientSide() || event.getAmount() <= 0) return;
            DamageSource source = event.getSource();
            if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            // 1) Aliado vinculado: el Mártir se lleva su parte como daño verdadero.
            if (!source.is(BOND_DAMAGE)) {
                ServerPlayer martyr = martyrOf(victim);
                if (martyr != null && martyr != victim) {
                    float share = (float) (event.getAmount() * AbilityService.number(params(), "taken_from_ally", 0.5));
                    event.setAmount(event.getAmount() - share);
                    hurtNow(martyr, bondSource(martyr, source.getEntity()), share);
                    if (martyr.tickCount % 2 == 0) ClassFx.line(victim, martyr, ClassFx.BLOOD, 10);
                }
            }
            // 2) El Mártir recibe daño (también el que le pasa su aliado): su enemigo vinculado recibe su parte.
            if (victim instanceof ServerPlayer martyr && BONDS.containsKey(martyr.getUUID())) {
                reflect(martyr, Math.min(event.getAmount(), martyr.getHealth()));
            }
        }

        /** Lazo de sangre chorreante y rotura por distancia, muerte o pérdida de la habilidad. */
        @SubscribeEvent
        public void onTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player instanceof FakePlayer) return;
            Bonds b = BONDS.get(player.getUUID());
            if (b == null || (b.ally == null && b.enemy == null)) return;
            if (!player.isAlive() || !AbilityService.has(player, BLOOD_BOND)) {
                breakBond(player, true, "lost");
                breakBond(player, false, "lost");
                return;
            }
            JsonObject p = params();
            tether(player, b.ally, true, AbilityService.number(p, "ally_range", 15));
            tether(player, b.enemy, false, AbilityService.number(p, "enemy_range", 10));
        }

        private static void tether(ServerPlayer player, UUID id, boolean ally, double range) {
            if (id == null) return;
            LivingEntity other = find(player, id);
            if (other == null || other.distanceToSqr(player) > range * range) {
                breakBond(player, ally, other == null ? "gone" : "range");
                return;
            }
            if (player.tickCount % 3 != 0) return;
            ServerLevel level = player.serverLevel();
            Vec3 a = player.position().add(0, player.getBbHeight() * 0.6, 0);
            Vec3 c = other.position().add(0, other.getBbHeight() * 0.6, 0);
            int steps = (int) Math.max(4, a.distanceTo(c) * 2);
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                double sag = Math.sin(Math.PI * t) * 0.6; // el lazo cuelga un poco
                Vec3 pt = a.lerp(c, t).subtract(0, sag, 0);
                level.sendParticles(ClassFx.BLOOD, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
                if (player.getRandom().nextFloat() < 0.08f) {
                    level.sendParticles(ParticleTypes.DRIPPING_DRIPSTONE_LAVA, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
                }
            }
        }

        @SubscribeEvent
        public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            BONDS.remove(event.getEntity().getUUID());
        }

        @SubscribeEvent
        public void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                breakBond(player, true, "dimension");
                breakBond(player, false, "dimension");
            }
        }
    }
}
