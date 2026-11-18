package io.github.rpgclasses.bard;

import com.google.gson.JsonObject;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.MechanicService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Ids y utilidades comunes del Bardo (adenda del Bardo). */
public final class Bard {
    public static final String RHYTHM = "rites_and_paths:rhythm";
    public static final String HARMONY = "rites_and_paths:harmony";
    public static final String COUNTERPOINT = "rites_and_paths:counterpoint";
    public static final String CRESCENDO = "rites_and_paths:crescendo";
    public static final String RHYTHMIC_FURY = "rites_and_paths:rhythmic_fury";
    public static final String BEWITCH = "rites_and_paths:bewitch";
    public static final String RECITAL = "rites_and_paths:recital";

    // Mecánicas (ramas, perks y subclases).
    public static final String MUSIC_POWER = "music_power";
    public static final String AURA_RADIUS = "aura_radius";
    public static final String TIMING_WINDOW = "timing_window";
    public static final String AURA_LINGER = "aura_linger";
    public static final String DANCE = "bard_dance";
    public static final String SOLOIST = "soloist";
    public static final String CHORUS = "chorus";
    public static final String AURA_SHAPE = "aura_shape";
    public static final String TEMPO_PRESTO = "tempo_presto";
    public static final String TEMPO_LARGO = "tempo_largo";
    public static final String TRAIL = "trail";
    public static final String LAST_NOTE = "last_note";
    public static final String SHARED_STEP = "shared_step";
    public static final String PERFECT_MARCH = "perfect_march";
    public static final String MARTIAL_COMBO = "martial_combo";
    public static final String RESONANT_STRIKE = "resonant_strike";
    public static final String HYPNOTIC_VOICE = "hypnotic_voice";
    public static final String CONTAGION = "dance_contagion";
    public static final String EXTENDED_REPERTOIRE = "extended_repertoire";
    public static final String FINE_EAR = "fine_ear";

    /** Carriles del minijuego: clic izquierdo, clic derecho, Shift y espacio. */
    public static final int LANE_ATTACK = 0, LANE_USE = 1, LANE_SNEAK = 2, LANE_JUMP = 3;

    /** Puente con Iron's Spells (null sin Iron's: el Bardo toca, pero no tiene conjuros). */
    public static BardMagic magic;

    /** En el cliente: ¿el jugador local es Bardo? y sus ranuras extra (los pone ClientSetup). */
    public static Predicate<Player> clientIsBard = p -> false;
    public static ToIntFunction<Player> clientExtraSlots = p -> 0;

    private Bard() {}

    /** Id de la clase Bardo. */
    public static final String CLASS = "rites_and_paths:bard";

    /**
     * ¿Es Bardo? Basta con tener la clase elegida: Ritmo se desbloquea con el primer punto, pero con 0 puntos ya se
     * puede tocar (antes el instrumento decía «solo un Bardo» hasta gastar un punto).
     */
    public static boolean isBard(Player player) {
        if (player instanceof ServerPlayer sp) {
            PlayerClassData data = PlayerClassDataProvider.get(sp);
            return data != null && CLASS.equals(data.state.classId);
        }
        return player != null && player.level().isClientSide && clientIsBard.test(player);
    }

    /** Parámetros de una habilidad del Bardo (vacío si no existe). */
    public static JsonObject params(String ability) {
        return AbilityService.params(ability);
    }

    public static double number(String ability, String key, double fallback) {
        return AbilityService.number(params(ability), key, fallback);
    }

    /**
     * Mano con el instrumento que se toca: la guitarra-hacha o un instrumento en la mano principal, si no un
     * instrumento en la secundaria. Con la guitarra-hacha en la mano principal, la secundaria no cuenta (dos manos).
     */
    public static InteractionHand instrumentHand(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof GuitarAxeItem || main.getItem() instanceof InstrumentItem) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().getItem() instanceof InstrumentItem) return InteractionHand.OFF_HAND;
        return null;
    }

    public static ItemStack instrumentStack(Player player) {
        InteractionHand hand = instrumentHand(player);
        return hand == null ? ItemStack.EMPTY : player.getItemInHand(hand);
    }

    /** El instrumento que toca el jugador, o null. */
    public static Instrument instrument(Player player) {
        return BardItems.instrumentOf(instrumentStack(player));
    }

    /** Ranuras de partitura de ese instrumento para ese jugador (base + Repertorio Ampliado del Juglar). */
    public static int capacity(Player player, Instrument instrument) {
        if (instrument == null) return 0;
        int extra = player instanceof ServerPlayer sp
                ? (int) Math.round(MechanicService.number(sp, EXTENDED_REPERTOIRE, "extra_slots", 0))
                : clientExtraSlots.applyAsInt(player);
        return Math.max(0, Math.min(Instrument.MAX_SLOTS, instrument.slots() + extra));
    }

    public static double mech(ServerPlayer player, String id, String key, double fallback) {
        return MechanicService.number(player, id, key, fallback);
    }

    public static boolean has(ServerPlayer player, String mechanic) {
        return MechanicService.has(player, mechanic);
    }
}
