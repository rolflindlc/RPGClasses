package io.github.rpgclasses.compat.irons.music;

import io.github.rpgclasses.bard.Bard;
import io.github.rpgclasses.bard.BardAuras;
import io.github.rpgclasses.bard.BardCombat;
import io.github.rpgclasses.bard.BardEffects;
import io.github.rpgclasses.bard.CuttingNoteEntity;
import io.github.rpgclasses.bard.GrandPianoEntity;
import io.github.rpgclasses.bard.MusicSpellInfo;
import io.github.rpgclasses.content.CombatAbilities;
import io.github.rpgclasses.content.race.Races;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.Allies;
import io.github.rpgclasses.server.MechanicService;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.config.SpellConfigManager;
import io.redspace.ironsspellbooks.api.config.SpellConfigParameter;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Los 12 conjuros de la escuela musical como conjuros de Iron's (instantáneos). Los números salen de
 * {@link MusicSpellInfo}; el poder es el de Iron's (poder de hechizo × poder musical × multiplicador de la config del
 * conjuro). Los buffs y debuffs reciben solo parte del poder musical (parámetro {@code buff_power_scaling} de Ritmo).
 */
final class MusicSpells {
    private MusicSpells() {}

    static AbstractSpell create(MusicSpellInfo info) {
        return switch (info) {
            case CUTTING_NOTE -> new CuttingNote(info);
            case MARCH, WAR_HYMN, REST_BALLAD, ELEGY, IRON_CHORALE, MANA_CADENCE -> new Aura(info);
            case RUMBLE -> new Rumble(info);
            case DISSONANCE -> new Dissonance(info);
            case GRAND_PIANO -> new GrandPiano(info);
            case FORCED_DANCE -> new ForcedDance(info);
            case FINAL_REQUIEM -> new FinalRequiem(info);
        };
    }

    // ================================================================== base

    abstract static class MusicSpell extends AbstractSpell {
        protected final MusicSpellInfo info;
        private final DefaultConfig config;

        MusicSpell(MusicSpellInfo info) {
            this.info = info;
            this.baseManaCost = info.mana;
            this.manaCostPerLevel = 0;
            this.baseSpellPower = 1;
            this.spellPowerPerLevel = 0;
            this.castTime = 0;
            SpellRarity rarity = info.combo >= 30 ? SpellRarity.EPIC : info.combo >= 20 ? SpellRarity.RARE
                    : info.combo >= 10 ? SpellRarity.UNCOMMON : SpellRarity.COMMON;
            this.config = new DefaultConfig().setMinRarity(rarity).setSchoolResource(MusicBridge.SCHOOL_ID)
                    .setMaxLevel(info.maxLevel).setCooldownSeconds(info.cooldownSeconds).setAllowCrafting(false).build();
        }

        @Override
        public ResourceLocation getSpellResource() {
            return info.rl();
        }

        @Override
        public DefaultConfig getDefaultConfig() {
            return config;
        }

        @Override
        public CastType getCastType() {
            return CastType.INSTANT;
        }

        @Override
        public int getManaCost(int level) {
            double mult = SpellConfigManager.getSpellConfigValue(this, SpellConfigParameter.MANA_MULTIPLIER);
            return (int) Math.round(info.manaCost(level) * mult);
        }

        @Override
        public boolean allowLooting() {
            return false;
        }

        @Override
        public boolean allowCrafting() {
            return false;
        }

        /** Multiplicador de poder del lanzador (1 = 100 %). */
        protected double power(LivingEntity caster) {
            return caster == null ? 1 : getEntityPowerMultiplier(caster);
        }

        /** Los buffs y debuffs reciben solo una parte del poder musical (propuesta: la mitad). */
        protected double buffPower(LivingEntity caster) {
            double scaling = AbilityService.number(AbilityService.params(Bard.RHYTHM), "buff_power_scaling", 0.5);
            return Math.max(0, 1 + (power(caster) - 1) * scaling);
        }

        protected double seconds(int level) {
            return info.duration + info.durationBonus(level);
        }

        protected double area(int level) {
            return info.area + info.areaBonus(level);
        }

        @Override
        public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
            List<MutableComponent> out = new ArrayList<>();
            info(level, caster, out);
            out.add(Component.translatable("spell.rites_and_paths.info.combo", info.combo));
            return out;
        }

        protected abstract void info(int level, LivingEntity caster, List<MutableComponent> out);

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
            if (entity instanceof ServerPlayer player) cast(player, spellLevel);
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
        }

        protected abstract void cast(ServerPlayer player, int level);

        protected static String num(double v) {
            return Math.abs(v - Math.round(v)) < 0.05 ? Long.toString(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
        }

        protected static DamageSource music(ServerPlayer p) {
            return new DamageSource(p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(CuttingNoteEntity.MUSIC_MAGIC), p);
        }

        protected static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
        }

        protected static void ring(ServerLevel level, Vec3 c, double r, ParticleOptions particle, int n) {
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                level.sendParticles(particle, c.x + Math.cos(a) * r, c.y + 0.4, c.z + Math.sin(a) * r, 1, 0, 0.1, 0, 0);
            }
        }
    }

    // ================================================================== Nota Cortante

    static final class CuttingNote extends MusicSpell {
        CuttingNote(MusicSpellInfo info) {
            super(info);
        }

        private float damage(int level, LivingEntity caster) {
            return (float) (info.value * info.damageFactor(level) * power(caster));
        }

        private static int pierce(int level) {
            return level >= 10 ? 2 : level >= 5 ? 1 : 0;
        }

        @Override
        protected void info(int level, LivingEntity caster, List<MutableComponent> out) {
            out.add(Component.translatable("spell.rites_and_paths.info.damage", num(damage(level, caster))));
            out.add(Component.translatable("spell.rites_and_paths.info.range", num(area(level))));
            if (pierce(level) > 0) out.add(Component.translatable("spell.rites_and_paths.info.pierce", pierce(level)));
        }

        @Override
        protected void cast(ServerPlayer p, int level) {
            CuttingNoteEntity.shoot(p, damage(level, p), pierce(level), area(level));
            sound(p, SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.6f);
        }
    }

    // ================================================================== auras

    static final class Aura extends MusicSpell {
        Aura(MusicSpellInfo info) {
            super(info);
        }

        private BardAuras.Kind kind() {
            return switch (info) {
                case MARCH -> BardAuras.Kind.MARCH;
                case WAR_HYMN -> BardAuras.Kind.WAR_HYMN;
                case REST_BALLAD -> BardAuras.Kind.REST_BALLAD;
                case ELEGY -> BardAuras.Kind.ELEGY;
                case IRON_CHORALE -> BardAuras.Kind.IRON_CHORALE;
                default -> BardAuras.Kind.MANA_CADENCE;
            };
        }

        private double potency(int level, LivingEntity caster) {
            return info.value * info.potencyFactor(level) * buffPower(caster);
        }

        @Override
        protected void info(int level, LivingEntity caster, List<MutableComponent> out) {
            double p = potency(level, caster);
            switch (info) {
                case MARCH -> out.add(Component.translatable("spell.rites_and_paths.info.speed", num(20 * p), num(10 * p)));
                case WAR_HYMN -> out.add(Component.translatable("spell.rites_and_paths.info.damage_bonus", num(100 * p)));
                case REST_BALLAD -> out.add(Component.translatable("spell.rites_and_paths.info.regen", num(p)));
                case ELEGY -> out.add(Component.translatable("spell.rites_and_paths.info.debuff"));
                case IRON_CHORALE -> out.add(Component.translatable("spell.rites_and_paths.info.reduction", num(Math.min(60, 100 * p))));
                default -> out.add(Component.translatable("spell.rites_and_paths.info.mana_regen", num(100 * p)));
            }
            out.add(Component.translatable("spell.rites_and_paths.info.duration", num(seconds(level))));
            out.add(Component.translatable("spell.rites_and_paths.info.radius", num(8 + info.areaBonus(level))));
        }

        @Override
        protected void cast(ServerPlayer p, int level) {
            double secs = seconds(level);
            if (info == MusicSpellInfo.ELEGY) secs = BardCombat.controlTicks(p, secs) / 20.0; // Voz Hipnótica
            BardAuras.start(p, kind(), (int) Math.round(secs * 20), potency(level, p), info.areaBonus(level));
            SoundEvent s = switch (info) {
                case MARCH -> SoundEvents.NOTE_BLOCK_SNARE.value();
                case WAR_HYMN -> SoundEvents.NOTE_BLOCK_DIDGERIDOO.value();
                case REST_BALLAD -> SoundEvents.NOTE_BLOCK_HARP.value();
                case ELEGY -> SoundEvents.NOTE_BLOCK_BASS.value();
                case IRON_CHORALE -> SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value();
                default -> SoundEvents.NOTE_BLOCK_CHIME.value();
            };
            sound(p, s, 1.2f, info == MusicSpellInfo.ELEGY ? 0.6f : 1.0f);
        }
    }

    // ================================================================== Estruendo

    static final class Rumble extends MusicSpell {
        private static final double HALF_ANGLE_COS = Math.cos(Math.toRadians(40));

        Rumble(MusicSpellInfo info) {
            super(info);
        }

        private float damage(int level, LivingEntity caster) {
            return (float) (info.value * info.damageFactor(level) * power(caster));
        }

        @Override
        protected void info(int level, LivingEntity caster, List<MutableComponent> out) {
            out.add(Component.translatable("spell.rites_and_paths.info.damage", num(damage(level, caster))));
            out.add(Component.translatable("spell.rites_and_paths.info.range", num(area(level))));
        }

        @Override
        protected void cast(ServerPlayer p, int level) {
            ServerLevel lvl = p.serverLevel();
            double len = area(level);
            float dmg = damage(level, p);
            Vec3 eye = p.getEyePosition();
            Vec3 look = p.getLookAngle().normalize();
            for (LivingEntity e : lvl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(len))) {
                if (!Allies.isEnemy(p, e)) continue;
                Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
                double d = to.length();
                if (d > len || d < 1e-3 || to.normalize().dot(look) < HALF_ANGLE_COS) continue;
                e.hurt(music(p), dmg);
                Vec3 flat = new Vec3(to.x, 0, to.z);
                if (flat.lengthSqr() > 1e-4) e.knockback(1.2, -flat.x, -flat.z);
            }
            for (int i = 1; i <= 3; i++) {
                Vec3 at = eye.add(look.scale(len * i / 3.5));
                lvl.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            }
            sound(p, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 2f, 0.6f);
            sound(p, SoundEvents.RAVAGER_ROAR, 0.5f, 1.5f);
        }
    }

    // ================================================================== Disonancia

    static final class Dissonance extends MusicSpell {
        Dissonance(MusicSpellInfo info) {
            super(info);
        }

        @Override
        protected void info(int level, LivingEntity caster, List<MutableComponent> out) {
            out.add(Component.translatable("spell.rites_and_paths.info.control", num(info.value), num(info.duration)));
            out.add(Component.translatable("spell.rites_and_paths.info.radius", num(area(level))));
        }

        @Override
        protected void cast(ServerPlayer p, int level) {
            ServerLevel lvl = p.serverLevel();
            double r = area(level);
            int silence = BardCombat.controlTicks(p, info.value);
            int stun = BardCombat.controlTicks(p, info.duration);
            for (LivingEntity e : lvl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r))) {
                if (e.distanceToSqr(p) > r * r || !Allies.isEnemy(p, e)) continue;
                boolean boss = Races.isBoss(e);
                BardCombat.silence(p, e, boss ? silence / 2 : silence);
                CombatAbilities.stun(e, boss ? stun / 2 : stun);
                lvl.sendParticles(ParticleTypes.ENCHANTED_HIT, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 6, 0.3, 0.2, 0.3, 0.1);
            }
            ring(lvl, p.position(), r, ParticleTypes.NOTE, 32);
            sound(p, SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 2f, 0.5f);
            sound(p, SoundEvents.NOTE_BLOCK_BIT.value(), 1.5f, 0.53f);
        }
    }

    // ================================================================== objetivo (Piano de Cola y Baile Forzado)

    abstract static class Targeted extends MusicSpell {
        private static final Map<UUID, Integer> TARGETS = new HashMap<>();
        private static final double RANGE = 24;

        Targeted(MusicSpellInfo info) {
            super(info);
        }

        protected boolean valid(ServerPlayer p, LivingEntity e) {
            return Allies.isEnemy(p, e);
        }

        /** Antes de gastar maná: sin objetivo a la vista no se lanza. */
        @Override
        public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
            if (!(entity instanceof ServerPlayer p)) return true;
            LivingEntity t = find(p);
            if (t == null) {
                p.displayClientMessage(Component.translatable("msg.rites_and_paths.bard.no_target"), true);
                return false;
            }
            TARGETS.put(p.getUUID(), t.getId());
            return true;
        }

        protected LivingEntity target(ServerPlayer p) {
            Integer id = TARGETS.remove(p.getUUID());
            Entity e = id == null ? null : p.level().getEntity(id);
            if (e instanceof LivingEntity le && le.isAlive() && le.distanceToSqr(p) < RANGE * RANGE * 1.5) return le;
            return find(p);
        }

        private LivingEntity find(ServerPlayer p) {
            Vec3 eye = p.getEyePosition();
            Vec3 look = p.getLookAngle();
            Vec3 end = eye.add(look.scale(RANGE));
            BlockHitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
            AABB box = p.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end, box,
                    e -> e instanceof LivingEntity le && le.isAlive() && valid(p, le));
            if (hit != null && hit.getEntity() instanceof LivingEntity le) return le;
            // Ayuda de puntería: el enemigo visible más alineado con la mirada (6°).
            LivingEntity best = null;
            double bestDot = Math.cos(Math.toRadians(6));
            for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(RANGE))) {
                if (e == p || !e.isAlive() || !valid(p, e) || !p.hasLineOfSight(e)) continue;
                Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
                if (to.lengthSqr() > RANGE * RANGE) continue;
                double dot = to.normalize().dot(look);
                if (dot > bestDot) {
                    bestDot = dot;
                    best = e;
                }
            }
            return best;
        }
    }

    static final class GrandPiano extends Targeted {
        GrandPiano(MusicSpellInfo info) {
            super(info);
        }

        private float damage(int level, LivingEntity caster) {
            return (float) (info.value * info.damageFactor(level) * power(caster));
        }

        @Override
        protected void info(int level, LivingEntity caster, List<MutableComponent> out) {
            out.add(Component.translatable("spell.rites_and_paths.info.damage", num(damage(level, caster))));
            out.add(Component.translatable("spell.rites_and_paths.info.radius", num(area(level))));
            if (level >= info.maxLevel) out.add(Component.translatable("spell.rites_and_paths.info.second_piano"));
        }

        @Override
        protected void cast(ServerPlayer p, int level) {
            LivingEntity t = target(p);
            if (t == null) return;
            float dmg = damage(level, p);
            int stun = (int) Math.round(info.duration * 20);
            GrandPianoEntity.drop(p, t, dmg, area(level), stun, 0);
            if (level >= info.maxLevel) GrandPianoEntity.drop(p, t, dmg / 2, area(level), stun, 12);
            sound(p, SoundEvents.NOTE_BLOCK_HARP.value(), 1.2f, 0.7f);
        }
    }

    static final class ForcedDance extends Targeted {
        ForcedDance(MusicSpellInfo info) {
            super(info);
        }

        @Override
        protected boolean valid(ServerPlayer p, LivingEntity e) {
            return super.valid(p, e) && !Races.isBoss(e);
        }

        private double mobSeconds(int level) {
            return info.duration + 0.5 * (Math.min(level, info.maxLevel) - 1);
        }

        private double playerSeconds(int level) {
            return info.duration / 2 + 0.25 * (Math.min(level, info.maxLevel) - 1);
        }

        @Override
        protected void info(int level, LivingEntity caster, List<MutableComponent> out) {
            out.add(Component.translatable("spell.rites_and_paths.info.dance", num(mobSeconds(level)), num(playerSeconds(level))));
        }

        @Override
        protected void cast(ServerPlayer p, int level) {
            LivingEntity t = target(p);
            if (t == null) return;
            int ticks = BardCombat.controlTicks(p, t instanceof Player ? playerSeconds(level) : mobSeconds(level));
            BardCombat.dance(p, t, ticks);
            // Contagio (Encantador): los mobs normales cerca del bailarín pueden unirse al baile.
            var contagion = MechanicService.find(p, Bard.CONTAGION);
            if (contagion != null) {
                double r = contagion.number("radius", 3);
                int max = (int) contagion.number("max_extra", 3), joined = 0;
                for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(r))) {
                    if (joined >= max) break;
                    if (e == t || !(e instanceof Mob) || Races.isBoss(e) || !Allies.isEnemy(p, e) || BardCombat.dancing(e)) continue;
                    if (p.getRandom().nextDouble() >= contagion.number("chance", 0.5)) continue;
                    BardCombat.dance(p, e, ticks);
                    joined++;
                }
            }
            p.serverLevel().sendParticles(ParticleTypes.NOTE, t.getX(), t.getY() + t.getBbHeight() + 0.3, t.getZ(), 8, 0.4, 0.2, 0.4, 1);
            sound(p, SoundEvents.NOTE_BLOCK_BIT.value(), 1.2f, 1.2f);
        }
    }

    // ================================================================== Réquiem Final

    static final class FinalRequiem extends MusicSpell {
        FinalRequiem(MusicSpellInfo info) {
            super(info);
        }

        private float damage(int level, LivingEntity caster) {
            return (float) (info.value * info.damageFactor(level) * power(caster));
        }

        private int fervor(int level, LivingEntity caster) {
            return (int) Math.round(20 * info.potencyFactor(level) * buffPower(caster));
        }

        @Override
        protected void info(int level, LivingEntity caster, List<MutableComponent> out) {
            out.add(Component.translatable("spell.rites_and_paths.info.damage", num(damage(level, caster))));
            out.add(Component.translatable("spell.rites_and_paths.info.radius", num(area(level))));
            out.add(Component.translatable("spell.rites_and_paths.info.requiem", fervor(level, caster), num(seconds(level))));
        }

        @Override
        protected void cast(ServerPlayer p, int level) {
            ServerLevel lvl = p.serverLevel();
            double r = area(level);
            float dmg = damage(level, p);
            int pts = fervor(level, p);
            int ticks = (int) Math.round(seconds(level) * 20);
            for (LivingEntity e : lvl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r))) {
                if (e.distanceToSqr(p) > r * r || !e.isAlive()) continue;
                if (Allies.isEnemy(p, e)) {
                    e.hurt(music(p), dmg);
                } else if (Allies.isAlly(p, e) && pts > 0) {
                    e.addEffect(new MobEffectInstance(BardEffects.REQUIEM_FERVOR.get(), ticks, Math.min(254, pts - 1), false, true, true), p);
                }
            }
            ring(lvl, p.position(), r, ParticleTypes.SONIC_BOOM, 10);
            ring(lvl, p.position(), r * 0.6, ParticleTypes.NOTE, 24);
            sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.5f, 1.0f);
            sound(p, SoundEvents.NOTE_BLOCK_BELL.value(), 2f, 0.5f);
        }
    }
}
