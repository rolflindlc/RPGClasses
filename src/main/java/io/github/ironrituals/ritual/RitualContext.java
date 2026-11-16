package io.github.ironrituals.ritual;

import io.github.ironrituals.config.ServerConfig;
import io.github.ironrituals.core.RitualMath;
import io.github.ironrituals.ritual.active.ActiveRitual;
import io.github.ironrituals.ritual.active.RitualSavedData;
import io.github.ironrituals.ritual.recipe.RitualRecipe;
import io.github.ironrituals.ritual.recipe.RitualTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Todo lo que un efecto necesita para aplicarse.
 *
 * @param souls      dagas consumidas, con su alma aún dentro (copias; las reales vuelven vacías al núcleo)
 * @param consumed   componentes consumidos (sin las dagas)
 * @param leftovers  ítems que siguen en el área central y no forman parte del ritual (p. ej. el objeto de la Forja)
 * @param schoolPower poder de hechizo del lanzador en la escuela del ritual
 */
public record RitualContext(ServerLevel level, BlockPos core, ServerPlayer caster, RitualRecipe recipe, RitualTier tier,
                            List<String> catalysts, List<ItemStack> souls, List<ItemStack> consumed,
                            List<ItemEntity> leftovers, double schoolPower) {

    public int ritualLevel() {
        return tier.level();
    }

    public boolean hasCatalyst(String id) {
        return catalysts.contains(id);
    }

    public CompoundTag params() {
        return tier.params();
    }

    public int paramInt(String key, int fallback) {
        return tier.params().contains(key) ? tier.params().getInt(key) : fallback;
    }

    public double paramDouble(String key, double fallback) {
        return tier.params().contains(key) ? tier.params().getDouble(key) : fallback;
    }

    /** Multiplicador de duración por poder de hechizo (sección 2.3). */
    public double durationMultiplier() {
        return RitualMath.durationMultiplier(schoolPower, ServerConfig.DURATION_SCALE_CAP.get());
    }

    /** Duración base del nivel en ticks, escalada por poder de hechizo. */
    public long scaledDurationTicks() {
        return Math.round(tier.durationSeconds() * 20L * durationMultiplier());
    }

    /** Registra el ritual como persistente anclado al núcleo (zonas, estados con duración). */
    public ActiveRitual startPersistent(int radius, long durationTicks, CompoundTag data) {
        ActiveRitual r = new ActiveRitual(UUID.randomUUID(), recipe.ritual(), tier.level(), caster.getUUID(),
                caster.getGameProfile().getName(), GlobalPos.of(level.dimension(), core), radius, catalysts, durationTicks, data);
        RitualSavedData.get(level.getServer()).add(r);
        return r;
    }
}
