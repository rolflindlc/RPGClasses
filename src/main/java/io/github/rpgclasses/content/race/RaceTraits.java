package io.github.rpgclasses.content.race;

import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.registry.ModAttributes;
import io.github.rpgclasses.server.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Rasgos de Rango 0 de las ocho razas (anexo de razas, sección 3). Los números salen de races/*.json.
 * <ul>
 *   <li>Tamaño (Orco, Enano, Goblin): atributo {@code rpgclasses:scale}; la hitbox la escala {@code PlayerSizeMixin}
 *       en todas las poses y aquí solo se ajusta la altura de los ojos.</li>
 *   <li>Orco: hambre extra y Última Resistencia. Enano: Minero. Goblin: Saqueo. Elfo: sin carne ni pescado.</li>
 *   <li>Tritón: respiración, minería y visión bajo el agua, daño de fuego extra.</li>
 *   <li>Infernal: inmune al fuego, golems hostiles, aldeanos más caros.</li>
 *   <li>No-muerto: sol, hambre fija, regeneración que se corta al recibir daño.</li>
 * </ul>
 */
public final class RaceTraits {
    public static final String LAST_STAND = "race_last_stand";
    private static final String LAST_STAND_COUNTER = "race_last_stand_used";
    private static final int NIGHT_VISION_TICKS = 420;

    private static final Map<Player, Float> LAST_SCALE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<UUID, Float> LAST_EXHAUSTION = new HashMap<>();
    private static final Map<UUID, Long> INVULNERABLE_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> REGEN_PAUSED_UNTIL = new HashMap<>();
    private static final Set<UUID> NIGHT_VISION = new HashSet<>();
    /** Segundos seguidos del Infernal en el agua. */
    private static final Map<UUID, Integer> WET_SECONDS = new HashMap<>();

    // ------------------------------------------------------------------ tamaño

    /** Factor de tamaño del jugador (1 = normal). Válido en cliente y servidor: el atributo está sincronizado. */
    public static float scale(Player player) {
        if (!player.isAddedToWorld()) return 1f; // durante la construcción aún no hay atributos
        return (float) Math.max(0.1, 1 + ModAttributes.value(player, ModAttributes.SCALE));
    }

    /** Centauro (adenda 0.7): hitbox de caballo, 1,4 de ancho. */
    public static final float CENTAUR_WIDTH = 1.4f;

    public static boolean isCentaur(Player player) {
        return player.isAddedToWorld() && ModAttributes.value(player, ModAttributes.BODY) == ModAttributes.BODY_CENTAUR;
    }

    public static float centaurHeight(net.minecraft.world.entity.Pose pose) {
        return switch (pose) {
            case CROUCHING -> 1.45f;
            case SWIMMING, FALL_FLYING, SPIN_ATTACK -> 1.0f;
            default -> 1.6f;
        };
    }

    /** La hitbox ya viene escalada de {@code PlayerSizeMixin}; aquí solo se escala la altura de los ojos. */
    @SubscribeEvent
    public void onSize(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getPose() != net.minecraft.world.entity.Pose.SLEEPING && isCentaur(player)) {
            // El torso humano sobresale algo por encima de la hitbox (ojos a 1,5).
            event.setNewEyeHeight(centaurHeight(event.getPose()) * 0.94f);
            return;
        }
        float s = scale(player);
        if (s == 1f) return;
        event.setNewEyeHeight(event.getNewEyeHeight() * s);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        float s = scale(player) + (isCentaur(player) ? 100f : 0f); // el cambio de cuerpo también refresca la hitbox
        Float last = LAST_SCALE.put(player, s);
        if (last == null ? s != 1f : last != s) player.refreshDimensions();
        if (player instanceof ServerPlayer sp && !(sp instanceof FakePlayer) && sp.isAlive()) {
            DebugLog.guard(sp, "raza", () -> serverTick(sp));
        }
    }

    // ------------------------------------------------------------------ tick de servidor

    private static void serverTick(ServerPlayer p) {
        UUID id = p.getUUID();
        long now = p.level().getGameTime();
        boolean second = p.tickCount % 20 == 0;
        FoodData food = p.getFoodData();

        // Elfo Silvano (R2): Regeneración en biomas de bosque.
        if (second) {
            ActiveMechanic forest = Races.find(p, "race_forest_regen");
            if (forest != null && p.level().getBiome(p.blockPosition()).is(net.minecraft.tags.BiomeTags.IS_FOREST)) {
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 60,
                        (int) forest.number("amplifier", 0), true, false, true));
            }
        }

        // Orco: +25 % de consumo de hambre (se añade un extra a cada cansancio que acumula).
        ActiveMechanic hunger = Races.find(p, "race_hunger");
        if (hunger != null) {
            float ex = Races.foodTag(food).getFloat("foodExhaustionLevel");
            Float last = LAST_EXHAUSTION.get(id);
            if (last != null && ex > last) {
                float extra = (float) ((ex - last) * hunger.number("exhaustion", 0.25));
                food.addExhaustion(extra);
                ex += extra;
            }
            LAST_EXHAUSTION.put(id, ex);
        }

        // Elfo: trepar por las hojas no acumula caída.
        if (p.fallDistance > 0 && ModAttributes.value(p, ModAttributes.LEAF_WALKER) > 0
                && p.level().getBlockState(p.blockPosition()).is(net.minecraft.tags.BlockTags.LEAVES)) p.resetFallDistance();

        // Tritón y No-muerto: respiran bajo el agua.
        if (Races.has(p, "race_water_breathing") && p.getAirSupply() < p.getMaxAirSupply()) p.setAirSupply(p.getMaxAirSupply());
        // Infernal: nunca arde.
        if (p.isOnFire() && Races.has(p, "race_fire_immune")) p.clearFire();

        // No-muerto: hambre fija, sin saturación.
        ActiveMechanic fixed = Races.find(p, "race_fixed_hunger");
        if (fixed != null) {
            net.minecraft.nbt.CompoundTag tag = Races.foodTag(food);
            int target = (int) fixed.number("food", 10);
            if (tag.getInt("foodLevel") != target || tag.getFloat("foodSaturationLevel") != 0 || tag.getFloat("foodExhaustionLevel") != 0) {
                tag.putInt("foodLevel", target);
                tag.putFloat("foodSaturationLevel", 0);
                tag.putFloat("foodExhaustionLevel", 0);
                food.readAdditionalSaveData(tag);
            }
        }

        if (!second) return;
        Level level = p.level();
        BlockPos eye = BlockPos.containing(p.getX(), p.getEyeY(), p.getZ());

        // No-muerto: el sol directo daña aunque lleve casco, y anula la regeneración.
        boolean inSun = false;
        ActiveMechanic sun = Races.find(p, "race_sunburn");
        if (sun != null && !p.isCreative() && !p.isSpectator()) {
            inSun = level.isDay() && !p.isInWaterRainOrBubble() && level.canSeeSky(eye) && p.getLightLevelDependentMagicValue() > 0.5f;
            if (inSun) p.hurt(Races.sunlight(p), (float) sun.number("damage_per_second", 1));
        }
        ActiveMechanic regen = Races.find(p, "race_dead_regen");
        if (regen != null && !inSun && now >= REGEN_PAUSED_UNTIL.getOrDefault(id, 0L) && p.getHealth() < p.getMaxHealth()) {
            p.heal((float) regen.number("heal_per_second", 1));
        }

        // Visión nocturna racial: Tritón (ojos bajo el agua), Enano de las Profundidades (sin cielo), Elfo Oscuro (poca luz).
        boolean vision = (Races.has(p, "race_aquatic") && p.isEyeInFluid(FluidTags.WATER))
                || (Races.has(p, "race_cave_eyes") && level.dimensionType().hasSkyLight() && level.getBrightness(LightLayer.SKY, eye) == 0)
                || (Races.has(p, "race_dark_vision") && level.getMaxLocalRawBrightness(eye) <= 7);
        if (vision) {
            p.addEffect(Races.hidden(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0));
            NIGHT_VISION.add(id);
        } else if (NIGHT_VISION.remove(id)) {
            MobEffectInstance current = p.getEffect(MobEffects.NIGHT_VISION);
            if (current != null && current.isAmbient() && current.getDuration() <= NIGHT_VISION_TICKS) p.removeEffect(MobEffects.NIGHT_VISION);
        }

        // Infernal (rev. 2.17): el agua le quema poco a poco (vapor y siseo).
        ActiveMechanic scald = Races.find(p, "race_water_scald");
        if (scald != null && !p.isCreative() && !p.isSpectator() && p.isInWaterOrBubble()) {
            int every = Math.max(1, (int) Math.round(scald.number("interval_seconds", 2)));
            int wet = WET_SECONDS.merge(id, 1, Integer::sum);
            if (wet % every == 0) p.hurt(Races.scald(p), (float) scald.number("scald_damage", 1));
            if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, p.getX(), p.getY() + p.getBbHeight() * 0.6, p.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
                sl.playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, 1.6f);
            }
        } else {
            WET_SECONDS.remove(id);
        }

        // Tritón (rev. 2.17): en el Nether (dimensiones abrasadoras) tiene Debilidad.
        ActiveMechanic dry = Races.find(p, "race_nether_weakness");
        if (dry != null && level.dimensionType().ultraWarm()) {
            p.addEffect(Races.hidden(MobEffects.WEAKNESS, 60, (int) Math.round(dry.number("level", 1)) - 1));
        }

        // Infernal: los golems de hierro cercanos siempre lo atacan.
        ActiveMechanic outcast = Races.find(p, "race_outcast");
        if (outcast != null && !p.isCreative() && !p.isSpectator()) {
            double radius = outcast.number("golem_radius", 16);
            for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, p.getBoundingBox().inflate(radius))) {
                if (golem.getTarget() == null && golem.isAlive()) golem.setTarget(p);
            }
        }
        SubraceTraits.secondTick(p);
    }

    // ------------------------------------------------------------------ daño

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        // Orco: 2 s invulnerable tras Última Resistencia.
        Long until = INVULNERABLE_UNTIL.get(player.getUUID());
        if (until != null) {
            if (player.level().getGameTime() < until) {
                event.setCanceled(true);
                return;
            }
            INVULNERABLE_UNTIL.remove(player.getUUID());
        }
        // Enano Demoledor: sus propias explosiones no le dañan.
        if (source.is(DamageTypeTags.IS_EXPLOSION) && source.getEntity() == player && Races.has(player, "race_blast_proof")) {
            event.setCanceled(true);
            return;
        }
        // Infernal: inmunidad al fuego (no al daño mágico de la escuela de fuego de Iron's).
        if (source.is(DamageTypeTags.IS_FIRE) && !source.is(Races.FIRE_MAGIC) && Races.has(player, "race_fire_immune")) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        DamageSource source = event.getSource();
        // Tritón: +30 % de daño de fuego (fuego, lava y la escuela de fuego de Iron's).
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(Races.FIRE_MAGIC)) {
            double extra = Races.number(player, "race_aquatic", "fire_damage", 0);
            if (extra != 0) event.setAmount((float) (event.getAmount() * (1 + extra)));
        }
        // Infernal (rev. 2.17): +30 % de daño de frío (congelación y escuela de hielo) y de agua (escuela de agua de T.O).
        ActiveMechanic frail = Races.find(player, "race_cold_frailty");
        if (frail != null) {
            io.github.rpgclasses.content.Schools.School school = source.typeHolder().unwrapKey()
                    .map(k -> io.github.rpgclasses.content.Schools.ofDamageType(k.location())).orElse(null);
            double extra = 0;
            if (source.is(DamageTypeTags.IS_FREEZING) || school == io.github.rpgclasses.content.Schools.School.ICE) extra = frail.number("cold_damage", 0.3);
            else if (school == io.github.rpgclasses.content.Schools.School.AQUA) extra = frail.number("water_damage", 0.3);
            if (extra != 0) event.setAmount((float) (event.getAmount() * (1 + extra)));
        }
    }

    /** Después de las habilidades de clase (prioridad LOW) y antes del Tótem (que vanilla mira al morir). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        DamageSource source = event.getSource();
        long now = player.level().getGameTime();

        // Orco Pétreo: Piel Gruesa resta un valor fijo a cada golpe, sin mínimo.
        double flat = Races.number(player, "race_thick_skin", "flat_reduction", 0);
        if (flat > 0 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            float before = event.getAmount();
            event.setAmount((float) Math.max(0, before - flat));
            // Chispas de piedra en los golpes que Piel Gruesa amortigua (no en el daño continuo).
            if (source.getEntity() != null && player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()), player.getX(), player.getY() + 1.0, player.getZ(),
                        8, 0.3, 0.4, 0.3, 0.1);
                if (event.getAmount() <= 0) player.level().playSound(null, player.blockPosition(), SoundEvents.STONE_HIT,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 0.7f);
            }
        }
        if (event.getAmount() <= 0) return;

        // No-muerto: la regeneración se corta al recibir daño (el Esqueleto de Rango 3, menos tiempo).
        ActiveMechanic regen = Races.find(player, "race_dead_regen");
        if (regen != null && !source.is(Races.SUNLIGHT)) {
            double pause = Races.number(player, "race_bones", "regen_pause_seconds", regen.number("pause_seconds", 3));
            REGEN_PAUSED_UNTIL.put(player.getUUID(), now + Math.round(pause * 20));
        }

        // Orco: Última Resistencia, 1 vez por vida.
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || event.getAmount() < player.getHealth()) return;
        ActiveMechanic stand = Races.find(player, LAST_STAND);
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (stand == null || data == null || data.counters.getOrDefault(LAST_STAND_COUNTER, 0) > 0) return;
        data.counters.put(LAST_STAND_COUNTER, 1);
        event.setAmount(Math.max(0, player.getHealth() - 1));
        INVULNERABLE_UNTIL.put(player.getUUID(), now + Math.round(stand.number("invulnerable_seconds", 2) * 20));
        player.level().playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1f, 0.8f);
        if (player.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY() + 1, player.getZ(), 12, 0.4, 0.5, 0.4, 0);
        }
        player.displayClientMessage(Component.translatable("message.rpgclasses.last_stand"), true);
        io.github.rpgclasses.gear.CurioEffects.onLastStand(player); // Pendiente de Hueso de Trol (rev. 2.20)
        DebugLog.event(player, "raza", "Última Resistencia: sobrevive con 1 HP");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data != null) data.counters.remove(LAST_STAND_COUNTER);
        INVULNERABLE_UNTIL.remove(player.getUUID());
        REGEN_PAUSED_UNTIL.remove(player.getUUID());
    }

    // ------------------------------------------------------------------ minería, botín y comida

    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        float speed = event.getNewSpeed();
        // Enano: Minero.
        if (event.getState().is(Races.DWARF_MINEABLE)) {
            speed *= 1 + (float) Races.number(player, "race_miner", "mining_speed", 0);
        }
        // Tritón: Minero Sumergido (se deshacen las dos penalizaciones de vanilla bajo el agua).
        if (player.isInWater() && Races.has(player, "race_aquatic")) {
            if (player.isEyeInFluid(FluidTags.WATER) && !EnchantmentHelper.hasAquaAffinity(player)) speed *= 5f;
            if (!player.onGround()) speed *= 5f;
        }
        if (speed != event.getNewSpeed()) event.setNewSpeed(speed);
    }

    /** Goblin: Saqueo racial. No se suma al encantamiento: cuenta el mayor (regla 11). */
    @SubscribeEvent
    public void onLooting(LootingLevelEvent event) {
        DamageSource source = event.getDamageSource();
        if (source == null || !(source.getEntity() instanceof ServerPlayer player)) return;
        int racial = (int) Races.number(player, "race_looting", "level", 0);
        if (racial > event.getLootingLevel()) event.setLootingLevel(racial);
    }

    /** Elfo: no puede comer carne ni pescado (tag rpgclasses:elf_forbidden_food). */
    @SubscribeEvent
    public void onStartEating(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof Player player) || !event.getItem().is(Races.FORBIDDEN_FOOD)) return;
        if (!Races.has(player, "race_no_meat")) return;
        event.setCanceled(true);
        player.stopUsingItem();
        if (player instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("message.rpgclasses.elf_no_meat"), true);
    }

    /** Infernal: los aldeanos le suben los precios (se añade al ajuste especial, que el aldeano borra al cerrar). */
    @SubscribeEvent
    public void onOpenContainer(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getContainer() instanceof MerchantMenu menu)) return;
        double increase = Races.number(player, "race_outcast", "price_increase", 0);
        if (increase <= 0) return;
        boolean villager = !player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(10),
                v -> v.getTradingPlayer() == player).isEmpty();
        if (!villager) return;
        for (MerchantOffer offer : menu.getOffers()) {
            offer.addToSpecialPriceDiff((int) Math.ceil(offer.getBaseCostA().getCount() * increase));
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST_EXHAUSTION.remove(id);
        INVULNERABLE_UNTIL.remove(id);
        REGEN_PAUSED_UNTIL.remove(id);
        NIGHT_VISION.remove(id);
    }
}
