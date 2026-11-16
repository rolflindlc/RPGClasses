package io.github.ironrituals.ritual;

import io.github.ironrituals.core.ComponentMatcher;
import io.github.ironrituals.ritual.recipe.RitualCatalyst;
import io.github.ironrituals.ritual.recipe.RitualComponent;
import io.github.ironrituals.ritual.recipe.RitualRecipe;
import io.github.ironrituals.ritual.recipe.RitualTier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resultado de buscar en el área central los componentes de un ritual (plan, 2.1): el mayor nivel cuyos componentes
 * completos están presentes y que el círculo permite, más los catalizadores que encajen con lo sobrante.
 *
 * @param taken unidades tomadas de cada {@link ItemEntity} de {@code items}
 */
public record RitualMatch(RitualRecipe recipe, RitualTier tier, List<String> catalysts, List<ItemEntity> items, int[] taken) {

    /**
     * @param circleLevel nivel del círculo válido (0 si no hay)
     * @return el reparto, o {@code null} si no hay componentes de ningún nivel permitido
     */
    @Nullable
    public static RitualMatch find(Level level, RitualRecipe recipe, int circleLevel, List<ItemEntity> items) {
        int[] available = counts(items);
        List<RitualTier> tiers = recipe.tiers();
        for (int i = tiers.size() - 1; i >= 0; i--) {
            RitualTier tier = tiers.get(i);
            if (tier.level() > circleLevel || tier.level() < recipe.minCircle()) continue;
            int[] taken = ComponentMatcher.allocate(available, requirements(level, tier.components(), items));
            if (taken == null) continue;

            List<String> cats = new ArrayList<>();
            Set<String> usedGroups = new HashSet<>();
            int[] rest = ComponentMatcher.subtract(available, taken);
            for (RitualCatalyst cat : recipe.catalysts()) {
                if (cat.minLevel() > tier.level()) continue;
                if (!cat.group().isEmpty() && usedGroups.contains(cat.group())) continue;
                int[] catTaken = ComponentMatcher.allocate(rest, requirements(level, cat.components(), items));
                if (catTaken == null) continue;
                cats.add(cat.id());
                if (!cat.group().isEmpty()) usedGroups.add(cat.group());
                rest = ComponentMatcher.subtract(rest, catTaken);
                for (int k = 0; k < taken.length; k++) taken[k] += catTaken[k];
            }
            return new RitualMatch(recipe, tier, List.copyOf(cats), items, taken);
        }
        return null;
    }

    /** Solo los componentes de un nivel concreto, sin catalizadores (prórroga, sección 2.5). */
    @Nullable
    public static RitualMatch exactTier(Level level, RitualRecipe recipe, RitualTier tier, List<ItemEntity> items) {
        int[] taken = ComponentMatcher.allocate(counts(items), requirements(level, tier.components(), items));
        return taken == null ? null : new RitualMatch(recipe, tier, List.of(), items, taken);
    }

    private static int[] counts(List<ItemEntity> items) {
        int[] a = new int[items.size()];
        for (int i = 0; i < a.length; i++) a[i] = items.get(i).getItem().getCount();
        return a;
    }

    private static List<ComponentMatcher.Requirement> requirements(Level level, List<RitualComponent> comps, List<ItemEntity> items) {
        List<ComponentMatcher.Requirement> reqs = new ArrayList<>(comps.size());
        for (RitualComponent c : comps) {
            reqs.add(new ComponentMatcher.Requirement(c.count(), i -> c.test(items.get(i).getItem(), level)));
        }
        return reqs;
    }
}
