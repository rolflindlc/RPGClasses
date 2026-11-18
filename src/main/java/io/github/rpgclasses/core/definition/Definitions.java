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
     * {@code icon}: objeto que la representa en el árbol de habilidades (null = el de la clase).
     */
    public record BranchDef(String id, int maxPoints, List<EffectDef> effects, List<PerkDef> perks, String icon) {
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
    /** Objeto del kit; {@code spells} son conjuros de Iron's que se inscriben en él (libros). */
    public record KitItem(String item, int count, String slot, List<KitSpell> spells) {}

    public record KitSpell(String id, int level) {}

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

    /** Subraza: variante de una raza. {@code available} = false mientras sus rasgos no estén programados. */
    public record SubraceDef(String id, String icon, boolean available, List<EffectDef> effects, List<RiteDef> rites) {
        /** Rito que lleva al rango {@code rank + 1}, o null si ya tiene el rango máximo o no hay ritos. */
        public RiteDef riteFor(int rank) {
            return rank >= 0 && rank < rites.size() ? rites.get(rank) : null;
        }
    }

    /**
     * Rito (anexo de razas, regla 5): misión que sube el rango de raza. Todos sus objetivos deben cumplirse.
     * Tipos de objetivo: kill, kill_variety, kill_styles, hits_taken, steals, barters, counter, offer, structure, boss.
     */
    public record RiteDef(String id, String kind, List<ObjectiveDef> objectives) {}

    /** Objetivo de un rito: tipo, cantidad y parámetros propios del tipo (filtros, objetos, condiciones del jefe). */
    public record ObjectiveDef(String type, int count, JsonObject params) {}

    /**
     * Raza (anexo de razas). Sus efectos y los de la subraza se evalúan con nivel = rango + 1 (de 4):
     * los valores por rango son tablas {@code "values": [r0, r1, r2, r3]}. Una mecánica con
     * {@code "min_rank": n} solo está activa a partir de ese rango.
     */
    public record RaceDef(String id, String icon, List<EffectDef> effects, List<SubraceDef> subraces) {
        public SubraceDef subrace(String subraceId) {
            for (SubraceDef s : subraces) if (s.id().equals(subraceId)) return s;
            return null;
        }
    }

    private Definitions() {}
}
