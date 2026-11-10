package io.github.rpgclasses.core.definition;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

/** Definiciones inmutables leídas del datapack. Lógica pura. */
public final class Definitions {

    /** Efecto pasivo: un tipo registrado más sus parámetros en bruto (los interpreta el tipo). */
    public record EffectDef(String type, JsonObject params) {}

    /** Rama: cada punto sube a la vez todos sus efectos; todos llegan al máximo con maxPoints. */
    public record BranchDef(String id, int maxPoints, List<EffectDef> effects) {}

    /**
     * Hito. Si {@code subclassLevel} es true, {@code level} se compara con el nivel de subclase;
     * si no, con el Nivel Total. {@code upgrades} (opcional) es la habilidad base que mejora:
     * el hito queda latente hasta que el jugador tenga esa habilidad.
     */
    public record MilestoneDef(int level, boolean subclassLevel, String grants, String upgrades) {}

    /** Objeto del equipo inicial. slot: mainhand, offhand, head, chest, legs, feet o inventory. */
    public record KitItem(String item, int count, String slot) {}

    public record ClassDef(
            String id,
            String icon,
            List<String> roles,
            List<KitItem> startingKit,
            double critChanceBonus,
            Map<String, Double> walls,
            boolean armorDisablesPassives,
            List<BranchDef> branches,
            List<MilestoneDef> milestones,
            List<String> subclasses) {

        public BranchDef branch(String branchId) {
            for (BranchDef b : branches) if (b.id().equals(branchId)) return b;
            return null;
        }
    }

    public record SubclassDef(
            String id,
            String parentClass,
            String icon,
            List<EffectDef> effects,
            List<MilestoneDef> milestones) {}

    /** Habilidad: sus números (cooldown, duración, porcentajes). El comportamiento lo pone el código (fase 4). */
    public record AbilityDef(String id, JsonObject params) {}

    private Definitions() {}
}
