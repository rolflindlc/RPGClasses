package io.github.rpgclasses.server;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.config.ServerConfig;
import io.github.rpgclasses.content.BannerAura;
import io.github.rpgclasses.content.BarbarianPerks;
import io.github.rpgclasses.content.CombatAbilities;
import io.github.rpgclasses.content.PaladinPerks;
import io.github.rpgclasses.content.Toxins;
import io.github.rpgclasses.content.RangerAbilities;
import io.github.rpgclasses.content.RogueAbilities;
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
                * StatMath.wallDamageFactor(StatCalculator.wall(data.state, classDef, subDef, Ids.BOW));
        tag.putDouble(TAG_FACTOR, factor);
        if (projectile instanceof AbstractArrow arrow) {
            // Alcance: más velocidad sin más daño (el daño de una flecha vanilla depende de su velocidad).
            double velocity = ModAttributes.value(shooter, ModAttributes.PROJECTILE_VELOCITY);
            if (velocity != 0 && velocity > -0.9) {
                arrow.setDeltaMovement(arrow.getDeltaMovement().scale(1 + velocity));
                arrow.setBaseDamage(arrow.getBaseDamage() / (1 + velocity));
            }
            // Una flecha que no gastó munición no se puede recoger (evita duplicarlas).
            if (shot[1] > 0) arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
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
            damage *= tag.getDouble(TAG_FACTOR);
            log.append(String.format(Locale.ROOT, " | proyectil (arco + muro) x%.2f", tag.getDouble(TAG_FACTOR)));
            canCrit = tag.getBoolean(TAG_CAN_CRIT);
        } else if (direct == attacker) {
            WeaponClassifier.Classification weapon = WeaponClassifier.classify(attacker.getMainHandItem());
            if (weapon.unarmed()) {
                double flat = ModAttributes.value(attacker, ModAttributes.UNARMED_DAMAGE);
                damage += flat;
                log.append(String.format(Locale.ROOT, " | desarmado +%.2f", flat));
            } else if (weapon.discipline() != null) {
                double wall = StatCalculator.wall(s, classDef, subDef, weapon.discipline());
                damage *= StatMath.wallDamageFactor(wall);
                log.append(String.format(Locale.ROOT, " | muro %s x%.2f", weapon.discipline(), StatMath.wallDamageFactor(wall)));
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
            if (backstab || attacker.getRandom().nextDouble() < chance) {
                double critBonus = ModAttributes.value(attacker, ModAttributes.CRIT_DAMAGE)
                        + (backstab ? ModAttributes.value(attacker, ModAttributes.BACKSTAB_CRIT_DAMAGE) : 0);
                double multiplier = StatMath.critMultiplier(critBonus);
                damage *= multiplier;
                attacker.crit(target);
                log.append(String.format(Locale.ROOT, backstab ? " | CRÍTICO SEGURO (sigilo) x%.2f" : " | CRÍTICO (%.0f%%) x%.2f",
                        backstab ? new Object[] {multiplier} : new Object[] {chance * 100, multiplier}));
            } else {
                log.append(String.format(Locale.ROOT, " | sin crítico (%.0f%%)", chance * 100));
            }
        }

        float result = StatMath.sanitizeDamage(damage);
        log.append(String.format(Locale.ROOT, " = %.2f (antes de armadura)", result));
        LAST_BREAKDOWN.put(attacker.getUUID(), log.toString());
        event.setAmount(result);

        if (judgement > 0) PaladinPerks.judgementArea(attacker, target, (float) holyDamage);
        if (melee) {
            Toxins.onMeleeHit(attacker, target);
            PaladinPerks.shockwave(attacker, target, result);
        }
    }

    /** Daño que RECIBE un jugador: reducciones y penalizaciones de sus habilidades activas. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDefend(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || victim instanceof FakePlayer || event.getAmount() <= 0) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        double factor = BarbarianPerks.hazardFactor(victim, event.getSource()) * BarbarianPerks.enduranceFactor(victim);
        if (victim.hasEffect(ModEffects.BULWARK.get())) factor *= 1 - CombatAbilities.bulwarkReduction(victim);
        if (victim.hasEffect(ModEffects.TAUNT_GUARD.get())) {
            factor *= 1 - AbilityService.number(AbilityService.params(CombatAbilities.TAUNT), "damage_reduction", 0.2);
        }
        if (victim.hasEffect(ModEffects.BERSERKER_RAGE.get())) {
            factor *= 1 + AbilityService.number(AbilityService.params(CombatAbilities.BERSERKER_RAGE), "damage_taken", 0.2);
        }
        if (factor != 1) event.setAmount(StatMath.sanitizeDamage(event.getAmount() * factor));
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
