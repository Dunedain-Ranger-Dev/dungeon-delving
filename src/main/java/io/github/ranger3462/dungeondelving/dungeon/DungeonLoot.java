package io.github.ranger3462.dungeondelving.dungeon;

import java.util.List;
import java.util.function.Consumer;

import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Tracks which items were found during a dungeon run. Anything generated in the dungeon dimension (chest loot, mob
 * drops, block drops) is marked as dungeon loot. Leaving through the exit claims it; failing the run takes it away.
 */
public final class DungeonLoot {
    private DungeonLoot() {}

    public static boolean isLoot(ItemStack stack) {
        return stack.has(ModRegistries.DUNGEON_LOOT.get());
    }

    public static void mark(ItemStack stack) {
        if (!stack.isEmpty()) {
            stack.set(ModRegistries.DUNGEON_LOOT.get(), Unit.INSTANCE);
        }
    }

    /** Marks freshly generated items as dungeon loot when they were generated in the dungeon dimension. */
    public static void markIfInDungeon(List<ItemStack> generated, ResourceKey<Level> dimension) {
        if (dimension == DungeonDimension.DUNGEON) {
            generated.forEach(DungeonLoot::mark);
        }
    }

    /** A successful exit: the player keeps everything, and their dungeon loot becomes ordinary items. */
    public static void claim(ServerPlayer player) {
        forEachStack(player.getInventory(), stack -> stack.remove(ModRegistries.DUNGEON_LOOT.get()));
        forEachStack(player.getEnderChestInventory(), stack -> stack.remove(ModRegistries.DUNGEON_LOOT.get()));
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
