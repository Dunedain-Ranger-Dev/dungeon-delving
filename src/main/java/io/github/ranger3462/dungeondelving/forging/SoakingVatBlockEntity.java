package io.github.ranger3462.dungeondelving.forging;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What's soaking in a vat ({@link SoakingVatBlock}): the pieces put in, when they'll be done, and the best ★ they can
 * come out at (the liquor's, for tanning).
 */
public class SoakingVatBlockEntity extends BlockEntity {
    private final List<ItemStack> contents = new ArrayList<>();
    private long readyAt;
    /** The ★ of the liquid in the vat (0 while it has none). */
    private int liquorStars;

    public SoakingVatBlockEntity(BlockPos pos, BlockState state) {
        super(Crafts.SOAKING_VAT_ENTITY.get(), pos, state);
    }

    public List<ItemStack> contents() {
        return contents;
    }

    public boolean soaking() {
        return !contents.isEmpty();
    }

    public long readyAt() {
        return readyAt;
    }

    public int liquorStars() {
        return liquorStars;
    }

    public void setLiquorStars(int stars) {
        liquorStars = stars;
        setChanged();
    }

    /** Starts a batch, ready at {@code readyAt}. */
    public void start(List<ItemStack> pieces, long readyAt) {
        contents.clear();
        pieces.forEach(piece -> contents.add(piece.copy()));
        this.readyAt = readyAt;
        setChanged();
    }

    /** Ends the batch early. For tests. */
    public void finishNow() {
        readyAt = 0;
        setChanged();
    }

    /** Empties the vat of its batch. */
    public List<ItemStack> takeAll() {
        List<ItemStack> taken = new ArrayList<>(contents);
        contents.clear();
        setChanged();
        return taken;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        contents.forEach(stack -> list.add(stack.save(registries)));
        tag.put("contents", list);
        tag.putLong("ready_at", readyAt);
        tag.putInt("liquor_stars", liquorStars);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        contents.clear();
        for (Tag entry : tag.getList("contents", Tag.TAG_COMPOUND)) {
            ItemStack.parse(registries, entry).ifPresent(contents::add);
        }
        readyAt = tag.getLong("ready_at");
        liquorStars = tag.getInt("liquor_stars");
    }
}
