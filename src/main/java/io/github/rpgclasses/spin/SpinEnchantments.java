package io.github.rpgclasses.spin;

import io.github.rpgclasses.RpgClasses;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Encantamientos de las bolas (adenda del Maestro del Giro, sección 3): salen en la mesa, en libros y en el yunque, y
 * solo se aplican a bolas. Los de durabilidad (Irrompibilidad, Reparación) son los vanilla. Sus números son fijos por
 * nivel (como los vanilla) y están aquí; el código que los usa está en {@link SpinThrow} y {@link SphereHits}.
 */
public final class SpinEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, RpgClasses.MODID);

    /** Categoría propia: solo las bolas. */
    public static final EnchantmentCategory SPHERE = EnchantmentCategory.create("RITES_AND_PATHS_SPHERE", item -> item instanceof SphereItem);

    /** Fricción (I-II): fuego a enemigos (4 s / 6 s) y a bloques inflamables (configurable). */
    public static final RegistryObject<Enchantment> FRICTION = ENCHANTMENTS.register("friction",
            () -> new SphereEnchantment(Enchantment.Rarity.UNCOMMON, 2, 12, 20));
    /** Densidad (I-III): +10 % de daño y −15 % de alcance por nivel. Incompatible con Ligereza. */
    public static final RegistryObject<Enchantment> DENSITY = ENCHANTMENTS.register("density",
            () -> new SphereEnchantment(Enchantment.Rarity.COMMON, 3, 1, 11));
    /** Ligereza (I-III): +20 % de alcance y −8 % de daño por nivel. Incompatible con Densidad. */
    public static final RegistryObject<Enchantment> LIGHTNESS = ENCHANTMENTS.register("lightness",
            () -> new SphereEnchantment(Enchantment.Rarity.COMMON, 3, 1, 11));
    /** Imán (I-III): −15 % de cooldown de retorno y −5 % de daño por nivel. */
    public static final RegistryObject<Enchantment> MAGNET = ENCHANTMENTS.register("magnet",
            () -> new SphereEnchantment(Enchantment.Rarity.UNCOMMON, 3, 5, 10));
    /** Garra (I-II): la bola sigue clavada +2 s por nivel y Giro Reverso arrastra +1 bloque por nivel; −10 % de velocidad. */
    public static final RegistryObject<Enchantment> CLAW = ENCHANTMENTS.register("claw",
            () -> new SphereEnchantment(Enchantment.Rarity.RARE, 2, 15, 20));
    /** Elasticidad (I-II): +1 rebote por nivel; cada rebote hace −8 % de daño más por nivel. */
    public static final RegistryObject<Enchantment> ELASTICITY = ENCHANTMENTS.register("elasticity",
            () -> new SphereEnchantment(Enchantment.Rarity.UNCOMMON, 2, 10, 15));

    private SpinEnchantments() {}

    // Números por nivel (adenda; [MEDIR]).
    public static final double DENSITY_DAMAGE = 0.10, DENSITY_RANGE = -0.15;
    public static final double LIGHTNESS_RANGE = 0.20, LIGHTNESS_DAMAGE = -0.08;
    public static final double MAGNET_RETURN = -0.15, MAGNET_DAMAGE = -0.05;
    public static final int CLAW_TICKS = 40;
    public static final double CLAW_PULL = 1.0, CLAW_SPEED = -0.10;
    public static final double ELASTICITY_FALLOFF = 0.08;
    public static final int[] FRICTION_SECONDS = {0, 4, 6};

    static int level(RegistryObject<Enchantment> e, ItemStack stack) {
        return e.isPresent() ? SpinMaster.enchant(e.get(), stack) : 0;
    }

    /** Encantamiento de bola: categoría propia, mano principal, en la mesa y en libros. */
    public static final class SphereEnchantment extends Enchantment {
        private final int maxLevel, minBase, perLevel;

        SphereEnchantment(Rarity rarity, int maxLevel, int minBase, int perLevel) {
            super(rarity, SPHERE, new EquipmentSlot[] {EquipmentSlot.MAINHAND});
            this.maxLevel = maxLevel;
            this.minBase = minBase;
            this.perLevel = perLevel;
        }

        @Override
        public int getMaxLevel() {
            return maxLevel;
        }

        @Override
        public int getMinCost(int level) {
            return minBase + (level - 1) * perLevel;
        }

        @Override
        public int getMaxCost(int level) {
            return getMinCost(level) + 20;
        }

        @Override
        protected boolean checkCompatibility(Enchantment other) {
            if (!super.checkCompatibility(other)) return false;
            // Densidad y Ligereza se excluyen.
            boolean a = this == DENSITY.get() && other == LIGHTNESS.get();
            boolean b = this == LIGHTNESS.get() && other == DENSITY.get();
            return !a && !b;
        }

        @Override
        public boolean canApplyAtEnchantingTable(ItemStack stack) {
            return stack.getItem() instanceof SphereItem;
        }
    }
}
