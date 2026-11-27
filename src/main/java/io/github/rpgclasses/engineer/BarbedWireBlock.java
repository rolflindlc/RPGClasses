package io.github.rpgclasses.engineer;

import io.github.rpgclasses.server.Allies;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Alambre de espino de la Trinchera (adenda del Ingeniero, 4.3): frena como una telaraña. A los enemigos del dueño los
 * frena del todo y les hace daño si se mueven dentro; al dueño y a sus aliados, la mitad y sin daño. Se rompe como una
 * telaraña (rápido con espada o tijeras), no suelta nada y desaparece solo tras un rato o al levantar otra trinchera.
 */
public class BarbedWireBlock extends Block implements EntityBlock {
    public BarbedWireBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Owner(pos, state);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        UUID owner = level.getBlockEntity(pos) instanceof Owner be ? be.owner : null;
        boolean friend = owner != null && (entity.getUUID().equals(owner)
                || !level.isClientSide && level.getServer() != null && Allies.isAllyOf(level.getServer(), owner, entity)
                || level.isClientSide && entity instanceof Player && EngineerHooks.clientAlly != null
                && EngineerHooks.clientAlly.test(owner));
        double slow = Engineer.a(Engineer.TRENCH, "ally_slow", .5);
        // Telaraña: 0,25 en horizontal y 0,05 en vertical; amigos, la mitad del frenazo.
        entity.makeStuckInBlock(state, friend ? new Vec3(1 - .75 * slow, 1 - .95 * slow, 1 - .75 * slow) : new Vec3(0.25, 0.05, 0.25));
        if (friend || level.isClientSide || !(entity instanceof LivingEntity living) || !(level instanceof ServerLevel server)) return;
        boolean moving = entity.getX() != entity.xo || entity.getZ() != entity.zo || entity.getY() != entity.yo;
        if (!moving) return;
        ServerPlayer ownerPlayer = owner == null ? null : server.getServer().getPlayerList().getPlayer(owner);
        // La invulnerabilidad de 10 tics hace que el daño llegue cada medio segundo.
        living.hurt(Hitscan.source(server, Artificer.WIRE, null, ownerPlayer), (float) Engineer.a(Engineer.TRENCH, "damage", 1));
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        var tool = player.getMainHandItem().getItem();
        float speed = tool instanceof SwordItem || tool instanceof ShearsItem ? 15 : 1;
        return speed / state.getDestroySpeed(level, pos) / 30f;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
    }

    /** Dueño del alambre (para saber quién es aliado). */
    public static class Owner extends BlockEntity {
        UUID owner;

        public Owner(BlockPos pos, BlockState state) {
            super(EngineerRegistry.BARBED_WIRE_ENTITY.get(), pos, state);
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            if (owner != null) tag.putUUID("Owner", owner);
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        }

        @Override
        public CompoundTag getUpdateTag() {
            CompoundTag tag = super.getUpdateTag();
            if (owner != null) tag.putUUID("Owner", owner);
            return tag;
        }

        @Override
        public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
            return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
        }
    }
}
