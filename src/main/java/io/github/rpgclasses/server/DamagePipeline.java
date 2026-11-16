package io.github.rpgclasses.server;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.config.ServerConfig;
import io.github.rpgclasses.content.BannerAura;
import io.github.rpgclasses.content.BarbarianPerks;
import io.github.rpgclasses.content.BarbarianSubclasses;
import io.github.rpgclasses.content.CombatAbilities;
import io.github.rpgclasses.content.MonkPerks;
import io.github.rpgclasses.content.MonkSubclasses;
import io.github.rpgclasses.content.PaladinPerks;
import io.github.rpgclasses.content.PaladinSubclasses;
import io.github.rpgclasses.content.Toxins;
import io.github.rpgclasses.content.RangerAbilities;
import io.github.rpgclasses.content.RangerPerks;
import io.github.rpgclasses.content.RogueAbilities;
import io.github.rpgclasses.content.RoguePerks;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.SubclassDef;
import io.github.rpgclasses.core.progression.ProgressState;
import io.github.rpgclasses.core.stat.StatCalculator;
import io.github.rpgclasses.core.stat.StatMath;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.registry.ModAttributes;
import io.github.rpgclasses.registry.ModEffects;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Único punto donde el mod modifica el daño que inflige un jugador. Orden fijo:
 * daño plano desarmado, bonus aditivos, bonus multiplicativos (Furia de Heridas), crítico, muro de disciplina.
 * Las reducciones del defensor y las habilidades se añadirán aquí en la fase 4.
 */
public final class DamagePipeline {
    /** Daño generado por el propio mod (Plaga, reflejo, réplicas...): no vuelve a pasar por el pipeline. */
    public static final TagKey<DamageType> NO_PROC = TagKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("no_proc"));

    private static final String TAG_FACTOR = "rpgclasses.factor";
    private static final String TAG_CAN_CRIT = "rpgclasses.can_crit";
    private static final Map<UUID, String> LAST_BREAKDOWN = new HashMap<>();

    /** Desglose del último golpe del jugador, para /clases debug stats. */
    public static String lastBreakdown(ServerPlayer player) {
        return LAST_BREAKDOWN.get(player.getUUID());
    }

    /** Con clase, el crítico es el del mod: se anula el crítico vanilla por salto para que no se acumulen. */
    @SubscribeEvent
    public void onVanillaCrit(CriticalHitEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && classOf(player) != null) {
            event.setResult(Event.Result.DENY);
        }
    }

    /**
     * Al disparar se graba en el proyectil el bonus de arco y el muro del tirador:
     * cambiar de arma con la flecha en vuelo no altera nada.
     */
    /** Flechas más rápidas que lo que vanilla puede sincronizar: se manda su velocidad real cada tick mientras vuelan. */
    private static final java.util.Set<AbstractArrow> FAST_ARROWS = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    @SubscribeEvent
    public void onServerTickArrows(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || FAST_ARROWS.isEmpty()) return;
        FAST_ARROWS.removeIf(arrow -> {
            if (arrow.isRemoved() || arrow.tickCount > 200 || arrow.getDeltaMovement().lengthSqr() < 3.9 * 3.9) return true;
            if (arrow.tickCount == 0) return false; // aún no lo conoce ningún cliente
            net.minecraft.world.phys.Vec3 v = arrow.getDeltaMovement();
            io.github.rpgclasses.network.Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> arrow),
                    new io.github.rpgclasses.network.Packets.ArrowVelocity(arrow.getId(), v.x, v.y, v.z));
            return false;
        });
    }

    @SubscribeEvent
    public void onProjectileSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Projectile projectile)) return;
        if (!(projectile.getOwner() instanceof ServerPlayer shooter) || shooter instanceof FakePlayer) return;
        CompoundTag tag = projectile.getPersistentData();
        if (tag.contains(TAG_FACTOR)) return; // ya procesado (recarga de chunk)
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(projectile.getType());
        if (type != null && "irons_spellbooks".equals(type.getNamespace())) return; // los hechizos van por el muro de Magia

        PlayerClassData data = PlayerClassDataProvider.get(shooter);
        ClassDef classDef = data == null ? null : DefinitionStore.SERVER.classDef(data.state.classId);
        if (classDef == null) return;
        SubclassDef subDef = DefinitionStore.SERVER.subclassDef(data.state.subclassId);

        // Disparo Pesado/Letal suma de forma aditiva con el bonus de arco de la clase.
        double[] shot = projectile instanceof AbstractArrow ? RangerAbilities.takeShot(shooter) : new double[] {0, 0};
        double factor = StatMath.additiveFactor(ModAttributes.value(shooter, ModAttributes.BOW_DAMAGE), shot[0])
                * StatMath.wallDamageFactor(StatCalculator.wall(data.state, classDef, subDef, StatEngine.race(data.state), Ids.BOW));
        if (projectile instanceof AbstractArrow arrow) factor *= RangerPerks.spawnFactor(shooter, arrow);
        tag.putDouble(TAG_FACTOR, factor);
        if (projectile instanceof AbstractArrow arrow) {
            // Alcance: más velocidad sin más daño (el daño de una flecha vanilla depende de su velocidad).
            double velocity = ModAttributes.value(shooter, ModAttributes.PROJECTILE_VELOCITY);
            if (velocity != 0 && velocity > -0.9) {
                arrow.setDeltaMovement(arrow.getDeltaMovement().scale(1 + velocity));
                arrow.setBaseDamage(arrow.getBaseDamage() / (1 + velocity));
                // Vanilla recorta la velocidad que manda al cliente a 3,9 por eje y la flecha se ve desviada.
                if (velocity > 0) FAST_ARROWS.add(arrow);
            }
            // Una flecha que no gastó munición no se puede recoger (evita duplicarlas).
            if (shot[1] > 0 || RangerAbilities.takeCrossbowConserved(shooter)) arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            // Solo las flechas con el arco tensado al máximo pueden ser críticas; el crítico vanilla se desactiva.
            tag.putBoolean(TAG_CAN_CRIT, arrow.isCritArrow());
            arrow.setCritArrow(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        if (source.is(NO_PROC) || event.getAmount() <= 0) return;
        if (!(source.getEntity() instanceof ServerPlayer attacker) || attacker instanceof FakePlayer) return;
        if (isSpell(source) || source.is(DamageTypes.THORNS) || source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypeTags.IS_FIRE)) return;

        PlayerClassData data = PlayerClassDataProvider.get(attacker);
        ClassDef classDef = data == null ? null : DefinitionStore.SERVER.classDef(data.state.classId);
        if (classDef == null) return;
        ProgressState s = data.state;
        SubclassDef subDef = DefinitionStore.SERVER.subclassDef(s.subclassId);
        LivingEntity target = event.getEntity();
        Entity direct = source.getDirectEntity();

        double damage = event.getAmount();
        StringBuilder log = new StringBuilder(String.format(Locale.ROOT, "base %.2f", damage));
        boolean canCrit;

        if (direct instanceof Projectile projectile) {
            CompoundTag tag = projectile.getPersistentData();
            if (!tag.contains(TAG_FACTOR)) return;
            double hit = RangerPerks.hitFactor(attacker, projectile);
            damage *= tag.getDouble(TAG_FACTOR) * hit;
            log.append(String.format(Locale.ROOT, " | proyectil (arco + muro + perks) x%.2f", tag.getDouble(TAG_FACTOR) * hit));
            canCrit = tag.getBoolean(TAG_CAN_CRIT);
        } else if (direct == attacker) {
            WeaponClassifier.Classification weapon = WeaponClassifier.classify(attacker.getMainHandItem());
            boolean isMonk = io.github.rpgclasses.gear.GearClass.MONK.is(attacker);
            if (weapon.martial() && isMonk && attacker.getMainHandItem().getItem() instanceof io.github.rpgclasses.gear.MartialWeaponItem martial) {
                // Rev. 2.20: para el Monje, el arma marcial cuenta como desarmado (cargas, perks y subclase).
                double f = io.github.rpgclasses.gear.GearRules.monkMartialFactor(attacker, martial);
                damage *= f;
                log.append(String.format(Locale.ROOT, " | arma marcial del Monje x%.2f", f));
                double charges = MonkPerks.unarmedFactor(attacker, target);
                if (charges != 1) {
                    damage *= charges;
                    log.append(String.format(Locale.ROOT, " | cargas/impulso x%.2f", charges));
                }
            } else if (weapon.unarmed()) {
                double flat = ModAttributes.value(attacker, ModAttributes.UNARMED_DAMAGE)
                        + io.github.rpgclasses.gear.GearRules.knucklesBonus(attacker, isMonk);
                damage += flat;
                log.append(String.format(Locale.ROOT, " | desarmado +%.2f", flat));
                double palm = MonkSubclasses.onUnarmedHit(attacker, target, damage);
                if (palm != 1) {
                    damage *= palm;
                    log.append(String.format(Locale.ROOT, " | golpe cargado x%.2f", palm));
                }
                double monk = MonkPerks.unarmedFactor(attacker, target);
                if (monk != 1) {
                    damage *= monk;
                    log.append(String.format(Locale.ROOT, " | cargas/impulso x%.2f", monk));
                }
            } else if (weapon.discipline() != null) {
                double wall = StatCalculator.wall(s, classDef, subDef, StatEngine.race(s), weapon.discipline());
                damage *= StatMath.wallDamageFactor(wall);
                log.append(String.format(Locale.ROOT, " | muro %s x%.2f", weapon.discipline(), StatMath.wallDamageFactor(wall)));
            }
            // Mago de Batalla: Eco Marcial cuenta golpes y Filo Imbuido añade daño de la escuela del último conjuro.
            double imbued = io.github.rpgclasses.content.BattleMage.onMeleeHit(attacker, target, damage);
            if (imbued != 1) {
                damage *= imbued;
                log.append(String.format(Locale.ROOT, " | filo imbuido x%.2f", imbued));
            }
            canCrit = true;
        } else {
            return; // daño indirecto que no es ni golpe ni proyectil (explosiones, mascotas...)
        }

        // Furia de Heridas + perk de Furia (Desatada suma, Templada resta).
        double fury = StatCalculator.woundFuryBonus(s, classDef, attacker.getHealth() / attacker.getMaxHealth())
                + BarbarianPerks.furyPerkBonus(attacker);
        if (fury != 0) {
            damage *= Math.max(0.1, 1 + fury);
            log.append(String.format(Locale.ROOT, " | furia x%.2f", Math.max(0.1, 1 + fury)));
        }
        double smite = PaladinPerks.smiteBonus(attacker, target);
        if (smite != 0) {
            damage *= 1 + smite;
            log.append(String.format(Locale.ROOT, " | castigo x%.2f", 1 + smite));
        }
        double war = BannerAura.damageBonus(attacker);
        if (war != 0) {
            damage *= 1 + war;
            log.append(String.format(Locale.ROOT, " | estandarte x%.2f", 1 + war));
        }
        double treacherous = RoguePerks.treacherousBonus(attacker, target);
        if (treacherous != 0) {
            damage *= 1 + treacherous;
            log.append(String.format(Locale.ROOT, " | traición x%.2f", 1 + treacherous));
        }
        double counter = RoguePerks.takeDodgeCounter(attacker);
        if (counter != 0) {
            damage *= 1 + counter;
            log.append(String.format(Locale.ROOT, " | contraataque x%.2f", 1 + counter));
        }
        double condemn = PaladinPerks.condemnBonus(attacker, target);
        if (condemn != 0) {
            damage *= 1 + condemn;
            log.append(String.format(Locale.ROOT, " | condena x%.2f", 1 + condemn));
        }
        boolean melee = direct == attacker;
        double judgement = melee ? PaladinPerks.judgementBonus(attacker) : 0;
        double holyDamage = damage * judgement;
        if (judgement > 0) {
            damage += holyDamage;
            log.append(String.format(Locale.ROOT, " | juicio +%.2f sagrado", holyDamage));
        }

        if (attacker.hasEffect(ModEffects.BERSERKER_RAGE.get())) {
            double bonus = AbilityService.number(AbilityService.params(CombatAbilities.BERSERKER_RAGE), "damage_bonus", 0.25);
            damage *= 1 + bonus;
            log.append(String.format(Locale.ROOT, " | berserker x%.2f", 1 + bonus));
        }
        if (target instanceof Mob mob && mob.getTarget() == attacker && AbilityService.has(attacker, CombatAbilities.STEADY_GUARD)) {
            double bonus = AbilityService.number(AbilityService.params(CombatAbilities.STEADY_GUARD), "damage_bonus", 0.15);
            damage *= 1 + bonus;
            log.append(String.format(Locale.ROOT, " | guardia firme x%.2f", 1 + bonus));
        }

        if (canCrit) {
            double chance = StatMath.critChance(ServerConfig.BASE_CRIT_CHANCE.get(),
                    classDef.critChanceBonus() + ModAttributes.value(attacker, ModAttributes.CRIT_CHANCE), false);
            // Pícaro: todo golpe desde invisibilidad es crítico seguro y suma su bonus de Backstab.
            boolean backstab = RogueAbilities.isStealthed(attacker);
            // Paso de Sombra: crítico seguro contra el objetivo marcado.
            boolean marked = !backstab && melee && RogueAbilities.hasShadowMark(attacker, target);
            if (backstab || marked || attacker.getRandom().nextDouble() < chance) {
                double critBonus = ModAttributes.value(attacker, ModAttributes.CRIT_DAMAGE)
                        + (backstab ? ModAttributes.value(attacker, ModAttributes.BACKSTAB_CRIT_DAMAGE) : 0);
                double multiplier = StatMath.critMultiplier(critBonus);
                damage *= multiplier * io.github.rpgclasses.gear.CurioEffects.critRoll(attacker, multiplier);
                attacker.crit(target);
                io.github.rpgclasses.gear.SetEffects.onCrit(attacker, target, damage, melee);
                log.append(String.format(Locale.ROOT, backstab ? " | CRÍTICO SEGURO (sigilo) x%.2f" : marked ? " | CRÍTICO SEGURO (paso de sombra) x%.2f" : " | CRÍTICO (%.0f%%) x%.2f",
                        backstab || marked ? new Object[] {multiplier} : new Object[] {chance * 100, multiplier}));
            } else {
                log.append(String.format(Locale.ROOT, " | sin crítico (%.0f%%)", chance * 100));
            }
        }

        if (melee) {
            double pierce = RoguePerks.armorPierceFactor(attacker, target, source, damage);
            if (pierce != 1) {
                damage *= pierce;
                log.append(String.format(Locale.ROOT, " | estocada x%.2f", pierce));
            }
            double ignite = io.github.rpgclasses.content.MonkCharges.armorIgnoreFactor(attacker, target, source, damage);
            if (ignite != 1) {
                damage *= ignite;
                log.append(String.format(Locale.ROOT, " | golpe ígneo (armadura) x%.2f", ignite));
            }
        }
        float result = StatMath.sanitizeDamage(damage);
        log.append(String.format(Locale.ROOT, " = %.2f (antes de armadura)", result));
        LAST_BREAKDOWN.put(attacker.getUUID(), log.toString());
        DebugLog.event(attacker, "golpe", "{} → {} ({}, {}): {}", attacker.getGameProfile().getName(), DebugLog.describe(target),
                melee ? "cuerpo a cuerpo con " + attacker.getMainHandItem().getItem() : "proyectil " + DebugLog.describe(direct),
                source.getMsgId(), log);
        event.setAmount(result);

        if (judgement > 0) DebugLog.guard(attacker, "Juicio (área)", () -> PaladinPerks.judgementArea(attacker, target, (float) holyDamage));
        if (direct instanceof Projectile projectile) {
            DebugLog.guard(attacker, "perks de flecha al impactar", () -> RangerPerks.onArrowHit(attacker, projectile, target, result));
        }
        if (melee) {
            DebugLog.guard(attacker, "Toxinas", () -> Toxins.onMeleeHit(attacker, target));
            DebugLog.guard(attacker, "subclase Bárbaro al golpear (Plaga...)", () -> BarbarianSubclasses.onMeleeHit(attacker, target));
            DebugLog.guard(attacker, "Onda de choque", () -> PaladinPerks.shockwave(attacker, target, result));
        }
    }

    /** Daño que RECIBE un jugador: reducciones y penalizaciones de sus habilidades activas. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDefend(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || victim instanceof FakePlayer || event.getAmount() <= 0) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        StringBuilder log = new StringBuilder(String.format(Locale.ROOT, "recibe %.2f (%s de %s)", event.getAmount(),
                event.getSource().getMsgId(), DebugLog.describe(event.getSource().getEntity())));
        double hazard = BarbarianPerks.hazardFactor(victim, event.getSource());
        double endurance = BarbarianPerks.enduranceFactor(victim);
        double factor = hazard * endurance;
        if (hazard != 1) log.append(String.format(Locale.ROOT, " | peligros x%.2f", hazard));
        if (endurance != 1) log.append(String.format(Locale.ROOT, " | aguante x%.2f", endurance));
        double beforeSkin = factor;
        factor = MonkPerks.spiritSkinFactor(victim, event.getSource(), factor);
        if (factor != beforeSkin) log.append(String.format(Locale.ROOT, " | piel espiritual %.2f→%.2f", beforeSkin, factor));
        double tenacity = PaladinSubclasses.tenacityFactor(victim);
        factor *= tenacity;
        if (tenacity != 1) log.append(String.format(Locale.ROOT, " | tenacidad x%.2f", tenacity));
        if (victim.hasEffect(ModEffects.BULWARK.get())) {
            double reduction = CombatAbilities.bulwarkReduction(victim);
            // Baluarte Sanador: cura con lo que Baluarte ha quitado de este golpe.
            float prevented = (float) (event.getAmount() * factor * reduction);
            DebugLog.guard(victim, "Baluarte Sanador", () -> PaladinSubclasses.healingBulwark(victim, prevented));
            factor *= 1 - reduction;
            log.append(String.format(Locale.ROOT, " | baluarte -%.0f%%", reduction * 100));
        }
        if (victim.hasEffect(ModEffects.TAUNT_GUARD.get())) {
            double reduction = AbilityService.number(AbilityService.params(CombatAbilities.TAUNT), "damage_reduction", 0.2);
            factor *= 1 - reduction;
            log.append(String.format(Locale.ROOT, " | provocar -%.0f%%", reduction * 100));
        }
        if (victim.hasEffect(ModEffects.BERSERKER_RAGE.get())) {
            double extra = AbilityService.number(AbilityService.params(CombatAbilities.BERSERKER_RAGE), "damage_taken", 0.2);
            factor *= 1 + extra;
            log.append(String.format(Locale.ROOT, " | berserker +%.0f%%", extra * 100));
        }
        if (factor != 1) event.setAmount(StatMath.sanitizeDamage(event.getAmount() * factor));
        log.append(String.format(Locale.ROOT, " = %.2f (antes de armadura)", event.getAmount()));
        DebugLog.event(victim, "defensa", "{}", log);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_BREAKDOWN.remove(event.getEntity().getUUID());
    }

    /** Los hechizos de Iron's usan tipos de daño de su namespace; su muro va por atributo, no por aquí. */
    private static boolean isSpell(DamageSource source) {
        return source.typeHolder().unwrapKey()
                .map(key -> "irons_spellbooks".equals(key.location().getNamespace()))
                .orElse(false);
    }

    private static ClassDef classOf(ServerPlayer player) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        return data == null ? null : DefinitionStore.SERVER.classDef(data.state.classId);
    }
}
