package io.github.ranger3462.dungeondelving.gate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds a Gate Tuner's one dungeon stone. */
public class GateTunerBlockEntity extends BlockEntity {
    private ItemStack stone = ItemStack.EMPTY;

    public GateTunerBlockEntity(BlockPos pos, BlockState state) {
        super(DungeonGates.TUNER_ENTITY.get(), pos, state);
    }

    public ItemStack stone() {
        return stone;
    }

    /** The stone's effect, if it holds one. */
    public DungeonStone tunedStone() {
        return stone.getItem() instanceof DungeonStoneItem item ? item.stone() : null;
    }

    public void setStone(ItemStack stack) {
        stone = stack.copyWithCount(1);
        changed();
    }

    /** Takes the stone out (or uses it up), leaving the tuner empty. */
    public ItemStack take() {
        ItemStack taken = stone;
        stone = ItemStack.EMPTY;
        changed();
        return taken;
    }

    private void changed() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.setBlock(worldPosition, state.setValue(GateTunerBlock.TUNED, !stone.isEmpty()), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!stone.isEmpty()) {
            tag.put("stone", stone.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stone = tag.contains("stone") ? ItemStack.parseOptional(registries, tag.getCompound("stone")) : ItemStack.EMPTY;
    }
}
