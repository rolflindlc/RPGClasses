package io.github.rpgclasses.compat.epicfight;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.content.RoguePerks;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.server.DebugLog;
import io.github.rpgclasses.server.MechanicService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import yesman.epicfight.api.animation.types.DodgeAnimation;
import yesman.epicfight.api.utils.AttackResult;
import yesman.epicfight.skill.SkillCategories;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.entity.eventlistener.PlayerEventListener.EventType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Puente con Epic Fight (solo se carga si el mod está instalado). Rama Acrobacia del Pícaro y sus perks:
 * <ul>
 *   <li><b>Acrobacia:</b> la esquiva gasta menos estamina ({@code stamina_cost}) y su invulnerabilidad dura más
 *       ({@code dodge_invulnerability}).</li>
 *   <li><b>Contraataque:</b> tras una esquiva con éxito, el siguiente golpe en {@code window_seconds} hace +{@code damage_bonus}.</li>
 *   <li><b>Voltereta Larga:</b> la esquiva recorre +{@code dodge_distance} y su invulnerabilidad dura menos.</li>
 * </ul>
 * En Epic Fight 20.x la esquiva es invulnerable durante toda su animación. La duración se cambia así:
 * si el factor total es mayor que 1, al acabar la animación queda una ventana extra en la que los golpes esquivables
 * fallan (y cuentan como esquiva con éxito); si es menor que 1, los golpes del último tramo de la animación entran.
 * La distancia de la esquiva escala con la velocidad de movimiento (propiedad AFFECT_SPEED de la animación), así que
 * Voltereta Larga suma un modificador temporal de velocidad mientras dura la esquiva, en cliente y en servidor.
 */
public final class EpicFightCompat {
    public static final String ACROBATICS = "acrobatics";
    public static final String DODGE_COUNTER = "dodge_counter";
    public static final String LONG_ROLL = "long_roll";

    private static final UUID LISTENER = UUID.fromString("6b0d3c9e-6a63-4f1e-9c55-2f6d0f7a1a01");
    private static final UUID DISTANCE_MODIFIER = UUID.fromString("6b0d3c9e-6a63-4f1e-9c55-2f6d0f7a1a02");
    private static final String DISTANCE_NAME = "rpgclasses_dodge_distance";

    /** Mecánica del jugador local en el cliente (la pone ClientSetup). En servidor se usa MechanicService. */
    public static BiFunction<Player, String, ActiveMechanic> clientMechanic = (player, id) -> null;

    /** Esquiva en curso en el servidor: tick de inicio y duración en ticks. */
    private record Dodge(long start, int duration, double factor) {}
    private static final Map<UUID, Dodge> DODGES = new HashMap<>();
    /** Fin (tick de juego) de la invulnerabilidad extra tras la esquiva. */
    private static final Map<UUID, Long> EXTRA_UNTIL = new HashMap<>();

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new EpicFightCompat());
        RpgClasses.LOGGER.info("[rpgclasses] Epic Fight detectado: Acrobacia, Contraataque y Voltereta Larga activos");
    }

    static ActiveMechanic mechanic(Player player, String id) {
        if (player instanceof ServerPlayer sp) return sp instanceof FakePlayer ? null : MechanicService.find(sp, id);
        return player.level().isClientSide() ? clientMechanic.apply(player, RpgClasses.MODID + ":" + id) : null;
    }

    /** Factor total de duración de la invulnerabilidad de la esquiva (1 = sin cambios). */
    static double invulnerabilityFactor(Player player) {
        ActiveMechanic acro = mechanic(player, ACROBATICS);
        ActiveMechanic roll = mechanic(player, LONG_ROLL);
        double f = (1 + (acro == null ? 0 : acro.number("dodge_invulnerability", 0)))
                * (1 + (roll == null ? 0 : roll.number("dodge_invulnerability", 0)));
        return Math.max(0, f);
    }

    // ------------------------------------------------------------------ alta de los oyentes de Epic Fight

    /** Epic Fight crea un PlayerPatch nuevo al entrar, reaparecer o cambiar de dimensión: se registra cada vez. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Player player) || player instanceof FakePlayer) return;
        PlayerPatch<?> patch = EpicFightCapabilities.getEntityPatch(player, PlayerPatch.class);
        if (patch == null) return;
        DebugLog.guard(player, "Epic Fight: alta de oyentes", () -> register(player, patch));
    }

    private static void register(Player player, PlayerPatch<?> patch) {
        var listener = patch.getEventListener();

        // Acrobacia: menos estamina por esquiva (cliente y servidor, para que la comprobación previa coincida).
        listener.addEventListener(EventType.SKILL_CONSUME_EVENT, LISTENER, event -> {
            if (event.getSkill().getCategory() != SkillCategories.DODGE) return;
            ActiveMechanic acro = mechanic(player, ACROBATICS);
            if (acro == null) return;
            float before = event.getAmount();
            event.setAmount((float) Math.max(0, before * (1 + acro.number("stamina_cost", 0))));
            if (!player.level().isClientSide()) {
                DebugLog.event(player, "Acrobacia", "estamina de la esquiva {} → {}", before, event.getAmount());
            }
        });

        listener.addEventListener(EventType.ANIMATION_BEGIN_EVENT, LISTENER, event -> {
            if (!(event.getAnimation() instanceof DodgeAnimation dodge)) return;
            ActiveMechanic roll = mechanic(player, LONG_ROLL);
            setDistanceModifier(player, roll == null ? 0 : roll.number("dodge_distance", 0));
            if (player instanceof ServerPlayer sp) {
                int duration = Math.max(1, Math.round(dodge.getTotalTime() / 0.05F));
                double factor = invulnerabilityFactor(player);
                DODGES.put(sp.getUUID(), new Dodge(sp.level().getGameTime(), duration, factor));
                EXTRA_UNTIL.remove(sp.getUUID());
                DebugLog.event(sp, "esquiva", "empieza ({} ticks); distancia +{}%, invulnerabilidad x{}", duration,
                        roll == null ? 0 : roll.number("dodge_distance", 0) * 100, factor);
            }
        });

        listener.addEventListener(EventType.ANIMATION_END_EVENT, LISTENER, event -> {
            if (!(event.getAnimation() instanceof DodgeAnimation)) return;
            setDistanceModifier(player, 0);
            if (player instanceof ServerPlayer sp) {
                Dodge dodge = DODGES.remove(sp.getUUID());
                if (dodge != null && dodge.factor() > 1) {
                    int extra = (int) Math.round(dodge.duration() * (dodge.factor() - 1));
                    EXTRA_UNTIL.put(sp.getUUID(), sp.level().getGameTime() + extra);
                    DebugLog.event(sp, "Acrobacia", "invulnerabilidad extra tras la esquiva: {} ticks", extra);
                }
            }
        });

        // Contraataque: tras esquivar con éxito, el siguiente golpe hace más daño.
        listener.addEventListener(EventType.DODGE_SUCCESS_EVENT, LISTENER, event -> {
            ServerPlayer sp = event.getPlayerPatch().getOriginal();
            if (cutZone(sp)) return; // Voltereta Larga: ese golpe entra, no cuenta como esquiva
            DebugLog.event(sp, "esquiva", "con éxito contra {} de {}", event.getDamageSource().getMsgId(),
                    DebugLog.describe(event.getDamageSource().getEntity()));
            io.github.rpgclasses.content.MonkCharges.onDodge(sp); // Agua: +3 cargas
            ActiveMechanic counter = mechanic(sp, DODGE_COUNTER);
            if (counter == null) return;
            int window = (int) Math.round(counter.number("window_seconds", 3) * 20);
            RoguePerks.armDodgeCounter(sp, counter.number("damage_bonus", 0.3), window);
        });
    }

    /** Voltereta Larga: Epic Fight escala la distancia de la esquiva con velocidad actual / velocidad base. */
    private static void setDistanceModifier(Player player, double bonus) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(DISTANCE_MODIFIER);
        if (bonus != 0) {
            speed.addTransientModifier(new AttributeModifier(DISTANCE_MODIFIER, DISTANCE_NAME, bonus, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    // ------------------------------------------------------------------ duración de la invulnerabilidad

    private static boolean dodgeable(DamageSource source) {
        return DodgeAnimation.DODGEABLE_SOURCE_VALIDATOR.apply(source) == AttackResult.ResultType.MISSED;
    }

    /** Acrobacia (factor > 1): ventana extra tras la esquiva en la que los golpes esquivables fallan. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttackExtended(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) return;
        Long until = EXTRA_UNTIL.get(player.getUUID());
        if (until == null) return;
        if (player.level().getGameTime() > until) {
            EXTRA_UNTIL.remove(player.getUUID());
            return;
        }
        if (!dodgeable(event.getSource())) return;
        event.setCanceled(true);
        DebugLog.event(player, "Acrobacia", "golpe evitado en la invulnerabilidad extra ({})", event.getSource().getMsgId());
        ServerPlayerPatch patch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
        if (patch != null) patch.onDodgeSuccess(event.getSource(), player.position());
    }

    /**
     * Voltereta Larga (factor &lt; 1): en el último tramo de la animación Epic Fight sigue anulando el golpe;
     * aquí se deja pasar. Solo se reabre lo que Epic Fight habría esquivado.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onAttackShortened(LivingAttackEvent event) {
        if (!event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (cutZone(player) && dodgeable(event.getSource())) {
            event.setCanceled(false);
            DebugLog.event(player, "Voltereta Larga", "golpe al final de la esquiva: entra ({})", event.getSource().getMsgId());
        }
    }

    /** True si el jugador está en el tramo final de la esquiva que su factor de invulnerabilidad recorta. */
    public static boolean cutZone(ServerPlayer player) {
        Dodge dodge = DODGES.get(player.getUUID());
        if (dodge == null || dodge.factor() >= 1) return false;
        long elapsed = player.level().getGameTime() - dodge.start();
        return elapsed >= dodge.duration() * dodge.factor();
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        DODGES.remove(event.getEntity().getUUID());
        EXTRA_UNTIL.remove(event.getEntity().getUUID());
    }

    private EpicFightCompat() {}
}
