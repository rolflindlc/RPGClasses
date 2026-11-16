package io.github.rpgclasses.content.race;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.registry.ModAttributes;
import io.github.rpgclasses.registry.ModEntities;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Formas del Slime (adenda 0.7): <b>Enjambre</b> (3.1) y <b>Mitótico</b> (3.3).
 * <p>
 * <b>Enjambre</b> (tecla racial 1; volver a pulsarla os une): el jugador se divide en un slime pequeño por corazón que
 * le quede (Rango 3, por medio corazón), hasta 25. Él es uno de ellos (se encoge, su vida máxima pasa a ser la de un
 * slime y se dibuja como un slime) y los demás son {@link SwarmSlime} que le siguen. Cada golpe que mata a un slime se
 * pierde entero (sin daño sobrante); si muere el que controla, pasa a otro; si mueren todos, muere. Dividido no usa
 * objetos, habilidades, conjuros ni armadura; solo hace daño por contacto (ignora la invulnerabilidad del objetivo,
 * 1 s por objetivo y slime). La curación de pociones y conjuros crea slimes nuevos; la natural cura a los dañados.
 * Al unirse (o al acabar el tiempo) recupera la vida de los que quedan, sin curarse.
 * <p>
 * <b>Mitótico</b>: un golpe letal de un enemigo, en vez de matarle (tras las habilidades de clase, como el Orco), le
 * divide en dos copias de la mitad de tamaño y de vida máxima, con la vida llena. Controla una ({@link MitosisCopy} es
 * la otra). Si muere una, sigue con la otra; si mueren las dos, muere. Agachado junto a su copia durante 3 s (2 s en
 * Rangos 2–3) se fusionan y recupera su tamaño con la vida del Rango. Si la copia muere, recupera su tamaño al acabar
 * el enfriamiento de la división.
 * <p>
 * Las dos formas se marcan con modificadores del atributo de tamaño (sincronizado): el cliente sabe quién está
 * dividido sin paquetes propios.
 */
public final class SlimeForms {
    public static final UUID SWARM_SCALE_ID = UUID.fromString("3e5b9d1f-2a7c-4c08-b6e4-8f1d0a2c4e01");
    private static final UUID SWARM_HEALTH_ID = UUID.fromString("3e5b9d1f-2a7c-4c08-b6e4-8f1d0a2c4e02");
    private static final UUID SWARM_ARMOR_ID = UUID.fromString("3e5b9d1f-2a7c-4c08-b6e4-8f1d0a2c4e03");
    private static final UUID SWARM_TOUGHNESS_ID = UUID.fromString("3e5b9d1f-2a7c-4c08-b6e4-8f1d0a2c4e04");
    public static final UUID MITOSIS_SCALE_ID = UUID.fromString("3e5b9d1f-2a7c-4c08-b6e4-8f1d0a2c4e05");
    private static final UUID MITOSIS_HEALTH_ID = UUID.fromString("3e5b9d1f-2a7c-4c08-b6e4-8f1d0a2c4e06");
    private static final String SWARM_NAME = "rpgclasses swarm form";
    private static final String MITOSIS_NAME = "rpgclasses mitosis form";
    private static final String SWARM_KEY = Ids.NS + ":race_swarm";
    private static final String MITOSIS_KEY = Ids.NS + ":race_mitosis";
    /** Tamaño total del jugador mientras es un slime pequeño (0,3 × 1,8 ≈ 0,54 de alto, como un slime de tamaño 1). */
    private static final double SWARM_SCALE = 0.3;
    public static final ResourceKey<DamageType> SWARM_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("swarm"));

    private static final class Swarm {
        long end;
        float per;
        float unit;
        float normalMax;
        float healPool;
        final List<SwarmSlime> followers = new ArrayList<>();
        final Map<UUID, Long> touched = new HashMap<>();
    }

    private static final class Mitosis {
        MitosisCopy copy;
        int mergeTicks;
        long regrowAt = Long.MAX_VALUE;
    }

    private static final Map<UUID, Swarm> SWARMS = new HashMap<>();
    private static final Map<UUID, Mitosis> SPLITS = new HashMap<>();

    public SlimeForms() {
        Rites.swarmAlive = SlimeForms::alive;
    }

    // ------------------------------------------------------------------ consultas (cliente y servidor)

    private static boolean hasModifier(Player p, Attribute attribute, UUID id) {
        if (!p.isAddedToWorld()) return false;
        AttributeInstance inst = p.getAttribute(attribute);
        return inst != null && inst.getModifier(id) != null;
    }

    /** true mientras el jugador está dividido en el Enjambre (es un slime pequeño). */
    public static boolean isSwarming(Player p) {
        return hasModifier(p, ModAttributes.SCALE.get(), SWARM_SCALE_ID);
    }

    /** true mientras el Mitótico está dividido (mitad de tamaño). */
    public static boolean isSplit(Player p) {
        return hasModifier(p, ModAttributes.SCALE.get(), MITOSIS_SCALE_ID);
    }

    /** ¿Es esa la copia viva del jugador? (la copia desaparece si deja de serlo). */
    static boolean isCopyOf(ServerPlayer owner, MitosisCopy copy) {
        Mitosis m = SPLITS.get(owner.getUUID());
        return m != null && m.copy == copy;
    }

    /** Slimes vivos del Enjambre, contando el que controla (0 si no está dividido). */
    public static int alive(ServerPlayer p) {
        Swarm s = SWARMS.get(p.getUUID());
        if (s == null) return 0;
        s.followers.removeIf(f -> !f.isAlive());
        return 1 + s.followers.size();
    }

    // ------------------------------------------------------------------ modificadores

    private static void add(Player p, Attribute attribute, UUID id, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = attribute == null ? null : p.getAttribute(attribute);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }

    private static void remove(Player p, Attribute attribute, UUID id) {
        AttributeInstance inst = attribute == null ? null : p.getAttribute(attribute);
        if (inst != null) inst.removeModifier(id);
    }

    /** Valor de un atributo sin uno de sus modificadores (para calcular el que hay que poner). */
    private static double valueWithout(Player p, Attribute attribute, UUID id) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return 0;
        AttributeModifier m = inst.getModifier(id);
        if (m == null) return inst.getValue();
        inst.removeModifier(id);
        double v = inst.getValue();
        inst.addTransientModifier(m);
        return v;
    }

    // ------------------------------------------------------------------ Enjambre

    /** Tecla racial 1 del Enjambre: se divide. */
    static boolean split(ServerPlayer p, ActiveMechanic m) {
        if (isSwarming(p) || isSplit(p) || p.isPassenger() || p.isSleeping()) return false;
        ServerLevel level = p.serverLevel();
        float health = p.getHealth();
        int perHeart = Math.max(1, (int) Math.round(m.number("slimes_per_heart", 1)));
        float unit = 2f / perHeart;
        int max = Math.max(1, (int) m.number("max_slimes", 25));
        int count = Math.max(1, Math.min(max, (int) Math.ceil(health / unit - 1e-4)));
        Swarm s = new Swarm();
        s.end = level.getGameTime() + Math.round(m.number("duration_seconds", 10) * 20);
        s.per = health / count;
        s.unit = unit;
        s.normalMax = p.getMaxHealth();
        // El jugador es uno de los slimes: pequeño, con la vida de un slime y sin armadura.
        double scale = 1 + valueWithout(p, ModAttributes.SCALE.get(), SWARM_SCALE_ID);
        add(p, ModAttributes.SCALE.get(), SWARM_SCALE_ID, SWARM_NAME, SWARM_SCALE - scale, AttributeModifier.Operation.ADDITION);
        setSwarmHealth(p, s.per);
        add(p, Attributes.ARMOR, SWARM_ARMOR_ID, SWARM_NAME, -1, AttributeModifier.Operation.MULTIPLY_TOTAL);
        add(p, Attributes.ARMOR_TOUGHNESS, SWARM_TOUGHNESS_ID, SWARM_NAME, -1, AttributeModifier.Operation.MULTIPLY_TOTAL);
        p.setHealth(s.per);
        p.refreshDimensions();
        SWARMS.put(p.getUUID(), s);
        for (int i = 1; i < count; i++) spawnFollower(p, s, s.per);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2f, 0.8f);
        level.sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + 0.8, p.getZ(), 30, 0.6, 0.6, 0.6, 0.05);
        DebugLog.event(p, "raza", "Enjambre: {} slimes de {} HP", count, s.per);
        return true;
    }

    /** Vida máxima del jugador = la de un slime (multiplicador total sobre su vida máxima normal). */
    private static void setSwarmHealth(ServerPlayer p, float per) {
        double normal = valueWithout(p, Attributes.MAX_HEALTH, SWARM_HEALTH_ID);
        if (normal <= 0) return;
        add(p, Attributes.MAX_HEALTH, SWARM_HEALTH_ID, SWARM_NAME, Math.max(-0.999, per / normal - 1), AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    private static void spawnFollower(ServerPlayer p, Swarm s, float health) {
        SwarmSlime slime = ModEntities.SWARM_SLIME.get().create(p.serverLevel());
        if (slime == null) return;
        double a = p.getRandom().nextDouble() * Math.PI * 2, r = 0.4 + p.getRandom().nextDouble() * 1.1;
        slime.moveTo(p.getX() + Math.cos(a) * r, p.getY() + 0.1, p.getZ() + Math.sin(a) * r, p.getRandom().nextFloat() * 360f, 0);
        // Sin sitio alrededor (un túnel estrecho): sale donde está el jugador.
        if (!p.serverLevel().noCollision(slime)) slime.moveTo(p.getX(), p.getY(), p.getZ(), slime.getYRot(), 0);
        slime.setOwner(p.getUUID());
        AttributeInstance max = slime.getAttribute(Attributes.MAX_HEALTH);
        if (max != null) max.setBaseValue(Math.max(1, health));
        slime.setHealth(health);
        slime.setDeltaMovement((p.getRandom().nextDouble() - 0.5) * 0.3, 0.3, (p.getRandom().nextDouble() - 0.5) * 0.3);
        p.serverLevel().addFreshEntity(slime);
        s.followers.add(slime);
    }

    /** Se une: recupera la vida de los slimes que quedan (sin curarse). El enfriamiento empieza ahora. */
    public static void merge(ServerPlayer p) {
        Swarm s = SWARMS.remove(p.getUUID());
        float total = p.isAlive() ? p.getHealth() : 0;
        int alive = 1;
        if (s != null) {
            for (SwarmSlime f : s.followers) {
                if (!f.isAlive()) continue;
                total += f.getHealth();
                alive++;
                f.vanish();
            }
        }
        remove(p, ModAttributes.SCALE.get(), SWARM_SCALE_ID);
        remove(p, Attributes.MAX_HEALTH, SWARM_HEALTH_ID);
        remove(p, Attributes.ARMOR, SWARM_ARMOR_ID);
        remove(p, Attributes.ARMOR_TOUGHNESS, SWARM_TOUGHNESS_ID);
        p.refreshDimensions();
        if (s == null || !p.isAlive()) return;
        p.setHealth(Math.min(p.getMaxHealth(), Math.max(0.5f, total)));
        ActiveMechanic m = Races.find(p, "race_swarm");
        if (m != null) {
            AbilityService.setCooldown(p, SWARM_KEY, (int) Math.round(m.number("cooldown_seconds", 60) * 20));
            Net.syncPlayer(p);
        }
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.2f, 0.8f);
        p.serverLevel().sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + 0.8, p.getZ(), 30, 0.5, 0.6, 0.5, 0.05);
        DebugLog.event(p, "raza", "Enjambre: se une con {} slimes y {} HP", alive, p.getHealth());
    }

    /** Enemigo válido para el contacto: hostil, jefe, jugador enemigo o quien ataca al dueño o a sus aliados. */
    static boolean isFoe(ServerPlayer owner, LivingEntity e) {
        if (e == owner || !e.isAlive() || e instanceof SwarmSlime || e instanceof ArmorStand || !Allies.isEnemy(owner, e)) return false;
        if (e instanceof Player || e instanceof Enemy || Races.isBoss(e) || Rites.isOwnBoss(owner, e)) return true;
        return e instanceof Mob mob && mob.getTarget() != null && (mob.getTarget() == owner || Allies.isAlly(owner, mob.getTarget()));
    }

    /** Objetivo de un slime: el enemigo más cercano a él, sin alejarse más de 16 bloques de su dueño. */
    static LivingEntity swarmTarget(ServerPlayer owner, SwarmSlime slime) {
        return slime.level().getEntitiesOfClass(LivingEntity.class, slime.getBoundingBox().inflate(8),
                        e -> isFoe(owner, e) && e.distanceToSqr(owner) < 256)
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(slime))).orElse(null);
    }

    static DamageSource swarmDamage(ServerPlayer owner, Entity direct) {
        return new DamageSource(owner.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SWARM_DAMAGE),
                direct, owner);
    }

    /** Daño por contacto de un slime (o del jugador dividido): ignora la invulnerabilidad; 1 s por objetivo. */
    static void touch(ServerPlayer owner, Entity source, AABB box, Map<UUID, Long> cooldowns) {
        long now = owner.level().getGameTime();
        if (cooldowns.size() > 32) cooldowns.values().removeIf(t -> now - t > 40);
        float damage = (float) Races.number(owner, "race_swarm", "touch_damage", 1);
        for (LivingEntity e : source.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != source && isFoe(owner, e))) {
            Long last = cooldowns.get(e.getUUID());
            if (last != null && now - last < 20) continue;
            cooldowns.put(e.getUUID(), now);
            e.invulnerableTime = 0;
            DamageSource src = swarmDamage(owner, source);
            Rites.dealing("swarm", () -> e.hurt(src, damage));
        }
    }

    private static void swarmTick(ServerPlayer p, Swarm s) {
        if (!Races.has(p, "race_swarm") || p.level().getGameTime() >= s.end) {
            merge(p);
            return;
        }
        s.followers.removeIf(f -> !f.isAlive());
        // La vida máxima normal puede cambiar (equipo, rango): se recalcula la de slime.
        if (p.tickCount % 20 == 0) setSwarmHealth(p, s.per);
        // Regeneración natural (como la de vanilla con la barra de comida casi llena): cura a un slime dañado.
        if (p.tickCount % 80 == 0 && p.getFoodData().getFoodLevel() >= 18 && p.getHealth() >= p.getMaxHealth()) {
            for (SwarmSlime f : s.followers) {
                if (f.getHealth() >= f.getMaxHealth()) continue;
                f.heal(1f);
                p.causeFoodExhaustion(6f);
                break;
            }
        }
        touch(p, p, p.getBoundingBox().inflate(0.1), s.touched);
    }

    // ------------------------------------------------------------------ Mitótico

    /** ¿El golpe letal lo causa un enemigo? (mob hostil, jefe, jugador enemigo o quien le tenía como objetivo). */
    private static boolean enemyBlow(ServerPlayer p, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false; // vacío, /kill
        Entity attacker = source.getEntity();
        if (!(attacker instanceof LivingEntity living) || attacker == p) return false;
        if (attacker instanceof Player) return Allies.isEnemy(p, living);
        return attacker instanceof Enemy || Races.isBoss(living) || Rites.isOwnBoss(p, attacker)
                || attacker instanceof Mob mob && mob.getTarget() == p;
    }

    private static void divide(ServerPlayer p, ActiveMechanic m) {
        ServerLevel level = p.serverLevel();
        double scale = 1 + valueWithout(p, ModAttributes.SCALE.get(), MITOSIS_SCALE_ID);
        add(p, ModAttributes.SCALE.get(), MITOSIS_SCALE_ID, MITOSIS_NAME, -scale / 2, AttributeModifier.Operation.ADDITION);
        add(p, Attributes.MAX_HEALTH, MITOSIS_HEALTH_ID, MITOSIS_NAME, -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL);
        p.setHealth(p.getMaxHealth());
        p.refreshDimensions();
        Mitosis st = new Mitosis();
        MitosisCopy copy = ModEntities.MITOSIS_COPY.get().create(level);
        if (copy != null) {
            double a = p.getRandom().nextDouble() * Math.PI * 2;
            copy.setScale(RaceTraits.scale(p));
            copy.moveTo(p.getX() + Math.cos(a) * 0.8, p.getY(), p.getZ() + Math.sin(a) * 0.8, p.getYRot(), 0);
            if (!level.noCollision(copy)) copy.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0);
            copy.setOwner(p.getUUID());
            AttributeInstance max = copy.getAttribute(Attributes.MAX_HEALTH);
            if (max != null) max.setBaseValue(Math.max(1, p.getMaxHealth()));
            copy.setHealth(p.getMaxHealth());
            copy.setCustomName(p.getDisplayName());
            level.addFreshEntity(copy);
            st.copy = copy;
        }
        SPLITS.put(p.getUUID(), st);
        AbilityService.setCooldown(p, MITOSIS_KEY, (int) Math.round(m.number("cooldown_seconds", 300) * 20));
        Net.syncPlayer(p);
        Rites.count(p, "mitosis_split", 1);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.4f, 0.6f);
        level.sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + 0.6, p.getZ(), 40, 0.5, 0.5, 0.5, 0.08);
        p.displayClientMessage(Component.translatable("message.rpgclasses.mitosis_split"), true);
        DebugLog.event(p, "raza", "Mitosis: se divide ({} HP por copia)", p.getMaxHealth());
    }

    /** Vuelve a su tamaño. Con {@code merged} (fusión con la copia viva) la vida pasa a la del Rango. */
    private static void restore(ServerPlayer p, boolean merged) {
        Mitosis st = SPLITS.remove(p.getUUID());
        if (st != null && st.copy != null && st.copy.isAlive()) st.copy.vanish();
        remove(p, ModAttributes.SCALE.get(), MITOSIS_SCALE_ID);
        remove(p, Attributes.MAX_HEALTH, MITOSIS_HEALTH_ID);
        p.refreshDimensions();
        if (!merged || !p.isAlive()) return;
        double frac = Races.number(p, "race_mitosis", "merge_health", 0);
        p.setHealth((float) Math.max(1, Math.min(p.getMaxHealth(), frac * p.getMaxHealth())));
        Rites.count(p, "mitosis_merge", 1);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.2f, 0.7f);
        p.serverLevel().sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + 0.8, p.getZ(), 40, 0.5, 0.6, 0.5, 0.05);
        p.displayClientMessage(Component.translatable("message.rpgclasses.mitosis_merged"), true);
        DebugLog.event(p, "raza", "Mitosis: se fusiona con su copia ({} HP)", p.getHealth());
    }

    /** Ticks agachado junto a la copia necesarios para fusionarse. */
    public static int mergeTicks(Player p) {
        return (int) Math.round(Races.number(p, "race_mitosis", "merge_seconds", 3) * 20);
    }

    private static void mitosisTick(ServerPlayer p, Mitosis st) {
        long now = p.level().getGameTime();
        if (!Races.has(p, "race_mitosis")) {
            restore(p, false);
            return;
        }
        if (st.copy != null && !st.copy.isAlive()) {
            // La copia ha muerto: recupera su tamaño al acabar el enfriamiento de la división.
            st.copy = null;
            st.regrowAt = now + AbilityService.remainingTicks(p, MITOSIS_KEY);
            p.displayClientMessage(Component.translatable("message.rpgclasses.mitosis_copy_lost"), true);
        }
        if (st.copy == null) {
            if (now >= st.regrowAt) restore(p, false);
            return;
        }
        boolean close = p.isShiftKeyDown() && st.copy.level() == p.level() && st.copy.distanceToSqr(p) <= 4;
        st.mergeTicks = close ? st.mergeTicks + 1 : 0;
        if (st.mergeTicks >= mergeTicks(p)) restore(p, true);
    }

    // ------------------------------------------------------------------ daño y curación

    /** Enjambre: si muere el slime que controla, pasa a otro (el daño sobrante se pierde). Antes que todo lo demás. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSwarmDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        Swarm s = SWARMS.get(p.getUUID());
        if (s == null || event.getAmount() < p.getHealth() || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        s.followers.removeIf(f -> !f.isAlive());
        SwarmSlime next = s.followers.stream().filter(f -> f.level() == p.level())
                .min(Comparator.comparingDouble(f -> f.distanceToSqr(p))).orElse(null);
        if (next == null) return; // era el último: muere
        event.setCanceled(true);
        ServerLevel level = p.serverLevel();
        level.sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + 0.3, p.getZ(), 12, 0.2, 0.2, 0.2, 0.05);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SLIME_DEATH_SMALL, SoundSource.PLAYERS, 1f, 1f);
        s.followers.remove(next);
        p.connection.teleport(next.getX(), next.getY(), next.getZ(), p.getYRot(), p.getXRot());
        p.setHealth(Math.min(p.getMaxHealth(), next.getHealth()));
        p.fallDistance = 0;
        next.discard();
        DebugLog.event(p, "raza", "Enjambre: muere el slime que controla; pasa a otro ({} quedan)", s.followers.size() + 1);
    }

    /**
     * Mitótico: el golpe letal de un enemigo le divide; si ya está dividido y su copia vive, pasa a ella. Después de
     * las habilidades de clase y en el mismo sitio que el Orco (el Tótem va después, al morir).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onMitosisDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer || event.getAmount() < p.getHealth()) return;
        ActiveMechanic m = Races.find(p, "race_mitosis");
        if (m == null || SWARMS.containsKey(p.getUUID())) return;
        Mitosis st = SPLITS.get(p.getUUID());
        if (st != null) {
            if (st.copy == null || !st.copy.isAlive() || st.copy.level() != p.level()
                    || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return; // la última copia: muere
            event.setCanceled(true);
            MitosisCopy copy = st.copy;
            p.serverLevel().sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + 0.5, p.getZ(), 20, 0.3, 0.4, 0.3, 0.05);
            p.connection.teleport(copy.getX(), copy.getY(), copy.getZ(), p.getYRot(), p.getXRot());
            p.setHealth(Math.min(p.getMaxHealth(), copy.getHealth()));
            p.fallDistance = 0;
            st.copy = null;
            st.regrowAt = p.level().getGameTime() + AbilityService.remainingTicks(p, MITOSIS_KEY);
            copy.vanish();
            p.displayClientMessage(Component.translatable("message.rpgclasses.mitosis_copy_lost"), true);
            DebugLog.event(p, "raza", "Mitosis: muere una copia; sigue con la otra");
            return;
        }
        if (!enemyBlow(p, event.getSource()) || AbilityService.remainingTicks(p, MITOSIS_KEY) > 0) return;
        event.setCanceled(true);
        divide(p, m);
    }

    /** Enjambre: la curación de pociones y conjuros crea slimes; la natural cura a los slimes dañados. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Swarm s = SWARMS.get(p.getUUID());
        if (s == null) return;
        float amount = event.getAmount();
        event.setCanceled(true);
        if (amount <= 0) return;
        s.followers.removeIf(f -> !f.isAlive());
        if (HealSources.potionOrSpell(p)) {
            s.healPool += amount;
            int max = (int) Races.number(p, "race_swarm", "max_slimes", 25);
            while (s.healPool >= s.unit && 1 + s.followers.size() < max && total(p, s) + s.unit <= s.normalMax + 1e-3) {
                s.healPool -= s.unit;
                spawnFollower(p, s, s.unit);
            }
            return;
        }
        float left = amount;
        float gain = Math.min(left, p.getMaxHealth() - p.getHealth());
        if (gain > 0) {
            p.setHealth(p.getHealth() + gain);
            left -= gain;
        }
        for (SwarmSlime f : s.followers) {
            if (left <= 0) break;
            float g = Math.min(left, f.getMaxHealth() - f.getHealth());
            if (g <= 0) continue;
            f.setHealth(f.getHealth() + g);
            left -= g;
        }
    }

    private static float total(ServerPlayer p, Swarm s) {
        float t = p.getHealth();
        for (SwarmSlime f : s.followers) t += f.getHealth();
        return t;
    }

    // ------------------------------------------------------------------ dividido: sin objetos, ataques ni habilidades

    /** El Enjambre dividido solo hace daño por contacto: nada de objetos, bloques, ataques, puertas ni cofres. */
    public static boolean blocksActions(Player p) {
        return isSwarming(p);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (blocksActions(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (blocksActions(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (blocksActions(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (blocksActions(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (blocksActions(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(AttackEntityEvent event) {
        if (blocksActions(event.getEntity())) event.setCanceled(true);
    }

    /** Tampoco hace daño de otra forma (Epic Fight, efectos, proyectiles): solo el contacto del Enjambre. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttackDamage(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer p && isSwarming(p) && !event.getSource().is(SWARM_DAMAGE)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPickup(EntityItemPickupEvent event) {
        if (blocksActions(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (blocksActions(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (blocksActions(event.getPlayer())) event.setCanceled(true);
    }

    // ------------------------------------------------------------------ tick, muerte, salida

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p instanceof FakePlayer || !p.isAlive()) return;
        Swarm s = SWARMS.get(p.getUUID());
        if (s != null) DebugLog.guard(p, "Enjambre", () -> swarmTick(p, s));
        Mitosis st = SPLITS.get(p.getUUID());
        if (st != null) DebugLog.guard(p, "Mitosis", () -> mitosisTick(p, st));
        // Restos de una forma que no se cerró (no debería pasar): se quitan.
        if (p.tickCount % 20 == 0) {
            if (s == null && isSwarming(p)) merge(p);
            if (st == null && isSplit(p)) restore(p, false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Swarm s = SWARMS.remove(p.getUUID());
        if (s != null) for (SwarmSlime f : s.followers) if (f.isAlive()) f.vanish();
        Mitosis st = SPLITS.remove(p.getUUID());
        if (st != null && st.copy != null && st.copy.isAlive()) st.copy.vanish();
    }

    /** Cambiar de dimensión une el Enjambre y deja atrás a la copia (que desaparece). */
    @SubscribeEvent
    public void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (SWARMS.containsKey(p.getUUID())) merge(p);
        Mitosis st = SPLITS.get(p.getUUID());
        if (st != null && st.copy != null) {
            st.copy.vanish();
            st.copy = null;
            st.regrowAt = p.level().getGameTime() + AbilityService.remainingTicks(p, MITOSIS_KEY);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (SWARMS.containsKey(p.getUUID())) merge(p);
        if (SPLITS.containsKey(p.getUUID())) restore(p, false);
    }

    @SubscribeEvent
    public void onStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            if (SWARMS.containsKey(p.getUUID())) merge(p);
            if (SPLITS.containsKey(p.getUUID())) restore(p, false);
        }
    }
}
