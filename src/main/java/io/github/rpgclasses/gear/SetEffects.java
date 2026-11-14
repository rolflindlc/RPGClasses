package io.github.rpgclasses.gear;

import io.github.rpgclasses.content.MonkCharges;
import io.github.rpgclasses.content.race.Races;
import io.github.rpgclasses.server.DamagePipeline;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.FrostWalkerEnchantment;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Efectos de set completo (4 piezas del mismo tier) del equipo de clase. <b>Solo funcionan para su clase</b>: un
 * Paladín con el set de dragonsteel del Bárbaro recibe su armadura, pero no la Ira del Wyrm (rev. 2.20).
 *
 * <p>Bárbaro: Olfato (cuero), Tintineo de Guerra (cota), Pecho de Hierro (hierro), Botín Dorado (oro), Crujido
 * (diamante), Corazón de Brasas (netherita), Rugido de Dragón (escamas) e Ira del Wyrm (dragonsteel).
 * Monje: Primer Paso (cuero), Anillas Sonoras (cota), Postura Firme (hierro), Peregrinaje (oro), Reflejo de Cristal
 * (diamante), Sello de Postura (netherita), Danza del Dragón (escamas) y Puño del Wyrm (dragonsteel).
 */
public final class SetEffects {
    private static final class State {
        int embers;
        long emberUntil;
        int sprintTicks;
        boolean tinkleReady;
        long lastCone;
        long lastSpark;
        long danceUntil;
        int danceStance;
        long nextBite;
        final List<Seal> seals = new ArrayList<>();
        final Map<UUID, long[]> frostCones = new HashMap<>();
        final Map<UUID, Long> crunch = new HashMap<>();
    }

    private record Seal(Vec3 pos, int stance, int charges, long end) {}

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static State state(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    /** Clave de idioma del set de un tier: barbarian_leather, monk_dragon, barbarian_dragonsteel... */
    public static String setKey(GearClass c, ArmorTier tier) {
        String group = tier.isDragonsteel() ? "dragonsteel" : tier.isDragon() ? "dragon" : tier.id;
        return c.id + "_" + group;
    }

    /** Tier del set completo activo para la clase del jugador, o null (otra clase, set incompleto o mezclado). */
    public static ArmorTier active(ServerPlayer player, GearClass c) {
        if (player instanceof FakePlayer || !c.is(player)) return null;
        return GearRules.fullSet(player, c);
    }

    private static boolean has(ServerPlayer p, GearClass c, ArmorTier tier) {
        return active(p, c) == tier;
    }

    private static ParticleOptions stanceParticle(int stance) {
        return switch (stance) {
            case MonkCharges.FIRE -> ParticleTypes.FLAME;
            case MonkCharges.WATER -> ParticleTypes.SPLASH;
            default -> GearFx.STONE_DUST;
        };
    }

    private static net.minecraft.core.particles.DustParticleOptions stanceDust(int stance) {
        return switch (stance) {
            case MonkCharges.FIRE -> GearFx.EMBER;
            case MonkCharges.WATER -> GearFx.WATER_DUST;
            default -> GearFx.STONE_DUST;
        };
    }

    private static double unarmedHit(ServerPlayer p) {
        return p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                + io.github.rpgclasses.registry.ModAttributes.value(p, io.github.rpgclasses.registry.ModAttributes.UNARMED_DAMAGE);
    }

    // ================================================================== ganchos del Monje (MonkCharges, MonkPerks)

    /** Multiplicador de los tiempos de decaimiento: cuero +10 % para todas; netherita ×2 en Piedra. */
    public static double monkDecayFactor(ServerPlayer p, int stance) {
        ArmorTier t = active(p, GearClass.MONK);
        if (t == ArmorTier.LEATHER) return 1.1;
        if (t == ArmorTier.NETHERITE && stance == MonkCharges.STONE) return 2.0;
        return 1;
    }

    /** Primer Paso (cuero): al entrar en una postura sin cargas, recibes 1. */
    public static int stanceEntryCharges(ServerPlayer p, int kept) {
        return kept == 0 && has(p, GearClass.MONK, ArmorTier.LEATHER) ? 1 : 0;
    }

    /** Anillas Sonoras (cota): tintineo por carga y acorde al llegar al umbral. */
    public static void onMonkGain(ServerPlayer p, int charges, int threshold) {
        if (!has(p, GearClass.MONK, ArmorTier.CHAIN)) return;
        float pitch = 0.6f + Math.min(1.4f, charges * 0.12f);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.5f, pitch);
        if (charges == threshold) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.9f, 1.0f);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.9f, 1.26f);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.9f, 1.5f);
        }
    }

    /** Postura Firme (hierro): Piedra gana carga con golpes desde 1,5 HP. */
    public static double stoneThreshold(ServerPlayer p, double base) {
        return has(p, GearClass.MONK, ArmorTier.IRON) ? Math.min(base, 1.5) : base;
    }

    /** Peregrinaje (oro): la Meditación cura un 20 % más. */
    public static double meditationFactor(ServerPlayer p) {
        return has(p, GearClass.MONK, ArmorTier.GOLD) ? 1.2 : 1;
    }

    /** Reflejo de Cristal (diamante): el Desvío da +1 carga y deja un destello prismático. */
    public static int deflectBonus(ServerPlayer p) {
        if (!has(p, GearClass.MONK, ArmorTier.DIAMOND)) return 0;
        GearFx.burst(p, ParticleTypes.END_ROD, 12, 0.08);
        GearFx.burst(p, ParticleTypes.GLOW, 8, 0.05);
        return 1;
    }

    /** Sello de Postura (netherita): la descarga con ≥5 cargas deja un sello que la repite al 30 % a los 3 s. */
    public static void onMonkDischarge(ServerPlayer p, int stance, int charges) {
        if (charges < 5 || !has(p, GearClass.MONK, ArmorTier.NETHERITE)) return;
        state(p).seals.add(new Seal(p.position(), stance, (int) Math.max(1, Math.round(charges * 0.3)), p.level().getGameTime() + 60));
    }

    /** Danza del Dragón (escamas) y Puño del Wyrm (dragonsteel): al disparar el efecto propio. */
    public static void onMonkFinisher(ServerPlayer p, LivingEntity target, int stance, int charges) {
        ArmorTier t = active(p, GearClass.MONK);
        if (t == null) return;
        if (t.isDragon()) {
            State st = state(p);
            st.danceUntil = p.level().getGameTime() + 80;
            st.danceStance = stance;
            st.nextBite = p.level().getGameTime() + 20;
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.7f, 1.4f);
        } else if (t.isDragonsteel()) {
            float damage = (float) (unarmedHit(p) * 0.4 * Math.max(1, charges / 10.0));
            DamageSource src = GearFx.source(p, GearFx.elementType(t.element));
            List<LivingEntity> line = GearFx.lineTargets(p, 8);
            LivingEntity last = null;
            for (LivingEntity e : line) {
                if (e != target) e.hurt(src, damage);
                GearFx.applyElement(p, e, t.element, 1);
                last = e;
            }
            GearFx.ray(p, GearFx.elementDust(t.element), 8, 24);
            GearFx.ray(p, GearFx.elementParticle(t.element), 8, 12);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.6f, 1.6f);
            if ("lightning".equals(t.element) && last != null && p.level() instanceof ServerLevel level) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(last.getX(), last.getY(), last.getZ());
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
            }
        }
    }

    // ================================================================== ganchos del Bárbaro (CombatAbilities, DamagePipeline)

    /** Corazón de Brasas (netherita) y Rugido de Dragón (escamas): al usar Embestida o Furia Berserker. */
    public static void onBarbarianAbility(ServerPlayer p, boolean berserker) {
        ArmorTier t = active(p, GearClass.BARBARIAN);
        if (t == null) return;
        ServerLevel level = (ServerLevel) p.level();
        if (t == ArmorTier.NETHERITE) {
            State st = state(p);
            int embers = level.getGameTime() <= st.emberUntil ? st.embers : 0;
            if (embers <= 0) return;
            float damage = 2 + 1.5f * embers;
            for (LivingEntity e : GearFx.enemiesAround(p, p.position(), 4)) {
                e.hurt(GearFx.source(p, GearFx.FLAME), damage);
                MonkCharges.addBurn(e, 3);
            }
            for (int r = 1; r <= 4; r++) GearFx.ring(level, p.position(), ParticleTypes.FLAME, r, 10 + r * 6);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 0.7f);
            st.embers = 0;
        } else if (t.isDragon() && berserker) {
            for (LivingEntity e : GearFx.enemiesAround(p, p.position(), 8)) {
                if (!Races.isBoss(e) && e instanceof Mob mob) {
                    Vec3 away = mob.position().subtract(p.position()).normalize().scale(8).add(mob.position());
                    mob.setTarget(null);
                    mob.getNavigation().moveTo(away.x, away.y, away.z, 1.4);
                    mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0));
                }
                switch (t.element) {
                    case "fire" -> MonkCharges.addBurn(e, 3);
                    case "ice" -> e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), p);
                    default -> GearFx.applyElement(p, e, "lightning", 1);
                }
            }
            for (int i = 0; i < 3; i++) GearFx.ring(level, p.position().add(0, i * 0.6, 0), GearFx.elementDust(t.element), 1.5 + i * 1.5, 24);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.0f, 0.9f);
        }
    }

    /** Crujido (diamante) e Ira del Wyrm (dragonsteel): en cada crítico del Bárbaro. */
    public static void onCrit(ServerPlayer p, LivingEntity target, double damage, boolean melee) {
        ArmorTier t = active(p, GearClass.BARBARIAN);
        if (t == null) return;
        State st = state(p);
        long now = p.level().getGameTime();
        if (t == ArmorTier.DIAMOND && melee) {
            Long last = st.crunch.get(target.getUUID());
            if (last == null || now - last >= 60) {
                st.crunch.put(target.getUUID(), now);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0), p);
                p.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.8f, 0.6f);
            }
        } else if (t.isDragonsteel() && wyrmActive(p) && now - st.lastCone >= 20) {
            st.lastCone = now;
            float cone = (float) (damage * 0.35);
            DamageSource src = GearFx.source(p, GearFx.elementType(t.element));
            List<LivingEntity> hit = GearFx.cone(p, 4, 70, null);
            for (LivingEntity e : hit) {
                if (e != target) e.hurt(src, cone);
                switch (t.element) {
                    case "fire" -> MonkCharges.addBurn(e, 3);
                    case "ice" -> {
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2), p);
                        long[] f = st.frostCones.computeIfAbsent(e.getUUID(), k -> new long[]{0, 0});
                        if (now - f[1] > 120) f[0] = 0;
                        f[0]++;
                        f[1] = now;
                        if (f[0] >= 3) {
                            f[0] = 0;
                            GearFx.freeze(e, 30);
                        }
                    }
                    default -> {
                        int chained = 0;
                        List<LivingEntity> done = new ArrayList<>(hit);
                        LivingEntity from = e;
                        while (chained < 2) {
                            LivingEntity next = GearFx.nearestEnemy(p, from.position(), 5, done);
                            if (next == null) break;
                            next.hurt(src, cone * 0.5f);
                            GearFx.line(from, next, ParticleTypes.ELECTRIC_SPARK, 10);
                            done.add(next);
                            from = next;
                            chained++;
                        }
                    }
                }
            }
            GearFx.coneFx(p, GearFx.elementParticle(t.element), 4, 70);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.PLAYERS, 0.7f, 1.3f);
        }
    }

    /** Ira del Wyrm: con menos del 40 % de vida o durante Furia Berserker. */
    private static boolean wyrmActive(ServerPlayer p) {
        return p.getHealth() < p.getMaxHealth() * 0.4f || p.hasEffect(io.github.rpgclasses.registry.ModEffects.BERSERKER_RAGE.get());
    }

    // ================================================================== eventos

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        if (event.getEntity() instanceof ServerPlayer victim && !(victim instanceof FakePlayer)) {
            ArmorTier barb = active(victim, GearClass.BARBARIAN);
            if (barb == ArmorTier.IRON && source.getDirectEntity() instanceof Projectile) {
                event.setAmount(event.getAmount() * 0.95f); // Pecho de Hierro
            }
            if (barb == ArmorTier.NETHERITE && event.getAmount() >= 1 && !source.is(DamagePipeline.NO_PROC)) {
                State st = state(victim);
                long now = victim.level().getGameTime();
                st.embers = now <= st.emberUntil ? Math.min(5, st.embers + 1) : 1;
                st.emberUntil = now + 160;
            }
            ArmorTier monk = active(victim, GearClass.MONK);
            if (monk == ArmorTier.DRAGONSTEEL_LIGHTNING && MonkCharges.activeStance(victim) == MonkCharges.STONE
                    && event.getAmount() >= 4 && source.getEntity() instanceof LivingEntity attacker && attacker != victim) {
                State st = state(victim);
                long now = victim.level().getGameTime();
                if (now - st.lastSpark >= 40) {
                    st.lastSpark = now;
                    attacker.hurt(GearFx.source(victim, GearFx.SPARK), 2);
                    GearFx.line(victim, attacker, ParticleTypes.ELECTRIC_SPARK, 8);
                }
            }
        }
        // Tintineo de Guerra (cota): el primer golpe tras 2 s corriendo empuja un 10 % más.
        if (source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() == attacker && !source.is(DamagePipeline.NO_PROC)) {
            State st = STATES.get(attacker.getUUID());
            if (st != null && st.tinkleReady && has(attacker, GearClass.BARBARIAN, ArmorTier.CHAIN)) {
                st.tinkleReady = false;
                LivingEntity target = event.getEntity();
                target.knockback(0.15, attacker.getX() - target.getX(), attacker.getZ() - target.getZ());
                attacker.level().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.CHAIN_FALL, SoundSource.PLAYERS, 0.8f, 1.2f);
            }
        }
    }

    /** Botín Dorado (oro): 10 % de soltar una pepita de oro al matar un mob hostil. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || !(event.getEntity() instanceof Enemy)) return;
        if (!has(p, GearClass.BARBARIAN, ArmorTier.GOLD) || p.getRandom().nextDouble() >= 0.1) return;
        LivingEntity dead = event.getEntity();
        ItemEntity nugget = new ItemEntity(dead.level(), dead.getX(), dead.getY() + 0.5, dead.getZ(), new ItemStack(Items.GOLD_NUGGET));
        Vec3 toward = p.position().subtract(dead.position()).normalize().scale(0.25);
        nugget.setDeltaMovement(toward.x, 0.35, toward.z);
        dead.level().addFreshEntity(nugget);
        GearFx.burst(dead, ParticleTypes.WAX_OFF, 10, 0.1);
        dead.level().playSound(null, dead.getX(), dead.getY(), dead.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.4f);
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        ServerLevel level = (ServerLevel) p.level();
        long now = level.getGameTime();
        ArmorTier barb = active(p, GearClass.BARBARIAN);
        ArmorTier monk = barb == null ? active(p, GearClass.MONK) : null;
        State st = STATES.get(p.getUUID());
        if (barb == null && monk == null) {
            if (st != null && st.seals.isEmpty() && st.danceUntil < now) STATES.remove(p.getUUID());
            if (st == null) return;
        }
        if (st == null) st = state(p);

        if (barb != null) {
            switch (barb) {
                case LEATHER -> { // Olfato: los mobs heridos brillan
                    if (now % 20 == 0) {
                        for (LivingEntity e : GearFx.enemiesAround(p, p.position(), 12)) {
                            if (e instanceof Enemy && e.getHealth() < e.getMaxHealth() * 0.3f) {
                                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false));
                            }
                        }
                    }
                }
                case CHAIN -> {
                    st.sprintTicks = p.isSprinting() ? st.sprintTicks + 1 : 0;
                    if (st.sprintTicks >= 40) st.tinkleReady = true;
                }
                case NETHERITE -> {
                    int embers = now <= st.emberUntil ? st.embers : 0;
                    if (embers > 0 && now % 6 == 0) {
                        level.sendParticles(GearFx.EMBER, p.getX(), p.getY() + 1.1, p.getZ(), embers * 2, 0.3, 0.45, 0.3, 0.01);
                    }
                }
                default -> {
                    if (barb.isDragonsteel() && wyrmActive(p) && now % 3 == 0) {
                        GearFx.wings(p, GearFx.elementDust(barb.element), now);
                    }
                }
            }
        }
        if (monk != null && monk.isDragonsteel()) {
            int stance = MonkCharges.activeStance(p);
            if ("ice".equals(monk.element) && stance == MonkCharges.WATER) {
                FrostWalkerEnchantment.onEntityMoved(p, level, p.blockPosition(), 1);
            } else if ("fire".equals(monk.element) && stance == MonkCharges.FIRE && p.isInLava() && now % 10 == 0) {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20, 0, false, false));
            }
        }

        // Danza del Dragón: un dragón espectral serpentea alrededor y muerde al enemigo más cercano cada segundo.
        if (st.danceUntil >= now) {
            double a = now * 0.35;
            for (int i = 0; i < 8; i++) {
                double t = a - i * 0.3;
                level.sendParticles(stanceDust(st.danceStance), p.getX() + Math.cos(t) * 1.6, p.getY() + 1 + Math.sin(t * 2) * 0.4,
                        p.getZ() + Math.sin(t) * 1.6, 1, 0, 0, 0, 0);
            }
            if (now >= st.nextBite) {
                st.nextBite = now + 20;
                LivingEntity bite = GearFx.nearestEnemy(p, p.position(), 6, List.of());
                if (bite != null) {
                    bite.invulnerableTime = 0;
                    bite.hurt(GearFx.source(p, st.danceStance == MonkCharges.FIRE ? GearFx.FLAME : GearFx.MARTIAL), (float) (unarmedHit(p) * 0.25));
                    switch (st.danceStance) {
                        case MonkCharges.FIRE -> MonkCharges.addBurn(bite, 1);
                        case MonkCharges.WATER -> bite.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0), p);
                        default -> bite.knockback(0.5, p.getX() - bite.getX(), p.getZ() - bite.getZ());
                    }
                    GearFx.line(p, bite, stanceParticle(st.danceStance), 10);
                }
            }
        }

        // Sellos de postura: círculo rúnico que se apaga repitiendo la descarga al 30 %.
        if (!st.seals.isEmpty()) {
            List<Seal> done = new ArrayList<>();
            for (Seal seal : st.seals) {
                if (now % 5 == 0) GearFx.ring(level, seal.pos(), stanceParticle(seal.stance()), 3, 20);
                if (now >= seal.end()) {
                    sealBurst(p, level, seal);
                    done.add(seal);
                }
            }
            st.seals.removeAll(done);
        }
    }

    private static void sealBurst(ServerPlayer p, ServerLevel level, Seal seal) {
        int n = seal.charges();
        switch (seal.stance()) {
            case MonkCharges.FIRE -> {
                float damage = (float) (unarmedHit(p) * n * 0.05);
                for (LivingEntity e : GearFx.enemiesAround(p, seal.pos(), 3)) e.hurt(GearFx.source(p, GearFx.FLAME), damage);
            }
            case MonkCharges.STONE -> {
                if (p.position().distanceToSqr(seal.pos()) <= 9) p.setAbsorptionAmount(p.getAbsorptionAmount() + n * 2f);
            }
            default -> {
                for (LivingEntity e : GearFx.enemiesAround(p, seal.pos(), 3)) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, n * 10, 0), p);
                    e.knockback(n / 6.0, seal.pos().x - e.getX(), seal.pos().z - e.getZ());
                    e.hurtMarked = true;
                }
            }
        }
        for (int r = 1; r <= 3; r++) GearFx.ring(level, seal.pos(), stanceParticle(seal.stance()), r, 12 * r);
        level.playSound(null, BlockPos.containing(seal.pos()), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    /** Uso desde comandos de depuración: describe el set activo. */
    public static String describe(ServerPlayer p) {
        for (GearClass c : GearClass.values()) {
            ArmorTier t = active(p, c);
            if (t != null) return setKey(c, t) + (t.element == null ? "" : " (" + t.element + ")");
        }
        return "ninguno";
    }

    private SetEffects() {}

    /** Instancia solo para el bus de eventos. */
    public static SetEffects listener() {
        return new SetEffects();
    }
}
