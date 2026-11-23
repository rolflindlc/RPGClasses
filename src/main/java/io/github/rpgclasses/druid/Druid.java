package io.github.rpgclasses.druid;

import com.google.gson.JsonObject;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.MechanicService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Druida (adenda del Druida, 22-11-2026): ids, utilidades comunes (servidor y cliente) y registro.
 * <ul>
 *   <li>{@link Bestiary}: el conocimiento de cada especie, las criaturas ya usadas y las quimeras (con el jugador; no se
 *       pierde al reiniciar la clase).</li>
 *   <li>{@link Beasts}: qué mobs valen y los números de cada forma (tabla del datapack o fórmula).</li>
 *   <li>{@link Knowledge}: Libro de Estudio, restos ({@link BeastRemains}) y Ritual del Druida.</li>
 *   <li>{@link WildShape}: la transformación (lanzamiento, estadísticas, vida propia, vuelo, rasgos y vuelta).</li>
 *   <li>{@link BeastAbilities}: el ataque de la bestia y su habilidad especial.</li>
 *   <li>{@link DruidActives}: Rastrear Presas, Dominar Animal y Enredaderas.</li>
 *   <li>{@link Sculptor}: Recipiente, partes, Altar de Carne y quimeras (Escultor).</li>
 * </ul>
 * Los números se leen siempre de los JSON (tools/druid.py).
 */
public final class Druid {
    public static final String CLASS = Ids.NS + ":druid";
    /** Escuela de naturaleza de Iron's: la del Druida (−100 % fijo para las demás clases). */
    public static final String NATURE_SCHOOL = "irons_spellbooks:nature";

    // Habilidades (hitos) y la tabla oculta de bestias.
    public static final String WILD_SHAPE = Ids.NS + ":wild_shape";
    public static final String BEAST_LORE = Ids.NS + ":beast_lore";
    public static final String TRACK_PREY = Ids.NS + ":track_prey";
    public static final String DOMINATE_BEAST = Ids.NS + ":dominate_beast";
    public static final String ENTANGLING_VINES = Ids.NS + ":entangling_vines";
    public static final String BEASTS = Ids.NS + ":druid_beasts";

    // Mecánicas (ramas, perks y subclases).
    public static final String FEROCITY = "druid_ferocity";
    public static final String WILD_RESERVE = "druid_wild_reserve";
    public static final String LORE = "druid_lore";
    public static final String ROOTS = "druid_roots";
    public static final String VENOM_SAP = "druid_venom_sap";
    public static final String VITAL_SAP = "druid_vital_sap";
    public static final String HUNTER = "druid_hunter";
    public static final String COLOSSUS = "druid_colossus";
    public static final String CHEAP_FORM = "druid_cheap_form";
    public static final String RICH_FORM = "druid_rich_form";
    public static final String SCHOLAR = "druid_scholar";
    public static final String SCAVENGER = "druid_scavenger";
    public static final String BODY_SAP = "druid_body_sap";
    public static final String HEALING_EXIT = "druid_healing_exit";
    public static final String MOON = "druid_moon";
    public static final String SUPERNATURAL = "druid_supernatural";
    public static final String SCULPTOR = "druid_sculptor";

    /** Cliente: mecánicas del jugador local (lo instala DruidClient). */
    public static BiFunction<Player, String, ActiveMechanic> clientMechanic = (p, id) -> null;
    /** Cliente: datos de clase del jugador local (lo instala DruidClient). */
    public static Supplier<PlayerClassData> clientData = () -> null;

    private Druid() {}

    public static void register(IEventBus modBus) {
        DruidRegistry.register(modBus);
        AbilityService.register(WILD_SHAPE, WildShape::activate);
        AbilityService.register(TRACK_PREY, DruidActives::trackPrey);
        AbilityService.register(DOMINATE_BEAST, DruidActives::dominate);
        AbilityService.register(ENTANGLING_VINES, DruidActives::vines);
        MinecraftForge.EVENT_BUS.register(new WildShape());
        MinecraftForge.EVENT_BUS.register(new BeastAbilities());
        MinecraftForge.EVENT_BUS.register(new BeastInventory());
        MinecraftForge.EVENT_BUS.register(new Knowledge());
        MinecraftForge.EVENT_BUS.register(new DruidActives());
        MinecraftForge.EVENT_BUS.register(new Dominate());
        MinecraftForge.EVENT_BUS.register(new Sculptor());
        MinecraftForge.EVENT_BUS.register(new DruidPerks());
    }

    // ------------------------------------------------------------------ jugador

    /** Datos de clase del jugador: los del servidor o, en el cliente, los del jugador local (null para los demás). */
    public static PlayerClassData data(Player player) {
        if (player == null || player instanceof FakePlayer) return null;
        if (player instanceof ServerPlayer sp) return PlayerClassDataProvider.get(sp);
        return player.level().isClientSide ? clientData.get() : null;
    }

    public static boolean isDruid(Player player) {
        PlayerClassData data = data(player);
        return data != null && CLASS.equals(data.state.classId);
    }

    public static int totalLevel(Player player) {
        PlayerClassData data = data(player);
        return data == null ? 0 : data.state.totalLevel();
    }

    /** ¿Tiene ya esa habilidad (hito alcanzado)? */
    public static boolean has(Player player, String abilityId) {
        if (player instanceof ServerPlayer sp) return AbilityService.has(sp, abilityId);
        PlayerClassData data = data(player);
        if (data == null) return false;
        return io.github.rpgclasses.core.ability.UnlockResolver.unlocked(data.state,
                DefinitionStore.CLIENT.classDef(data.state.classId), DefinitionStore.CLIENT.subclassDef(data.state.subclassId))
                .contains(abilityId);
    }

    /** La mecánica del Druida (servidor o jugador local), o null. */
    public static ActiveMechanic mech(Player player, String id) {
        if (player instanceof ServerPlayer sp) return sp instanceof FakePlayer ? null : MechanicService.find(sp, id);
        if (player == null || !player.level().isClientSide) return null;
        return clientMechanic.apply(player, id.contains(":") ? id : Ids.NS + ":" + id);
    }

    public static boolean hasMech(Player player, String id) {
        return mech(player, id) != null;
    }

    public static double mech(Player player, String id, String key, double fallback) {
        ActiveMechanic m = mech(player, id);
        return m == null ? fallback : m.number(key, fallback);
    }

    // ------------------------------------------------------------------ números

    /** Parámetros de una habilidad del Druida en el lado que toque (vacío si no existe). */
    public static JsonObject params(String abilityId, boolean client) {
        var def = (client ? DefinitionStore.CLIENT : DefinitionStore.SERVER).abilityDef(abilityId);
        if (def == null && client) def = DefinitionStore.SERVER.abilityDef(abilityId);
        return def == null ? new JsonObject() : def.params();
    }

    public static double number(String abilityId, String key, double fallback, boolean client) {
        return AbilityService.number(params(abilityId, client), key, fallback);
    }

    /** Número de una habilidad (lado servidor). */
    public static double number(String abilityId, String key, double fallback) {
        return number(abilityId, key, fallback, false);
    }

    public static int ticks(String abilityId, String key, double fallbackSeconds) {
        return (int) Math.round(number(abilityId, key, fallbackSeconds) * 20);
    }
}
