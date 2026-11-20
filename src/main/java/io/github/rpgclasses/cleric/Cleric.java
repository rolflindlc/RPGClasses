package io.github.rpgclasses.cleric;

import com.google.gson.JsonObject;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.Allies;
import io.github.rpgclasses.server.MechanicService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;
import java.util.UUID;

/**
 * Clérigo (adenda del Clérigo, 19-11-2026): ids, utilidades comunes y registro.
 * <ul>
 *   <li>{@link ClericHealing}: Sanación, Canal de Vida, Fe Propia, Purificación (Absolución, Penitencia), Égida y Retorno de Fe.</li>
 *   <li>{@link ClericBuffs}: Bendición (Mayor, Egoísta), absorción de conjuros, Fe Ciega y Halo.</li>
 *   <li>{@link Miracle}: Milagro (aliados tumbados) y la inmunidad tras levantarse.</li>
 *   <li>{@link Sanctuary}: Santuario (esfera canalizada).</li>
 *   <li>{@link ClericSubclasses}: Imposición de Manos, Forma Angelical y Gracia Alada, Sacrificio y Resolución.</li>
 *   <li>{@link Prayer}: Plegaria del Intercesor (cámara en un ángel, conjuros desde él).</li>
 * </ul>
 * Los números se leen siempre de los JSON (tools/cleric.py).
 */
public final class Cleric {
    public static final String CLASS = Ids.NS + ":cleric";
    public static final String HEALER_EYE = Ids.NS + ":healer_eye";
    public static final String LAYING_ON_HANDS = Ids.NS + ":laying_on_hands";
    public static final String SANCTUARY = Ids.NS + ":sanctuary";
    public static final String MIRACLE = Ids.NS + ":miracle";
    public static final String ANGELIC_FORM = Ids.NS + ":angelic_form";
    public static final String SACRIFICE = Ids.NS + ":sacrifice";
    public static final String PRAYER = Ids.NS + ":prayer";

    // Mecánicas (ramas, perks y subclases).
    public static final String HEALING_POWER = "healing_power";
    public static final String BLESSING = "blessing_duration";
    public static final String PURIFICATION = "purification";
    public static final String AEGIS = "aegis";
    public static final String LIFE_CHANNEL = "life_channel";
    public static final String OWN_FAITH = "own_faith";
    public static final String GREATER_BLESSING = "greater_blessing";
    public static final String SELFISH_BLESSING = "selfish_blessing";
    public static final String FAITH_RETURN = "faith_return";
    public static final String BLIND_FAITH = "blind_faith";
    public static final String ABSOLUTION = "absolution";
    public static final String PENANCE = "penance";
    public static final String DEEP_AEGIS = "deep_aegis";
    public static final String SWIFT_AEGIS = "swift_aegis";
    public static final String WINGED_GRACE = "winged_grace";
    public static final String HALO = "halo";
    public static final String MARTYR_RESOLVE = "martyr_resolve";
    public static final String FAR_VOICE = "far_voice";
    public static final String PRAYER_BODY = "prayer_body";

    private static final ResourceLocation SPELL_POWER = new ResourceLocation("irons_spellbooks", "spell_power");
    private static final ResourceLocation HOLY_POWER = new ResourceLocation("irons_spellbooks", "holy_spell_power");

    private Cleric() {}

    public static void register() {
        AbilityService.register(LAYING_ON_HANDS, ClericSubclasses::layingOnHands);
        AbilityService.register(SANCTUARY, Sanctuary::activate);
        AbilityService.register(ANGELIC_FORM, ClericSubclasses::angelicForm);
        AbilityService.register(SACRIFICE, ClericSubclasses::sacrifice);
        AbilityService.register(PRAYER, Prayer::open);
        MinecraftForge.EVENT_BUS.register(new ClericHealing());
        MinecraftForge.EVENT_BUS.register(new ClericBuffs());
        MinecraftForge.EVENT_BUS.register(new Sanctuary());
        MinecraftForge.EVENT_BUS.register(new ClericSubclasses());
        MinecraftForge.EVENT_BUS.register(new Prayer());
        // Milagro va el último: en el mismo golpe letal actúan antes las supervivencias propias (regla 10).
        MinecraftForge.EVENT_BUS.register(new Miracle());
    }

    public static boolean isCleric(Player player) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        return data != null && CLASS.equals(data.state.classId);
    }

    public static ActiveMechanic mech(ServerPlayer player, String id) {
        return MechanicService.find(player, id);
    }

    public static double mech(ServerPlayer player, String id, String key, double fallback) {
        return MechanicService.number(player, id, key, fallback);
    }

    public static JsonObject params(String abilityId) {
        return AbilityService.params(abilityId);
    }

    public static double number(String abilityId, String key, double fallback) {
        return AbilityService.number(params(abilityId), key, fallback);
    }

    /**
     * Poder de las habilidades sagradas del Clérigo: poder de hechizo × poder sagrado de Iron's (1 sin Iron's). El muro
     * de Magia no cuenta: la sagrada está exenta (StatEngine compensa el muro en el poder sagrado). Con la Plegaria,
     * además la penalización por distancia.
     */
    public static double holyPower(ServerPlayer player) {
        double p = attribute(player, SPELL_POWER) * attribute(player, HOLY_POWER);
        // El modificador de la Plegaria ya está en el poder de hechizo; no se vuelve a aplicar.
        return Math.max(0, p);
    }

    private static double attribute(ServerPlayer player, ResourceLocation id) {
        Attribute a = ForgeRegistries.ATTRIBUTES.getValue(id);
        if (a == null) return 1;
        AttributeInstance inst = player.getAttribute(a);
        return inst == null ? 1 : inst.getValue();
    }

    /** Aliado del Clérigo (él mismo cuenta como aliado). */
    public static boolean ally(ServerPlayer cleric, Entity e) {
        return e != null && Allies.isAlly(cleric, e);
    }

    /** Origen de la mirada: los ojos (rezando, el jugador ya está junto al aliado como cabeza). */
    public static Vec3 eye(ServerPlayer player) {
        return player.getEyePosition();
    }

    /**
     * El aliado (no uno mismo) al que mira el jugador hasta {@code range} bloques, con algo de ayuda de puntería;
     * desde el ángel si está rezando. Las paredes cortan la mirada.
     */
    public static LivingEntity lookAlly(ServerPlayer player, double range) {
        Vec3 from = eye(player);
        Vec3 dir = player.getLookAngle();
        Vec3 to = from.add(dir.scale(range));
        BlockHitResult block = player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) to = block.getLocation();
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        AABB box = new AABB(from, to).inflate(1.5);
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive()
                && !(e instanceof Player p && p.isSpectator()) && ally(player, e))) {
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.45).clip(from, to);
            if (hit.isEmpty()) continue;
            double d = from.distanceToSqr(hit.get());
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    /** Modificador transitorio con un id fijo que StatEngine no borra (su nombre no empieza por "rites_and_paths:"). */
    public static void setModifier(LivingEntity e, Attribute attribute, UUID id, String name, double amount, AttributeModifier.Operation op) {
        if (attribute == null) return;
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst == null) return;
        AttributeModifier cur = inst.getModifier(id);
        if (cur != null && Math.abs(cur.getAmount() - amount) < 1.0E-6 && cur.getOperation() == op) return;
        inst.removeModifier(id);
        if (amount != 0) inst.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }

    public static Attribute ironsAttribute(String path) {
        return ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("irons_spellbooks", path));
    }
}
