package io.github.ironrituals.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Canalización en curso en un núcleo (plan, 2.2). Los componentes tomados quedan guardados en {@link #reserved}
 * hasta que el ritual termina (se consumen) o se interrumpe (vuelven al suelo).
 */
public final class RitualChannel {
    public final ResourceLocation ritual;
    public final int level;
    public final List<String> catalysts;
    public final UUID caster;
    public final int total;
    public final float manaTotal;
    public final List<BlockPos> candles;
    public final List<ItemStack> reserved;
    public int elapsed;
    public float manaSpent;

    /** Barra de progreso (no se guarda; se recrea al vuelo). */
    @Nullable transient ServerBossEvent bar;

    public RitualChannel(ResourceLocation ritual, int level, List<String> catalysts, UUID caster, int total, float manaTotal,
                         List<BlockPos> candles, List<ItemStack> reserved) {
        this.ritual = ritual;
        this.level = level;
        this.catalysts = List.copyOf(catalysts);
        this.caster = caster;
        this.total = Math.max(1, total);
        this.manaTotal = manaTotal;
        this.candles = List.copyOf(candles);
        this.reserved = new ArrayList<>(reserved);
    }

    public float progress() {
        return Math.min(1.0F, (float) elapsed / total);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Ritual", ritual.toString());
        t.putInt("Level", level);
        ListTag cats = new ListTag();
        for (String c : catalysts) cats.add(StringTag.valueOf(c));
        t.put("Catalysts", cats);
        t.putUUID("Caster", caster);
        t.putInt("Total", total);
        t.putInt("Elapsed", elapsed);
        t.putFloat("ManaTotal", manaTotal);
        t.putFloat("ManaSpent", manaSpent);
        ListTag cand = new ListTag();
        for (BlockPos p : candles) cand.add(NbtUtils.writeBlockPos(p));
        t.put("Candles", cand);
        ListTag res = new ListTag();
        for (ItemStack s : reserved) res.add(s.save(new CompoundTag()));
        t.put("Reserved", res);
        return t;
    }

    public static RitualChannel load(CompoundTag t) {
        List<String> cats = new ArrayList<>();
        for (Tag c : t.getList("Catalysts", Tag.TAG_STRING)) cats.add(c.getAsString());
        List<BlockPos> cand = new ArrayList<>();
        for (Tag c : t.getList("Candles", Tag.TAG_COMPOUND)) cand.add(NbtUtils.readBlockPos((CompoundTag) c));
        List<ItemStack> res = new ArrayList<>();
        for (Tag c : t.getList("Reserved", Tag.TAG_COMPOUND)) res.add(ItemStack.of((CompoundTag) c));
        RitualChannel ch = new RitualChannel(ResourceLocation.tryParse(t.getString("Ritual")), t.getInt("Level"), cats,
                t.getUUID("Caster"), t.getInt("Total"), t.getFloat("ManaTotal"), cand, res);
        ch.elapsed = t.getInt("Elapsed");
        ch.manaSpent = t.getFloat("ManaSpent");
        return ch;
    }
}
