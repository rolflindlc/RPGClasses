package io.github.rpgclasses.content.race;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import io.github.rpgclasses.server.MechanicService;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.FakePlayer;

import java.util.function.BiFunction;

/** Consultas comunes de los rasgos raciales. Las mecánicas raciales llevan el prefijo {@code race_}. */
public final class Races {
    public static final TagKey<Item> FORBIDDEN_FOOD = TagKey.create(Registries.ITEM, RpgClasses.id("elf_forbidden_food"));
    public static final TagKey<Item> MEAT = TagKey.create(Registries.ITEM, RpgClasses.id("meat"));
    public static final TagKey<Block> DWARF_MINEABLE = TagKey.create(Registries.BLOCK, RpgClasses.id("dwarf_mineable"));
    public static final TagKey<DamageType> FIRE_MAGIC = TagKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("fire_magic"));
    public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE,
            new net.minecraft.resources.ResourceLocation("forge", "bosses"));
    public static final ResourceKey<DamageType> SUNLIGHT = ResourceKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("sunlight"));
    public static final ResourceKey<DamageType> SCALD = ResourceKey.create(Registries.DAMAGE_TYPE, RpgClasses.id("scald"));

    /** En el cliente, las mecánicas del jugador local (lo instala ClientSetup). */
    public static BiFunction<Player, String, ActiveMechanic> clientLookup = (player, id) -> null;

    /** La mecánica racial del jugador (servidor o jugador local del cliente), o null. */
    public static ActiveMechanic find(Player player, String id) {
        String full = id.contains(":") ? id : Ids.NS + ":" + id;
        if (player instanceof ServerPlayer sp) return sp instanceof FakePlayer ? null : MechanicService.find(sp, full);
        return clientLookup.apply(player, full);
    }

    public static boolean has(Player player, String id) {
        return find(player, id) != null;
    }

    public static double number(Player player, String id, String key, double fallback) {
        ActiveMechanic m = find(player, id);
        return m == null ? fallback : m.number(key, fallback);
    }

    public static int rank(ServerPlayer player) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        return data == null ? 0 : data.state.raceRank;
    }

    public static boolean isBoss(LivingEntity e) {
        return e.getType().is(BOSSES);
    }

    public static DamageSource sunlight(LivingEntity e) {
        return new DamageSource(e.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SUNLIGHT));
    }

    /** Infernal en el agua (rev. 2.17). */
    public static DamageSource scald(LivingEntity e) {
        return new DamageSource(e.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SCALD));
    }

    /** Efecto de fondo: sin partículas ni icono (los rasgos raciales no llenan la pantalla de efectos). */
    public static MobEffectInstance hidden(net.minecraft.world.effect.MobEffect effect, int ticks, int amplifier) {
        return new MobEffectInstance(effect, ticks, amplifier, true, false, false);
    }

    /** FoodData no tiene setters de servidor para saturación y agotamiento: se lee y edita por NBT. */
    public static net.minecraft.nbt.CompoundTag foodTag(net.minecraft.world.food.FoodData food) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        food.addAdditionalSaveData(tag);
        return tag;
    }

    private Races() {}
}
