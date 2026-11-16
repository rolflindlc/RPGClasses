package io.github.rpgclasses.content;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;
import java.util.Map;

/**
 * Las 10 escuelas de magia que reconoce el mod (rev. 2.15): las 9 de Iron's Spells (fuego, hielo, rayo, sagrada,
 * ender, sangre, invocación —"evocation", la de las esmeraldas—, naturaleza y eldritch) y Agua ("aqua"), que añade
 * T.O Magic 'n Extras. T.O es opcional: Agua se reconoce por el nombre de la escuela o del tipo de daño en
 * cualquier espacio de nombres, sin depender de sus clases, y si no está instalado simplemente nunca aparece.
 */
public final class Schools {
    public enum School { FIRE, ICE, LIGHTNING, HOLY, ENDER, BLOOD, EVOCATION, NATURE, ELDRITCH, AQUA;
        public String key() { return name().toLowerCase(Locale.ROOT); }
    }

    private static final Map<String, School> BY_PATH = Map.ofEntries(
            Map.entry("fire", School.FIRE), Map.entry("ice", School.ICE), Map.entry("lightning", School.LIGHTNING),
            Map.entry("holy", School.HOLY), Map.entry("ender", School.ENDER), Map.entry("blood", School.BLOOD),
            Map.entry("evocation", School.EVOCATION), Map.entry("nature", School.NATURE), Map.entry("eldritch", School.ELDRITCH),
            Map.entry("aqua", School.AQUA), Map.entry("water", School.AQUA));

    private static MobEffect wet;
    private static boolean wetLooked;

    private Schools() {}

    /** Id de escuela de Iron's ("irons_spellbooks:fire", "traveloptics:aqua"…) → escuela; null si es otra. */
    public static School of(String schoolId) {
        if (schoolId == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(schoolId);
        if (id == null) return null;
        School s = BY_PATH.get(id.getPath());
        // Las 9 de Iron's solo con su espacio de nombres; Agua con cualquiera (la añade un addon).
        return s == null || s == School.AQUA || "irons_spellbooks".equals(id.getNamespace()) ? s : null;
    }

    /** Tipo de daño de conjuro ("irons_spellbooks:fire_magic", "<t.o>:aqua_magic") → escuela; null si no lo es. */
    public static School ofDamageType(ResourceLocation type) {
        String path = type.getPath();
        if (!path.endsWith("_magic")) return null;
        return of(type.getNamespace() + ":" + path.substring(0, path.length() - "_magic".length()));
    }

    public static ParticleOptions particle(School s) {
        if (s == null) return ParticleTypes.ENCHANT;
        return switch (s) {
            case FIRE -> ParticleTypes.FLAME;
            case ICE -> ParticleTypes.SNOWFLAKE;
            case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
            case HOLY -> ParticleTypes.END_ROD;
            case ENDER -> ParticleTypes.PORTAL;
            case BLOOD -> ClassFx.BLOOD;
            case EVOCATION -> ParticleTypes.HAPPY_VILLAGER;
            case NATURE -> ParticleTypes.SPORE_BLOSSOM_AIR;
            case ELDRITCH -> ParticleTypes.SCULK_SOUL;
            case AQUA -> ParticleTypes.SPLASH;
        };
    }

    /** Id del efecto "Mojado" de T.O Magic 'n Extras (leído en el pack). */
    public static final ResourceLocation WET_ID = new ResourceLocation("traveloptics", "wet");

    /** Efecto "Mojado" de T.O (reduce resistencias a rayo y hielo) si está instalado; null si no. */
    public static MobEffect wetEffect() {
        if (!wetLooked) {
            wetLooked = true;
            wet = ForgeRegistries.MOB_EFFECTS.containsKey(WET_ID) ? ForgeRegistries.MOB_EFFECTS.getValue(WET_ID) : null;
        }
        return wet;
    }

    /** Agua: aplica Mojado de T.O si existe (amplificador dado), o nada. */
    public static void applyWet(LivingEntity target, ServerPlayer source, int ticks, int amplifier) {
        MobEffect w = wetEffect();
        if (w != null) target.addEffect(new MobEffectInstance(w, ticks, amplifier), source);
    }

    /** Invocación: anillo de colmillos de evocador del jugador alrededor del objetivo (vanilla, no dañan al dueño). */
    public static void fangs(ServerPlayer owner, LivingEntity target, int count, double radius) {
        for (int i = 0; i < count; i++) {
            double a = (Math.PI * 2 * i) / count;
            double x = target.getX() + Math.cos(a) * radius, z = target.getZ() + Math.sin(a) * radius;
            owner.level().addFreshEntity(new EvokerFangs(owner.level(), x, target.getY(), z, (float) a, i * 2, owner));
        }
        if (radius > 0) owner.level().addFreshEntity(new EvokerFangs(owner.level(), target.getX(), target.getY(), target.getZ(), 0f, count * 2, owner));
    }

    /** Ender: teletransporte aleatorio corto del objetivo (como la fruta coral); false si no encontró sitio. */
    public static boolean blink(LivingEntity target, double range) {
        for (int i = 0; i < 16; i++) {
            double x = target.getX() + (target.getRandom().nextDouble() - 0.5) * 2 * range;
            double y = target.getY() + target.getRandom().nextInt((int) Math.max(2, range)) - range / 2;
            double z = target.getZ() + (target.getRandom().nextDouble() - 0.5) * 2 * range;
            double ox = target.getX(), oy = target.getY(), oz = target.getZ();
            if (target.randomTeleport(x, y, z, true)) {
                target.level().playSound(null, ox, oy, oz, net.minecraft.sounds.SoundEvents.CHORUS_FRUIT_TELEPORT,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.2f);
                return true;
            }
        }
        return false;
    }
}
