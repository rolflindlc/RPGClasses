package io.github.rpgclasses.difficulty;

import io.github.rpgclasses.config.ServerConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * Vida y daño del mob según T, con modificadores permanentes (se guardan con el mob y son compatibles con otros mods).
 * No se toca la armadura (la pone el equipo) ni la velocidad (choca con las animaciones de Epic Fight).
 */
public final class ThreatStats {
    private static final UUID HEALTH_ID = UUID.fromString("5b1c3a52-7f0e-4d11-9a6e-0c4f2d7e9a01");
    private static final UUID DAMAGE_ID = UUID.fromString("5b1c3a52-7f0e-4d11-9a6e-0c4f2d7e9a02");

    private ThreatStats() {}

    public static void apply(Mob mob, double threat) {
        if (threat < ServerConfig.DIFFICULTY_MIN_THREAT.get()) return;
        boolean health = add(mob, Attributes.MAX_HEALTH, HEALTH_ID, "rites_and_paths threat health",
                ServerConfig.DIFFICULTY_HEALTH_PER_T.get() * threat);
        add(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, "rites_and_paths threat damage",
                ServerConfig.DIFFICULTY_DAMAGE_PER_T.get() * threat);
        if (health) mob.setHealth(mob.getMaxHealth());
    }

    /** MULTIPLY_BASE: base x (1 + amount), sumado a otros bonus porcentuales de la base. */
    private static boolean add(Mob mob, Attribute attribute, UUID id, String name, double amount) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance == null || amount <= 0 || instance.getModifier(id) != null) return false;
        instance.addPermanentModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_BASE));
        return true;
    }
}
