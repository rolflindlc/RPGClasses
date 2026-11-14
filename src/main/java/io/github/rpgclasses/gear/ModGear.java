package io.github.rpgclasses.gear;

import io.github.rpgclasses.RpgClasses;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registro del «Equipo de Clase» (rev. 2.20): armaduras de Bárbaro y Monje de todos los tiers, armas marciales,
 * objetos intermedios y una pestaña de creativo propia. Los ids siguen el patrón:
 * {@code <clase>_<tier>_<pieza>} (barbarian_iron_chestplate), {@code <arma>_<tier>} (sai_diamond).
 * Los objetos de Ice and Fire se registran siempre; solo sus recetas dependen de que el mod esté instalado.
 */
public final class ModGear {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RpgClasses.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RpgClasses.MODID);

    public static final Map<GearClass, Map<ArmorTier, Map<ArmorItem.Type, RegistryObject<Item>>>> ARMOR = new EnumMap<>(GearClass.class);
    public static final Map<WeaponKind, Map<WeaponTier, RegistryObject<Item>>> WEAPONS = new EnumMap<>(WeaponKind.class);
    private static final List<RegistryObject<Item>> ORDER = new ArrayList<>();

    /** Correa Curtida: arneses del Bárbaro y nudilleras. */
    public static final RegistryObject<Item> CURED_STRAP = simple("cured_strap", new Item.Properties());
    /** Tela Marcial: vestiduras del Monje. */
    public static final RegistryObject<Item> MARTIAL_CLOTH = simple("martial_cloth", new Item.Properties());
    /** Plantilla de Forja Dracónica: mejora del tier de escamas al de dragonsteel en la mesa de herrería. */
    public static final RegistryObject<Item> DRACONIC_TEMPLATE = simple("draconic_forge_template",
            new Item.Properties().rarity(Rarity.EPIC).fireResistant());

    static {
        for (GearClass c : GearClass.values()) {
            Map<ArmorTier, Map<ArmorItem.Type, RegistryObject<Item>>> byTier = new EnumMap<>(ArmorTier.class);
            for (ArmorTier tier : ArmorTier.values()) {
                Map<ArmorItem.Type, RegistryObject<Item>> byType = new EnumMap<>(ArmorItem.Type.class);
                for (ArmorItem.Type type : ArmorItem.Type.values()) {
                    String name = c.id + "_" + tier.id + "_" + type.getName();
                    RegistryObject<Item> ro = ITEMS.register(name, () -> new ClassArmorItem(c, tier, type, props(tier.fireResistant(), tier.isDragonsteel())));
                    byType.put(type, ro);
                    ORDER.add(ro);
                }
                byTier.put(tier, byType);
            }
            ARMOR.put(c, byTier);
        }
        for (WeaponKind kind : WeaponKind.values()) {
            Map<WeaponTier, RegistryObject<Item>> byTier = new EnumMap<>(WeaponTier.class);
            for (WeaponTier tier : WeaponTier.values()) {
                String name = kind.id + "_" + tier.id;
                RegistryObject<Item> ro = kind == WeaponKind.KNUCKLES
                        ? ITEMS.register(name, () -> new KnucklesItem(tier, props(tier.fireResistant(), tier.lateTier() == 3).stacksTo(1)))
                        : ITEMS.register(name, () -> new MartialWeaponItem(kind, tier, props(tier.fireResistant(), tier.lateTier() == 3).durability(tier.durability)));
                byTier.put(tier, ro);
                ORDER.add(ro);
            }
            WEAPONS.put(kind, byTier);
        }
    }

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("class_gear", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.rpgclasses.class_gear"))
            .icon(() -> new ItemStack(ARMOR.get(GearClass.MONK).get(ArmorTier.DRAGONSTEEL_FIRE).get(ArmorItem.Type.CHESTPLATE).get()))
            .displayItems((params, out) -> ORDER.forEach(ro -> out.accept(ro.get())))
            .build());

    private static Item.Properties props(boolean fireResistant, boolean epic) {
        Item.Properties p = new Item.Properties();
        if (fireResistant) p.fireResistant();
        if (epic) p.rarity(Rarity.EPIC);
        return p;
    }

    private static RegistryObject<Item> simple(String name, Item.Properties props) {
        RegistryObject<Item> ro = ITEMS.register(name, () -> new Item(props));
        ORDER.add(ro);
        return ro;
    }

    public static Item armor(GearClass c, ArmorTier tier, ArmorItem.Type type) {
        return ARMOR.get(c).get(tier).get(type).get();
    }

    public static Item weapon(WeaponKind kind, WeaponTier tier) {
        return WEAPONS.get(kind).get(tier).get();
    }

    private ModGear() {}
}
