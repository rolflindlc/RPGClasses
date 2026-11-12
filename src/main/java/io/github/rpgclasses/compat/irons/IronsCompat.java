package io.github.rpgclasses.compat.irons;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.content.RogueAbilities;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.registry.ModEffects;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.MechanicService;
import io.github.rpgclasses.server.StatEngine;
import io.redspace.ironsspellbooks.api.events.ModifySpellLevelEvent;
import io.redspace.ironsspellbooks.api.events.SetSummonOwnerEvent;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.rpgclasses.core.definition.Definitions.KitItem;
import io.github.rpgclasses.core.definition.Definitions.KitSpell;
import io.github.rpgclasses.server.KitService;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.IPresetSpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainerMutable;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Puente con Iron's Spells 'n Spellbooks. Solo se carga si Iron's está instalado ({@link #init()}), así que es la
 * única clase que toca su API. Lo que no está en la API pública (dueño de invocaciones, fuente de daño de
 * conjuro) se usa por reflexión y falla en silencio si cambia.
 *
 * <ul>
 *   <li>Regla 14: anula los conjuros de escuelas con muro de −100 % (sagrada salvo Paladín).</li>
 *   <li>Mago: Convocación (summon_bonus), Legión/Élite (summon_count), Paso Arcano (cast_speed_burst),
 *       Poder a Distancia/Quemarropa (distance_power) y Magia de Sangre (blood_magic).</li>
 *   <li>Pícaro: lanzar desde Sigilo gasta barra; Hoja Arcana: Emboscada Arcana (arcane_ambush).</li>
 *   <li>Mago: Conjuros Gratuitos por Cargas ({@link FreeCasts}), kit con libro, Escudo Arcano y
 *       Concentración Inquebrantable (Mago de Batalla).</li>
 *   <li>Evocador Caótico: Magia Salvaje (wild_magic). Elementalista: {@link ElementalDebuffs}.</li>
 * </ul>
 * Fluidez (cast_no_slow) no necesita eventos: es un modificador de casting_movespeed en {@link StatEngine}.
 */
public final class IronsCompat {
    public static final String IRONS = "irons_spellbooks";

    private static final String CD_ARCANE_STEP = "rpgclasses:arcane_step";
    private static final String CD_AMBUSH = "rpgclasses:arcane_ambush";
    private static final String CD_WILD = "rpgclasses:wild_magic";
    /** Entidades creadas por una réplica de Magia Salvaje: guardan el multiplicador de daño. */
    private static final String TAG_REPLICA = "rpgclasses:replica";
    /**
     * Conjuros ofensivos que no muestran "Daño" en su descripción (control, área, zonas). El resto se detecta
     * por la línea de daño de {@link AbstractSpell#getUniqueInfo}.
     */
    private static final Set<String> EXTRA_OFFENSIVE = Set.of(
            "irons_spellbooks:wall_of_fire", "irons_spellbooks:heat_surge", "irons_spellbooks:ice_tomb",
            "irons_spellbooks:frostwave", "irons_spellbooks:frostbite", "irons_spellbooks:snowball",
            "irons_spellbooks:blizzard", "irons_spellbooks:earthquake", "irons_spellbooks:acid_orb",
            "irons_spellbooks:blight", "irons_spellbooks:root", "irons_spellbooks:heartstop",
            "irons_spellbooks:black_hole", "irons_spellbooks:gravity_fissure", "irons_spellbooks:slow",
            "irons_spellbooks:arcane_shackle");
    private static final String TAG_SUMMON_DONE = "rpgclasses:summon_done";
    private static final String TAG_SUMMON_DAMAGE = "rpgclasses:summon_damage";
    private static final String TAG_CLONE_OF = "rpgclasses:clone_of";
    private static final UUID SUMMON_HEALTH_ID = UUID.fromString("6f0c3b1e-2a4d-4c7e-8b59-1d3e5f7a9c20");
    /** Tope de vida de una invocación extra de Legión si se pierde el vínculo con la original (10 min). */
    private static final int CLONE_MAX_TICKS = 20 * 60 * 10;

    private static final String SUMMON_MANAGER = "io.redspace.ironsspellbooks.capabilities.magic.SummonManager";
    /** Métodos fuera de la API de Iron's: se buscan la primera vez que hacen falta (no al arrancar). */
    private static final Map<String, Method> METHODS = new HashMap<>();

    private static long tick;
    /** Magia de Sangre: maná prestado este tick a cambio de vida; se devuelve si el conjuro no llega a empezar. */
    private static final Map<UUID, Loan> LOANS = new HashMap<>();
    /** Jugadores con Legión/Élite que han lanzado un conjuro este tick, para ajustar sus invocaciones nuevas. */
    private static final Map<UUID, Long> SUMMON_CASTS = new HashMap<>();
    /** Invocaciones que acaban de recibir dueño, pendientes de procesar al final del tick (tras initSummon). */
    private static final List<Entity[]> NEW_SUMMONS = new ArrayList<>();
    /** Conjuro Gratuito aceptado por Iron's, pendiente de lanzarse (se cobra la carga al lanzar). */
    private static final Map<UUID, FreeCast> FREE_PENDING = new HashMap<>();
    /** Maná de Magia de Sangre del conjuro en curso: no cuenta como gastado (Escudo Arcano, Magia Salvaje). */
    private static final Map<UUID, Float> BLOOD_MANA = new HashMap<>();
    /** Escudo Arcano: absorción propia de cada jugador (aparte de la de manzanas, etc.). */
    private static final Map<UUID, Shield> SHIELDS = new HashMap<>();
    /** Emboscada Arcana preparada: conjuro y tick de caducidad. */
    private static final Map<UUID, Ambush> AMBUSHES = new HashMap<>();
    /** Invocaciones extra de Legión → su original (desaparecen con ella). */
    private static final Map<UUID, Clone> CLONES = new HashMap<>();
    /** Magia Salvaje que ha saltado este tick: se lanza al final del tick, después del conjuro original. */
    private static final List<WildProc> WILD_PENDING = new ArrayList<>();
    /** Réplica en curso (jugador y multiplicador de daño); null fuera de ella. Las réplicas no encadenan. */
    private static UUID replicating;
    private static double replicaFactor = 1;

    /** Maná adelantado para pasar la comprobación de Iron's: con vida (Magia de Sangre) o por una carga gratuita (slot > 0). */
    private record Loan(long tick, String spellId, float mana, float health, int slot) {}
    private record FreeCast(String spellId, int slot, long expires) {}
    private static final class Shield { float own; long lastSpend; }
    private record Ambush(String spellId, long expires) {}
    private record Clone(ServerLevel level, UUID original) {}
    private record WildProc(UUID player, String castSpellId) {}

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new IronsCompat());
        KitService.spellInscriber = IronsCompat::inscribe;
        MinecraftForge.EVENT_BUS.register(new RacialSpells());
        RpgClasses.LOGGER.info("[rpgclasses] Iron's Spells detectado: puente activado");
    }

    // ---------------------------------------------------------------- regla 14 + confirmación de préstamos

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPreCast(SpellPreCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (io.github.rpgclasses.content.DeathGamble.flipping(player)) { // Apuesta con la Muerte: no puede hacer nada
            event.setCanceled(true);
            return;
        }
        String school = event.getSchoolType().getId().toString();
        if (StatEngine.schoolWall(player, school) >= 1.0) {
            io.github.rpgclasses.server.DebugLog.event(player, "conjuro", "bloqueado: la escuela {} está vetada para su clase", school);
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("message.rpgclasses.school_locked",
                    event.getSchoolType().getDisplayName()), true);
        }
    }

    /** Si el conjuro empieza (y gasta maná), el préstamo de Magia de Sangre queda pagado; si no, se devuelve. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onPreCastResolved(SpellPreCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Loan loan = LOANS.remove(player.getUUID());
        if (loan == null) return;
        boolean starts = !event.isCanceled() && event.getCastSource().consumesMana();
        if (loan.slot() > 0) {
            // La carga solo necesitaba pasar la comprobación: el maná se retira ya y el lanzamiento costará 0.
            refund(player, loan);
            if (starts) FREE_PENDING.put(player.getUUID(), new FreeCast(loan.spellId(), loan.slot(), tick + 20 * 30));
        } else if (!starts) {
            refund(player, loan);
        } else {
            BLOOD_MANA.put(player.getUUID(), loan.mana());
        }
    }

    // ---------------------------------------------------------------- Magia de Sangre

    /**
     * Iron's comprueba el maná antes de cualquier evento cancelable, pero calcula el nivel del conjuro
     * (ModifySpellLevelEvent) justo antes de intentar lanzarlo: ahí se adelanta el maná que falta pagando vida.
     * Iron's también calcula niveles de otros conjuros justo antes (p. ej. al montar la selección), así que
     * manda la última consulta del tick: si cambia de conjuro, el préstamo anterior se devuelve y se rehace.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onSpellLevel(ModifySpellLevelEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative()) return;
        ActiveMechanic blood = MechanicService.find(player, "blood_magic");
        if (blood == null && !hasFreeSlots(player)) return;
        String spellId = event.getSpell().getSpellId();
        Loan previous = LOANS.get(player.getUUID());
        if (previous != null) {
            if (previous.spellId().equals(spellId)) return;
            LOANS.remove(player.getUUID());
            refund(player, previous);
        }
        MagicData magic = MagicData.getPlayerMagicData(player);
        if (magic == null || magic.isCasting()) return;
        float cost = event.getSpell().getManaCost(event.getLevel());
        float missing = cost - magic.getMana();
        int slot = FreeCasts.usableSlot(player, spellId, cost);
        if (slot > 0) {
            float lent = Math.max(0, missing);
            magic.setMana(magic.getMana() + lent);
            LOANS.put(player.getUUID(), new Loan(tick, spellId, lent, 0, slot));
            return;
        }
        if (blood == null || missing <= 0) return;
        float health = (float) (missing / blood.number("mana_per_health", 15));
        float floor = (float) (player.getMaxHealth() * blood.number("min_health_fraction", 0.25));
        if (player.getHealth() - health < floor) return;
        player.setHealth(player.getHealth() - health);
        magic.setMana(magic.getMana() + missing);
        io.github.rpgclasses.server.DebugLog.event(player, "Magia de Sangre", "{}: faltaban {} de maná, paga {} de vida",
                spellId, missing, health);
        LOANS.put(player.getUUID(), new Loan(tick, spellId, missing, health, 0));
    }

    /**
     * Magia de Sangre con conjuros continuos (Aliento de Dragón...): Iron's cobra cada 10 ticks y corta el conjuro
     * si el maná no llega al doble del coste. Justo antes de ese cobro se paga con vida lo que falte.
     */
    private static void bloodForContinuous(ServerPlayer player) {
        if (player.isCreative()) return;
        MagicData magic = MagicData.getPlayerMagicData(player);
        if (magic == null || !magic.isCasting() || magic.getCastType() != CastType.CONTINUOUS || !magic.getCastSource().consumesMana()) return;
        int remaining = magic.getCastDurationRemaining();
        if (remaining <= 0 || remaining % 10 != 0) return;
        ActiveMechanic blood = MechanicService.find(player, "blood_magic");
        if (blood == null) return;
        AbstractSpell spell = SpellRegistry.getSpell(magic.getCastingSpellId());
        if (spell == null) return;
        float missing = spell.getManaCost(magic.getCastingSpellLevel()) * 2 - magic.getMana();
        if (missing <= 0) return;
        float health = (float) (missing / blood.number("mana_per_health", 15));
        float floor = (float) (player.getMaxHealth() * blood.number("min_health_fraction", 0.25));
        if (player.getHealth() - health < floor) return;
        player.setHealth(player.getHealth() - health);
        magic.setMana(magic.getMana() + missing);
    }

    private static void refund(ServerPlayer player, Loan loan) {
        MagicData magic = MagicData.getPlayerMagicData(player);
        if (magic != null) magic.setMana(Math.max(0, magic.getMana() - loan.mana()));
        if (player.isAlive()) player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + loan.health()));
    }

    // ---------------------------------------------------------------- al lanzar

    @SubscribeEvent
    public void onCast(SpellOnCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String spellId = event.getSpellId();

        // Élite: los conjuros de invocar cuestan más maná.
        ActiveMechanic count = MechanicService.find(player, "summon_count");
        if (count != null) {
            SUMMON_CASTS.put(player.getUUID(), tick);
            if (isSummonSpell(spellId)) {
                io.github.rpgclasses.server.DebugLog.event(player, "Legión/Élite", "{} lanza {} (tick {})", player.getGameProfile().getName(), spellId, tick);
            }
            double extra = count.number("summon_mana_cost", 0);
            if (extra != 0 && isSummonSpell(spellId)) {
                event.setManaCost((int) Math.round(event.getManaCost() * (1 + extra)));
            }
        }

        // Paso Arcano: velocidad tras lanzar (ICD).
        ActiveMechanic step = MechanicService.find(player, "cast_speed_burst");
        if (step != null && AbilityService.remainingTicks(player, CD_ARCANE_STEP) <= 0) {
            player.addEffect(new MobEffectInstance(ModEffects.ARCANE_STEP.get(),
                    (int) Math.round(step.number("duration_seconds", 2) * 20), 0, false, false, true));
            AbilityService.setCooldown(player, CD_ARCANE_STEP, (int) Math.round(step.number("icd_seconds", 4) * 20));
        }

        // Sigilo: lanzar gasta barra como un ataque; con Emboscada Arcana lista, gasta su coste y prepara el bono.
        if (RogueAbilities.isStealthed(player)) {
            ActiveMechanic ambush = MechanicService.find(player, "arcane_ambush");
            if (ambush != null && AbilityService.remainingTicks(player, CD_AMBUSH) <= 0) {
                AMBUSHES.put(player.getUUID(), new Ambush(spellId, tick + 20 * 5));
                AbilityService.setCooldown(player, CD_AMBUSH, (int) Math.round(ambush.number("icd_seconds", 8) * 20));
                RogueAbilities.spend(player, ambush.number("stealth_cost", 0.4));
            } else {
                RogueAbilities.spend(player, AbilityService.number(AbilityService.params(RogueAbilities.STEALTH), "action_cost", 0.4));
            }
        }
    }

    /** Al final: Conjuro Gratuito (coste 0 y gasta carga) y maná realmente gastado para el Escudo Arcano. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCastResolved(SpellOnCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        FreeCast free = FREE_PENDING.remove(player.getUUID());
        if (free != null && free.spellId().equals(event.getSpellId()) && event.getCastSource().consumesMana()) {
            event.setManaCost(0);
            FreeCasts.consume(player, free.slot());
        }
        float blood = BLOOD_MANA.getOrDefault(player.getUUID(), 0f);
        BLOOD_MANA.remove(player.getUUID());
        io.github.rpgclasses.server.DebugLog.event(player, "conjuro", "{} lanzado; coste final {} maná, fuente {}{}{}",
                event.getSpellId(), event.getManaCost(), event.getCastSource(),
                free != null && free.spellId().equals(event.getSpellId()) ? ", Conjuro Gratuito (ranura " + free.slot() + ")" : "",
                blood > 0 ? ", " + blood + " de maná pagado con vida (Magia de Sangre)" : "");
        if (!event.getCastSource().consumesMana() || player.isCreative()) return;
        float spent = Math.max(0, event.getManaCost() - blood);
        ActiveMechanic shield = MechanicService.find(player, "arcane_shield");
        if (shield != null && spent > 0) {
            float cap = (float) (player.getMaxHealth() * shield.number("cap_health_fraction", 0.5));
            Shield st = SHIELDS.computeIfAbsent(player.getUUID(), id -> new Shield());
            float own = Math.min(st.own, player.getAbsorptionAmount());
            float next = Math.min(cap, own + (float) (spent * shield.number("mana_fraction", 0.03)));
            player.setAbsorptionAmount(player.getAbsorptionAmount() - own + next);
            st.own = next;
            st.lastSpend = tick;
        }

        // Magia Salvaje: probabilidad según el maná gastado de verdad (las cargas y la sangre no cuentan).
        ActiveMechanic wild = MechanicService.find(player, "wild_magic");
        if (wild != null && spent > 0 && replicating == null && AbilityService.remainingTicks(player, CD_WILD) <= 0) {
            double chance = Math.min(wild.number("chance_cap", 0.5),
                    Math.max(wild.number("min_chance", 0), spent * wild.number("chance_per_mana", 0.0033)));
            boolean proc = player.getRandom().nextDouble() < chance;
            io.github.rpgclasses.server.DebugLog.event(player, "Magia Salvaje", "tirada {}%: {}", chance * 100, proc ? "SALTA" : "no");
            if (proc) {
                AbilityService.setCooldown(player, CD_WILD, (int) Math.round(wild.number("icd_ticks", 30)));
                WILD_PENDING.add(new WildProc(player.getUUID(), event.getSpellId()));
            }
        }
    }

    // ---------------------------------------------------------------- Magia Salvaje

    /**
     * Lanza otro conjuro ofensivo equipado (al azar, no legendario, no canalizado continuo ni con relanzamientos)
     * en {@code directions} direcciones horizontales, sin maná ni cooldown, con un tercio del daño.
     * Se usa un MagicData temporal para no pisar el estado de lanzamiento real del jugador.
     */
    private static void wildMagic(ServerPlayer player, String castSpellId) {
        ActiveMechanic wild = MechanicService.find(player, "wild_magic");
        if (wild == null || !player.isAlive() || !(player.level() instanceof ServerLevel level)) return;
        List<SpellData> options = new ArrayList<>();
        try {
            for (SpellSelectionManager.SelectionOption o : new SpellSelectionManager(player).getAllSpells()) {
                SpellData d = o.spellData;
                AbstractSpell s = d == null ? null : d.getSpell();
                if (s == null || "irons_spellbooks:none".equals(s.getSpellId())) continue;
                if (options.stream().anyMatch(x -> x.getSpell() == s)) continue;
                if (s.getRarity(d.getLevel()) == SpellRarity.LEGENDARY || s.getCastType() == CastType.CONTINUOUS
                        || s.getRecastCount(d.getLevel(), player) > 0 || isSummonSpell(s.getSpellId())) continue;
                if (StatEngine.schoolWall(player, s.getSchoolType().getId().toString()) >= 1.0) continue;
                if (isOffensive(s, d.getLevel(), player)) options.add(d);
            }
        } catch (RuntimeException e) {
            RpgClasses.LOGGER.warn("[rpgclasses] Magia Salvaje: no se pudieron leer los conjuros equipados", e);
            return;
        }
        if (options.isEmpty()) {
            io.github.rpgclasses.server.DebugLog.event(player, "Magia Salvaje", "{} no tiene conjuros ofensivos válidos para replicar", player.getGameProfile().getName());
            return;
        }
        // Se prefiere otro conjuro distinto del lanzado; si solo está ese, se replica el mismo (rev. 2.7f).
        List<SpellData> others = options.stream().filter(d -> !d.getSpell().getSpellId().equals(castSpellId)).toList();
        List<SpellData> pool = others.isEmpty() ? options : others;
        SpellData pick = pool.get(player.getRandom().nextInt(pool.size()));
        AbstractSpell spell = pick.getSpell();
        int directions = Math.max(1, (int) Math.round(wild.number("directions", 8)));
        float yaw = player.getYRot(), pitch = player.getXRot(), head = player.getYHeadRot(), body = player.yBodyRot;
        replicating = player.getUUID();
        replicaFactor = wild.number("replica_damage", 0.33);
        int cast = 0;
        try {
            for (int i = 0; i < directions; i++) {
                float dir = yaw + i * 360f / directions;
                player.setYRot(dir);
                player.setYHeadRot(dir);
                player.yBodyRot = dir;
                player.setXRot(0);
                MagicData temp = new MagicData(player);
                if (spell.checkPreCastConditions(level, pick.getLevel(), player, temp)) {
                    spell.onCast(level, pick.getLevel(), player, CastSource.COMMAND, temp);
                    cast++;
                }
            }
        } catch (RuntimeException e) {
            RpgClasses.LOGGER.warn("[rpgclasses] Magia Salvaje: fallo al replicar {}", spell.getSpellId(), e);
        } finally {
            player.setYRot(yaw);
            player.setYHeadRot(head);
            player.yBodyRot = body;
            player.setXRot(pitch);
            replicating = null;
        }
        io.github.rpgclasses.server.DebugLog.event(player, "Magia Salvaje", "{} replica {} en {} direcciones ({} lanzadas)",
                player.getGameProfile().getName(), spell.getSpellId(), directions, cast);
        if (cast > 0) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                    player.getX(), player.getY() + 1, player.getZ(), 30, 0.8, 0.5, 0.8, 0.05);
            player.displayClientMessage(Component.translatable("message.rpgclasses.wild_magic", spell.getDisplayName(player)), true);
        }
    }

    private static boolean isOffensive(AbstractSpell spell, int spellLevel, ServerPlayer player) {
        if (EXTRA_OFFENSIVE.contains(spell.getSpellId())) return true;
        try {
            for (MutableComponent line : spell.getUniqueInfo(spellLevel, player)) {
                for (Component part : line.toFlatList()) {
                    if (part.getContents() instanceof TranslatableContents t && t.getKey().contains("damage")) return true;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    private static void decayShields(net.minecraft.server.MinecraftServer server) {
        SHIELDS.entrySet().removeIf(e -> {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            ActiveMechanic shield = p == null ? null : MechanicService.find(p, "arcane_shield");
            if (shield == null) return true;
            Shield st = e.getValue();
            st.own = Math.min(st.own, p.getAbsorptionAmount());
            if (tick - st.lastSpend > shield.number("decay_delay_seconds", 6) * 20) {
                float cap = (float) (p.getMaxHealth() * shield.number("cap_health_fraction", 0.5));
                float next = Math.max(0, st.own - cap * (float) shield.number("decay_per_second", 0.1));
                p.setAbsorptionAmount(p.getAbsorptionAmount() - st.own + next);
                st.own = next;
            }
            return st.own <= 0;
        });
    }

    // ---------------------------------------------------------------- Concentración Inquebrantable

    /** Iron's no interrumpe el conjuro si el golpe está "marcado como veneno": se marca antes de que lo mire. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttacked(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && concentrating(player)) {
            MagicData.getPlayerMagicData(player).markPoisoned();
        }
    }

    @SubscribeEvent
    public void onKnockback(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && concentrating(player)) event.setCanceled(true);
    }

    private static boolean concentrating(ServerPlayer player) {
        if (!MechanicService.has(player, "unbreakable_concentration")) return false;
        MagicData magic = MagicData.getPlayerMagicData(player);
        return magic != null && magic.isCasting() && magic.getCastType() == CastType.LONG;
    }

    // ---------------------------------------------------------------- kit y comandos

    private static void inscribe(ItemStack stack, KitItem kit) {
        if (stack.getItem() instanceof IPresetSpellContainer preset) preset.initializeSpellContainer(stack);
        ISpellContainer container = ISpellContainer.get(stack);
        if (container == null) container = ISpellContainer.create(Math.max(1, kit.spells().size()), true, true);
        ISpellContainerMutable mutable = container.mutableCopy();
        int index = 0;
        for (KitSpell k : kit.spells()) {
            AbstractSpell spell = SpellRegistry.getSpell(k.id());
            if (spell == null || "irons_spellbooks:none".equals(spell.getSpellId())) {
                RpgClasses.LOGGER.warn("[rpgclasses] Conjuro de kit desconocido: {}", k.id());
                continue;
            }
            mutable.addSpellAtIndex(spell, k.level(), index++, false);
        }
        ISpellContainer.set(stack, mutable.toImmutable());
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        // Se fusiona con el /clase de ClassCommands.
        event.getDispatcher().register(Commands.literal("clase")
                .then(Commands.literal("conjuro").then(Commands.argument("ranura", IntegerArgumentType.integer(1, FreeCasts.SLOTS))
                        .executes(c -> FreeCasts.bind(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "ranura"))))));
    }

    private static boolean hasFreeSlots(ServerPlayer player) {
        return AbilityService.has(player, FreeCasts.abilityId(1));
    }

    private static boolean isSummonSpell(String spellId) {
        String path = spellId.contains(":") ? spellId.substring(spellId.indexOf(':') + 1) : spellId;
        return path.startsWith("summon_") || path.equals("raise_dead");
    }

    // ---------------------------------------------------------------- daño de conjuros

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        if (attacker == null) return;

        // Daño de invocaciones (Convocación, Legión/Élite). Iron's pone al dueño como causante y a la
        // invocación como atacante directo, así que se mira primero el directo.
        Entity direct = source.getDirectEntity();
        for (Entity e : new Entity[]{direct, attacker}) {
            if (e != null && e.getPersistentData().contains(TAG_SUMMON_DAMAGE)) {
                event.setAmount((float) (event.getAmount() * e.getPersistentData().getDouble(TAG_SUMMON_DAMAGE)));
                return;
            }
            if (e != null && e.getPersistentData().getBoolean(TAG_SUMMON_DONE)) return;
        }

        // Réplicas de Magia Salvaje: daño reducido (daño directo durante la réplica o de lo que creó).
        if (direct != null && direct.getPersistentData().contains(TAG_REPLICA)) {
            event.setAmount((float) (event.getAmount() * direct.getPersistentData().getDouble(TAG_REPLICA)));
            return;
        }
        if (replicating != null && attacker.getUUID().equals(replicating)) {
            event.setAmount((float) (event.getAmount() * replicaFactor));
            return;
        }

        if (!(attacker instanceof ServerPlayer player) || !isSpellDamage(source)) return;
        double factor = 1;

        ActiveMechanic power = MechanicService.find(player, "distance_power");
        if (power != null) {
            double near = power.number("near_blocks", 6), far = power.number("far_blocks", 15);
            double nearBonus = power.number("near_bonus", 0), farBonus = power.number("far_bonus", 0);
            double t = Math.max(0, Math.min(1, (player.distanceTo(event.getEntity()) - near) / Math.max(0.01, far - near)));
            factor *= 1 + nearBonus + (farBonus - nearBonus) * t;
        }

        Ambush ambush = AMBUSHES.get(player.getUUID());
        if (ambush != null && tick <= ambush.expires()) {
            String spellId = spellId(source);
            if (spellId == null || spellId.equals(ambush.spellId())) {
                factor *= 1 + MechanicService.number(player, "arcane_ambush", "damage_bonus", 0.6);
            }
        }

        if (factor != 1) event.setAmount((float) Math.max(0, event.getAmount() * factor));
        ElementalDebuffs.onSpellHit(player, event.getEntity(), source, event.getAmount(), tick);
    }

    static boolean isSpellDamage(DamageSource source) {
        return source.typeHolder().unwrapKey().map(k -> IRONS.equals(k.location().getNamespace())).orElse(false);
    }

    static String spellId(DamageSource source) {
        Method spellOf = method("io.redspace.ironsspellbooks.damage.SpellDamageSource", "spell");
        if (spellOf == null || !spellOf.getDeclaringClass().isInstance(source)) return null;
        try {
            Object spell = spellOf.invoke(source);
            return spell instanceof AbstractSpell s ? s.getSpellId() : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- invocaciones

    @SubscribeEvent
    public void onSummonOwner(SetSummonOwnerEvent event) {
        if (event.getOwner() instanceof ServerPlayer && event.getSummon() instanceof LivingEntity) {
            NEW_SUMMONS.add(new Entity[]{event.getOwner(), event.getSummon()});
        }
    }

    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (replicating != null && !(event.getEntity() instanceof net.minecraft.world.entity.player.Player)) {
            event.getEntity().getPersistentData().putDouble(TAG_REPLICA, replicaFactor);
        }
        if (event.getLevel() instanceof ServerLevel level && event.getEntity().getPersistentData().hasUUID(TAG_CLONE_OF)) {
            CLONES.put(event.getEntity().getUUID(), new Clone(level, event.getEntity().getPersistentData().getUUID(TAG_CLONE_OF)));
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            tick++;
            for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) bloodForContinuous(p);
            return;
        }
        if (!LOANS.isEmpty()) {
            // Préstamos que no llegaron a un intento de lanzamiento (p. ej. conjuro en cooldown): se devuelven.
            for (Iterator<Map.Entry<UUID, Loan>> it = LOANS.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Loan> e = it.next();
                if (e.getValue().tick() >= tick) continue;
                ServerPlayer p = event.getServer().getPlayerList().getPlayer(e.getKey());
                if (p != null) refund(p, e.getValue());
                it.remove();
            }
        }
        if (!NEW_SUMMONS.isEmpty()) processNewSummons();
        if (!WILD_PENDING.isEmpty()) {
            List<WildProc> procs = new ArrayList<>(WILD_PENDING);
            WILD_PENDING.clear();
            for (WildProc w : procs) {
                ServerPlayer p = event.getServer().getPlayerList().getPlayer(w.player());
                if (p != null) wildMagic(p, w.castSpellId());
            }
        }
        if (tick % 20 == 0) {
            decayShields(event.getServer());
            FREE_PENDING.values().removeIf(f -> tick > f.expires());
            for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                if (hasFreeSlots(p)) FreeCasts.recharge(p);
            }
            CLONES.entrySet().removeIf(e -> {
                Entity clone = e.getValue().level().getEntity(e.getKey());
                if (clone == null) return !e.getValue().level().getServer().isRunning();
                if (!clone.isAlive()) return true;
                Entity original = e.getValue().level().getEntity(e.getValue().original());
                if (original != null && original.isAlive()) return false;
                unsummon(clone);
                return true;
            });
            AMBUSHES.values().removeIf(a -> tick > a.expires());
            ElementalDebuffs.cleanup(tick);
            SUMMON_CASTS.values().removeIf(t -> t < tick - 1);
        }
    }

    private static void processNewSummons() {
        Map<ServerPlayer, List<LivingEntity>> byOwner = new HashMap<>();
        for (Entity[] pair : NEW_SUMMONS) {
            if (pair[0] instanceof ServerPlayer owner && pair[1] instanceof LivingEntity summon && summon.isAlive()) {
                byOwner.computeIfAbsent(owner, o -> new ArrayList<>()).add(summon);
            }
        }
        NEW_SUMMONS.clear();
        for (Map.Entry<ServerPlayer, List<LivingEntity>> e : byOwner.entrySet()) {
            ServerPlayer owner = e.getKey();
            List<LivingEntity> fresh = new ArrayList<>();
            for (LivingEntity s : e.getValue()) {
                // Iron's asigna dueño dos veces (constructor e initSummon): cada invocación cuenta una vez.
                if (!s.getPersistentData().getBoolean(TAG_SUMMON_DONE) && !fresh.contains(s)) fresh.add(s);
            }
            if (fresh.isEmpty()) continue;

            ActiveMechanic count = MechanicService.find(owner, "summon_count");
            Long castTick = SUMMON_CASTS.get(owner.getUUID());
            List<LivingEntity> originals = new ArrayList<>(fresh.stream().filter(s -> !s.getPersistentData().hasUUID(TAG_CLONE_OF)).toList());
            if (count != null && castTick != null && castTick >= tick - 1 && !originals.isEmpty()) {
                SUMMON_CASTS.remove(owner.getUUID());
                int change = (int) Math.round(count.number("count_change", 0));
                if (change < 0) {
                    int min = Math.max(1, (int) Math.round(count.number("min_count", 1)));
                    int remove = Math.min(-change, originals.size() - min);
                    for (int i = 0; i < remove; i++) {
                        LivingEntity extra = originals.get(originals.size() - 1 - i);
                        unsummon(extra);
                        fresh.remove(extra);
                    }
                } else {
                    for (int i = 0; i < change; i++) {
                        LivingEntity clone = cloneSummon(owner, originals.get(i % originals.size()));
                        if (clone != null) fresh.add(clone);
                    }
                }
                io.github.rpgclasses.server.DebugLog.event(null, "Legión/Élite", "{} invocaciones nuevas de {}, cambio {}",
                        originals.size(), owner.getGameProfile().getName(), change);
            }
            for (LivingEntity s : fresh) applySummonStats(owner, s);
        }
    }

    private static void applySummonStats(ServerPlayer owner, LivingEntity summon) {
        double health = 1 + MechanicService.number(owner, "summon_bonus", "summon_health", 0)
                + MechanicService.number(owner, "summon_count", "summon_stats", 0);
        double damage = 1 + MechanicService.number(owner, "summon_bonus", "summon_damage", 0)
                + MechanicService.number(owner, "summon_count", "summon_stats", 0);
        summon.getPersistentData().putBoolean(TAG_SUMMON_DONE, true);
        if (damage != 1) summon.getPersistentData().putDouble(TAG_SUMMON_DAMAGE, Math.max(0, damage));
        AttributeInstance max = summon.getAttribute(Attributes.MAX_HEALTH);
        if (max != null && health != 1) {
            max.removeModifier(SUMMON_HEALTH_ID);
            max.addPermanentModifier(new AttributeModifier(SUMMON_HEALTH_ID, "rpgclasses:summon_health",
                    Math.max(-0.9, health - 1), AttributeModifier.Operation.MULTIPLY_TOTAL));
            summon.setHealth(summon.getMaxHealth());
        }
    }

    /** Legión: copia de la invocación (mismo tipo y datos), del mismo dueño; desaparece con la original. */
    private static LivingEntity cloneSummon(ServerPlayer owner, LivingEntity original) {
        Method setOwner = setOwner();
        if (setOwner == null || !(original.level() instanceof ServerLevel level)) {
            RpgClasses.LOGGER.warn("[rpgclasses] Legión: no se puede copiar {} (setOwner={})", original, setOwner);
            return null;
        }
        Entity created = original.getType().create(level);
        if (!(created instanceof LivingEntity clone)) {
            RpgClasses.LOGGER.warn("[rpgclasses] Legión: el tipo {} no se puede crear", original.getType());
            return null;
        }
        CompoundTag tag = new CompoundTag();
        original.saveWithoutId(tag);
        tag.remove("UUID");
        clone.load(tag);
        clone.setUUID(UUID.randomUUID());
        clone.moveTo(original.getX() + (level.random.nextDouble() - 0.5) * 2, original.getY(),
                original.getZ() + (level.random.nextDouble() - 0.5) * 2, original.getYRot(), original.getXRot());
        clone.getPersistentData().putUUID(TAG_CLONE_OF, original.getUUID());
        clone.getPersistentData().remove(TAG_SUMMON_DONE);
        if (!level.addFreshEntity(clone)) {
            RpgClasses.LOGGER.warn("[rpgclasses] Legión: el mundo rechazó la copia de {}", original);
            return null;
        }
        try {
            setOwner.invoke(null, clone, owner);
            Method duration = method(SUMMON_MANAGER, "setDuration", Entity.class, int.class);
            if (duration != null) duration.invoke(null, clone, CLONE_MAX_TICKS);
        } catch (ReflectiveOperationException | RuntimeException e) {
            RpgClasses.LOGGER.warn("[rpgclasses] Legión: fallo al asignar dueño a la copia", e);
            clone.discard();
            return null;
        }
        joinRecast(owner, original, clone);
        // setOwner vuelve a lanzar SetSummonOwnerEvent: la copia ya se procesa aquí, así que se saca de la cola.
        NEW_SUMMONS.removeIf(pair -> pair[1] == clone);
        CLONES.put(clone.getUUID(), new Clone(level, original.getUUID()));
        return clone;
    }

    /**
     * Mete la copia en los datos de relanzamiento del conjuro que creó la original: así el cliente la ve como
     * invocación propia (contorno verde a través de las paredes) y relanzar el conjuro también la retira.
     */
    private static void joinRecast(ServerPlayer owner, LivingEntity original, LivingEntity clone) {
        try {
            MagicData magic = MagicData.getPlayerMagicData(owner);
            Object recasts = magic.getClass().getMethod("getPlayerRecasts").invoke(magic);
            for (Object instance : (Iterable<?>) recasts.getClass().getMethod("getActiveRecasts").invoke(recasts)) {
                Object data = instance.getClass().getMethod("getCastData").invoke(instance);
                if (data == null) continue;
                Method summons;
                try {
                    summons = data.getClass().getMethod("getSummons");
                } catch (NoSuchMethodException e) {
                    continue;
                }
                if (!(summons.invoke(data) instanceof java.util.Set<?> set) || !set.contains(original.getUUID())) continue;
                data.getClass().getMethod("add", Entity.class).invoke(data, clone);
                recasts.getClass().getMethod("syncToPlayer", instance.getClass()).invoke(recasts, instance);
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            RpgClasses.LOGGER.warn("[rpgclasses] Legión: no se pudo marcar la copia como invocación en el cliente", e);
        }
    }

    private static void unsummon(Entity summon) {
        try {
            Method remove = method(SUMMON_MANAGER, "removeSummon", Entity.class);
            if (remove != null) remove.invoke(null, summon);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        if (summon.level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                    summon.getX(), summon.getY() + summon.getBbHeight() / 2, summon.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
        }
        summon.discard();
    }

    private static Method setOwner() {
        return method(SUMMON_MANAGER, "setOwner", Entity.class, Entity.class);
    }

    private static Method method(String className, String name, Class<?>... params) {
        String key = className + "#" + name;
        if (METHODS.containsKey(key)) return METHODS.get(key);
        Method m = null;
        for (ClassLoader loader : new ClassLoader[]{IronsCompat.class.getClassLoader(), Thread.currentThread().getContextClassLoader()}) {
            try {
                m = Class.forName(className, true, loader).getMethod(name, params);
                break;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        }
        if (m == null) RpgClasses.LOGGER.warn("[rpgclasses] Iron's: no se encontró {}; esa parte del puente queda desactivada", key);
        METHODS.put(key, m);
        return m;
    }
}
