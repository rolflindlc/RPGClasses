package io.github.rpgclasses.content;

import io.github.rpgclasses.registry.ModEffects;
import io.github.rpgclasses.server.Allies;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Magia Caótica del Evocador Caótico (rev. 2.18, sustituye a la Erupción de Magia Salvaje).
 * <p>
 * De vez en cuando, al lanzar un conjuro, salta un <b>efecto aleatorio</b>: casi siempre bueno para el mago
 * (velocidad, saltos enormes, volverse gallina y volar, lluvia de meteoros, rayos, colmillos, un segundo conjuro...),
 * a veces raro (intercambiar posiciones, teletransportes) y a veces malo (lentitud, náuseas, retroceso).
 * Además, el daño de todos sus conjuros varía al azar ({@code variance_min}–{@code variance_max}).
 * <p>
 * Esta clase solo usa la API de Minecraft. Lo que necesita Iron's (repetir el conjuro, lanzar otro de la lista
 * vistosa, tocar el maná) lo hace el puente {@link Hooks}, que instala {@code IronsCompat}.
 */
public final class ChaosMagic {
    /** Puente con Iron's Spells; null si no está instalado (esos efectos no salen). */
    public interface Hooks {
        /** Repite el conjuro que acaba de lanzar (un segundo conjuro). */
        boolean recast(ServerPlayer player, String spellId, int spellLevel);
        /** Lanza un conjuro vistoso al azar de la lista del mod. */
        boolean castShowy(ServerPlayer player, int spellLevel);
        /** Suma (o resta, si es negativo) una fracción del maná máximo. */
        void addMana(ServerPlayer player, double fractionOfMax);
    }

    public static Hooks hooks;

    private enum Kind { GOOD, ODD, BAD }

    private interface Run { boolean apply(Ctx c); }

    private record Effect(String id, Kind kind, int weight, boolean needsEnemies, Run run) {}

    private record Ctx(ServerPlayer p, ServerLevel level, String spellId, int spellLevel, List<LivingEntity> enemies) {
        RandomSource rng() { return p.getRandom(); }
    }

    /** Radio en el que se buscan enemigos para los efectos de área. */
    private static final double RADIUS = 12.0;
    private static final String TAG_FLIGHT = "rpgclasses.chicken_flight";
    private static final int CHICKEN_TICKS = 20 * 8;

    /** Jugadores convertidos en gallina: tick de juego en que acaba. */
    private static final Map<UUID, Long> CHICKENS = new HashMap<>();

    private static final List<Effect> EFFECTS = new ArrayList<>();

    private static void add(String id, Kind kind, int weight, boolean needsEnemies, Run run) {
        EFFECTS.add(new Effect(id, kind, weight, needsEnemies, run));
    }

    static {
        // ------------------------------------------------------------ buenos (para el lanzador)
        add("chicken", Kind.GOOD, 8, false, c -> { chicken(c.p); return true; });
        add("speed_50", Kind.GOOD, 6, false, c -> {
            c.p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 3, 49, false, true, true));
            c.p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 5, 0, false, false, true));
            burst(c, ParticleTypes.CLOUD, 25);
            return true;
        });
        add("jump_15", Kind.GOOD, 6, false, c -> {
            c.p.addEffect(new MobEffectInstance(MobEffects.JUMP, 20 * 6, 14, false, true, true));
            c.p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 12, 0, false, false, true));
            burst(c, ParticleTypes.CLOUD, 25);
            return true;
        });
        add("fury", Kind.GOOD, 6, false, c -> {
            c.p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 8, 1, false, true, true));
            c.p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 20 * 8, 1, false, true, true));
            burst(c, ParticleTypes.ANGRY_VILLAGER, 8);
            return true;
        });
        add("guard", Kind.GOOD, 6, false, c -> {
            c.p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 12, 3, false, true, true));
            c.p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 4, 1, false, true, true));
            burst(c, ParticleTypes.ENCHANT, 40);
            return true;
        });
        add("heal", Kind.GOOD, 6, false, c -> {
            c.p.heal(c.p.getMaxHealth() * 0.3f);
            c.p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 3, 1, false, true, true));
            burst(c, ParticleTypes.HEART, 8);
            return true;
        });
        add("cleanse", Kind.GOOD, 4, false, c -> {
            List<MobEffect> harmful = new ArrayList<>();
            for (MobEffectInstance e : c.p.getActiveEffects()) if (e.getEffect().getCategory() == MobEffectCategory.HARMFUL) harmful.add(e.getEffect());
            for (MobEffect e : harmful) c.p.removeEffect(e);
            c.p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 8, 1, false, true, true));
            burst(c, ParticleTypes.END_ROD, 30);
            return true;
        });
        add("mana", Kind.GOOD, 6, false, c -> {
            if (hooks == null) return false;
            hooks.addMana(c.p, 0.35);
            burst(c, ParticleTypes.ENCHANT, 40);
            return true;
        });
        add("vanish", Kind.GOOD, 4, false, c -> {
            c.p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20 * 5, 0, false, false, true));
            c.p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 5, 1, false, true, true));
            burst(c, ParticleTypes.POOF, 25);
            return true;
        });
        add("second_cast", Kind.GOOD, 9, false, c -> hooks != null && hooks.recast(c.p, c.spellId, c.spellLevel));
        add("showy_spell", Kind.GOOD, 9, false, c -> hooks != null && hooks.castShowy(c.p, c.spellLevel));

        // ------------------------------------------------------------ buenos (contra enemigos)
        add("meteors", Kind.GOOD, 7, true, ChaosMagic::meteors);
        add("lightning", Kind.GOOD, 6, true, ChaosMagic::lightning);
        add("fangs", Kind.GOOD, 6, true, ChaosMagic::fangs);
        add("shockwave", Kind.GOOD, 5, true, ChaosMagic::shockwave);
        add("zero_gravity", Kind.GOOD, 4, true, c -> {
            for (LivingEntity e : first(c.enemies, 5)) {
                e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20 * 3, 1, false, true, true));
                puff(c.level, e, ParticleTypes.END_ROD, 12);
            }
            return true;
        });
        add("hex", Kind.GOOD, 4, true, c -> {
            for (LivingEntity e : first(c.enemies, 6)) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 4, 2, false, true, true));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 5, 0, false, true, true));
                puff(c.level, e, ParticleTypes.WITCH, 14);
            }
            return true;
        });
        add("scatter", Kind.GOOD, 4, true, c -> {
            int moved = 0;
            for (LivingEntity e : first(c.enemies, 3)) if (scatter(c, e, 8)) moved++;
            return moved > 0;
        });
        add("fireworks", Kind.GOOD, 3, false, c -> {
            c.level.playSound(null, c.p.getX(), c.p.getY(), c.p.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.2f, 1.0f);
            c.level.sendParticles(ParticleTypes.FIREWORK, c.p.getX(), c.p.getY() + 2.5, c.p.getZ(), 80, 1.2, 1.0, 1.2, 0.15);
            for (LivingEntity e : c.enemies) e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 8, 0, false, false, true));
            c.p.heal(2.0f);
            return true;
        });

        // ------------------------------------------------------------ raros
        add("blink", Kind.ODD, 4, false, c -> {
            Vec3 from = c.p.position();
            for (int i = 0; i < 10; i++) {
                double a = c.rng().nextDouble() * Math.PI * 2, d = 5 + c.rng().nextDouble() * 5;
                if (c.p.randomTeleport(from.x + Math.cos(a) * d, from.y, from.z + Math.sin(a) * d, true)) {
                    c.p.fallDistance = 0;
                    c.level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1, from.z, 30, 0.3, 0.6, 0.3, 0.2);
                    c.level.sendParticles(ParticleTypes.PORTAL, c.p.getX(), c.p.getY() + 1, c.p.getZ(), 30, 0.3, 0.6, 0.3, 0.2);
                    c.level.playSound(null, c.p.getX(), c.p.getY(), c.p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.1f);
                    return true;
                }
            }
            return false;
        });
        add("swap_enemy", Kind.ODD, 4, true, c -> {
            LivingEntity e = c.enemies.get(0);
            Vec3 a = c.p.position(), b = e.position();
            tp(c.p, b);
            tp(e, a);
            swapFx(c, a, b);
            return true;
        });
        add("swap_enemies", Kind.ODD, 4, true, c -> {
            if (c.enemies.size() < 2) return false;
            int i = c.rng().nextInt(c.enemies.size()), j = (i + 1 + c.rng().nextInt(c.enemies.size() - 1)) % c.enemies.size();
            LivingEntity x = c.enemies.get(i), y = c.enemies.get(j);
            Vec3 a = x.position(), b = y.position();
            tp(x, b);
            tp(y, a);
            swapFx(c, a, b);
            return true;
        });
        add("pull", Kind.ODD, 3, true, c -> {
            for (LivingEntity e : first(c.enemies, 6)) {
                Vec3 dir = c.p.position().subtract(e.position()).normalize().scale(1.4);
                e.setDeltaMovement(dir.x, 0.3, dir.z);
                e.hurtMarked = true;
                puff(c.level, e, ParticleTypes.PORTAL, 10);
            }
            return true;
        });

        add("rabbit", Kind.ODD, 3, false, c -> { rabbit(c.p); return true; });
        add("levitate_all", Kind.ODD, 3, false, c -> {
            int n = 0;
            for (LivingEntity e : around(c, 8)) {
                e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 50, 0, false, false, true));
                e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 8, 0, false, false, true));
                puff(c.level, e, ParticleTypes.END_ROD, 10);
                n++;
            }
            c.level.playSound(null, c.p.getX(), c.p.getY(), c.p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9f, 1.6f);
            return n > 0;
        });
        add("glow_all", Kind.ODD, 3, false, c -> {
            for (LivingEntity e : around(c, 12)) {
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 10, 0, false, false, true));
            }
            c.level.sendParticles(ParticleTypes.FIREWORK, c.p.getX(), c.p.getY() + 1.2, c.p.getZ(), 60, 3.0, 1.0, 3.0, 0.08);
            c.level.playSound(null, c.p.getX(), c.p.getY(), c.p.getZ(), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 1.2f, 1.0f);
            return true;
        });
        add("time_stop", Kind.ODD, 2, true, c -> {
            for (LivingEntity e : first(c.enemies, 8)) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 3, 6, false, true, true));
                e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20 * 3, 4, false, false, true));
                puff(c.level, e, ParticleTypes.END_ROD, 12);
                c.level.sendParticles(ParticleTypes.ENCHANT, e.getX(), e.getY() + e.getBbHeight() + 0.4, e.getZ(), 10, 0.3, 0.2, 0.3, 0.3);
            }
            c.level.playSound(null, c.p.getX(), c.p.getY(), c.p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 0.5f);
            return true;
        });
        add("popcorn", Kind.ODD, 2, true, c -> {
            for (LivingEntity e : first(c.enemies, 8)) {
                e.setDeltaMovement((c.rng().nextDouble() - 0.5) * 0.4, 0.75 + c.rng().nextDouble() * 0.35, (c.rng().nextDouble() - 0.5) * 0.4);
                e.hurtMarked = true;
                puff(c.level, e, ParticleTypes.NOTE, 4);
            }
            c.level.playSound(null, c.p.getX(), c.p.getY(), c.p.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.2f, 1.4f);
            return true;
        });
        add("random_potion", Kind.ODD, 3, false, c -> {
            MobEffect[] pool = {MobEffects.MOVEMENT_SPEED, MobEffects.JUMP, MobEffects.REGENERATION, MobEffects.DAMAGE_BOOST,
                    MobEffects.FIRE_RESISTANCE, MobEffects.NIGHT_VISION, MobEffects.HEALTH_BOOST, MobEffects.GLOWING,
                    MobEffects.INVISIBILITY, MobEffects.LEVITATION, MobEffects.SLOW_FALLING, MobEffects.DIG_SPEED};
            int a = c.rng().nextInt(pool.length), b = (a + 1 + c.rng().nextInt(pool.length - 1)) % pool.length;
            for (int i : new int[] {a, b}) {
                boolean brief = pool[i] == MobEffects.LEVITATION;
                c.p.addEffect(new MobEffectInstance(pool[i], brief ? 30 : 20 * 10, pool[i] == MobEffects.JUMP ? 3 : 1, false, true, true));
            }
            burst(c, ParticleTypes.WITCH, 30);
            return true;
        });
        add("rotate", Kind.ODD, 2, true, c -> {
            List<LivingEntity> ring = new ArrayList<>(first(c.enemies, 6));
            if (ring.size() < 3) return false;
            List<Vec3> spots = new ArrayList<>();
            for (LivingEntity e : ring) spots.add(e.position());
            for (int i = 0; i < ring.size(); i++) {
                Vec3 to = spots.get((i + 1) % ring.size());
                swapFx(c, ring.get(i).position(), to);
                tp(ring.get(i), to);
            }
            return true;
        });
        add("cannon", Kind.ODD, 2, false, c -> {
            Vec3 look = c.p.getLookAngle();
            c.p.setDeltaMovement(look.x * 1.9, Math.max(0.5, look.y * 1.9 + 0.4), look.z * 1.9);
            c.p.hurtMarked = true;
            c.p.fallDistance = 0;
            c.p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 8, 0, false, false, true));
            c.level.sendParticles(ParticleTypes.FIREWORK, c.p.getX(), c.p.getY() + 0.5, c.p.getZ(), 40, 0.3, 0.3, 0.3, 0.15);
            c.level.playSound(null, c.p.getX(), c.p.getY(), c.p.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.2f, 0.8f);
            return true;
        });
        add("moon_gravity", Kind.ODD, 2, false, c -> {
            for (LivingEntity e : around(c, 10)) {
                e.addEffect(new MobEffectInstance(MobEffects.JUMP, 20 * 10, 2, false, false, true));
                e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 10, 0, false, false, true));
                puff(c.level, e, ParticleTypes.CLOUD, 6);
            }
            c.level.playSound(null, c.p.getX(), c.p.getY(), c.p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.6f);
            return true;
        });

        // ------------------------------------------------------------ malos (para el lanzador)
        add("slow_feet", Kind.BAD, 4, false, c -> {
            c.p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 3, 1, false, true, true));
            burst(c, ParticleTypes.SMOKE, 15);
            return true;
        });
        add("hiccups", Kind.BAD, 3, false, c -> {
            c.p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20 * 4, 0, false, false, true));
            return true;
        });
        add("backfire", Kind.BAD, 3, false, c -> {
            if (c.p.getHealth() <= 8.0f) return false; // nunca mata
            c.p.hurt(c.level.damageSources().magic(), 2.0f);
            Vec3 back = c.p.getLookAngle().scale(-1.2);
            c.p.setDeltaMovement(back.x, 0.35, back.z);
            c.p.hurtMarked = true;
            burst(c, ParticleTypes.EXPLOSION, 3);
            return true;
        });
        add("mana_leak", Kind.BAD, 3, false, c -> {
            if (hooks == null) return false;
            hooks.addMana(c.p, -0.2);
            burst(c, ParticleTypes.SMOKE, 15);
            return true;
        });
        add("glow", Kind.BAD, 3, false, c -> {
            c.p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 6, 0, false, false, true));
            return true;
        });
        add("weightless", Kind.BAD, 3, false, c -> {
            c.p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 0, false, true, true));
            c.p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 6, 0, false, false, true));
            burst(c, ParticleTypes.END_ROD, 15);
            return true;
        });
        add("weak", Kind.BAD, 3, false, c -> {
            c.p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 5, 0, false, true, true));
            return true;
        });
    }

    /** Número de efectos del catálogo (para registros y pruebas). */
    public static int effectCount() {
        return EFFECTS.size();
    }

    // =========================================================================== tirada

    /**
     * Saca un efecto al azar y lo aplica. Si el elegido no se puede (no hay enemigos cerca, Iron's no puede lanzar
     * el conjuro...) se vuelve a tirar unas pocas veces para que el jugador casi siempre vea algo.
     */
    public static void roll(ServerPlayer player, String spellId, int spellLevel) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) return;
        List<LivingEntity> enemies = enemies(player, level);
        Ctx ctx = new Ctx(player, level, spellId, Math.max(1, spellLevel), enemies);
        for (int attempt = 0; attempt < 6; attempt++) {
            Effect pick = choose(player.getRandom(), enemies.isEmpty());
            if (pick == null) return;
            boolean ok;
            try {
                ok = pick.run().apply(ctx);
            } catch (RuntimeException e) {
                io.github.rpgclasses.RpgClasses.LOGGER.warn("[rpgclasses] Magia Caótica: fallo en el efecto {}", pick.id(), e);
                ok = false;
            }
            io.github.rpgclasses.server.DebugLog.event(player, "Magia Caótica", "efecto {} ({}): {}", pick.id(), pick.kind(), ok ? "aplicado" : "no se pudo");
            if (!ok) continue;
            ChatFormatting color = switch (pick.kind()) { case GOOD -> ChatFormatting.GREEN; case ODD -> ChatFormatting.AQUA; case BAD -> ChatFormatting.RED; };
            player.displayClientMessage(Component.translatable("message.rpgclasses.chaos",
                    Component.translatable("chaos.rpgclasses." + pick.id()).withStyle(color)), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 0.6f + player.getRandom().nextFloat() * 0.8f);
            return;
        }
    }

    private static Effect choose(RandomSource rng, boolean noEnemies) {
        int total = 0;
        for (Effect e : EFFECTS) if (!(noEnemies && e.needsEnemies())) total += e.weight();
        if (total <= 0) return null;
        int roll = rng.nextInt(total);
        for (Effect e : EFFECTS) {
            if (noEnemies && e.needsEnemies()) continue;
            roll -= e.weight();
            if (roll < 0) return e;
        }
        return null;
    }

    /** Enemigos válidos cerca del mago, del más cercano al más lejano. */
    private static List<LivingEntity> enemies(ServerPlayer p, ServerLevel level) {
        List<LivingEntity> list = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(RADIUS),
                e -> e != p && e.isAlive() && !e.isInvulnerable() && Allies.isEnemy(p, e) && p.hasLineOfSight(e)));
        list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        return list;
    }

    private static List<LivingEntity> first(List<LivingEntity> list, int n) {
        return list.subList(0, Math.min(n, list.size()));
    }

    // =========================================================================== efectos de área

    private static boolean meteors(Ctx c) {
        float damage = 5.0f + 0.5f * Math.min(c.spellLevel, 10);
        for (LivingEntity e : first(c.enemies, 4)) {
            for (int i = 0; i < 14; i++) {
                c.level.sendParticles(ParticleTypes.FLAME, e.getX(), e.getY() + 2 + i * 0.9, e.getZ(), 4, 0.15, 0.15, 0.15, 0.0);
            }
            c.level.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY() + 0.5, e.getZ(), 2, 0.4, 0.3, 0.4, 0.0);
            c.level.sendParticles(ParticleTypes.LAVA, e.getX(), e.getY() + 0.5, e.getZ(), 14, 0.5, 0.3, 0.5, 0.0);
            c.level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7f, 1.3f);
            e.hurt(c.level.damageSources().indirectMagic(c.p, c.p), damage);
            e.setSecondsOnFire(3);
        }
        return true;
    }

    private static boolean lightning(Ctx c) {
        for (LivingEntity e : first(c.enemies, 3)) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(c.level);
            if (bolt != null) {
                bolt.moveTo(e.getX(), e.getY(), e.getZ());
                bolt.setVisualOnly(true); // sin fuego ni daño propio: el daño lo pone el mod
                c.level.addFreshEntity(bolt);
            }
            e.hurt(c.level.damageSources().indirectMagic(c.p, c.p), 8.0f + 0.5f * Math.min(c.spellLevel, 10));
        }
        return true;
    }

    private static boolean fangs(Ctx c) {
        int made = 0;
        for (LivingEntity e : first(c.enemies, 4)) {
            for (int i = 0; i < 3; i++) {
                double a = i * Math.PI * 2 / 3;
                c.level.addFreshEntity(new net.minecraft.world.entity.projectile.EvokerFangs(c.level,
                        e.getX() + Math.cos(a) * 0.9, e.getY(), e.getZ() + Math.sin(a) * 0.9, (float) a, i * 3, c.p));
                made++;
            }
        }
        return made > 0;
    }

    private static boolean shockwave(Ctx c) {
        for (LivingEntity e : c.enemies) {
            if (e.distanceTo(c.p) > 6.5) continue;
            Vec3 dir = e.position().subtract(c.p.position()).multiply(1, 0, 1).normalize().scale(1.6);
            e.setDeltaMovement(dir.x, 0.45, dir.z);
            e.hurtMarked = true;
            e.hurt(c.level.damageSources().indirectMagic(c.p, c.p), 4.0f);
        }
        c.level.sendParticles(ParticleTypes.EXPLOSION, c.p.getX(), c.p.getY() + 0.3, c.p.getZ(), 6, 2.5, 0.1, 2.5, 0.0);
        c.level.sendParticles(ParticleTypes.CLOUD, c.p.getX(), c.p.getY() + 0.3, c.p.getZ(), 40, 3.0, 0.1, 3.0, 0.1);
        c.level.playSound(null, c.p.getX(), c.p.getY(), c.p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6f, 1.6f);
        return true;
    }

    private static boolean scatter(Ctx c, LivingEntity e, double radius) {
        Vec3 from = e.position();
        for (int i = 0; i < 8; i++) {
            double a = c.rng().nextDouble() * Math.PI * 2, d = 4 + c.rng().nextDouble() * (radius - 4);
            if (e.randomTeleport(from.x + Math.cos(a) * d, from.y, from.z + Math.sin(a) * d, true)) {
                c.level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1, from.z, 20, 0.3, 0.6, 0.3, 0.2);
                return true;
            }
        }
        return false;
    }

    // =========================================================================== utilidades

    private static void tp(Entity e, Vec3 to) {
        if (e instanceof ServerPlayer sp) {
            sp.teleportTo(to.x, to.y, to.z);
        } else {
            e.moveTo(to.x, to.y, to.z, e.getYRot(), e.getXRot());
            if (e instanceof net.minecraft.world.entity.Mob mob) mob.getNavigation().stop();
        }
        e.fallDistance = 0;
    }

    private static void swapFx(Ctx c, Vec3 a, Vec3 b) {
        c.level.sendParticles(ParticleTypes.REVERSE_PORTAL, a.x, a.y + 1, a.z, 30, 0.3, 0.6, 0.3, 0.2);
        c.level.sendParticles(ParticleTypes.REVERSE_PORTAL, b.x, b.y + 1, b.z, 30, 0.3, 0.6, 0.3, 0.2);
        c.level.playSound(null, a.x, a.y, a.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.9f);
    }

    private static void burst(Ctx c, ParticleOptions type, int count) {
        c.level.sendParticles(type, c.p.getX(), c.p.getY() + 1.0, c.p.getZ(), count, 0.5, 0.7, 0.5, 0.05);
    }

    private static void puff(ServerLevel level, Entity e, ParticleOptions type, int count) {
        level.sendParticles(type, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), count, 0.4, 0.5, 0.4, 0.05);
    }

    /** El mago y todos los seres vivos a {@code radius} bloques (aliados, enemigos y mascotas), máximo 24. */
    private static List<LivingEntity> around(Ctx c, double radius) {
        List<LivingEntity> list = new ArrayList<>(c.level.getEntitiesOfClass(LivingEntity.class, c.p.getBoundingBox().inflate(radius),
                e -> e.isAlive() && e.distanceToSqr(c.p) <= radius * radius));
        list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(c.p)));
        if (!list.contains(c.p)) list.add(0, c.p);
        return list.subList(0, Math.min(24, list.size()));
    }

    // =========================================================================== conejo

    private static final int RABBIT_TICKS = 20 * 8;

    /** Conejo: 8 s en los que solo puede correr y saltar (sin atacar ni usar objetos ni lanzar conjuros). */
    private static void rabbit(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(ModEffects.RABBIT.get(), RABBIT_TICKS, 0, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, RABBIT_TICKS, 4, false, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.JUMP, RABBIT_TICKS, 4, false, false, false));
        p.stopUsingItem();
        p.serverLevel().sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.8, p.getZ(), 25, 0.4, 0.5, 0.4, 0.05);
        p.serverLevel().sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.6, p.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RABBIT_JUMP, SoundSource.PLAYERS, 1.4f, 1.3f);
    }

    @SubscribeEvent
    public void onRabbitAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (event.getEntity().hasEffect(ModEffects.RABBIT.get())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRabbitUse(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player pl && pl.hasEffect(ModEffects.RABBIT.get())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRabbitInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity().hasEffect(ModEffects.RABBIT.get())) event.setCanceled(true);
    }

    // =========================================================================== gallina

    /** Gallina: 8 s con caída lenta y vuelo (aleteo). El dibujo de gallina lo pone el cliente al ver el efecto. */
    private static void chicken(ServerPlayer p) {
        long until = p.serverLevel().getGameTime() + CHICKEN_TICKS;
        CHICKENS.put(p.getUUID(), until);
        p.addEffect(new MobEffectInstance(ModEffects.CHICKEN.get(), CHICKEN_TICKS, 0, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, CHICKEN_TICKS + 40, 0, false, false, false));
        if (!p.isCreative() && !p.isSpectator() && !p.getAbilities().mayfly) {
            p.getAbilities().mayfly = true;
            p.getAbilities().setFlyingSpeed(0.025f);
            p.onUpdateAbilities();
            p.getPersistentData().putBoolean(TAG_FLIGHT, true);
        }
        p.serverLevel().sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 1, p.getZ(), 25, 0.4, 0.6, 0.4, 0.05);
        p.serverLevel().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 10, 0.4, 0.6, 0.4, 0.05);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CHICKEN_AMBIENT, SoundSource.PLAYERS, 1.4f, 1.0f);
    }

    private static void endChicken(ServerPlayer p) {
        CHICKENS.remove(p.getUUID());
        p.removeEffect(ModEffects.CHICKEN.get());
        if (p.getPersistentData().getBoolean(TAG_FLIGHT)) {
            p.getPersistentData().remove(TAG_FLIGHT);
            if (!p.isCreative() && !p.isSpectator()) {
                p.getAbilities().mayfly = false;
                p.getAbilities().flying = false;
                p.getAbilities().setFlyingSpeed(0.05f);
                p.onUpdateAbilities();
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || CHICKENS.isEmpty()) return;
        var server = event.getServer();
        for (UUID id : new ArrayList<>(CHICKENS.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) { CHICKENS.remove(id); continue; }
            long now = p.serverLevel().getGameTime();
            if (!p.isAlive() || now >= CHICKENS.get(id)) { endChicken(p); continue; }
            p.fallDistance = 0;
            if (now % 40 == 0 && p.getRandom().nextFloat() < 0.35f) { // cacarea y pone algún huevo
                p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1.0f, 1.0f);
                p.spawnAtLocation(Items.EGG);
            }
        }
    }

    /** Si el servidor se cerró mientras era gallina, se le quita el vuelo al volver. */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.getPersistentData().getBoolean(TAG_FLIGHT)) return;
        endChicken(p);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && CHICKENS.containsKey(p.getUUID())) endChicken(p);
    }
}
