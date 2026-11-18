package io.github.rpgclasses.bard;

import io.github.rpgclasses.content.CombatAbilities;
import io.github.rpgclasses.content.race.Races;
import io.github.rpgclasses.core.stat.StatCalculator;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.registry.ModAttributes;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.Allies;
import io.github.rpgclasses.server.DebugLog;
import io.github.rpgclasses.server.StatEngine;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Estado del minijuego de cada Bardo en el servidor (adenda del Bardo, secciones 2 y 4): combo, etapas de Armonía,
 * Crescendo y el golpe a compás. El cliente juzga las notas (la latencia no permite hacerlo aquí) y manda cada
 * resultado ({@link Packets.BardNote}); el servidor lleva la cuenta, la limita y devuelve el estado ({@link Packets.BardSync}).
 * Aplica además el poder musical (combo + Compás + instrumento + Solista/Coro, con tope) y los bonus del instrumento
 * como modificadores transitorios, y la velocidad de tocar y de Danza a través de {@link StatEngine}.
 */
public final class BardState {
    public static final int MISS = 0, HIT = 1, PERFECT = 2, EXTRA = 3, PHRASE = 4;

    /** No empiezan por "rites_and_paths:": StatEngine no debe borrarlos al recalcular. */
    private static final String MOD_PREFIX = "rites_and_paths_bard:";
    private static final ResourceLocation CAST_TIME = new ResourceLocation("irons_spellbooks", "cast_time_reduction");
    private static final ResourceLocation MANA_REGEN = new ResourceLocation("irons_spellbooks", "mana_regen");
    private static final ResourceLocation COOLDOWN = new ResourceLocation("irons_spellbooks", "cooldown_reduction");
    private static final ResourceLocation MAX_MANA = new ResourceLocation("irons_spellbooks", "max_mana");
    /** Margen (ticks) entre la nota de clic izquierdo y el golpe que la cobra (Epic Fight golpea con retraso). */
    private static final int BEAT_WINDOW_TICKS = 12;
    /** Aciertos por segundo como máximo (180 pulsos por minuto son 3 por segundo; el resto es margen). */
    private static final int MAX_HITS_PER_SECOND = 10;

    private static final class S {
        int combo, stage, consecutive, phraseMisses, phraseExtras, attackHits;
        long crescendoUntil, onBeatUntil, lastHolding, lastSecond, sunLuteReady;
        int hitsThisSecond;
        boolean resonantPending, dirty = true;
        double groupBonus;
        String speedKey = "";
    }

    private static final Map<UUID, S> STATES = new HashMap<>();

    /** Solo Magistral: el combo al máximo y sin perderlo (Crescendo) durante unos segundos. */
    public static void solo(ServerPlayer p, int ticks) {
        if (!Bard.isBard(p)) return;
        S s = STATES.computeIfAbsent(p.getUUID(), k -> new S());
        long now = p.level().getGameTime();
        s.combo = Math.max(s.combo, (int) Bard.number(Bard.RHYTHM, "max_combo", 30));
        s.crescendoUntil = Math.max(s.crescendoUntil, now + ticks);
        s.lastHolding = now;
        s.dirty = true;
        update(p, s, now);
    }

    // ------------------------------------------------------------------ consultas

    public static int combo(ServerPlayer p) {
        S s = STATES.get(p.getUUID());
        return s == null ? 0 : s.combo;
    }

    public static int stage(ServerPlayer p) {
        S s = STATES.get(p.getUUID());
        return s == null ? 0 : s.stage;
    }

    public static boolean crescendo(ServerPlayer p) {
        S s = STATES.get(p.getUUID());
        return s != null && s.crescendoUntil > p.level().getGameTime();
    }

    /** ¿Toca ahora mismo? (Bardo con un instrumento en la mano). */
    public static boolean playing(ServerPlayer p) {
        return Bard.instrumentHand(p) != null && Bard.isBard(p);
    }

    /** Golpe a compás: el siguiente golpe cuerpo a cuerpo tras una nota de clic izquierdo acertada. Lo gasta. */
    public static boolean consumeOnBeat(ServerPlayer p) {
        S s = STATES.get(p.getUUID());
        if (s == null || s.onBeatUntil < p.level().getGameTime()) return false;
        s.onBeatUntil = 0;
        return true;
    }

    /** Golpe Resonante (Cantor de Guerra): el golpe a compás que cierra cada quinto acierto de clic izquierdo. */
    public static boolean consumeResonant(ServerPlayer p) {
        S s = STATES.get(p.getUUID());
        if (s == null || !s.resonantPending) return false;
        s.resonantPending = false;
        return true;
    }

    /** Combo mínimo efectivo de un conjuro (Oído Fino del Juglar lo reduce). */
    public static int comboNeeded(ServerPlayer p, MusicSpellInfo spell) {
        int reduction = (int) Math.round(Bard.mech(p, Bard.FINE_EAR, "combo_reduction", 0));
        return Math.max(0, spell.combo - reduction);
    }

    // ------------------------------------------------------------------ notas

    /** Resultado de una nota juzgada por el cliente. */
    public static void onNote(ServerPlayer p, int type, int lane) {
        if (p instanceof FakePlayer || !p.isAlive() || !playing(p)) return;
        S s = STATES.computeIfAbsent(p.getUUID(), k -> new S());
        long now = p.level().getGameTime();
        s.lastHolding = now;
        switch (type) {
            case HIT, PERFECT -> hit(p, s, now, lane, type == PERFECT);
            case MISS -> miss(p, s);
            case EXTRA -> extra(p, s);
            case PHRASE -> phrase(p, s);
            default -> { }
        }
        s.dirty = true;
        update(p, s, now);
    }

    private static void hit(ServerPlayer p, S s, long now, int lane, boolean perfect) {
        if (now / 20 != s.lastSecond) {
            s.lastSecond = now / 20;
            s.hitsThisSecond = 0;
        }
        if (++s.hitsThisSecond > MAX_HITS_PER_SECOND) return; // más rápido de lo que el tempo permite
        int max = (int) Bard.number(Bard.RHYTHM, "max_combo", 30);
        int gain = s.crescendoUntil > now ? (int) Bard.number(Bard.CRESCENDO, "hit_multiplier", 2) : 1;
        int before = s.combo;
        s.combo = Math.min(max, s.combo + gain);
        s.consecutive++;
        Instrument instrument = Bard.instrument(p);

        // Contrapunto: al cruzar 10, 20 o 30, los conjuros en recarga pierden la mitad de lo que les queda.
        if (AbilityService.has(p, Bard.COUNTERPOINT)) {
            for (int t = 10; t <= max; t += 10) {
                if (before < t && s.combo >= t) {
                    if (Bard.magic != null) Bard.magic.refundAllCooldowns(p, Bard.number(Bard.COUNTERPOINT, "refund", 0.5));
                    p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.4f);
                    particles(p, ParticleTypes.NOTE, 8);
                }
            }
        }
        if (instrument != null) {
            switch (instrument.passive) {
                // Tambor del Coloso: cada 10 aciertos de combo (también pasado el tope de 30), estruendo que aturde.
                case COLOSSUS_DRUM -> {
                    if (s.consecutive % 10 == 0) colossusBoom(p);
                }
                // Arpa de Cristal: cada 10 aciertos seguidos, el 5 % del maná.
                case CRYSTAL_HARP -> {
                    if (s.consecutive % 10 == 0 && Bard.magic != null) Bard.magic.addMana(p, Bard.magic.maxMana(p) * 0.05f);
                }
                // Laúd del Sol Eterno: los aciertos perfectos curan a los aliados cercanos (y a ti, a la mitad).
                case SUN_LUTE -> {
                    if (perfect && now >= s.sunLuteReady) {
                        s.sunLuteReady = now + 10;
                        sunHeal(p);
                    }
                }
                default -> { }
            }
        }
        if (lane == Bard.LANE_ATTACK) {
            s.onBeatUntil = now + BEAT_WINDOW_TICKS;
            s.attackHits++;
            int every = (int) Bard.mech(p, Bard.RESONANT_STRIKE, "every_hits", 0);
            if (every > 0 && s.attackHits % every == 0) s.resonantPending = true;
        }
        // Los demás oyen la nota (el propio Bardo la oye en su cliente).
        if (instrument != null) {
            float pitch = (float) Math.pow(2.0, ((lane * 4 + Math.min(12, s.combo / 3)) - 12) / 12.0);
            p.level().playSound(p, p.getX(), p.getY(), p.getZ(), instrument.sound.getSoundEvent().value(), SoundSource.PLAYERS, 0.7f, pitch);
        }
    }

    private static void miss(ServerPlayer p, S s) {
        long now = p.level().getGameTime();
        s.consecutive = 0;
        s.phraseMisses++;
        if (s.crescendoUntil <= now) {
            // Un fallo quita la mitad del combo; Oído Absoluto (por puntos de rama) lo deja en un cuarto con 10 puntos.
            double keep = Math.max(Bard.number(Bard.RHYTHM, "miss_keep", 0.5), Bard.mech(p, Bard.TIMING_WINDOW, "miss_keep", 0));
            // Violín Espectral: pierdes la mitad de lo que perderías (50 % menos de pérdida).
            if (Bard.instrument(p) != null && Bard.instrument(p).passive == Instrument.Passive.SPECTRAL_VIOLIN) keep = 1 - (1 - keep) * 0.5;
            s.combo = (int) Math.floor(s.combo * Math.max(0, Math.min(1, keep)));
        }
        loseStages(p, s);
    }

    private static void extra(ServerPlayer p, S s) {
        s.phraseExtras++;
        int forgiven = (int) Bard.mech(p, Bard.TEMPO_PRESTO, "forgiven_per_phrase", 0);
        if (s.phraseExtras > forgiven) loseStages(p, s);
    }

    private static void phrase(ServerPlayer p, S s) {
        int forgiven = (int) Bard.mech(p, Bard.TEMPO_PRESTO, "forgiven_per_phrase", 0);
        if (AbilityService.has(p, Bard.HARMONY) && s.phraseMisses == 0 && s.phraseExtras <= forgiven) {
            s.stage = Math.min((int) Bard.number(Bard.HARMONY, "max_stage", 5), s.stage + 1);
        }
        s.phraseMisses = 0;
        s.phraseExtras = 0;
    }

    private static void loseStages(ServerPlayer p, S s) {
        if (s.stage > 0) s.stage = Math.max(0, s.stage - (int) Bard.number(Bard.HARMONY, "stage_loss", 2));
    }

    // ------------------------------------------------------------------ Crescendo

    public static boolean startCrescendo(ServerPlayer p, com.google.gson.JsonObject params) {
        if (!Bard.isBard(p)) return false;
        S s = STATES.computeIfAbsent(p.getUUID(), k -> new S());
        long now = p.level().getGameTime();
        s.crescendoUntil = now + AbilityService.ticks(params, "duration_seconds", 8);
        s.dirty = true;
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.2f, 0.7f);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.6f);
        particles(p, ParticleTypes.NOTE, 20);
        p.displayClientMessage(Component.translatable("msg.rites_and_paths.bard.crescendo"), true);
        update(p, s, now);
        return true;
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        long tick = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p instanceof FakePlayer) continue;
            if ((tick + (p.getId() & 3)) % 5 != 0) continue;
            S s = STATES.get(p.getUUID());
            boolean bard = Bard.isBard(p);
            if (!bard) {
                if (s != null) {
                    STATES.remove(p.getUUID());
                    clearModifiers(p);
                    StatEngine.recompute(p);
                    Net.toPlayer(p, new Packets.BardSync(0, 0, 0, false));
                }
                continue;
            }
            if (s == null) s = STATES.computeIfAbsent(p.getUUID(), k -> new S());
            long now = p.level().getGameTime();
            if (Bard.instrumentHand(p) != null) {
                s.lastHolding = now;
            } else if (now - s.lastHolding > AbilityService.ticks(Bard.params(Bard.RHYTHM), "idle_reset_seconds", 3) && (s.combo > 0 || s.stage > 0)) {
                s.combo = 0;
                s.stage = 0;
                s.dirty = true;
            }
            if (tick % 20 == 0) s.groupBonus = groupBonus(p);
            // Rotación de conjuros: si el elegido ya no se puede tocar (recarga, o el combo bajó), pasa al siguiente.
            if (Bard.magic != null && Bard.instrumentHand(p) != null) Bard.magic.rotateSpell(p, false);
            update(p, s, now);
        }
    }

    /** Recalcula los modificadores, la velocidad y, si algo ha cambiado, se lo manda al cliente. */
    private static void update(ServerPlayer p, S s, long now) {
        applyModifiers(p, s);
        String key = speedKey(p, s);
        if (!key.equals(s.speedKey)) {
            s.speedKey = key;
            StatEngine.recompute(p);
        }
        if (s.dirty) {
            s.dirty = false;
            int cres = (int) Math.max(0, s.crescendoUntil - now);
            Net.toPlayer(p, new Packets.BardSync(s.combo, s.stage, cres, true));
        }
    }

    // ------------------------------------------------------------------ poder musical e instrumento

    /** Poder musical por acierto según el Nivel Total (1 %, 2 % desde el 15, 3 % desde el 30). */
    public static double powerPerHit(ServerPlayer p) {
        PlayerClassData data = PlayerClassDataProvider.get(p);
        int level = data == null ? 0 : data.state.totalLevel();
        if (level >= 30) return Bard.number(Bard.RHYTHM, "power_per_hit_30", 0.03);
        if (level >= 15) return Bard.number(Bard.RHYTHM, "power_per_hit_15", 0.02);
        return Bard.number(Bard.RHYTHM, "power_per_hit", 0.01);
    }

    /** Bonus de poder musical que da el mod (combo, Compás, instrumento y perks), con el tope global. */
    public static double musicBonus(ServerPlayer p) {
        S s = STATES.get(p.getUUID());
        int combo = s == null ? 0 : s.combo;
        Instrument instrument = Bard.instrument(p);
        double bonus = combo * powerPerHit(p)
                + Bard.mech(p, Bard.MUSIC_POWER, "music_bonus", 0)
                + (instrument == null ? 0 : instrument.musicPower())
                + (s == null ? 0 : s.groupBonus)
                + Bard.mech(p, Bard.TEMPO_LARGO, "power_penalty", 0);
        return Math.min(bonus, Bard.number(Bard.RHYTHM, "music_power_cap", 1.5));
    }

    /** Solista y Coro: según los aliados cercanos (se recalcula cada segundo). */
    private static double groupBonus(ServerPlayer p) {
        boolean soloist = Bard.has(p, Bard.SOLOIST), chorus = Bard.has(p, Bard.CHORUS);
        if (!soloist && !chorus) return 0;
        String id = soloist ? Bard.SOLOIST : Bard.CHORUS;
        double r = Bard.mech(p, id, "radius", soloist ? 12 : 9);
        int allies = 0;
        for (ServerPlayer other : p.serverLevel().players()) {
            if (other != p && other.isAlive() && !other.isSpectator() && other.distanceToSqr(p) <= r * r && Allies.isAlly(p, other)) allies++;
        }
        if (soloist) return allies == 0 ? Bard.mech(p, id, "alone_bonus", 0.2) : Bard.mech(p, id, "crowd_penalty", -0.1);
        if (allies == 0) return Bard.mech(p, id, "alone_penalty", -0.12);
        return Bard.mech(p, id, "per_ally", 0.08) * Math.min(allies, (int) Bard.mech(p, id, "chorus_cap", 4));
    }

    private static void applyModifiers(ServerPlayer p, S s) {
        Instrument instrument = Bard.instrument(p);
        set(p, ModAttributes.MUSIC_SPELL_POWER.get(), "music_power", AttributeModifier.Operation.ADDITION, musicBonus(p));
        set(p, attr(CAST_TIME), "cast_time", AttributeModifier.Operation.MULTIPLY_BASE, instrument == null ? 0 : instrument.castSpeed());
        set(p, attr(MANA_REGEN), "mana_regen", AttributeModifier.Operation.MULTIPLY_BASE, instrument == null ? 0 : instrument.manaRegen());
        set(p, attr(COOLDOWN), "cooldown", AttributeModifier.Operation.MULTIPLY_BASE, instrument == null ? 0 : instrument.cooldownReduction());
        set(p, attr(MAX_MANA), "max_mana", AttributeModifier.Operation.ADDITION, instrument == null ? 0 : instrument.maxMana());
    }

    private static void clearModifiers(ServerPlayer p) {
        set(p, ModAttributes.MUSIC_SPELL_POWER.get(), "music_power", AttributeModifier.Operation.ADDITION, 0);
        set(p, attr(CAST_TIME), "cast_time", AttributeModifier.Operation.MULTIPLY_BASE, 0);
        set(p, attr(MANA_REGEN), "mana_regen", AttributeModifier.Operation.MULTIPLY_BASE, 0);
        set(p, attr(COOLDOWN), "cooldown", AttributeModifier.Operation.MULTIPLY_BASE, 0);
        set(p, attr(MAX_MANA), "max_mana", AttributeModifier.Operation.ADDITION, 0);
    }

    private static Attribute attr(ResourceLocation id) {
        return ForgeRegistries.ATTRIBUTES.getValue(id);
    }

    private static void set(ServerPlayer p, Attribute attribute, String source, AttributeModifier.Operation op, double amount) {
        if (attribute == null) return;
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        String name = MOD_PREFIX + source;
        UUID id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        AttributeModifier current = inst.getModifier(id);
        if (amount == 0 || !Double.isFinite(amount)) {
            if (current != null) inst.removeModifier(id);
            return;
        }
        if (current != null && Math.abs(current.getAmount() - amount) < 1e-6) return;
        inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }

    // ------------------------------------------------------------------ velocidad (cuenta para el tope global)

    /** Bonus de velocidad de Danza que tendría ahora (0 si no suena ninguna canción suya). */
    public static double danceBonus(ServerPlayer p) {
        if (!BardAuras.hasActive(p)) return 0;
        double bonus = Bard.mech(p, Bard.DANCE, "speed_bonus", 0);
        if (Bard.has(p, Bard.PERFECT_MARCH)) {
            bonus = stage(p) >= (int) Bard.mech(p, Bard.PERFECT_MARCH, "min_stage", 1) ? Bard.mech(p, Bard.PERFECT_MARCH, "speed_bonus", 0.25) : 0;
        }
        return bonus;
    }

    private static String speedKey(ServerPlayer p, S s) {
        return playing(p) + "|" + Math.round(danceBonus(p) * 1000) + "|" + Bard.has(p, Bard.DANCE);
    }

    /**
     * Velocidad del Bardo para {@link StatEngine}: tocar ralentiza un poco salvo con Danza, y Danza acelera mientras
     * suena una canción (Paso Compartido reduce tu parte). Entra antes de los topes globales (regla 7).
     */
    public static Map<StatCalculator.Key, Double> extra(ServerPlayer p) {
        if (!STATES.containsKey(p.getUUID())) return Map.of();
        double speed = 0;
        if (playing(p) && !Bard.has(p, Bard.DANCE)) speed += Bard.number(Bard.RHYTHM, "play_slow", -0.1);
        double dance = danceBonus(p);
        if (dance > 0 && Bard.has(p, Bard.SHARED_STEP)) dance *= 1 + Bard.mech(p, Bard.SHARED_STEP, "self_penalty", -0.3);
        speed += dance;
        if (speed == 0) return Map.of();
        Map<StatCalculator.Key, Double> out = new HashMap<>();
        out.put(new StatCalculator.Key(StatCalculator.MOVEMENT_SPEED, "multiply_base"), speed);
        return out;
    }

    // ------------------------------------------------------------------ pasivas de instrumento

    private static void colossusBoom(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.PLAYERS, 2f, 0.5f);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.4f, 1.6f);
        level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.5, p.getZ(), 1, 0, 0, 0, 0);
        double r = 4;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r))) {
            if (e.distanceToSqr(p) > r * r || !Allies.isEnemy(p, e) || Races.isBoss(e)) continue;
            CombatAbilities.stun(e, 20);
        }
    }

    private static void sunHeal(ServerPlayer p) {
        double r = 8;
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r))) {
            if (!e.isAlive() || e.distanceToSqr(p) > r * r || !Allies.isAlly(p, e)) continue;
            if (e.getHealth() >= e.getMaxHealth()) continue;
            e.heal(e == p ? 0.25f : 0.5f);
            p.serverLevel().sendParticles(ParticleTypes.WAX_OFF, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 2, 0.3, 0.3, 0.3, 0);
        }
    }

    static void particles(ServerPlayer p, net.minecraft.core.particles.ParticleOptions type, int count) {
        p.serverLevel().sendParticles(type, p.getX(), p.getY() + 1.6, p.getZ(), count, 0.6, 0.4, 0.6, 0.5);
    }

    // ------------------------------------------------------------------ ciclo de vida

    private static void reset(ServerPlayer p) {
        S s = STATES.get(p.getUUID());
        if (s == null) return;
        s.combo = 0;
        s.stage = 0;
        s.consecutive = 0;
        s.crescendoUntil = 0;
        s.onBeatUntil = 0;
        s.resonantPending = false;
        s.dirty = true;
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            reset(p);
            DebugLog.event(p, "bardo", "muere: combo y Armonía a 0");
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
        BardPlay.forget(event.getEntity().getUUID());
    }
}
