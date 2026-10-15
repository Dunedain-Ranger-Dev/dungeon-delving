package io.github.ranger3462.dungeondelving.dungeon.bag;

import java.util.ArrayList;
import java.util.List;

import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Everything a player has found during their current dungeon run. Loot goes here instead of the player's inventory;
 * a successful exit moves it into their inventory, and death or failure empties it.
 */
public class DungeonBag {
    /** Slots in a bag with no upgrades. */
    public static final int BASE_SLOTS = 9;
    /** The most a bag can hold: a double chest. */
    public static final int MAX_SLOTS = 54;

    private final SimpleContainer items;

    public DungeonBag(int slots) {
        this.items = new SimpleContainer(Math.max(BASE_SLOTS, Math.min(MAX_SLOTS, slots)));
    }

    /** Bag size for a number of upgrades: a row of 9 more slots per upgrade, up to {@link #MAX_SLOTS}. */
    public static int slotsForLevel(int level) {
        return Math.min(MAX_SLOTS, BASE_SLOTS * (1 + Math.max(0, level)));
    }

    /** The underlying container, for showing the bag in a menu. */
    public SimpleContainer container() {
        return items;
    }

    public int size() {
        return items.getContainerSize();
    }

    public int usedSlots() {
        int used = 0;
        for (int slot = 0; slot < items.getContainerSize(); slot++) {
            if (!items.getItem(slot).isEmpty()) {
                used++;
            }
        }
        return used;
    }

    /**
     * Puts as much of a stack into the bag as fits, filling matching stacks first. Returns what didn't fit; the given
     * stack is left unchanged. Dungeon bags themselves never go in.
     */
    public ItemStack insert(ItemStack stack) {
        if (stack.isEmpty() || stack.is(ModRegistries.DUNGEON_BAG.get())) {
            return stack.copy();
        }
        return items.addItem(stack.copy());
    }

    /** Empties the bag and returns everything that was in it. */
    public List<ItemStack> takeAll() {
        List<ItemStack> taken = new ArrayList<>();
        for (int slot = 0; slot < items.getContainerSize(); slot++) {
            ItemStack stack = items.removeItemNoUpdate(slot);
            if (!stack.isEmpty()) {
                taken.add(stack);
            }
        }
        return taken;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("size", items.getContainerSize());
        ContainerHelper.saveAllItems(tag, items.getItems(), registries);
        return tag;
    }

    public static DungeonBag load(CompoundTag tag, HolderLookup.Provider registries) {
        DungeonBag bag = new DungeonBag(tag.getInt("size"));
        NonNullList<ItemStack> loaded = NonNullList.withSize(bag.size(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, loaded, registries);
        for (int slot = 0; slot < loaded.size(); slot++) {
            bag.items.setItem(slot, loaded.get(slot));
        }
        return bag;
    }
}
