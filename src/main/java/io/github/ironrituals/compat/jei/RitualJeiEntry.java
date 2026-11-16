package io.github.ironrituals.compat.jei;

import io.github.ironrituals.ritual.recipe.RitualRecipe;
import io.github.ironrituals.ritual.recipe.RitualTier;

/** Una entrada de JEI por nivel de ritual. */
public record RitualJeiEntry(RitualRecipe recipe, RitualTier tier) {}
