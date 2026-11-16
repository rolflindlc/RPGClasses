package io.github.ironrituals.ritual.effect;

import io.github.ironrituals.config.ServerConfig;
import io.github.ironrituals.core.RitualMath;
import io.github.ironrituals.item.RitualDaggerItem;
import io.github.ironrituals.item.RitualTomeItem;
import io.github.ironrituals.ritual.RitualContext;
import io.github.ironrituals.ritual.RitualEffect;
import io.github.ironrituals.ritual.recipe.RitualTier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Ritual de Forja Eterna (plan, 4.F.26). El objeto con durabilidad que esté en el área central (sin ser componente)
 * se repara y recibe {@code Unbreakable}. El nivel mínimo lo marca su durabilidad máxima (parámetro
 * {@code max_durability} de cada nivel, sin huecos); un nivel superior también sirve. Con el catalizador
 * {@code fireproof} queda además inmune a lava, fuego y explosiones mientras está en el suelo.
 */
public class EternalForgeEffect implements RitualEffect {
    public static final String FIREPROOF_TAG = "ironrituals_fireproof";

    @Nullable
    @Override
    public Component checkStart(RitualContext ctx) {
        List<ItemEntity> targets = targets(ctx.leftovers());
        if (targets.isEmpty()) return Component.translatable("ritual.ironrituals.eternal_forge.no_item");
        if (targets.size() > 1) return Component.translatable("ritual.ironrituals.eternal_forge.too_many");
        ItemStack stack = targets.get(0).getItem();
        if (isBlacklisted(stack)) return Component.translatable("ritual.ironrituals.eternal_forge.refused", stack.getHoverName());
        if (stack.hasTag() && stack.getTag().getBoolean("Unbreakable") && (!ctx.hasCatalyst("fireproof") || isFireproof(stack))) {
            return Component.translatable("ritual.ironrituals.eternal_forge.already", stack.getHoverName());
        }
        int required = requiredLevel(ctx, stack.getMaxDamage());
        if (required > ctx.ritualLevel()) {
            return Component.translatable("ritual.ironrituals.eternal_forge.needs_level", stack.getHoverName(), required);
        }
        return null;
    }

    @Override
    public void apply(RitualContext ctx) {
        List<ItemEntity> targets = targets(ctx.leftovers());
        if (targets.isEmpty()) return; // se retiró durante la canalización: los componentes ya se gastaron
        ItemEntity entity = targets.get(0);
        ItemStack stack = entity.getItem().copy();
        stack.setDamageValue(0);
        stack.getOrCreateTag().putBoolean("Unbreakable", true);
        if (ctx.hasCatalyst("fireproof")) {
            stack.getOrCreateTag().putBoolean(FIREPROOF_TAG, true);
            entity.setInvulnerable(true);
        }
        entity.setItem(stack);

        ServerLevel level = ctx.level();
        level.sendParticles(ParticleTypes.LAVA, entity.getX(), entity.getY() + 0.3, entity.getZ(), 12, 0.2, 0.1, 0.2, 0.0);
        level.sendParticles(ParticleTypes.FLAME, entity.getX(), entity.getY() + 0.4, entity.getZ(), 30, 0.25, 0.3, 0.25, 0.03);
        level.playSound(null, entity.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, 0.7F);
        level.playSound(null, entity.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.8F, 0.6F);
    }

    public static boolean isFireproof(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(FIREPROOF_TAG);
    }

    /** Objetos candidatos: con durabilidad y que no sean herramientas del propio mod. */
    private static List<ItemEntity> targets(List<ItemEntity> leftovers) {
        List<ItemEntity> out = new ArrayList<>();
        for (ItemEntity e : leftovers) {
            if (!e.isAlive()) continue;
            ItemStack s = e.getItem();
            if (s.getItem() instanceof RitualDaggerItem || s.getItem() instanceof RitualTomeItem) continue;
            if (s.getMaxDamage() > 0 && s.getCount() == 1) out.add(e);
        }
        return out;
    }

    private static boolean isBlacklisted(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && ServerConfig.FORGE_BLACKLIST.get().contains(id.toString());
    }

    /** Menor nivel del ritual cuyo umbral de durabilidad cubre el objeto. */
    private static int requiredLevel(RitualContext ctx, int maxDamage) {
        List<RitualTier> tiers = ctx.recipe().tiers();
        int[] thresholds = new int[tiers.size()];
        for (int i = 0; i < tiers.size(); i++) {
            RitualTier t = tiers.get(i);
            thresholds[i] = t.params().contains("max_durability") ? t.params().getInt("max_durability") : Integer.MAX_VALUE;
        }
        int idx = RitualMath.forgeLevel(maxDamage, thresholds);
        return idx <= 0 ? Integer.MAX_VALUE : tiers.get(idx - 1).level();
    }
}
