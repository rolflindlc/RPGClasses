package io.github.rpgclasses.compat.irons;

import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.server.DebugLog;
import io.github.rpgclasses.server.StatEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Conjuros que se lanzan sin el muro de Magia de la clase ({@code magic_wall_exempt_spells} en el JSON de la clase):
 * el Ranger lanza sin él los conjuros de flechas mágicas de Iron's (Flecha Mágica y Lluvia de Flechas).
 *
 * <p>El muro es un modificador multiplicativo sobre el poder de hechizo ({@code poder × (1 − muro)}). Durante el
 * lanzamiento de uno de esos conjuros se añade el modificador contrario ({@code ×1/(1 − muro)}), de modo que el poder
 * queda sin muro, y se quita al terminar {@code castSpell} ({@link #end()}, llamado desde el mixin) o, como red de
 * seguridad, al final del tick. Iron's calcula el daño de esas flechas al lanzar, así que basta con esa ventana.
 * El muro del resto de conjuros, y el de las demás clases, no cambia.
 */
public final class SpellExemptions {
    private static final ResourceLocation SPELL_POWER = new ResourceLocation("irons_spellbooks", "spell_power");
    private static final UUID MODIFIER_ID = UUID.fromString("b3f2a6c0-4d1e-4b7a-9c58-2e6d0a7f1c93");
    private static final Set<UUID> ACTIVE = io.github.rpgclasses.server.SessionState.track(new HashSet<>());

    private SpellExemptions() {}

    /** ¿Es uno de los conjuros exentos de su clase? */
    static boolean exempt(ServerPlayer player, String spellId) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null || data.state.classId == null) return false;
        ClassDef classDef = DefinitionStore.SERVER.classDef(data.state.classId);
        return classDef != null && classDef.magicWallExemptSpells().contains(spellId);
    }

    /** Antes de ejecutar el conjuro (SpellOnCastEvent): si es de los exentos de su clase, quita el muro de Magia. */
    static void begin(ServerPlayer player, String spellId) {
        if (!exempt(player, spellId)) return;
        // Adenda del Druida: la Flecha de Veneno es de la naturaleza, vetada (−100 %) para el Ranger. Durante el
        // lanzamiento se quita también ese muro de escuela.
        liftSchoolWall(player, spellId);
        double wall = StatEngine.wall(player, Ids.MAGIC);
        if (wall <= 0 || wall >= 1) return;
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(SPELL_POWER);
        AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(MODIFIER_ID);
        instance.addTransientModifier(new AttributeModifier(MODIFIER_ID, "rites_and_paths spell wall exemption",
                wall / (1 - wall), AttributeModifier.Operation.MULTIPLY_TOTAL));
        ACTIVE.add(player.getUUID());
        DebugLog.event(player, "conjuro", "{} se lanza sin el muro de Magia ({} %)", spellId, Math.round(wall * 100));
    }

    /** Muro de escuela quitado durante un lanzamiento exento: jugador → (atributo, modificador). */
    private static final java.util.Map<UUID, Object[]> LIFTED = io.github.rpgclasses.server.SessionState.track(new java.util.HashMap<>());

    private static void liftSchoolWall(ServerPlayer player, String spellId) {
        io.redspace.ironsspellbooks.api.spells.AbstractSpell spell =
                io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(new ResourceLocation(spellId));
        if (spell == null) return;
        ResourceLocation school = spell.getSchoolType().getId();
        if (StatEngine.schoolWall(player, school.toString()) < 1.0) return;
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(school.getNamespace(), school.getPath() + "_spell_power"));
        AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
        if (instance == null) return;
        for (AttributeModifier m : java.util.List.copyOf(instance.getModifiers())) {
            if (m.getName().startsWith(StatEngine.MODIFIER_PREFIX + "school_wall")) {
                instance.removeModifier(m.getId());
                LIFTED.put(player.getUUID(), new Object[] {attribute, m});
                ACTIVE.add(player.getUUID());
                DebugLog.event(player, "conjuro", "{} se lanza sin el muro de su escuela ({})", spellId, school);
                return;
            }
        }
    }

    /** Fin del lanzamiento: el muro vuelve a aplicarse. */
    public static void end() {
        if (ACTIVE.isEmpty()) return;
        restoreSchoolWalls();
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        for (UUID id : ACTIVE) {
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(id);
            Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(SPELL_POWER);
            AttributeInstance instance = player == null || attribute == null ? null : player.getAttribute(attribute);
            if (instance != null) instance.removeModifier(MODIFIER_ID);
        }
        ACTIVE.clear();
    }

    private static void restoreSchoolWalls() {
        if (LIFTED.isEmpty()) return;
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        for (java.util.Map.Entry<UUID, Object[]> e : LIFTED.entrySet()) {
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(e.getKey());
            if (player == null) continue;
            AttributeInstance instance = player.getAttribute((Attribute) e.getValue()[0]);
            AttributeModifier m = (AttributeModifier) e.getValue()[1];
            if (instance != null && instance.getModifier(m.getId()) == null) instance.addTransientModifier(m);
        }
        LIFTED.clear();
    }
}
