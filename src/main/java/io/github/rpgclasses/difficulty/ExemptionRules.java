package io.github.rpgclasses.difficulty;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.config.ServerConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/** Qué mobs no reciben dificultad. Se comprueba, por orden, antes de calcular T. */
public final class ExemptionRules {
    /** Entidades (de cualquier mod) que nunca reciben dificultad: jefes, por datapack. */
    public static final TagKey<EntityType<?>> EXEMPT_TAG =
            TagKey.create(Registries.ENTITY_TYPE, RpgClasses.id("difficulty_exempt"));

    public enum Reason {
        NONE("recibe dificultad"),
        NOT_HOSTILE("no es hostil"),
        NAMED("tiene nombre"),
        TAG("está en el tag difficulty_exempt"),
        HEALTH("tiene más vida base que el umbral"),
        SAFE_ZONE("está en una zona protegida");

        public final String text;

        Reason(String text) { this.text = text; }
    }

    private ExemptionRules() {}

    public static Reason check(Mob mob, ServerLevel level, Vec3 pos) {
        if (!(mob instanceof Enemy)) return Reason.NOT_HOSTILE;
        if (mob.hasCustomName()) return Reason.NAMED;
        if (mob.getType().is(EXEMPT_TAG)) return Reason.TAG;
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null && health.getBaseValue() > ServerConfig.DIFFICULTY_HEALTH_EXEMPT.get()) return Reason.HEALTH;
        if (isInSafeZone(level, pos)) return Reason.SAFE_ZONE;
        return Reason.NONE;
    }

    public static boolean isInSafeZone(ServerLevel level, Vec3 pos) {
        return RespawnIndex.get(level.getServer()).isProtected(level, pos, ServerConfig.DIFFICULTY_SAFE_RADIUS.get());
    }
}
