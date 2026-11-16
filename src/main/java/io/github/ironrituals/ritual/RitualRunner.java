package io.github.ironrituals.ritual;

import io.github.ironrituals.IronRituals;
import io.github.ironrituals.block.ArcaneCandleBlock;
import io.github.ironrituals.block.RitualCoreBlockEntity;
import io.github.ironrituals.compat.IssCompat;
import io.github.ironrituals.config.ServerConfig;
import io.github.ironrituals.core.RitualMath;
import io.github.ironrituals.item.RitualDaggerItem;
import io.github.ironrituals.item.RitualTomeItem;
import io.github.ironrituals.item.SoulData;
import io.github.ironrituals.ritual.active.ActiveRitual;
import io.github.ironrituals.ritual.active.RitualSavedData;
import io.github.ironrituals.ritual.active.RitualTicker;
import io.github.ironrituals.ritual.recipe.RitualComponent;
import io.github.ironrituals.ritual.recipe.RitualRecipe;
import io.github.ironrituals.ritual.recipe.RitualTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Motor de ejecución de rituales (plan, 2.2): activación, canalización, drenaje de maná, interrupciones,
 * consumo y prórroga de rituales persistentes.
 */
public final class RitualRunner {
    private static final DustParticleOptions MISSING_DUST = new DustParticleOptions(new Vector3f(0.9F, 0.1F, 0.15F), 1.2F);
    private static final DustParticleOptions UNLIT_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.75F, 0.1F), 1.2F);

    private RitualRunner() {}

    // ================================================================== Activación

    public static void activate(ServerLevel level, BlockPos pos, RitualCoreBlockEntity core, ServerPlayer player) {
        RitualChannel running = core.getChannel();
        if (running != null) {
            if (running.caster.equals(player.getUUID()) && player.isShiftKeyDown()) {
                interrupt(level, pos, core, "cancelled");
            } else {
                player.displayClientMessage(Component.translatable("message.ironrituals.ritual.busy"), true);
            }
            return;
        }

        RitualRecipe recipe = RitualRecipe.find(level, RitualTomeItem.ritualId(core.getBook()));
        if (recipe == null) {
            fail(player, "message.ironrituals.ritual.unknown");
            return;
        }
        RitualEffect effect = RitualEffects.get(recipe.ritual());
        if (effect == null) {
            fail(player, "message.ironrituals.ritual.not_implemented");
            diagnose(level, pos, core, player);
            return;
        }
        if (RitualTicker.isSuppressed(level.getServer(), level.dimension(), Vec3.atCenterOf(pos))) {
            fail(player, "message.ironrituals.ritual.suppressed");
            return;
        }

        List<ItemEntity> items = core.itemsInCentralArea();

        // Prórroga (sección 2.5): mismo ritual activo en este núcleo → sumar la duración base, sin velas ni canalización.
        if (recipe.extendable()) {
            for (ActiveRitual active : RitualSavedData.get(level.getServer()).atCore(level.dimension(), pos)) {
                if (!active.ritual.equals(recipe.ritual())) continue;
                RitualTier tier = recipe.tier(active.level);
                RitualMatch m = tier == null ? null : RitualMatch.exactTier(level, recipe, tier, items);
                if (m == null) {
                    fail(player, "message.ironrituals.ritual.extend_missing", active.level);
                    return;
                }
                takeComponents(m);
                long add = tier.durationSeconds() * 20L;
                active.remaining += add;
                active.total += add;
                RitualSavedData.get(level.getServer()).setDirty();
                level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.3F);
                player.displayClientMessage(Component.translatable("message.ironrituals.ritual.extended",
                        formatTicks(active.remaining)).withStyle(ChatFormatting.LIGHT_PURPLE), false);
                return;
            }
        }

        CircleValidator.Report circle = CircleValidator.inspect(level, pos);
        if (circle.level() < recipe.minCircle()) {
            fail(player, "message.ironrituals.ritual.circle_too_small", recipe.minCircle());
            diagnose(level, pos, core, player);
            return;
        }

        RitualMatch match = RitualMatch.find(level, recipe, circle.level(), items);
        if (match == null) {
            fail(player, "message.ironrituals.ritual.missing_components");
            diagnose(level, pos, core, player);
            return;
        }

        double power = IssCompat.schoolPower(player, recipe.school());
        RitualContext preview = new RitualContext(level, pos, player, recipe, match.tier(), match.catalysts(),
                previewSouls(match), previewConsumed(match), leftovers(match), power);
        Component refusal = effect.checkStart(preview);
        if (refusal != null) {
            player.displayClientMessage(refusal.copy().withStyle(ChatFormatting.RED), false);
            return;
        }

        List<ItemStack> reserved = takeComponents(match);
        RitualTier tier = match.tier();
        int ticks = tier.channelSeconds() >= 0
                ? tier.channelSeconds() * 20
                : RitualMath.channelTicks(tier.level(), ServerConfig.CHANNEL_BASE_SECONDS.get(), ServerConfig.CHANNEL_PER_LEVEL_SECONDS.get());
        float mana = (float) (tier.mana() * ServerConfig.MANA_COST_MULTIPLIER.get());
        List<BlockPos> candles = new ArrayList<>();
        for (int l = 1; l <= tier.level(); l++) candles.addAll(CircleValidator.ringPositions(pos, l));

        core.setChannel(new RitualChannel(recipe.ritual(), tier.level(), match.catalysts(), player.getUUID(), ticks, mana, candles, reserved));
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
        player.displayClientMessage(Component.translatable("message.ironrituals.ritual.started",
                RitualTomeItem.ritualName(core.getBook()), tier.level(), Math.round(mana)).withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }

    // ================================================================== Canalización

    public static void tick(ServerLevel level, BlockPos pos, RitualCoreBlockEntity core) {
        RitualChannel ch = core.getChannel();
        if (ch == null) return;

        ServerPlayer caster = level.getServer().getPlayerList().getPlayer(ch.caster);
        double maxDist = ServerConfig.CASTER_MAX_DISTANCE.get();
        if (caster == null || !caster.isAlive() || caster.level() != level) {
            interrupt(level, pos, core, "caster_gone");
            return;
        }
        if (caster.distanceToSqr(Vec3.atCenterOf(pos)) > maxDist * maxDist) {
            interrupt(level, pos, core, "too_far");
            return;
        }
        if (ch.elapsed % 5 == 0) {
            for (BlockPos c : ch.candles) {
                if (!ArcaneCandleBlock.isLitCandle(level.getBlockState(c))) {
                    interrupt(level, pos, core, "candle");
                    return;
                }
            }
        }
        if (ch.elapsed % 20 == 0 && RitualTicker.isSuppressed(level.getServer(), level.dimension(), Vec3.atCenterOf(pos))) {
            interrupt(level, pos, core, "suppressed");
            return;
        }

        float perTick = RitualMath.manaPerTick(ch.manaTotal, ch.total);
        boolean free = caster.getAbilities().instabuild && ServerConfig.CREATIVE_FREE_MANA.get();
        if (perTick > 0 && !free) {
            if (!IssCompat.trySpend(caster, perTick)) {
                interrupt(level, pos, core, "no_mana");
                return;
            }
        }
        ch.manaSpent += perTick;

        updateBar(ch, caster, core);
        channelFx(level, pos, ch);

        ch.elapsed++;
        core.setChanged();
        if (ch.elapsed >= ch.total) complete(level, pos, core, caster);
    }

    private static void updateBar(RitualChannel ch, ServerPlayer caster, RitualCoreBlockEntity core) {
        if (ch.bar == null) {
            ch.bar = new ServerBossEvent(Component.translatable("message.ironrituals.ritual.bar", RitualTomeItem.ritualName(core.getBook())),
                    BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
        }
        if (!ch.bar.getPlayers().contains(caster)) ch.bar.addPlayer(caster);
        ch.bar.setProgress(ch.progress());
    }

    public static void removeBar(RitualChannel ch) {
        if (ch.bar != null) {
            ch.bar.removeAllPlayers();
            ch.bar = null;
        }
    }

    private static void channelFx(ServerLevel level, BlockPos pos, RitualChannel ch) {
        if (ch.elapsed % 3 == 0 && !ch.candles.isEmpty()) {
            BlockPos c = ch.candles.get(level.random.nextInt(ch.candles.size()));
            level.sendParticles(ParticleTypes.WITCH, c.getX() + 0.5, c.getY() + 0.7, c.getZ() + 0.5, 2, 0.05, 0.1, 0.05, 0.0);
        }
        if (ch.elapsed % 2 == 0) {
            double a = ch.elapsed * 0.25;
            double r = 1.2 * (1.0 - ch.progress() * 0.7);
            level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5 + Math.cos(a) * r, pos.getY() + 1.3,
                    pos.getZ() + 0.5 + Math.sin(a) * r, 1, 0, 0, 0, 0.2);
        }
        if (ch.elapsed % 40 == 0) {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8F, 0.6F + ch.progress());
        }
    }

    // ================================================================== Fin

    private static void complete(ServerLevel level, BlockPos pos, RitualCoreBlockEntity core, ServerPlayer caster) {
        RitualChannel ch = core.getChannel();
        if (ch == null) return;
        removeBar(ch);
        core.setChannel(null);

        RitualRecipe recipe = RitualRecipe.find(level, ch.ritual);
        RitualEffect effect = RitualEffects.get(ch.ritual);
        RitualTier tier = recipe == null ? null : recipe.tier(ch.level);
        if (recipe == null || effect == null || tier == null) {
            // La definición desapareció (recarga de datapacks): se devuelve todo.
            dropAll(level, pos, ch.reserved);
            fail(caster, "message.ironrituals.ritual.unknown");
            return;
        }

        List<ItemStack> souls = new ArrayList<>();
        List<ItemStack> consumed = new ArrayList<>();
        List<ItemStack> returned = new ArrayList<>();
        for (ItemStack s : ch.reserved) {
            if (s.getItem() instanceof RitualDaggerItem && SoulData.hasSoul(s)) {
                souls.add(s.copy());
                ItemStack empty = s.copy();
                SoulData.clear(empty);
                returned.add(empty);
            } else {
                consumed.add(s);
            }
        }

        for (BlockPos c : ch.candles) {
            BlockState st = level.getBlockState(c);
            if (st.getBlock() instanceof ArcaneCandleBlock candle) candle.consumeUse(level, c, st);
        }

        double power = IssCompat.schoolPower(caster, recipe.school());
        RitualContext ctx = new RitualContext(level, pos, caster, recipe, tier, ch.catalysts, souls, consumed,
                core.itemsInCentralArea(), power);
        try {
            effect.apply(ctx);
        } catch (RuntimeException e) {
            IronRituals.LOGGER.error("Error al aplicar el ritual {}", ch.ritual, e);
            fail(caster, "message.ironrituals.ritual.error");
            return;
        } finally {
            dropAll(level, pos, returned); // la daga vuelve vacía al altar
        }

        level.playSound(null, pos, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.BLOCKS, 1.2F, 1.0F);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.4, 0.6, 0.4, 0.15);
        caster.displayClientMessage(Component.translatable("message.ironrituals.ritual.completed",
                RitualTomeItem.ritualName(core.getBook())).withStyle(ChatFormatting.GREEN), false);
    }

    /** Interrumpe la canalización: devuelve los componentes al suelo y las velas no gastan usos (sección 2.2). */
    public static void interrupt(ServerLevel level, BlockPos pos, RitualCoreBlockEntity core, String reason) {
        RitualChannel ch = core.getChannel();
        if (ch == null) return;
        removeBar(ch);
        core.setChannel(null);
        dropAll(level, pos, ch.reserved);
        level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 20, 0.3, 0.2, 0.3, 0.02);
        ServerPlayer caster = level.getServer().getPlayerList().getPlayer(ch.caster);
        if (caster != null) {
            caster.displayClientMessage(Component.translatable("message.ironrituals.interrupt." + reason).withStyle(ChatFormatting.RED), false);
        }
    }

    // ================================================================== Utilidades

    /** Retira del suelo las unidades del reparto y las devuelve como pilas. */
    private static List<ItemStack> takeComponents(RitualMatch m) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < m.items().size(); i++) {
            int n = m.taken()[i];
            if (n <= 0) continue;
            ItemEntity e = m.items().get(i);
            ItemStack stack = e.getItem().copy();
            ItemStack part = stack.split(n);
            out.add(part);
            if (stack.isEmpty()) e.discard();
            else e.setItem(stack);
        }
        return out;
    }

    private static List<ItemStack> previewSouls(RitualMatch m) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < m.items().size(); i++) {
            ItemStack s = m.items().get(i).getItem();
            if (m.taken()[i] > 0 && s.getItem() instanceof RitualDaggerItem && SoulData.hasSoul(s)) out.add(s.copy());
        }
        return out;
    }

    private static List<ItemStack> previewConsumed(RitualMatch m) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < m.items().size(); i++) {
            ItemStack s = m.items().get(i).getItem();
            if (m.taken()[i] > 0 && !(s.getItem() instanceof RitualDaggerItem && SoulData.hasSoul(s))) out.add(s.copyWithCount(m.taken()[i]));
        }
        return out;
    }

    private static List<ItemEntity> leftovers(RitualMatch m) {
        List<ItemEntity> out = new ArrayList<>();
        for (int i = 0; i < m.items().size(); i++) {
            if (m.items().get(i).getItem().getCount() > m.taken()[i]) out.add(m.items().get(i));
        }
        return out;
    }

    private static void dropAll(ServerLevel level, BlockPos pos, List<ItemStack> stacks) {
        for (ItemStack s : stacks) {
            if (s.isEmpty()) continue;
            ItemEntity e = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, s.copy());
            e.setDeltaMovement(level.random.nextGaussian() * 0.03, 0.15, level.random.nextGaussian() * 0.03);
            e.setDefaultPickUpDelay();
            level.addFreshEntity(e);
        }
    }

    private static void fail(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED), false);
    }

    public static String formatTicks(long ticks) {
        long s = ticks / 20;
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }

    /** Diagnóstico: informa del círculo y del área central y marca las velas problemáticas. */
    public static void diagnose(ServerLevel level, BlockPos pos, RitualCoreBlockEntity core, ServerPlayer player) {
        CircleValidator.Report report = CircleValidator.inspect(level, pos);

        if (report.level() > 0) {
            player.sendSystemMessage(Component.translatable("message.ironrituals.core.circle_level", report.level()).withStyle(ChatFormatting.GREEN));
        } else {
            player.sendSystemMessage(Component.translatable("message.ironrituals.core.circle_none").withStyle(ChatFormatting.RED));
        }
        if (report.problems() > 0 && report.level() < 3) {
            player.sendSystemMessage(Component.translatable("message.ironrituals.core.next_ring",
                    report.level() + 1, report.missing().size(), report.unlit().size()).withStyle(ChatFormatting.GRAY));
        }
        for (BlockPos p : report.missing()) {
            level.sendParticles(player, MISSING_DUST, true, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 8, 0.15, 0.3, 0.15, 0);
        }
        for (BlockPos p : report.unlit()) {
            level.sendParticles(player, UNLIT_DUST, true, p.getX() + 0.5, p.getY() + 0.7, p.getZ() + 0.5, 8, 0.1, 0.2, 0.1, 0);
        }

        Map<String, Integer> components = new LinkedHashMap<>();
        Map<String, Component> names = new LinkedHashMap<>();
        for (ItemEntity e : core.itemsInCentralArea()) {
            ItemStack s = e.getItem();
            String key = s.getDescriptionId() + (SoulData.hasSoul(s) ? "#soul" : "");
            components.merge(key, s.getCount(), Integer::sum);
            names.putIfAbsent(key, SoulData.hasSoul(s)
                    ? s.getHoverName().copy().append(" (").append(SoulData.displayName(s)).append(")")
                    : s.getHoverName());
        }
        if (components.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.ironrituals.core.no_components").withStyle(ChatFormatting.GRAY));
        } else {
            MutableComponent line = Component.translatable("message.ironrituals.core.components").withStyle(ChatFormatting.GRAY);
            boolean first = true;
            for (Map.Entry<String, Integer> entry : components.entrySet()) {
                line.append(first ? " " : ", ");
                line.append(Component.literal(entry.getValue() + "× ").append(names.get(entry.getKey())).withStyle(ChatFormatting.WHITE));
                first = false;
            }
            player.sendSystemMessage(line);
        }
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, report.level() > 0 ? 1.2F : 0.6F);
    }

    /** Lista legible de los componentes de un nivel (para mensajes). */
    public static Component describe(List<RitualComponent> comps) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < comps.size(); i++) {
            RitualComponent c = comps.get(i);
            if (i > 0) out.append(", ");
            if (c.isSoul()) out.append(c.soul().describe());
            else {
                ItemStack[] st = c.ingredient().getItems();
                out.append(c.count() + "× ").append(st.length > 0 ? st[0].getHoverName() : Component.literal("?"));
            }
        }
        return out;
    }
}
