package io.github.rpgclasses.difficulty;

import io.github.rpgclasses.config.ServerConfig;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Equipo variado según T. Cada ranura tira por separado (rara vez sale un set completo) y solo se rellenan
 * las ranuras vacías: nunca se quita lo que el mob ya traía en vanilla.
 */
public final class GearRoller {
    /** Orden de las columnas de {@link #WEIGHTS} y de los objetos. */
    private static final int LEATHER = 0, GOLD = 1, CHAIN = 2, IRON = 3, DIAMOND = 4, NETHERITE = 5;

    /** Pesos por tier para T en 10-19, 20-29, 30-39 y 40 [MEDIR]. */
    private static final double[][] WEIGHTS = {
            {40, 15, 30, 15, 0, 0},
            {15, 10, 25, 40, 10, 0},
            {0, 10, 10, 35, 45, 0},
            {0, 0, 0, 25, 60, 15},
    };

    private static final EquipmentSlot[] ARMOR_SLOTS =
            {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static final Item[][] ARMOR = {
            {Items.LEATHER_HELMET, Items.GOLDEN_HELMET, Items.CHAINMAIL_HELMET, Items.IRON_HELMET, Items.DIAMOND_HELMET, Items.NETHERITE_HELMET},
            {Items.LEATHER_CHESTPLATE, Items.GOLDEN_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE},
            {Items.LEATHER_LEGGINGS, Items.GOLDEN_LEGGINGS, Items.CHAINMAIL_LEGGINGS, Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS},
            {Items.LEATHER_BOOTS, Items.GOLDEN_BOOTS, Items.CHAINMAIL_BOOTS, Items.IRON_BOOTS, Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS},
    };

    /** Las armas no tienen cuero ni cota: el cuero equivale a la madera y la cota a la piedra. */
    private static final Item[] SWORDS = {Items.WOODEN_SWORD, Items.GOLDEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
    private static final Item[] AXES = {Items.WOODEN_AXE, Items.GOLDEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE};

    private GearRoller() {}

    /** Mobs que dibujan armadura humanoide. */
    private static boolean wearsArmor(Mob mob) {
        return mob instanceof Zombie || mob instanceof AbstractSkeleton || mob instanceof AbstractPiglin;
    }

    /** Solo los zombis (menos el ahogado, con su tridente): cambiar el arma a un esqueleto o a un piglin rompería su IA a distancia. */
    private static boolean usesMelee(Mob mob) {
        return mob instanceof Zombie && !(mob instanceof Drowned);
    }

    /**
     * @param zone amenaza de la zona. La netherita solo sale si la media real de niveles es 40: una poción de
     *             Desafío puede subir T pero no desbloquea la netherita.
     */
    public static void roll(Mob mob, ThreatService.Result zone) {
        double threat = zone.threat();
        if (threat < ServerConfig.DIFFICULTY_MIN_THREAT.get()) return;
        RandomSource random = mob.getRandom();
        double[] weights = weightsFor(threat, allowsNetherite(zone));
        double slotChance = Math.min(ServerConfig.DIFFICULTY_EQUIP_MAX.get(),
                ServerConfig.DIFFICULTY_EQUIP_BASE.get() + ServerConfig.DIFFICULTY_EQUIP_PER_T.get() * threat);

        if (wearsArmor(mob)) {
            for (int i = 0; i < ARMOR_SLOTS.length; i++) {
                EquipmentSlot slot = ARMOR_SLOTS[i];
                if (!mob.getItemBySlot(slot).isEmpty() || random.nextDouble() >= slotChance) continue;
                int tier = pickTier(weights, random);
                give(mob, slot, new ItemStack(ARMOR[i][tier]), tier, threat, random);
            }
        }
        if (usesMelee(mob) && mob.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && random.nextDouble() < slotChance) {
            int tier = pickTier(weights, random);
            Item weapon = (random.nextFloat() < 0.3f ? AXES : SWORDS)[tier];
            give(mob, EquipmentSlot.MAINHAND, new ItemStack(weapon), tier, threat, random);
        }
    }

    private static void give(Mob mob, EquipmentSlot slot, ItemStack stack, int tier, double threat, RandomSource random) {
        double start = ServerConfig.DIFFICULTY_ENCHANT_START.get();
        if (threat > start && random.nextDouble() < enchantChance(threat, start)) {
            stack = EnchantmentHelper.enchantItem(random, stack, (int) (10 + threat / 2), false);
        }
        mob.setItemSlot(slot, stack);
        // La netherita que pone el sistema no se suelta (evita granjas de netherita). El resto, como en vanilla.
        mob.setDropChance(slot, tier == NETHERITE ? 0.0f : 0.085f);
    }

    private static double enchantChance(double threat, double start) {
        double span = Math.max(1e-6, ThreatService.MAX_THREAT - start);
        return ServerConfig.DIFFICULTY_ENCHANT_MAX_CHANCE.get() * Math.min(1.0, (threat - start) / span);
    }

    static boolean allowsNetherite(ThreatService.Result zone) {
        return zone.baseThreat() >= ThreatService.MAX_THREAT - 1e-9;
    }

    /** Pesos del tramo de T, con el peso de la netherita recortado para que su probabilidad no pase del tope. */
    static double[] weightsFor(double threat, boolean netherite) {
        int band = threat >= ThreatService.MAX_THREAT - 1e-9 ? 3 : (int) Math.min(2, Math.max(0, (threat - 10) / 10));
        double[] w = WEIGHTS[band].clone();
        if (!netherite) w[NETHERITE] = 0;
        double cap = ServerConfig.DIFFICULTY_NETHERITE_MAX.get();
        double others = 0;
        for (int i = 0; i < NETHERITE; i++) others += w[i];
        double total = others + w[NETHERITE];
        if (total > 0 && w[NETHERITE] / total > cap) {
            w[NETHERITE] = cap >= 1.0 ? w[NETHERITE] : cap * others / (1.0 - cap);
        }
        return w;
    }

    private static int pickTier(double[] weights, RandomSource random) {
        double total = 0;
        for (double w : weights) total += w;
        double roll = random.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) return i;
        }
        return IRON;
    }
}
