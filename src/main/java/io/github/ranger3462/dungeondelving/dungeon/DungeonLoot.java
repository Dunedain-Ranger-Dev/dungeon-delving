package io.github.ranger3462.dungeondelving.dungeon;

import java.util.List;
import java.util.function.Consumer;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Tracks the dungeon ores found during a run. Items in the {@code dungeondelving:lost_on_failure} tag that are generated
 * in the dungeon dimension (chest loot, mob drops, block drops) are marked as dungeon loot. Leaving through the exit
 * claims them; failing the run takes them away. Everything else found in a dungeon is the player's to keep.
 */
public final class DungeonLoot {
    /** Items that are lost if the run fails: by default, the Dungeon Delving ores and their raw ores, ingots and blocks. */
    public static final TagKey<Item> LOST_ON_FAILURE = TagKey.create(Registries.ITEM, DungeonDelving.id("lost_on_failure"));

    private DungeonLoot() {}

    public static boolean isLoot(ItemStack stack) {
        return stack.has(ModRegistries.DUNGEON_LOOT.get());
    }

    public static void mark(ItemStack stack) {
        if (!stack.isEmpty()) {
            stack.set(ModRegistries.DUNGEON_LOOT.get(), Unit.INSTANCE);
        }
    }

    /** Marks freshly generated items in the {@link #LOST_ON_FAILURE} tag when they were generated in the dungeon dimension. */
    public static void markIfInDungeon(List<ItemStack> generated, ResourceKey<Level> dimension) {
        if (dimension == DungeonDimension.DUNGEON) {
            generated.stream().filter(stack -> stack.is(LOST_ON_FAILURE)).forEach(DungeonLoot::mark);
        }
    }

    /**
     * A successful exit: the player keeps everything, and their dungeon loot becomes ordinary items. Returns how many
     * loot stacks were claimed.
     */
    public static int claim(ServerPlayer player) {
        int[] claimed = {0};
        Consumer<ItemStack> unmark = stack -> {
            if (stack.remove(ModRegistries.DUNGEON_LOOT.get()) != null) {
                claimed[0]++;
            }
        };
        forEachStack(player.getInventory(), unmark);
        forEachStack(player.getEnderChestInventory(), unmark);
        return claimed[0];
    }

    /**
     * A failed run: removes dungeon loot from the player's inventory and ender chest, and damages their gear by the
     * given fraction of its max durability, without fully breaking anything. Returns how many loot stacks were lost.
     */
    public static int applyFailurePenalty(ServerPlayer player, double gearDamage) {
        int lost = removeLoot(player.getInventory()) + removeLoot(player.getEnderChestInventory());
        if (gearDamage > 0) {
            forEachStack(player.getInventory(), stack -> damage(stack, gearDamage));
        }
        player.inventoryMenu.broadcastChanges();
        return lost;
    }

    private static int removeLoot(Container container) {
        int lost = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (isLoot(container.getItem(slot))) {
                container.setItem(slot, ItemStack.EMPTY);
                lost++;
            }
        }
        return lost;
    }

    private static void damage(ItemStack stack, double fraction) {
        if (!stack.isDamageableItem()) {
            return;
        }
        int max = stack.getMaxDamage();
        int added = Mth.ceil(max * fraction);
        // Leave at least 1 durability so the penalty never destroys an item outright.
        stack.setDamageValue(Math.min(max - 1, stack.getDamageValue() + added));
    }

    private static void forEachStack(Container container, Consumer<ItemStack> action) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                action.accept(stack);
            }
        }
    }
}
