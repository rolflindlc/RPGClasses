package io.github.ironrituals.compat;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/** Único punto de contacto con la API de ISS para maná y escuelas: si la API cambia, se corrige aquí. */
public final class IssCompat {
    private IssCompat() {}

    public static float mana(ServerPlayer player) {
        return MagicData.getPlayerMagicData(player).getMana();
    }

    public static float maxMana(ServerPlayer player) {
        return (float) player.getAttributeValue(AttributeRegistry.MAX_MANA.get());
    }

    /** Gasta maná si hay suficiente. */
    public static boolean trySpend(ServerPlayer player, float amount) {
        MagicData data = MagicData.getPlayerMagicData(player);
        if (data.getMana() < amount) return false;
        data.setMana(data.getMana() - amount);
        return true;
    }

    @Nullable
    private static SchoolType school(ResourceLocation id) {
        try {
            return SchoolRegistry.getSchool(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Poder de hechizo efectivo para una escuela: poder general × poder de la escuela (como hace ISS). */
    public static double schoolPower(LivingEntity entity, ResourceLocation schoolId) {
        double general = entity.getAttributeValue(AttributeRegistry.SPELL_POWER.get());
        SchoolType school = school(schoolId);
        return general * (school == null ? 1.0 : school.getPowerFor(entity));
    }

    public static Component schoolName(ResourceLocation schoolId) {
        SchoolType school = school(schoolId);
        return school == null ? Component.literal(schoolId.getPath()) : school.getDisplayName();
    }
}
