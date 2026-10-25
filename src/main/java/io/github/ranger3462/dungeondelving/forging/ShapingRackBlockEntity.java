package io.github.ranger3462.dungeondelving.forging;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The piece lying on a Shaping Rack: one tanned leather or fulled cloth, waiting to be cut or sewn. Shown on the rack. */
public class ShapingRackBlockEntity extends BlockEntity {
    private ItemStack piece = ItemStack.EMPTY;

    public ShapingRackBlockEntity(BlockPos pos, BlockState state) {
        super(Crafts.SHAPING_RACK_ENTITY.get(), pos, state);
    }

    public ItemStack piece() {
        return piece;
    }

    public void setPiece(ItemStack piece) {
        this.piece = piece.copyWithCount(piece.isEmpty() ? 0 : 1);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Takes the piece off the rack. */
    public ItemStack take() {
        ItemStack taken = piece;
        setPiece(ItemStack.EMPTY);
        return taken;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // Always written, even empty: an empty update tag is ignored by the client, which then keeps drawing the old piece.
        tag.putBoolean("has_piece", !piece.isEmpty());
        if (!piece.isEmpty()) {
            tag.put("piece", piece.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        piece = tag.contains("piece") ? ItemStack.parse(registries, tag.get("piece")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
