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
import net.minecraft.nbt.CompoundTag;
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
 * </ul>
 * Fluidez (cast_no_slow) no necesita eventos: es un modificador de casting_movespeed en {@link StatEngine}.
 */
public final class IronsCompat {
    public static final String IRONS = "irons_spellbooks";

    private static final String CD_ARCANE_STEP = "rpgclasses:arcane_step";
    private static final String CD_AMBUSH = "rpgclasses:arcane_ambush";
    private static final String TAG_SUMMON_DONE = "rpgclasses:summon_done";
    private static final String TAG_SUMMON_DAMAGE = "rpgclasses:summon_damage";
    private static final String TAG_CLONE_OF = "rpgclasses:clone_of";
    private static final UUID SUMMON_HEALTH_ID = UUID.fromString("6f0c3b1e-2a4d-4c7e-8b59-1d3e5f7a9c20");
    /** Tope de vida de una invocación extra de Legión si se pierde el vínculo con la original (10 min). */
    private static final int CLONE_MAX_TICKS = 20 * 60 * 10;

    private static final Method SPELL_OF_SOURCE = method("io.redspace.ironsspellbooks.damage.SpellDamageSource", "spell");
    private static final Method SET_OWNER = method("io.redspace.ironsspellbooks.capabilities.magic.SummonManager", "setOwner", Entity.class, Entity.class);
    private static final Method SET_DURATION = method("io.redspace.ironsspellbooks.capabilities.magic.SummonManager", "setDuration", Entity.class, int.class);
    private static final Method REMOVE_SUMMON = method("io.redspace.ironsspellbooks.capabilities.magic.SummonManager", "removeSummon", Entity.class);

    private static long tick;
    /** Magia de Sangre: maná prestado este tick a cambio de vida; se devuelve si el conjuro no llega a empezar. */
    private static final Map<UUID, Loan> LOANS = new HashMap<>();
    /** Jugadores con Legión/Élite que han lanzado un conjuro este tick, para ajustar sus invocaciones nuevas. */
    private static final Map<UUID, Long> SUMMON_CASTS = new HashMap<>();
    /** Invocaciones que acaban de recibir dueño, pendientes de procesar al final del tick (tras initSummon). */
    private static final List<Entity[]> NEW_SUMMONS = new ArrayList<>();
    /** Emboscada Arcana preparada: conjuro y tick de caducidad. */
    private static final Map<UUID, Ambush> AMBUSHES = new HashMap<>();
    /** Invocaciones extra de Legión → su original (desaparecen con ella). */
    private static final Map<UUID, Clone> CLONES = new HashMap<>();

    private record Loan(long tick, float mana, float health) {}
    private record Ambush(String spellId, long expires) {}
    private record Clone(ServerLevel level, UUID original) {}

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new IronsCompat());
        RpgClasses.LOGGER.info("[rpgclasses] Iron's Spells detectado: puente activado");
    }

    // ---------------------------------------------------------------- regla 14 + confirmación de préstamos

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPreCast(SpellPreCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String school = event.getSchoolType().getId().toString();
        if (StatEngine.schoolWall(player, school) >= 1.0) {
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
        if (event.isCanceled() || !event.getCastSource().consumesMana()) refund(player, loan);
    }

    // ---------------------------------------------------------------- Magia de Sangre

    /**
     * Iron's comprueba el maná antes de cualquier evento cancelable, pero calcula el nivel del conjuro
     * (ModifySpellLevelEvent) justo antes de intentar lanzarlo: ahí se adelanta el maná que falta pagando vida.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onSpellLevel(ModifySpellLevelEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative()) return;
        ActiveMechanic blood = MechanicService.find(player, "blood_magic");
        if (blood == null || LOANS.containsKey(player.getUUID())) return;
        MagicData magic = MagicData.getPlayerMagicData(player);
        if (magic == null || magic.isCasting()) return;
        float cost = event.getSpell().getManaCost(event.getLevel());
        float missing = cost - magic.getMana();
        if (missing <= 0) return;
        float health = (float) (missing / blood.number("mana_per_health", 15));
        float floor = (float) (player.getMaxHealth() * blood.number("min_health_fraction", 0.25));
        if (player.getHealth() - health < floor) return;
        player.setHealth(player.getHealth() - health);
        magic.setMana(magic.getMana() + missing);
        LOANS.put(player.getUUID(), new Loan(tick, missing, health));
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

        // Daño de invocaciones (Convocación, Legión/Élite).
        CompoundTag data = attacker.getPersistentData();
        if (data.contains(TAG_SUMMON_DAMAGE)) {
            event.setAmount((float) (event.getAmount() * data.getDouble(TAG_SUMMON_DAMAGE)));
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
    }

    private static boolean isSpellDamage(DamageSource source) {
        return source.typeHolder().unwrapKey().map(k -> IRONS.equals(k.location().getNamespace())).orElse(false);
    }

    private static String spellId(DamageSource source) {
        if (SPELL_OF_SOURCE == null || !SPELL_OF_SOURCE.getDeclaringClass().isInstance(source)) return null;
        try {
            Object spell = SPELL_OF_SOURCE.invoke(source);
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
        if (event.getLevel() instanceof ServerLevel level && event.getEntity().getPersistentData().hasUUID(TAG_CLONE_OF)) {
            CLONES.put(event.getEntity().getUUID(), new Clone(level, event.getEntity().getPersistentData().getUUID(TAG_CLONE_OF)));
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            tick++;
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
        if (tick % 20 == 0) {
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
                if (!s.getPersistentData().getBoolean(TAG_SUMMON_DONE)) fresh.add(s);
            }
            if (fresh.isEmpty()) continue;

            ActiveMechanic count = MechanicService.find(owner, "summon_count");
            Long castTick = SUMMON_CASTS.get(owner.getUUID());
            List<LivingEntity> originals = fresh.stream().filter(s -> !s.getPersistentData().hasUUID(TAG_CLONE_OF)).toList();
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
        if (SET_OWNER == null || !(original.level() instanceof ServerLevel level)) return null;
        Entity created = original.getType().create(level);
        if (!(created instanceof LivingEntity clone)) return null;
        CompoundTag tag = new CompoundTag();
        original.saveWithoutId(tag);
        tag.remove("UUID");
        clone.load(tag);
        clone.setUUID(UUID.randomUUID());
        clone.moveTo(original.getX() + (level.random.nextDouble() - 0.5) * 2, original.getY(),
                original.getZ() + (level.random.nextDouble() - 0.5) * 2, original.getYRot(), original.getXRot());
        clone.getPersistentData().putUUID(TAG_CLONE_OF, original.getUUID());
        clone.getPersistentData().remove(TAG_SUMMON_DONE);
        if (!level.addFreshEntity(clone)) return null;
        try {
            SET_OWNER.invoke(null, clone, owner);
            if (SET_DURATION != null) SET_DURATION.invoke(null, clone, CLONE_MAX_TICKS);
        } catch (ReflectiveOperationException e) {
            clone.discard();
            return null;
        }
        // setOwner vuelve a lanzar SetSummonOwnerEvent: la copia ya se procesa aquí, así que se saca de la cola.
        NEW_SUMMONS.removeIf(pair -> pair[1] == clone);
        CLONES.put(clone.getUUID(), new Clone(level, original.getUUID()));
        return clone;
    }

    private static void unsummon(Entity summon) {
        try {
            if (REMOVE_SUMMON != null) REMOVE_SUMMON.invoke(null, summon);
        } catch (ReflectiveOperationException ignored) {
        }
        if (summon.level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                    summon.getX(), summon.getY() + summon.getBbHeight() / 2, summon.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
        }
        summon.discard();
    }

    private static Method method(String className, String name, Class<?>... params) {
        try {
            Method m = Class.forName(className).getMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (ReflectiveOperationException | RuntimeException e) {
            RpgClasses.LOGGER.warn("[rpgclasses] Iron's: no se encontró {}#{}; esa parte del puente queda desactivada", className, name);
            return null;
        }
    }
}
