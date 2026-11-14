package io.github.rpgclasses.gear;

import io.github.rpgclasses.content.CombatAbilities;
import io.github.rpgclasses.content.race.Races;
import io.github.rpgclasses.server.Allies;
import io.github.rpgclasses.server.DamagePipeline;
import io.github.rpgclasses.server.WeaponClassifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.FrostWalkerEnchantment;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Efectos del catálogo de curios ({@link Curio}). Funciona sin depender de Curios: el compat
 * ({@code compat/curios}) rellena {@link #equipped}; sin Curios ningún curio está equipado (salvo las nudilleras,
 * que también valen en la mano secundaria).
 */
public final class CurioEffects {
    /** Pila del curio equipado (o vacía). La pone el compat de Curios. */
    public static BiFunction<Player, Curio, ItemStack> equipped = (p, c) -> ItemStack.EMPTY;

    private static final class State {
        long nextWisp;
        BlockPos deathPos;
        ResourceLocation deathDim;
        long deathUntil;
        long nextEat;
        long nextPixie;
        long nextHorn;
        long lastCombat;
        UUID gazeTarget;
        int gazeTicks;
        long nextGaze;
        long nextStorm;
        long nextHourglass;
        int spellCount;
        int fangs;
        boolean wasInvisible;
        long nextCrows;
        int arrows;
        String lastDiscipline;
        long tradesUntil;
        long nextTrades;
        long nextFear;
        long nextConch;
        boolean wasInWater;
        final Map<UUID, Long> reliquaryHeal = new HashMap<>();
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static State state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    /** El curio está equipado y el jugador cumple su restricción. */
    public static boolean has(Player p, Curio c) {
        return !(p instanceof FakePlayer) && !equipped.apply(p, c).isEmpty() && c.allowed(p);
    }

    private static void sound(Entity at, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        at.level().playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static boolean isSpell(DamageSource source) {
        return source.typeHolder().unwrapKey().map(k -> "irons_spellbooks".equals(k.location().getNamespace())).orElse(false);
    }

    // ================================================================== ganchos

    /** Dados Trucados (Pícaro): en cada crítico, un 6 multiplica ×1,5 extra; un 1 lo deja en golpe normal. */
    public static double critRoll(ServerPlayer p, double critMultiplier) {
        if (!has(p, Curio.LOADED_DICE)) return 1;
        int roll = 1 + p.getRandom().nextInt(6);
        GearFx.burst(p, ParticleTypes.ENCHANTED_HIT, roll * 2, 0.1);
        if (roll == 6) {
            sound(p, SoundEvents.PLAYER_LEVELUP, 0.5f, 1.8f);
            return 1.5;
        }
        if (roll == 1) {
            sound(p, SoundEvents.VILLAGER_NO, 0.6f, 1.2f);
            return 1 / Math.max(1, critMultiplier);
        }
        return 1;
    }

    /** Tambor de Guerra (Bárbaro): Furia Berserker da Prisa a los aliados cercanos. */
    public static void onBerserker(ServerPlayer p) {
        if (!has(p, Curio.WAR_DRUM)) return;
        for (Player ally : p.level().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(10), a -> a == p || Allies.isAlly(p, a))) {
            ally.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200, 0));
        }
        ServerLevel level = (ServerLevel) p.level();
        for (int r = 2; r <= 10; r += 2) GearFx.ring(level, p.position(), GearFx.EMBER, r, 8 + r * 3);
        sound(p, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.0f, 0.6f);
        sound(p, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.0f, 0.5f);
    }

    /** Rosario de las Tres Posturas (Monje): la descarga con ≥8 cargas suelta cuentas que rebotan entre 3 enemigos. */
    public static void onMonkDischarge(ServerPlayer p, int charges) {
        if (charges < 8 || !has(p, Curio.STANCE_ROSARY)) return;
        List<LivingEntity> done = new ArrayList<>();
        Entity from = p;
        for (int i = 0; i < 3; i++) {
            LivingEntity next = GearFx.nearestEnemy(p, from.position(), 6, done);
            if (next == null) break;
            next.invulnerableTime = 0;
            next.hurt(GearFx.source(p, GearFx.MARTIAL), 1);
            GearFx.line(from, next, ParticleTypes.END_ROD, 8);
            done.add(next);
            from = next;
        }
        if (!done.isEmpty()) sound(p, SoundEvents.AMETHYST_CLUSTER_HIT, 1.0f, 1.5f);
    }

    /** Campana del Templo (Monje): al terminar de meditar, los aliados cercanos pierden un efecto negativo. */
    public static void onMeditationEnd(ServerPlayer p) {
        if (!has(p, Curio.TEMPLE_BELL)) return;
        for (Player ally : p.level().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(8), a -> a == p || Allies.isAlly(p, a))) {
            for (MobEffectInstance e : List.copyOf(ally.getActiveEffects())) {
                if (e.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                    ally.removeEffect(e.getEffect());
                    break;
                }
            }
        }
        GearFx.ring((ServerLevel) p.level(), p.position(), ClassFxGold.GOLD, 8, 48);
        sound(p, SoundEvents.BELL_BLOCK, 1.0f, 1.2f);
    }

    /** Pendiente de Hueso de Trol (Orco): la Última Resistencia hace huir a los mobs cercanos. */
    public static void onLastStand(ServerPlayer p) {
        if (!has(p, Curio.TROLL_EARRING)) return;
        fear(p, 6, 40);
        GearFx.burst(p, ParticleTypes.ANGRY_VILLAGER, 10, 0.1);
    }

    private static void fear(ServerPlayer p, double radius, int ticks) {
        for (LivingEntity e : GearFx.enemiesAround(p, p.position(), radius)) {
            if (e instanceof Mob mob && !Races.isBoss(e)) {
                Vec3 away = mob.position().subtract(p.position()).normalize().scale(8).add(mob.position());
                mob.setTarget(null);
                mob.getNavigation().moveTo(away.x, away.y, away.z, 1.4);
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0));
            }
        }
    }

    // ================================================================== eventos de combate

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        LivingEntity target = event.getEntity();
        long now = target.level().getGameTime();

        if (target instanceof ServerPlayer victim && !(victim instanceof FakePlayer)) {
            State st = state(victim);
            st.lastCombat = now;
            // Velo de Medianoche (No-muerto): el sol hace la mitad de daño.
            if (source.is(Races.SUNLIGHT) && has(victim, Curio.MIDNIGHT_VEIL)) event.setAmount(event.getAmount() * 0.5f);
            // Cráneo Trofeo: inmunidad a la quemadura si su trofeo es de un jefe de fuego.
            if (source.is(DamageTypeTags.IS_FIRE) && trophyGrants(victim, "fire")) event.setAmount(0);
            LivingEntity attacker = source.getEntity() instanceof LivingEntity l && source.getDirectEntity() == l ? l : null;
            if (attacker != null && attacker != victim) {
                if (has(victim, Curio.STORM_TALISMAN) && now >= st.nextStorm && victim.getRandom().nextDouble() < 0.1) {
                    st.nextStorm = now + 160;
                    attacker.hurt(GearFx.source(victim, GearFx.SPARK), 4);
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(victim.level());
                    if (bolt != null) {
                        bolt.moveTo(attacker.getX(), attacker.getY(), attacker.getZ());
                        bolt.setVisualOnly(true);
                        victim.level().addFreshEntity(bolt);
                    }
                }
                if (has(victim, Curio.MAGMA_HEART) && victim.getRandom().nextDouble() < 0.15) {
                    attacker.setSecondsOnFire(3);
                    GearFx.line(victim, attacker, ParticleTypes.LAVA, 6);
                }
            }
            // Reloj de Arena Arcano (Mago): al bajar del 30 % de vida, los proyectiles cercanos se frenan 2 s.
            if (has(victim, Curio.ARCANE_HOURGLASS) && now >= st.nextHourglass
                    && victim.getHealth() - event.getAmount() < victim.getMaxHealth() * 0.3f) {
                st.nextHourglass = now + 1200;
                for (Projectile pr : victim.level().getEntitiesOfClass(Projectile.class, victim.getBoundingBox().inflate(6),
                        pr -> pr.getOwner() != victim)) {
                    pr.setDeltaMovement(pr.getDeltaMovement().scale(0.3));
                    pr.hurtMarked = true;
                }
                GearFx.ring((ServerLevel) victim.level(), victim.position(), ClassFxGold.GOLD, 6, 40);
                sound(victim, SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.6f);
            }
        }

        if (!(source.getEntity() instanceof ServerPlayer attacker) || attacker instanceof FakePlayer || source.is(DamagePipeline.NO_PROC)) return;
        State st = state(attacker);
        // Cuerno de Cíclope: al entrar en combate (primer golpe tras 10 s), suena el cuerno.
        if (has(attacker, Curio.CYCLOPS_HORN) && now - st.lastCombat > 200 && now >= st.nextHorn) {
            st.nextHorn = now + 1800;
            for (LivingEntity e : GearFx.enemiesAround(attacker, attacker.position(), 16)) {
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0));
            }
            for (Player ally : attacker.level().getEntitiesOfClass(Player.class, attacker.getBoundingBox().inflate(16),
                    a -> a == attacker || Allies.isAlly(attacker, a))) {
                ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 160, 0));
            }
            sound(attacker, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(), 2.0f, 0.8f);
        }
        st.lastCombat = now;
        // Prisma del Archimago: cada 5.º conjuro que hace daño se refracta al 30 % hacia otro enemigo.
        if (isSpell(source) && has(attacker, Curio.ARCHMAGE_PRISM) && ++st.spellCount >= 5) {
            st.spellCount = 0;
            LivingEntity other = GearFx.nearestEnemy(attacker, target.position(), 10, List.of(target));
            if (other != null) {
                other.hurt(attacker.damageSources().indirectMagic(attacker, attacker), event.getAmount() * 0.3f);
                GearFx.line(target, other, ParticleTypes.END_ROD, 12);
                sound(attacker, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);
            }
        }
        boolean melee = source.getDirectEntity() == attacker;
        if (melee) {
            // Collar de Colmillos (Bárbaro): +1 % de daño por colmillo.
            if (st.fangs > 0 && has(attacker, Curio.FANG_COLLAR)) event.setAmount(event.getAmount() * (1 + st.fangs * 0.01f));
            // Insignia de Mil Oficios (Humano): +10 % tras cambiar de disciplina.
            if (st.tradesUntil >= now && has(attacker, Curio.GUILD_INSIGNIA)) event.setAmount(event.getAmount() * 1.1f);
            // Cráneo Trofeo: +10 % si el trofeo es de un jefe «de fuerza».
            if (trophyGrants(attacker, "damage")) event.setAmount(event.getAmount() * 1.1f);
        }
        // Relicario del Juramento (Paladín): golpear a un mob que persigue a un Paladín con el relicario te cura 1 HP.
        if (target instanceof Mob mob && mob.getTarget() instanceof ServerPlayer paladin && has(paladin, Curio.OATH_RELIQUARY)
                && (paladin == attacker || Allies.isAlly(paladin, attacker))) {
            State ps = state(paladin);
            Long last = ps.reliquaryHeal.get(attacker.getUUID());
            if (last == null || now - last >= 20) {
                ps.reliquaryHeal.put(attacker.getUUID(), now);
                attacker.heal(1);
                GearFx.burst(target, ClassFxGold.GOLD, 6, 0.02);
            }
        }
    }

    /**
     * Pluma de Fénix: tras las defensas de clase y de raza (y tras la armadura), un golpe letal te hace renacer en
     * llamas (20 min). Va antes que el Tótem, que vanilla comprueba después de este evento.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLethal(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.isCanceled()) return;
        if (event.getAmount() < p.getHealth() || !has(p, Curio.PHOENIX_FEATHER)) return; // la absorción ya está descontada
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        CompoundTag tag = p.getPersistentData();
        long now = p.level().getGameTime();
        if (tag.getLong("rpgclasses.phoenix_ready") > now) return;
        tag.putLong("rpgclasses.phoenix_ready", now + 24000);
        event.setAmount(0);
        p.setHealth(Math.max(1, p.getMaxHealth() * 0.3f));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 4));
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100, 0));
        ServerLevel level = (ServerLevel) p.level();
        for (int i = 0; i < 3; i++) GearFx.ring(level, p.position().add(0, i * 0.7, 0), ParticleTypes.FLAME, 1.2 + i * 0.4, 30);
        GearFx.wings(p, ParticleTypes.FLAME, now);
        sound(p, SoundEvents.BLAZE_SHOOT, 1.0f, 0.6f);
        sound(p, SoundEvents.TOTEM_USE, 0.6f, 1.4f);
    }

    @SubscribeEvent
    public void onAttacked(LivingAttackEvent event) {
        // Cráneo Trofeo de un jefe de fuego: sin quemadura (el daño de fuego se anula en onHurt).
        if (event.getEntity() instanceof ServerPlayer p && event.getSource().is(DamageTypeTags.IS_FIRE) && trophyGrants(p, "fire")) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead instanceof ServerPlayer p) {
            State st = state(p);
            st.fangs = 0;
            if (has(p, Curio.LAST_BREATH_COMPASS)) {
                st.deathPos = p.blockPosition();
                st.deathDim = p.level().dimension().location();
                st.deathUntil = p.level().getGameTime() + 12000;
            }
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || killer instanceof FakePlayer) return;
        if (dead instanceof Enemy && has(killer, Curio.FANG_COLLAR)) {
            State st = state(killer);
            st.fangs = Math.min(10, st.fangs + 1);
            GearFx.burst(killer, GearFx.BONE, 4, 0.02);
        }
        if (Races.isBoss(dead) && has(killer, Curio.TROPHY_SKULL)) {
            ItemStack skull = equipped.apply(killer, Curio.TROPHY_SKULL);
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(dead.getType());
            if (id != null) {
                skull.getOrCreateTag().putString("trophy", id.toString());
                killer.displayClientMessage(net.minecraft.network.chat.Component.translatable("curio.rpgclasses.trophy_skull.taken",
                        dead.getDisplayName()), true);
                sound(killer, SoundEvents.WITHER_SKELETON_AMBIENT, 1.0f, 0.7f);
            }
        }
    }

    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || !has(p, Curio.TRINKET_POUCH)) return;
        if (event.getDrops().isEmpty() || p.getRandom().nextDouble() >= 0.05) return;
        ItemEntity pick = new ArrayList<>(event.getDrops()).get(p.getRandom().nextInt(event.getDrops().size()));
        ItemStack stack = pick.getItem();
        if (stack.getRarity() != net.minecraft.world.item.Rarity.COMMON || stack.isDamageableItem()) return;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        event.getDrops().add(new ItemEntity(pick.level(), pick.getX(), pick.getY(), pick.getZ(), copy));
        GearFx.burst(event.getEntity(), ParticleTypes.WAX_ON, 8, 0.1);
    }

    /** Cráneo Trofeo: inmunidades de los trofeos de jefes a efectos (lista fija; los demás jefes, solo cosmético). */
    @SubscribeEvent
    public void onEffect(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        MobEffect effect = event.getEffectInstance().getEffect();
        if ((effect == MobEffects.MOVEMENT_SLOWDOWN && trophyGrants(p, "slowness"))
                || (effect == MobEffects.WITHER && trophyGrants(p, "wither"))
                || (effect == MobEffects.DIG_SLOWDOWN && trophyGrants(p, "fatigue"))
                || (effect == MobEffects.DARKNESS && trophyGrants(p, "darkness"))) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** Rasgo del trofeo según el jefe muerto (por id de entidad). */
    private static boolean trophyGrants(ServerPlayer p, String trait) {
        if (!has(p, Curio.TROPHY_SKULL)) return false;
        CompoundTag tag = equipped.apply(p, Curio.TROPHY_SKULL).getTag();
        if (tag == null || !tag.contains("trophy")) return false;
        String boss = tag.getString("trophy");
        return switch (trait) {
            case "fire" -> boss.equals("cataclysm:ignis") || boss.equals("minecraft:blaze") || boss.equals("iceandfire:fire_dragon");
            case "slowness" -> boss.equals("mowziesmobs:frostmaw") || boss.equals("iceandfire:ice_dragon");
            case "wither" -> boss.equals("minecraft:wither") || boss.equals("cataclysm:maledictus");
            case "fatigue" -> boss.equals("minecraft:elder_guardian") || boss.equals("cataclysm:the_leviathan");
            case "darkness" -> boss.equals("minecraft:warden");
            case "damage" -> boss.equals("cataclysm:netherite_monstrosity") || boss.equals("cataclysm:the_harbinger")
                    || boss.equals("mowziesmobs:ferrous_wroughtnaut") || boss.equals("minecraft:ender_dragon")
                    || boss.equals("iceandfire:lightning_dragon");
            default -> false;
        };
    }

    /** Hoja del Árbol Eterno (Elfo): dentro de hojas, los mobs a más de 8 bloques no te fijan como objetivo. */
    @SubscribeEvent
    public void onTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof ServerPlayer p) || !has(p, Curio.EVERGREEN_LEAF) || !inLeaves(p)) return;
        if (event.getEntity().distanceToSqr(p) > 64) event.setCanceled(true);
    }

    private static boolean inLeaves(Player p) {
        return p.level().getBlockState(p.blockPosition()).is(BlockTags.LEAVES)
                || p.level().getBlockState(p.blockPosition().above()).is(BlockTags.LEAVES);
    }

    /** Carcaj del Matadragones (Ranger): cada 10 flechas, la siguiente es de hueso de dragón (+50 % y perfora 1). */
    @SubscribeEvent
    public void onArrow(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getOwner() instanceof ServerPlayer p) || !has(p, Curio.DRAGONSLAYER_QUIVER)) return;
        State st = state(p);
        if (++st.arrows < 11) return;
        st.arrows = 0;
        arrow.setBaseDamage(arrow.getBaseDamage() * 1.5);
        arrow.setPierceLevel((byte) Math.max(1, arrow.getPierceLevel()));
        arrow.setGlowingTag(true);
        sound(p, SoundEvents.ENDER_DRAGON_SHOOT, 0.4f, 1.8f);
    }

    // ================================================================== efectos por tic

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        ServerLevel level = (ServerLevel) p.level();
        long now = level.getGameTime();
        State st = state(p);

        // Medallón del Ermitaño: atrae objetos y orbes de XP.
        if (now % 2 == 0 && has(p, Curio.HERMIT_MEDALLION)) {
            for (Entity e : level.getEntities(p, p.getBoundingBox().inflate(6), e -> e instanceof ItemEntity || e instanceof ExperienceOrb)) {
                if (e instanceof ItemEntity item && item.hasPickUpDelay()) continue;
                Vec3 pull = p.position().add(0, 0.5, 0).subtract(e.position()).normalize().scale(0.25);
                e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(pull));
                if (now % 6 == 0) level.sendParticles(ClassFxGold.GOLD, e.getX(), e.getY() + 0.2, e.getZ(), 1, 0, 0, 0, 0);
            }
        }
        // Brújula del Último Aliento: columna de luz donde moriste.
        if (st.deathPos != null && now % 10 == 0) {
            if (now > st.deathUntil) st.deathPos = null;
            else if (level.dimension().location().equals(st.deathDim) && has(p, Curio.LAST_BREATH_COMPASS)) {
                for (int y = 0; y < 24; y += 2) {
                    level.sendParticles(p, ParticleTypes.END_ROD, true, st.deathPos.getX() + 0.5, st.deathPos.getY() + y, st.deathPos.getZ() + 0.5, 1, 0.05, 0.5, 0.05, 0);
                }
            }
        }
        // Farol de Fuegos Fatuos: señala el cofre o spawner más cercano.
        if (now >= st.nextWisp && has(p, Curio.WISP_LANTERN)) {
            st.nextWisp = now + 600;
            BlockPos found = nearestTreasure(level, p.blockPosition(), 24);
            if (found != null) {
                Vec3 from = p.getEyePosition();
                Vec3 to = Vec3.atCenterOf(found).add(0, 1, 0);
                for (int i = 0; i <= 20; i++) {
                    Vec3 pt = from.lerp(to, i / 20.0);
                    level.sendParticles(p, ParticleTypes.SOUL_FIRE_FLAME, true, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
                }
                level.sendParticles(p, ParticleTypes.SOUL, true, to.x, to.y, to.z, 20, 0.2, 0.4, 0.2, 0.01);
                sound(p, SoundEvents.ALLAY_AMBIENT_WITH_ITEM, 0.6f, 1.2f);
            }
        }
        // Cinturón del Viajero: comes solo de tu inventario por debajo de 6 muslitos.
        if (now >= st.nextEat && p.getFoodData().getFoodLevel() <= 12 && has(p, Curio.TRAVELER_BELT)) {
            st.nextEat = now + 100;
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack food = p.getInventory().getItem(i);
                if (food.isEdible() && food.getFoodProperties(p) != null && food.getFoodProperties(p).getEffects().isEmpty()) {
                    p.eat(level, food.split(1).copyWithCount(1));
                    if (food.isEmpty()) p.getInventory().setItem(i, ItemStack.EMPTY);
                    break;
                }
            }
        }
        // Anillo de Escarcha Errante: Paso Helado I.
        if (has(p, Curio.FROST_RING) && p.onGround()) FrostWalkerEnchantment.onEntityMoved(p, level, p.blockPosition(), 1);
        // Campanilla de Pixie: agachado en el aire, flotas 3 s.
        if (!p.onGround() && p.isShiftKeyDown() && now >= st.nextPixie && p.getDeltaMovement().y < 0 && has(p, Curio.PIXIE_BELL)) {
            st.nextPixie = now + 200;
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0));
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY(), p.getZ(), 15, 0.4, 0.2, 0.4, 0.02);
            sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 2.0f);
        }
        // Colgante de Serpiente Marina: nadas más rápido y al salir del agua corres 5 s.
        if (has(p, Curio.SERPENT_PENDANT)) {
            if (p.isInWater() && p.isSwimming() && now % 10 == 0) p.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 20, 0, false, false));
            if (st.wasInWater && !p.isInWater()) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 0, false, true));
        }
        st.wasInWater = p.isInWater();
        // Ojo de Gorgona: mirar 2 s a un mob no jefe lo petrifica 2 s (30 s).
        if (has(p, Curio.GORGON_EYE) && now >= st.nextGaze) {
            LivingEntity look = lookedAt(p, 16);
            if (look != null && look.getUUID().equals(st.gazeTarget)) {
                if (++st.gazeTicks >= 40) {
                    st.gazeTicks = 0;
                    st.nextGaze = now + 600;
                    CombatAbilities.stun(look, 40);
                    look.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 6));
                    level.sendParticles(GearFx.STONE_DUST, look.getX(), look.getY() + look.getBbHeight() / 2, look.getZ(), 40,
                            look.getBbWidth() / 2, look.getBbHeight() / 2, look.getBbWidth() / 2, 0);
                    sound(look, SoundEvents.STONE_PLACE, 1.0f, 0.5f);
                }
            } else {
                st.gazeTarget = look == null ? null : look.getUUID();
                st.gazeTicks = 0;
            }
        }
        // Capa de Cuervos (Pícaro): al salir de la invisibilidad, cuervos que ciegan a los enemigos cercanos.
        boolean invisible = p.isInvisible();
        if (st.wasInvisible && !invisible && now >= st.nextCrows && has(p, Curio.CROW_CLOAK)) {
            st.nextCrows = now + 300;
            for (LivingEntity e : GearFx.enemiesAround(p, p.position(), 3)) e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0), p);
            level.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1.2, p.getZ(), 30, 0.6, 0.6, 0.6, 0.15);
            sound(p, SoundEvents.PHANTOM_FLAP, 1.0f, 1.4f);
        }
        st.wasInvisible = invisible;
        // Pluma de Hipogrifo (Ranger): tensar el arco en el aire te hace planear.
        if (has(p, Curio.HIPPOGRYPH_FEATHER) && !p.onGround() && p.isUsingItem() && p.getUseItem().getItem() instanceof BowItem) {
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0, false, false));
            if (now % 4 == 0) level.sendParticles(ParticleTypes.WHITE_ASH, p.getX(), p.getY(), p.getZ(), 4, 0.4, 0.1, 0.4, 0);
        }
        // Estandarte de Bolsillo (Paladín): estandarte dorado en la espalda (visual).
        if (has(p, Curio.POCKET_BANNER) && now % 8 == 0) {
            double yaw = Math.toRadians(p.yBodyRot);
            Vec3 back = p.position().add(Math.sin(yaw) * 0.4, 0, -Math.cos(yaw) * 0.4);
            for (int i = 0; i < 4; i++) level.sendParticles(ClassFxGold.GOLD, back.x, p.getY() + 1.4 + i * 0.25, back.z, 1, 0, 0, 0, 0);
        }
        // Insignia de Mil Oficios (Humano): cambiar de disciplina da +10 % de daño 3 s (6 s de espera).
        if (has(p, Curio.GUILD_INSIGNIA)) {
            WeaponClassifier.Classification c = WeaponClassifier.classify(p.getMainHandItem());
            String discipline = c.unarmed() ? "unarmed" : c.discipline();
            if (discipline != null && st.lastDiscipline != null && !discipline.equals(st.lastDiscipline) && now >= st.nextTrades) {
                st.tradesUntil = now + 60;
                st.nextTrades = now + 120;
                GearFx.burst(p, ClassFxGold.GOLD, 10, 0.05);
            }
            if (discipline != null) st.lastDiscipline = discipline;
        }
        // Farolillo de Mina (Enano): los minerales cercanos chispean.
        if (now % 20 == 0 && has(p, Curio.MINE_LANTERN)) {
            BlockPos c = p.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(c.offset(-4, -4, -4), c.offset(4, 4, 4))) {
                if (level.getBlockState(pos).is(Tags.Blocks.ORES)) {
                    level.sendParticles(p, ParticleTypes.WAX_OFF, true, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0);
                }
            }
        }
        // Hoja del Árbol Eterno (Elfo): regeneras dentro de hojas.
        if (now % 40 == 0 && has(p, Curio.EVERGREEN_LEAF) && inLeaves(p)) {
            p.heal(1);
            level.sendParticles(ParticleTypes.CHERRY_LEAVES, p.getX(), p.getY() + 1.5, p.getZ(), 6, 0.5, 0.5, 0.5, 0);
        }
        // Caracola de las Mareas (Tritón): bajo el agua, tus aliados en 5 bloques respiran; fuera, te apaga el fuego.
        if (now % 20 == 0 && has(p, Curio.TIDE_CONCH)) {
            if (p.isUnderWater()) {
                for (Player ally : level.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(5), a -> a == p || Allies.isAlly(p, a))) {
                    ally.setAirSupply(Math.min(ally.getMaxAirSupply(), ally.getAirSupply() + 60));
                }
                level.sendParticles(ParticleTypes.BUBBLE, p.getX(), p.getY() + 1, p.getZ(), 20, 2, 1, 2, 0.05);
            } else if (p.isOnFire() && now >= st.nextConch) {
                st.nextConch = now + 200;
                p.clearFire();
                level.sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.8, 0.5, 0.2);
                sound(p, SoundEvents.GENERIC_SPLASH, 1.0f, 1.2f);
            }
        }
        // Corazón de Magma (Infernal): huellas incandescentes.
        if (now % 5 == 0 && has(p, Curio.MAGMA_HEART) && p.onGround() && p.getDeltaMovement().horizontalDistanceSqr() > 1.0E-3) {
            level.sendParticles(ParticleTypes.SMALL_FLAME, p.getX(), p.getY() + 0.05, p.getZ(), 2, 0.15, 0, 0.15, 0);
        }
        // Velo de Medianoche (No-muerto): al sol, la silueta humea.
        if (now % 10 == 0 && has(p, Curio.MIDNIGHT_VEIL) && level.isDay() && level.canSeeSky(p.blockPosition().above())) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.6, p.getZ(), 3, 0.3, 0.3, 0.3, 0.01);
        }
        // Collar de Colmillos: los colmillos se ven al moverse.
        if (st.fangs > 0 && now % 20 == 0 && has(p, Curio.FANG_COLLAR)) {
            level.sendParticles(GearFx.BONE, p.getX(), p.getY() + 1.35, p.getZ(), st.fangs, 0.2, 0.05, 0.2, 0);
        }
    }

    private static LivingEntity lookedAt(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        LivingEntity best = null;
        double bestDot = 0.985;
        for (LivingEntity e : GearFx.enemiesAround(p, p.position(), range)) {
            if (Races.isBoss(e) || !(e instanceof Mob)) continue;
            Vec3 to = e.getEyePosition().subtract(eye).normalize();
            double dot = to.dot(look);
            if (dot > bestDot && p.hasLineOfSight(e)) {
                bestDot = dot;
                best = e;
            }
        }
        return best;
    }

    private static BlockPos nearestTreasure(ServerLevel level, BlockPos center, int radius) {
        BlockPos best = null;
        double bestDist = radius * radius;
        int cx = center.getX() >> 4, cz = center.getZ() >> 4, r = (radius >> 4) + 1;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (!level.hasChunk(x, z)) continue;
                LevelChunk chunk = level.getChunk(x, z);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof ChestBlockEntity || be instanceof SpawnerBlockEntity)) continue;
                    double d = be.getBlockPos().distSqr(center);
                    if (d < bestDist) {
                        bestDist = d;
                        best = be.getBlockPos();
                    }
                }
            }
        }
        return best;
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    /** Partícula dorada compartida (como ClassFx.GOLD, sin depender de su clase). */
    static final class ClassFxGold {
        static final net.minecraft.core.particles.DustParticleOptions GOLD = io.github.rpgclasses.content.ClassFx.GOLD;
    }
}
