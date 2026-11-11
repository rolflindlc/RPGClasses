package io.github.rpgclasses.core.definition;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

/** Definiciones inmutables leídas del datapack. Lógica pura. */
public final class Definitions {

    /** Efecto pasivo: un tipo registrado más sus parámetros en bruto (los interpreta el tipo). */
    public record EffectDef(String type, JsonObject params) {}

    /**
     * Perk de rama (regla 11): al llenar la rama se elige uno de sus perks. Sus efectos se aplican
     * enteros (sin escalar), como los rasgos de clase.
     */
    public record PerkDef(String id, List<EffectDef> effects) {}

    /**
     * Rama: cada punto sube a la vez todos sus efectos; todos llegan al máximo con maxPoints.
     * {@code perks}: las opciones que se eligen al llenarla (2 en el diseño).
     */
    public record BranchDef(String id, int maxPoints, List<EffectDef> effects, List<PerkDef> perks) {
        public PerkDef perk(String perkId) {
            for (PerkDef p : perks) if (p.id().equals(perkId)) return p;
            return null;
        }
    }

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
            /** Muros fijos por escuela de Iron's (id de escuela → 0..1); 1 = la escuela no se puede usar (regla 14). */
            Map<String, Double> schoolWalls,
            /** Escuelas de Iron's a las que no afecta el muro de Magia (Paladín: sagrada). */
            List<String> magicWallExemptSchools,
            /** Rasgos base (regla 12): efectos fijos, completos desde que se elige la clase. */
            List<EffectDef> traits,
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
