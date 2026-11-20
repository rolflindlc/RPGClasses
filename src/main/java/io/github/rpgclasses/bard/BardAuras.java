package io.github.rpgclasses.bard;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.Allies;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Auras del Bardo (canciones): duran un tiempo fijo y, cada medio segundo, dan su efecto a los aliados (o a los
 * enemigos, la Elegía) dentro del radio. El efecto dura un poco más que el refresco y, con Reverberación, sigue unos
 * segundos tras salir del aura o tras terminar la canción (con Estela, más tiempo y más débil). Las de distintos
 * Bardos no se apilan: el efecto se queda con el amplificador más alto. Volver a lanzar la misma canción la renueva.
 */
public final class BardAuras {
    public enum Kind { MARCH, WAR_HYMN, REST_BALLAD, ELEGY, IRON_CHORALE, MANA_CADENCE }

    public static final ResourceKey<DamageType> SONIC = ResourceKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("sonic"));
    private static final int PERIOD = 10;

    /** Una canción sonando. {@code potency}: valor ya escalado por nivel y poder (fracción, multiplicador o vida/s). */
    private record Aura(Kind kind, long end, double potency, double radius) {}

    private static final Map<UUID, List<Aura>> ACTIVE = io.github.rpgclasses.server.SessionState.track(new HashMap<>());

    public BardAuras() {}

    /** Radio de las auras del Bardo: base + Resonancia + bonus de nivel, por perk e instrumento. */
    public static double radius(ServerPlayer bard, double levelBonus) {
        double r = Bard.number(Bard.RHYTHM, "aura_radius", 8) + Bard.mech(bard, Bard.AURA_RADIUS, "extra_blocks", 0) + levelBonus;
        r *= 1 + Bard.mech(bard, Bard.AURA_SHAPE, "radius_factor", 0);
        Instrument instrument = Bard.instrument(bard);
        if (instrument != null) r *= instrument.auraRadiusFactor();
        return Math.max(1, r);
    }

    /** Empieza (o renueva) una canción. */
    public static void start(ServerPlayer bard, Kind kind, int ticks, double potency, double levelRadiusBonus) {
        List<Aura> list = ACTIVE.computeIfAbsent(bard.getUUID(), k -> new ArrayList<>());
        list.removeIf(a -> a.kind() == kind);
        Aura aura = new Aura(kind, bard.level().getGameTime() + ticks, potency, radius(bard, levelRadiusBonus));
        list.add(aura);
        apply(bard, aura);
        ring(bard, aura.radius());
        // Al empezar una canción: estallido del color de la canción.
        ServerLevel level = bard.serverLevel();
        level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(color(kind), 1.6f),
                bard.getX(), bard.getY() + 1.0, bard.getZ(), 24, 0.6, 0.6, 0.6, 0.05);
    }

    public static boolean hasActive(ServerPlayer bard) {
        List<Aura> list = ACTIVE.get(bard.getUUID());
        if (list == null) return false;
        long now = bard.level().getGameTime();
        for (Aura a : list) if (a.end() > now) return true;
        return false;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % PERIOD != 0) return;
        Iterator<Map.Entry<UUID, List<Aura>>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, List<Aura>> e = it.next();
            ServerPlayer bard = server.getPlayerList().getPlayer(e.getKey());
            if (bard == null || !bard.isAlive()) {
                it.remove();
                continue;
            }
            long now = bard.level().getGameTime();
            Iterator<Aura> auras = e.getValue().iterator();
            while (auras.hasNext()) {
                Aura a = auras.next();
                if (now >= a.end()) {
                    auras.remove();
                    lastNote(bard);
                } else {
                    apply(bard, a);
                }
            }
            if (e.getValue().isEmpty()) it.remove();
        }
    }

    private static void apply(ServerPlayer bard, Aura aura) {
        ServerLevel level = bard.serverLevel();
        double stageFactor = AbilityService.has(bard, Bard.HARMONY)
                ? 1 + Bard.number(Bard.HARMONY, "buff_per_stage", 0.06) * BardState.stage(bard) : 1;
        double p = aura.potency() * stageFactor * (1 + Bard.mech(bard, Bard.AURA_SHAPE, "potency_factor", 0));
        int refresh = AbilityService.ticks(Bard.params(Bard.RHYTHM), "aura_refresh_seconds", 1.25);
        int linger;
        double lingerPotency = 1;
        if (Bard.has(bard, Bard.TRAIL)) {
            linger = (int) Math.round(Bard.mech(bard, Bard.TRAIL, "linger_seconds", 10) * 20);
            lingerPotency = Bard.mech(bard, Bard.TRAIL, "linger_potency", 0.7);
        } else {
            linger = (int) Math.round(Bard.mech(bard, Bard.AURA_LINGER, "linger_seconds", 0) * 20);
        }
        double r = aura.radius();
        Instrument instrument = Bard.instrument(bard);
        RegistryObject<MobEffect> ward = instrument == null ? null : switch (instrument.passive) {
            case SCALE_FIRE -> BardEffects.SCALE_WARD_FIRE;
            case SCALE_ICE -> BardEffects.SCALE_WARD_ICE;
            case SCALE_LIGHTNING -> BardEffects.SCALE_WARD_LIGHTNING;
            default -> null;
        };
        double sharedStep = Bard.has(bard, Bard.SHARED_STEP) ? BardState.danceBonus(bard) * Bard.mech(bard, Bard.SHARED_STEP, "ally_share", 0.5) : 0;

        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, bard.getBoundingBox().inflate(r))) {
            if (!e.isAlive() || e.distanceToSqr(bard) > r * r) continue;
            if (aura.kind() == Kind.ELEGY) {
                if (!enemy(bard, e)) continue;
                int t = refresh + linger;
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, t, 0, true, true, true), bard);
                // Control contra jefes a la mitad: solo Debilidad, sin Lentitud.
                if (!io.github.rpgclasses.content.race.Races.isBoss(e)) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, t, 0, true, true, true), bard);
                }
                continue;
            }
            if (!Allies.isAlly(bard, e) || (e instanceof Player pl && pl.isSpectator())) continue;
            switch (aura.kind()) {
                case MARCH -> BardEffects.give(e, BardEffects.MARCH.get(), (int) Math.round(20 * p), refresh, linger, lingerPotency, bard);
                case WAR_HYMN -> BardEffects.give(e, BardEffects.WAR_HYMN.get(), (int) Math.round(100 * p), refresh, linger, lingerPotency, bard);
                case REST_BALLAD -> BardEffects.give(e, BardEffects.REST_BALLAD.get(), (int) Math.round(10 * p), refresh, linger, lingerPotency, bard);
                case IRON_CHORALE -> BardEffects.give(e, BardEffects.IRON_CHORALE.get(), (int) Math.round(Math.min(60, 100 * p)), refresh, linger, lingerPotency, bard);
                case MANA_CADENCE -> BardEffects.give(e, BardEffects.MANA_CADENCE.get(), (int) Math.round(100 * p), refresh, linger, lingerPotency, bard);
                default -> { }
            }
            // Lira de Escamas: resistencia a su elemento (25 %, propuesta).
            if (ward != null) BardEffects.give(e, ward.get(), 25, refresh, linger, lingerPotency, bard);
            // Paso Compartido: la mitad del bonus de Danza para los aliados (no para ti).
            if (sharedStep > 0 && e != bard) BardEffects.give(e, BardEffects.SHARED_STEP.get(), (int) Math.round(sharedStep * 100), refresh, linger, lingerPotency, bard);
        }
        if (bard.tickCount % 20 < PERIOD) {
            level.sendParticles(ParticleTypes.NOTE, bard.getX(), bard.getY() + 2.2, bard.getZ(), 1, 0.4, 0.1, 0.4, 1);
        }
        visuals(bard, aura, level);
    }

    // ------------------------------------------------------------------ efectos visuales (adenda del Clérigo, 11.2)

    /** Color de cada canción: anillo en el borde del aura y destellos en quien la recibe. */
    private static org.joml.Vector3f color(Kind kind) {
        return switch (kind) {
            case MARCH -> new org.joml.Vector3f(0.98f, 0.85f, 0.25f);
            case WAR_HYMN -> new org.joml.Vector3f(0.92f, 0.22f, 0.18f);
            case REST_BALLAD -> new org.joml.Vector3f(0.35f, 0.92f, 0.45f);
            case ELEGY -> new org.joml.Vector3f(0.55f, 0.25f, 0.75f);
            case IRON_CHORALE -> new org.joml.Vector3f(0.70f, 0.78f, 0.88f);
            case MANA_CADENCE -> new org.joml.Vector3f(0.30f, 0.55f, 1.0f);
        };
    }

    /**
     * Cada refresco (medio segundo): un anillo de polvo del color de la canción que gira despacio en el borde del aura,
     * notas que suben alrededor del Bardo y un destello del mismo color en cada aliado (o enemigo, la Elegía) afectado.
     * Todo son partículas de vanilla enviadas por el servidor, así que las ven todos y funcionan con shaders.
     */
    private static void visuals(ServerPlayer bard, Aura aura, ServerLevel level) {
        net.minecraft.core.particles.DustParticleOptions dust = new net.minecraft.core.particles.DustParticleOptions(color(aura.kind()), 1.1f);
        double r = aura.radius();
        int points = (int) Math.max(12, Math.min(40, r * 3));
        double spin = (bard.tickCount % 80) / 80.0 * Math.PI * 2 + aura.kind().ordinal() * 0.4;
        for (int i = 0; i < points; i++) {
            double a = spin + Math.PI * 2 * i / points;
            level.sendParticles(dust, bard.getX() + Math.cos(a) * r, bard.getY() + 0.15, bard.getZ() + Math.sin(a) * r, 1, 0, 0.02, 0, 0);
        }
        // Notas que suben en espiral alrededor del Bardo (color de la nota = color de la canción en la paleta de notas).
        double a = spin * 2;
        level.sendParticles(ParticleTypes.NOTE, bard.getX() + Math.cos(a) * 0.8, bard.getY() + 0.6 + (bard.tickCount % 20) / 20.0,
                bard.getZ() + Math.sin(a) * 0.8, 0, aura.kind().ordinal() / 6.0, 0, 0, 1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, bard.getBoundingBox().inflate(r))) {
            if (e == bard || !e.isAlive() || e.distanceToSqr(bard) > r * r || e.isInvisible()) continue;
            boolean affected = aura.kind() == Kind.ELEGY ? enemy(bard, e) : Allies.isAlly(bard, e);
            if (!affected) continue;
            level.sendParticles(dust, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 2, 0.25, 0.3, 0.25, 0);
        }
    }

    /** Enemigo válido para la Elegía: mobs hostiles, jugadores enemigos o mobs que persiguen a un aliado. */
    static boolean enemy(ServerPlayer bard, LivingEntity e) {
        if (!Allies.isEnemy(bard, e)) return false;
        if (e instanceof Enemy || e instanceof Player) return true;
        return e instanceof Mob m && m.getTarget() != null && Allies.isAlly(bard, m.getTarget());
    }

    /** Última Nota: al terminar una canción, onda que empuja y hace un poco de daño sónico (el doble con el Órgano). */
    private static void lastNote(ServerPlayer bard) {
        if (!Bard.has(bard, Bard.LAST_NOTE)) return;
        Instrument instrument = Bard.instrument(bard);
        boolean organ = instrument != null && instrument.passive == Instrument.Passive.ABYSSAL_ORGAN;
        double r = Bard.mech(bard, Bard.LAST_NOTE, "radius", 4) * (organ ? 1.25 : 1);
        float damage = (float) (Bard.mech(bard, Bard.LAST_NOTE, "sonic_damage", 2) * (organ ? 2 : 1) * musicPower(bard));
        double push = Bard.mech(bard, Bard.LAST_NOTE, "knockback_strength", 1);
        ServerLevel level = bard.serverLevel();
        level.playSound(null, bard.getX(), bard.getY(), bard.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.5f, 0.6f);
        level.sendParticles(ParticleTypes.SONIC_BOOM, bard.getX(), bard.getY() + 1, bard.getZ(), 1, 0, 0, 0, 0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, bard.getBoundingBox().inflate(r))) {
            if (e.distanceToSqr(bard) > r * r || !Allies.isEnemy(bard, e)) continue;
            e.hurt(sonic(bard), damage);
            Vec3 d = e.position().subtract(bard.position());
            if (d.lengthSqr() > 1e-4) e.knockback(push, -d.x, -d.z);
        }
    }

    /** Multiplicador de poder de la escuela musical del Bardo (1 = 100 %). */
    public static double musicPower(ServerPlayer bard) {
        return 1 + BardState.musicBonus(bard);
    }

    public static DamageSource sonic(ServerPlayer bard) {
        return new DamageSource(bard.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SONIC), bard);
    }

    private static void ring(ServerPlayer bard, double r) {
        ServerLevel level = bard.serverLevel();
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            level.sendParticles(ParticleTypes.NOTE, bard.getX() + Math.cos(a) * r, bard.getY() + 0.3, bard.getZ() + Math.sin(a) * r, 1, 0, 0, 0, i / 24.0);
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer)) ACTIVE.remove(p.getUUID());
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }
}
