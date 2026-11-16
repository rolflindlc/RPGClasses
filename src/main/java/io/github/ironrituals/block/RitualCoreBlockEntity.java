package io.github.ironrituals.block;

import io.github.ironrituals.item.RitualTomeItem;
import io.github.ironrituals.ritual.RitualChannel;
import io.github.ironrituals.ritual.RitualRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import io.github.ironrituals.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Núcleo del Ritual (plan, 3.B): guarda el libro de ritual de su ranura y el propietario. En fases posteriores
 * llevará también el estado de la canalización.
 */
public class RitualCoreBlockEntity extends BlockEntity {
    private ItemStack book = ItemStack.EMPTY;
    @Nullable private UUID owner;
    private String ownerName = "";
    @Nullable private RitualChannel channel;
    private boolean clientChanneling;

    public RitualCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RITUAL_CORE.get(), pos, state);
    }

    // ------------------------------------------------------------------ Libro

    public ItemStack getBook() {
        return book;
    }

    public boolean hasBook() {
        return !book.isEmpty();
    }

    /** Coloca un libro (copia de 1 unidad). Devuelve {@code false} si la ranura está ocupada o no es un libro. */
    public boolean insertBook(ItemStack stack) {
        if (hasBook() || !(stack.getItem() instanceof RitualTomeItem)) return false;
        book = stack.copyWithCount(1);
        changed();
        return true;
    }

    public ItemStack removeBook() {
        ItemStack out = book;
        book = ItemStack.EMPTY;
        changed();
        return out;
    }

    // ------------------------------------------------------------------ Propietario

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwner(UUID owner, String name) {
        this.owner = owner;
        this.ownerName = name;
        changed();
    }

    // ------------------------------------------------------------------ Canalización

    @Nullable
    public RitualChannel getChannel() {
        return channel;
    }

    public boolean isChanneling() {
        return channel != null;
    }

    public void setChannel(@Nullable RitualChannel channel) {
        this.channel = channel;
        changed();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RitualCoreBlockEntity core) {
        if (core.channel != null && level instanceof ServerLevel sl) RitualRunner.tick(sl, pos, core);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (channel != null) RitualRunner.removeBar(channel);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (channel != null) RitualRunner.removeBar(channel);
    }

    // ------------------------------------------------------------------ Área central

    /**
     * Área donde se arrojan los componentes: el interior del primer anillo (3×3 bloques alrededor del núcleo,
     * desde su altura hasta 2 bloques por encima).
     */
    public AABB centralArea() {
        BlockPos p = getBlockPos();
        return new AABB(p.getX() - 1, p.getY(), p.getZ() - 1, p.getX() + 2, p.getY() + 2, p.getZ() + 2);
    }

    public List<ItemEntity> itemsInCentralArea() {
        if (level == null) return List.of();
        return level.getEntitiesOfClass(ItemEntity.class, centralArea(), e -> e.isAlive() && !e.getItem().isEmpty());
    }

    // ------------------------------------------------------------------ Persistencia y sincronización

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!book.isEmpty()) tag.put("Book", book.save(new CompoundTag()));
        if (owner != null) {
            tag.putUUID("Owner", owner);
            tag.putString("OwnerName", ownerName);
        }
        if (channel != null) tag.put("Channel", channel.save());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        book = tag.contains("Book") ? ItemStack.of(tag.getCompound("Book")) : ItemStack.EMPTY;
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        channel = tag.contains("Channel") ? RitualChannel.load(tag.getCompound("Channel")) : null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        // Al cliente solo le hace falta el libro (y si se está canalizando), no los componentes reservados.
        CompoundTag tag = new CompoundTag();
        if (!book.isEmpty()) tag.put("Book", book.save(new CompoundTag()));
        tag.putBoolean("Channeling", channel != null);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        book = tag.contains("Book") ? ItemStack.of(tag.getCompound("Book")) : ItemStack.EMPTY;
        clientChanneling = tag.getBoolean("Channeling");
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) handleUpdateTag(pkt.getTag());
    }

    /** Solo en cliente: si el núcleo está canalizando (para el renderizador). */
    public boolean clientChanneling() {
        return clientChanneling;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
