package io.github.ranger3462.dungeondelving.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * Finds dungeon loot wherever a player carries it: in their inventory slots, and inside items there that hold other
 * items (backpacks such as Sophisticated Backpacks, shulker boxes, bundles), however they're nested. Each loot stack
 * found is replaced with what the given action returns, so a sweep can take loot away or turn it into ordinary items.
 */
public final class LootSweep {
    /** How many containers deep to look, so a container holding itself can't loop forever. */
    private static final int MAX_DEPTH = 8;
    /** Most extractions to empty one storage slot; large backpack slots hand items out a stack at a time. */
    private static final int MAX_EXTRACTIONS = 1024;

    private final Predicate<ItemStack> match;
    private final UnaryOperator<ItemStack> action;
    private final List<ItemStack> overflow = new ArrayList<>();
    private int found;

    /** @param action given each loot stack found, returns its replacement (empty to remove it). */
    public LootSweep(UnaryOperator<ItemStack> action) {
        this(DungeonLoot::isLoot, action);
    }

    /** A sweep for any kind of stack, e.g. Dungeon Keys, rather than dungeon loot. */
    public LootSweep(Predicate<ItemStack> match, UnaryOperator<ItemStack> action) {
        this.match = match;
        this.action = action;
    }

    /** How many loot stacks the sweep has found so far. */
    public int found() {
        return found;
    }

    /**
     * Items that couldn't be put back where they came from, such as a backpack a container refused to take back. The
     * caller should give them to the player.
     */
    public List<ItemStack> overflow() {
        return overflow;
    }

    /** Sweeps every slot of a container, such as a player's inventory or ender chest. */
    public void sweep(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int before = found;
            ItemStack result = sweep(stack, 0);
            if (found != before) {
                container.setItem(slot, result);
            }
        }
    }

    /** Sweeps every slot of an item handler whose slots can be set directly, such as Curios slots. */
    public void sweep(IItemHandlerModifiable handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int before = found;
            ItemStack result = sweep(stack.copy(), 0);
            if (found != before) {
                handler.setStackInSlot(slot, result);
            }
        }
    }

    /**
     * Sweeps one stack: loot is passed to the action, and anything else has its contents swept. Returns the stack to
     * keep in its place, which may be the same (changed) stack.
     */
    public ItemStack sweep(ItemStack stack, int depth) {
        if (match.test(stack)) {
            found++;
            return action.apply(stack);
        }
        if (depth < MAX_DEPTH) {
            sweepContainerContents(stack, depth);
            sweepBundleContents(stack, depth);
            sweepItemHandler(stack, depth);
        }
        return stack;
    }

    /** Items stored on the stack itself, e.g. a shulker box. */
    private void sweepContainerContents(ItemStack stack, int depth) {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) {
            return;
        }
        NonNullList<ItemStack> items = NonNullList.withSize(contents.getSlots(), ItemStack.EMPTY);
        contents.copyInto(items);
        int before = found;
        items.replaceAll(item -> item.isEmpty() ? item : sweep(item, depth + 1));
        if (found != before) {
            stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        }
    }

    private void sweepBundleContents(ItemStack stack, int depth) {
        BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (contents == null) {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        int before = found;
        for (ItemStack item : contents.itemsCopy()) {
            ItemStack result = sweep(item, depth + 1);
            if (!result.isEmpty()) {
                items.add(result);
            }
        }
        if (found != before) {
            stack.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(items));
        }
    }

    /** Items a mod stores elsewhere and exposes as an item handler, e.g. a Sophisticated Backpack. */
    private void sweepItemHandler(ItemStack stack, int depth) {
        IItemHandler handler = stack.getCapability(Capabilities.ItemHandler.ITEM);
        if (handler == null) {
            return;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack inside = handler.getStackInSlot(slot);
            if (inside.isEmpty()) {
                continue;
            }
            int before = found;
            ItemStack result = sweep(inside.copy(), depth + 1);
            if (found == before) {
                continue;
            }
            // Handlers can't be written to directly, so swap the old stack for the result.
            for (int i = 0; i < MAX_EXTRACTIONS && !handler.getStackInSlot(slot).isEmpty(); i++) {
                if (handler.extractItem(slot, handler.getStackInSlot(slot).getCount(), false).isEmpty()) {
                    break;
                }
            }
            if (!result.isEmpty()) {
                ItemStack left = handler.insertItem(slot, result, false);
                if (!left.isEmpty()) {
                    overflow.add(left);
                }
            }
        }
    }
}
