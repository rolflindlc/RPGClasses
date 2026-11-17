package io.github.rpgclasses.bard;

import io.github.rpgclasses.server.CombatTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Lo que comparten los instrumentos y la guitarra-hacha: tocar (clic derecho), el atril y la descripción. */
public final class BardPlay {
    /** Mantener pulsado el clic derecho repite el uso cada 4 ticks: solo cuenta la primera pulsación. */
    private static final int HOLD_TICKS = 5;
    private static final Map<UUID, Long> LAST_USE = new HashMap<>();

    private BardPlay() {}

    public static InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand, ItemStack stack) {
        if (!Bard.isBard(player)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("msg.rites_and_paths.bard.not_bard"), true);
            return InteractionResultHolder.pass(stack);
        }
        // Con la guitarra-hacha en la mano principal, la secundaria no se toca (va a dos manos).
        if (hand == InteractionHand.OFF_HAND && player.getMainHandItem().getItem() instanceof GuitarAxeItem) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) return InteractionResultHolder.consume(stack);
        ServerPlayer sp = (ServerPlayer) player;
        long now = level.getGameTime();
        Long last = LAST_USE.put(sp.getUUID(), now);
        if (last != null && now - last >= 0 && now - last < HOLD_TICKS) return InteractionResultHolder.consume(stack);

        // Mayús + clic derecho con el instrumento en la mano principal y fuera de combate: el atril.
        if (player.isShiftKeyDown() && hand == InteractionHand.MAIN_HAND) {
            if (!CombatTracker.inCombat(sp)) {
                openStand(sp);
                return InteractionResultHolder.consume(stack);
            }
        }
        BardMagic magic = Bard.magic;
        if (magic == null) {
            sp.displayClientMessage(Component.translatable("msg.rites_and_paths.bard.no_irons"), true);
        } else {
            magic.castFromInstrument(sp, hand, stack);
        }
        return InteractionResultHolder.consume(stack);
    }

    public static void openStand(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        Instrument kind = BardItems.instrumentOf(stack);
        if (kind == null) return;
        int capacity = Bard.capacity(player, kind);
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inv, p) -> new MusicStandMenu(id, inv, capacity),
                Component.translatable("ui.rites_and_paths.stand.title", stack.getHoverName())), buf -> buf.writeVarInt(capacity));
    }

    /** Cada segundo (escalonado por hueco), el contenedor de Iron's se ajusta a las partituras y a quien lo lleva. */
    public static void inventoryTick(ItemStack stack, Level level, Entity entity, int slot) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player) || Bard.magic == null) return;
        if ((level.getGameTime() + slot) % 20 != 0) return;
        Bard.magic.syncContainer(player, stack);
    }

    public static void forget(UUID player) {
        LAST_USE.remove(player);
    }

    public static void tooltip(ItemStack stack, Instrument kind, List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.rites_and_paths.instrument.header",
                Component.translatable("tooltip.rites_and_paths.instrument.rarity." + kind.tier.key())).withStyle(ChatFormatting.GOLD));
        tooltip.add(line("power", pct(kind.musicPower())));
        if (kind.castSpeed() > 0) tooltip.add(line("cast_speed", pct(kind.castSpeed())));
        if (kind.manaRegen() > 0) tooltip.add(line("mana_regen", pct(kind.manaRegen())));
        if (kind.cooldownReduction() > 0) tooltip.add(line("cooldown", pct(kind.cooldownReduction())));
        if (kind.maxMana() > 0) tooltip.add(line("max_mana", num(kind.maxMana())));
        if (kind.passive != Instrument.Passive.NONE) {
            tooltip.add(Component.translatable("tooltip.rites_and_paths.instrument.passive",
                    Component.translatable("tooltip.rites_and_paths.instrument.passive." + kind.id)).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        List<Sheets.Entry> sheets = Sheets.read(stack);
        tooltip.add(Component.translatable("tooltip.rites_and_paths.instrument.slots", sheets.size(), kind.slots(),
                num(kind.levelCap() * 100)).withStyle(ChatFormatting.GRAY));
        for (Sheets.Entry e : sheets) {
            int cap = e.spell().levelCap(kind.levelCap());
            Component name = Component.translatable(e.spell().nameKey());
            tooltip.add((e.level() > cap
                    ? Component.translatable("tooltip.rites_and_paths.instrument.spell_capped", name, e.level(), cap)
                    : Component.translatable("tooltip.rites_and_paths.instrument.spell", name, e.level())).withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("tooltip.rites_and_paths.instrument.how").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component line(String key, String value) {
        return Component.translatable("tooltip.rites_and_paths.instrument." + key, value).withStyle(ChatFormatting.BLUE);
    }

    private static String pct(double fraction) {
        return num(fraction * 100);
    }

    private static String num(double v) {
        return Math.abs(v - Math.round(v)) < 1e-6 ? Long.toString(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }
}
